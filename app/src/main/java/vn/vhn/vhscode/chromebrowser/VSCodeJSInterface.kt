package vn.vhn.vhscode.chromebrowser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.JavascriptInterface
import androidx.core.content.ContextCompat.getSystemService


class VSCodeJSInterface(val mContext: Context) {
    var _isShiftKeyPressed = false
    fun setShiftKeyPressed(value: Boolean) {
        _isShiftKeyPressed = value
    }

    @JavascriptInterface
    fun isShiftKeyPressed(): Boolean {
        return this._isShiftKeyPressed
    }

    // from https://stackoverflow.com/questions/61243646/clipboard-api-call-throws-notallowederror-without-invoking-onpermissionrequest
    @JavascriptInterface
    fun copyToClipboard(text: String?) {
        val clipboard: ClipboardManager? =
            mContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        val clip = ClipData.newPlainText("vheditor-clipboard", text)
        clipboard?.setPrimaryClip(clip)
    }

    @JavascriptInterface
    fun getClipboard(): String {
        val clipboard: ClipboardManager? =
            mContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        return clipboard?.primaryClip?.getItemAt(0)?.text.toString()
    }
}