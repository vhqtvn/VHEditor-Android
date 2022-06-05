package vn.vhn.vhscode.root.helpers

import android.os.SystemClock
import android.view.MotionEvent
import androidx.drawerlayout.widget.DrawerLayout

class DrawerLayoutDrawerController(
    private val mDrawerLayout: DrawerLayout
) {
    private var mCurrentDownTime = 0L
    private var mCurrentHasTouch = false
    private var mLastOffset = 0F
    private var mCurrX = 0F
    private var mCurrY = 0F

    private fun createEvent(
        action: Int,
        x: Float,
        y: Float,
    ): MotionEvent {
        return MotionEvent.obtain(
            mCurrentDownTime,
            SystemClock.uptimeMillis(),
            action,
            x, y, 0
        )
    }

    fun updateDrawerOffset(offset: Float) {
        if (offset == 0F) return
        if (!mCurrentHasTouch || ((offset >= 0F) != (mLastOffset >= 0F))) {
            if (mCurrentHasTouch) {
                cancel()
            }
            mCurrentHasTouch = true
            mCurrX = mDrawerLayout.x + 1
            mCurrY = mDrawerLayout.y + mDrawerLayout.height / 2
            mCurrentDownTime = SystemClock.uptimeMillis()
            val downMotionEvent = createEvent(
                MotionEvent.ACTION_DOWN,
                mCurrX, mCurrY
            )
            mDrawerLayout.dispatchTouchEvent(downMotionEvent)
            downMotionEvent.recycle()
            mLastOffset = 0F
        }
        mCurrX += offset - mLastOffset
        mLastOffset = offset

        val moveMotionEvent = createEvent(
            MotionEvent.ACTION_MOVE,
            mCurrX, mCurrY
        )
        mDrawerLayout.dispatchTouchEvent(moveMotionEvent)
        moveMotionEvent.recycle()
    }

    fun cancel() {
        if (mCurrentHasTouch) {
            mCurrentHasTouch = false
            mCurrX = mDrawerLayout.x + 1
            val moveMotionEvent = createEvent(
                MotionEvent.ACTION_MOVE,
                mCurrX, mCurrY
            )
            mDrawerLayout.dispatchTouchEvent(moveMotionEvent)
            moveMotionEvent.recycle()
            val upMotionEvent = createEvent(
                MotionEvent.ACTION_UP,
                mCurrX, mCurrY
            )
            mDrawerLayout.dispatchTouchEvent(upMotionEvent)
            upMotionEvent.recycle()
            mLastOffset = 0F
        }
    }

    fun commitDrawerPosition() {
        if (mCurrentHasTouch) {
            mCurrentHasTouch = false
            val upMotionEvent = createEvent(
                MotionEvent.ACTION_UP,
                mCurrX, mCurrY
            )
            mDrawerLayout.dispatchTouchEvent(upMotionEvent)
            upMotionEvent.recycle()
            mLastOffset = 0F
        }
    }
}