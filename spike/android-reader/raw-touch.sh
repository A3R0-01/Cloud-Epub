#!/system/bin/sh
# Disposable AOSP emulator only. event2 was identified with getevent/dumpsys input.
# Raw axis range: 0..32767; caller supplies normalized coordinates.
x=$1
y=$2
count=${3:-1}
i=0
while [ "$i" -lt "$count" ]; do
  sendevent /dev/input/event2 3 47 0
  sendevent /dev/input/event2 3 57 7
  sendevent /dev/input/event2 3 53 "$x"
  sendevent /dev/input/event2 3 54 "$y"
  sendevent /dev/input/event2 3 58 100
  sendevent /dev/input/event2 3 48 5
  sendevent /dev/input/event2 0 0 0
  sleep .08
  if [ "$4" = "explore" ]; then
    sendevent /dev/input/event2 3 53 $((x+40))
    sendevent /dev/input/event2 0 0 0
    sleep .6
  fi
  sendevent /dev/input/event2 3 57 -1
  sendevent /dev/input/event2 0 0 0
  sleep .08
  i=$((i+1))
done
