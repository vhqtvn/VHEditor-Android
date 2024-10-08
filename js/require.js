import { NativeModules } from 'react-native';
import crypto from 'crypto'

const { VHERNFile } = NativeModules;

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
const bundler = require('@react-native/metro-babel-transformer').transform;
const generator = require("@babel/generator").default;
const require_cache = require('./require-cached');

function compiler_cache(path, original_content) {
    const cache_path = path + '.compiled-cache'
    const hmac_computer = crypto.createHmac('sha256', 'cache-v1');
    hmac_computer.update(original_content)
    const hash = hmac_computer.digest('hex')
    const magic_line = "//compiled:" + hash
    return {
        read: function () {
            try {
                const cached_content = VHERNFile.readText(cache_path)
                if (cached_content.startsWith(magic_line)) {
                    return [cached_content.substring(magic_line.length).trim(), true]
                }
            } catch (_) { }
            return [undefined, false]
        },
        write: async function (new_content) {
            VHERNFile.writeText(cache_path, magic_line + "\n" + new_content)
        }
    }
}

function compile(content, path) {
    let result, ok
    const cache = compiler_cache(path, content);
    [result, ok] = cache.read()
    if (!ok) {
        // enabling babel runtime will cause the app to crash
        // > You gave us a visitor for the node type ReferencedIdentifier but it's not a valid type
        const transformed = bundler({ filename: path, options: {enableBabelRuntime: false}, src: content })
        result = generator(transformed.ast, {}, content)
        cache.write(JSON.stringify(result))
        ok = true
    } else {
        result = JSON.parse(result)
    }
    if (!ok) throw Error("Compiler error?");
    return result
}

export const reqm = (function CommonJS() {
    const gmodule = Object.create(null)
    gmodule._cache = {}
    const load = (content, path) => {
        if (typeof content !== 'string') {
            throw (gmodule._cache[path] = new Error("error"));
        }
        try {
            const transformed_content = compile(content, path)
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
        } catch (e) {
            throw (gmodule._cache[path] = e);
        }
    }

    const load_sync = (path) => {
        let content = VHERNFile.readText(path)
        return load(content, path)

    }
    const load_async = async (path) => {
        let content = await VHERNFile.readTextAsync(path)
        return load(content, path)
    }

    gmodule.import = async (path, base) => {
        path = normalize(path, (base || BASEDIR) + "/");
        return load_async(path)
    }

    gmodule.require = (path, base) => require_cache(gmodule, normalize(path, (base || BASEDIR) + "/"), () => {
        path = normalize(path, (base || BASEDIR) + "/")
        return load_sync(path)
    })

    return gmodule;
}());
