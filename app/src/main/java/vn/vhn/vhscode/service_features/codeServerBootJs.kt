package vn.vhn.vhscode.service_features

import android.content.Context
import vn.vhn.vhscode.CodeServerService
import vn.vhn.vhscode.R
import java.io.File
import java.io.FileInputStream

fun CodeServerService.Companion.getBootjs(ctx: Context): String? {
    val fontname = "firacode" //TODO: add more fonts and let user configure
    val HOME = HOME_PATH
    val configFile = File("$HOME/${BOOTJS}")
    if (!configFile.exists()) {
        copyRawResource(ctx, R.raw.vsboot, configFile.absolutePath)
    }
    val stream = FileInputStream(configFile)
    val windowScriptBytes = stream.readBytes()
    stream.close()
    var windowScript = """
                    (function(){
                        window.addEventListener("DOMContentLoaded", function(){
                            if (document.querySelector("#vscode_font_css")) return;
                            var link = document.createElement( "link" );
                            link.href = "${ASSET_PREFIX}fonts/$fontname/font.css";
                            link.type = "text/css";
                            link.rel = "stylesheet";
                            link.id = "vscode_font_css";
                            link.media = "screen,print";
                            document.getElementsByTagName( "head" )[0].appendChild( link );
                        }, false);
                    })();
            """.trimIndent() + String(windowScriptBytes) + "\n"
    windowScript += """
                (function(){
                    if(!window.vscodeOrigKeyboardEventDescriptorShiftKey) window.vscodeOrigKeyboardEventDescriptorShiftKey = Object.getOwnPropertyDescriptor(window.KeyboardEvent.prototype, 'shiftKey');
                    var shiftGetter = window.vscodeOrigKeyboardEventDescriptorShiftKey.get;
                    Object.defineProperty(window.KeyboardEvent.prototype, 'shiftKey', {
                        get(){
                            let orig = shiftGetter.apply(this);
                            if (orig) return true;
                            if (typeof this.cached_shift_pressed === 'undefined') {
                                this.cached_shift_pressed = _vn_vhn_vscjs_.isShiftKeyPressed()
                            }
                            return this.cached_shift_pressed;
                        }
                    });
                })()
            """.trimIndent()
    return """
                (function(){
                    var single_window_apply = function(window){
                        if(window.__vscode_boot_included__) return;
                        window.__vscode_boot_included__ = true;
                        var document = window.document;
                        var local_apply = function(){
                            if(!document.body) {
                                setTimeout(local_apply, 100);
                                return;
                            }
                            if(typeof _vn_vhn_vscjs_!=='undefined') {
                                var mkstring;
                                mkstring = async function(x) {
                                    let t = typeof x;
                                    if (t==="undefined") return "";
                                    if (t!=="object") return t.toString();
                                    if (Array.isArray(x)) {
                                        let result = "";
                                        for(const i of x) result += await mkstring(i);
                                        return result;
                                    }
                                    if(x instanceof ClipboardItem) {
                                        return await a.getType("text/plain");
                                    } else {
                                        return x.toString();
                                    }
                                 }
                                Object.defineProperty(window.navigator,'clipboard',{value:{
                                    write: async function(data) {
                                        return _vn_vhn_vscjs_.copyToClipboard(await mkstring(data));
                                    },
                                    writeText: function(txt) {
                                        return _vn_vhn_vscjs_.copyToClipboard(txt);
                                    },
                                    readText: function() {
                                        return _vn_vhn_vscjs_.getClipboard();
                                    }
                                }, writable: false});
                            }
                            Object.defineProperty(window.navigator,'onLine',{value:true, writable: false});
                            $windowScript
                        };
                        local_apply();
                	}
                	single_window_apply(window);
                	var vscode_boot_frames_inner = function(window){
                	    for(var i=0;i<window.frames.length;i++) {
                            single_window_apply(window.frames[i]);
                            vscode_boot_frames_inner(window.frames[i]);
                        }
                	}
                	window.vscode_boot_frames = function(){
                	    vscode_boot_frames_inner(window);
                	}
                	window.vscode_boot_frames();
                })()
            """.trimIndent()
}
