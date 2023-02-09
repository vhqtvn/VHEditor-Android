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
import kotlin.collections.HashSet

typealias RunStatus = ICodeServerSession.RunStatus

class CodeServerLocalService(
    override val id: Int,
    val ctx: Context,
    val listenOnAllInterface: Boolean,
    val useSSL: Boolean,
    val port: Int? = null,
    val verbose: Boolean = false,
) : ICodeServerSession() {
    companion object {
        private const val LOG_LIMIT = 64000

        const val OUTPUT_STREAM_STDOUT = 1
        const val OUTPUT_STREAM_STDERR = 2

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

    val refs = HashSet<Int>()

    private var isServerStarted = false
    private var mIOJobs: List<Job>? = null
    private var mPort = port ?: freePort()
    var hasStarted = false
    var mTerminated = false
    var process: Process? = null
    override var title = ""
    override val terminated: Boolean
        get() = mTerminated

    var error: String? = null

    override val sessionName = "EditorBase"

    override val status: MutableLiveData<RunStatus> = MutableLiveData(RunStatus.NOT_STARTED)

    override val liveServerLog: MutableLiveData<String> = MutableLiveData("")

    override val url: String
        get() {
            return ((if (useSSL) "https://" else "http://")
                    + ("127.0.0.1")
                    + ":${mPort}")
        }

    override fun kill(context: Context) {
        killIfExecuting(context)
    }

    override val inputState: MutableLiveData<InputState> = MutableLiveData(InputState.C.None())

    @Synchronized
    fun start() {
        if (hasStarted) return
        hasStarted = true
        _run()
    }

    fun killIfExecuting(context: Context) {
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
            ) +
                    (if (verbose)
                        arrayOf("-vvv")
                    else arrayOf()) +
                    (if (useSSL) arrayOf(
                        "--cert", "${CodeServerService.HOME_PATH}/cert.cert",
                        "--cert-key", "${CodeServerService.HOME_PATH}/cert.key"
                    ) else arrayOf()) +
                    arrayOf(
                        "--app-name",
                        "VHEditor::code-server",
                        "--auth",
                        "none",
                        "--host",
                        if (listenOnAllInterface) "0.0.0.0" else "127.0.0.1",
                        "--port",
                        mPort.toString(),
                        "--without-connection-token"
                    )
            val env = CodeServerService.buildEnv("VSCODE__WITHOUT_CONNECTION_TOKEN=1")
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
        }
    }

    private var outputBuffer: String = ""

    @Synchronized
    private fun appendLog(type: Int, log: String) {
        outputBuffer += log
        if (outputBuffer.length >= LOG_LIMIT) {
            outputBuffer = outputBuffer.substring(outputBuffer.length - LOG_LIMIT)
        }
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