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
import { Button, Text, View, StyleSheet, TouchableHighlight } from 'react-native';

const SSH_LIST_FILE = '~/.vheditor/ssh'

function Section(props, child) {
  const { name, children, empty, footer } = props;
  return (
    <View style={styles.section_container}>
      <Text style={[styles.text, styles.section_title]}>{name}</Text>
      <View style={styles.section_children_container}>
        {children && children.length ? children : empty()}
      </View>
      {footer && (
        <View style={styles.section_footer}>
          {footer()}
        </View>
      )}
    </View>
  )
}

function SSHItem(props) {
  const { user, host, name, command, args } = props
  return (
      <View style={[styles.sshitem_container]}>
    <Button title={"Remote Editor"} color={"#666666"} onPress={() => {
                                       VHEApi.startRemoteCodeServerSession({
                                         sshCommand: command,
                                         sshArguments: args,
                                         title: `VSCode: ${name}`
                                       })
                                     }}/>
    <TouchableHighlight style={[styles.sshitem_touch_container]} onPress={() => {
      VHEApi.startSession({
        command: `vh-editor-ensure-ssh && ${command} ${args.join(' ')}`,
        title: `ssh ${name}`
      })
    }} underlayColor="#555555">
      <View style={[styles.sshitem_inner_container]}>
        <Text style={[styles.text, styles.sshitem_name]}>{name}</Text>
        <Text style={[styles.text, styles.sshitem_username]}>{user}</Text>
      </View>
    </TouchableHighlight>
    </View>
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

function SSHFooter() {
  return (
    <View>
      <TouchableHighlight onPress={() => {
        VHEApi.openEditor({
          path: SSH_LIST_FILE,
        })
      }} underlayColor="#555555">
        <Text style={[styles.text, styles.sshfooter_edit]}>Edit {SSH_LIST_FILE}</Text>
      </TouchableHighlight>
    </View>
  )
}

function SSHItems(props) {
  const { name } = props
  const items = []
  if (VHERNFile) {
    let content
    try {
      content = VHERNFile.readText(SSH_LIST_FILE)
    } catch (e) { }
    if (content)
      for (let line of content.split("\n")) {
        line = line.trim()
        if (line.startsWith("#") || !line) continue
        //user@host
        let [user, host] = line.split("@")
        if (typeof host === 'undefined') host = user
        const name = host
        const command = "ssh"
        const args = [line]
        items.push(<SSHItem {...{ user, host, name, command, args }} />)
      }
  }
  return (
    <Section name="SSH" empty={SSHItemsEmpty} footer={SSHFooter}>
      {items}
    </Section>
  )
}

export function main() {
  return (
    <View style={styles.container}>
      <SSHItems />
      <TouchableHighlight onPress={() => {
        VHEApi.openEditor({
          folder: '~/vhe-modules/',
          paths: [
            '~/vhe-modules/new-session.js',
            '~/vhe-modules/new-session-default.js',
          ]
        })
      }} underlayColor="#555555">
        <Text style={[styles.text, styles.footer_edit]}>Edit this screen</Text>
      </TouchableHighlight>
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
    borderColor: '#444444',
    borderWidth: 1,
    borderRadius: 1,
    padding: 10
  },
  section_title: {
    fontSize: 10,
    color: "#CCCCCC"
  },
  section_children_container: {
  },
  footer_edit: {
    fontStyle: "italic",
    paddingTop: 4,
    paddingBottom: 10,
    color: "#999999"
  },
  sshfooter_edit: {
    fontStyle: "italic",
    paddingLeft: 10,
    paddingRight: 10,
    paddingTop: 10,
    paddingBottom: 10,
    color: "#999999"
  },
  sshitem_container: {
    flex: 1,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "stretch",
    paddingTop: 10,
    paddingBottom: 10,
    borderBottomColor: "#333333",
    borderBottomWidth: 1,
  },
  sshitem_touch_container: {
    flex: 1,
    alignItems: "center",
    flexDirection: "row",
  },
  sshitem_inner_container: {
    flex: 1,
    paddingLeft: 10,
    flexDirection: "row",
    justifyContent: "space-between",
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
