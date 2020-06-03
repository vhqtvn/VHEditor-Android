package vn.vhn.vhscode

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.*
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import kotlinx.android.synthetic.main.activity_vscode.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import vn.vhn.vhscode.chromebrowser.webclient.VSCodeWebChromeClient
import vn.vhn.vhscode.chromebrowser.webclient.VSCodeWebClient
import vn.vhn.vhscode.generic_dispatcher.BBKeyboardEventDispatcher
import vn.vhn.vhscode.generic_dispatcher.IGenericEventDispatcher

class VSCodeActivity : AppCompatActivity() {
    companion object {
        val kConfigUseHardKeyboard = "use_hardkb";
        val kConfigUrl = "url";
        val TAG = "VSCodeActivity"
    }

    class WebInterface {
    }

    var useHardKeyboard = false
    var genericMotionEventDispatcher: IGenericEventDispatcher? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        useHardKeyboard = intent.getBooleanExtra(kConfigUseHardKeyboard, false)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()
        setContentView(R.layout.activity_vscode)
        configureWebView(webView)
        CoroutineScope(Dispatchers.Main).launch {
            Log.d(TAG, "Started on model ${android.os.Build.MODEL}")
        }
        if (android.os.Build.MODEL.matches(Regex("BB[FB]100-[0-9]+"))) { //Key1,2
            genericMotionEventDispatcher = BBKeyboardEventDispatcher()
        }
        if (genericMotionEventDispatcher != null) {
            genericMotionEventDispatcher!!.initializeForTarget(this, webView)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowContentAccess = true
        webView.settings.allowFileAccess = true
        webView.settings.allowFileAccessFromFileURLs = true
        webView.settings.allowUniversalAccessFromFileURLs = true
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        webView.settings.setAppCachePath("/data/data/vn.vhn.vsc/cache")
        webView.settings.setAppCacheEnabled(true)
        webView.settings.allowFileAccessFromFileURLs = true
        webView.webChromeClient = VSCodeWebChromeClient()
        webView.settings.fixedFontFamily = "vscode-monospace"
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

    override fun dispatchKeyEvent(ev: KeyEvent?): Boolean {
        if (genericMotionEventDispatcher != null && ev != null) {
            if (genericMotionEventDispatcher!!.dispatchKeyEvent(ev))
                return true
        }
        if (webView.dispatchKeyEvent(ev)) return true
        return super.dispatchKeyEvent(ev)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent?): Boolean {
        if (genericMotionEventDispatcher != null && ev != null) {
            if (genericMotionEventDispatcher!!.dispatchGenericMotionEvent(ev))
                return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }
}
