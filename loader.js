require('./shim')
const React = require('react');
const ReactNative = require('react-native');
const { reqm } = require("./js/require");

ReactNative.AppRegistry.registerComponent(
    'VHERoot',
    () => ({ mod }) => reqm.require(mod).main()
)
