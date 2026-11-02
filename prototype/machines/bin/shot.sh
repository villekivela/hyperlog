#!/usr/bin/env bash
# usage: shot.sh <name> <level> <d> <theme> [az] [el]: renders machines.html's stage alone, 728 × 582
cd "$(dirname "$0")/.."
mkdir -p shots
timeout 90 chromium --headless=new --disable-gpu --enable-unsafe-swiftshader --use-angle=swiftshader --hide-scrollbars --force-device-scale-factor=2 --window-size=364,291 --virtual-time-budget=12000 --screenshot=shots/$1.png "file://$PWD/machines.html?level=$2&d=$3&theme=$4&az=${5:-62}&el=${6:-8}&shot=1" >/dev/null 2>&1
