package vn.vhn.vhscode.chromebrowser.webclient

import android.util.Log
import android.view.KeyEvent
import android.webkit.WebView
import android.webkit.WebViewClient

class VSCodeWebClient : WebViewClient() {
    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url);
        view!!.evaluateJavascript(
            """(function(){
	if(!window.vscodeOrigKeyboardEventDescriptorCode) window.vscodeOrigKeyboardEventDescriptorCode = Object.getOwnPropertyDescriptor(KeyboardEvent.prototype, 'code');
	var codeGetter = window.vscodeOrigKeyboardEventDescriptorCode.get;
	// if(!window.vscodeOrigKeyboardEventDescriptorShift) window.vscodeOrigKeyboardEventDescriptorShift = Object.getOwnPropertyDescriptor(KeyboardEvent.prototype, 'shiftKey');
	// var shiftGetter = window.vscodeOrigKeyboardEventDescriptorShift.get;
	var customMapper = {
		27: 'Escape'
	};
	var prepareCustomProps = function() {
		if(this._vscode_modded) return;
		this._vscode_modded = true;
		var orig = codeGetter.apply(this);
		if (this.which in customMapper) {
			this._vscodeCode = customMapper[this.which];
		} else this._vscodeCode = orig;
	}
	Object.defineProperty(KeyboardEvent.prototype, 'code', {
		get(){
			prepareCustomProps.apply(this);
			return this._vscodeCode;
		}
	});
})()""", null
        );
    }
}