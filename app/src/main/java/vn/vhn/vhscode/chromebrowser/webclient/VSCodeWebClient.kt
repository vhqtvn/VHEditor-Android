package vn.vhn.vhscode.chromebrowser.webclient

import android.util.Log
import android.view.KeyEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import vn.vhn.vhscode.CodeServerService

class VSCodeWebClient : WebViewClient() {
    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        val HOME = CodeServerService.homePath(view!!.context)
        CodeServerService.getBootjs(view!!.context)
        view!!.context
        view!!.evaluateJavascript(CodeServerService.getBootjs(view!!.context)
            , null
        );
    }
}