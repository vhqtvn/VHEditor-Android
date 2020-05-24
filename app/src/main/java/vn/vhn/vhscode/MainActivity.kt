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
        AsyncTask.execute {
            runServer()
        }
    }

    fun runServer() {
        val proc =
            Runtime.getRuntime().exec("whoami")
        val bufferedReader = BufferedReader(
            InputStreamReader(proc.inputStream)
        )

        var line: String?
        while (bufferedReader.readLine().also { line = it } != null) {
            Log.e("VHRES", "RES:$line")
        }

        val HOME = applicationContext.getFileStreamPath("node").parentFile.absolutePath
        Log.i("VHHOME", "HOME=" + HOME)
        val process = Runtime.getRuntime().exec(
            arrayOf(
                applicationContext.getFileStreamPath("node").absolutePath,
                "${HOME}/code-server/release-static",
                "--auth",
                "none"
            ),
            arrayOf(
                "HOME=${HOME}",
                "LD_LIBRARY_PATH=${HOME}",
                "PORT=13337"
            )
        )
        val stream = DataInputStream(process.getInputStream());
        val bufSize = 4096
        val buffer = ByteArray(bufSize)
        var outputBuffer = ""
        var serverStarted = false
        while (process.isAlive) {
            val size = stream.read(buffer)
            if (size > 0) {
                val currentBuffer = String(buffer, 0, size)
                Log.d("VHSMainActivityOutput", currentBuffer)
                if (!serverStarted) {
                    outputBuffer += currentBuffer
                    if (outputBuffer.indexOf("HTTP server listening on") >= 0) {
                        serverStarted = true
                        runOnUiThread { findViewById<WebView>(R.id.webview).loadUrl("http://127.0.0.1:13337/?_=" + System.currentTimeMillis()) }
                    }
                }
            }
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
