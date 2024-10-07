const glob = require("glob");

console.log(`var preload_fns = Object.create(null)`);
for (var package of [
    'react',
    'react/jsx-runtime',
    'react-native',
]) {
    console.log(`preload_fns[${JSON.stringify(package)}] = ()=>require(${JSON.stringify(package)})`)
}
for (var fn of glob.sync("node_modules/@babel/runtime/helpers/*.js")) {
    fn = fn.substring(13);
    fn = fn.substring(0, fn.length - 3);
    fn = fn.replace(/\\/g, "/");
    console.log(`preload_fns[${JSON.stringify(fn)}] = ()=>require(${JSON.stringify(fn)})`)
}
for (var fn of glob.sync("node_modules/@babel/runtime/helpers/esm/*.js")) {
    fn = fn.substring(13);
    fn = fn.substring(0, fn.length - 3);
    fn = fn.replace(/\\/g, "/");
    console.log(`preload_fns[${JSON.stringify(fn)}] = ()=>require(${JSON.stringify(fn)})`)
}
console.log(`module.exports = function (gmodule, path, def) {`)
console.log(`  if(gmodule._cache[path]) return gmodule._cache[path]`)
console.log(`  if(preload_fns[path]) return gmodule._cache[path]=preload_fns[path]()`)
console.log(`  return def()`)
console.log(`}`)