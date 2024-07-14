package vn.vhn.vhscode.root

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.*
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import vn.vhn.vhscode.R
import vn.vhn.vhscode.databinding.SessionsListItemBinding
import vn.vhn.vhscode.root.codeserver.SharedLocalCodeServerSession

val kTagBinding = R.string.tag_binding
val kTagBindingPosition = R.string.tag_binding_position

val ColorDEADGRAY = 0xFF666666.toInt()
val spanNumberRunning = ForegroundColorSpan(Color.GREEN)
val spanNumberStopped = ForegroundColorSpan(ColorDEADGRAY)
val spanName = StyleSpan(Typeface.BOLD)
val spanNameExt = listOf(
    StyleSpan(Typeface.NORMAL),
    ForegroundColorSpan(Color.parseColor("#cfd8dc"))
)
val spanTitle = StyleSpan(Typeface.ITALIC)

class SessionsListAdapter(
    val activity: EditorHostActivity,
    sessionsList: List<EditorHostItem>,
) : ArrayAdapter<EditorHostItem>(
    activity.applicationContext,
    R.layout.sessions_list_item,
    sessionsList
), AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {
    private val mIconClickListener = View.OnClickListener {
        val position = it.getTag(kTagBindingPosition) as Int
        activity.codeServerService?.sessionsHost?.also { sessionsHost ->
            when (val item = getItem(position)) {
                is TerminalItem -> {
                    val (sid) = item
                    if (sessionsHost.getTerminalSessionForCommandId(sid)?.isRunning != true)
                        sessionsHost.cleanupTerminalSessionForCommandId(sid)
                }
                is CodeEditorItem -> {
                    val (sid) = item
                    if (sessionsHost.getVSCodeSessionForId(sid)?.terminated != false)
                        sessionsHost.cleanupVSCodeSessionForId(sid)
                }
                else -> {}
            }
        }
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var sessionRowView: View? = convertView
        var binding: SessionsListItemBinding? = null
        if (sessionRowView == null) {
            val newBinding = SessionsListItemBinding.inflate(activity.layoutInflater)
            sessionRowView = newBinding.root
            sessionRowView!!.setTag(kTagBinding, newBinding)
            binding = newBinding
            binding.icon.apply {
                setOnClickListener(mIconClickListener)
            }
        } else {
            binding = sessionRowView!!.getTag(kTagBinding) as SessionsListItemBinding?
        }

        val ic = binding!!.icon
        val tv = binding!!.sessionTitle

        var txtPartNumber = "${1 + position} "
        var txtPartName = "Undefined"
        var txtPartTitle = ""
        var running = true
        var icIcon = R.drawable.logo_b

        ic.setTag(kTagBindingPosition, position)

        var nameHasExtPart = false

        val item = getItem(position)
        when (item) {
            null -> {
                icIcon = R.drawable.logo_b
                txtPartName = "NULL"
            }
            is TerminalItem -> {
                icIcon = R.drawable.icon_bash
                val (sid) = item
                val session =
                    activity.codeServerService?.sessionsHost?.getTerminalSessionForCommandId(sid)
                running = false
                session?.apply {
                    txtPartName = mSessionName ?: "Noname"
                    txtPartTitle = title ?: cwd ?: ""
                    running = isRunning
                    if (!running) {
                        txtPartTitle = ("Exited (${exitStatus})"
                                + (if (txtPartTitle.isNotEmpty()) " " else "")
                                + txtPartTitle
                                )
                    }
                }
            }
            is CodeEditorItem -> {
                icIcon = R.drawable.icon_vscode
                val (id) = item
                val session = activity.codeServerService?.sessionsHost?.getVSCodeSessionForId(id)
                if (session != null) {
                    if (session is SharedLocalCodeServerSession) txtPartName = "Local Editor"
                    else txtPartName = "Remote Editor"
                    txtPartName += " (${session.url})"
                    nameHasExtPart = true
                    txtPartTitle = session.title
                    running = session.terminated != true
                } else {
                    txtPartName = "Editor"
                    txtPartTitle = ""
                    running = false
                }
            }
            else -> {
                throw Exception("Invalid type")
            }
        }

        txtPartName = " $txtPartName"
        if (txtPartTitle.isNotEmpty()) txtPartTitle = "\n" + txtPartTitle

        tv.text = SpannableString(
            txtPartNumber + txtPartName + txtPartTitle
        ).also {
            var start = 0
            it.setSpan(
                if (running) spanNumberRunning else spanNumberStopped,
                start,
                start + txtPartNumber.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            start += txtPartNumber.length
            var needStylingName = false
            if (nameHasExtPart) {
                val idx = txtPartName.indexOf('(')
                if (idx >= 0) {
                    it.setSpan(spanName,
                        start,
                        start + idx,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spanNameExt.forEach { sp ->
                        it.setSpan(sp,
                            start + idx,
                            start + txtPartName.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    needStylingName = false
                }
            }
            if (needStylingName) {
                it.setSpan(
                    spanName,
                    start,
                    start + txtPartName.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            start += txtPartName.length
            it.setSpan(
                spanTitle,
                start,
                start + txtPartTitle.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        if (running) {
            ic.setImageResource(icIcon)
            ic.setColorFilter(Color.WHITE)
        } else {
            ic.setImageResource(R.drawable.icon_trash)
            ic.setColorFilter(Color.RED)
        }

        binding!!.root.dispatchSetActivated(item == activity.currentItem)

        return sessionRowView
    }

    override fun onItemClick(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
        val item = getItem(position)
        activity.currentItem = item
    }

    override fun onItemLongClick(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long): Boolean {
        when (val item = getItem(position)) {
            is TerminalItem -> {
                val (sid) = item
                activity.codeServerService?.sessionsHost?.also { sessionHost ->
                    sessionHost.getTerminalSessionForCommandId(sid)
                        ?.also { sessionHost.renameSession(activity, it) }
                }
                return true
            }
            else -> {}
        }
        return false
    }
}