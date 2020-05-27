package vn.vhn.vhscode

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.webkit.WebView
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_vscode.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import vn.vhn.vhscode.chromebrowser.webclient.VSCodeWebChromeClient
import vn.vhn.vhscode.chromebrowser.webclient.VSCodeWebClient

class VSCodeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()
        setContentView(R.layout.activity_vscode)
        configureWebView(webView)
        CoroutineScope(Dispatchers.Main).launch {

        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowContentAccess = true
        webView.settings.allowFileAccess = true
        webView.settings.allowFileAccessFromFileURLs = true
        webView.webChromeClient = VSCodeWebChromeClient()
        webView.webViewClient = VSCodeWebClient()
        webView.focusable = View.NOT_FOCUSABLE
        webView.loadUrl("http://127.0.0.1:13337/?_=" + System.currentTimeMillis())
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (webView.dispatchKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }
}
