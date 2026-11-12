#!/bin/sh
adb install -r out/borrowed-time-debug.apk
mkdir -p shots
for s in create setup pick welcome home home_spent block block_ask settings home_skew home_end; do
  adb shell am start -S -n app.borrowedtime.debug/app.borrowedtime.MainActivity --es demo $s
  sleep 9
  adb exec-out screencap -p > shots/$s.png
done
