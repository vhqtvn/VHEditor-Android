package vn.vhn.vhscode

import android.content.Context
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.Window
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.*
import java.util.zip.GZIPInputStream

class MainActivity : AppCompatActivity() {

    private class VHSWebViewClient : WebChromeClient() {
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        getSupportActionBar()?.hide()
        setContentView(R.layout.activity_main)
        val webView = findViewById<WebView>(R.id.webview)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowContentAccess = true
        webView.settings.allowFileAccess = true
        webView.settings.allowFileAccessFromFileURLs = true
        webView.webChromeClient = VHSWebViewClient()
        AsyncTask.execute {
            extractServer()
        }
    }

    fun extractServer() {
        if (!applicationContext.getFileStreamPath("node").exists()) {
            copyRawResource(R.raw.node, "node")
            applicationContext.getFileStreamPath("node").setExecutable(true)
            copyRawResource(R.raw.libcpp, "libc++_shared.so")
            copyRawResource(R.raw.cs, "cs.tgz")
            val csSourceFile = applicationContext.getFileStreamPath("cs.tgz")
            extractTarGz(
                csSourceFile,
                csSourceFile.parentFile
            )
        }
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

    fun extractTarGz(archiveFile: File, outputDir: File) {
        val bufSize: Int = 4096
        val buffer = ByteArray(bufSize)

        var total = 0

        var reader = TarArchiveInputStream(GZIPInputStream(FileInputStream(archiveFile)))
        var currentEntry = reader.nextTarEntry
        while (currentEntry != null) {
            total += 1
            currentEntry = reader.nextTarEntry
        }

        var currentFileIndex = 0
        reader = TarArchiveInputStream(GZIPInputStream(FileInputStream(archiveFile)))
        currentEntry = reader.nextTarEntry

        while (currentEntry != null) {
            currentFileIndex++
            var fileName = outputDir.absolutePath
            if (currentFileIndex % 100 == 0)
                Log.i(
                    "VHSMainActivity",
                    " extract " + currentFileIndex + "/" + total + ":" + currentEntry.name
                )
            val outputFile = File(outputDir.absolutePath + "/" + currentEntry.name)
            if (!outputFile.parentFile.exists()) {
                outputFile.parentFile.mkdirs()
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
}
