package vn.vhn.vhscode.root.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import vn.vhn.vhscode.preferences.EditorHostPrefs
import vn.vhn.vhscode.root.EditorHostActivity
import vn.vhn.vhscode.service_features.SessionsHost

class GlobalSessionsManager() {
    enum class SessionType {
        TERMUX_TERMINAL,
        CODESERVER_EDITOR,
        CODESERVER_REMOTE_MANAGED_EDITOR,
        REMOTE_CODESERVER_EDITOR;

        companion object {
            val Count = values().size
            val Values = values()
        }
    }

    companion object {
        private const val MAX_SESSIONS = 8
        private const val LOG_TAG = "GlobalSessionManager"

        private var _EXECUTION_ID = 1000

        @Synchronized
        fun getNextSessionId(sess: SessionType): Int {
            val targetMod = sess.ordinal
            while (++_EXECUTION_ID % SessionType.Count != targetMod);
            return _EXECUTION_ID;
        }

        fun getSessionType(sessionId: Int): SessionType {
            val ord = sessionId % SessionType.Count
            return SessionType.Values[ord]
        }
    }

    private var _activity: EditorHostActivity? = null
    public var activity: EditorHostActivity?
        get() = _activity
        set(x) {
            _activity = x
        }

    public val preferences: EditorHostPrefs?
        get() = activity?.preferences

    private var _sessionsHost: SessionsHost? = null
    public var sessionsHost: SessionsHost?
        get() = _sessionsHost
        set(x) {
            _sessionsHost = x
        }

    fun notifySessionsListUpdated() {
        CoroutineScope(Dispatchers.Main).launch {
            activity?.setSessionsListView()
        }
    }

    fun notifyMaxTerminalSessionsReached() {
        activity?.notifyMaxTerminalsReached()
    }

    fun notifyMaxCodeEditorSessionsReached() {
        activity?.notifyMaxEditorsReached()
    }
}