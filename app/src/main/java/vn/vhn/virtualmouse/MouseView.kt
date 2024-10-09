package vn.vhn.virtualmouse

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.SensorManager
import android.os.*
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.view.MotionEvent.PointerCoords
import android.webkit.WebView
import java.lang.Long.max
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs


class MouseView(
    context: Context,
    private val mTarget: WebView,
) : View(context), PointerIconChangedListen.Listener {
    companion object {
        val TAG = "MouseView"

        val MOUSE_ACTION_CLICK_AFTER_TAP = 0x1001
        val MOUSE_ACTION_FLING = 0x1002
        val MOUSE_ACTION_UPDATE_POINTER = 0x1003
        val PERFORM_HOVER = 0x1004

        val TAP_MS = ViewConfiguration.getTapTimeout()
        val DOUBLE_TAP_MS = ViewConfiguration.getDoubleTapTimeout()

        val SECRET_DEVICE = 0x19238347

        private val kEnableTapTapDrag = true

        var actionButtonOffset: Int = -1
        private var actionButtonIsBigEndian = false

        init {
            var e = MotionEvent.obtain(0, 0, 0, 0F, 0F, 0)
            var p = Parcel.obtain()
            e.writeToParcel(p, Parcelable.PARCELABLE_WRITE_RETURN_VALUE)
            e.recycle()
            var buf = p.marshall()
            p.recycle()

            val candidates = mutableListOf<Int>()
            for (i in 0 until (buf.size - 4)) {
                p = Parcel.obtain()
                val b1 = buf[i]
                val b2 = buf[i + 1]
                val b3 = buf[i + 2]
                val b4 = buf[i + 3]
                buf[i] = 0x43.toByte()
                buf[i + 1] = 0x34.toByte()
                buf[i + 2] = 0x25.toByte()
                buf[i + 3] = 0x16.toByte()
                p.unmarshall(buf, 0, buf.size)
                buf[i] = b1
                buf[i + 1] = b2
                buf[i + 2] = b3
                buf[i + 3] = b4
                p.setDataPosition(0)
                try {
                    e = MotionEvent.CREATOR.createFromParcel(p)
                    if (e.actionButton == 0x43342516) {
                        candidates.add(1 + i)
                    } else if (e.actionButton == 0x16253443) {
                        candidates.add(-(1 + i))
                    }
                    e.recycle()
                } catch (e: Exception) {
                }
            }
            if (candidates.size >= 1) {
                actionButtonOffset = abs(candidates[0]) - 1
                actionButtonIsBigEndian = candidates[0] > 0
            }
        }

        private val DECELERATION_RATE = Math.log(0.78) / Math.log(0.9)
        private const val INFLEXION = 0.35 // Tension lines cross at (INFLEXION, 1)

        private const val START_TENSION = 0.5
        private const val END_TENSION = 1.0
        private const val P1 = START_TENSION * INFLEXION
        private const val P2 = 1.0f - END_TENSION * (1.0f - INFLEXION)

        private var canResolvePointerIcons: Boolean? = null
        private var hackPointerTypeField: Field? = null
        private var hackPointerBitmapField: Field? = null
        private var hackPointerHotpotXField: Field? = null
        private var hackPointerHotpotYField: Field? = null
        private var hackPointerLoadMethod: Method? = null

        @SuppressLint("DiscouragedPrivateApi")
        @Synchronized
        private fun initPointerHack(context: Context) {
            if (canResolvePointerIcons != null) return
            canResolvePointerIcons = false
            try {
                val fiels = PointerIcon::class.java.declaredFields
                val targetFieldType = PointerIcon.TYPE_ALIAS::class.java
                val checkPointerTypes = mutableListOf<Pair<Int, PointerIcon>>()
                val nullIcon = PointerIcon.getSystemIcon(context, PointerIcon.TYPE_NULL)
                for (iconType in listOf(
                    PointerIcon.TYPE_DEFAULT,
                    PointerIcon.TYPE_HAND,
                    PointerIcon.TYPE_TEXT,
                    PointerIcon.TYPE_ZOOM_IN,
                    PointerIcon.TYPE_ZOOM_OUT,
                )) {
                    val icon = PointerIcon.getSystemIcon(context, iconType)
                    if (!icon.equals(nullIcon)) {
                        checkPointerTypes.add(Pair(iconType, icon))
                    }
                }
                if (checkPointerTypes.isEmpty()) {
                    Log.e(TAG, "Cannot find icon type to work")
                    return
                }
                try {
                    hackPointerTypeField = PointerIcon::class.java.getDeclaredField("mType")
                    hackPointerTypeField?.apply { if (!isAccessible) isAccessible = true }
                    canResolvePointerIcons = true
                } catch (e: NoSuchFieldException) {
                }
                if (hackPointerTypeField == null)
                    for (f in fiels) {
                        if (f.type == targetFieldType && !java.lang.reflect.Modifier.isStatic(f.modifiers)) {
                            try {
                                if (!f.isAccessible) f.isAccessible = true
                                var valid = true
                                for ((type, icon) in checkPointerTypes) {
                                    if (f.get(icon) != type) {
                                        valid = false
                                        break
                                    }
                                }
                                if (valid) {
                                    Log.w(TAG, "Found field " + f)
                                    hackPointerTypeField = f
                                    canResolvePointerIcons = true
                                    break
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error: ${e}")
                            }
                        }
                    }
                if (canResolvePointerIcons == true) {
                    try {
                        hackPointerBitmapField = PointerIcon::class.java.getDeclaredField("mBitmap")
                    } catch (e: NoSuchFieldException) {
                        for (f in fiels) {
                            if (f.type == Bitmap::class.java) {
                                hackPointerBitmapField = f
                                break
                            }
                        }
                    }
                    hackPointerBitmapField?.apply { if (!isAccessible) isAccessible = true }
                    Log.d(TAG, "Pointer bitmap field " + hackPointerBitmapField)
                    try {
                        hackPointerHotpotXField =
                            PointerIcon::class.java.getDeclaredField("mHotSpotX")
                        hackPointerHotpotYField =
                            PointerIcon::class.java.getDeclaredField("mHotSpotY")
                    } catch (e: NoSuchFieldException) {
                        var idx = 0
                        for (f in fiels) {
                            if (f.type == Float::class.java) {
                                if (idx == 0) hackPointerHotpotXField = f
                                else if (idx == 1) hackPointerHotpotYField = f
                                if (++idx == 2) break
                                break
                            }
                        }
                        if (idx != 2) {
                            hackPointerHotpotXField = null
                            hackPointerHotpotYField = null
                        }
                    }
                    hackPointerHotpotXField?.apply { if (!isAccessible) isAccessible = true }
                    hackPointerHotpotYField?.apply { if (!isAccessible) isAccessible = true }
                    Log.d(
                        TAG,
                        "Pointer hotspot field " + hackPointerHotpotXField + "; " + hackPointerHotpotYField
                    )
                    try {
                        hackPointerLoadMethod =
                            PointerIcon::class.java.getDeclaredMethod("load", Context::class.java)
                    } catch (e: NoSuchMethodException) {
                    }
                } else {
                    Log.e(TAG, "Cant resolve pointer fields")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Cant resolve pointer fields", e)
            }
        }
    }

    val mHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MOUSE_ACTION_CLICK_AFTER_TAP -> {
                    if (!mTouchState.hasFlag(TouchState.FLAG_POSSIBLE_TAP_DRAG)) {
                        return
                    }
                    mTouchState.reset()
                    mousePerformClick()
                }

                MOUSE_ACTION_FLING -> {
                    mCurrentFlingAnimation?.also { anim ->
                        if (!mousePerformScroll(0f, anim.nextDelta()) || anim.isFinished()) {
                            mCurrentFlingAnimation = null
                            return
                        }
                        sendFlingMessage()
                    }
                }

                MOUSE_ACTION_UPDATE_POINTER -> {
                    mMotionEventToUpdate.getAndSet(null)?.apply {
                        performUpdatePointer(this)
                        recycle()
                    }
                    postUpdateMousePointer(100)
                }

                PERFORM_HOVER -> {
                    performHoverEvent()
                }
            }
        }
    }

    private var cx: Float = 200.0f
    private var cy: Float = 200.0f
    private var dipInPixels = 0f
    private var px2VScroll = 0.01f
    private var px2HScroll = 0.01f
    private var kMaxTapMovementSquared = 1f
    private var kTouchSlop = 10f
    private var kMaxVelocity = 1000f
    private var kMinVelocity = 10f
    private var kFlingFriction = ViewConfiguration.getScrollFriction()
    private var kPhysicalCoeff = 1f
    var mMouseSize: Float = 20f

    fun initialize() {
        dipInPixels = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            1f,
            resources.displayMetrics
        )

        px2VScroll = 1f / TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            80f,
            resources.displayMetrics
        )
