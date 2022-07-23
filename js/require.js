import { NativeModules } from 'react-native';

const { VHERNFile } = NativeModules;

var RNFS = require('react-native-fs');

const normalize = function (url, base) {
    if (!url.startsWith("./") && !url.startsWith("/")) return url;
    for (var
        path = base.slice(0, base.lastIndexOf('/')),
        length = url.length,
        c, i = 0, p = 0; i < length; p = i + 1
    ) {
        i = url.indexOf('/', p);
        if (i < 0) {
            i = length;
            path += '/' + url.slice(p);
            if (!/\\.js$/i.test(path)) path += '.js';
        } else if (i === 0) {
            path = '';
        } else {
            c = p; p = i;
            while (p && url.charAt(p - 1) === '.') --p;
            switch (i - p) {
                case 0: path += '/' + url.slice(c, i); break;
                case 1: break;
                case 2: path = path.slice(0, path.lastIndexOf('/')); break;
            }
        }
    }
    return path;
}

process.version = 'vheditor-custom';
const BASEDIR = '/data/data/vn.vhn.vsc/files/home/vhe-modules';
process.cwd = () => BASEDIR;
const bundler = require('metro-react-native-babel-transformer').transform;
const generator = require("@babel/generator").default;
const require_cache = require('./require-cached');

export const reqm = (function CommonJS() {
    const gmodule = Object.create(null)
    gmodule._cache = {}
    const load = (content, path) => {
        if (!typeof content === 'string') {
            throw (gmodule._cache[path] = new Error("error"));
        }
        const transformed = bundler({ filename: path, options: {}, src: content })
        const transformed_content = generator(transformed.ast, {}, content)
        const creator = eval('(function(gmodule, global){' +
            'var module=Object.assign({},gmodule,{exports:{}});' +
            'var __filename=module.filename=' + JSON.stringify(path) + ';' +
            'var __dirname=__filename.slice(0,__filename.lastIndexOf("/"));' +
            'var require=function(path){return gmodule.require(path, __dirname);};' +
            'var async_import=function(path){return gmodule.import(path, __dirname);};' +
            'var exports=module.exports;' +
            '(function(){"use strict";\n' +
            transformed_content.code +
            ';\n}.call(exports));' +
            'return module.exports;' +
            '});')
        return gmodule._cache[path] = creator(gmodule, global);
    }

    gmodule.import = async (path, base) => {
        path = normalize(path, (base || BASEDIR) + "/");
        let content = await RNFS.readFile(path, 'utf8')
        return load(content, path)
    }

    gmodule.require = (path, base) => require_cache(gmodule, normalize(path, (base || BASEDIR) + "/"), () => {
        // throw new Error(\`Not supported yet (require(\${path}))\`)
        let content = VHERNFile.readText(normalize(path, (base || BASEDIR) + "/"))
        return load(content, path)
    })

    return gmodule;
}());
