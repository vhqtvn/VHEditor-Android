#!/data/data/vn.vhn.vsc/files/usr/bin/bash
[ -f ~/.profile ] && . ~/.profile
function _at_exit() {
  kill $(cat /data/data/vn.vhn.vsc/files/.pid)
  rm -f /data/data/vn.vhn.vsc/files/.pid
}
trap _at_exit EXIT SIGHUP SIGTERM SIGINT SIGKILL

/data/data/vn.vhn.vsc/files/node <<SCRIPT

const fs = require("fs");


(function() {
  try{
    let p
    try{
      p = JSON.parse(fs.readFileSync("/data/data/vn.vhn.vsc/files/home/.local/share/code-server/User/settings.json"));
    }catch(e){
    }
    if(!p) p = {};
    let needwrite = false;
    if(!p.hasOwnProperty("security.workspace.trust.enabled")) {
      console.log("Setting default option for security.workspace.trust.enabled: false");
      p["security.workspace.trust.enabled"]=false;
      needwrite = true;
    }
    if(!p.hasOwnProperty("terminal.integrated.gpuAcceleration")) {
      console.log("Setting default option for terminal.integrated.gpuAcceleration: off");
      p["terminal.integrated.gpuAcceleration"] = "off";
      needwrite = true;
    }
    if(needwrite) {
      try{
        fs.mkdirSync("/data/data/vn.vhn.vsc/files/home/.local/share/code-server/User", {recursive: true});
      }catch(e){}
      fs.writeFileSync("/data/data/vn.vhn.vsc/files/home/.local/share/code-server/User/settings.json", JSON.stringify(p, null, 2));
    }
  }catch(e){
    console.log("Error: ",e);
  }
})();

(function() {
  const p = JSON.parse(fs.readFileSync("/data/data/vn.vhn.vsc/files/code-server/release-standalone/lib/vscode/product.json"));
  if(p.extensionsGallery) {
    console.log("Extensions gallery defined, removing...");
    delete p.extensionsGallery;
    fs.writeFileSync("/data/data/vn.vhn.vsc/files/code-server/release-standalone/lib/vscode/product.json", JSON.stringify(p, null, 2));
  }
})()
SCRIPT

chmod +x /data/user/0/vn.vhn.vsc/files/code-server/release-standalone/lib/vscode/node_modules/@vscode/ripgrep/bin/rg
mkdir -p /data/data/vn.vhn.vsc/files/tmp
echo '$' /data/data/vn.vhn.vsc/files/node "$@"
/data/data/vn.vhn.vsc/files/node "$@" &
echo $! >/data/data/vn.vhn.vsc/files/.pid
wait $!
