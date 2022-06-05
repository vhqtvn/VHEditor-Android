package vn.vhn.vhscode.chromebrowser

import android.content.Context
import android.net.http.SslCertificate
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebView

class VSCodeBrowser(context: Context, attrs: AttributeSet?) : WebView(context, attrs) {
    override fun getCertificate(): SslCertificate? {
        return super.getCertificate()
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
//    override fun onHoverEvent(event: MotionEvent?): Boolean {
//        val r = super.onHoverEvent(event)
//        Log.d("MouseViewZ", "onHoverEvent " + r + " : " + event)
//        return r
//    }
//
//    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
//        Log.d("MouseViewZ", "onKeyDown " + keyCode + " : " + event)
//        return super.onKeyDown(keyCode, event)
//    }
}