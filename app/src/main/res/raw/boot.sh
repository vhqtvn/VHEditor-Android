#!/data/data/vn.vhn.vsc/files/usr/bin/bash
[ -f ~/.profile ] && . ~/.profile
function _at_exit()
{
  kill $(cat /data/data/vn.vhn.vsc/files/.pid)
  rm -f /data/data/vn.vhn.vsc/files/.pid
}
trap _at_exit EXIT SIGHUP SIGTERM SIGINT SIGKILL
chmod +x /data/data/vn.vhn.vsc/files/code-server/release-standalone/lib/vscode/node_modules/vscode-ripgrep/bin/rg
/data/data/vn.vhn.vsc/files/node "$@" &
echo $! > /data/data/vn.vhn.vsc/files/.pid
wait $!