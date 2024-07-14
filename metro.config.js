const {getDefaultConfig, mergeConfig} = require('@react-native/metro-config');
const path = require("path");
const metroResolver = require("metro-resolver");
const exclusionList = require('metro-config/src/defaults/exclusionList');

// exclusionList is a function that takes an array of regexes and combines
// them with the default exclusions to return a single regex.
/**
 * Metro configuration
 * https://reactnative.dev/docs/metro
 *
 * @type {import('metro-config').MetroConfig}
 */
const config = {
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

module.exports = mergeConfig(getDefaultConfig(__dirname), config);