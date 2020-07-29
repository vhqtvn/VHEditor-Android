package vn.vhn.vhscode.chromebrowser

import android.content.Context
import android.net.http.SslCertificate
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.webkit.WebView

class VSCodeBrowser(context: Context?, attrs: AttributeSet?) : WebView(context, attrs) {
    override fun getCertificate(): SslCertificate? {
        return super.getCertificate()
    }
}