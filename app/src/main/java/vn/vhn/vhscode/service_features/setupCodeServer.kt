package vn.vhn.vhscode.service_features

import android.content.Context
import android.system.Os
import vn.vhn.vhscode.CodeServerService
import vn.vhn.vhscode.R
import java.io.File

fun CodeServerService.Companion.setupIfNeeded(context: Context, whenDone: () -> Unit) {
    // region home symlink
    HOME_PATH.apply {
        val homeFile = File(this)
        if (homeFile.canonicalFile.equals(homeFile.canonicalFile)) {
            homeFile.delete()
            homeFile.mkdir()
        }
        File("$this/storage").apply {
            if (!exists()) {
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
                    module.exports = require('./cli_orig_vhcode')
                    Object.assign(
                        module.exports.options,
                        {
                            'without-connection-token': {type: "boolean",desc:'zzz'},
                            'connection-token': {type: "string",desc:'zzz1'},
                            'connection-token-file': {type: "string",desc:'zzz2'},
                        }
                    )
                """
        )
    } while (false)
    // endregion

    "${PREFIX_PATH}/boot.sh".apply {
        copyRawResource(context, R.raw.boot, this)
        File(this).setExecutable(true)
    }

    File("${PREFIX_PATH}/usr/lib/libc++_shared.so").copyTo(
        File("${ROOT_PATH}/libc++_shared.so"),
        true
    )

    // region global inject
    "${PREFIX_PATH}/globalinject.js".apply {
        copyRawResource(context, R.raw.globalinject, this)
    }
    // endregion
    whenDone()
}
