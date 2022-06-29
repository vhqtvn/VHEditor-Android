package vn.vhn.vhscode.root.codeserver

import android.app.Activity
import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import vn.vhn.vhscode.CodeServerService
import vn.vhn.vhscode.R
import java.io.File
import java.io.IOException
import java.lang.Exception

class CodeServerManager {
    companion object {
        fun runInstallServer(context: Context, onFinished: () -> Unit) {
            CoroutineScope(Dispatchers.Main).launch {
                var progressBar: ProgressBar
                var progressText: TextView
                val dialog = Dialog(context).apply {
                    setCancelable(false)
                    setContentView(R.layout.progress_dialog)
                    progressBar = findViewById(R.id.progressBar)
                    progressText = findViewById(R.id.progressText)
                    setTitle(R.string.extracting)
                }
                try {
                    var currentProgress: Int = 0
                    var progressMax: Int = 0
                    var finished = false
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
                                    progressText.text = "%d/%d".format(uiProgress, uiProgressMax)
                                }
                            }
                        }
                    }
                    val progressChannel = Channel<Pair<Int, Int>>()
                    dialog.show()
                    dialog.window!!.setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT
                    )
                    launch {
                        delay(1000)
                        CodeServerService.extractServer(context, progressChannel)
                        CoroutineScope(Dispatchers.Main).launch { onFinished() }
                        finished = true
                        progressChannel.close()
                    }
                    for ((progress, max) in progressChannel) {
                        currentProgress = progress
                        progressMax = max
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, e.toString(), Toast.LENGTH_LONG).show()
                } finally {
                    dialog.dismiss()
                }
            }
        }

        fun onResetRootFS(context: Activity) {
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
                        }
                    }
                }.start()
            }
        }
    }
}