package vn.vhn.vhscode.chromebrowser.webclient

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.util.Log
import android.webkit.*
import androidx.core.content.ContextCompat.startActivity
import vn.vhn.vhscode.CodeServerService
import vn.vhn.vhscode.root.fragments.VSCodeFragment
import vn.vhn.vhscode.service_features.getBootjs
import java.net.URI


class VSCodeWebClient(
    val vsCodeFragment: VSCodeFragment,
    val rootUrl: String,
) : WebViewClient() {
    companion object {
        val TAG = "VSCodeWebClient"
    }

    private val rootUri: Uri = Uri.parse(rootUrl)

    var resource_html = Regex("\\.html?(\\?|\$)")
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        view?.apply {
            CodeServerService.getBootjs(context)?.let {
                evaluateJavascript(
                    it, null
                )
            }
        }
        vsCodeFragment.onPageStarted(view, url, favicon)
    }

    override fun onLoadResource(view: WebView?, url: String?) {
        super.onLoadResource(view, url)
        if (url != null && url.contains(resource_html)) {
            Log.d(TAG, "Loading url $url")
            view!!.evaluateJavascript("setTimeout(window.vscode_boot_frames,500)", null)
        }
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        Log.d(TAG, "SSL error ${URI.create(error?.url)?.host} vs ${rootUri.host}: $error")
        if (error != null && URI.create(error.url)?.host != rootUri.host) {
            Log.d(TAG, "SSL -> cancel")
            handler?.cancel()
            return
        }
        handler?.proceed()
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?,
    ): WebResourceResponse? {
        if (request != null && view != null) {
            val url = request.url.toString()
            val asset_prefix = CodeServerService.ASSET_PREFIX
            if (url.contains(asset_prefix)) {
                val assetManager = view.context.assets
                var asset_path = url.substring(url.indexOf(asset_prefix) + asset_prefix.length)
                try {
                    val ext = asset_path.substring(asset_path.lastIndexOf('.') + 1)
                    val mimeType = when (ext) {
                        "js" -> "text/javascript"
                        "css" -> "text/css"
                        "ttf" -> "font/ttf"
                        "woff" -> "font/woff"
                        "woff2" -> "font/woff2"
                        else -> "application/octet-stream"
                    }
                    Log.d(TAG, "Load asset $asset_path with ext $ext; mime = $mimeType")
                    val inputStream = assetManager.open(asset_path)

                    return WebResourceResponse(mimeType, "utf-8", inputStream)
                } catch (e: Exception) {
                    Log.e(TAG, "Load asset got exception $e")
                }
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        if (request != null && request.isForMainFrame) {
            if (request.url.host != rootUri.host) {
                Log.d(TAG, "Request ${request.url.host}, root is ${rootUri.host}")
                view?.context?.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                return true
            }
        }
        return super.shouldOverrideUrlLoading(view, request)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        vsCodeFragment.onPageFinished(view, url)
    }
}