//        px2VScroll = px2VScroll

        kMaxTapMovementSquared = dipInPixels * dipInPixels * 400f

        val configuration = ViewConfiguration.get(context)

        kTouchSlop = configuration.scaledTouchSlop * 1f
        kMaxVelocity = configuration.scaledMaximumFlingVelocity * 1f
        kMinVelocity = configuration.scaledMinimumFlingVelocity * 1f

        kPhysicalCoeff = (SensorManager.GRAVITY_EARTH // g (m/s^2)
                * 39.37f // inch/meter
                * dipInPixels * 160f // dp
                * 0.84f)

        mMouseSize = dipInPixels * 10

        initPointerHack(context)

        val t = SystemClock.uptimeMillis()
        val e = mouseObtainEvent(t, t, MotionEvent.ACTION_HOVER_MOVE, cx, cy)
        updatePointer(e)
        e.recycle()

        if (mCurrentMouseBitmap == null) {
            try {
                val icon = if (hackPointerLoadMethod != null) {
                    hackPointerLoadMethod!!.invoke(null, context) as PointerIcon
                } else {
                    PointerIcon.getSystemIcon(
                        context,
                        PointerIcon.TYPE_HAND
                    ) as PointerIcon
                }
                if (icon != null) updatePointer(icon)
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e}")
                hackPointerLoadMethod = null
            }
        }

        if (mTarget is PointerIconChangedListen) {
            mTarget.setPointerIconChangedListener(this)
        }
    }

    public fun update(x: Float, y: Float) {
        cx = x
        cy = y
    }

    val mMousePadding: Float
        get() = if (mCurrentMouseBitmap == null) mMouseSize / 2 else maxOf(
            mCurrentMouseBitmap!!.width,
            mCurrentMouseBitmap!!.height
        ).toFloat()
    val mMouseLeft: Float get() = if (mCurrentMouseBitmap == null) mMouseSize / 2 else mCurrentMouseHotspotX
    val mMouseRight: Float get() = if (mCurrentMouseBitmap == null) mMouseSize / 2 else mCurrentMouseBitmap!!.width - mCurrentMouseHotspotX
    val mMouseTop: Float get() = if (mCurrentMouseBitmap == null) mMouseSize / 2 else mCurrentMouseHotspotY
    val mMouseBottom: Float get() = if (mCurrentMouseBitmap == null) mMouseSize / 2 else mCurrentMouseBitmap!!.height - mCurrentMouseHotspotY

    /**
     * TODO: partial postInvalidate is not working when hardware-accelerated is enabled, so just disable it for now
     */
    fun updateDeltaTimed(triggerTime: Long, x: Float, y: Float) {
//        var left = cx
//        var right = cx
//        var top = cy
//        var bottom = cy
        cx += x
        cy += y
        if (cx < 0) cx = 0.0F
        if (cx > width) cx = width.toFloat()
        if (cy < 0) cy = 0.0F
        if (cy > height) cy = height.toFloat()
//        if (cx > left) right = cx else left = cx
//        if (cy > top) bottom = cy else top = cy
//
//        val padding = mMousePadding
//        left = maxOf(0f, left - mMouseLeft + padding)
//        right = minOf(width.toFloat(), right + mMouseRight + padding)
//        top = maxOf(0f, top - mMouseTop + padding)
//        bottom = minOf(height.toFloat(), bottom + mMouseBottom + padding)
//        postInvalidate(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

        postInvalidate()
    }

    private val cursorPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.RED
        alpha = 128
        xfermode = PorterDuffXfermode(PorterDuff.Mode.XOR)
    }

    private val cursorPaintInner = Paint().apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        xfermode = PorterDuffXfermode(PorterDuff.Mode.XOR)
    }

    private val cursorBitmapPaint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = false
    }

    private val cursorBitmapMatrix = Matrix()

    private var mCursorScale = 1.0f

    var cursorScale: Float
        get() = mCursorScale
        set(value) {
            if (value != mCursorScale) {
                mCursorScale = value; postInvalidate()
            }
        }

    override fun onDraw(canvas: Canvas) {
        val bitmap = mCurrentMouseBitmap
        if (bitmap == null) {
            canvas.drawCircle(cx, cy, mMouseSize / 2, cursorPaint)
            canvas.drawCircle(cx, cy, mMouseSize / 8, cursorPaintInner)
        } else {
            cursorBitmapMatrix.setScale(
                mCursorScale,
                mCursorScale,
                mCurrentMouseHotspotX,
                mCurrentMouseHotspotY
            )
            cursorBitmapMatrix.postTranslate(cx - mCurrentMouseHotspotX, cy - mCurrentMouseHotspotY)
            canvas.drawBitmap(bitmap, cursorBitmapMatrix, cursorBitmapPaint)
        }
    }

    private var mLastResolvedPointerIcon: PointerIcon? = null
    private var mCurrentMouseType: Int = -1
    private var mCurrentMouseBitmap: Bitmap? = null
    private var mCurrentMouseHotspotX: Float = 0f
    private var mCurrentMouseHotspotY: Float = 0f
    private var mMotionEventToUpdate: AtomicReference<MotionEvent?> = AtomicReference(null)

    private fun updatePointer(pointer: PointerIcon) {
        try {
            val type = hackPointerTypeField!!.get(pointer) as Int
            Log.d(TAG, "Resolved to $type")
            if (type != mCurrentMouseType) {
                val loadedPointer = PointerIcon.getSystemIcon(context, PointerIcon.TYPE_ARROW)
                mCurrentMouseType = type
                val bitmap = hackPointerBitmapField!!.get(loadedPointer) as Bitmap?
                if (bitmap != null ) {
                    mCurrentMouseBitmap = bitmap
                    mCurrentMouseHotspotX = hackPointerHotpotXField!!.get(loadedPointer) as Float
                    mCurrentMouseHotspotY = hackPointerHotpotYField!!.get(loadedPointer) as Float
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e}")
            mCurrentMouseType = -1
            mCurrentMouseBitmap = null
        }
    }

    override fun onPointerIconChanged(pointer: PointerIcon?) {
        if (pointer != null && pointer != mLastResolvedPointerIcon) {
            mLastResolvedPointerIcon = pointer
            updatePointer(pointer)
            postInvalidate()
        }
    }

    private var lastUpdateCx = -1234.0f
    private var lastUpdateCy = -1234.0f
    private fun performUpdatePointer(e: MotionEvent) {
        if (lastUpdateCx == cx && lastUpdateCy == cy) return
        lastUpdateCx = cx
        lastUpdateCy = cy
        var buttonState = 0x8000000
        if (mLeftMouseDown) buttonState = buttonState.or(MotionEvent.BUTTON_PRIMARY)
        val t = SystemClock.uptimeMillis()
        var pe = mouseObtainEvent(
            t - 1, t - 1,
            MotionEvent.ACTION_MOVE, cx, cy,
            _buttons = buttonState, _actionButtons = buttonState
        )
        mTarget.onHoverEvent(pe)
        pe.recycle()
    }

    private var mousePointerNextUpdate: AtomicLong = AtomicLong(0)
    private fun postUpdateMousePointer(delayMs: Long) {
        val now = SystemClock.uptimeMillis()
        val x = (now + delayMs).and(3L.inv())
        if (mousePointerNextUpdate.updateAndGet { maxOf(it.or(3L), x) } == x) {
            mHandler.sendEmptyMessageAtTime(MOUSE_ACTION_UPDATE_POINTER, now + delayMs)
        }
    }

    private fun updatePointer(e: MotionEvent, pointerIndex: Int = 0) {
        if (canResolvePointerIcons != true) return
        e.action = MotionEvent.ACTION_HOVER_ENTER
        val ec = MotionEvent.obtain(e)
        mMotionEventToUpdate.getAndUpdate { currentEvent ->
            if (currentEvent == null) {
                postUpdateMousePointer(10)
            } else {
                currentEvent.recycle()
            }
            ec
        }
    }

    private var performHoverEventAction = AtomicInteger(-1)
    private fun performHoverEvent() {
        val action = performHoverEventAction.getAndSet(-1)
        if (action == -1) return
        var buttonState = 0
        if (mLeftMouseDown) buttonState = buttonState.or(MotionEvent.BUTTON_PRIMARY)
        val e = mouseObtainEvent(
            touchStartTime, SystemClock.uptimeMillis(),
            action, cx, cy,
            _buttons = buttonState
        )
        mTarget.onHoverEvent(e)
        updatePointer(e)
        e.recycle()
    }

    private fun dispatchHoverEvent(action: Int) {
        performHoverEventAction.getAndUpdate {
            if (it == -1) {
                mHandler.sendEmptyMessageDelayed(PERFORM_HOVER, 150)
            }
            action
        }
    }

    private fun dispatchMouseAction(action: Int) {
        var buttonState = 0
        if (mLeftMouseDown) buttonState = buttonState.or(MotionEvent.BUTTON_PRIMARY)
        if (action == MotionEvent.ACTION_UP) mousePerformUp(buttonState)
        val e = mouseObtainEvent(
            touchStartTime,
            SystemClock.uptimeMillis(),
            action,
            cx, cy,
            _buttons = buttonState,
        )
        mTarget.onTouchEvent(e)
        updatePointer(e)
        e.recycle()
        if (action == MotionEvent.ACTION_DOWN) mousePerformDown(buttonState)
    }

    override fun onHoverEvent(event: MotionEvent?): Boolean {
        return false
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        return false
    }

    var touchStartTime = 0L
    var maxPointers = 0
    var mInitialMotionEvent = mutableMapOf<Int, MotionEvent>()
    var mLastMotionEvent: MotionEvent? = null
    var mLeftMouseDown: Boolean = false
    private var mVelocityTracker: VelocityTracker = VelocityTracker.obtain()
    private var mActivePointerId: Int = MotionEvent.INVALID_POINTER_ID
    private var mRecentPointerIds = mutableMapOf<Int, Long>()

    val mTouchState = TouchState()

    override fun onTouchEvent(motionEvent: MotionEvent?): Boolean {
        if (motionEvent == null) return false
        if (motionEvent.source == SECRET_DEVICE) {
            return false
        }

        when (motionEvent.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (mCurrentFlingAnimation != null) {
                    mCurrentFlingAnimation = null
                }
                mInitialMotionEvent.forEach { (_, motionEvent) -> motionEvent.recycle() }
                mInitialMotionEvent.clear()
                mVelocityTracker.clear()
                mVelocityTracker.addMovement(motionEvent)
                touchStartTime = SystemClock.uptimeMillis()
                maxPointers = 1
                val pointerId = motionEvent.getPointerId(0)
                mActivePointerId = pointerId
                mInitialMotionEvent[pointerId]?.recycle()
                mInitialMotionEvent[pointerId] = MotionEvent.obtain(motionEvent)
                if (mTouchState.hasFlag(TouchState.FLAG_POSSIBLE_TAP_DRAG)) {
                    mTouchState.reset()
                    mLeftMouseDown = true
                    dispatchMouseAction(MotionEvent.ACTION_DOWN)
                } else {
                    mTouchState.reset()
                    mTouchState.addFlag(TouchState.FLAG_POSSIBLE_TAP)
                    dispatchHoverEvent(MotionEvent.ACTION_HOVER_ENTER)
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                mVelocityTracker.addMovement(motionEvent)
                maxPointers = maxOf(maxPointers, motionEvent.pointerCount)
                val pointerId = motionEvent.getPointerId(motionEvent.actionIndex)
                mInitialMotionEvent[pointerId]?.recycle()
                mInitialMotionEvent[pointerId] = MotionEvent.obtain(motionEvent)
            }

            MotionEvent.ACTION_POINTER_UP -> {
                mVelocityTracker.addMovement(motionEvent)
                val pointerId = motionEvent.getPointerId(motionEvent.actionIndex)
                mRecentPointerIds[pointerId] = motionEvent.eventTime
                mInitialMotionEvent[pointerId]?.also { initialEvent ->
                    if (!allowedTapMovement(initialEvent, motionEvent, pointerId))
                        mTouchState.removeFlag(TouchState.FLAG_POSSIBLE_TAP)
                    initialEvent.recycle()
                    mInitialMotionEvent.remove(pointerId)
                }
            }

            MotionEvent.ACTION_BUTTON_PRESS -> {
                mTouchState.accumulateDeltaX = 0f
                mTouchState.accumulateDeltaY = 0f
            }

            MotionEvent.ACTION_MOVE -> {
                mVelocityTracker.addMovement(motionEvent)
                if (mTouchState.hasFlag(TouchState.FLAG_POSSIBLE_TAP)) {
                    val pointerId = motionEvent.getPointerId(motionEvent.actionIndex)
                    mInitialMotionEvent[pointerId]?.also { initialEvent ->
                        if (!allowedTapMovement(initialEvent, motionEvent, pointerId))
                            mTouchState.removeFlag(TouchState.FLAG_POSSIBLE_TAP)
                    }
                }
                var unsetDragging = true
                mLastMotionEvent?.also { prev ->
                    if (motionEvent.pointerCount == 1 && prev.pointerCount == 1) {
                        updateDeltaTimed(
                            motionEvent.eventTime,
                            motionEvent.x - prev.x,
                            motionEvent.y - prev.y
                        )
                        if (mLeftMouseDown) dispatchMouseAction(MotionEvent.ACTION_MOVE)
                        else dispatchHoverEvent(MotionEvent.ACTION_HOVER_MOVE)
                    } else if (motionEvent.pointerCount == 2 && prev.pointerCount == 2) {
                        var dx = 0f
                        var dy = 0f
                        unsetDragging = false
                        for (i in 0..1) {
                            if (motionEvent.getPointerId(i) == prev.getPointerId(i)) {
                                val dxi = motionEvent.getX(i) - prev.getX(i)
                                if (abs(dxi) > abs(dx)) dx = dxi
                                val dyi = motionEvent.getY(i) - prev.getY(i)
                                if (abs(dyi) > abs(dy)) dy = dyi
                            } else {
                                unsetDragging = true
                            }
                        }
                        mTouchState.accumulateDeltaX += dx
                        mTouchState.accumulateDeltaY += dy
                        if (!mTouchState.hasFlag(TouchState.FLAG_IS_DRAGGING) && (
                                    maxOf(
                                        abs(mTouchState.accumulateDeltaX),
                                        abs(mTouchState.accumulateDeltaY)
                                    ) > kTouchSlop
                                    )
                        ) {
                            mTouchState.addFlag(TouchState.FLAG_HAS_SCROLLED)
                            mTouchState.addFlag(TouchState.FLAG_IS_DRAGGING)
                            mTouchState.removeFlag(TouchState.FLAG_POSSIBLE_TAP)
                        }

                        if (mTouchState.hasFlag(TouchState.FLAG_IS_DRAGGING) && (dx != 0f || dy != 0f)) {
                            mousePerformScroll(dx, dy)
                        }
                    }
                }
                if (unsetDragging) mTouchState.removeFlag(TouchState.FLAG_IS_DRAGGING)
            }

            MotionEvent.ACTION_UP -> {
                if (mLeftMouseDown) {
                    dispatchMouseAction(MotionEvent.ACTION_UP)
                    mLeftMouseDown = false
                } else {
                    dispatchHoverEvent(MotionEvent.ACTION_HOVER_EXIT)
                }
                mVelocityTracker.addMovement(motionEvent)
                val pointerId = motionEvent.getPointerId(motionEvent.actionIndex)
                val now = motionEvent.eventTime
                mRecentPointerIds[pointerId] = now
                if (mTouchState.hasFlag(TouchState.FLAG_IS_DRAGGING)) {
                    mVelocityTracker.also { currentVelocityTracker ->
                        mVelocityTracker = VelocityTracker.obtain()
                        currentVelocityTracker.computeCurrentVelocity(1000, kMaxVelocity)
                        var vy = 0f
                        for ((pId, pTime) in mRecentPointerIds) {
                            if (pTime >= now - TAP_MS) {
                                val vyi = currentVelocityTracker.getYVelocity(pId)
                                if (abs(vyi) > abs(vy)) vy = vyi
                            }
                        }
                        currentVelocityTracker.recycle()
                        if (abs(vy) >= kMinVelocity) {
                            fling(-vy)
                        }
                    }
                } else if (SystemClock.uptimeMillis() - touchStartTime <= TAP_MS
                    && mTouchState.hasFlag(TouchState.FLAG_POSSIBLE_TAP)
                ) {
                    mInitialMotionEvent[pointerId]?.also { initialEvent ->
                        if (!allowedTapMovement(initialEvent, motionEvent, pointerId))
                            mTouchState.removeFlag(TouchState.FLAG_POSSIBLE_TAP)
                        initialEvent.recycle()
                        mInitialMotionEvent.remove(pointerId)
                    }
                    if (!mTouchState.hasFlag(TouchState.FLAG_POSSIBLE_TAP)) {
                        //
                    } else if (maxPointers == 1) {
                        if (mTouchState.canEnableTapTapDrag() && kEnableTapTapDrag) {
                            mTouchState.addFlag(TouchState.FLAG_POSSIBLE_TAP_DRAG)
                            mHandler.sendEmptyMessageDelayed(
                                MOUSE_ACTION_CLICK_AFTER_TAP,
                                max(
                                    0L,
                                    touchStartTime + DOUBLE_TAP_MS - SystemClock.uptimeMillis()
                                )
                            )
                        } else {
                            mousePerformClick()
                        }
                    } else if (maxPointers == 2) {
                        if (mTouchState.canPerformRightClick())
                            mousePerformRightClick()
                    }
                }
                mRecentPointerIds.clear()
                mTouchState.removeFlag(TouchState.FLAG_IS_DRAGGING)
            }

            MotionEvent.ACTION_CANCEL -> {
                if (mLeftMouseDown) {
                    dispatchMouseAction(MotionEvent.ACTION_UP)
                    mLeftMouseDown = false
                } else {
                    dispatchHoverEvent(MotionEvent.ACTION_HOVER_EXIT)
                }
                mRecentPointerIds.clear()
                mTouchState.removeFlag(TouchState.FLAG_IS_DRAGGING)
            }
        }
        mLastMotionEvent = MotionEvent.obtain(motionEvent)
        return true
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

    private val gTmpPointerCoords = arrayOf(PointerCoords())
    private val gTmpPointerProps = arrayOf(MotionEvent.PointerProperties())

    private fun mouseObtainEvent(
        _downTime: Long, _eventTime: Long,
        _action: Int, _x: Float, _y: Float,
        _meta: Int = 0, _buttons: Int = 0, _actionButtons: Int = 0,
        _sx: Float? = null, _sy: Float? = null,
        _source: Int = InputDevice.SOURCE_MOUSE,
    ): MotionEvent {
        synchronized(gTmpPointerCoords) {
            gTmpPointerProps[0].apply {
                clear()
                id = 0
                toolType = MotionEvent.TOOL_TYPE_MOUSE
            }
            gTmpPointerCoords[0].apply {
                clear()
                x = _x
                y = _y
                pressure = 1f
                size = 0f
                if (_sx != null)
                    setAxisValue(MotionEvent.AXIS_HSCROLL, _sx)
                if (_sy != null)
                    setAxisValue(MotionEvent.AXIS_VSCROLL, _sy)
            }
            var e = MotionEvent.obtain(
                _downTime, _eventTime,
                _action, 1, gTmpPointerProps, gTmpPointerCoords,
                _meta, _buttons,
                1f, 1f, SECRET_DEVICE, 0, _source, 0
            )
            if (_actionButtons != 0 && actionButtonOffset >= 0) {
                val p = Parcel.obtain()
                e.writeToParcel(p, Parcelable.PARCELABLE_WRITE_RETURN_VALUE)
                e.recycle()
                val buf = p.marshall()
                val actionButtonsUnsigned = _actionButtons.toUInt()
                if (actionButtonIsBigEndian) {
                    buf[actionButtonOffset] = actionButtonsUnsigned.shr(24).and(0xFFu).toByte()
                    buf[actionButtonOffset + 1] =
                        actionButtonsUnsigned.shr(16).and(0xFFu).toByte()
                    buf[actionButtonOffset + 2] =
                        actionButtonsUnsigned.shr(8).and(0xFFu).toByte()
                    buf[actionButtonOffset + 3] = actionButtonsUnsigned.and(0xFFu).toByte()
                } else {
                    buf[actionButtonOffset + 3] =
                        actionButtonsUnsigned.shr(24).and(0xFFu).toByte()
                    buf[actionButtonOffset + 2] =
                        actionButtonsUnsigned.shr(16).and(0xFFu).toByte()
                    buf[actionButtonOffset + 1] =
                        actionButtonsUnsigned.shr(8).and(0xFFu).toByte()
                    buf[actionButtonOffset] = actionButtonsUnsigned.and(0xFFu).toByte()
                }
                p.unmarshall(buf, 0, buf.size)
                p.setDataPosition(0)
                e = MotionEvent.CREATOR.createFromParcel(p)
                p.recycle()
            }
            return e
        }
    }

    private fun mousePerformScroll(dx: Float, dy: Float): Boolean {
        val t = SystemClock.uptimeMillis()
        val e = mouseObtainEvent(
            t - 1000, t,
            MotionEvent.ACTION_SCROLL, cx, cy,
            _sx = dx * px2HScroll, _sy = dy * px2VScroll
        )
        val r = mTarget.onGenericMotionEvent(e)
        e.recycle()
        return r
    }

    var mCurrentFlingAnimation: FlingAnimation? = null
    var mNextFlingMessageTime: Long = 0

    private fun sendFlingMessage() {
        val now = SystemClock.uptimeMillis()
        mHandler.sendEmptyMessageDelayed(
            MOUSE_ACTION_FLING,
            max(0, mNextFlingMessageTime - SystemClock.uptimeMillis())
        )
        mNextFlingMessageTime = now + 16 // ~ 60fps =))
    }

    private fun fling(vy: Float) {
        val duration = getSplineFlingDuration(vy.toDouble())
        val totalDistance = getSplineFlingDistance(vy.toDouble())
        mCurrentFlingAnimation =
            FlingAnimation(duration.toDouble(), -totalDistance * Math.signum(vy))
        sendFlingMessage()
    }

    private fun getSplineDeceleration(velocity: Double): Double {
        return Math.log(INFLEXION * Math.abs(velocity) / (kFlingFriction * kPhysicalCoeff))
    }

    private fun getSplineFlingDistance(velocity: Double): Double {
        val l = getSplineDeceleration(velocity)
        val decelMinusOne: Double = DECELERATION_RATE - 1.0
        return kFlingFriction * kPhysicalCoeff * Math.exp(DECELERATION_RATE / decelMinusOne * l)
    }

    /* Returns the duration, expressed in milliseconds */
    private fun getSplineFlingDuration(velocity: Double): Int {
        val l = getSplineDeceleration(velocity)
        val decelMinusOne: Double = DECELERATION_RATE - 1.0
        return (1000.0 * Math.exp(l / decelMinusOne)).toInt()
    }

    private fun mousePerformDown(buttonState: Int) {
        val t = SystemClock.uptimeMillis()
        val e = mouseObtainEvent(
            t - 1, t - 1,
            MotionEvent.ACTION_BUTTON_PRESS, cx, cy,
            _buttons = buttonState, _actionButtons = buttonState
        )
        mTarget.onGenericMotionEvent(e)
        e.recycle()
    }

    private fun mousePerformUp(buttonState: Int) {
        val t = SystemClock.uptimeMillis()
        val e = mouseObtainEvent(
            t - 1, t,
            MotionEvent.ACTION_BUTTON_RELEASE, cx, cy,
            _buttons = 0, _actionButtons = buttonState
        )
        mTarget.onGenericMotionEvent(e)
        e.recycle()
    }

    private fun mousePerformClick(buttonState: Int) {
        mousePerformDown(buttonState)
        mousePerformUp(buttonState)
    }

    private fun mousePerformClick() {
        mousePerformClick(MotionEvent.BUTTON_PRIMARY)
        mTouchState.reset()
    }

    private fun mousePerformRightClick() {
        mousePerformClick(MotionEvent.BUTTON_SECONDARY)
        mTouchState.reset()
    }

    class FlingAnimation(
        val duration: Double,
        val distance: Double,
        val startTime: Long = time(),
    ) {
        companion object {
            fun time() = SystemClock.uptimeMillis()

            private const val NB_SAMPLES = 100
            private val SPLINE_POSITION = FloatArray(NB_SAMPLES + 1)
            private val SPLINE_TIME = FloatArray(NB_SAMPLES + 1)

            init {
                var x_min = 0.0f
                var y_min = 0.0f
                for (i in 0 until NB_SAMPLES) {
                    val alpha = i.toFloat() / NB_SAMPLES
                    var x_max = 1.0f
                    var x: Float
                    var tx: Float
                    var coef: Float
                    while (true) {
                        x = x_min + (x_max - x_min) / 2.0f
                        coef = 3.0f * x * (1.0f - x)
                        tx = (coef * ((1.0f - x) * P1 + x * P2) + x * x * x).toFloat()
                        if (Math.abs(tx - alpha) < 1E-5) break
                        if (tx > alpha) x_max = x else x_min = x
                    }
                    SPLINE_POSITION[i] =
                        (coef * ((1.0f - x) * START_TENSION + x) + x * x * x).toFloat()
                    var y_max = 1.0f
                    var y: Float
                    var dy: Float
                    while (true) {
                        y = y_min + (y_max - y_min) / 2.0f
                        coef = 3.0f * y * (1.0f - y)
                        dy = (coef * ((1.0f - y) * START_TENSION + y) + y * y * y).toFloat()
                        if (Math.abs(dy - alpha) < 1E-5) break
                        if (dy > alpha) y_max = y else y_min = y
                    }
                    SPLINE_TIME[i] = (coef * ((1.0f - y) * P1 + y * P2) + y * y * y).toFloat()
                }
                SPLINE_TIME[NB_SAMPLES] = 1.0f
                SPLINE_POSITION[NB_SAMPLES] = SPLINE_TIME[NB_SAMPLES]
            }
        }

        var mAccumulatedDistance = 0F
        var mFinished = false

        fun isFinished(): Boolean {
            return mFinished
        }

        fun currentDistance(): Float {
            val nowr = time() - startTime
            if (nowr >= duration) {
                mFinished = true
                return distance.toFloat()
            }
            val t = (nowr.toFloat() / duration).toFloat()
            val index = (NB_SAMPLES * t).toInt()
            var distanceCoef = 1f
            var velocityCoef = 0f
            if (index < NB_SAMPLES) {
                val t_inf = index.toFloat() / NB_SAMPLES
                val t_sup = (index + 1).toFloat() / NB_SAMPLES
                val d_inf = SPLINE_POSITION[index]
                val d_sup = SPLINE_POSITION[index + 1]
                velocityCoef = (d_sup - d_inf) / (t_sup - t_inf)
                distanceCoef = d_inf + (t - t_inf) * velocityCoef
            }
            return (distanceCoef * distance).toFloat()
        }

        fun nextDelta(): Float {
            val c = currentDistance()
            val r = c - mAccumulatedDistance
            mAccumulatedDistance = c
            return r
        }
    }

    class TouchState {
        companion object {
            const val FLAG_POSSIBLE_TAP = 0x1
            const val FLAG_POSSIBLE_TAP_DRAG = 0x2
            const val FLAG_HAS_SCROLLED = 0x4
            const val FLAG_IS_DRAGGING = 0x8
        }

        private var mState = 0
        var accumulateDeltaX = 0F
        var accumulateDeltaY = 0F

        fun reset() {
            mState = 0
            accumulateDeltaX = 0F
            accumulateDeltaY = 0F
        }

        fun isEmpty(): Boolean {
            return mState == 0
        }

        fun addFlag(flag: Int) {
            mState = mState or flag
        }

        fun removeFlag(flag: Int) {
            mState = mState and flag.inv()
        }

        fun hasFlag(flag: Int): Boolean {
            return mState.and(flag) != 0
        }

        fun canEnableTapTapDrag(): Boolean = !hasFlag(FLAG_HAS_SCROLLED) && !hasFlag(
            FLAG_IS_DRAGGING
        )

        fun canPerformRightClick(): Boolean = !hasFlag(FLAG_HAS_SCROLLED) && !hasFlag(
            FLAG_IS_DRAGGING
        )
    }
}