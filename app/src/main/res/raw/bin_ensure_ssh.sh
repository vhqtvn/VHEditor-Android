#!/data/data/vn.vhn.vsc/files/usr/bin/bash

RED="\033[0;31m"
NC="\033[0m"

while ! command -v ssh &>/dev/null; do
  set +x
  echo -e ${RED}ssh command not installed, which package do you want to install?${NC}
  echo 1. dropbear
  echo 2. openssh
  read -n1 kbd
  case $kbd in
    1) set -x && apt update && apt install dropbear && echo installed && exit 0 ;;
    2) set -x && apt update && apt install openssh && echo installed && exit 0 ;;
  esac
done
