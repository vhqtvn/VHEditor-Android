package vn.vhn.vhscode

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Window
import android.webkit.WebView
import kotlinx.android.synthetic.main.activity_vscode.*
import vn.vhn.vhscode.webclient.VSCodeWebClient
import java.io.DataInputStream

class VSCodeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()
        setContentView(R.layout.activity_vscode)
        configureWebView(webView)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowContentAccess = true
        webView.settings.allowFileAccess = true
        webView.settings.allowFileAccessFromFileURLs = true
        webView.webChromeClient = VSCodeWebClient()
    }

    fun runServer() {
        val nodeBinary = applicationContext.getFileStreamPath("node")
        val envHome = nodeBinary?.parentFile?.absolutePath
        val process = Runtime.getRuntime().exec(
            arrayOf(
                applicationContext.getFileStreamPath("node").absolutePath,
                "${envHome}/code-server/release-static",
                "--auth",
                "none"
            ),
            arrayOf(
                "HOME=${envHome}",
                "LD_LIBRARY_PATH=${envHome}",
                "PORT=13337"
            )
        )
        val stream = DataInputStream(process.inputStream);
        val bufSize = kConfigStreamBuferSize
        val buffer = ByteArray(bufSize)
        var outputBuffer = ""
        var serverStarted = false
        while (process.isAlive) {
            val size = stream.read(buffer)
            if (size <= 0) continue;
            val currentBuffer = String(buffer, 0, size)
            Log.d("VHSServerOutput", currentBuffer)
            if (!serverStarted) {
                outputBuffer += currentBuffer
                if (outputBuffer.indexOf("HTTP server listening on") >= 0) {
                    serverStarted = true
                    outputBuffer = ""
                    runOnUiThread {
//                        findViewById<WebView>(R.id.webview).loadUrl("http://127.0.0.1:13337/?_=" + System.currentTimeMillis())
                    }
                }
            }
        }
    }
}
