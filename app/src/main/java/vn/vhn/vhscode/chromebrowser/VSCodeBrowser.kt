package vn.vhn.vhscode.chromebrowser

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.PointerIcon
import android.webkit.WebView
import java.lang.reflect.Method

class VSCodeBrowser(context: Context, attrs: AttributeSet?) : WebView(context, attrs) {
    companion object {
        val TAG = "VSCodeBrowser"
        lateinit var mWebviewProviderMethod: Method

        init {
            mWebviewProviderMethod = WebView::class.java.getDeclaredMethod("getWebViewProvider")
            mWebviewProviderMethod.isAccessible = true
        }
    }


    override fun setPointerIcon(pointerIcon: PointerIcon?) {
        super.setPointerIcon(pointerIcon)
        Log.d(TAG, "Pointer icon changed")
    }

//    override fun onKeyPreIme(keyCode: Int, event: KeyEvent?): Boolean {
//        Log.d("MouseViewZ", "onKeyPreIme " + keyCode + " : " + event)
//        return super.onKeyPreIme(keyCode, event)
//    }
//
//    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
//        val r = super.onGenericMotionEvent(event)
//        Log.d("MouseViewZ", "onGeneric " + r + " : " + event)
//        return r
//    }
//
//    override fun onTouchEvent(event: MotionEvent?): Boolean {
//        val r = super.onTouchEvent(event)
//        Log.d("MouseViewZ", "onTouchEvent " + r + " : " + event)
//        return r
//    }
//

    override fun onHoverEvent(event: MotionEvent?): Boolean {
        val r = super.onHoverEvent(event)
//        val provider: WebView.WebViewProvider = mWebviewProviderMethod.invoke(this)
//        val m: AccessibilityManager = AccessibilityManager()
//        Log.d("MouseViewZ", "onHoverEvent " + r + " : " + event)
        return r
    }
//
//    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
//        Log.d("MouseViewZ", "onKeyDown " + keyCode + " : " + event)
//        return super.onKeyDown(keyCode, event)
//    }
}