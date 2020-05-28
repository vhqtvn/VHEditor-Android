package vn.vhn.vhscode

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_vscode.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import vn.vhn.vhscode.chromebrowser.webclient.VSCodeWebChromeClient
import vn.vhn.vhscode.chromebrowser.webclient.VSCodeWebClient
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream

class MainActivity : AppCompatActivity() {
    companion object {
        val kCurrentServerVersion = "202005271607"
        val kPrefHardKeyboard = "hardkb"
    }

    var startServerObserver: Observer<Boolean>? = null

    private class VHSWebViewClient : WebChromeClient() {
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)
        configureUI()
        updateUI()
    }

    override fun onDestroy() {
        if (startServerObserver != null) {
            CodeServerService.liveServerStarted.removeObserver(startServerObserver!!)
            startServerObserver = null
        }
        super.onDestroy()
    }

    suspend fun startServerService() {
        if (CodeServerService.liveServerStarted.value != true) {
            val progressDialog = ProgressDialog(this)
            progressDialog.setTitle(R.string.starting_server)
            progressDialog.setMessage(getString(R.string.please_wait_starting_server))
            progressDialog.setCancelable(false)
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
            progressDialog.show()
            if (startServerObserver == null) {
                startServerObserver = Observer<Boolean> { value ->
                    if (value) {
                        progressDialog.dismiss()
                        if (startServerObserver != null) {
                            CodeServerService.liveServerStarted.removeObserver(startServerObserver!!)
                        }
                        startEditor()
                    }
                }
                CodeServerService.liveServerStarted.observeForever(startServerObserver!!)
            }
            CodeServerService.startService(this)
        } else {
            startEditor()
        }

    }

    fun startEditor() {
        val intent = Intent(this, VSCodeActivity::class.java)
        intent.putExtra(VSCodeActivity.kConfigUseHardKeyboard, chkHardKeyboard.isChecked)
        startActivity(intent)
    }

    fun configureUI() {
        btnInstallServer.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {

                var progressBar: ProgressBar
                var progressText: TextView
                val dialog = Dialog(this@MainActivity).apply {
                    setCancelable(false)
                    setContentView(R.layout.progress_dialog)
                    progressBar = findViewById(R.id.progressBar)
                    progressText = findViewById(R.id.progressText)
                    setTitle(R.string.extracting)
                }
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
                    delay(500)
                    extractServer(progressChannel)
                    finished = true
                    progressChannel.close()
                }
                for ((progress, max) in progressChannel) {
                    currentProgress = progress
                    progressMax = max
                }
                dialog.hide()
            }
        }
        btnStartCode.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                startServerService()
            }
        }
        chkHardKeyboard.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences().edit().putBoolean(kPrefHardKeyboard, isChecked).apply()
        }
    }

    fun updateUI() {
        CoroutineScope(Dispatchers.Main).launch {
            chkHardKeyboard.isChecked = sharedPreferences().getBoolean(kPrefHardKeyboard, true);
            val codeServerVersion =
                withContext(Dispatchers.IO) { getCurrentCodeServerVersion() }
            txtInstalledServerVersion.text = getString(
                R.string.server_version,
                codeServerVersion ?: getString(R.string.not_installed)
            )
            if (codeServerVersion.isNullOrBlank()) {
                btnStartCode.isEnabled = false
                btnInstallServer.text =
                    getString(R.string.install_server)
            } else {
                btnStartCode.isEnabled = true
                if (codeServerVersion < kCurrentServerVersion) btnInstallServer.text =
                    getString(R.string.update_server)
                else btnInstallServer.text = getString(R.string.reinstall_server)
            }
        }
    }

    suspend fun getCurrentCodeServerVersion(): String? {
        val node = getFileStreamPath("node")
        if (!node.exists()) return null
        val dataHome = node.parent.toString()
        val versionFile = File("$dataHome/code-server/VERSION")
        if (versionFile.exists()) {
            val stream = FileInputStream(versionFile)
            val bytes = stream.readBytes()
            stream.close()
            return String(bytes, 0, bytes.size)
        }
        return "202005250000"
    }

    suspend fun extractServer(progressChannel: Channel<Pair<Int, Int>>) {
        copyRawResource(R.raw.libcpp, "libc++_shared.so")
        copyRawResource(R.raw.cs, "cs.tgz")
        val csSourceFile = applicationContext.getFileStreamPath("cs.tgz")
        with(applicationContext.getFileStreamPath("code-server")) {
            if (exists()) deleteRecursively()
        }
        extractTarGz(
            csSourceFile,
            csSourceFile.parentFile,
            progressChannel
        )
        csSourceFile.delete()
        copyRawResource(R.raw.node, "node")
        applicationContext.getFileStreamPath("node").setExecutable(true)
        updateUI()
    }

    fun copyRawResource(resource_id: Int, output_path: String) {
        val inStream = applicationContext.resources.openRawResource(resource_id)
        val outStream = applicationContext.openFileOutput(output_path, Context.MODE_PRIVATE)

        val bufSize = 4096
        val buffer = ByteArray(bufSize)
        while (true) {
            val cnt = inStream.read(buffer)
            if (cnt <= 0) break;
            outStream.write(buffer, 0, cnt)
        }

        inStream.close()
        outStream.close()
    }

    suspend fun extractTarGz(
        archiveFile: File,
        outputDir: File,
        progressChannel: Channel<Pair<Int, Int>>
    ) {
        val bufSize: Int = 4096
        val buffer = ByteArray(bufSize)

        var total = 0

        var reader = TarArchiveInputStream(GZIPInputStream(FileInputStream(archiveFile)))
        var currentEntry = reader.nextTarEntry
        while (currentEntry != null) {
            total += 1
            currentEntry = reader.nextTarEntry
        }

        progressChannel.send(Pair(0, total))

        var currentFileIndex = 0
        reader = TarArchiveInputStream(GZIPInputStream(FileInputStream(archiveFile)))
        currentEntry = reader.nextTarEntry

        while (currentEntry != null) {
            currentFileIndex++
            progressChannel.send(Pair(currentFileIndex, total))
            val outputFile = File(outputDir.absolutePath + "/" + currentEntry.name)
            if (!outputFile.parentFile!!.exists()) {
                outputFile.parentFile!!.mkdirs()
            }
            if (currentEntry.isDirectory) {
                outputFile.mkdirs()
            } else {
                val outStream = FileOutputStream(outputFile)
                while (true) {
                    val size = reader.read(buffer)
                    if (size <= 0) break;
                    outStream.write(buffer, 0, size)
                }
                outStream.close()
            }
            currentEntry = reader.nextTarEntry
        }
    }

    private fun sharedPreferences(): SharedPreferences {
        return getSharedPreferences("main_settings", Context.MODE_PRIVATE)
    }
}
