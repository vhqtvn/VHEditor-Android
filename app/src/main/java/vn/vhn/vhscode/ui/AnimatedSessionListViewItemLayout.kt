package vn.vhn.vhscode.ui

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.widget.LinearLayout
import vn.vhn.vhscode.R


class AnimatedSessionListViewItemLayout(
    context: Context?,
    attrs: AttributeSet?
) : LinearLayout(context, attrs) {
    val mPaddingLeftOnActivated =
        resources.getDimensionPixelSize(R.dimen.session_list_view_selected_padding_left)
    private var mCurrentAnimator: ValueAnimator? = null
    override fun setActivated(activated: Boolean) {
        if (activated == this.isActivated) return
        super.setActivated(activated)
        animatePaddingLeft(if (activated) mPaddingLeftOnActivated else 0)
    }

    @Synchronized
    private fun animatePaddingLeft(target: Int) {
        if (mCurrentAnimator != null) {
            mCurrentAnimator?.cancel()
        }
        val animator = ValueAnimator.ofInt(paddingLeft, target)
        mCurrentAnimator = animator
        animator.addUpdateListener { valueAnimator ->
            setPadding(
                valueAnimator.animatedValue as Int,
                paddingTop,
                paddingRight,
                paddingBottom
            )
        }
        animator.duration = 200
        animator.start()

    }
}