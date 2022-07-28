import React from 'react';
import {
  AppRegistry,
  StyleSheet,
  Text,
  View
} from 'react-native';



export function main() {
	const HelloWorld = () => {
    return (
      <View style={styles.container}>
        <Text style={styles.hello}>Hello, World From JSX</Text>
      </View>
    );
  };
  var styles = StyleSheet.create({
    container: {
      flex: 1,
      justifyContent: 'center'
    },
    hello: {
      fontSize: 30,
      textAlign: 'center',
      color: 'white',
      margin: 10
    }
  });

  return HelloWorld()
}