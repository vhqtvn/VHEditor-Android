package vn.vhn.vhscode.chromebrowser

import android.webkit.JavascriptInterface

class VSCodeJSInterface {
    var _isShiftKeyPressed = false
    fun setShiftKeyPressed(value: Boolean) {
        _isShiftKeyPressed = value
    }

    @JavascriptInterface
    fun isShiftKeyPressed(): Boolean {
        return this._isShiftKeyPressed
    }
}