#!/data/data/vn.vhn.vsc/files/usr/bin/bash
[ -f ~/.profile ] && . ~/.profile
function _at_exit()
{
  kill $(cat /data/data/vn.vhn.vsc/files/.pid)
  rm -f /data/data/vn.vhn.vsc/files/.pid
}
trap _at_exit EXIT SIGHUP SIGTERM SIGINT SIGKILL
chmod +x /data/user/0/vn.vhn.vsc/files/code-server/release-standalone/vendor/modules/code-oss-dev/node_modules/vscode-ripgrep/bin/rg
mkdir -p /data/data/vn.vhn.vsc/files/tmp
/data/data/vn.vhn.vsc/files/node "$@" &
echo $! > /data/data/vn.vhn.vsc/files/.pid
wait $!