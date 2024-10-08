var child_process = require('child_process');

var isWin = process.platform === "win32" || process.platform === "win64";

const reactNativeBin = (
    isWin ? ".\\node_modules\\.bin\\react-native.cmd"
    :"./node_modules/.bin/react-native"
);

const child = child_process.spawnSync(reactNativeBin, [
    "bundle",
    "--platform","android",
    "--dev","false",
    "--entry-file","loader.js",
    "--bundle-output","app/src/main/assets/loader.bundled.js",
    "--minify","false",
    "--assets-dest","app/src/main/res/",
], { encoding : 'utf8', shell: true, stdio: ['inherit', 'inherit', 'inherit'] });
if(child.error) {
    console.log("ERROR: ",child.error);
    process.exit(child.error)
}

const hermescBin = isWin ? 'hermesc.exe' : 'hermesc';
let osBin = "linux64-bin";

if(isWin) {
    osBin = "win64-bin"
} else if (process.platform == "darwin") {
    osBin = "osx-bin"
}

let hermescCmd = `node_modules/react-native/sdks/hermesc/${osBin}/${hermescBin}`

if(isWin) hermescCmd = hermescCmd.replace(/\//g,'\\');

const child2 = child_process.spawnSync(hermescCmd, [
    "-emit-binary",
    "-g3",
    "-out","app/src/main/assets/loader.bundled.tmp.js",
    "app/src/main/assets/loader.bundled.js",
    "-O"
], { encoding : 'utf8', shell: true, stdio: ['inherit', 'inherit', 'inherit'] });
if(child2.error) {
    console.log("ERROR: ",child2.error);
    process.exit(child2.error)
}

require("fs").renameSync("app/src/main/assets/loader.bundled.tmp.js", "app/src/main/assets/loader.bundled.js");
