package vn.vhn.vhscode.root.codeserver

import android.content.Context
import androidx.lifecycle.MutableLiveData
import java.util.*

class UnmanagedRemoteCodeServerSession(
    override val id: Int,
    override val sessionName: String?,
    override val url: String,
) : ICodeServerSession() {
    val mHandle = UUID.randomUUID().toString()

    override val isRemote = true

    override val liveServerLog = MutableLiveData<String>()
    override val status = MutableLiveData(RunStatus.RUNNING)

    override var title: String = ""
    override var terminated: Boolean = false
    override val inputState: MutableLiveData<InputState> = MutableLiveData(InputState.C.None())

    override fun kill(context: Context) {
        terminated = true
    }
}