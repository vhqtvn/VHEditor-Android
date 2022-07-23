const path = require("path");
const metroResolver = require("metro-resolver");
const exclusionList = require('metro-config/src/defaults/exclusionList');

// exclusionList is a function that takes an array of regexes and combines
// them with the default exclusions to return a single regex.

module.exports = {
    resolver: {
        blacklistRE: exclusionList([
            /node_modules\/hermes-parser\/.*/,
            /node_modules\/metro-hermes-compiler\/.*/,
        ]),
        resolveRequest: (context, realModuleName, platform, moduleName) => {
            if (['hermes-parser', 'metro-hermes-compiler'].includes(moduleName || realModuleName)) {
                return {
                    filePath: path.resolve(__dirname + "/js/dummy.js"),
                    type: "sourceFile"
                };
            }
            return metroResolver.resolve(
                { ...context, resolveRequest: undefined },
                moduleName || realModuleName,
                platform
            );

        }
    }
};