package vn.vhn.vhscode.root.fragments

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import org.json.JSONArray
import vn.vhn.vhscode.R
import vn.vhn.vhscode.chromebrowser.VSCodeJSInterface
import vn.vhn.vhscode.chromebrowser.webclient.VSCodeWebChromeClient
import vn.vhn.vhscode.chromebrowser.webclient.VSCodeWebClient
import vn.vhn.vhscode.databinding.FragmentVSCodeBinding
import vn.vhn.vhscode.generic_dispatcher.BBKeyboardEventDispatcher
import vn.vhn.vhscode.generic_dispatcher.IGenericEventDispatcher
import vn.vhn.vhscode.root.EditorHostActivity
import vn.vhn.vhscode.root.codeserver.ICodeServerSession
import vn.vhn.virtualmouse.VirtualMouse
import java.io.File
import java.lang.ref.WeakReference
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean


private const val FRAGMENT_ID = "fragment_id"
private const val ARG_ID = "id"

class VSCodeFragment : Fragment() {
    companion object {
        const val TAG = "VSCodeFragment"

        @JvmStatic
        fun newInstance(
            fragmentID: Long,
            id: Int,
        ) = VSCodeFragment().apply {
            arguments = Bundle().apply {
                putLong(FRAGMENT_ID, fragmentID)
                putInt(ARG_ID, id)
            }
        }
    }

    val host: EditorHostActivity
        get() = activity as EditorHostActivity

    private var sid: Int? = null
    private var fragmentId: Long? = null
    private var jsInterface: VSCodeJSInterface? = null
    private var mUseHardKeyboard: Boolean? = null

    private var mVirtualMouse: VirtualMouse = VirtualMouse()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            sid = it.getInt(ARG_ID)
            fragmentId = it.getLong(FRAGMENT_ID)
        }
    }

    private var _binding: FragmentVSCodeBinding? = null
    private val binding get() = _binding!!
    private var mSession: ICodeServerSession? = null
    private var mWebViewConfigured = false
    private var genericMotionEventDispatcher: IGenericEventDispatcher? = null
    private var mNewUIScale: Int = 0
    private var mNewVirtualMouseScaleParam: Int = -1

    private val inputStateObserver: Observer<ICodeServerSession.InputState> =
        Observer<ICodeServerSession.InputState> { updateInputState(it) }
    private val serverLogObserver: Observer<String> = Observer<String> { updateLogView(it) }
    private val statusObserver: Observer<ICodeServerSession.RunStatus> =
        Observer<ICodeServerSession.RunStatus> { onServerStatusUpdated(it) }

    val webView: WebView
        get() = binding.webView

    val mOnLayoutChangeListener: View.OnLayoutChangeListener =
        object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View?,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int,
            ) {
                if (mCurrentLogVisible) setLogVisible(mCurrentLogVisible)
            }

        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        _binding = FragmentVSCodeBinding.inflate(inflater, container, false)
        _binding?.chkUseHardwareKeyboard?.setOnClickListener { onChkUseHardwareKeyboard(it) }
        _binding?.chkUseVirtualMouse?.setOnClickListener { onChkUseVirtualMouse(it) }
        _binding?.chkUseHWA?.setOnClickListener { onChkUseHWA(it) }
        _binding?.txtServerLog?.setOnLongClickListener {
            (activity?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?)?.setPrimaryClip(
                ClipData(null,
                    arrayOf("text/plain"),
                    ClipData.Item(mSession?.liveServerLog?.value)))
            Toast.makeText(activity, R.string.copied_to_clipboard, Toast.LENGTH_LONG).show()
            return@setOnLongClickListener true
        }
        _binding?.txtPassword?.setOnEditorActionListener { txt, actionId, k ->
            if (actionId == EditorInfo.IME_ACTION_SEND || k.keyCode == KeyEvent.KEYCODE_ENTER) {
                val p = txt.text
                txt.text = ""
                mSession?.sendInput(p.toString())
                true
            } else {
                false
            }
        }
        _binding?.virtualMouseScaleSeekBar?.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                mNewVirtualMouseScaleParam = p1
                val newVirtualMouseScale = host.preferences.calculateEditorVirtualMouseScale(p1)
                binding.virtualMouseScaleLabel.post {
                    binding.virtualMouseScaleLabel.text =
                        resources.getString(R.string.virtualMouseScaleValue, newVirtualMouseScale)
                }
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
            }
        })
        _binding?.zoomScaleSeekBar?.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                mNewUIScale = (1 + p1) * 25
                binding.zoomScaleLabel.post {
                    binding.zoomScaleLabel.text =
                        resources.getString(R.string.zoomValue, mNewUIScale)
                }
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
            }
        })
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        binding.root.addOnLayoutChangeListener(mOnLayoutChangeListener)
        host.preferences.also {
            it.editorVirtualMouse.also { useVirtualMouse ->
                configureVirtualMouseMode(useVirtualMouse)
                binding.chkUseVirtualMouse.isChecked = useVirtualMouse
            }
            it.hardwareKeyboardMode.also { isHWKB ->
                configureHWKeyboardMode(isHWKB)
                binding.chkUseHardwareKeyboard.isChecked = isHWKB
            }
            it.editorUseHWAccelerator.also { isHWAW ->
                configureWebViewHWAMode(isHWAW)
                binding.chkUseHWA.isChecked = isHWAW
            }
            binding.virtualMouseScaleSeekBar.progress = it.editorVirtualMouseScaleParam
            binding.zoomScaleSeekBar.progress = (it.editorUIScale / 25) - 1
        }
        host.onFragmentResume(fragmentId, WeakReference(this))
        val session = host.codeServerService?.sessionsHost?.getVSCodeSessionForId(sid)
        mSession = session
        session?.liveServerLog?.observeForever(serverLogObserver) // this will trigger any initial value
        session?.status?.observeForever(statusObserver)
        session?.inputState?.observeForever(inputStateObserver)
        Log.d(TAG, "Started on model ${android.os.Build.BRAND}::${android.os.Build.MODEL}")
        jsInterface = VSCodeJSInterface(host)
        if (
            android.os.Build.MODEL.matches(Regex("BB[FB]100-[0-9]+")) //Key1,2
            || android.os.Build.MODEL.matches(Regex("STV100-[0-9]+")) //Priv
        ) {
            Log.d(TAG, "Found BlackBerry device")
            genericMotionEventDispatcher = BBKeyboardEventDispatcher(jsInterface!!)
        } else if (android.os.Build.BRAND == "Unihertz" && (
                    android.os.Build.MODEL.matches(Regex("Titan( pocket)?.*"))
                    )
        ) {
            Log.d(TAG, "Found Unihertz device")
            genericMotionEventDispatcher = BBKeyboardEventDispatcher(jsInterface!!)
        }
        if (genericMotionEventDispatcher != null) {
            genericMotionEventDispatcher!!.initializeForTarget(host, binding.webView)
        }

