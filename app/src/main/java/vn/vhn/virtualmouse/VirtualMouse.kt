package vn.vhn.virtualmouse

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet


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
        mMouseView = MouseView(rootView.context, targetView).also {
            it.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            it.cursorScale = scaleFactor
            it.id = View.generateViewId()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.focusable = View.NOT_FOCUSABLE
            }
            val params = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT
            )
            rootView.addView(it, params)
            it.initialize()
            val cons = ConstraintSet()
            cons.clone(rootView)
            for (dir in listOf(
                ConstraintSet.LEFT,
                ConstraintSet.TOP,
                ConstraintSet.RIGHT,
                ConstraintSet.BOTTOM
            ))
                cons.connect(it.id, dir, ConstraintSet.PARENT_ID, dir, 0)
            cons.applyTo(rootView)
        }
    }

    fun disable() {
        mMouseView?.also {
            val parent = it.parent as? ViewGroup
            parent?.removeView(it)
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