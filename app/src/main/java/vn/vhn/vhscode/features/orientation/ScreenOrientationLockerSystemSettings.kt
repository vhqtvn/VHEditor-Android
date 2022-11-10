package vn.vhn.vhscode.features.orientation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Surface
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import vn.vhn.vhscode.R

class ScreenOrientationLockerSystemSettings(private val mContext: Context) :
    IScreenOrientationLocker {
    private var mSavedAccelerometerEnable: Int? = null
    private var mSavedLockRotation: Int? = null
    private var mLockedOrientation: Int? = null
    override fun saveCurrentWindowManager(window: Window?, wm: WindowManager?) {}
    override fun lock(): Boolean {
        val wm = mContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            lock(mContext.display?.rotation ?: 0)
        } else {
            @Suppress("DEPRECATION")
            lock(wm.defaultDisplay.rotation)
        }
    }

    override fun lock(screenRotation: Int): Boolean {
        val contentResolver = mContext.contentResolver
        if (mSavedAccelerometerEnable == null)
            mSavedAccelerometerEnable = Settings.System.getInt(contentResolver,
                Settings.System.ACCELEROMETER_ROTATION, 0)
        if (mSavedLockRotation == null)
            mSavedLockRotation = Settings.System.getInt(contentResolver,
                Settings.System.USER_ROTATION, 0)
        var targetRotation = screenRotation
        Log.d(TAG, "Force rotation $targetRotation")
        mLockedOrientation = targetRotation
        if (!Settings.System.putInt(contentResolver,
                Settings.System.USER_ROTATION,
                targetRotation)
        ) return false
        if (!Settings.System.putInt(contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                0)
        ) return false
        if (!Settings.System.putInt(contentResolver,
                Settings.System.USER_ROTATION,
                targetRotation)
        ) return false
        return true
    }

    override fun unlock(): Int? {
        val contentResolver = mContext.contentResolver
        var orientation = mLockedOrientation
        mLockedOrientation = null
        mSavedLockRotation?.also {
            Settings.System.putInt(contentResolver, Settings.System.USER_ROTATION, it)
            mSavedLockRotation = null
        }
        mSavedAccelerometerEnable?.also {
            Log.d(TAG, "Restore acc $it")
            if (!Settings.System.putInt(contentResolver,
                    Settings.System.ACCELEROMETER_ROTATION,
                    it)
            ) {
                Log.e(TAG, "Restore acc $it failed")
            }
            mSavedAccelerometerEnable = null
        }
        return orientation
    }

    companion object {
        private const val TAG = "ScreenOrientationLocker"

        fun check(context: Context): Boolean {
            return Settings.System.canWrite(context)
        }

        fun request(context: Context) {
            Handler(Looper.getMainLooper()).also { handler ->
                handler.post {
                    Toast.makeText(context.applicationContext,
                        R.string.locking_orientation_rewrite_write_settings,
                        Toast.LENGTH_LONG).show()
                    handler.postDelayed({
                        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:" + context.packageName))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }, 1500)
                }
            }
        }
    }
}