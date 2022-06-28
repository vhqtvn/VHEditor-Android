package vn.vhn.vhscode.root.codeserver

import android.content.Context
import android.os.Build
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import vn.vhn.vhscode.CodeServerService
import vn.vhn.vhscode.kConfigStreamBuferSize
import java.io.DataInputStream
import java.io.File
import java.net.ServerSocket
import java.util.*

class CodeServerSession(
    val id: Int,
    val ctx: Context,
    val listenOnAllInterface: Boolean,
    val useSSL: Boolean,
    val remote: Boolean = false,
    var remoteURL: String? = null,
    port: Int? = null,
) {
    companion object {
        const val OUTPUT_STREAM_STDOUT = 1
        const val OUTPUT_STREAM_STDERR = 2

        public enum class RunStatus {
            NOT_STARTED,
            STARTING,
            RUNNING,
            ERROR,
            FINISHED
        }

        private fun freePort(): Int {
            return try {
                val s = ServerSocket(0)
                val r = s.localPort
                try {
                    s.close()
                } catch (e: Exception) {
                }
                r
            } catch (e: Exception) {
                13337
            }
        }
    }

    val mHandle = UUID.randomUUID().toString()

    private var isServerStarted = false
    private var mIOJobs: List<Job>? = null
    private var mPort = port ?: freePort()
    var hasStarted = false
    var mTerminated = false
    var process: Process? = null
    var title = ""

    var error: String? = null

    // -1: starting, 0: not started, 1: started
    val status: MutableLiveData<RunStatus> =
        MutableLiveData<RunStatus>().apply { postValue(RunStatus.NOT_STARTED) }

    val port: Int
        get() = mPort

    public val liveServerLog: MutableLiveData<String> =
        MutableLiveData<String>().apply { postValue("") }

    @Synchronized
    fun start() {
        if (hasStarted) return
        hasStarted = true
        _run()
    }

    fun killIfExecuting(context: Context) {
        if (remote) {
            onProcessFinished(RunStatus.FINISHED, true)
            return
        }
        if (!checkIfHasExited()) {
            process?.destroy()
            for (i in 0..30) {
                if (checkIfHasExited()) break
                try {
                    Thread.sleep(100)
                } catch (e: Exception) {
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!checkIfHasExited()) process?.destroyForcibly()
            }
        }
    }

    private fun _run() {
        if (remote) {
            status.postValue(RunStatus.RUNNING)
            appendLog(OUTPUT_STREAM_STDERR, "Running remote editor: ${remoteURL}\n")
            return
        }
        status.postValue(RunStatus.STARTING)
        val nodeBinary = ctx.getFileStreamPath("node")
        val envHome = nodeBinary?.parentFile?.absolutePath
        appendLog(
            OUTPUT_STREAM_STDERR, "Starting... listenOnAllInterface=${listenOnAllInterface}\n"
        )
        try {
            error = null
            val codeServerRoot = listOf(
                "${envHome}/code-server/release-standalone",
                "${envHome}/code-server/release-static"
            ).reduce { x, y -> if (File(y).exists()) y else x }

            try {
                Runtime.getRuntime().exec("killall node").inputStream.read()
            } catch (_: java.lang.Exception) {
            }

            val cmd = arrayOf(
                "${CodeServerService.ROOT_PATH}/usr/bin/bash",
                ctx.getFileStreamPath("boot.sh").absolutePath,
                codeServerRoot,
                "--disable-telemetry",
                "--disable-update-check"
            ) + (if (useSSL) arrayOf(
                "--cert", "${CodeServerService.HOME_PATH}/cert.cert",
                "--cert-key", "${CodeServerService.HOME_PATH}/cert.key"
            ) else arrayOf()) +
                    arrayOf(
                        "--auth",
                        "none",
                        "--host",
                        if (listenOnAllInterface) "0.0.0.0" else "127.0.0.1",
                        "--port",
                        mPort.toString(),
                        "--without-connection-token"
                    )
            val env = CodeServerService.buildEnv()
            File("${CodeServerService.PREFIX_PATH}/run-vs-code.sh").bufferedWriter().use {
                for (e in env) it.write("$e ")
                for (c in cmd) it.write("$c ")
            }
            process = Runtime.getRuntime().exec(cmd, env)
            val stream = DataInputStream(process!!.inputStream)
            val errStream = DataInputStream(process!!.errorStream)
            mIOJobs = listOf(
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        outputStreamUpdate(stream, OUTPUT_STREAM_STDOUT)
                    } catch (e: Exception) {
                        error = e.toString()
                        appendLog(OUTPUT_STREAM_STDERR, "\nException: ${error}")
                    } finally {
                        appendLog(OUTPUT_STREAM_STDERR, "\nFinished.")
                        onProcessFinished(RunStatus.FINISHED)
                    }
                },
                CoroutineScope(Dispatchers.IO).launch {
                    outputStreamUpdate(errStream, OUTPUT_STREAM_STDERR)
                },
                CoroutineScope(Dispatchers.IO).launch {
                    while (!mTerminated) {
                        try {
                            process?.waitFor()
                            process?.exitValue()
                            break
                        } catch (e: IllegalThreadStateException) {
                        }
                    }
                    onProcessFinished(RunStatus.FINISHED, true)
                    //finished
                }
            )
        } catch (e: Exception) {
            error = e.toString()
            appendLog(OUTPUT_STREAM_STDERR, "\nProcess initializing exception: ${error}")
            onProcessFinished(RunStatus.ERROR)
        }
    }

    private fun checkIfHasExited(): Boolean {
        if (remote) {
            return true
        }

        if (process == null) return true
        return try {
            process?.exitValue()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun onProcessFinished(
        newStatus: RunStatus,
        sure: Boolean = false,
    ) {
        if (!sure && !checkIfHasExited()) return
        if (checkIfHasExited()) {
            status.postValue(newStatus)
            mTerminated = true
            if (remote) {
                appendLog(OUTPUT_STREAM_STDERR, "Finished.\n")
            }
        }
    }

    private var outputBuffer: String = ""

    @Synchronized
    private fun appendLog(type: Int, log: String) {
        outputBuffer += log
        if (!isServerStarted) {
            if (
                outputBuffer.indexOf("HTTPS server listening on") >= 0
                || outputBuffer.indexOf("HTTP server listening on") >= 0
            ) {
                isServerStarted = true
                status.postValue(RunStatus.RUNNING)
            }
        }
        liveServerLog.postValue(outputBuffer)
    }

    private suspend fun outputStreamUpdate(outputStream: DataInputStream, type: Int) {
        val bufSize = kConfigStreamBuferSize
        val buffer = ByteArray(bufSize)
        withContext(Dispatchers.IO) {
            while (!mTerminated) {
                try {
                    process?.exitValue()
                    break
                } catch (e: IllegalThreadStateException) {
                }
                var size: Int = 0
                try {
                    size = outputStream.read(buffer)
                } catch (e: Exception) {
                    // ???
                    continue
                }
                if (size <= 0) continue
                try {
                    appendLog(type, String(buffer, 0, size))
                } catch (e: Exception) {
                    // not relating to process
                }
            }
        }
    }
}