package vn.vhn.vhscode

import android.annotation.SuppressLint
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
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
    companion object {
        val kConfigUseHardKeyboard = "use_hardkb";
        val kConfigUrl = "url";
    }

    var useHardKeyboard = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        useHardKeyboard = intent.getBooleanExtra(kConfigUseHardKeyboard, false)
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
        if (useHardKeyboard) {
            webView.focusable = View.NOT_FOCUSABLE
        } else {
            webView.focusable = View.FOCUSABLE_AUTO
        }
        var url = intent.getStringExtra(kConfigUrl)
        if (intent.data != null) {
            val dataUri = Uri.parse(intent.data.toString())
            val paramUrl = dataUri.getQueryParameter("url")
            if (paramUrl != null) url = paramUrl
        }
        if (url == null) url = "http://127.0.0.1:13337/?_=" + System.currentTimeMillis()
        webView.loadUrl(url)
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (webView.dispatchKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (webView.onTouchEvent(event)) return true
        return super.onTouchEvent(event)
    }
}
