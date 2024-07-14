package vn.vhn.vhscode.root.codeserver

import android.app.Activity
import android.app.Dialog
import android.app.ProgressDialog
import android.content.ClipData
import android.content.ClipData.Item
import android.content.ClipboardManager
import android.content.Context
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import vn.vhn.vhscode.CodeServerService
import vn.vhn.vhscode.R
import java.io.File
import java.io.IOException
import java.util.*

class CodeServerManager {
    companion object {
        fun runInstallServer(context: Context, onFinished: () -> Unit) {
            CoroutineScope(Dispatchers.Main).launch {
                var progressBar: ProgressBar
                var progressText: TextView
                var progressLog: TextView
                val dialog = Dialog(context).apply {
                    setCancelable(false)
                    setContentView(R.layout.progress_dialog)
                    progressBar = findViewById(R.id.progressBar)
                    progressText = findViewById(R.id.progressText)
                    progressLog = findViewById(R.id.progressLog)
                    progressLog.setHorizontallyScrolling(true)
                    setTitle(R.string.extracting)
                }
                var progressLogText = ""
                progressLog.setOnLongClickListener {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData(null,
                            arrayOf("text/plain"),
                            Item(progressLogText)))
                    Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                    return@setOnLongClickListener false
                }
                try {
                    var currentProgress: Int = 0
                    var progressMax: Int = 0
                    var finished = false
                    var progressLogDisplay = LinkedList<String>()
                    var progressLogLocker = progressLogDisplay
                    var progressLogTextChanged = false
                    var isError = false
                    launch {
                        var uiProgress: Int = -1
                        var uiProgressMax: Int = -1
                        while (!finished) {
                            delay(50)
                            if (uiProgress != currentProgress || uiProgressMax != progressMax) {
                                uiProgress = currentProgress
                                uiProgressMax = progressMax
                                withContext(Dispatchers.Main) {
                                    progressBar.progress =
                                        currentProgress * progressBar.max / maxOf(1, progressMax)
                                    progressText.text =
                                        "code-server: %d/%d".format(uiProgress, uiProgressMax)
                                }
                            }
                            if (progressLogTextChanged) {
                                withContext(Dispatchers.Main) {
                                    synchronized(progressLogLocker) {
                                        progressLog.text = progressLogDisplay.joinToString("\n")
                                        progressLogTextChanged = false
                                    }
                                }
                            }
                        }
                    }
                    val progressChannel = Channel<Pair<Pair<Int, Int>?, String?>>()
                    dialog.show()
                    dialog.window!!.setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT
                    )
                    launch {
                        progressChannel.send(Pair(null, "Starting..."))
                        delay(100)
                        try {
                            CodeServerService.extractServer(context, progressChannel)
                            CoroutineScope(Dispatchers.Main).launch { onFinished() }
                            finished = true
                            progressChannel.close()
                        } catch (e: Exception) {
                            isError = true
                            progressChannel.send(Pair(null,
                                "Exception: ${e.stackTraceToString()}"))
                            delay(300)
                            finished = true
                        }
                    }
                    for ((progress, msg) in progressChannel) {
                        progress?.apply {
                            currentProgress = first
                            progressMax = second
                        }
                        msg?.apply {
                            synchronized(progressLogLocker) {
                                progressLogText += msg + "\n"
                                progressLogDisplay.add(msg)
                                if (progressLogDisplay.size > 10) progressLogDisplay.removeFirst()
                                progressLogTextChanged = true
                            }
                        }
                    }
                    if (isError) {
                        withContext(Dispatchers.Main) {
                            progressLog.text =
                                progressLog.text.toString() + context.resources.getString(R.string.long_press_to_copy_full)
                            Toast.makeText(context,
                                R.string.error_failed_to_extract,
                                Toast.LENGTH_LONG).show()
                            progressLog.setTextColor(ContextCompat.getColor(context,
                                com.termux.shared.R.color.red_error))
                            dialog.setCancelable(true)
                        }
                    } else {
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, e.toString(), Toast.LENGTH_LONG).show()
                    withContext(Dispatchers.Main) {
                        progressLog.text =
                            progressLog.text.toString() + context.resources.getString(R.string.long_press_to_copy_full)
                        progressLog.setTextColor(ContextCompat.getColor(context, com.termux.shared.R.color.red_error))
                        dialog.setCancelable(true)
                    }
                }
            }
        }

        fun onResetRootFS(context: Activity, whenDone: (() -> Unit)?) {
            context.runOnUiThread {
                @Suppress("DEPRECATION")
                val progress = ProgressDialog.show(
                    context,
                    null,
                    context.getString(R.string.deleting),
                    true,
                    false
                )
                Thread {
                    try {
                        val homeAbsolute = File(CodeServerService.HOME_PATH).absolutePath
                        File(CodeServerService.ROOT_PATH).list()?.forEach {
                            if (it == "." || it == "..") return@forEach
                            val f = File(CodeServerService.ROOT_PATH + "/" + it)
                            if (f.absolutePath == homeAbsolute) return@forEach
                            if (!f.deleteRecursively()) throw IOException("Remove ${f.name} failed")
                        }
                        context.runOnUiThread {
                            Toast.makeText(
                                context,
                                R.string.reset_root_fs_finished,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: IOException) {
                        context.runOnUiThread {
                            Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                        }
                    } finally {
                        context.runOnUiThread {
                            progress.dismiss()
                            if (whenDone != null) whenDone()
                        }
                    }
                }.start()
            }
        }
    }
}