package vn.vhn.vhscode.service_features

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.termux.shared.file.FileUtils
import com.termux.shared.interact.TextInputDialogUtils
import com.termux.shared.logger.Logger
import com.termux.shared.models.ExecutionCommand
import com.termux.shared.shell.TermuxSession
import com.termux.shared.terminal.TermuxTerminalSessionClientBase
import com.termux.shared.termux.TermuxConstants
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import vn.vhn.vhscode.CodeServerService
import vn.vhn.vhscode.R
import vn.vhn.vhscode.root.codeserver.CodeServerSession
import vn.vhn.vhscode.root.terminal.GlobalSessionsManager
import vn.vhn.vhscode.root.terminal.VHEditorShellEnvironmentClient
import java.util.*

class SessionsHost(
    context: CodeServerService,
    globalSessionsManager: GlobalSessionsManager,
) : TermuxSession.TermuxSessionClient {
    companion object {
        const val TAG = "SessionsHost"

        open class SessionWrapper()
        data class SessionCaseTerminal(val session: TermuxSession?) : SessionWrapper()
        data class SessionCaseCodeEditor(val session: CodeServerSession?) : SessionWrapper()

        private val MAX_TERMINAL_SESSIONS = 10
        private val MAX_CODESERVER_LOCAL_SESSIONS = 1
    }

    init {
        globalSessionsManager.sessionsHost = this
    }

    private val mContext = context

    var mWantsToStop = false
    val mSessions: MutableList<SessionWrapper> = ArrayList()
    val mTermuxSessions: MutableList<TermuxSession?> = ArrayList()
    val mCodeServerSessions: MutableList<CodeServerSession?> = ArrayList()
    val mGlobalSessionsManager: GlobalSessionsManager = globalSessionsManager
    val mTermuxTerminalSessionClientBase = TermuxTerminalSessionClientBase()

    @Synchronized
    fun createCodeEditorSession(
        id: Int,
        sessionName: String?,
        listenOnAllInterface: Boolean,
        useSSL: Boolean,
        remote: Boolean = false,
        remoteURL: String? = null,
        port: Int? = null
    ): CodeServerSession? {
        if (!remote && mCodeServerSessions.filter { it?.remote == false }.size >= MAX_CODESERVER_LOCAL_SESSIONS) {
            mGlobalSessionsManager.notifyMaxCodeEditorSessionsReached()
            return null
        }
        val newEditorSession = CodeServerSession(
            id,
            mContext,
            listenOnAllInterface,
            useSSL,
            remote,
            remoteURL,
            port
        )
        newEditorSession.start()
        mCodeServerSessions.add(newEditorSession)
        mSessions.add(SessionCaseCodeEditor(newEditorSession))

        mGlobalSessionsManager.notifySessionsListUpdated()
        updateNotification()
        return newEditorSession

    }

    @Synchronized
    fun createTermuxSession(
        executionCommand: ExecutionCommand?,
        sessionName: String?,
        termuxTerminalSessionClient: TerminalSessionClient? = null,
    ): TermuxSession? {
        if (executionCommand == null) return null
        if (executionCommand.isPluginExecutionCommand) return null

        if (mTermuxSessions.size >= MAX_TERMINAL_SESSIONS) {
            mGlobalSessionsManager.notifyMaxTerminalSessionsReached()
            return null
        }

        Log.d(
            TAG,
            "Creating \"" + executionCommand.commandIdAndLabelLogString + "\" TermuxSession"
        )
        if (executionCommand.inBackground) {
            Log.d(
                TAG,
                "Ignoring a background execution command passed to createTermuxSession()"
            )
            return null
        }
        // If the execution command was started for a plugin, only then will the stdout be set
        // Otherwise if command was manually started by the user like by adding a new terminal session,
        // then no need to set stdout
        executionCommand.terminalTranscriptRows = 30
        val newTermuxSession = TermuxSession.execute(
            mContext,
            executionCommand,
            termuxTerminalSessionClient ?: mTermuxTerminalSessionClientBase,
            this,
            VHEditorShellEnvironmentClient(),
            sessionName,
            executionCommand.isPluginExecutionCommand
        )
        if (newTermuxSession == null) {
            Log.e(
                TAG,
                """
                Failed to execute new TermuxSession command for:
                ${executionCommand.commandIdAndLabelLogString}
                """.trimIndent()
            )
            Log.e(
                TAG, executionCommand.toString()
            )
            return null
        }
        mTermuxSessions.add(newTermuxSession)
        mSessions.add(SessionCaseTerminal(newTermuxSession))

        mGlobalSessionsManager.notifySessionsListUpdated()
        updateNotification()
        return newTermuxSession
    }


    fun onDestroy() {
        Log.d(TAG, "onDestroy")

        FileUtils.clearDirectory(
            "\$TMPDIR",
            FileUtils.getCanonicalPath(CodeServerService.TMP_PATH, null)
        )

        actionReleaseWakeLock(false)
        if (!mWantsToStop) {
            killAllSessions()
        }
    }

    fun killAllSessions() {
        killAllTermuxExecutionCommands()
        killAllCodeServerExecutionCommands()
    }

    @Synchronized
    private fun killAllCodeServerExecutionCommands() {
        Log.d(
            TAG,
            "Killing EditorSessions=" + mCodeServerSessions.size
        )
        val editorSessions = ArrayList(mCodeServerSessions)
        for (i in editorSessions.indices) {
            editorSessions[i]?.apply {
                killIfExecuting(mContext)
                try {
                    Log.d(TAG, "kill codeserver $title -> ${process?.exitValue()}")
                } catch (e: Exception) {
                    Log.d(TAG, "kill codeserver $title -> CHECK_ERROR")
                }
            }
        }
    }

    @Synchronized
    private fun killAllTermuxExecutionCommands() {
        Log.d(
            TAG,
            "Killing TermuxSessions=" + mTermuxSessions.size
        )
        val processResult = false //mWantsToStop
        val termuxSessions = ArrayList(mTermuxSessions)
        for (i in termuxSessions.indices) {
            termuxSessions[i]?.apply {
                killIfExecuting(mContext, processResult)
                Log.d(TAG,
                    "kill termux ${executionCommand.executable} -> ${executionCommand.isExecuting}")
            }
        }
    }

    private var mWakeLock: PowerManager.WakeLock? = null
    private var mWifiLock: WifiManager.WifiLock? = null

    val hasWakeLock: Boolean
        get() = mWakeLock != null

    val hasWifiLock: Boolean
        get() = mWifiLock != null

    /** Process action to acquire Power and Wi-Fi WakeLocks.  */
    @SuppressLint("WakelockTimeout", "BatteryLife")
    private fun actionAcquireWakeLock() {
        if (mWakeLock != null) {
            Log.d(
                TAG,
                "Ignoring acquiring WakeLocks since they are already held"
            )
            return
        }
        Log.d(TAG, "Acquiring WakeLocks")
        val pm = mContext.getSystemService(Service.POWER_SERVICE) as PowerManager
        mWakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, TermuxConstants.TERMUX_APP_NAME.lowercase(
                Locale.getDefault()
            ) + ":service-wakelock"
        )
        mWakeLock?.acquire()

        // http://tools.android.com/tech-docs/lint-in-studio-2-3#TOC-WifiManager-Leak
        val wm = mContext.applicationContext.getSystemService(Service.WIFI_SERVICE) as WifiManager
        mWifiLock = wm.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF, TermuxConstants.TERMUX_APP_NAME.lowercase(
                Locale.getDefault()
            )
        )
        mWifiLock?.acquire()
        val packageName = mContext.packageName
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val whitelist = Intent()
            whitelist.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            whitelist.data = Uri.parse("package:$packageName")
            whitelist.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            try {
                mContext.startActivity(whitelist)
            } catch (e: ActivityNotFoundException) {
                Logger.logStackTraceWithMessage(
                    TAG,
                    "Failed to call ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
                    e
                )
            }
        }
        updateNotification()
        Log.d(TAG, "WakeLocks acquired successfully")
    }

    /** Process action to release Power and Wi-Fi WakeLocks.  */
    private fun actionReleaseWakeLock(updateNotification: Boolean) {
        if (mWakeLock == null && mWifiLock == null) {
            Log.d(
                TAG,
                "Ignoring releasing WakeLocks since none are already held"
            )
            return
        }
        Log.d(TAG, "Releasing WakeLocks")
        mWakeLock?.release()
        mWakeLock = null
        mWifiLock?.release()
        mWifiLock = null
        if (updateNotification) updateNotification()
        Log.d(TAG, "WakeLocks released successfully")
    }

    override fun onTermuxSessionExited(termuxSession: TermuxSession?) {
        if (termuxSession != null) {
            mTermuxSessions.remove(termuxSession)
            mSessions.removeIf { it is SessionCaseTerminal && it.session == termuxSession }

            mGlobalSessionsManager.notifySessionsListUpdated()
        }

        updateNotification()
    }

    fun onVSCodeSessionExited(session: CodeServerSession?) {
        if (session != null) {
            mCodeServerSessions.remove(session)
            mSessions.removeIf { it is SessionCaseCodeEditor && it.session == session }

            mGlobalSessionsManager.notifySessionsListUpdated()
        }

        updateNotification()
    }

    private fun updateNotification() {
        mContext.updateNotification()
    }

    public val isSessionsEmpty: Boolean
        get() {
            return mSessions.isEmpty()
        }

    val sessionsCount: Int
        get() {
            return mSessions.size
        }

    @Synchronized
    public fun getSessions(): List<SessionWrapper>? {
        return mSessions
    }

    @Synchronized
    fun getTerminalSessionForCommandId(commandId: Int?): TerminalSession? {
        if (commandId == null) return null
        for (session in mTermuxSessions) {
            if (session!!.executionCommand.id == commandId) return session.terminalSession
        }
        return null
    }

    @Synchronized
    fun cleanup() {
        for (session in ArrayList(mTermuxSessions)) {
            session!!.finish()
            onTermuxSessionExited(session)
        }
        for (session in ArrayList(mCodeServerSessions)) {
            session!!.killIfExecuting(mContext)
            onVSCodeSessionExited(session)
        }
    }

    @Synchronized
    fun cleanupTerminalSessionForCommandId(commandId: Int?): Boolean {
        if (commandId == null) return false
        for (session in mTermuxSessions) {
            if (session!!.executionCommand.id == commandId) {
                session!!.finish()
                onTermuxSessionExited(session)
                return true
            }
        }
        return false
    }

    @Synchronized
    fun killTerminalSessionForCommandId(commandId: Int?): Boolean {
        if (commandId == null) return false
        for (session in mTermuxSessions) {
            if (session!!.executionCommand.id == commandId) {
                session!!.killIfExecuting(mContext, false)
                return true
            }
        }
        return false
    }

    @Synchronized
    fun killVSCodeSessionForId(id: Int?): Boolean {
        if (id == null) return false
        for (session in mCodeServerSessions) {
            if (session!!.id == id) {
                session!!.killIfExecuting(mContext)
                return true
            }
        }

        return false
    }

    @Synchronized
    fun cleanupVSCodeSessionForId(id: Int?): Boolean {
        if (id == null) return false
        for (session in mCodeServerSessions) {
            if (session!!.id == id) {
                session!!.killIfExecuting(mContext)
                onVSCodeSessionExited(session)
                return true
            }
        }

        return false
    }

    @Synchronized
    fun getVSCodeSessionForId(id: Int?): CodeServerSession? {
        if (id == null) return null
        for (session in mCodeServerSessions) {
            if (session!!.id == id) return session
        }
        return null
    }

    fun setDefaultSessionClient(termSession: TerminalSession) {
        termSession.updateTerminalSessionClient(mTermuxTerminalSessionClientBase)
    }

    @SuppressLint("InflateParams")
    fun renameSession(ctx: Activity, sessionToRename: TerminalSession?) {
        if (sessionToRename == null) return
        TextInputDialogUtils.textInput(
            ctx,
            R.string.title_rename_session,
            sessionToRename.mSessionName,
            R.string.action_rename_session_confirm,
            { text: String? ->
                sessionToRename.mSessionName = text
                mGlobalSessionsManager.notifySessionsListUpdated()
            },
            -1,
            null,
            -1,
            null,
            null
        )
    }

    fun onBinding() {
        //
    }

    fun onUnbinding() {
        resetTermuxSessionClients()
    }

    public fun resetTermuxSessionClients() {
        for (i in 0 until mTermuxSessions.size) {
            mTermuxSessions[i]!!.terminalSession.updateTerminalSessionClient(
                mTermuxTerminalSessionClientBase
            )
        }
    }

    fun hasAliveSession(): Boolean {
        for (i in 0 until mTermuxSessions.size) {
            if (mTermuxSessions[i]!!.terminalSession.isRunning) return true
        }
        for (i in 0 until mCodeServerSessions.size) {
            if (mCodeServerSessions[i]!!.status.value == CodeServerSession.Companion.RunStatus.RUNNING) return true
        }
        return false
    }
}