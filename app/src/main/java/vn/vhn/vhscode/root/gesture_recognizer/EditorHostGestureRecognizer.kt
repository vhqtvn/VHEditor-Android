package vn.vhn.vhscode.root.gesture_recognizer

import android.content.Context
import android.util.TypedValue
import android.view.InputDevice
import android.view.MotionEvent
import java.lang.Integer.max
import kotlin.math.abs

const val TAP_MS = 300

class EditorHostGestureRecognizer(
    val mContext: Context,
    val mListener: EditorHostGestureRecognizerListener
) {
    interface EditorHostGestureRecognizerListener {
        fun onGestureTap(touches: Int)
        fun onGestureSwipeX(touches: Int, relativeDelta: Float, absoluteValue: Float): Boolean
        fun onGestureSwipeY(touches: Int, relativeDelta: Float, absoluteValue: Float): Boolean
        fun onGestureEnd(motionEvent: MotionEvent)
    }

//    private val dipInPixels = TypedValue.applyDimension(
//        TypedValue.COMPLEX_UNIT_DIP,
//        1f,
//        mContext.resources.displayMetrics
//    )


    private var mStartTouchTime = 0L
    private var mTapValid = false
    private var mTapFingerCnt = 1
    private var mInHandledGesture = false

    private var mCurrentMotionEvent: MotionEvent? = null
    private var mLastMotionEvent: MotionEvent? = null

    private var mAccumulatedSwipeX = FloatArray(77)
    private var mAccumulatedSwipeY = FloatArray(77)

    private fun handleTouchEvent(): Boolean {
        val event = mCurrentMotionEvent!!
        if (mTapValid && event.eventTime > mStartTouchTime + TAP_MS)
            mTapValid = false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mStartTouchTime = event.eventTime
                mTapValid = true
                mInHandledGesture = false
                mAccumulatedSwipeX[1] = 0F
                mAccumulatedSwipeY[1] = 0F
                mTapFingerCnt = 1
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                mTapFingerCnt = max(mTapFingerCnt, event.pointerCount)
                for (i in (mLastMotionEvent!!.pointerCount + 1)..event.pointerCount) {
                    mAccumulatedSwipeX[i] = 0F
                    mAccumulatedSwipeY[i] = 0F
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val lastEvent = mLastMotionEvent
                val touches = event.pointerCount
                if (lastEvent?.pointerCount == touches) {
                    var combinedDeltaX = 0F
                    var combinedDeltaY = 0F
                    for (i in 0 until touches) {
                        if (event.getPointerId(i) == lastEvent.getPointerId(i)) {
                            val deltaX = event.getX(i) - lastEvent.getX(i)
                            if (abs(deltaX) > abs(combinedDeltaX)) combinedDeltaX = deltaX
                            val deltaY = event.getY(i) - lastEvent.getY(i)
                            if (abs(deltaY) > abs(combinedDeltaY)) combinedDeltaY = deltaY
                        } else {
                            combinedDeltaX = Float.NaN
                            combinedDeltaY = Float.NaN
                            break
                        }
                    }
                    if (combinedDeltaX.isFinite()) {
                        if (combinedDeltaX != 0F) {
                            mAccumulatedSwipeX[touches] += combinedDeltaX
                            if (mListener.onGestureSwipeX(
                                    touches,
                                    combinedDeltaX,
                                    mAccumulatedSwipeX[touches]
                                )
                            ) mInHandledGesture = true
                        }
                        if (combinedDeltaY != 0F) {
                            mAccumulatedSwipeY[touches] += combinedDeltaY
                            if (mListener.onGestureSwipeY(
                                    touches,
                                    combinedDeltaY,
                                    mAccumulatedSwipeY[touches]
                                )
                            ) mInHandledGesture = true
                        }
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
            }
            MotionEvent.ACTION_UP -> {
                if (mInHandledGesture) {
                    mInHandledGesture = false
                    mListener.onGestureEnd(event)
                } else {
                    if (mTapValid) {
                        mTapValid = false
                        mListener.onGestureTap(mTapFingerCnt)
                    }
                }
            }
        }
        return mInHandledGesture
    }

    fun dispatchTouchEvent(event: MotionEvent): Boolean {
        mCurrentMotionEvent?.recycle()
        mCurrentMotionEvent = MotionEvent.obtain(event)
        val handled = handleTouchEvent()
        mLastMotionEvent?.recycle()
        mLastMotionEvent = MotionEvent.obtain(event)
        return handled
    }
}