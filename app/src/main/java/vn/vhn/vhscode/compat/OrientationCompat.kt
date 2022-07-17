package vn.vhn.vhscode.compat

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import android.view.Surface

class OrientationCompat {
    companion object {
        fun getCurrentRotation(activity: Activity): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.display?.rotation ?: 0
            } else {
                @Suppress("DEPRECATION")
                activity.windowManager.defaultDisplay.rotation
            }
        }

        private fun getCurrentOrientation(activity: Activity): Int {
            return activity.resources.configuration.orientation
        }

        fun getCurrentScreenOrientation(activity: Activity): Int {
            val orientation = getCurrentOrientation(activity)
            return when (getCurrentRotation(activity)) {
                Surface.ROTATION_0, Surface.ROTATION_90 -> {
                    when (orientation) {
                        Configuration.ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        Configuration.ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
                Surface.ROTATION_180, Surface.ROTATION_270 -> {
                    when (orientation) {
                        Configuration.ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                        Configuration.ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
                else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
}