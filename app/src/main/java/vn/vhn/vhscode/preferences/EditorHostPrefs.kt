package vn.vhn.vhscode.preferences

import android.content.Context
import android.util.Log
import android.util.TypedValue
import com.termux.shared.settings.preferences.SharedPreferenceUtils
import com.termux.shared.settings.preferences.TermuxPreferenceConstants
import com.termux.shared.settings.properties.TermuxPropertyConstants


class EditorHostPrefs(context: Context) {
    companion object {
        val kPrefLatestVersionCachedValue = "cached:latestversion:value"
        val kPrefLatestVersionCachedTime = "cached:latestversion:time"
        val kPrefFullScreen = "fullscreen"
        val kPrefHardwareKeyboardMode = "hwkeyboard"
        val kPrefLockedKeyboardId = "locked-kb"
        val kPrefEditorHWAcceleration = "editor:hwa"
        val kPrefEditorUIScale = "editor:uiscale"
        val kPrefEditorUseSSL = "editor:use-ssl"
        val kPrefEditorVerbose = "editor:verbose"
        val kPrefEditorUseVirtualMouse = "editor:use-vmouse"
        val kPrefEditorMobileDisplayMode = "editor:mobile-display-mode"
        val kPrefEditorListenALlInterfaces = "editor:all-interfaces"
        val kPrefRemoteCodeEditorURL = "editor:remote-url"
        val kPrefVirtualMouseScale = "editor:virtualmousescale"
        val kPrefLocalServerListenPort = "editor:listen-port"

        val kPrefLockedOrientation = "locked-orientation"

        val kPrefInitialTool = "startup:tool"

        val kRequestedPermissions = "requested-permissions"

        private fun <T : Comparable<T>> limitMinMax(x: T, min: T, max: T): T {
            if (x < min) return min
            if (x > max) return max
            return x
        }

        fun getDefaultFontSizes(context: Context): IntArray {
            val dipInPixels = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                1f,
                context.resources.displayMetrics
            )
            val sizes = IntArray(3)

            // This is a bit arbitrary and sub-optimal. We want to give a sensible default for minimum font size
            // to prevent invisible text due to zoom be mistake:
            sizes[1] = (4f * dipInPixels).toInt() // min

            // http://www.google.com/design/spec/style/typography.html#typography-line-height
            var defaultFontSize = Math.round(12 * dipInPixels)
            // Make it divisible by 2 since that is the minimal adjustment step:
            if (defaultFontSize % 2 == 1) defaultFontSize--
            sizes[0] = defaultFontSize // default
            sizes[2] = 256 // max
            return sizes
        }
    }

    private val _defaultFontSizes = getDefaultFontSizes(context)

    private var mSharedPreferences =
        SharedPreferenceUtils.getPrivateAndMultiProcessSharedPreferences(context, "EditorHostPrefs")

    var currentSession: String?
        get() = SharedPreferenceUtils.getString(
            mSharedPreferences,
            TermuxPreferenceConstants.TERMUX_APP.KEY_CURRENT_SESSION,
            null,
            true
        )
        set(value) {
            SharedPreferenceUtils.setString(
                mSharedPreferences,
                TermuxPreferenceConstants.TERMUX_APP.KEY_CURRENT_SESSION,
                value,
                false
            )
        }

    var lockedKeyboard: String?
        get() = SharedPreferenceUtils.getString(
            mSharedPreferences,
            kPrefLockedKeyboardId,
            null,
            true
        )
        set(value) {
            SharedPreferenceUtils.setString(
                mSharedPreferences,
                kPrefLockedKeyboardId,
                value,
                false
            )
        }

    var cursorBlinkRate: Int
        get() =
            limitMinMax(
                SharedPreferenceUtils.getInt(
                    mSharedPreferences,
                    TermuxPropertyConstants.KEY_TERMINAL_CURSOR_BLINK_RATE,
                    555
                ),
                TermuxPropertyConstants.IVALUE_TERMINAL_CURSOR_BLINK_RATE_MIN,
                TermuxPropertyConstants.IVALUE_TERMINAL_CURSOR_BLINK_RATE_MAX
            )
        set(value) {
            SharedPreferenceUtils.setInt(
                mSharedPreferences,
                TermuxPropertyConstants.KEY_TERMINAL_CURSOR_BLINK_RATE,
                limitMinMax(
                    value,
                    TermuxPropertyConstants.IVALUE_TERMINAL_CURSOR_BLINK_RATE_MIN,
                    TermuxPropertyConstants.IVALUE_TERMINAL_CURSOR_BLINK_RATE_MAX
                ),
                false
            )
        }

    var editorUIScale: Int
        get() =
            limitMinMax(
                SharedPreferenceUtils.getInt(
                    mSharedPreferences,
                    kPrefEditorUIScale,
                    100
                ),
                25, 300
            )
        set(value) {
            SharedPreferenceUtils.setInt(
                mSharedPreferences,
                kPrefEditorUIScale,
                limitMinMax(value, 25, 300),
                false
            )
        }

    private val editorVirtualMouseScalePowerBase = Math.exp(Math.log(4.0) / 10)
    fun calculateEditorVirtualMouseScale(param: Int) =
        Math.pow(editorVirtualMouseScalePowerBase, (param - 10).toDouble()).toFloat()

    val editorVirtualMouseScale: Float
        get() = calculateEditorVirtualMouseScale(editorVirtualMouseScaleParam)

    var editorVirtualMouseScaleParam: Int
        get() =
            limitMinMax(
                SharedPreferenceUtils.getInt(
                    mSharedPreferences,
                    kPrefVirtualMouseScale,
                    10
                ),
                0, 20
            )
        set(value) {
            SharedPreferenceUtils.setInt(
                mSharedPreferences,
                kPrefVirtualMouseScale,
                limitMinMax(value, 0, 20),
                false
            )
        }

    var shouldKeepScreenOn: Boolean
        get() =
            SharedPreferenceUtils.getBoolean(
                mSharedPreferences,
                TermuxPreferenceConstants.TERMUX_APP.KEY_KEEP_SCREEN_ON,
                false
            )
        set(value) {
            SharedPreferenceUtils.setBoolean(
                mSharedPreferences,
                TermuxPreferenceConstants.TERMUX_APP.KEY_KEEP_SCREEN_ON,
                value,
                false
            )
        }

    var fullScreen: Boolean
        get() =
            SharedPreferenceUtils.getBoolean(
                mSharedPreferences,
                kPrefFullScreen,
                false
            )
        set(value) {
            SharedPreferenceUtils.setBoolean(
                mSharedPreferences,
                kPrefFullScreen,
                value,
                false
            )
        }

    var defaultRemoteEditorURL: String
        get() =
            SharedPreferenceUtils.getString(
                mSharedPreferences,
                TermuxPreferenceConstants.TERMUX_APP.KEY_SOFT_KEYBOARD_ENABLED,
                "https://127.0.0.1:13337",
                false
            )
        set(value) {
            SharedPreferenceUtils.setString(
                mSharedPreferences,
                TermuxPreferenceConstants.TERMUX_APP.KEY_SOFT_KEYBOARD_ENABLED,
                value,
                false
            )
        }

    var softKeyboardEnabled: Boolean
        get() =
            SharedPreferenceUtils.getBoolean(
                mSharedPreferences,
                TermuxPreferenceConstants.TERMUX_APP.KEY_SOFT_KEYBOARD_ENABLED,
                true
            )
        set(value) {
            SharedPreferenceUtils.setBoolean(
                mSharedPreferences,
                TermuxPreferenceConstants.TERMUX_APP.KEY_SOFT_KEYBOARD_ENABLED,
                value,
                false
            )
        }

    var softKeyboardEnabledOnlyIfNoHardware: Boolean
        get() =
            SharedPreferenceUtils.getBoolean(
                mSharedPreferences,
                TermuxPreferenceConstants.TERMUX_APP.KEY_SOFT_KEYBOARD_ENABLED_ONLY_IF_NO_HARDWARE,
                true
            )
        set(value) {
            SharedPreferenceUtils.setBoolean(
                mSharedPreferences,
                TermuxPreferenceConstants.TERMUX_APP.KEY_SOFT_KEYBOARD_ENABLED_ONLY_IF_NO_HARDWARE,
                value,
                false
            )
        }

    var fontSize: Int
        get() =
            SharedPreferenceUtils.getInt(
                mSharedPreferences,
                TermuxPreferenceConstants.TERMUX_APP.KEY_FONTSIZE,
                _defaultFontSizes[0]
            )
        set(value) {
            SharedPreferenceUtils.setInt(
                mSharedPreferences,
                TermuxPreferenceConstants.TERMUX_APP.KEY_FONTSIZE,
                limitMinMax(value, _defaultFontSizes[1], _defaultFontSizes[2]),
                false
            )
        }

    fun fontSizeChange(increase: Boolean) {
        var sz = fontSize
        if (increase) sz += 1
        else sz -= 1
        fontSize = sz
    }

    val latestVersionCheckTime: Long
        get() = SharedPreferenceUtils.getLong(
            mSharedPreferences, kPrefLatestVersionCachedTime, 0
        )

    var latestVersion: String
        get() = SharedPreferenceUtils.getString(
            mSharedPreferences, kPrefLatestVersionCachedValue, "unknown", true
        )
        set(value) {
            SharedPreferenceUtils.setString(
                mSharedPreferences,
                kPrefLatestVersionCachedValue,
                value,
                false
            )
            SharedPreferenceUtils.setLong(
                mSharedPreferences,
                kPrefLatestVersionCachedTime,
                System.currentTimeMillis(),
                false
            )
        }

    private val lockedOrientationUndef = -1337
    var lockedOrientation: Int?
        get() = SharedPreferenceUtils.getInt(
            mSharedPreferences, kPrefLockedOrientation, lockedOrientationUndef
        ).takeIf { it != lockedOrientationUndef }
        set(value) {
            SharedPreferenceUtils.setInt(
                mSharedPreferences,
                kPrefLockedOrientation,
                value ?: lockedOrientationUndef,
                false
            )
        }

    var editLocalServerListenPort: String
        get() = SharedPreferenceUtils.getString(
            mSharedPreferences, kPrefLocalServerListenPort, "", true
        )
        set(value) {
            SharedPreferenceUtils.setString(
                mSharedPreferences,
                kPrefLocalServerListenPort,
                if (value == "0") "" else value,
                false
            )
        }


    var hardwareKeyboardMode: Boolean
        get() =
            SharedPreferenceUtils.getBoolean(
                mSharedPreferences,
                kPrefHardwareKeyboardMode,
                false
            )
        set(value) {
            SharedPreferenceUtils.setBoolean(
                mSharedPreferences,
                kPrefHardwareKeyboardMode,
                value,
                false
            )
        }

    var editorUseSSL: Boolean
        get() =
            SharedPreferenceUtils.getBoolean(
                mSharedPreferences,
                kPrefEditorUseSSL,
                true
            )
        set(value) {
            SharedPreferenceUtils.setBoolean(
                mSharedPreferences,
                kPrefEditorUseSSL,
                value,
                false
            )
        }

    var editorVerbose: Boolean
        get() =
            SharedPreferenceUtils.getBoolean(
                mSharedPreferences,
                kPrefEditorVerbose,
                false
            )
        set(value) {
            SharedPreferenceUtils.setBoolean(
                mSharedPreferences,
                kPrefEditorVerbose,
                value,
                false
            )
        }

    var editorUseHWAccelerator: Boolean
        get() =
            SharedPreferenceUtils.getBoolean(
                mSharedPreferences,
                kPrefEditorHWAcceleration,
                true
            )
        set(value) {
            SharedPreferenceUtils.setBoolean(
                mSharedPreferences,
                kPrefEditorHWAcceleration,
                value,
                false
            )
        }

    var editorVirtualMouse: Boolean
        get() =
            SharedPreferenceUtils.getBoolean(
                mSharedPreferences,
                kPrefEditorUseVirtualMouse,
                true
            )
        set(value) {
            SharedPreferenceUtils.setBoolean(
                mSharedPreferences,
                kPrefEditorUseVirtualMouse,
                value,
                false
            )
        }

    var editorMobileDisplayMode: Boolean
        get() =
            SharedPreferenceUtils.getBoolean(
                mSharedPreferences,
                kPrefEditorMobileDisplayMode,
                true
            )
        set(value) {
            SharedPreferenceUtils.setBoolean(
                mSharedPreferences,
                kPrefEditorMobileDisplayMode,
                value,
                false
            )
        }

    var editorListenAllInterfaces: Boolean
        get() =
            SharedPreferenceUtils.getBoolean(
                mSharedPreferences,
                kPrefEditorListenALlInterfaces,
                true
            )
        set(value) {
            SharedPreferenceUtils.setBoolean(
                mSharedPreferences,
                kPrefEditorListenALlInterfaces,
                value,
                false
            )
        }

    var requestedPermissions: Boolean
        get() =
            SharedPreferenceUtils.getBoolean(
                mSharedPreferences,
                kRequestedPermissions,
                false
            )
        set(value) {
            SharedPreferenceUtils.setBoolean(
                mSharedPreferences,
                kRequestedPermissions,
                value,
                false
            )
        }

    public enum class StartupTool {
        NONE,
        EDITOR,
    }

    var startupTool: StartupTool
        get() =
            try {
                StartupTool.valueOf(SharedPreferenceUtils.getString(
                    mSharedPreferences,
                    kPrefInitialTool,
                    "", true
                ))
            } catch (e: Exception) {
                StartupTool.NONE
            }
        set(value) {
            SharedPreferenceUtils.setString(
                mSharedPreferences,
                kPrefInitialTool,
                value.toString(),
                false
            )
        }
}