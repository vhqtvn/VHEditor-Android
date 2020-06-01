package vn.vhn.vhscode.generic_dispatcher

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebView

interface IGenericEventDispatcher {
    fun initializeForTarget(ctx: Context, webView: WebView)

    fun dispatchKeyEvent(ev: KeyEvent): Boolean
    fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean
}