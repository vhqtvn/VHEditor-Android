package vn.vhn.vhscode

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.*
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import kotlinx.android.synthetic.main.activity_vscode.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import vn.vhn.vhscode.chromebrowser.VSCodeJSInterface
import vn.vhn.vhscode.chromebrowser.webclient.VSCodeWebChromeClient
import vn.vhn.vhscode.chromebrowser.webclient.VSCodeWebClient
import vn.vhn.vhscode.generic_dispatcher.BBKeyboardEventDispatcher
import vn.vhn.vhscode.generic_dispatcher.IGenericEventDispatcher

class VSCodeActivity : AppCompatActivity() {
    companion object {
        val kConfigUseHardKeyboard = "use_hardkb";
        val kConfigUrl = "url";
        val kConfigScreenAlive = "screen_alive";
        val TAG = "VSCodeActivity"
    }

    var useHardKeyboard = false
    var genericMotionEventDispatcher: IGenericEventDispatcher? = null
    var jsInterface = VSCodeJSInterface()

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
            genericMotionEventDispatcher = BBKeyboardEventDispatcher(jsInterface)
        } else if (android.os.Build.MODEL.matches(Regex("STV100-[0-9]+"))) { //Priv
            genericMotionEventDispatcher = BBKeyboardEventDispatcher(jsInterface)
        }
        if (genericMotionEventDispatcher != null) {
            genericMotionEventDispatcher!!.initializeForTarget(this, webView)
        }
    }

    override fun onResume() {
        super.onResume()
        val keepalive = getBooleanParameter(kConfigScreenAlive, false)
        if (keepalive == true) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        webView.settings.setAppCachePath("/data/data/vn.vhn.vsc/cache")
        webView.settings.setAppCacheEnabled(true)
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        webView.settings.allowFileAccessFromFileURLs = true
        webView.webChromeClient = VSCodeWebChromeClient()
        webView.settings.fixedFontFamily = "vscode-monospace"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (useHardKeyboard) {
                webView.focusable = View.NOT_FOCUSABLE
            } else {
                webView.focusable = View.FOCUSABLE_AUTO
            }
        }
        val url: String = getStringParameter(
            kConfigUrl,
            "https://127.0.0.1:13337/?_=" + System.currentTimeMillis()
        )!!
        if (url.startsWith("https://127.0.0.1:13337")) {
            webView.clearCache(true)
            webView.clearSslPreferences()
        }
        webView.webViewClient = VSCodeWebClient(url)
        webView.addJavascriptInterface(jsInterface, "_vn_vhn_vscjs_")
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

    fun getStringParameter(name: String, default: String? = null): String? {
        var res = intent.getStringExtra(name)
        if (intent.data != null) {
            val dataUri = Uri.parse(intent.data.toString())
            val paramValue = dataUri.getQueryParameter(name)
            if (paramValue != null) res = paramValue
        }
        if (res == null) res = default
        return res
    }

    fun getBooleanParameter(name: String, default: Boolean? = null): Boolean? {
        var res: Boolean? = null
        if (intent.hasExtra(name)) {
            res = intent.getBooleanExtra(name, false)
        }
        if (intent.data != null) {
            val dataUri = Uri.parse(intent.data.toString())
            val paramValue = dataUri.getQueryParameter(name)?.toLowerCase()
            if (paramValue != null)
                res = paramValue != "" && paramValue != "0" && paramValue != "false"
        }
        if (res == null) res = default
        return res
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            finish()
        }
    }
}
