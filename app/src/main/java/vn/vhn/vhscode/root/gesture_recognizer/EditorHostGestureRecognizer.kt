package vn.vhn.vhscode.root.gesture_recognizer

import android.content.Context
import android.util.TypedValue
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import vn.vhn.virtualmouse.MouseView
import java.lang.Integer.max
import kotlin.math.abs

const val TAP_MS = 300

class EditorHostGestureRecognizer(
    val mContext: Context,
    val mListener: EditorHostGestureRecognizerListener,
) {
    interface EditorHostGestureRecognizerListener {
        fun onGestureTap(touches: Int)
        fun onGestureSwipeX(touches: Int, relativeDelta: Float, absoluteValue: Float): Boolean
        fun onGestureSwipeY(touches: Int, relativeDelta: Float, absoluteValue: Float): Boolean
        fun onGestureEnd(motionEvent: MotionEvent)
    }


    private var mStartTouchTime = 0L
    private var mTapValid = false
    private var mTapFingerCnt = 1
    private var mInHandledGesture = false

    private var mCurrentMotionEvent: MotionEvent? = null
    private var mLastMotionEvent: MotionEvent? = null

    private var mAccumulatedSwipeX = FloatArray(77)
    private var mAccumulatedSwipeY = FloatArray(77)
    private var shouldReportSwipeX = BooleanArray(77)
    private var shouldReportSwipeY = BooleanArray(77)

    private var dipInPixels = 0f
    private var kTouchSlop = 10f
    private var kMaxTapMovementSquared = 1f
    fun initialize() {
        dipInPixels = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            1f,
            mContext.resources.displayMetrics
        )
        kTouchSlop = ViewConfiguration.get(mContext).scaledTouchSlop * 1f
        kMaxTapMovementSquared = dipInPixels * dipInPixels * 400f
    }

    var mInitialMotionEvent = mutableMapOf<Int, MotionEvent>()
    private fun handleTouchEvent(): Boolean {
        val event = mCurrentMotionEvent!!
        if (mTapValid && event.eventTime > mStartTouchTime + TAP_MS)
            mTapValid = false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mStartTouchTime = event.eventTime
                mInitialMotionEvent.forEach { (_, motionEvent) -> motionEvent.recycle() }
                mInitialMotionEvent.clear()
                mTapValid = true
                mInHandledGesture = false
                mAccumulatedSwipeX[1] = 0F
                mAccumulatedSwipeY[1] = 0F
                shouldReportSwipeX[1] = false
                shouldReportSwipeY[1] = false
                mTapFingerCnt = 1
                val pointerId = event.getPointerId(event.actionIndex)
                mInitialMotionEvent[pointerId]?.recycle()
                mInitialMotionEvent[pointerId] = MotionEvent.obtain(event)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                mTapFingerCnt = max(mTapFingerCnt, event.pointerCount)
                for (i in (mLastMotionEvent!!.pointerCount + 1)..event.pointerCount) {
                    mAccumulatedSwipeX[i] = 0F
                    mAccumulatedSwipeY[i] = 0F
                    shouldReportSwipeX[i] = false
                    shouldReportSwipeY[i] = false
                }
                val pointerId = event.getPointerId(event.actionIndex)
                mInitialMotionEvent[pointerId]?.recycle()
                mInitialMotionEvent[pointerId] = MotionEvent.obtain(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (mTapValid) {
                    val pointerId = event.getPointerId(event.actionIndex)
                    mInitialMotionEvent[pointerId]?.also { initialEvent ->
                        if (!allowedTapMovement(initialEvent, event, pointerId))
                            mTapValid = false
                    }
                }
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
                            if (abs(mAccumulatedSwipeX[touches]) >= kTouchSlop) shouldReportSwipeX[touches] =
                                true
                            if (shouldReportSwipeX[touches] && mListener.onGestureSwipeX(
                                    touches,
                                    combinedDeltaX,
                                    mAccumulatedSwipeX[touches]
                                )
                            ) mInHandledGesture = true
                        }
                        if (combinedDeltaY != 0F) {
                            mAccumulatedSwipeY[touches] += combinedDeltaY
                            if (abs(mAccumulatedSwipeY[touches]) >= kTouchSlop) shouldReportSwipeY[touches] =
                                true
                            if (shouldReportSwipeY[touches] && mListener.onGestureSwipeY(
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
                val pointerId = event.getPointerId(event.actionIndex)
                mInitialMotionEvent[pointerId]?.also { initialEvent ->
                    if (!allowedTapMovement(initialEvent, event, pointerId))
                        mTapValid = false
                    initialEvent.recycle()
                    mInitialMotionEvent.remove(pointerId)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (mInHandledGesture) {
                    mInHandledGesture = false
                    mListener.onGestureEnd(event)
                } else {
                    val pointerId = event.getPointerId(event.actionIndex)
                    mInitialMotionEvent[pointerId]?.also { initialEvent ->
                        if (!allowedTapMovement(initialEvent, event, pointerId))
                            mTapValid = false
                        initialEvent.recycle()
                        mInitialMotionEvent.remove(pointerId)
                    }
                    if (mTapValid) {
                        mTapValid = false
                        mListener.onGestureTap(mTapFingerCnt)
                    }
                }
            }
        }
        return mInHandledGesture
    }

    private fun allowedTapMovement(
        initialEvent: MotionEvent,
        motionEvent: MotionEvent,
        pointerId: Int,
    ): Boolean {
        val initialIndex = initialEvent.findPointerIndex(pointerId)
        val secondIndex = motionEvent.findPointerIndex(pointerId)
        val dx = initialEvent.getX(initialIndex) - motionEvent.getX(secondIndex)
        val dy = initialEvent.getY(initialIndex) - motionEvent.getY(secondIndex)
        return dx * dx + dy * dy <= kMaxTapMovementSquared
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