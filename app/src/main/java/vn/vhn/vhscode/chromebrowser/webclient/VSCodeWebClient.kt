package vn.vhn.vhscode.chromebrowser.webclient

import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import vn.vhn.vhscode.CodeServerService

class VSCodeWebClient : WebViewClient() {
    companion object {
        val TAG = "VSCodeWebClient"
    }

    var resource_html = Regex("\\.html?(\\?|\$)")
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        view?.apply {
            CodeServerService.getBootjs(context)
            context
            evaluateJavascript(
                CodeServerService.getBootjs(context)
                , null
            )
        }
    }


    override fun onLoadResource(view: WebView?, url: String?) {
        super.onLoadResource(view, url)
        if (url != null && url.contains(resource_html)) {
            Log.d(TAG, "Loading url $url")
            view!!.evaluateJavascript("setTimeout(window.vscode_boot_frames,500)", null)
        }
    }
}