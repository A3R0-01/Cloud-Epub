#!/system/bin/sh
# Raw keyboard input on the identified disposable AOSP emulator keyboard device.
# Backtalk's new keymap uses Linux KEY_LEFTMETA (125) + KEY_SPACE (57) to activate.
modifier=$1
key=$2
extra=$3
sendevent /dev/input/event1 1 "$modifier" 1
sendevent /dev/input/event1 0 0 0
if [ -n "$extra" ]; then
  sendevent /dev/input/event1 1 "$extra" 1
  sendevent /dev/input/event1 0 0 0
fi
sendevent /dev/input/event1 1 "$key" 1
sendevent /dev/input/event1 0 0 0
sendevent /dev/input/event1 1 "$key" 0
sendevent /dev/input/event1 0 0 0
if [ -n "$extra" ]; then
  sendevent /dev/input/event1 1 "$extra" 0
  sendevent /dev/input/event1 0 0 0
fi
sendevent /dev/input/event1 1 "$modifier" 0
sendevent /dev/input/event1 0 0 0
