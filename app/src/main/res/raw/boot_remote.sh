#!/usr/bin/env bash
set -eum

if [[ "$1" == "remote" ]]; then
  VHEDITOR_STORAGE=$HOME/.local/vheditor
  mkdir -p $VHEDITOR_STORAGE
  shift

  CODESERVER=
  if command -v code-server; then
    CODESERVER="$(which code-server)"
  else
    if test -e "$HOME/.local/bin/code-server"; then
      CODESERVER="$HOME/.local/bin/code-server"
    fi
  fi

  echo "CODESERVER=$CODESERVER"

  if ! test -e "$CODESERVER"; then
    rm -rf $VHEDITOR_STORAGE/bin
    mkdir -p $VHEDITOR_STORAGE/bin
    cat <<ASKPASS >$VHEDITOR_STORAGE/bin/askpass
#!/bin/bash
echo "[[VHEditor: sudo password request ($VHEDITORASKPASSTOKEN) $@]]" >&2
while IFS= read -r line; do
  echo "(received input)" >&2
  if [[ \$line =~ ^vheditorpassreply: ]]; then
    echo \${line:18}
    break
  fi
done
ASKPASS
    cat <<SUDO >$VHEDITOR_STORAGE/bin/sudo
#!/bin/bash
"$(which sudo)" -A "\$@"
SUDO

    chmod +x $VHEDITOR_STORAGE/bin/*
    echo "No code-server binary in PATH, will try to install."
    echo "Please install manually if failed."
    echo '$ curl -fsSL https://code-server.dev/install.sh | sh'
    echo 'curl -fsSL https://code-server.dev/install.sh | sh' >/tmp/install-cs
    echo 'or'
    echo '$ sh /tmp/install-cs'
    SUDO_ASKPASS=$VHEDITOR_STORAGE/bin/askpass PATH=$VHEDITOR_STORAGE/bin:$PATH sh -c "$(curl -fsSL https://code-server.dev/install.sh)"
    rm -rf $VHEDITOR_STORAGE/bin
    rm -rf /tmp/install-cs

    CODESERVER=
    if command -v code-server; then
      CODESERVER="$(which code-server)"
    else
      if test -e "$HOME/.local/bin/code-server"; then
        CODESERVER="$HOME/.local/bin/code-server"
      fi
    fi
  fi

  unset VHEDITORASKPASSTOKEN
  export VHEDITORASKPASSTOKEN

  if ! test -e "$CODESERVER"; then
    echo "No code-server installed."
    exit 1
  fi

  [[ "$1" == "--" ]] && shift
  ARGS_HASH=$(echo "$@" | md5sum | awk '{print $1}')

  NODE=$(NODE_OPTIONS='-v' $CODESERVER 2>&1 | awk -F: '{print $1}')
  CURRENT_SESSION_FILE="$VHEDITOR_STORAGE/current-session"
  IS_RUNNING=0
  if [[ -f "$CURRENT_SESSION_FILE" ]]; then
    CURRENT_SESSION_ARGHASH=$(F=$CURRENT_SESSION_FILE $NODE -p 'JSON.parse(require("fs").readFileSync(process.env.F,"utf8")).arg_hash')
    CURRENT_SESSION_PID=$(F=$CURRENT_SESSION_FILE $NODE -p 'JSON.parse(require("fs").readFileSync(process.env.F,"utf8")).pid')
    vhecheckexit() {
      sleep $1
      ! kill -0 $2
    }
    if [[ "$ARGS_HASH" != "$CURRENT_SESSION_ARGHASH" ]]; then
      echo "Remote session configurations is different, killing..."
      vhekill() {
        vhecheckexit 1 $1 && return
        echo "Sending SIGINT"
        kill -SIGINT $1
        vhecheckexit 1 $1 && return
        echo "Sending SIGTERM"
        kill -SIGTERM $1
        vhecheckexit 2 $1 && return
        echo "Sending SIGKILL"
        kill -SIGKILL $1
        vhecheckexit 2 $1 && return
        return 1
      }
      if ! vhekill $CURRENT_SESSION_PID; then
        echo "Killing previous session failed"
        exit 1
      fi
      rm -f $CURRENT_SESSION_FILE
    else
      if ! vhecheckexit 0 $CURRENT_SESSION_PID; then
        IS_RUNNING=1
      fi
    fi
  fi

  CF="$HOME/.local/share/code-server/Machine/settings.json" $NODE <<"ICODE"
    const fs = require("fs");
    let settings = {};
    let needwrite = false;
    if (fs.existsSync(process.env.CF))
      settings = JSON.parse(fs.readFileSync(process.env.CF, "utf-8"));
    settings = settings || {};
    if(!settings.hasOwnProperty("security.workspace.trust.enabled")) {
      console.log("Setting default option for security.workspace.trust.enabled: false");
      settings["security.workspace.trust.enabled"]=false;
      needwrite = true;
    }
    if(!settings.hasOwnProperty("terminal.integrated.gpuAcceleration")) {
      console.log("Setting default option for gpuAcceleration: off");
      settings["terminal.integrated.gpuAcceleration"] = "off";
      needwrite = true;
    }

    if(needwrite){
      fs.mkdirSync(require("path").dirname(process.env.CF), {recursive: true});
      fs.writeFileSync(process.env.CF, JSON.stringify(settings, null, 2))
    }
ICODE

  if [[ "$IS_RUNNING" == "0" ]]; then
    echo "Starting new instance..."
    if [[ "$1" == "ssl" ]]; then
      shift
      echo "$1" >$VHEDITOR_STORAGE/vheditor.cert
      echo "$2" >$VHEDITOR_STORAGE/vheditor.key
      shift
      shift
      set -- $CODESERVER --cert ~/.local/vheditor/vheditor.cert \
        --cert-key ~/.local/vheditor/vheditor.key \
        "$@"
    else
      set -- $CODESERVER "$@"
    fi
    "$@" </dev/null 2>&1 >$VHEDITOR_STORAGE/log &
    PID=$!
    echo '{"arg_hash":"'$ARGS_HASH'","pid":'$PID'}' >$CURRENT_SESSION_FILE
  else
    echo "Found existing instance."
  fi
  # show options
  head -n 100 $VHEDITOR_STORAGE/log
  # follow the logs
  tail -f $VHEDITOR_STORAGE/log
else
  SELF="${BASH_SOURCE[0]}"
  SSHCMD=()
  while [[ $# -gt 0 ]]; do
    case $1 in
    --remote--args--)
      shift
      break
      ;;
    *)
      SSHCMD+=("$1")
      shift
      ;;
    esac
  done
  ARGS=()
  for x in "$@"; do
    ARGS+=("${x@Q}")
  done
  SELF_SRC=$(cat $SELF)
  "${SSHCMD[@]}" env VHEDITORASKPASSTOKEN=$VHEDITORASKPASSTOKEN bash -s -c "${SELF_SRC@Q}" -- remote "${ARGS[@]}"
fi
