package vn.vhn.vhscode.chromebrowser

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.PointerIcon
import android.webkit.WebView
import vn.vhn.virtualmouse.PointerIconChangedListen
import java.lang.reflect.Method

class VSCodeBrowser(context: Context, attrs: AttributeSet?) : WebView(context, attrs),
    PointerIconChangedListen {
    companion object {
        val TAG = "VSCodeBrowser"
        lateinit var mWebviewProviderMethod: Method

        init {
            mWebviewProviderMethod = WebView::class.java.getDeclaredMethod("getWebViewProvider")
            mWebviewProviderMethod.isAccessible = true
        }
    }

    private var mPointerIconChangedListener: PointerIconChangedListen.Listener? = null

    override fun setPointerIconChangedListener(listener: PointerIconChangedListen.Listener?) {
        mPointerIconChangedListener = listener
    }

    override fun setPointerIcon(pointerIcon: PointerIcon?) {
        super.setPointerIcon(pointerIcon)
        mPointerIconChangedListener?.onPointerIconChanged(pointerIcon)
    }
}