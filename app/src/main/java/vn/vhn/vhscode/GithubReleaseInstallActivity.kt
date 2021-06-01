package vn.vhn.vhscode

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.method.LinkMovementMethod
import kotlinx.android.synthetic.main.activity_github_release_install.*

class GithubReleaseInstallActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_github_release_install)

        txtReleaseLink.movementMethod = LinkMovementMethod.getInstance()
    }
}