package vn.vhn.vhscode.root.terminal

import android.annotation.SuppressLint
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.termux.shared.terminal.TermuxTerminalViewClientBase
import com.termux.shared.terminal.io.extrakeys.SpecialButton
import com.termux.shared.view.KeyboardUtils
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalSession
import vn.vhn.vhscode.root.fragments.TerminalFragment


class TermuxTerminalSingleViewClient(
    fragment: TerminalFragment,
    termuxTerminalSingleSessionClient: TermuxTerminalSingleSessionClient,
) :
    TermuxTerminalViewClientBase() {
    val mTermuxTerminalSingleSessionClient: TermuxTerminalSingleSessionClient
    val mFragment = fragment
    val mActivity = fragment.host

    /** Keeping track of the special keys acting as Ctrl and Fn for the soft keyboard and other hardware keys.  */
    var mVirtualControlKeyDown = false
    var mVirtualFnKeyDown = false
    private var mShowSoftKeyboardRunnable: Runnable? = null
    private var mShowSoftKeyboardIgnoreOnce = false
    private var mShowSoftKeyboardWithDelayOnce = false
    private var mTerminalCursorBlinkerStateAlreadySet = false

    fun onCreate() {
        mFragment.terminalView.setTextSize(mActivity.preferences.fontSize)
        mFragment.terminalView.keepScreenOn = mActivity.preferences.shouldKeepScreenOn
    }

    fun onResume() {
        setSoftKeyboardState(true, mActivity.isActivityRecreated)
        mTerminalCursorBlinkerStateAlreadySet = false
        if (mFragment.terminalView.mEmulator != null) {
            setTerminalCursorBlinkerState(true)
            mTerminalCursorBlinkerStateAlreadySet = true;
        }
    }

    fun onStop() {
        setTerminalCursorBlinkerState(false)
    }

    fun onReload() {
        // Show the soft keyboard if required
    }

    /**
     * Should be called when [com.termux.view.TerminalView.mEmulator] is set
     */
    override fun onEmulatorSet() {
        if (!mTerminalCursorBlinkerStateAlreadySet) {
            setTerminalCursorBlinkerState(true)
            mTerminalCursorBlinkerStateAlreadySet = true
        }
    }

    override fun onScale(scale: Float): Float {
        if (scale < 0.9f || scale > 1.1f) {
            val increase = scale > 1f
            changeFontSize(increase)
            return 1.0f
        }
        return scale
    }

    override fun onSingleTapUp(e: MotionEvent) {
        val term = mFragment.terminalView.currentSession.emulator
        if (!term.isMouseTrackingActive && !e.isFromSource(InputDevice.SOURCE_MOUSE)) {
            if (!KeyboardUtils.areDisableSoftKeyboardFlagsSet(mActivity))
                KeyboardUtils.showSoftKeyboard(mActivity, mFragment.terminalView);
        }
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean {
        return false
    }

    override fun shouldEnforceCharBasedInput(): Boolean {
        return true
    }

    override fun shouldUseCtrlSpaceWorkaround(): Boolean {
        return false
    }

    override fun isTerminalViewSelected(): Boolean {
        return false
    }

    override fun copyModeChanged(copyMode: Boolean) {
    }

    @SuppressLint("RtlHardcoded")
    override fun onKeyDown(keyCode: Int, e: KeyEvent, currentSession: TerminalSession): Boolean {
        if (handleVirtualKeys(keyCode, e, true)) return true
        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean {
        return handleVirtualKeys(keyCode, e, false)
    }

    /** Handle dedicated volume buttons as virtual keys if applicable.  */
    private fun handleVirtualKeys(keyCode: Int, event: KeyEvent, down: Boolean): Boolean {
        val inputDevice = event.device
        if (inputDevice != null && inputDevice.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC) {
            // Do not steal dedicated buttons from a full external keyboard.
            return false
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            mVirtualControlKeyDown = down
            return true
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            mVirtualFnKeyDown = down
            return true
        }
        return false
    }

    override fun readControlKey(): Boolean {
        return readExtraKeysSpecialButton(SpecialButton.CTRL) || mVirtualControlKeyDown
    }

    override fun readAltKey(): Boolean {
        return readExtraKeysSpecialButton(SpecialButton.ALT)
    }

    override fun readShiftKey(): Boolean {
        return readExtraKeysSpecialButton(SpecialButton.SHIFT)
    }

    override fun readFnKey(): Boolean {
        return readExtraKeysSpecialButton(SpecialButton.FN)
    }

    fun readExtraKeysSpecialButton(specialButton: SpecialButton): Boolean {
        return false
    }

    override fun onLongPress(event: MotionEvent): Boolean {
        return false
    }

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        if (mVirtualFnKeyDown) {
            var resultingKeyCode = -1
            var resultingCodePoint = -1
            var altDown = false
            val lowerCase: Char = Char(Char(codePoint).lowercaseChar().code)
            when (lowerCase) {
                'w' -> resultingKeyCode = KeyEvent.KEYCODE_DPAD_UP
                'a' -> resultingKeyCode = KeyEvent.KEYCODE_DPAD_LEFT
                's' -> resultingKeyCode = KeyEvent.KEYCODE_DPAD_DOWN
                'd' -> resultingKeyCode = KeyEvent.KEYCODE_DPAD_RIGHT
                'p' -> resultingKeyCode = KeyEvent.KEYCODE_PAGE_UP
                'n' -> resultingKeyCode = KeyEvent.KEYCODE_PAGE_DOWN
                't' -> resultingKeyCode = KeyEvent.KEYCODE_TAB
                'i' -> resultingKeyCode = KeyEvent.KEYCODE_INSERT
                'h' -> resultingCodePoint = '~'.code
                'u' -> resultingCodePoint = '_'.code
                'l' -> resultingCodePoint = '|'.code
                '1', '2', '3', '4', '5', '6', '7', '8', '9' -> resultingKeyCode =
                    codePoint - '1'.code + KeyEvent.KEYCODE_F1
                '0' -> resultingKeyCode = KeyEvent.KEYCODE_F10
                'e' -> resultingCodePoint =  /*Escape*/27
                '.' -> resultingCodePoint =  /*^.*/28
            }
            if (resultingKeyCode != -1) {
                val term = session.emulator
                session.write(
                    KeyHandler.getCode(
                        resultingKeyCode,
                        0,
                        term.isCursorKeysApplicationMode,
                        term.isKeypadApplicationMode
                    )
                )
            } else if (resultingCodePoint != -1) {
                session.writeCodePoint(altDown, resultingCodePoint)
            }
            return true
        }
        return false
    }

    private fun changeFontSize(increase: Boolean) {
        mActivity.preferences.fontSizeChange(increase)
        mFragment.terminalView.setTextSize(mActivity.preferences.fontSize)
    }

    /**
     * Called when user requests the soft keyboard to be toggled via "KEYBOARD" toggle button in
     * drawer or extra keys, or with ctrl+alt+k hardware keyboard shortcut.
     */
    fun onToggleSoftKeyboardRequest() {
        // If soft keyboard toggle behaviour is enable/disabled
        if (
            true
        ) {
            // If soft keyboard is visible
            if (!KeyboardUtils.areDisableSoftKeyboardFlagsSet(mActivity)) {
                Log.v(TAG, "Disabling soft keyboard on toggle")
                mActivity.preferences.softKeyboardEnabled = false
                KeyboardUtils.disableSoftKeyboard(mActivity, mFragment.terminalView)
            } else {
                // Show with a delay, otherwise pressing keyboard toggle won't show the keyboard after
                // switching back from another app if keyboard was previously disabled by user.
                // Also request focus, since it wouldn't have been requested at startup by
                // setSoftKeyboardState if keyboard was disabled. #2112
                Log.v(TAG, "Enabling soft keyboard on toggle")
                mActivity.preferences.softKeyboardEnabled = true
                KeyboardUtils.clearDisableSoftKeyboardFlags(mActivity)
                if (mShowSoftKeyboardWithDelayOnce) {
                    mShowSoftKeyboardWithDelayOnce = false
                    mFragment.terminalView.postDelayed(getShowSoftKeyboardRunnable(), 500)
                    mFragment.terminalView.requestFocus()
                } else KeyboardUtils.showSoftKeyboard(mActivity, mFragment.terminalView)
            }
        } else {
            // If soft keyboard is disabled by user for Termux
            if (!mActivity.preferences.softKeyboardEnabled) {
                Log.d(TAG, "Maintaining disabled soft keyboard on toggle")
                KeyboardUtils.disableSoftKeyboard(mActivity, mFragment.terminalView)
            } else {
                Log.v(TAG, "Showing/Hiding soft keyboard on toggle")
                KeyboardUtils.clearDisableSoftKeyboardFlags(mActivity)
                KeyboardUtils.toggleSoftKeyboard(mActivity)
            }
        }
    }

    fun setSoftKeyboardState(isStartup: Boolean, isReloadTermuxProperties: Boolean) {
        var noShowKeyboard = false
        if (KeyboardUtils.shouldSoftKeyboardBeDisabled(
                mActivity,
                mActivity.preferences.softKeyboardEnabled,
                mActivity.preferences.softKeyboardEnabledOnlyIfNoHardware
            )
        ) {
            Log.v(TAG, "Maintaining disabled soft keyboard")
            KeyboardUtils.disableSoftKeyboard(mActivity, mFragment.terminalView)
            mFragment.terminalView.requestFocus()
            noShowKeyboard = true
            if (isStartup && mActivity.isOnResumeAfterOnCreate)
                mShowSoftKeyboardWithDelayOnce = true
        } else {
            KeyboardUtils.setSoftInputModeAdjustResize(mActivity)
            KeyboardUtils.clearDisableSoftKeyboardFlags(mActivity)
//            if (isStartup && mActivity.getProperties().shouldSoftKeyboardBeHiddenOnStartup()) {
//                Logger.logVerbose(LOG_TAG, "Hiding soft keyboard on startup")
//                // Required to keep keyboard hidden when Termux app is switched back from another app
//                KeyboardUtils.setSoftKeyboardAlwaysHiddenFlags(mActivity)
//                KeyboardUtils.hideSoftKeyboard(mActivity, mActivity.getTerminalView())
//                mActivity.getTerminalView().requestFocus()
//                noShowKeyboard = true
//                // Required to keep keyboard hidden on app startup
//                mShowSoftKeyboardIgnoreOnce = true
//            }
        }
        mFragment.terminalView.setOnFocusChangeListener(object : View.OnFocusChangeListener {
            override fun onFocusChange(view: View?, hasFocus: Boolean) {
                if (hasFocus) {
                    if (mShowSoftKeyboardIgnoreOnce) {
                        mShowSoftKeyboardIgnoreOnce = false
                        return
                    }
                    Log.v(TAG, "Showing soft keyboard on focus change")
                } else {
                    Log.v(TAG, "Hiding soft keyboard on focus change")
                }
                KeyboardUtils.setSoftKeyboardVisibility(
                    getShowSoftKeyboardRunnable()!!,
                    mActivity,
                    mFragment.terminalView,
                    hasFocus
                )
            }
        })

        // Do not force show soft keyboard if termux-reload-settings command was run with hardware keyboard
        // or soft keyboard is to be hidden or is disabled
        if (!isReloadTermuxProperties && !noShowKeyboard) {
            // Request focus for TerminalView
            // Also show the keyboard, since onFocusChange will not be called if TerminalView already
            // had focus on startup to show the keyboard, like when opening url with context menu
            // "Select URL" long press and returning to Termux app with back button. This
            // will also show keyboard even if it was closed before opening url. #2111
            Log.v(TAG, "Requesting TerminalView focus and showing soft keyboard")
            mFragment.terminalView.requestFocus()
            mFragment.terminalView.postDelayed(getShowSoftKeyboardRunnable(), 300)
        }
    }

    private fun getShowSoftKeyboardRunnable(): Runnable? {
        if (mShowSoftKeyboardRunnable == null) {
            mShowSoftKeyboardRunnable =
                Runnable {
                    KeyboardUtils.showSoftKeyboard(
                        mActivity,
                        mFragment.terminalView
                    )
                }
        }
        return mShowSoftKeyboardRunnable
    }


    private fun setTerminalCursorBlinkerState(start: Boolean) {
        if (start) {
            // If set/update the cursor blinking rate is successful, then enable cursor blinker
            if (mFragment.terminalView.setTerminalCursorBlinkerRate(
                    mFragment.host.preferences.cursorBlinkRate
                )
            ) mFragment.terminalView
                .setTerminalCursorBlinkerState(true, true)
            else Log.e(
                TAG,
                "Failed to start cursor blinker"
            )
        } else {
            // Disable cursor blinker
            mFragment.terminalView.setTerminalCursorBlinkerState(false, true)
        }
    }


    companion object {
        private val TAG = "TermuxTerminalSingleViewClient"
    }

    init {
        mTermuxTerminalSingleSessionClient = termuxTerminalSingleSessionClient
    }
}
