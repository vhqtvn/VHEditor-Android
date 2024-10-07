package vn.vhn.vhscode.service_features

import android.content.Context
import android.system.Os
import vn.vhn.vhscode.CodeServerService
import vn.vhn.vhscode.R
import vn.vhn.vhscode.compat.FSCompat
import java.io.File

private class SetupStorage {
    companion object {
        var initialSetupRan = false
    }
}

fun CodeServerService.Companion.initialSetupIfNeededSync(context: Context) {
    if (SetupStorage.initialSetupRan) return
    SetupStorage.initialSetupRan = true
    // region home symlink
    HOME_PATH.apply {
        val homeFile = File(this)
        if (FSCompat.isSymbolicLink(homeFile)) {
            homeFile.delete()
            homeFile.mkdir()
        }
        File("$this/storage").apply {
            if (exists() && !FSCompat.isSymbolicLink(this)) {
                delete()
            }
            if (!exists()) {
                parentFile?.mkdirs()
                val oldHome = homePath(context)
                if (File(oldHome).exists()) {
                    Runtime.getRuntime()
                        .exec("mv $oldHome/* $oldHome/.[!.]* $oldHome/..?* ${HOME_PATH}/")
                        .waitFor()
                }
                Os.symlink(oldHome, absolutePath)
            }
        }
    }
    // region

    File(VHEMOD_PATH).mkdirs()
    File("$VHEMOD_PATH/new-session.js").apply {
        if (!exists()) copyRawResource(
            context,
            R.raw.new_session_loader,
            absolutePath)
    }

    copyRawResource(
        context,
        R.raw.new_session_default,
        "$VHEMOD_PATH/new-session-default.js")
}

fun CodeServerService.Companion.setupIfNeeded(context: Context, whenDone: () -> Unit) {
    // region setup certs
    copyRawResource(
        context,
        R.raw.cert_crt,
        "${HOME_PATH}/cert.cert"
    )
    copyRawResource(
        context,
        R.raw.cert_key,
        "${HOME_PATH}/cert.key"
    )
    // region

    // region setup trusted gpg
    "${PREFIX_PATH}/usr/etc/apt/trusted.gpg.d/vhnvn.gpg".apply {
        copyRawResource(context, R.raw.vhnvn, this)
    }
    // endregion

    // region patch apt sources lists
    listOf(File("${PREFIX_PATH}/usr/etc/apt/sources.list")).forEach {
        it.bufferedWriter()
            .use { it.write("deb https://vsc.vhn.vn/termux-packages-24/ stable main") }
    }
    // endregion

    // region patch for code-server 4.3.0, adding without-connection-token option
    do {
        val utilPath =
            "${PREFIX_PATH}/code-server/release-standalone/out/node/cli.js"
        val utilOrigPath =
            "${PREFIX_PATH}/code-server/release-standalone/out/node/cli_orig_vhcode.js"
        val util = File(utilPath)
        if (!util.exists()) break
        if (util.readText().contains("without-connection-token")) break
        try {
            File(utilOrigPath).delete()
        } catch (_: Exception) {
        }
        util.renameTo(File(utilOrigPath))
        File(utilPath).writeText(
            """
                    Object.assign(exports, require('./cli_orig_vhcode'));
                    Object.assign(
                        exports.options,
                        {
                            'without-connection-token': {type: "boolean",desc:'zzz'},
                            'connection-token': {type: "string",desc:'zzz1'},
                            'connection-token-file': {type: "string",desc:'zzz2'},
                        }
                    );
                """
        )
    } while (false)
    // endregion

    "${PREFIX_PATH}/boot.sh".apply {
        copyRawResource(context, R.raw.boot, this)
        File(this).setExecutable(true)
    }

    "${PREFIX_PATH}/boot-remote.sh".apply {
        copyRawResource(context, R.raw.boot_remote, this)
        File(this).setExecutable(true)
    }

    File("${PREFIX_PATH}/usr/bin/vh-editor-ensure-ssh").also { f ->
        val required_content = readRawResourceString(context, R.raw.bin_ensure_ssh)
        if (!f.exists() || f.readText(Charsets.UTF_8) != required_content) {
            f.writeText(required_content)
        }
        f.setExecutable(true)
    }

    // region global inject
    "${PREFIX_PATH}/globalinject.js".apply {
        copyRawResource(context, R.raw.globalinject, this)
    }
    // endregion
    whenDone()
}
