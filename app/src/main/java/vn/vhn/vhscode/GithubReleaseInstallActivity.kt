package vn.vhn.vhscode

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.method.LinkMovementMethod
import vn.vhn.vhscode.databinding.ActivityGithubReleaseInstallBinding

class GithubReleaseInstallActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGithubReleaseInstallBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGithubReleaseInstallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.txtReleaseLink.movementMethod = LinkMovementMethod.getInstance()
    }
}