package vn.vhn.vhscode.root.codeserver

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import java.util.*

class ProxyMutableLiveData<T>(
    private var mTarget: MutableLiveData<T>,
    private val combiner: (prev: T?, current: T?) -> T,
) : MutableLiveData<T>(mTarget.value) {
    private var mLastValue: T = combiner(null, null)
    private var isObserving = false
    private val localObserver = androidx.lifecycle.Observer<T> {
        val combined = combiner(mLastValue, it)
        if (combined != value) postValue(combined)
    }

    fun setNewTarget(target: MutableLiveData<T>) {
        if (mTarget != target) {
            stopObserve()
            localObserver.onChanged(mTarget.value) // just for sure
            mLastValue = combiner(mLastValue, value)
            mTarget = target
        }
    }

    private fun startObserve(target: MutableLiveData<T>) {
        setNewTarget(target)
        if (!isObserving) {
            isObserving = true
            mTarget.observeForever(localObserver)
        }
    }

    fun stopObserve() {
        if (isObserving) {
            mTarget.removeObserver(localObserver)
            isObserving = false
        }
    }

    override fun observeForever(observer: Observer<in T>) {
        checkStartObserver()
        super.observeForever(observer)
    }

    override fun observe(owner: LifecycleOwner, observer: Observer<in T>) {
        checkStartObserver()
        super.observe(owner, observer)
    }

    private fun checkStartObserver() {
        if (!hasActiveObservers()) startObserve(mTarget)
    }

    override fun removeObserver(observer: Observer<in T>) {
        super.removeObserver(observer)
        checkStopObserve()
    }

    override fun removeObservers(owner: LifecycleOwner) {
        super.removeObservers(owner)
        checkStopObserve()
    }

    private fun checkStopObserve() {
        if (!hasActiveObservers()) stopObserve()
    }
}

class SharedLocalCodeServerSession(
    override val id: Int,
    baseService: CodeServerLocalService,
    override val sessionName: String?,
    private val mUrl: String? = null,
) : ICodeServerSession() {
    val mHandle = UUID.randomUUID().toString()

    var mBase: CodeServerLocalService = baseService
    val base get() = mBase

    override val liveServerLog = ProxyMutableLiveData(baseService.liveServerLog) { prev, cur ->
        if (cur == null || prev == null) ""
        else prev + cur
    }
    override val status =
        ProxyMutableLiveData(mBase.status) { _, cur -> cur ?: RunStatus.ERROR }
    override val url: String
        get() = mUrl ?: mBase.url

    override var title: String = ""
    override var terminated: Boolean = false

    override fun kill(context: Context) {
        mBase.refs.remove(id)
        terminated = true
    }

    override val inputState: MutableLiveData<InputState> = MutableLiveData(InputState.C.None())

    init {
        register()
    }

    fun startMigratingBase() {
        liveServerLog.stopObserve()
        mBase.refs.remove(id)
        status.postValue(RunStatus.RESTARTING)
    }

    fun finishMigratingBase() {}

    fun migrateBase(newBase: CodeServerLocalService) {
        mBase = newBase
        register()
    }

    @Synchronized
    fun register() {
        if (!mBase.refs.contains(id)) {
            mBase.refs.add(id)
            liveServerLog.setNewTarget(mBase.liveServerLog)
            status.setNewTarget(mBase.status)
        }
    }
}