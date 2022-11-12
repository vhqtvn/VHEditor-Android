(function(){
    (function(){
        const av = process.argv;
        if(av.length>=2 && av[1].match(/release-standalone\/out\/node\/entry$/) && process.env.VSCODE__WITHOUT_CONNECTION_TOKEN)
            av.push("--without-connection-token")
    })();

    const fs = require('fs');
    const os = require('os');

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

    (function(){
        var orig = os.networkInterfaces;
        os.networkInterfaces = function() {
            try{
                return orig.call(os);
            }catch(ex){
            }
            return {
                     lo: [
                       {
                         address: '127.0.0.1',
                         netmask: '255.0.0.0',
                         family: 4,
                         mac: '00:00:00:00:00:00',
                         internal: true,
                         cidr: '127.0.0.1/8'
                       },
                       {
                         address: '::1',
                         netmask: 'ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff',
                         family: 6,
                         mac: '00:00:00:00:00:00',
                         scopeid: 0,
                         internal: true,
                         cidr: '::1/128'
                       }
                     ],
                     eth0: [
                       {
                         address: '192.168.1.108',
                         netmask: '255.255.255.0',
                         family: 4,
                         mac: '01:02:03:0a:0b:0c',
                         internal: false,
                         cidr: '192.168.1.108/24'
                       },
                       {
                         address: 'fe80::a00:27ff:fe4e:66a1',
                         netmask: 'ffff:ffff:ffff:ffff::',
                         family: 6,
                         mac: '01:02:03:0a:0b:0c',
                         scopeid: 1,
                         internal: false,
                         cidr: 'fe80::a00:27ff:fe4e:66a1/64'
                       }
                     ]
                   };
        }
    })();

    (function(){
        var _ = function(overwrite) {
            if(overwrite && overwrite.options) overwrite.options = Object.assign(
                overwrite.options,
                {
                    'without-connection-token': {type: "boolean",desc:'zzz'},
                    'connection-token': {type: "string",desc:'zzz1'},
                    'connection-token-file': {type: "string",desc:'zzz2'},
                }
            );
        }
        _(require('./code-server/release-standalone/out/node/cli.js'));
    })();

    (function(){
        var inner = false;
        Object.defineProperty(
            process,
            'platform',
            {
                value: "linux"
            })
    })()
})();
