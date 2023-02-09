package vn.vhn.vhscode.generic_dispatcher

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import androidx.core.content.ContextCompat.getSystemService
import vn.vhn.vhscode.chromebrowser.VSCodeJSInterface


class BBKeyboardEventDispatcher(val jsInterface: VSCodeJSInterface) : IGenericEventDispatcher {
    companion object {
        val TAG = "BBKeyboardEventDispatcher"
        private val KEYBOARD_WIDTH = 1080.0
        private val KEYBOARD_HEIGHT = 525.0
        private val KB_BTN_WIDTH = KEYBOARD_WIDTH / 10.0
        private val KB_BTN_HEIGHT = KEYBOARD_HEIGHT / 4.0

        private const val LEFT_MODIFICATION_OFFSET = 80.0
        private const val TOP_LEFT_SLIDE_START = 80.0
        private const val BOTTOM_LEFT_SLIDE_END = 200.0

    }

    var mContext: Context? = null
    var mWebView: WebView? = null
    var mInputConnection: BaseInputConnection? = null

    var mAccumulateX = 0.0
    var mAccumulateY = 0.0

    private enum class SpecialHandlingState {
        STATE_NONE,
        STATE_FIRST_TAP,
        STATE_ENABLED
    }

    private var mHandlingState: SpecialHandlingState = SpecialHandlingState.STATE_NONE
    private var mFirstTapDeadline: Long = 0
    private var mMeta: Int = 0
    private var mShift: Int = 0

    override fun initializeForTarget(ctx: Context, webView: WebView) {
        this.mContext = ctx
        this.mWebView = webView
        this.mInputConnection = BaseInputConnection(webView, true)
    }

    private fun setHandlingState(newState: SpecialHandlingState, ev: MotionEvent) {
        if (newState == SpecialHandlingState.STATE_FIRST_TAP) {
            mFirstTapDeadline = ev.eventTime + 600
        }
        if (newState != mHandlingState) {
            Log.d(TAG, "change state " + mHandlingState + " -> " + newState)
            mHandlingState = newState
        }
    }

