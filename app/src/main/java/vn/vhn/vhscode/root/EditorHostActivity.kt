package vn.vhn.vhscode.root

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.drawerlayout.widget.DrawerLayout.DrawerListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.termux.shared.models.ExecutionCommand
import eightbitlab.com.blurview.RenderScriptBlur
import james.crasher.Crasher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import vn.vhn.vhscode.features.orientation.IScreenOrientationLocker
import vn.vhn.vhscode.features.orientation.createScreenOrientationLocker
import vn.vhn.vhscode.BuildConfig
import vn.vhn.vhscode.CodeServerService
import vn.vhn.vhscode.R
import vn.vhn.vhscode.compat.OrientationCompat
import vn.vhn.vhscode.databinding.ActivityEditorHostBinding
import vn.vhn.vhscode.preferences.EditorHostPrefs
import vn.vhn.vhscode.root.fragments.TerminalFragment
import vn.vhn.vhscode.root.fragments.VSCodeFragment
import vn.vhn.vhscode.root.gesture_recognizer.EditorHostGestureRecognizer
import vn.vhn.vhscode.root.helpers.DrawerLayoutDrawerController
import vn.vhn.vhscode.root.terminal.GlobalSessionsManager
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.min
import kotlin.properties.Delegates


private const val ARG_ACTIVITY_RECREATED = "activity_recreated"

