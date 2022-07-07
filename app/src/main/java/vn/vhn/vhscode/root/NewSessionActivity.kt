package vn.vhn.vhscode.root

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.Window
import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.termux.app.TermuxInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.vhn.vhscode.BuildConfig
import vn.vhn.vhscode.CodeServerService
import vn.vhn.vhscode.GithubReleaseInstallActivity
import vn.vhn.vhscode.R
import vn.vhn.vhscode.databinding.ActivityNewSessionBinding
import vn.vhn.vhscode.preferences.EditorHostPrefs
import vn.vhn.vhscode.root.codeserver.CodeServerManager
import vn.vhn.vhscode.service_features.setupIfNeeded
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.net.URL


class NewSessionActivity : AppCompatActivity() {
    companion object {
        val kCurrentServerVersion = "4.4.0-" + BuildConfig.CS_VERSION

        val kVersionCheckPeriodMilli = 24 * 60 * 60 * 1000; // 1 day

        val kSessionSSL = "ssl"
        val kSessionAllInterfaces = "all-interfaces"

        val kSessionType = "SESSION_TYPE"
        val kSessionTypeTerminal = "SESSION_TYPE_TERMINAL"
        val kSessionTypeCodeEditor = "SESSION_TYPE_CODEEDITOR"
        val kSessionTypeRemoteCodeEditor = "SESSION_TYPE_REMOTE_CODEEDITOR"

        val kRemoteCodeEditorURL = "REMOTE_CODEEDITOR_URL"
    }

    private lateinit var binding: ActivityNewSessionBinding
    lateinit var preferences: EditorHostPrefs

    private var latestRemoteVersion: String? = null

