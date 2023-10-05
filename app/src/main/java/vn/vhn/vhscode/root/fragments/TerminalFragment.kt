package vn.vhn.vhscode.root.fragments

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import vn.vhn.vhscode.databinding.FragmentTerminalBinding
import vn.vhn.vhscode.root.EditorHostActivity
import vn.vhn.vhscode.root.terminal.TermuxTerminalSingleSessionClient
import vn.vhn.vhscode.root.terminal.TermuxTerminalSingleViewClient
import java.lang.ref.WeakReference


private const val ARG_FRAGMENT_ID = "fragment_id"
private const val ARG_COMMAND_ID = "command_id"

class TerminalFragment : Fragment() {
    companion object {
        private final val TAG = "TerminalFragment"

        private var consoleFont: Typeface? = null

        @JvmStatic
        fun newInstance(
            fragmentId: Long,
            commandId: Int,
        ) =
            TerminalFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_FRAGMENT_ID, fragmentId)
                    putInt(ARG_COMMAND_ID, commandId)
                }
            }
    }

    private var commandId: Int? = null
    private var fragmentId: Long? = null

    private var _binding: FragmentTerminalBinding? = null
    private val binding get() = _binding!!
    public val userVisible: Boolean
        get() = isResumed


    private lateinit var mTermuxTerminalSingleSessionClient: TermuxTerminalSingleSessionClient
    private lateinit var mTermuxTerminalSingleViewClient: TermuxTerminalSingleViewClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            fragmentId = it.getLong(ARG_FRAGMENT_ID)
            commandId = it.getInt(ARG_COMMAND_ID)
        }

        mTermuxTerminalSingleSessionClient = TermuxTerminalSingleSessionClient(this)
        mTermuxTerminalSingleViewClient =
            TermuxTerminalSingleViewClient(this, mTermuxTerminalSingleSessionClient)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTerminalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.terminalView.setTerminalViewClient(mTermuxTerminalSingleViewClient)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        mTermuxTerminalSingleViewClient.onCreate()
    }

    override fun onResume() {
        super.onResume()
        host.onFragmentResume(fragmentId, WeakReference(this))
        ensureBindOrStartTerminalSession()
        mTermuxTerminalSingleViewClient.onResume()
        mTermuxTerminalSingleSessionClient.onResume()
    }

    override fun onPause() {
        val inputMethodManager =
            host.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        inputMethodManager?.hideSoftInputFromWindow(binding.terminalView.windowToken, 0)
        host.onFragmentPause(fragmentId)
        mTermuxTerminalSingleViewClient.onStop()
        super.onPause()
    }

    override fun onDestroyView() {
        host.codeServerService?.sessionsHost?.apply {
            this.getTerminalSessionForCommandId(commandId)?.also {
                setDefaultSessionClient(it)
            }
        }
        super.onDestroyView()
        _binding = null
    }

    val terminalView
        get() = binding.terminalView

    val host: EditorHostActivity
        get() = activity as EditorHostActivity

    private fun ensureBindOrStartTerminalSession() {
        val term = terminalView
        if (term.currentSession != null) return
        term.setTextSize(host.preferences.fontSize)
        if (consoleFont == null) {
            consoleFont = Typeface.createFromAsset(host.applicationContext.assets,
                "fonts/powerline/Literation Mono Powerline/Literation Mono Powerline.ttf")
        }
        term.setTypeface(consoleFont)
        term.attachSession(
            host.codeServerService?.sessionsHost?.getTerminalSessionForCommandId(
                commandId
            )?.apply {
                updateTerminalSessionClient(mTermuxTerminalSingleSessionClient)
            }
        )
        term.setBackgroundColor(Color.BLACK)
        term.setTerminalCursorBlinkerRate(host.preferences.cursorBlinkRate)
    }

    fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event == null || _binding == null) return false
        if (
            event.keyCode != KeyEvent.KEYCODE_BACK
            && event.keyCode != KeyEvent.KEYCODE_HOME
            && event.keyCode != KeyEvent.KEYCODE_MENU
            && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN
            && event.keyCode != KeyEvent.KEYCODE_VOLUME_UP
            && event.keyCode != KeyEvent.KEYCODE_CAMERA
        ) {
            if (!binding.terminalView.hasFocus()) binding.terminalView.requestFocus()
        }
        return binding.root.dispatchKeyEvent(event)
    }

}
