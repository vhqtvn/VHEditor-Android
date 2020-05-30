package vn.vhn.vhscode

import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Window
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Observer
import com.termux.app.TermuxInstaller
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.io.FileInputStream

class MainActivity : AppCompatActivity() {
    companion object {
        val kCurrentServerVersion = "202005271607"
        val kPrefHardKeyboard = "hardkb"
        val kPrefRemoteServer = "remoteserver"
    }

    var startServerObserver: Observer<Boolean>? = null
    var serverLogObserver: Observer<String>? = null

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

    suspend fun startServerService() {
        File(CodeServerService.HOME_PATH).mkdirs()
        if (CodeServerService.liveServerStarted.value != true) {
            val progressDialog = ProgressDialog(this)
            progressDialog.setTitle(R.string.starting_server)
            progressDialog.setMessage(getString(R.string.please_wait_starting_server))
            progressDialog.setCancelable(false)
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
            progressDialog.show()
            if (startServerObserver == null) {
                startServerObserver = Observer<Boolean> { value ->
                    if (value) {
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

    fun startEditor(url: String = "http://127.0.0.1:1337") {
        val intent = Intent(this, VSCodeActivity::class.java)
        intent.putExtra(VSCodeActivity.kConfigUseHardKeyboard, chkHardKeyboard.isChecked)
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
        btnInstallServer.setOnClickListener {
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
        btnStartCode.setOnClickListener {
            TermuxInstaller.setupIfNeeded(this) {
                CodeServerService.setupIfNeeded(this) {
                    CoroutineScope(Dispatchers.Main).launch {
                        startServerService()
                    }
                }
            }
        }
        chkHardKeyboard.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences().edit().putBoolean(kPrefHardKeyboard, isChecked).apply()
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
        btnStartRemote.setOnClickListener {
            startEditor(editTxtRemoteServer.text.toString())
        }
    }

    fun updateUI() {
        CoroutineScope(Dispatchers.Main).launch {
            chkHardKeyboard.isChecked = sharedPreferences().getBoolean(kPrefHardKeyboard, true);
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
            } else {
                btnStartCode.isEnabled = true
                if (codeServerVersion < kCurrentServerVersion) btnInstallServer.text =
                    getString(R.string.update_server)
                else btnInstallServer.text = getString(R.string.reinstall_server)
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
}
