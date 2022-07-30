package vn.vhn.vhscode.root.terminal

import android.content.Context
import com.termux.shared.shell.ShellEnvironmentClient
import okhttp3.internal.concat
import vn.vhn.vhscode.CodeServerService

class VHEditorShellEnvironmentClient : ShellEnvironmentClient {
    override fun getDefaultWorkingDirectoryPath(): String {
        return CodeServerService.HOME_PATH
    }

    override fun getDefaultBinPath(): String {
        return CodeServerService.PREFIX_PATH + "/usr/bin"
    }

    override fun buildEnvironment(
        currentPackageContext: Context?,
        isFailSafe: Boolean,
        workingDirectory: String?,
    ): Array<String> {
        return CodeServerService.buildEnv()
    }

    override fun setupProcessArgs(
        fileToExecute: String,
        arguments: Array<out String>?,
    ): Array<String> {
        val r = mutableListOf<String>(fileToExecute)
        r.addAll(arguments ?: emptyArray())
        return r.toTypedArray()
    }
}