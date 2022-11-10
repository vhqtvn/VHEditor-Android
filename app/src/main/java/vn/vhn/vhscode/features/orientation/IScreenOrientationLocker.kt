package vn.vhn.vhscode.features.orientation

import android.content.Context
import android.view.Window
import android.view.WindowManager

interface IScreenOrientationLocker {
    fun saveCurrentWindowManager(window: Window?, wm: WindowManager?)
    fun lock(): Boolean
    fun lock(screenRotation: Int): Boolean
    fun unlock(): Int?
}


fun createScreenOrientationLocker(context: Context): IScreenOrientationLocker? {
//    if (RootCompat.isCompatibleRooted()) return ScreenOrientationLockerRoot(context)
    if (ScreenOrientationLockerSystemSettings.check(context))
        return ScreenOrientationLockerSystemSettings(context)
    else {
        ScreenOrientationLockerSystemSettings.request(context)
    }
//    if (ScreenOrientationLockerView.check(context))
//        return ScreenOrientationLockerView(context)
    return null
}