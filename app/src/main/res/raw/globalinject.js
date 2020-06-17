const fs = require('fs');
(function(){
    var origReadFile = fs.readFile;
    var fileExists = async function (fn){
        return new Promise((resolve,reject)=>{
            fs.access(fn, fs.constants.F_OK, (err)=>{
                resolve(!err);
            })
        })
    }
    const PREFIX = "/data/data/vn.vhn.vsc/files/usr";
    const possibleShells = [
        `${PREFIX}/bin/bash`,
        `${PREFIX}/bin/fish`,
        `${PREFIX}/bin/ksh`,
        `${PREFIX}/bin/sh`,
        `${PREFIX}/bin/tcsh`,
        `${PREFIX}/bin/zsh`,
    ];
    fs.readFile = async function(path, options, callback) {
        if(path == "/etc/shells") {
            if(typeof options === 'function') callback=options;
            var data = '';
            for(const shell of possibleShells) {
                if(await fileExists(shell)) data += `${shell}\n`;
            }
            if(typeof callback === 'function') callback(null,data);
            return;
        }
        return origReadFile.call(this, path, options, callback);
    };
})();