    override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        var msk = 0
        if (ev.keyCode == KeyEvent.KEYCODE_SHIFT_LEFT) {
            msk = 1
        }
        if (ev.keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            msk = 2
        }
        if (msk != 0) {
            var nextShift = mShift
            if (ev.action == KeyEvent.ACTION_DOWN) nextShift = nextShift or msk
            else if (ev.action == KeyEvent.ACTION_UP) nextShift = nextShift and msk.inv()
            if (nextShift != mShift) {
                mShift = nextShift
                jsInterface.setShiftKeyPressed(mShift != 0)
            }
        }
        return false
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (this.mInputConnection == null) return false
        if (ev.action == MotionEvent.ACTION_UP) return false
        if (mHandlingState == SpecialHandlingState.STATE_FIRST_TAP && (
                    ev.eventTime > mFirstTapDeadline
                            || ev.action == MotionEvent.ACTION_DOWN)
        ) {
            setHandlingState(SpecialHandlingState.STATE_NONE, ev)
        }
//        Log.d(TAG, "ev: " + mHandlingState + " ; " + ev.toString());
        when (mHandlingState) {
            SpecialHandlingState.STATE_NONE ->
                if (ev.action == MotionEvent.ACTION_DOWN && ev.pointerCount == 1
                    && ev.getX() < LEFT_MODIFICATION_OFFSET
                    && ev.getY() < TOP_LEFT_SLIDE_START
                ) {
                    setHandlingState(SpecialHandlingState.STATE_FIRST_TAP, ev)
                }
            SpecialHandlingState.STATE_FIRST_TAP ->
                if (ev.action == MotionEvent.ACTION_MOVE && ev.pointerCount == 1
                    && ev.getX() < LEFT_MODIFICATION_OFFSET
                    && ev.getY() > BOTTOM_LEFT_SLIDE_END
                ) {
                    setHandlingState(SpecialHandlingState.STATE_ENABLED, ev)
                } else if (ev.action == MotionEvent.ACTION_UP) {
                    setHandlingState(SpecialHandlingState.STATE_NONE, ev)
                }
            SpecialHandlingState.STATE_ENABLED ->
                when (true) {
                    (ev.action == MotionEvent.ACTION_MOVE && ev.pointerCount > 1 && ev.historySize > 0) -> {
                        var dx = 0.0
                        var dy = 0.0
                        for (pointerId in 0 until ev.pointerCount) {
                            dx += ev.getX(pointerId) - ev.getHistoricalX(pointerId, 0)
                            dy += ev.getY(pointerId) - ev.getHistoricalY(pointerId, 0)
                        }
                        var minX = ev.getX(0)
                        var maxY = ev.getY(0)
                        for (pointerId in 1 until ev.pointerCount) {
                            minX = Math.min(minX, ev.getX(pointerId))
                            maxY = Math.max(maxY, ev.getY(pointerId))
                        }
                        if (minX > TOP_LEFT_SLIDE_START || maxY < BOTTOM_LEFT_SLIDE_END) {
                            setHandlingState(SpecialHandlingState.STATE_NONE, ev)
                            return true
                        }
                        if (Math.abs(dx) > Math.abs(dy)) {
                            var minY = ev.getY(0)
                            for (pointerId in 1 until ev.pointerCount) {
                                minY = Math.min(minY, ev.getY(pointerId))
                            }
                            val speedMultiplier = (Math.max(minY / KEYBOARD_HEIGHT * 8, 1.0))
                            mAccumulateY = 0.0
                            mAccumulateX += dx * speedMultiplier
                            val nSteps = (mAccumulateX / KB_BTN_WIDTH).toInt()
                            if (nSteps != 0) {
                                if (nSteps > 0) {
                                    for (i in 1..nSteps) sendDownUpKeyEvent(
                                        KeyEvent.KEYCODE_DPAD_RIGHT,
                                        mMeta
                                    )
                                } else {
                                    for (i in 1..-nSteps) sendDownUpKeyEvent(
                                        KeyEvent.KEYCODE_DPAD_LEFT,
                                        mMeta
                                    )
                                }
                                mAccumulateX = 0.0
                            }
                        } else {
                            var maxX = ev.getX(0)
                            for (pointerId in 1 until ev.pointerCount) {
                                maxX = Math.max(maxX, ev.getX(pointerId))
                            }
                            val speedMultiplier =
                                (.5 * (1.0 + Math.max(0.0, maxX - KEYBOARD_WIDTH / 3)
                                        / KEYBOARD_WIDTH * 12))
                            mAccumulateX = 0.0
                            mAccumulateY += dy * speedMultiplier
                            val nSteps = (mAccumulateY / KB_BTN_HEIGHT).toInt()
                            if (nSteps != 0) {
                                if (nSteps > 0) {
                                    for (i in 1..nSteps) sendDownUpKeyEvent(
                                        KeyEvent.KEYCODE_DPAD_DOWN,
                                        mMeta
                                    )
                                } else {
                                    for (i in 1..-nSteps) sendDownUpKeyEvent(
                                        KeyEvent.KEYCODE_DPAD_UP,
                                        mMeta
                                    )
                                }
                                mAccumulateY = 0.0
                            }
                        }
                    }
                    else -> {}
                }
        }

        return true
    }

    private fun sendDownUpKeyEvent(keyCode: Int, metaState: Int) {
        if (this.mInputConnection != null) {
            val eventTime = SystemClock.uptimeMillis()
            this.mInputConnection!!.sendKeyEvent(
                KeyEvent(eventTime, eventTime, 0, keyCode, 0, metaState, -1, 0, 6)
            )
            this.mInputConnection!!.sendKeyEvent(
                KeyEvent(SystemClock.uptimeMillis(), eventTime, 1, keyCode, 0, metaState, -1, 0, 6)
            )
        }
    }

}