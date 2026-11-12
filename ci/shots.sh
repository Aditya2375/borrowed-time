#!/bin/sh
adb install -r out/borrowed-time-debug.apk
mkdir -p shots
for s in welcome create setup pick home home_spent block block_ask settings; do
  adb shell am start -S -n app.borrowedtime.debug/app.borrowedtime.MainActivity --es demo $s
  sleep 4
  adb exec-out screencap -p > shots/$s.png
done
