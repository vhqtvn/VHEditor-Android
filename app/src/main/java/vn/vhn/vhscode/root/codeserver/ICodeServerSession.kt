package vn.vhn.vhscode.root.codeserver

import android.content.Context
import androidx.lifecycle.MutableLiveData

abstract class ICodeServerSession {
    enum class RunStatus {
        NOT_STARTED,
        STARTING,
        RUNNING,
        RESTARTING,
        ERROR,
        FINISHED
    }

    abstract val id: Int

    abstract val sessionName: String?

    abstract val liveServerLog: MutableLiveData<String>
    abstract val status: MutableLiveData<RunStatus>
    abstract val url: String
    abstract var title: String
    abstract val terminated: Boolean
    open val isRemote: Boolean = false

    abstract fun kill(context: Context)
    open fun sendInput(s: String) {}

    val pathsToOpen: MutableList<String> = mutableListOf<String>()

    fun openPath(path: String) {
        pathsToOpen.add(path)
    }

    sealed class InputState() {
        companion object C {
            class None : InputState()
            class Password(val message: String) : InputState()
            data class ReinstallConfirmation(val message: String) : InputState()
        }
    }

    abstract val inputState: MutableLiveData<InputState>
}