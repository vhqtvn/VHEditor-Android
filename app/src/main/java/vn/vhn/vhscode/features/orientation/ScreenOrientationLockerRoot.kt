//package vn.vhn.vhscode.features.orientation
//
//import android.content.Context
//import android.content.pm.ActivityInfo
//import android.os.Build
//import android.os.Handler
//import android.util.Log
//import android.view.Window
//import android.view.WindowManager
//import com.lge.ime.util.p118f.LGMultiDisplayUtils
//import vn.vhn.pckeyboard.root.RootCompat
//import java.io.IOException
//import java.io.InputStream
//import java.io.InputStreamReader
//import java.io.StringWriter
//
//class ScreenOrientationLockerRoot(private val mContext: Context) : IScreenOrientationLocker {
//    private var mSavedRotation = ""
//    private var mSavedOrientation = ""
//    private var mLockedOrientation = ""
//    override fun saveCurrentWindowManager(window: Window?, wm: WindowManager?) {}
//
//    override fun lock(): Boolean {
//        val wm = mContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
//        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//            lock(mContext.display?.rotation ?: 0)
//        } else {
//            @Suppress("DEPRECATION")
//            lock(wm.defaultDisplay.rotation)
//        }
//    }
//
//    override fun lock(orientation: Int): Boolean {
//        if (mSavedRotation.isEmpty()) mSavedRotation =
//            RootCompat.rootRun("settings get system accelerometer_rotation", true).second
//        if (mSavedOrientation.isEmpty()) mSavedOrientation =
//            RootCompat.rootRun("settings get system user_rotation", true).second
//        RootCompat.rootRun("settings put system accelerometer_rotation 0", true)
//        var targetOrientation = orientation
//        if (LGMultiDisplayUtils.supportDualScreen()) {
//            //Hack for LG
//            if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
//                val wm = mContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
//                targetOrientation = if (wm.defaultDisplay.displayId != 0) {
//                    1
//                } else {
//                    3
//                }
//            }
//        }
//        RootCompat.rootRun("wm set-user-rotation lock -d 0 $targetOrientation; wm set-user-rotation lock -d 1 $targetOrientation",
//            true)
//        mLockedOrientation = targetOrientation.toString()
//        return true
//    }
//
//    override fun unlock(): Int? {
//        var orientation = mLockedOrientation
//        mLockedOrientation = ""
//        if (!mSavedOrientation.isEmpty()) {
//            RootCompat.rootRun("settings put system user_rotation $mSavedOrientation", false)
//            mSavedRotation = ""
//        }
//        if (!mSavedRotation.isEmpty()) {
//            RootCompat.rootRun("settings put system accelerometer_rotation $mSavedRotation", false)
//            mSavedRotation = ""
//        }
//        return orientation.toIntOrNull()
//    }
//
//    companion object {
//        private const val TAG = "ScreenOrientationLocker"
//
//    }
//}