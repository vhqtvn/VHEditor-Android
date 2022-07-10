package vn.vhn.vhscode.ui.transtion

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.util.Log
import android.view.ViewGroup
import androidx.transition.Transition
import androidx.transition.TransitionValues

class ChangePadding : Transition() {
    override fun captureStartValues(transitionValues: TransitionValues) {
        captureValues(transitionValues)
    }

    override fun captureEndValues(transitionValues: TransitionValues) {
        captureValues(transitionValues)
    }

    private fun captureValues(transitionValues: TransitionValues) {
        val view = transitionValues.view
        transitionValues.values["paddingLeft"] = view.paddingLeft
        transitionValues.values["paddingRight"] = view.paddingRight
        transitionValues.values["paddingTop"] = view.paddingTop
        transitionValues.values["paddingBottom"] = view.paddingBottom
    }

    override fun createAnimator(
        sceneRoot: ViewGroup,
        startValues: TransitionValues?,
        endValues: TransitionValues?,
    ): Animator? {
        if (startValues == null) return null
        if (endValues == null) return null
        val view = startValues.view
        val animatorLeft = ValueAnimator.ofInt(
            startValues.values["paddingLeft"] as Int,
            endValues.values["paddingLeft"] as Int
        )
        val animatorRight = ValueAnimator.ofInt(
            startValues.values["paddingRight"] as Int,
            endValues.values["paddingRight"] as Int
        )
        val animatorTop = ValueAnimator.ofInt(
            startValues.values["paddingTop"] as Int,
            endValues.values["paddingTop"] as Int
        )
        val animatorBottom = ValueAnimator.ofInt(
            startValues.values["paddingBottom"] as Int,
            endValues.values["paddingBottom"] as Int
        )
        val animator = AnimatorSet()
        animator.playTogether(animatorLeft, animatorRight, animatorTop, animatorBottom)
        animatorLeft.addUpdateListener { valueAnimator ->
            view.setPadding(
                animatorLeft.animatedValue as Int,
                animatorTop.animatedValue as Int,
                animatorRight.animatedValue as Int,
                animatorBottom.animatedValue as Int
            )
        }

        return animator
    }
}
