require('./shim')
const React = require('react');
const ReactNative = require('react-native');
const { reqm } = require("./js/require");

class AsyncModule extends React.Component {
    constructor(props) {
        super(props)
        const { mod } = props;
        (async () => {
            try {
                this.setState({
                    loaded: true,
                    comp: await (await reqm.import(mod)).main()
                })
            } catch (e) {
                this.setState({
                    loadText: "Error " + e.message + "\n" + e.stack
                })
            }
        })();
    }

    state = {
        loaded: false,
        loadText: 'Loading...'
    }

    render() {
        return (
            this.state.loaded
                ? this.state.comp()
                : <ReactNative.Text style={{ color: 'white' }}>{this.state.loadText}</ReactNative.Text>
        )
    }
}

ReactNative.AppRegistry.registerComponent(
    'VHERoot',
    () => ({ mod }) => (
        <AsyncModule mod={mod} />
    )
)
