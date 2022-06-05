package vn.vhn.vhscode.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout


@SuppressLint("ViewConstructor")
class OverlayGuideView(
    val mContext: Activity,
    val mTarget: View,
) : CoordinatorLayout(mContext) {
    private val mTextView = TextView(mContext).apply {
        gravity = Gravity.CENTER
        setBackgroundColor(0x80FFFFFF.toInt())
        setTextColor(Color.BLACK)
    }

    init {
        val lp = CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT,
            CoordinatorLayout.LayoutParams.MATCH_PARENT
        )
        addView(mTextView, lp)
        isClickable = false
        setBackgroundColor(Color.TRANSPARENT)
    }

    val textView: TextView
        get() = mTextView

    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        updateLocation()
    }

    fun updateLocation() {
        val locationTarget = IntArray(2)
        mTarget.getLocationOnScreen(locationTarget)
        (mTextView.layoutParams as CoordinatorLayout.LayoutParams).setMargins(
            locationTarget[0],
            locationTarget[1],
            0, 0
        )
        mTextView.requestLayout()
    }

    private fun removeFromParent() {
        if (parent != null) (parent as ViewGroup).removeView(this)
    }

    fun show() {
        removeFromParent()
        updateLocation()
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        (mContext.window.decorView as ViewGroup).addView(this)
        val startAnimation = AlphaAnimation(0.0f, 1.0f)
        startAnimation.duration = 400
        startAnimation.fillAfter = true
        startAnimation(startAnimation)
        viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
    }

    fun dismiss() {
        viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        val endAnimation = AlphaAnimation(1.0f, 0.0f)
        endAnimation.duration = 300
        endAnimation.fillAfter = true
        endAnimation.setAnimationListener(object: Animation.AnimationListener {
            override fun onAnimationStart(p0: Animation?) {
            }

            override fun onAnimationEnd(p0: Animation?) {
                removeFromParent()
            }

            override fun onAnimationRepeat(p0: Animation?) {
            }
        })
        startAnimation(endAnimation)
    }
}