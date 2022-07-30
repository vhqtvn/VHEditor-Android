// DO NOT MODIFY THIS FILE, IT WILL BE OVERWRITTEN!!!!
// DO NOT MODIFY THIS FILE, IT WILL BE OVERWRITTEN!!!!
// DO NOT MODIFY THIS FILE, IT WILL BE OVERWRITTEN!!!!
// DO NOT MODIFY THIS FILE, IT WILL BE OVERWRITTEN!!!!
// DO NOT MODIFY THIS FILE, IT WILL BE OVERWRITTEN!!!!
// DO NOT MODIFY THIS FILE, IT WILL BE OVERWRITTEN!!!!
// DO NOT MODIFY THIS FILE, IT WILL BE OVERWRITTEN!!!!

import { NativeModules } from 'react-native';
const { VHERNFile, VHEApi } = NativeModules;

import * as React from 'react';
import { Text, View, StyleSheet, TouchableHighlight } from 'react-native';

const SSH_LIST_FILE = '~/.vheditor/ssh'

function Section(props, child) {
  const {name, children, empty} = props;
  return (
    <View style={styles.section_container}>
      <Text style={[styles.text, styles.section_title]}>{name}</Text>
      <View style={styles.section_children_container}>
        {children && children.length ? children : empty()}
      </View>
    </View>
  )
}

function SSHItem(props) {
  const {user,host,name,command} = props
  return (
    <TouchableHighlight onPress={()=>{
        VHEApi.startSession({
            command: `vh-editor-ensure-ssh && ${command}`,
            title: `ssh ${name}`
        })
    }} underlayColor="#555555">
      <View style={[styles.sshitem_container]}>
        <Text style={[styles.text, styles.sshitem_name]}>{name}</Text>
        <Text style={[styles.text, styles.sshitem_username]}>{user}</Text>
      </View>
    </TouchableHighlight>
  )
}

function SSHItemsEmpty() {
  return (
    <View style={styles.empty_container}>
      <Text style={[styles.text, styles.empty_text]}>
        {`No ssh shortcut define, add one in ${SSH_LIST_FILE}.`}
      </Text>
    </View>
  )
}

function SSHItems(props) {
  const {name} = props
  const items = []
  if(VHERNFile) {
    let content
    try{
        content = VHERNFile.readText(SSH_LIST_FILE)
    }catch(e){}
    if(content)
        for(let line of content.split("\n")) {
          line = line.trim()
          if(line.startsWith("#") || !line) continue
          if(line.indexOf(' ')===-1) {
              //user@host
              const [user,host] = line.split("@")
              const name = host
              const command = `ssh ${user}@${host}`
              items.push(<SSHItem {...{user,host,name,command}} />)
          } else if(line.startsWith("ssh ")) {
              const name = line
              const command = line
              items.push(<SSHItem {...{name, command}} />)
          }
        }
  }
  return (
    <Section name="SSH" empty={SSHItemsEmpty}>
      {items}
    </Section>
  )
}

export function main() {
  return (
    <View style={styles.container}>
      <SSHItems />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: "black",
  },
  text: {
    color: "white",
  },
  fa: {
    fontFamily: "fa_regular_400",
  },
  section_container: {
  },
  section_title: {
    fontSize: 10,
    color: "#CCCCCC"
  },
  section_children_container: {
  },
  sshitem_container: {
    flex: 1,
    flexDirection: "row",
    justifyContent: "space-between",
    paddingTop: 10,
    paddingBottom: 10,
    borderBottomColor: "#333333",
    borderBottomWidth: 1,
  },
  sshitem_name: {
    fontWeight: "bold",
    fontSize: 14,
  },
  sshitem_username: {
    fontStyle: "italic",
    fontSize: 14,
  },
});
