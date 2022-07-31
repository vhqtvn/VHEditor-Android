package vn.vhn.vhscode.root.codeserver

import android.content.Context
import androidx.lifecycle.MutableLiveData
import java.util.*

class SharedCodeServerSession(
    override val id: Int,
    baseService: CodeServerLocalService,
    override val sessionName: String?,
    private val mUrl: String? = null,
) : ICodeServerSession {
    val mHandle = UUID.randomUUID().toString()

    var mBase: CodeServerLocalService = baseService
    val base get() = mBase

    init {
        register()
    }

    override val liveServerLog: MutableLiveData<String>
        get() = mBase.liveServerLog
    override val status: MutableLiveData<ICodeServerSession.RunStatus>
        get() = mBase.status
    override val url: String
        get() = mUrl ?: mBase.url

    override var title: String = ""
    override var terminated: Boolean = false

    override fun kill(context: Context) {
        mBase.refs.remove(id)
        terminated = true
    }

    fun startMigratingBase() {
        mBase.refs.remove(id)
    }

    fun finishMigratingBase() {}

    fun migrateBase(newBase: CodeServerLocalService) {
        mBase = newBase
        register()
    }

    fun register() {
        mBase.refs.add(id)
    }
}