//        binding.webView.setOnGenericMotionListener { view, motionEvent -> false }
//        binding.webViewContainer.setOnTouchListener { view, motionEvent ->
//            Log.d("MouseView", "WebViewTouch " + motionEvent)
//            binding.webView.onTouchEvent(motionEvent)
//            return@setOnTouchListener true
//        }
    }

    override fun onPause() {
        binding.root.removeOnLayoutChangeListener(mOnLayoutChangeListener)
        host.onFragmentPause(fragmentId)
        super.onPause()
    }

    override fun onDestroyView() {
        binding.webView.webChromeClient = null
        binding.webView.webViewClient = WebViewClient()
        mSession?.also { session ->
            session.liveServerLog.removeObserver(serverLogObserver)
            session.status.removeObserver(statusObserver)
            session.inputState.removeObserver(inputStateObserver)
        }
        super.onDestroyView()
        _binding = null
    }

    private fun updateInputState(newState: ICodeServerSession.InputState) {
        _binding?.txtPasswordLayout?.post {
            _binding?.also {
                it.txtPasswordLayout.apply {
                    if (newState is ICodeServerSession.InputState.C.Password) {
                        hint = if (newState.message.isEmpty()) {
                            getString(R.string.input_password)
                        } else {
                            newState.message
                        }
                        visibility = View.VISIBLE
                    } else {
                        visibility = View.GONE
                    }
                }
                when (newState) {
                    is ICodeServerSession.InputState.C.ReinstallConfirmation -> {
                        var replied = AtomicBoolean(false)
                        val replyOnce = { result: String ->
                            if (replied.compareAndSet(false, true))
                                mSession?.sendInput(result)
                        }
                        AlertDialog.Builder(requireContext())
                            .setTitle(R.string.title_reinstall_remote_cs)
                            .setMessage(newState.message)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                replyOnce("1")
                            }
                            .setNegativeButton(android.R.string.cancel) { _, _ ->
                                replyOnce("0")
                            }
                            .setOnDismissListener {
                                replyOnce("0")
                            }
                            .show()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun scrollLogView() {
        _binding?.txtServerLog?.post {
            _binding?.also {
                if (it.txtServerLog.layout == null) return@post
                val scrollAmount =
                    it.txtServerLog.layout.getLineTop(it.txtServerLog.lineCount) - it.txtServerLog.height
                if (scrollAmount > 0) it.txtServerLog.scrollTo(0, scrollAmount + 10)
                else it.txtServerLog.scrollTo(0, 0)
            }
        }

    }

    private fun updateLogView(txt: String) {
        _binding?.txtServerLog?.post {
            _binding?.also {
                var displayTxt = if (txt.length > 10000) "..." + txt.substring(txt.length - 10000)
                else txt
                it.txtServerLog.setTextKeepState(displayTxt)
                scrollLogView()
            }
        }
    }

    private fun onServerStatusUpdated(status: ICodeServerSession.RunStatus) {
        _binding?.webView?.post {
            when (status) {
                ICodeServerSession.RunStatus.RUNNING -> {
                    setLogVisible(false)
                    setEditorVisible(true)
                }
                ICodeServerSession.RunStatus.ERROR,
                ICodeServerSession.RunStatus.FINISHED,
                -> {
                    _binding?.also {
                        it.webView.loadUrl("about:blank")
                        setLogVisible(true)
                    }
                }
                else -> {
                    setLogVisible(true)
                }
            }
        }
    }

    private var mCurrentLogAnimator: ValueAnimator? = null
    private var mCurrentLogVisible = true
    private var mLastLogVisibleHeight = -1
    private fun setLogVisible(visible: Boolean) {
        if (mSession?.status?.value != ICodeServerSession.RunStatus.RUNNING && !visible) return

        if (!visible && mCurrentLogVisible != visible) {
            var shouldConfigureWebview = false
            if (mNewUIScale > 0 && mNewUIScale != host.preferences.editorUIScale) {
                host.preferences.editorUIScale = mNewUIScale
                shouldConfigureWebview = true
            }
            if (mNewVirtualMouseScaleParam >= 0 && mNewVirtualMouseScaleParam != host.preferences.editorVirtualMouseScaleParam) {
                host.preferences.editorVirtualMouseScaleParam = mNewVirtualMouseScaleParam
                mVirtualMouse.setMouseScale(host.preferences.editorVirtualMouseScale)
            }
            if (shouldConfigureWebview) configureWebView(binding.webView, true)
        }
        val newHeight = if (visible) binding.root.height
        else 1
        if (newHeight == mLastLogVisibleHeight && mCurrentLogVisible && visible) return
        mLastLogVisibleHeight = newHeight
        mCurrentLogVisible = visible
        mCurrentLogAnimator?.cancel()
        val view = binding.overlay
        view.visibility = View.VISIBLE
        val animator = ValueAnimator.ofInt(view.measuredHeight, newHeight)
        mCurrentLogAnimator = animator
        animator.addUpdateListener {
            val lp = view.layoutParams
            lp.height = it.animatedValue as Int
            view.layoutParams = lp
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (mCurrentLogAnimator == animator) {
                    if (!visible) {
                        view.visibility = View.GONE
                        if (mUseHardKeyboard == true && !binding.webView.hasFocus()) binding.webView.requestFocus()
                    } else {
                        scrollLogView()
                    }
                }
            }
        })

        animator.duration = 400
        animator.start()
    }

    private fun setEditorVisible(visible: Boolean) {
        if (visible) {
            configureWebView(binding.webView)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Synchronized
    private fun configureWebView(webView: WebView, force: Boolean = false) {
        if (mWebViewConfigured && !force) return
        mWebViewConfigured = true

        try {
            WebView.setWebContentsDebuggingEnabled(true)
        } catch (_: Exception) {
        }

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.allowContentAccess = true
        webView.settings.allowFileAccess = true
        @Suppress("DEPRECATION") webView.settings.allowFileAccessFromFileURLs = true
        @Suppress("DEPRECATION") webView.settings.allowUniversalAccessFromFileURLs = true
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT

        // Set mobile display mode user agent if enabled
        if (host.preferences.editorMobileDisplayMode) {
            val mobileUserAgent = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
            webView.settings.userAgentString = mobileUserAgent
        }

        webView.webChromeClient = VSCodeWebChromeClient(this)
        webView.setInitialScale(host.preferences.editorUIScale)
        webView.settings.fixedFontFamily = "vscode-monospace"
        var url: String = mSession?.url ?: throw Error("No url defined")
        val host = URL(url).host
        mSession?.pathsToOpen?.also { paths ->
            if (paths.isNotEmpty()) {
                val queryIdx = url.indexOf('?')
                if (queryIdx >= 0) url = url.substring(0, queryIdx)
                val filesToOpen = JSONArray()
                var directoriesToOpen = mutableListOf<String>()
                for (path in paths) {
                    File(path).also { f ->
                        if (mSession?.isRemote == true) {
                            if (directoriesToOpen.isEmpty()) {
                                directoriesToOpen.add(path)
                            } else {
                                filesToOpen.put("vscode-remote://$host${f.absolutePath}")
                            }
                        } else if (f.exists()) {
                            if (f.isDirectory) directoriesToOpen.add(path)
                            else filesToOpen.put("vscode-remote://$host${f.absolutePath}")
                        } else {
                            filesToOpen.put("vscode-remote://$host${f.absolutePath}")
                        }
                    }
                }
                if (!url.endsWith("/")) url += "/"
                url += "?ew=true"
                if (directoriesToOpen.isNotEmpty()) {
                    if (directoriesToOpen.size > 1) {
                        Toast.makeText(context,
                            R.string.can_open_1_folder_in_a_single_instance_only,
                            Toast.LENGTH_SHORT).show()
                    }
                    url += "&folder=" + java.net.URLEncoder.encode(directoriesToOpen[0], "utf-8")
                }
                if (filesToOpen.length() > 0) {
                    url += "&payload=" + java.net.URLEncoder.encode(JSONArray().apply {
                        put(JSONArray().apply {
                            put("openFiles")
                            put(filesToOpen)
                        })
                    }.toString(), "utf-8")
                }
            }
        }
        Log.d(TAG, "Loading $url")
        webView.webViewClient = VSCodeWebClient(this, url)
        webView.addJavascriptInterface(jsInterface!!, "_vn_vhn_vscjs_")
        webView.loadUrl(url)
    }

    fun postTitle(title: String?) {
        binding.webView.post {
            mSession?.title = title ?: ""
            host.postUpdateSessionsListView()
        }
    }

    fun dispatchKeyEvent(ev: KeyEvent?): Boolean {
        if (ev == null) return false
        genericMotionEventDispatcher?.also {
            if (it.dispatchKeyEvent(ev)) return true
        }
        if (binding.webView.dispatchKeyEvent(ev)) return true
        return false
    }

    fun dispatchGenericMotionEvent(ev: MotionEvent?): Boolean {
        if (ev == null) return false
        genericMotionEventDispatcher?.also {
            if (it.dispatchGenericMotionEvent(ev)) return true
        }
        return false
    }

    fun configureVirtualMouseMode(enable: Boolean) {
        if (enable == mVirtualMouse.isEnabled) return
        if (enable) mVirtualMouse.enable(binding.webViewContainer,
            binding.webView,
            host.preferences.editorVirtualMouseScale)
        else mVirtualMouse.disable()
    }

    fun toggleSettings(newValue: Boolean? = null) {
        setLogVisible(newValue ?: !mCurrentLogVisible)
    }

    fun configureWebViewHWAMode(hardware: Boolean) {
        if (hardware) {
            binding.webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        } else {
            binding.webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
    }

    fun configureHWKeyboardMode(enable: Boolean) {
        if (mUseHardKeyboard == enable) return
        mUseHardKeyboard = enable
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (mUseHardKeyboard!!) {
                binding.webView.focusable = View.NOT_FOCUSABLE
            } else {
                binding.webView.focusable = View.FOCUSABLE_AUTO
            }
        }
    }

    fun onChkUseHardwareKeyboard(view: View) {
        (view as CheckBox).isChecked.also {
            host.preferences.hardwareKeyboardMode = it
            configureHWKeyboardMode(it)
        }
    }

    fun onChkUseVirtualMouse(view: View) {
        (view as CheckBox).isChecked.also {
            host.preferences.editorVirtualMouse = it
            configureVirtualMouseMode(it)
        }
    }

    fun onChkUseHWA(view: View) {
        (view as CheckBox).isChecked.also {
            host.preferences.editorUseHWAccelerator = it
            configureWebViewHWAMode(it)
        }
    }

    fun onBackPressed(): Boolean {
        if (mCurrentLogVisible) {
            setLogVisible(false)
            return true
        }
//        if (binding.webView.canGoBack()) {
//            binding.webView.goBack()
//            return true
//        }
        return false
    }

    fun onPageFinished(view: WebView?, url: String?) {
        _binding?.also {
            it.loading.postDelayed({
                _binding?.loading?.visibility = View.GONE
            }, 500)
            if (mUseHardKeyboard == true && !it.webView.hasFocus()) it.webView.requestFocus()
        }
    }

    fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        binding.loading.visibility = View.VISIBLE
    }

    fun resetCache() {
        _binding?.also {
            it.webView.post {
                it.webView.clearCache(true)
                it.webView.reload()
            }
        }
    }
}
