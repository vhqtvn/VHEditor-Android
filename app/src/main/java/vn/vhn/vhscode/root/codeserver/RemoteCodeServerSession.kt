package vn.vhn.vhscode.root.codeserver

import android.content.Context
import androidx.lifecycle.MutableLiveData
import java.util.*

class RemoteCodeServerSession(
    override val id: Int,
    override val sessionName: String?,
    override val url: String,
) : ICodeServerSession() {
    val mHandle = UUID.randomUUID().toString()

    override val liveServerLog = MutableLiveData<String>()
    override val status = MutableLiveData<ICodeServerSession.RunStatus>()

    override var title: String = ""
    override var terminated: Boolean = false

    override fun kill(context: Context) {
        terminated = true
    }
}