package vn.vhn.vhscode.root

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.termux.shared.shell.TermuxSession
import vn.vhn.vhscode.root.fragments.TerminalFragment
import vn.vhn.vhscode.root.fragments.VSCodeFragment
import vn.vhn.vhscode.service_features.SessionsHost
import java.lang.Error
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList

sealed class EditorHostItem(val unique_id: Long)
data class TerminalItem(val commandId: Int) : EditorHostItem(commandId.toLong())
data class CodeEditorItem(val id: Int) : EditorHostItem(id.toLong())

class EditorHostAdapter(
    val mActivity: EditorHostActivity,
    fa: FragmentActivity,
) : FragmentStateAdapter(fa) {
    companion object {
        private const val TAG = "EditorHostAdapter"
    }

    private val mData: ArrayList<EditorHostItem> = ArrayList()

    val dataArray: List<EditorHostItem>
        get() = Collections.unmodifiableList(mData)

    override fun getItemId(position: Int): Long {
        return mData[position].unique_id
    }

    override fun containsItem(itemId: Long): Boolean {
        return mData.any { it.unique_id == itemId }
    }

    override fun getItemCount(): Int {
        return mData.size
    }

    override fun createFragment(position: Int): Fragment {
        return when (val item = mData[position]) {
            is TerminalItem -> {
                val (scriptId) = item
                TerminalFragment.newInstance(getItemId(position), scriptId)
            }
            is CodeEditorItem -> {
                val (id) = item
                VSCodeFragment.newInstance(getItemId(position), id)
            }
            else -> {
                throw Exception("Invalid type")
            }
        }
    }

    @Synchronized
    fun updateSessions(sessions: List<SessionsHost.Companion.SessionWrapper>?): Boolean {
        var changed = false
        val runningSessions = mutableListOf<Int>()
        var newSelectionItem: EditorHostItem? = null
        if (sessions != null) {
            for (wrappedSession in sessions) {
                when (wrappedSession) {
                    is SessionsHost.Companion.SessionCaseTerminal -> {
                        val sid = wrappedSession!!.session!!.executionCommand.id
                        runningSessions.add(sid)
                        if (!containsItem(sid.toLong())) {
                            mData.add(TerminalItem(sid))
                            notifyItemInserted(mData.size - 1)
                            newSelectionItem = mData[mData.size - 1]
                            changed = true
                        }
                    }
                    is SessionsHost.Companion.SessionCaseCodeEditor -> {
                        val sid = wrappedSession!!.session!!.id
                        runningSessions.add(sid)
                        if (!containsItem(sid.toLong())) {
                            mData.add(CodeEditorItem(sid))
                            notifyItemInserted(mData.size - 1)
                            newSelectionItem = mData[mData.size - 1]
                            changed = true
                        }
                    }
                }
            }
        }
        val toRemoveItems = mutableListOf<Int>()
        for ((index, item) in mData.withIndex()) {
            when (item) {
                is TerminalItem -> {
                    if (!runningSessions.contains(item.commandId)) toRemoveItems.add(index)
                }
                is CodeEditorItem -> {
                    if (!runningSessions.contains(item.id)) toRemoveItems.add(index)
                }
            }
        }
        for ((removedCnt, index) in toRemoveItems.withIndex()) {
            val adjustedIndex = index - removedCnt
            mData.removeAt(adjustedIndex)
            notifyItemRemoved(adjustedIndex)
        }
        if (toRemoveItems.isNotEmpty()) changed = true
        if (newSelectionItem != null) {
            Handler(Looper.getMainLooper()).postDelayed({
                mActivity.currentItem = newSelectionItem
            }, 100)
        }
        return changed
    }
}
