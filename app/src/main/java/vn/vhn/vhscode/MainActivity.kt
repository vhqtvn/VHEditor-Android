package vn.vhn.vhscode

import android.app.AlertDialog
import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.termux.app.TermuxInstaller
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.io.FileInputStream
import java.io.IOException


class MainActivity : AppCompatActivity() {
    companion object {
        val kCurrentServerVersion = "202006100100"
        val kPrefHardKeyboard = "hardkb"
        val kPrefKeepScreenAlive = "screenalive"
        val kPrefRemoteServer = "remoteserver"
    }

    var startServerObserver: Observer<Int>? = null
    var serverLogObserver: Observer<String>? = null

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)
        configureUI()
        updateUI()
    }

    override fun onDestroy() {
        if (startServerObserver != null) {
            CodeServerService.liveServerStarted.removeObserver(startServerObserver!!)
            startServerObserver = null
        }
        if (serverLogObserver != null) {
            CodeServerService.liveServerLog.removeObserver(serverLogObserver!!)
            serverLogObserver = null
        }
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    suspend fun startServerService() {
        File(CodeServerService.HOME_PATH).mkdirs()
        if (CodeServerService.liveServerStarted.value != 1) {
            val progressDialog = ProgressDialog(this)
            progressDialog.setTitle(R.string.starting_server)
            progressDialog.setMessage(getString(R.string.please_wait_starting_server))
            progressDialog.setCancelable(false)
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
            progressDialog.show()
            if (startServerObserver == null) {
                startServerObserver = Observer<Int> { value ->
                    if (value == 1) {
                        progressDialog.dismiss()
                        if (startServerObserver != null) {
                            CodeServerService.liveServerStarted.removeObserver(
                                startServerObserver!!
                            )
                        }
                        startEditor()
                    } else {
                        if (!CodeServerService.isServerStarting()) {
                            progressDialog.dismiss()
                        }
                    }
                }
                CodeServerService.liveServerStarted.observeForever(startServerObserver!!)
            }
            CodeServerService.startService(this)
        } else {
            startEditor()
        }
    }

    fun stopServerService() {
        if (CodeServerService.liveServerStarted.value == 1) {
            CodeServerService.stopService(this)
        }
    }

    fun startEditor(url: String = "http://127.0.0.1:13337") {
        val intent = Intent(this, VSCodeActivity::class.java)
        intent.putExtra(VSCodeActivity.kConfigUseHardKeyboard, chkHardKeyboard.isChecked)
        intent.putExtra(VSCodeActivity.kConfigUrl, url)
        intent.putExtra(VSCodeActivity.kConfigScreenAlive, chkKeepScreenAlive.isChecked)
        startActivity(intent)
    }

    fun configureUI() {
        serverLogObserver = Observer<String> { txt ->
            runOnUiThread {
                txtServerLog.setTextKeepState(txt)
                if (txtServerLog.layout == null) return@runOnUiThread
                val scrollAmount =
                    txtServerLog.layout.getLineTop(txtServerLog.lineCount) - txtServerLog.height;
                if (scrollAmount > 0)
                    txtServerLog.scrollTo(0, scrollAmount);
                else
                    txtServerLog.scrollTo(0, 0);
            }
        }
        CodeServerService.liveServerLog.observeForever(serverLogObserver!!)
        btnStopCode.isEnabled = CodeServerService.liveServerStarted.value == 1
        CodeServerService.liveServerStarted.observeForever { value ->
            btnStopCode.isEnabled = value == 1
        }

        chkHardKeyboard.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences().edit().putBoolean(kPrefHardKeyboard, isChecked).apply()
        }
        chkKeepScreenAlive.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences().edit().putBoolean(kPrefKeepScreenAlive, isChecked).apply()
        }

        editTxtRemoteServer.setText(
            sharedPreferences().getString(
                kPrefRemoteServer,
                "http://127.0.0.1:13337"
            )
        )
        editTxtRemoteServer.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                sharedPreferences().edit().putString(kPrefRemoteServer, s.toString()).apply()
            }
        })
    }

    fun updateUI() {
        CoroutineScope(Dispatchers.Main).launch {
            sharedPreferences().apply {
                chkHardKeyboard.isChecked = getBoolean(kPrefHardKeyboard, false)
                chkKeepScreenAlive.isChecked = getBoolean(kPrefKeepScreenAlive, false)
            }
            val codeServerVersion =
                withContext(Dispatchers.IO) { getCurrentCodeServerVersion() }
            txtInstalledServerVersion.text = getString(
                R.string.server_version,
                codeServerVersion ?: getString(R.string.not_installed)
            )
            if (codeServerVersion.isNullOrBlank()) {
                btnStartCode.isEnabled = false
                btnInstallServer.text =
                    getString(R.string.install_server)
                btnInstallServer.visibility = View.VISIBLE
                installedRegionGroup.visibility = View.GONE
            } else {
                btnStartCode.isEnabled = true
                if (codeServerVersion < kCurrentServerVersion) {
                    btnInstallServer.text = getString(R.string.update_server)
                    btnInstallServer.visibility = View.VISIBLE
                } else {
                    btnInstallServer.text = getString(R.string.reinstall_server)
                    btnInstallServer.visibility = View.GONE
                }
                installedRegionGroup.visibility = View.VISIBLE
            }
        }
    }

    suspend fun getCurrentCodeServerVersion(): String? {
        val node = getFileStreamPath("node")
        if (!node.exists()) return null
        val dataHome = node.parent!!.toString()
        val versionFile = File("$dataHome/code-server/VERSION")
        if (versionFile.exists()) {
            val stream = FileInputStream(versionFile)
            val bytes = stream.readBytes()
            stream.close()
            return String(bytes, 0, bytes.size)
        }
        return "202005250000"
    }

    private fun sharedPreferences(): SharedPreferences {
        return getSharedPreferences("main_settings", Context.MODE_PRIVATE)
    }

    fun onInstallServerClick(view: View) {
        CoroutineScope(Dispatchers.Main).launch {
            var progressBar: ProgressBar
            var progressText: TextView
            val dialog = Dialog(this@MainActivity).apply {
                setCancelable(false)
                setContentView(R.layout.progress_dialog)
                progressBar = findViewById(R.id.progressBar)
                progressText = findViewById(R.id.progressText)
                setTitle(R.string.extracting)
            }
            var currentProgress: Int = 0
            var progressMax: Int = 0
            var finished = false
            launch {
                var uiProgress: Int = -1
                var uiProgressMax: Int = -1
                while (!finished) {
                    delay(50)
                    if (uiProgress != currentProgress || uiProgressMax != progressMax) {
                        uiProgress = currentProgress
                        uiProgressMax = progressMax
                        withContext(Dispatchers.Main) {
                            progressBar.progress =
                                currentProgress * progressBar.max / maxOf(1, progressMax)
                            progressText.text = "%d/%d".format(uiProgress, uiProgressMax)
                        }
                    }
                }
            }
            val progressChannel = Channel<Pair<Int, Int>>()
            dialog.show()
            dialog.window!!.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            launch {
                delay(500)
                CodeServerService.extractServer(this@MainActivity, progressChannel)
                updateUI()
                finished = true
                progressChannel.close()
            }
            for ((progress, max) in progressChannel) {
                currentProgress = progress
                progressMax = max
            }
            dialog.hide()
        }
    }

    fun onSettingsClick(view: View) {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setView(R.layout.dialog_settings)
            .setNegativeButton(
                R.string.close
            ) { dialog: DialogInterface?, which: Int ->
                dialog?.dismiss()
            }
            .setOnDismissListener {
                updateUI()
            }
            .show()
    }

    fun onStartRemote(view: View) {
        startEditor(editTxtRemoteServer.text.toString())
    }

    fun onStartCode(view: View) {
        TermuxInstaller.setupIfNeeded(this) {
            CodeServerService.setupIfNeeded(this) {
                CoroutineScope(Dispatchers.Main).launch {
                    startServerService()
                }
            }
        }
    }

    fun onStopCode(view: View) {
        CoroutineScope(Dispatchers.Main).launch {
            stopServerService()
        }
    }

    @Suppress("DEPRECATION")
    fun onResetRootFS(view: View) {
        val progress = ProgressDialog.show(
            this,
            null,
            getString(R.string.deleting),
            true,
            false
        )
        Thread {
            val runtime = Runtime.getRuntime()
            try {
                runtime.exec("rm -rf ${CodeServerService.ROOT_PATH}; mkdir ${CodeServerService.ROOT_PATH}")
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        R.string.reset_root_fs_finished,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: IOException) {
                Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show()
            } finally {
                runOnUiThread {
                    progress.dismiss()
                }
            }
        }.start()
    }
}
