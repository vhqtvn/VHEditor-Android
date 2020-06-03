package vn.vhn.vhscode.chromebrowser.webclient

import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import vn.vhn.vhscode.CodeServerService
import java.lang.Exception

class VSCodeWebClient : WebViewClient() {
    companion object {
        val TAG = "VSCodeWebClient"
    }

    var resource_html = Regex("\\.html?(\\?|\$)")
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        view?.apply {
            CodeServerService.getBootjs(context)
            context
            evaluateJavascript(
                CodeServerService.getBootjs(context)
                , null
            )
        }
    }

    override fun onLoadResource(view: WebView?, url: String?) {
        super.onLoadResource(view, url)
        if (url != null && url.contains(resource_html)) {
            Log.d(TAG, "Loading url $url")
            view!!.evaluateJavascript("setTimeout(window.vscode_boot_frames,500)", null)
        }
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
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
}