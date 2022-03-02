package vn.vhn.virtualmouse

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import java.util.logging.Handler


class MouseView(context: Context?) : View(context) {
    companion object {
        val TAG = "MouseView"
    }

    private var cx: Float = 200.0f
    private var cy: Float = 200.0f

    private var passthrough = false

    public fun update(x: Float, y: Float) {
        cx = x
        cy = y
    }

    public fun updateDelta(x: Float, y: Float) {
        cx += x
        cy += y
        if (cx < 0) cx = 0.0F
        if (cx > width) cx = width.toFloat()
        if (cy < 0) cy = 0.0F
        if (cy > height) cy = height.toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val paint = Paint()
        paint.style = Paint.Style.FILL
        paint.color = Color.RED
        canvas.drawCircle(cx, cy, 10.0f, paint)
    }

    var ox = 0.0F
    var oy = 0.0F
    var controlPointerId = VirtualMouse.INVALID_POINTER_ID
    var touchStartTime = 0L
    var maxPointers = 0
    override fun onTouchEvent(motionEvent: MotionEvent?): Boolean {
        if (motionEvent == null) return false
        Log.d(TAG, "Touch " + motionEvent.deviceId + "; src = " + motionEvent.source)
        if (passthrough) return false
        if (motionEvent.source == VirtualMouse.FAKE_TOUCH_SOURCE) return false
        val actionMasked = motionEvent.actionMasked
        if (
            actionMasked == MotionEvent.ACTION_DOWN
            || actionMasked == MotionEvent.ACTION_POINTER_DOWN
        ) {
            if (actionMasked == MotionEvent.ACTION_DOWN) {
                touchStartTime = System.currentTimeMillis()
                maxPointers = 1
            }
            maxPointers = maxOf(maxPointers, motionEvent.pointerCount)
            if (controlPointerId == VirtualMouse.INVALID_POINTER_ID) {
                controlPointerId = motionEvent.getPointerId(motionEvent.actionIndex);
                ox = motionEvent.getX(motionEvent.actionIndex)
                oy = motionEvent.getY(motionEvent.actionIndex)
            }
        } else if (actionMasked == MotionEvent.ACTION_MOVE) {
            if (System.currentTimeMillis() > touchStartTime + VirtualMouse.TAP_THRESHOLD_MS)
                if (controlPointerId != VirtualMouse.INVALID_POINTER_ID) {
                    var controlPointerIndex = motionEvent.pointerCount - 1
                    while (controlPointerIndex > 0 && motionEvent.getPointerId(
                            controlPointerIndex
                        ) != controlPointerId
                    )
                        controlPointerIndex--
                    updateDelta(
                        motionEvent.getX(controlPointerIndex) - ox,
                        motionEvent.getY(controlPointerIndex) - oy
                    )
                    postInvalidate()
                    ox = motionEvent.getX(controlPointerIndex)
                    oy = motionEvent.getY(controlPointerIndex)
                }
        } else if (actionMasked == MotionEvent.ACTION_UP
            || actionMasked == MotionEvent.ACTION_CANCEL
            || actionMasked == MotionEvent.ACTION_POINTER_UP
        ) {
            if (System.currentTimeMillis() <= touchStartTime + VirtualMouse.TAP_THRESHOLD_MS) {
                if (maxPointers == 1) {
                    android.os.Handler().postDelayed({
                        passthrough = true
                        val timeStart = SystemClock.uptimeMillis() - 10
                        val mActivity = context as? Activity
                        Toast.makeText(context, "Click", Toast.LENGTH_SHORT).show()
                        val m1 = mActivity?.dispatchTouchEvent(
                            VirtualMouse.fakeMotionEvent(
                                MotionEvent.obtain(
                                    timeStart,
                                    timeStart+100,
                                    MotionEvent.ACTION_UP,
                                    1,
                                    listOf<MotionEvent.PointerProperties>(
                                        MotionEvent.PointerProperties().apply {
                                            id = 0
                                            toolType = MotionEvent.TOOL_TYPE_FINGER
                                        }
                                    ).toTypedArray(),
                                    listOf<MotionEvent.PointerCoords>(
                                        MotionEvent.PointerCoords().apply {
                                            x = MouseView@x
                                            y = MouseView@y
                                            x
                                            pressure = 1.0F
                                            size = 1.0F
                                        }
                                    ).toTypedArray(),
                                    0,
                                    0,
                                    1.0F,
                                    1.0F,
                                    4,
                                    0,
                                    4098,
                                    0
                                )
                            )
                        )
                        Log.d(TAG, "dispatch returned " + m1)
                        if (true)
                            mActivity?.dispatchTouchEvent(
                                VirtualMouse.fakeMotionEvent(
                                    MotionEvent.obtain(
                                        timeStart + 10,
                                        timeStart + 10,
                                        MotionEvent.ACTION_UP,
                                        x,
                                        y,
                                        0
                                    )
                                )
                            )
                        passthrough = false
                    }, 10)
                } else {

                }
            }
            if (motionEvent.getPointerId(motionEvent.actionIndex) == controlPointerId) {
                controlPointerId = VirtualMouse.INVALID_POINTER_ID
            }
        }
        return true
    }
}