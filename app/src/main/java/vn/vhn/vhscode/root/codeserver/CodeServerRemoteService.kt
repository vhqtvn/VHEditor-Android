package vn.vhn.vhscode.root.codeserver

import android.content.Context
import android.os.Build
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import vn.vhn.vhscode.CodeServerService
import vn.vhn.vhscode.kConfigStreamBuferSize
import java.io.BufferedWriter
import java.io.DataInputStream
import java.io.File
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.util.*
import kotlin.collections.HashSet

class CodeServerRemoteService(
    override val id: Int,
    val ctx: Context,
    val useSSL: Boolean,
    val sshCmd: String,
    val sshArgs: Array<String>,
    val port: Int? = null,
    override var sessionName: String?,
    val verbose: Boolean = false,
) : ICodeServerSession() {
    companion object {
        private const val LOG_LIMIT = 64000

        const val OUTPUT_STREAM_STDOUT = 1
        const val OUTPUT_STREAM_STDERR = 2

        const val ROLLING_BUFFER_SIZE = 8000
        const val MAGIC_CMD_START = "[[VHEditor:"
        const val MAGIC_CMD_END = "/VHEditor]]"

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

        private fun freeRemotePort(): Int {
            return 8762 //10000 + (Math.random() * 30000).toInt()
        }
    }

    val remotePort = freeRemotePort()

    override val isRemote = true

    val mHandle = UUID.randomUUID().toString()

    val refs = HashSet<Int>()

    override val inputState: MutableLiveData<InputState> = MutableLiveData(InputState.C.None())

    private var isServerStarted = false
    private var mIOJobs: List<Job>? = null
    private var mPort = port ?: freePort()
    private val token = UUID.randomUUID().toString()
    private var stdin: BufferedWriter? = null
    var hasStarted = false
    var mTerminated = false
    var process: Process? = null
    override var title = ""
    override val terminated: Boolean
        get() = mTerminated

    var error: String? = null

    override val status: MutableLiveData<RunStatus> = MutableLiveData(RunStatus.NOT_STARTED)

    override val liveServerLog: MutableLiveData<String> = MutableLiveData("")

    override val url: String
        get() {
            return ((if (useSSL) "https://" else "http://") + ("127.0.0.1") + ":${mPort}")
        }

    override fun kill(context: Context) {
        killIfExecuting(context)
    }

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
        appendLog(OUTPUT_STREAM_STDERR, "Starting...\n")
        try {
            error = null
            val cmd = arrayOf(
                "${CodeServerService.ROOT_PATH}/usr/bin/bash",
                ctx.getFileStreamPath("boot-remote.sh").absolutePath,
            ) + arrayOf(
                sshCmd,
                "-L",
                "${mPort}:127.0.0.1:${remotePort}",
            ) + sshArgs + arrayOf(
                "--remote--args--",
            ) + (if (useSSL) arrayOf(
                "ssl",
                File("${CodeServerService.HOME_PATH}/cert.cert").readText(),
                File("${CodeServerService.HOME_PATH}/cert.key").readText(),
            ) else arrayOf()
                    ) + arrayOf(
                "--disable-telemetry",
//                "--disable-update-check"
            ) + (if (verbose) arrayOf("-vvv")
            else arrayOf()) + arrayOf("--auth",
                "none",
                "--host",
                "127.0.0.1",
                "--port",
                remotePort.toString())

            var env = CodeServerService.buildEnv("VHEDITORASKPASSTOKEN=$token")
            File("${CodeServerService.PREFIX_PATH}/run-vs-code-remote.sh").bufferedWriter().use {
                it.write("env ")
                for (e in env) it.write("\"$e\" ")
                for (c in cmd) it.write("\"$c\" ")
            }
            process = Runtime.getRuntime().exec(cmd, env)
            val stream = DataInputStream(process!!.inputStream)
            stdin = BufferedWriter(OutputStreamWriter(process!!.outputStream))
            val errStream = DataInputStream(process!!.errorStream)
            mIOJobs = listOf(CoroutineScope(Dispatchers.IO).launch {
                try {
                    outputStreamUpdate(stream, OUTPUT_STREAM_STDOUT)
                } catch (e: Exception) {
                    error = e.toString()
                    appendLog(OUTPUT_STREAM_STDERR, "\nException: ${error}")
                } finally {
                    stdin?.close()
                    appendLog(OUTPUT_STREAM_STDERR, "\nFinished.")
                    onProcessFinished(RunStatus.FINISHED)
                }
            }, CoroutineScope(Dispatchers.IO).launch {
                outputStreamUpdate(errStream, OUTPUT_STREAM_STDERR)
            }, CoroutineScope(Dispatchers.IO).launch {
                while (!mTerminated) {
                    try {
                        process?.exitValue()
                        break
                    } catch (e: java.lang.Exception) {
                    }
                    try {
                        process?.waitFor()
                    } catch (e: java.lang.Exception) {
                    }
                }
                onProcessFinished(RunStatus.FINISHED, true)
                //finished
            })
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
            if (outputBuffer.indexOf("HTTPS server listening on") >= 0 || outputBuffer.indexOf("HTTP server listening on") >= 0) {
                isServerStarted = true
                status.postValue(RunStatus.RUNNING)
            }
        }
        liveServerLog.postValue(outputBuffer)
    }

    override fun sendInput(s: String) {
        when (inputState.value) {
            is InputState.C.Password,
            is InputState.C.ReinstallConfirmation,
            -> {
                inputState.postValue(InputState.C.None())
                CoroutineScope(Dispatchers.IO).launch {
                    stdin?.write("vheditorpassreply:$s\n")
                    stdin?.flush()
                }
            }
            else -> {}
        }
    }

    private suspend fun outputStreamUpdate(outputStream: DataInputStream, type: Int) {
        val bufSize = kConfigStreamBuferSize
        val buffer = ByteArray(bufSize)
        var rollingBuffer = ""
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
                    val buf = String(buffer, 0, size)
                    appendLog(type, buf)
                    rollingBuffer += buf
                    if (rollingBuffer.length > ROLLING_BUFFER_SIZE) {
                        rollingBuffer =
                            rollingBuffer.substring(rollingBuffer.length - ROLLING_BUFFER_SIZE)
                    }
                    // one input at a time
                    while (inputState.value is InputState.C.None) {
                        val startIndex = rollingBuffer.indexOf(MAGIC_CMD_START)
                        if (startIndex < 0) {
                            break
                        }
                        val endIndex = rollingBuffer.indexOf(MAGIC_CMD_END,
                            startIndex + MAGIC_CMD_START.length)
                        if (endIndex < 0) {
                            // the command might not be completed, check it later
                            break
                        }
                        var cmd = rollingBuffer.substring(startIndex + MAGIC_CMD_START.length,
                            endIndex).trim()

                        if (cmd.startsWith("sudo password request ($token)")) {
                            val msg = cmd.substring(24 + token.length).trim()
                            inputState.postValue(InputState.C.Password(msg.trim()))
                        } else if (cmd.startsWith("reinstall($token):")) {
                            val msg = cmd.substring(12 + token.length).trim()
                            inputState.postValue(InputState.C.ReinstallConfirmation(msg))
                        } else {
                            //invalid cmd?
                        }

                        rollingBuffer = rollingBuffer.substring(endIndex + MAGIC_CMD_END.length)
                    }
                } catch (e: Exception) {
                    // not relating to process
                }
            }
        }
    }
}