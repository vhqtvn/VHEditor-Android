#!/data/data/vn.vhn.vsc/files/usr/bin/bash
[ -f ~/.profile ] && . ~/.profile
function _at_exit()
{
  kill $(cat /data/data/vn.vhn.vsc/files/.pid)
  rm -f /data/data/vn.vhn.vsc/files/.pid
}
trap _at_exit EXIT SIGHUP SIGTERM SIGINT SIGKILL

/data/data/vn.vhn.vsc/files/node <<SCRIPT

const fs = require("fs");

var shouldAddGallery = false
var extensionsGallery

const p = JSON.parse(fs.readFileSync("/data/data/vn.vhn.vsc/files/code-server/release-standalone/lib/vscode/product.json"));
if(!p.extensionsGallery) {
  console.log("Extensions gallery not defined, adding...");
  shouldAddGallery = true;
}

if(process.env.EXTENSIONS_GALLERY && JSON.parse(process.env.EXTENSIONS_GALLERY)) {
  console.log("Setting extensions gallery from env variable EXTENSIONS_GALLERY");
  extensionsGallery = JSON.parse(process.env.EXTENSIONS_GALLERY);
  shouldAddGallery = true;
} else if(shouldAddGallery) {
  console.log("Using open-vsx extension gallery");
  extensionsGallery = {"serviceUrl":"https://open-vsx.org/vscode/gallery","itemUrl":"https://open-vsx.org/vscode/item","resourceUrlTemplate":"https://open-vsx.org/vscode/asset/{publisher}/{name}/{version}/Microsoft.VisualStudio.Code.WebResources/{path}","controlUrl":"","recommendationsUrl":""};
}

if(shouldAddGallery) {
  p.extensionsGallery = extensionsGallery;
  fs.writeFileSync("/data/data/vn.vhn.vsc/files/code-server/release-standalone/lib/vscode/product.json", JSON.stringify(p, null, 2));
}

console.log("Current extensionsGallery", JSON.parse(fs.readFileSync("/data/data/vn.vhn.vsc/files/code-server/release-standalone/lib/vscode/product.json")).extensionsGallery)

SCRIPT

chmod +x /data/user/0/vn.vhn.vsc/files/code-server/release-standalone/lib/vscode/node_modules/@vscode/ripgrep/bin/rg
mkdir -p /data/data/vn.vhn.vsc/files/tmp
echo '$' /data/data/vn.vhn.vsc/files/node "$@"
/data/data/vn.vhn.vsc/files/node "$@" &
echo $! > /data/data/vn.vhn.vsc/files/.pid
wait $!