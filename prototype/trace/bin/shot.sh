#!/usr/bin/env bash
# usage: shot.sh <name> <src> <d> <az> <el>: render traced.html's stage headless, cropped like shots/run.sh
cd "$(dirname "$0")/.."
timeout 90 chromium --headless=new --disable-gpu --enable-unsafe-swiftshader --use-angle=swiftshader --allow-file-access-from-files --hide-scrollbars --force-device-scale-factor=2 --window-size=440,560 --virtual-time-budget=9000 --screenshot=out/shots/$1.png "file://$PWD/traced.html?src=$2&d=$3&az=$4&el=$5&shot=1&muscles=off#clay" >/dev/null 2>&1

magick out/shots/$1.png -crop 688x585+96+120 +repage out/shots/$1.png
