package vn.vhn.vhscode.compat

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

interface IPermManager {
    fun ensure(activity: Activity, msg: Int? = null): Boolean {
        if (!check(activity)) {
            msg?.also {
                Toast.makeText(activity, it, Toast.LENGTH_LONG).show()
            }
            request(activity)
            return false
        }
        return true
    }

    fun check(activity: Activity): Boolean
    fun request(activity: Activity)
}

class PermSystemSettingWrite : IPermManager {
    override fun check(activity: Activity): Boolean {
        return Settings.System.canWrite(activity)
    }

    override fun request(activity: Activity) {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:" + activity.packageName))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
    }

}

class PermissionCompat {
    companion object {
        val systemSettings = PermSystemSettingWrite()
    }
}