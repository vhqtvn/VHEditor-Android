package vn.vhn.virtualmouse

import android.Manifest.permission.SYSTEM_ALERT_WINDOW
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.*
import android.webkit.WebView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import vn.vhn.vhscode.R


class VirtualMouse() {
    companion object {
        val TAG = "VirtualMouse"
    }

    private var mRootView: View? = null
    private var mMouseView: MouseView? = null

    val isEnabled
        get() = mMouseView?.parent != null

    fun enable(rootView: View, targetView: WebView, scaleFactor: Float) {
        if (MouseView.actionButtonOffset < 0) {
            Toast.makeText(rootView.context,
                "Failed to obtain actionButton offset",
                Toast.LENGTH_SHORT)
                .show()
            return
        }
        mRootView = rootView
        val rootView = mRootView as? ConstraintLayout ?: return
        if (mMouseView != null && mMouseView!!.parent == rootView) {
            return
        }
        disable()
        mMouseView = MouseView(rootView.context, targetView)
        mMouseView!!.cursorScale = scaleFactor
        mMouseView!!.id = View.generateViewId()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mMouseView!!.focusable = View.NOT_FOCUSABLE
        }
        val params = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.MATCH_PARENT
        )
        rootView.addView(mMouseView, params)
        mMouseView!!.initialize()
        val cons = ConstraintSet()
        cons.clone(rootView)
        for (dir in listOf(
            ConstraintSet.LEFT,
            ConstraintSet.TOP,
            ConstraintSet.RIGHT,
            ConstraintSet.BOTTOM
        ))
            cons.connect(mMouseView!!.id, dir, ConstraintSet.PARENT_ID, dir, 0)
        cons.applyTo(rootView)
    }

    fun disable() {
        if (mMouseView != null) {
            val parent = mMouseView?.parent as? ViewGroup
            parent?.removeView(mMouseView)
            mMouseView = null
        }
    }

    fun moveMouseTo(x: Float, y: Float) {
        mMouseView?.update(x, y)
        mMouseView?.postInvalidate()
    }

    fun setMouseScale(factor: Float) {
        mMouseView?.cursorScale = factor
    }
}