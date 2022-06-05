package vn.vhn.vhscode.chromebrowser.webclient

import android.webkit.WebChromeClient
import android.webkit.WebView
import vn.vhn.vhscode.root.fragments.VSCodeFragment

class VSCodeWebChromeClient(
    val mFragment: VSCodeFragment
): WebChromeClient() {
    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        mFragment.postTitle(title)
    }
}