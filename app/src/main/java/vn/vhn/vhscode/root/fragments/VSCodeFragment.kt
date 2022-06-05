package vn.vhn.vhscode.root.fragments

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.CheckBox
import android.widget.SeekBar
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import vn.vhn.vhscode.R
import vn.vhn.vhscode.chromebrowser.VSCodeJSInterface
import vn.vhn.vhscode.chromebrowser.webclient.VSCodeWebChromeClient
import vn.vhn.vhscode.chromebrowser.webclient.VSCodeWebClient
import vn.vhn.vhscode.databinding.FragmentVSCodeBinding
import vn.vhn.vhscode.generic_dispatcher.BBKeyboardEventDispatcher
import vn.vhn.vhscode.generic_dispatcher.IGenericEventDispatcher
import vn.vhn.vhscode.root.EditorHostActivity
import vn.vhn.vhscode.root.codeserver.CodeServerSession
import vn.vhn.vhscode.ui.OverlayGuideView
import vn.vhn.virtualmouse.VirtualMouse
import java.lang.ref.WeakReference


private const val FRAGMENT_ID = "fragment_id"
private const val ARG_ID = "id"

class VSCodeFragment : Fragment() {
    companion object {
        const val TAG = "VSCodeFragment"

        @JvmStatic
        fun newInstance(
            fragmentID: Long,
            id: Int,
        ) =
            VSCodeFragment().apply {
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
    private var mGuideShowDrawer: OverlayGuideView? = null

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
    private var mSession: CodeServerSession? = null
    private var mWebViewConfigured = false
    private var genericMotionEventDispatcher: IGenericEventDispatcher? = null
    private var mNewUIScale: Int = 0

    private val serverLogObserver: Observer<String> = Observer<String> { updateLogView(it) }
    private val statusObserver: Observer<CodeServerSession.Companion.RunStatus> =
        Observer<CodeServerSession.Companion.RunStatus> { onServerStatusUpdated(it) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        _binding = FragmentVSCodeBinding.inflate(inflater, container, false)
        _binding?.chkUseHardwareKeyboard?.setOnClickListener { onChkUseHardwareKeyboard(it) }
        _binding?.chkUseVirtualMouse?.setOnClickListener { onChkUseVirtualMouse(it) }
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
        host.preferences.also {
            it.editorVirtualMouse.also { useVirtualMouse ->
                configureVirtualMouseMode(useVirtualMouse)
                binding.chkUseVirtualMouse.isChecked = useVirtualMouse
            }
            it.hardwareKeyboardMode.also { isHWKB ->
                configureHWKeyboardMode(isHWKB)
                binding.chkUseHardwareKeyboard.isChecked = isHWKB
            }
            binding.zoomScaleSeekBar.progress = (it.editorUIScale / 25) - 1
        }
        host.onFragmentResume(fragmentId, WeakReference(this))
        val session = host.codeServerService?.sessionsHost?.getVSCodeSessionForId(sid)
        mSession = session
        session?.liveServerLog?.observeForever(serverLogObserver)
        session?.status?.observeForever(statusObserver)
        Log.d(TAG, "Started on model ${android.os.Build.MODEL}")
        jsInterface = VSCodeJSInterface(host)
        if (android.os.Build.MODEL.matches(Regex("BB[FB]100-[0-9]+"))) { //Key1,2
            genericMotionEventDispatcher = BBKeyboardEventDispatcher(jsInterface!!)
        } else if (android.os.Build.MODEL.matches(Regex("STV100-[0-9]+"))) { //Priv
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
        host.onFragmentPause(fragmentId)
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        mSession?.also { session ->
            session.liveServerLog.removeObserver(serverLogObserver)
            session.status.removeObserver(statusObserver)
        }

    }

    private fun updateLogView(txt: String) {
        binding.txtServerLog.post {
            binding.txtServerLog.setTextKeepState(txt)
            if (binding.txtServerLog.layout == null) return@post
            val scrollAmount =
                binding.txtServerLog.layout.getLineTop(binding.txtServerLog.lineCount) - binding.txtServerLog.height;
            if (scrollAmount > 0)
                binding.txtServerLog.scrollTo(0, scrollAmount);
            else
                binding.txtServerLog.scrollTo(0, 0);
        }
    }

    private fun onServerStatusUpdated(status: CodeServerSession.Companion.RunStatus) {
        binding.webView.post {
            if (status == CodeServerSession.Companion.RunStatus.RUNNING) {
                setLogVisible(false)
                setEditorVisible(true)
            } else {
                setLogVisible(true)
            }

        }
    }

    private var mCurrentLogAnimator: ValueAnimator? = null
    private var mCurrentLogVisible = true
    private fun setLogVisible(visible: Boolean) {
        if (visible == mCurrentLogVisible) return
        if (!visible && mNewUIScale > 0 && mNewUIScale != host.preferences.editorUIScale) {
            host.preferences.editorUIScale = mNewUIScale
            configureWebView(binding.webView, true)
        }
        if (visible) mGuideShowDrawer?.apply {
            host.preferences.guideEditorSettingsShown = true
            dismiss()
        }
        mCurrentLogVisible = visible
        mCurrentLogAnimator?.cancel()
        val view = binding.overlay
        view.visibility = View.VISIBLE
        val newHeight =
            if (visible) binding.root.height
            else 1
        val animator = ValueAnimator.ofInt(view.measuredHeight, newHeight)
        mCurrentLogAnimator = animator
        animator.addUpdateListener {
            val lp = view.layoutParams
            lp.height = it.animatedValue as Int
            view.layoutParams = lp
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (!visible && mCurrentLogAnimator == animator) {
                    view.visibility = View.GONE
                    if (mUseHardKeyboard == true && !binding.webView.hasFocus())
                        binding.webView.requestFocus()
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

//        webView.onResolvePointerIcon()

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowContentAccess = true
        webView.settings.allowFileAccess = true
        webView.settings.allowFileAccessFromFileURLs = true
        webView.settings.allowUniversalAccessFromFileURLs = true
        webView.settings.setAppCachePath("/data/data/vn.vhn.vsc/cache")
        webView.settings.setAppCacheEnabled(true)
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        webView.webChromeClient = VSCodeWebChromeClient(this)
        webView.setInitialScale(host.preferences.editorUIScale)
        webView.settings.fixedFontFamily = "vscode-monospace"
        val isSSL = mSession?.useSSL == true
        val protocol = if (isSSL) "https" else "http"
        val port = mSession?.port ?: throw Error("St wrong, no port obtained")
        val url: String = protocol + "://127.0.0.1:" + port + "/?_=" + System.currentTimeMillis()
        if (isSSL) {
            webView.clearCache(true)
            webView.clearSslPreferences()
        }
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
        if (enable) mVirtualMouse.enable(binding.webViewContainer, binding.webView)
        else mVirtualMouse.disable()
    }

    fun toggleSettings(newValue: Boolean) {
        setLogVisible(newValue)
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

    fun onBackPressed(): Boolean {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
            return true
        }
        return false
    }

    private fun guideEditorSettings() {
        if (!host.preferences.guideEditorSettingsShown && mGuideShowDrawer == null) {
            mGuideShowDrawer = OverlayGuideView(host, binding.root).apply {
                textView.text =
                    HtmlCompat.fromHtml("Swipe up/down using 3 fingers to show/hide settings",
                        HtmlCompat.FROM_HTML_MODE_COMPACT)
                show()
            }
        }
    }

    fun onPageFinished(view: WebView?, url: String?) {
        binding.loading.postDelayed({
            binding.loading.visibility = View.GONE
        }, 500)
        if (url?.startsWith("http") == true)
            guideEditorSettings()
        if (mUseHardKeyboard == true && !binding.webView.hasFocus()) binding.webView.requestFocus()
    }

    fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        binding.loading.visibility = View.VISIBLE
    }
}