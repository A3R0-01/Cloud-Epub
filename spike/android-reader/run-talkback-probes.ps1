param([string]$Adb = "$env:ANDROID_HOME/platform-tools/adb.exe", [string]$CaptureName = 'api30')
$ErrorActionPreference = 'Stop'
# Enable the documented TalkBack build on the disposable emulator before this script.
# Do not run `uiautomator dump` while TalkBack is active: UiAutomation may suppress services.
function Command([string]$Name, [string]$Data = '') { (& "$PSScriptRoot/invoke-spike.ps1" -Command $Name -Data $Data -Adb $Adb).data.result }
$serviceBefore = (& $Adb -s emulator-5554 shell dumpsys accessibility) -join "`n"
$serviceBefore | Set-Content -LiteralPath "$PSScriptRoot/evidence/talkback-$CaptureName-before-services.txt" -Encoding utf8
if ($serviceBefore -notmatch 'Bound services:\{Service\[label=TalkBack') {
    $invalid = [ordered]@{screenReaderAssertionsValid=$false;reason='TalkBack is not a bound service; ordinary taps cannot be scored as screen-reader activation.'}
    $invalid | ConvertTo-Json | Set-Content -LiteralPath "$PSScriptRoot/evidence/talkback-$CaptureName-probes.json" -Encoding utf8
    $invalid | ConvertTo-Json
    return
}
Command close-image | Out-Null
Command controls false | Out-Null
Command chapter 1 | Out-Null
Start-Sleep -Milliseconds 500
$before = Command state
$point = Command eval '(()=>{const r=spike.controls.get(0).getBoundingClientRect();return {x:(r.left+r.width/2)*devicePixelRatio,y:(r.top+r.height/2)*devicePixelRatio};})()'
$x=[int]$point.x; $y=[int]($point.y+$before.readerBounds[1])
& $Adb -s emulator-5554 shell input tap $x $y
Start-Sleep -Milliseconds 500
& $Adb -s emulator-5554 shell input tap $x $y
& $Adb -s emulator-5554 shell input tap $x $y
$opened = Command state
& $Adb -s emulator-5554 shell screencap -p /sdcard/talkback-popup.png
& $Adb -s emulator-5554 pull /sdcard/talkback-popup.png "$PSScriptRoot/evidence/talkback-$CaptureName-popup.png" | Out-Null
& $Adb -s emulator-5554 shell input keyevent 4
Start-Sleep -Milliseconds 500
$returned = Command state
& $Adb -s emulator-5554 shell screencap -p /sdcard/talkback-return.png
& $Adb -s emulator-5554 pull /sdcard/talkback-return.png "$PSScriptRoot/evidence/talkback-$CaptureName-return.png" | Out-Null
# Global double-tap activates TalkBack's current focus without another focus-setting tap.
& $Adb -s emulator-5554 shell input tap 540 900
& $Adb -s emulator-5554 shell input tap 540 900
$activatedAfterReturn = Command state
$serviceAfter = (& $Adb -s emulator-5554 shell dumpsys accessibility) -join "`n"
$serviceValid = $serviceAfter -match 'Bound services:\{Service\[label=TalkBack'
$results = [ordered]@{
    screenReaderAssertionsValid = $serviceValid
    nativeTalkBackActivationOpenedPopup = if ($serviceValid) { $opened.viewer -eq 'popup' } else { $null }
    backPreservedReader = if ($serviceValid) { $returned.viewer -eq 'reading' -and $before.reader.scrollY -eq $returned.reader.scrollY } else { $null }
    talkBackActivationReturnedToOpeningControl = if ($serviceValid) { $activatedAfterReturn.viewer -eq 'popup' -and $activatedAfterReturn.viewerDetails.description -eq $opened.viewerDetails.description } else { $null }
    before=$before;opened=$opened;returned=$returned;activatedAfterReturn=$activatedAfterReturn
}
$results | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath "$PSScriptRoot/evidence/talkback-$CaptureName-probes.json" -Encoding utf8
$serviceAfter | Set-Content -LiteralPath "$PSScriptRoot/evidence/talkback-$CaptureName-services.txt" -Encoding utf8
& $Adb -s emulator-5554 exec-out run-as dev.cloudepub.spike cat files/journal.jsonl > "$PSScriptRoot/evidence/runtime-$CaptureName-talkback.jsonl"
$results | ConvertTo-Json -Depth 30
