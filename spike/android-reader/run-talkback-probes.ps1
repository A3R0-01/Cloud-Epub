param([string]$Adb = "$env:ANDROID_HOME/platform-tools/adb.exe", [string]$CaptureName = 'backtalk-api35', [string]$Serial = 'emulator-5554')
# The former bound-service + ADB tap method did not establish screen-reader activation.
# Use raw touch exploration and actual screen-reader keyboard activation instead.
& "$PSScriptRoot/run-screenreader-probes.ps1" -Adb $Adb -CaptureName $CaptureName -Serial $Serial
