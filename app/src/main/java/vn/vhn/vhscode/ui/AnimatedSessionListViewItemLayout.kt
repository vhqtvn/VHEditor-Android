package vn.vhn.vhscode.ui

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import androidx.transition.*
import vn.vhn.vhscode.R
import vn.vhn.vhscode.ui.transtion.ChangePadding


class AnimatedSessionListViewItemLayout(
    context: Context?,
    attrs: AttributeSet?,
) : LinearLayout(context, attrs) {
    val mPaddingLeftOnActivated = resources.getDimensionPixelSize(R.dimen.session_list_view_selected_padding_left)
    override fun setActivated(activated: Boolean) {
        if (activated == this.isActivated) return
        TransitionManager.beginDelayedTransition(
            this,
            TransitionSet()
                .setDuration(200)
                .addTransition(ChangeBounds())
                .addTransition(Fade())
                .addTransition(ChangePadding())
        )
        super.setActivated(activated)
        alpha = if (activated) 1.0f else 0.35f
        setPadding(
            if (activated) mPaddingLeftOnActivated else 0,
            paddingTop,
            paddingRight,
            paddingBottom
        )
        if (activated) {
            findViewById<View>(R.id.session_meta).visibility = View.VISIBLE
        } else {
            findViewById<View>(R.id.session_meta).visibility = View.GONE
        }
    }
}