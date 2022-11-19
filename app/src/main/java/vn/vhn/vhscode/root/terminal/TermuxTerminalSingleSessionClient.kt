package vn.vhn.vhscode.root.terminal

import android.R.attr
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.TextUtils
import android.util.Log
import com.termux.shared.interact.ShareUtils
import com.termux.shared.shell.TermuxSession
import com.termux.shared.terminal.TermuxTerminalSessionClientBase
import com.termux.terminal.TerminalSession
import com.termux.terminal.TextStyle
import vn.vhn.vhscode.R
import vn.vhn.vhscode.root.fragments.TerminalFragment


class TermuxTerminalSingleSessionClient(fragment: TerminalFragment) :
    TermuxTerminalSessionClientBase(), TermuxSession.TermuxSessionClient {

    companion object {
        private const val LOG_TAG = "TermuxTerminalSingleSessionClient"
    }

    private val mFragment = fragment

    //
//    fun onCreate() {
//        // Set terminal fonts and colors
//        checkForFontAndColors()
//    }
//
//    fun onStart() {
////        if (mActivity.termuxService != null) {
////            setCurrentSession(getCurrentStoredSessionOrLast())
////            termuxSessionListNotifyUpdated()
////        }
//
//        // The current terminal session may have changed while being away, force
//        // a refresh of the displayed terminal.
//        mFragment.terminalView.onScreenUpdated()
//    }
//
    fun onResume() {
        mFragment.terminalView.onScreenUpdated()
    }
//
//    /**
//     * Should be called when mActivity.onStop() is called
//     */
//    fun onStop() {
//        // Store current session in shared preferences so that it can be restored later in
//        // {@link #onStart} if needed.
////        setCurrentStoredSession()
//    }
//
//    /**
//     * Should be called when mActivity.reloadActivityStyling() is called
//     */
//    fun onReload() {
//        // Set terminal fonts and colors
//        checkForFontAndColors()
//    }


    override fun onTextChanged(changedSession: TerminalSession) {
        if (!mFragment.userVisible) return
        mFragment.terminalView.onScreenUpdated()
    }

    override fun onTitleChanged(updatedSession: TerminalSession) {
        mFragment.host.postUpdateSessionsListView(1)
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        mFragment.host.postOnSessionFinished()
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        ShareUtils.copyTextToClipboard(mFragment.requireContext(),
            text,
            mFragment.getString(R.string.copied))

    }

    override fun onPasteTextFromClipboard(session: TerminalSession) {
        if (!mFragment.userVisible) return

        val clipData =
            (mFragment.context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?)?.primaryClip
        if (clipData != null) {
            val paste = clipData.getItemAt(0).coerceToText(mFragment.context)
            if (!TextUtils.isEmpty(paste)) mFragment.terminalView.mEmulator.paste(paste.toString())
        }
    }

    override fun onBell(session: TerminalSession) {
    }

    override fun onColorsChanged(changedSession: TerminalSession) {
        updateBackgroundColor();
    }

    override fun onTerminalCursorStateChange(enabled: Boolean) {
        if (!mFragment.userVisible) return
        mFragment.terminalView.setTerminalCursorBlinkerState(enabled, false)
    }

//    fun notifyOfSessionChange() {
//        //
//    }
//
//    fun checkForFontAndColors() {
//
//    }
//
//    fun updateBackgroundColor() {
////        if (!mActivity.isVisible()) return
////        val session = mActivity.currentSession
////        if (session != null && session.emulator != null) {
////            mActivity.window.decorView.setBackgroundColor(session.emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND])
////        }
//    }
//
//    fun checkAndScrollToSession(session: TerminalSession?) {
//
//    }

//    fun switchToSession(forward: Boolean) {
//        val service = mActivity.termuxService ?: return
//        val currentTerminalSession = mActivity.currentSession!!
//        var index: Int = service.getIndexOfSession(currentTerminalSession)
//        val size = service.termuxSessionsSize
//        if (forward) {
//            if (++index >= size) index = 0
//        } else {
//            if (--index < 0) index = size - 1
//        }
//        val termuxSession = service.getTermuxSession(index)
//        if (termuxSession != null) setCurrentSession(termuxSession.terminalSession)
//    }
//
//    fun switchToSession(index: Int) {
//        val service = mActivity.termuxService ?: return
//        val termuxSession = service.getTermuxSession(index)
//        if (termuxSession != null) setCurrentSession(termuxSession.terminalSession)
//    }

//    fun addNewSession(isFailSafe: Boolean, sessionName: String?) {
//        val service = mActivity.termuxService ?: return
//        if (service.termuxSessionsSize >= MAX_SESSIONS) {
//            AlertDialog.Builder(mActivity).setTitle(R.string.title_max_terminals_reached)
//                .setMessage(R.string.msg_max_terminals_reached)
//                .setPositiveButton(android.R.string.ok, null).show()
//        } else {
//            val currentSession: TerminalSession? = mActivity.currentSession
//            val workingDirectory: String = if (currentSession == null) {
//                CodeServerService.HOME_PATH
//            } else {
//                currentSession.cwd
//            }
//            val newTermuxSession: TermuxSession = service.createTermuxSession(
//                null,
//                null,
//                null,
//                workingDirectory,
//                isFailSafe,
//                sessionName
//            )
//                ?: return
//            val newTerminalSession = newTermuxSession.terminalSession
//            setCurrentSession(newTerminalSession)
////            mActivity.getDrawer().closeDrawers()
//        }
//    }

    override fun onTermuxSessionExited(termuxSession: TermuxSession?) {
        if (termuxSession != null)
            mFragment.host.codeServerService?.globalSessionsManager?.sessionsHost?.apply {
                onTermuxSessionExited(termuxSession)
            }
    }

    fun checkForFontAndColors() {
//        try {
//            val colorsFile: File = TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE
//            val fontFile: File = TermuxConstants.TERMUX_FONT_FILE
//            val props = Properties()
//            if (colorsFile.isFile()) {
//                FileInputStream(colorsFile).use { `in` -> props.load(`in`) }
//            }
//            TerminalColors.COLOR_SCHEME.updateWith(props)
//            val session: TerminalSession = mActivity.getCurrentSession()
//            if (session != null && session.emulator != null) {
//                session.emulator.mColors.reset()
//            }
//            updateBackgroundColor()
//            val newTypeface =
//                if (fontFile.exists() && fontFile.length() > 0) Typeface.createFromFile(fontFile) else Typeface.MONOSPACE
//            mActivity.getTerminalView().setTypeface(newTypeface)
//        } catch (e: Exception) {
//            Logger.logStackTraceWithMessage(
//                TermuxSharedProperties.LOG_TAG,
//                "Error in checkForFontAndColors()",
//                e
//            )
//        }
    }

    private fun updateBackgroundColor() {
        if (!mFragment.userVisible) return
        mFragment.terminalView.mTermSession?.emulator?.apply {
            mFragment.terminalView.setBackgroundColor(
                mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND]
            )
        }
    }

}