    private fun sharedPreferences(): SharedPreferences {
        return getSharedPreferences("main_settings", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()

        binding = ActivityNewSessionBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onResume() {
        super.onResume()
        preferences = EditorHostPrefs(this)
        configureUI()
    }

    private fun configureUI() {
        binding.txtAppVersion.movementMethod = LinkMovementMethod.getInstance()
        binding.editTxtRemoteServer.setText(preferences.defaultRemoteEditorURL)
        checkLatestVersion()
    }

    fun onNewTerminal(view: View) {
        TermuxInstaller.setupIfNeeded(this) {
            CodeServerService.setupIfNeeded(this) {
                CoroutineScope(Dispatchers.Main).launch {
                    setResult(RESULT_OK, Intent().putExtra(kSessionType, kSessionTypeTerminal))
                    finish()
                }
            }
        }
    }

    fun onStartCode(view: View) {
        TermuxInstaller.setupIfNeeded(this) {
            CodeServerService.setupIfNeeded(this) {
                CoroutineScope(Dispatchers.Main).launch {
                    setResult(
                        RESULT_OK,
                        Intent()
                            .putExtra(kSessionType, kSessionTypeCodeEditor)
                            .putExtra(kSessionSSL, preferences.editorUseSSL)
                            .putExtra(kSessionAllInterfaces, preferences.editorListenAllInterfaces)
                    )
                    finish()
                }
            }
        }
    }

    fun onStartRemote(view: View) {
        setResult(
            RESULT_OK, Intent()
                .putExtra(kSessionType, kSessionTypeRemoteCodeEditor)
                .putExtra(kRemoteCodeEditorURL, binding.editTxtRemoteServer.text.toString())
        )
        preferences.defaultRemoteEditorURL = binding.editTxtRemoteServer.text.toString()
        finish()
    }

    private fun checkLatestVersion() {
        CoroutineScope(Dispatchers.IO).launch {
            if (preferences.latestVersionCheckTime >= System.currentTimeMillis() - kVersionCheckPeriodMilli
            ) {
                latestRemoteVersion = preferences.latestVersion
                updateUI()
                //return?
            }

            try {
                val url = URL("https://github.com/vhqtvn/VHEditor-Android/releases/latest")
                val br = BufferedReader(InputStreamReader(url.openStream()))

                var inputLine: String?
                var version = ""

                val versionExtractor =
                    Regex("\"/vhqtvn/VHEditor-Android/releases/tag/v([\\d\\.]+)\"")

                while (br.readLine().also { inputLine = it } != null) {
                    if (inputLine != null) {
                        val matches = versionExtractor.find(inputLine!!)
                        if (matches != null) {
                            version = matches!!.groupValues[1]
                        }
                    }
                }

                br.close()

                if (version != "" && version != latestRemoteVersion) {
                    latestRemoteVersion = version
                    preferences.latestVersion = version
                    updateUI()
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun updateUI() {
        if (wrongTargetSDK()) {
            val intent = Intent(this, GithubReleaseInstallActivity::class.java)
            startActivity(intent)
            finish()
        }
        CoroutineScope(Dispatchers.Main).launch {
            var txtVersion = "App version: ${BuildConfig.VERSION_NAME}"
            if (latestRemoteVersion != null) {
                if (latestRemoteVersion == BuildConfig.VERSION_NAME) {
                    txtVersion += ", already latest version."
                } else {
                    txtVersion += ", latest version: <a href=\"https://github.com/vhqtvn/VHEditor-Android/releases/latest\">${latestRemoteVersion}</a>"
                }
            }
            binding.txtAppVersion.text = Html.fromHtml(txtVersion, 0)

            val codeServerVersion =
                withContext(Dispatchers.IO) { getCurrentCodeServerVersion() }
            binding.txtInstalledServerVersion.text = getString(
                R.string.server_version,
                codeServerVersion ?: getString(R.string.not_installed)
            )
            if (codeServerVersion.isNullOrBlank()) {
                binding.btnStartCode.isEnabled = false
                binding.btnInstallServer.text =
                    getString(R.string.install_server)
                binding.btnInstallServer.visibility = View.VISIBLE
                binding.installedRegionGroup.visibility = View.GONE
            } else {
                binding.btnStartCode.isEnabled = true
                if (codeServerVersion != kCurrentServerVersion) {
                    binding.btnInstallServer.text =
                        getString(R.string.update_server) + " (" + kCurrentServerVersion + ")"
                    binding.btnInstallServer.visibility = View.VISIBLE
                } else {
                    binding.btnInstallServer.text = getString(R.string.reinstall_server)
                    binding.btnInstallServer.visibility = View.GONE
                }
                binding.installedRegionGroup.visibility = View.VISIBLE
            }

            performRequestPermissions()
        }
    }

    private fun wrongTargetSDK(): Boolean {
        val targetSdkVersion = applicationContext.applicationInfo.targetSdkVersion
        return android.os.Build.VERSION.SDK_INT >= 29 && targetSdkVersion != 28
    }

    var requestedPermission: Boolean = false
    private fun performRequestPermissions() {
        CoroutineScope(Dispatchers.Main).launch {
            if (requestedPermission) return@launch
            requestedPermission = true
            preferences.requestedPermissions = true
            val listPermissionsNeeded = mutableListOf<String>()
            for (permission in listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
                if (ContextCompat.checkSelfPermission(this@NewSessionActivity, permission)
                    != PackageManager.PERMISSION_GRANTED
                )
                    listPermissionsNeeded.add(permission)
            if (!listPermissionsNeeded.isEmpty()) {
                ActivityCompat.requestPermissions(
                    this@NewSessionActivity,
                    listPermissionsNeeded.toTypedArray(),
                    0
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
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
            var csVersion = String(bytes, 0, bytes.size).trim()
            val buildFile = File("$dataHome/code-server/CSBUILD_VERSION")
            if (buildFile.exists()) {
                val stream = FileInputStream(buildFile)
                val bytes = stream.readBytes()
                stream.close()
                var csBuildVersion = String(bytes, 0, bytes.size).trim()
                csVersion += "-$csBuildVersion"
            }
            return csVersion
        }
        return ""
    }

    fun onChkListenOnAllInterfacesClick(view: View) {
        preferences.editorListenAllInterfaces = (view as CheckBox).isChecked
    }

    fun onChkUseSSLClick(view: View) {
        preferences.editorUseSSL = (view as CheckBox).isChecked

    }

    fun onSettingsClick(view: View) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setView(R.layout.dialog_settings)
            .setNegativeButton(
                R.string.close
            ) { dialog: DialogInterface?, _: Int ->
                dialog?.dismiss()
            }
            .setOnDismissListener {
                updateUI()
            }
            .show()

        // Set checkboxes to their sharedPreferences setting state
        dialog.findViewById<CheckBox>(R.id.chkListenOnAllInterfaces)?.isChecked =
            preferences.editorListenAllInterfaces
        dialog.findViewById<CheckBox>(R.id.chkUseSSL)?.isChecked =
            preferences.editorUseSSL
    }


    fun onResetRootFS(v: View) {
        CodeServerManager.onResetRootFS(this)
    }

    fun onInstallServerClick(v: View) {
        CodeServerManager.runInstallServer(this) {
            updateUI()
        }
    }
}