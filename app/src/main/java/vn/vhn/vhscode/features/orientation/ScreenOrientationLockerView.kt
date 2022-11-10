//package vn.vhn.vhscode.features.orientation
//
//import android.annotation.SuppressLint
//import android.content.Context
//import android.content.Intent
//import android.content.pm.ActivityInfo
//import android.graphics.PixelFormat
//import android.net.Uri
//import android.os.Build
//import android.os.Handler
//import android.provider.Settings
//import android.util.Log
//import android.view.View
//import android.view.Window
//import android.view.WindowManager
//import android.view.inputmethod.InputMethodManager
//import android.widget.Toast
//import java.io.IOException
//
//class ScreenOrientationLockerView(private val mContext: Context) : IScreenOrientationLocker {
//
//    override fun saveCurrentWindowManager(window: Window?, wm: WindowManager?) {
//        mSavedWindowManager = wm
//        mSavedWindow = window
//        mSavedFocus = mSavedWindow!!.currentFocus
//        Log.i(TAG,
//            "Saving Current window: " + wm?.defaultDisplay?.name + ", current focus: " + mSavedFocus)
//    }
//
//    override fun lock(): Boolean {
//        val wm = mSavedWindowManager
//            ?: mContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
//        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//            lock(mContext.display?.rotation ?: 0)
//        } else {
//            @Suppress("DEPRECATION")
//            lock(wm.defaultDisplay.rotation)
//        }
//    }
//
//    override fun lock(orientation: Int): Boolean {
//        var orientation = orientation
//        Log.d(TAG, "orientation lock: $orientation")
//        if (mOverlayViewForOrientationLock == null) mOverlayViewForOrientationLock = View(mContext)
//        if (!Settings.canDrawOverlays(mContext)) {
//            Toast.makeText(mContext, "Need overlay perm to lock orientation", Toast.LENGTH_LONG)
//                .show()
//            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
//                Uri.parse("package:" + mContext.packageName))
//            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//            mContext.startActivity(intent)
//            return false
//        }
//        val params = WindowManager.LayoutParams(
//            0, 0,
//            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
//            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
//                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
//                    or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
//            PixelFormat.TRANSLUCENT)
//        val wm =
//            if (mSavedWindowManager != null) mSavedWindowManager else mContext.getSystemService(
//                Context.WINDOW_SERVICE) as WindowManager
//        Log.i(TAG,
//            "Current window: " + wm!!.defaultDisplay.name + " (" + wm.defaultDisplay.displayId + ")")
//        if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
//            if (wm.defaultDisplay.displayId != 0) {
//                orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
//            } else {
////                ((Application)mContext.getApplicationContext()).
//            }
//        }
//        params.screenOrientation = orientation
//        if (wm.defaultDisplay != mOverlayViewForOrientationLock!!.display && mLastWindowManager != null) {
//            try {
//                mLastWindowManager!!.removeView(mOverlayViewForOrientationLock)
//            } catch (ex: Exception) {
//            }
//        }
//        try {
//            wm.addView(mOverlayViewForOrientationLock, params)
//            mLastWindowManager = wm
//        } catch (ex: Exception) {
//            wm.updateViewLayout(mOverlayViewForOrientationLock, params)
//            mLastWindowManager = wm
//        }
//        return true
//    }
//
//    override fun unlock(): Int? {
//        Log.d(TAG, "orientation unlock")
//        if (mLastWindowManager != null) {
//            mLastWindowManager!!.removeView(mOverlayViewForOrientationLock)
//            mLastWindowManager = null
//        }
//        return null
//    }
//
//
//    companion object {
//        private const val TAG = "ScreenOrientationLocker"
//        var mLastWindowManager: WindowManager? = null
//        var mSavedWindowManager: WindowManager? = null
//
//        @SuppressLint("StaticFieldLeak")
//        private var mOverlayViewForOrientationLock: View? = null
//
//        @SuppressLint("StaticFieldLeak")
//        private var mSavedFocus: View? = null
//
//        //    private DisplayManager mDisplayManager;
//        private var mSavedWindow: Window? = null
//
//        fun check(context: Context): Boolean {
//            return Settings.canDrawOverlays(context)
//        }
//
//        fun request(context: Context) {
//            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
//                Uri.parse("package:" + context.packageName))
//            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//            context.startActivity(intent)
//        }
//    }
//}