class EditorHostActivity : FragmentActivity(), ServiceConnection,
    EditorHostGestureRecognizer.EditorHostGestureRecognizerListener {
    companion object {
        private const val TAG = "EditorHostActivity"
    }

    private lateinit var binding: ActivityEditorHostBinding

    private var mIsVisible = false
    private var mInitialResume = true
    private var mIsOnResumeAfterOnCreate = false
    private var mIsActivityRecreated = false

    private var mCodeServerService: CodeServerService? = null
    lateinit var preferences: EditorHostPrefs
    var mDrawerLayoutDrawerController: DrawerLayoutDrawerController? = null

    private val mEditorHostAdapter = EditorHostAdapter(this, this)
    private val mEditorHostGestureRecognizer = EditorHostGestureRecognizer(this, this)
    private lateinit var mListViewAdapter: SessionsListAdapter
    private var mCurrentEditorFragmentId: Long? = null
    private var mCurrentEditorFragment: VSCodeFragment? = null
    private var mCurrentTerminalFragmentId: Long? = null
    private var mCurrentTerminalFragment: TerminalFragment? = null
    private val mVisibleFragments = HashMap<Long, WeakReference<Fragment>>()
    private var dipInPixels by Delegates.notNull<Float>()

    private val mOnViewPagerPageChange = object : OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            if (!updateCurrentFragment(position)) {
                binding.viewPager.postDelayed({
                    updateCurrentFragment(position)
                }, 250)
            }
            binding.sessions.postDelayed({
                configureOverlayView()
            }, 100)
        }
    }

    private var mOrientationLockerCreationTries = 0
    private var mOrientationLockerImpl: IScreenOrientationLocker? = null
    private val orientationLocker: IScreenOrientationLocker?
        get() {
            //there should be no race here
            if (mOrientationLockerImpl == null && mOrientationLockerCreationTries <= 5) {
                mOrientationLockerCreationTries++
                mOrientationLockerImpl = createScreenOrientationLocker(this)
            }
            return mOrientationLockerImpl
        }

    @Synchronized
    private fun updateCurrentFragment(position: Int): Boolean {
        val fragmentId = mEditorHostAdapter.getItemId(position)
        if (!mVisibleFragments.containsKey(fragmentId)) return false
        val weakFragment = mVisibleFragments.get(fragmentId)
        val fragment = weakFragment?.get()
        var hasSet = false
        when (fragment) {
            is VSCodeFragment -> {
                mCurrentEditorFragment = fragment
                mCurrentEditorFragmentId = fragmentId
                mCurrentTerminalFragment = null
                mCurrentTerminalFragmentId = null
                binding.overlayControlButtonSettings.visibility = View.VISIBLE
                hasSet = true
            }
            is TerminalFragment -> {
                mCurrentEditorFragment = null
                mCurrentEditorFragmentId = null
                mCurrentTerminalFragment = fragment
                mCurrentTerminalFragmentId = fragmentId
                binding.overlayControlButtonSettings.visibility = View.GONE
                hasSet = true
            }
        }
        return hasSet
    }

    override fun onStart() {
        super.onStart()
        mIsVisible = true
    }

    override fun onStop() {
        super.onStop()
        mIsVisible = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (!BuildConfig.GOOGLEPLAY_BUILD)
            Crasher(applicationContext)
        mIsOnResumeAfterOnCreate = true

        if (savedInstanceState != null)
            mIsActivityRecreated = savedInstanceState.getBoolean(ARG_ACTIVITY_RECREATED, false)

        super.onCreate(savedInstanceState)

        preferences = EditorHostPrefs(this)

        binding = ActivityEditorHostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewPager.setPageTransformer { page, position ->
            page.alpha = 1.0f - min(1.0f, abs(position))
        }
        binding.viewPager.adapter = mEditorHostAdapter
        binding.viewPager.isUserInputEnabled = false
        mDrawerLayoutDrawerController = DrawerLayoutDrawerController(binding.drawerLayout)
        binding.viewPager.registerOnPageChangeCallback(mOnViewPagerPageChange)

        mListViewAdapter = SessionsListAdapter(this, mEditorHostAdapter.dataArray)
        binding.sessions.apply {
            adapter = mListViewAdapter
            onItemClickListener = mListViewAdapter
            onItemLongClickListener = mListViewAdapter
            onItemSelectedListener = sessionsSelectedListener
        }

        val serviceIntent = Intent(this, CodeServerService::class.java)
        startService(serviceIntent)

        if (!bindService(serviceIntent, this, 0))
            throw RuntimeException("bindService() failed")

        binding.blurView.setupWith(binding.root)
            .setBlurAlgorithm(RenderScriptBlur(this))
            .setBlurRadius(3f)
            .setBlurAutoUpdate(true)
        binding.btnGroupBlurView.setupWith(binding.root)
            .setBlurAlgorithm(RenderScriptBlur(this))
            .setBlurRadius(1f)
            .setBlurAutoUpdate(true)

        dipInPixels = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            1f,
            resources.displayMetrics
        )

        binding.drawerLayout.addDrawerListener(drawerListener)

        mOrientationLockerNeedsRelock = true
    }

    private var mCurrentDefaultKeyboard: String? = null
    override fun onResume() {
        super.onResume()

        preferences.lockedKeyboard?.also {
            try {
                val currentKB =
                    Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                Settings.Secure.putString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD, it)
                mCurrentDefaultKeyboard = currentKB
            } catch (e: Exception) {
                Toast.makeText(this, R.string.failed_to_set_keyboard, Toast.LENGTH_SHORT).show()
            }
        }

        mIsOnResumeAfterOnCreate = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (preferences.fullScreen) {
                window.insetsController?.hide(WindowInsets.Type.statusBars())
            } else {
                window.insetsController?.show(WindowInsets.Type.statusBars())
            }
        } else {
            val attrs: WindowManager.LayoutParams = window.attributes
            if (preferences.fullScreen) {
                attrs.flags = attrs.flags or WindowManager.LayoutParams.FLAG_FULLSCREEN
            } else {
                attrs.flags = attrs.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN.inv()
            }
            window.attributes = attrs
        }

        if (mOrientationLockerNeedsRelock) {
            updateLockOrientationFromPreferences()
        }
        mOrientationLockerSkipRelease = false
        updateLockKeyboardLabel()
    }

    private var mSavedAccelerometerRotationEnabled: Boolean? = null
    private var mOrientationLockerNeedsRelock = true
    private var mOrientationLockerSkipRelease = false
    private fun updateLockOrientationFromPreferences() {
        val screenOrientation = preferences.lockedOrientation
        if (screenOrientation == null) {
            binding.overlayControlButtonLockOrientation.text =
                resources.getString(R.string.overlay_btn_lock_orientation_unlocked)
            mOrientationLockerImpl?.unlock()
        } else {
            var lockSuccess = false
            try {
                if (orientationLocker?.lock(screenOrientation) == true) {
                    binding.overlayControlButtonLockOrientation.text =
                        resources.getString(R.string.overlay_btn_lock_orientation_locked)
                    lockSuccess = true
                }
            } catch (e: Exception) {
            }
            if (!lockSuccess)
                Toast.makeText(this, R.string.failed_to_lock_orientation, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(ARG_ACTIVITY_RECREATED, true)
    }

    override fun onPause() {
        super.onPause()
        if (!mOrientationLockerSkipRelease) {
            mOrientationLockerNeedsRelock = true
            mOrientationLockerImpl?.unlock()
        }
        mCurrentDefaultKeyboard?.also {
            Settings.Secure.putString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD, it)
            mCurrentDefaultKeyboard = null
        }
    }

    override fun onDestroy() {
        binding.drawerLayout.removeDrawerListener(drawerListener)
        binding.viewPager.unregisterOnPageChangeCallback(mOnViewPagerPageChange)

        super.onDestroy()

        mCodeServerService?.sessionsHost?.resetTermuxSessionClients()

        try {
            unbindService(this)
        } catch (e: Exception) {
        }
    }


    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE -> {
                binding.fnView.visibility = View.VISIBLE
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                binding.fnView.visibility = View.INVISIBLE
            }
        }
        if (binding.fnView.visibility == View.VISIBLE) {
            var selfScreenLoc = IntArray(2)
            var screenLoc = IntArray(2)
            var rectSize = Rect()
            binding.fnView.getLocationOnScreen(screenLoc)
            binding.fnView.getDrawingRect(rectSize)
            rectSize.offset(screenLoc[0], screenLoc[1])
            var fnHover = false
            for (i in 0 until event.pointerCount) {
                //FIXME: what's the coordinate for this event?
                var x = event.getX(i).toInt()
                var y = event.getY(i).toInt()
                if (rectSize.contains(x, y)) {
                    fnHover = true
                    break
                }
            }
            if (fnHover) {
                setFNVisible(true)
                binding.fnView.alpha = 0.5f;
            } else {
                setFNVisible(false)
                binding.fnView.alpha = 0.1f;
            }
        } else {
            setFNVisible(false)
        }
        if (mEditorHostGestureRecognizer.dispatchTouchEvent(event)) return true
        return super.dispatchTouchEvent(event)
    }

    private var isFnVisible: Boolean = false
    private fun setFNVisible(b: Boolean) {
        runOnUiThread {
            if (isFnVisible == b) return@runOnUiThread
            isFnVisible = b
            if (isFnVisible) {
                binding.overlayControlView.visibility = View.VISIBLE
            } else {
                binding.overlayControlView.visibility = View.GONE
            }
        }
    }

    @Synchronized
    fun setSessionsListView(checkExit: Boolean = true) {
        if (checkExit && mEditorHostAdapter.dataArray.isNotEmpty()) {
            checkIfShouldFinish()
        }
        mEditorHostAdapter.updateSessions(mCodeServerService?.sessionsHost?.getSessions())
        postUpdateSessionsListView()
        configureOverlayView()
    }

    fun postUpdateSessionsListView(delay: Long = 0) {
        binding.sessions.postDelayed({
            mListViewAdapter.notifyDataSetChanged()
            postUpdateListViewSelection()
        }, delay)
    }

    private fun postUpdateListViewSelection() {
        if (mListViewAdapter.count == 1) {
            binding.sessions.post {
                if (mListViewAdapter.count == 1) {
                    binding.sessions.setItemChecked(0, true)
                }
            }
        }
    }

    fun startNewSessionActivityIfRequired() {
        if (mCodeServerService == null) return
        if (!mIsVisible) return
        if (!mInitialResume) return
        if (mCodeServerService?.sessionsHost?.isSessionsEmpty == true) {
            startNewSessionActivity()
        }
    }

    private val startForResult =
        (this as ComponentActivity).registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                val intent = result.data
                when (intent?.getStringExtra(NewSessionActivity.kSessionType)) {
                    NewSessionActivity.kSessionTypeTerminal -> {
                        val name = intent?.getStringExtra(NewSessionActivity.kTerminalSessionName)
                        val executable =
                            intent?.getStringExtra(NewSessionActivity.kTerminalExecutable)
                        val arguments =
                            intent?.getStringArrayExtra(NewSessionActivity.kTerminalArguments)
                        codeServerService?.sessionsHost?.createTermuxSession(
                            ExecutionCommand(
                                GlobalSessionsManager.getNextSessionId(GlobalSessionsManager.SessionType.TERMUX_TERMINAL),
                                executable,
                                arguments,
                                null,
                                CodeServerService.HOME_PATH,
                                false,
                                false
                            ), name
                        )
                    }
                    NewSessionActivity.kSessionTypeRemoteCodeServer -> {
                        val name = intent!!.getStringExtra(NewSessionActivity.kTerminalSessionName)
                        val executable =
                            intent!!.getStringExtra(NewSessionActivity.kTerminalExecutable)!!
                        val arguments =
                            intent!!.getStringArrayListExtra(NewSessionActivity.kTerminalArguments)!!
                        codeServerService?.sessionsHost?.createRemoteManagedCodeEditorSession(
                            GlobalSessionsManager.getNextSessionId(GlobalSessionsManager.SessionType.CODESERVER_REMOTE_MANAGED_EDITOR),
                            name,
                            executable, arguments.toTypedArray(),
                            useSSL = intent.getBooleanExtra(NewSessionActivity.kSessionSSL, true),
                            port = null,
                            verbose = preferences.editorVerbose,
                        )?.also { session ->
                            intent.getStringArrayExtra(NewSessionActivity.kEditorPathToOpen)
                                ?.also { paths -> paths.onEach { session.openPath(it) } }
                        }
                    }
                    NewSessionActivity.kSessionTypeCodeEditor -> {
                        codeServerService?.sessionsHost?.createCodeEditorSession(
                            GlobalSessionsManager.getNextSessionId(GlobalSessionsManager.SessionType.CODESERVER_EDITOR),
                            "Editor",
                            listenOnAllInterface = intent.getBooleanExtra(
                                NewSessionActivity.kSessionAllInterfaces,
                                true
                            ),
                            useSSL = intent.getBooleanExtra(NewSessionActivity.kSessionSSL, true),
                            port = preferences.editLocalServerListenPort.toIntOrNull(),
                            verbose = preferences.editorVerbose,
                        )?.also { session ->
                            intent.getStringArrayExtra(NewSessionActivity.kEditorPathToOpen)
                                ?.also { paths -> paths.onEach { session.openPath(it) } }
                        }
                    }
                    NewSessionActivity.kSessionTypeRemoteCodeEditor -> {
                        codeServerService?.sessionsHost?.createCodeEditorSession(
                            GlobalSessionsManager.getNextSessionId(GlobalSessionsManager.SessionType.REMOTE_CODESERVER_EDITOR),
                            "RemoteEditor",
                            listenOnAllInterface = false,
                            useSSL = false,
                            remote = true,
                            remoteURL = intent?.getStringExtra(NewSessionActivity.kRemoteCodeEditorURL),
                            verbose = preferences.editorVerbose,
                        )?.also { session ->
                            intent.getStringArrayExtra(NewSessionActivity.kEditorPathToOpen)
                                ?.also { paths -> paths.onEach { session.openPath(it) } }
                        }
                    }
                }
            }
        }

    private fun startNewSessionActivity() {
        binding.drawerLayout.post {
            mOrientationLockerSkipRelease = true
            try {
                binding.drawerLayout.closeDrawers()
            } catch (e: Exception) {
            }
            val intent = Intent(this, NewSessionActivity::class.java)
            intent.putExtra(NewSessionActivity.kIsInitialStart, mInitialResume)
            startForResult.launch(intent)
        }
    }

    val codeServerService: CodeServerService?
        get() {
            return mCodeServerService
        }

    override fun onServiceConnected(componentName: ComponentName?, service: IBinder) {
        mCodeServerService = (service as CodeServerService.LocalBinder).service
        mCodeServerService?.globalSessionsManager?.activity = this

        setSessionsListView()
        startNewSessionActivityIfRequired()
    }

    override fun onServiceDisconnected(p0: ComponentName?) {
        finishActivityIfNotFinishing()
    }

    private fun finishActivityIfNotFinishing() {
        if (!isFinishing) finish()
    }

    fun notifyMaxTerminalsReached() {
        CoroutineScope(Dispatchers.Main).launch {
            AlertDialog.Builder(this@EditorHostActivity)
                .setTitle(R.string.title_max_terminals_reached)
                .setMessage(R.string.msg_max_terminals_reached)
                .setPositiveButton(android.R.string.ok, null).show()
        }
    }

    fun notifyMaxEditorsReached() {
        CoroutineScope(Dispatchers.Main).launch {
            AlertDialog.Builder(this@EditorHostActivity)
                .setTitle(R.string.title_max_editors_reached)
                .setMessage(R.string.msg_max_editors_reached)
                .setPositiveButton(android.R.string.ok, null).show()
        }
    }

    val isOnResumeAfterOnCreate: Boolean
        get() = mIsOnResumeAfterOnCreate

    val isActivityRecreated: Boolean
        get() = mIsActivityRecreated

    var currentItem: EditorHostItem?
        get() = mEditorHostAdapter.dataArray[binding.viewPager.currentItem]
        set(value) {
            binding.viewPager.post {
                val idx = mEditorHostAdapter.dataArray.indexOf(value)
                if (idx >= 0) {
                    binding.viewPager.setCurrentItem(idx, false)
                    binding.sessions.setItemChecked(idx, true)
//                    binding.drawerLayout.closeDrawers()
                }
            }
        }

    enum class GestureHandlerType {
        TYPE_NONE,
        TYPE_LEFT_RIGHT,
        TYPE_UP_DOWN,
    }

    private var mCurrentGestureHandler: GestureHandlerType = GestureHandlerType.TYPE_NONE
    override fun onGestureSwipeX(
        touches: Int,
        relativeDelta: Float,
        absoluteValue: Float,
    ): Boolean {
        if (
            mCurrentGestureHandler != GestureHandlerType.TYPE_LEFT_RIGHT
            && mCurrentGestureHandler != GestureHandlerType.TYPE_NONE
        ) return false
        if (touches == 3) {
            mCurrentGestureHandler = GestureHandlerType.TYPE_LEFT_RIGHT
            mDrawerLayoutDrawerController?.updateDrawerOffset(absoluteValue)
            return true
        }
        return false
    }

    override fun onGestureSwipeY(
        touches: Int,
        relativeDelta: Float,
        absoluteValue: Float,
    ): Boolean {
        if (
            mCurrentGestureHandler != GestureHandlerType.TYPE_UP_DOWN
            && mCurrentGestureHandler != GestureHandlerType.TYPE_NONE
        ) return false
        if (touches == 3) {
            mCurrentGestureHandler = GestureHandlerType.TYPE_UP_DOWN
            if (absoluteValue <= -80 * dipInPixels) {
                mDrawerLayoutDrawerController?.cancel()
                mCurrentEditorFragment?.toggleSettings(true)
                return true
            }
            if (absoluteValue >= 80 * dipInPixels) {
                mDrawerLayoutDrawerController?.cancel()
                mCurrentEditorFragment?.toggleSettings(false)
                return true
            }
        }
        return false
    }

    override fun onGestureTap(touches: Int) {
        if (touches == 3) {
            binding.drawerLayout.apply {
                if (isDrawerOpen(Gravity.LEFT)) {
                    closeDrawers()
                } else {
                    openDrawer(Gravity.LEFT)
                }
            }
        }
    }

    override fun onGestureEnd(motionEvent: MotionEvent) {
        mDrawerLayoutDrawerController?.commitDrawerPosition()
        mCurrentGestureHandler = GestureHandlerType.TYPE_NONE
    }

    fun onNewSession(view: View) {
        startNewSessionActivity()
    }

    private fun checkIfShouldFinish() {
        runOnUiThread {
            codeServerService?.globalSessionsManager?.sessionsHost?.also { sessionsHost ->
                if (!sessionsHost.hasAliveSession()) {
                    sessionsHost.cleanup()
                    val serviceIntent = Intent(this, CodeServerService::class.java)
                    stopService(serviceIntent)
                    finish()
                }
            }
        }
    }

    fun postOnSessionFinished() {
        checkIfShouldFinish()
        postUpdateSessionsListView()
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(ev: KeyEvent?): Boolean {
        mCurrentEditorFragment?.also {
            if (it.dispatchKeyEvent(ev)) return true
        }
        mCurrentTerminalFragment?.also {
            if (it.dispatchKeyEvent(ev)) return true
        }
        return super.dispatchKeyEvent(ev)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent?): Boolean {
        mCurrentEditorFragment?.also {
            if (it.dispatchGenericMotionEvent(ev)) return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun onBackPressed() {
        mCurrentEditorFragment?.also {
            if (it.onBackPressed()) return
        }
        if (!binding.drawerLayout.isDrawerOpen(binding.leftDrawer)) {
            binding.drawerLayout.openDrawer(binding.leftDrawer)
            return
        }
        finish()
    }

    fun onFragmentResume(fragmentId: Long?, fragment: WeakReference<Fragment>) {
        if (fragmentId != null) {
            mVisibleFragments[fragmentId] = fragment
            if (mEditorHostAdapter.dataArray.isNotEmpty())
                updateCurrentFragment(binding.viewPager.currentItem)
        }
    }

    @Synchronized
    fun onFragmentPause(fragmentId: Long?) {
        if (fragmentId != null) {
            if (fragmentId == mCurrentEditorFragmentId) {
                mCurrentEditorFragment = null
                mCurrentEditorFragmentId = null
            }
            mVisibleFragments.remove(fragmentId)
        }
    }

    private val drawerListener = object : DrawerListener {
        override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
        }

        override fun onDrawerOpened(drawerView: View) {
            if (mEditorHostAdapter.itemCount > 0) {
                binding.overlayContainer.visibility = View.VISIBLE
                configureOverlayView()
            }
        }

        override fun onDrawerClosed(drawerView: View) {
            binding.overlayContainer.visibility = View.GONE
        }

        override fun onDrawerStateChanged(newState: Int) {
        }

    }

    private val sessionsSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
            binding.sessions.postDelayed({
                configureOverlayView()
            }, 100)
        }

        override fun onNothingSelected(p0: AdapterView<*>?) {
            binding.sessions.postDelayed({
                configureOverlayView()
            }, 100)
        }
    }

    private fun configureOverlayView() {
        if (mListViewAdapter.isEmpty || binding.overlayContainer.visibility == View.GONE) return
        if (binding.viewPager.currentItem >= mListViewAdapter.count) return
        when (val item = mListViewAdapter.getItem(binding.viewPager.currentItem)) {
            is TerminalItem -> {
                val (commandId) = item
                binding.overlayReloadBtn.visibility = View.GONE
                binding.overlayKillBtn.isEnabled =
                    codeServerService?.sessionsHost?.getTerminalSessionForCommandId(commandId)?.isRunning
                        ?: false
            }
            is CodeEditorItem -> {
                val (sid) = item
                binding.overlayReloadBtn.visibility = View.VISIBLE
                binding.overlayKillBtn.isEnabled =
                    codeServerService?.sessionsHost?.getVSCodeSessionForId(sid)?.terminated != true
            }
            else -> {}
        }
    }

    fun onResetCacheClicked(view: View) {
        mCurrentEditorFragment?.resetCache()
    }

    fun onKillClicked(view: View) {
        if (mListViewAdapter.isEmpty) return
        when (val item = mListViewAdapter.getItem(binding.viewPager.currentItem)) {
            is TerminalItem -> {
                val (commandId) = item
                codeServerService?.sessionsHost?.killTerminalSessionForCommandId(commandId)
            }
            is CodeEditorItem -> {
                val (sid) = item
                codeServerService?.sessionsHost?.killVSCodeSessionForId(sid)
            }
            else -> {}
        }
        binding.viewPager.postDelayed({
            setSessionsListView()
        }, 300)
    }

    fun toggleSidebar(view: View) {
        runOnUiThread {
            binding.drawerLayout.apply {
                if (!isDrawerOpen(binding.leftDrawer)) openDrawer(binding.leftDrawer)
                else closeDrawers()
            }
        }
    }

    fun onOverlaySettingsClick(view: View) {
        mCurrentEditorFragment?.toggleSettings();
    }

    fun onOverlayButtonKeyboardClick(view: View) {
        mCurrentEditorFragment?.webView?.also {
            if (it.requestFocus()) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.toggleSoftInputFromWindow(
                    it.applicationWindowToken,
                    InputMethodManager.SHOW_FORCED,
                    InputMethodManager.HIDE_NOT_ALWAYS
                )

            }
        }
    }

    fun onOverlayButtonLockOrientationClick(view: View) {
        val orientation = preferences.lockedOrientation
        if (orientation == null) {
            preferences.lockedOrientation = OrientationCompat.getCurrentRotation(this)
        } else {
            preferences.lockedOrientation = null
        }
        updateLockOrientationFromPreferences()
    }

    fun onOverlayButtonLockKeyboardClick(view: View) {
        val resolver = contentResolver
        val sKey = Settings.Secure.DEFAULT_INPUT_METHOD
        val currentKeyboard: String = Settings.Secure.getString(resolver, sKey)
        try {
            if (currentKeyboard == preferences.lockedKeyboard)
                preferences.lockedKeyboard = null
            else {
                Settings.Secure.putString(resolver, sKey, currentKeyboard)
                preferences.lockedKeyboard = currentKeyboard
            }
            updateLockKeyboardLabel()
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                data =
                    Uri.parse("https://github.com/vhqtvn/VHEditor-Android/wiki/How-to-grant-write-secure-permissions-to-VHEditor")
            })
        }
    }

    private fun updateLockKeyboardLabel() {
        if (preferences.lockedKeyboard == null) {
            binding.overlayControlButtonLockKeyboard.setText(R.string.overlay_btn_lock_keyboard)
        } else {
            binding.overlayControlButtonLockKeyboard.text =
                resources.getString(R.string.use_keyboard, preferences.lockedKeyboard)
        }
    }
}