param([string]$Adb = "$env:ANDROID_HOME/platform-tools/adb.exe")
$ErrorActionPreference = 'Stop'
$checks = [Collections.Generic.List[object]]::new()
function Command([string]$Name, [string]$Data = '') { (& "$PSScriptRoot/invoke-spike.ps1" -Command $Name -Data $Data -Adb $Adb).data.result }
function Check([string]$Name, [bool]$Pass, $Observed) {
    $checks.Add([ordered]@{name=$Name;pass=$Pass;observed=$Observed})
    "${Name}: $Pass"
    $checks | ConvertTo-Json -Depth 35 | Set-Content -LiteralPath "$PSScriptRoot/evidence/extended-probes.json" -Encoding utf8
}
function Tap-Action([string]$Name) {
    & $Adb -s emulator-5554 shell uiautomator dump /sdcard/cloud-epub-action.xml | Out-Null
    [xml]$tree = (& $Adb -s emulator-5554 exec-out cat /sdcard/cloud-epub-action.xml) -join "`n"
    $node = $tree.SelectNodes('//node') | Where-Object { $_.'content-desc' -eq $Name -or $_.text -eq $Name } | Select-Object -First 1
    if (!$node) { throw "Native action not found: $Name" }
    $points = [regex]::Matches($node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    & $Adb -s emulator-5554 shell input tap ([int](($points[0]+$points[2])/2)) ([int](($points[1]+$points[3])/2))
}
Command close-image | Out-Null
Command controls false | Out-Null
Command chapter 1 | Out-Null
Start-Sleep -Milliseconds 500
$before = Command state
$point = Command eval '(()=>{const r=spike.controls.get(0).getBoundingClientRect();return {x:(r.left+r.width/2)*devicePixelRatio,y:(r.top+r.height/2)*devicePixelRatio};})()'
& $Adb -s emulator-5554 shell input tap ([int]$point.x) ([int]($point.y+$before.readerBounds[1]))
$popup = Command state
Check 'Native tap opens meaningful-image popup' ($popup.viewer -eq 'popup') $popup
Tap-Action 'Full screen'
$fit = Command state
$v = $fit.viewerDetails
Check 'Full screen fits the whole image without visible metadata' (!$v.zoomed -and !$v.metadataVisible -and $v.imageBounds[0] -ge -1 -and $v.imageBounds[2] -le $v.width+1 -and $v.imageBounds[1] -ge -1 -and $v.imageBounds[3] -le $v.height+1) $v
& $Adb -s emulator-5554 shell input tap 540 900
& $Adb -s emulator-5554 shell input tap 540 900
$zoom = Command state
Check 'Native double tap zooms in' $zoom.viewerDetails.zoomed $zoom.viewerDetails
& $Adb -s emulator-5554 shell input swipe 540 900 750 1050 300
$pan = Command state
Check 'Native drag pans the zoomed image' ($pan.viewerDetails.imageBounds[0] -ne $zoom.viewerDetails.imageBounds[0]) $pan.viewerDetails
& $Adb -s emulator-5554 shell input tap 540 900
& $Adb -s emulator-5554 shell input tap 540 900
$refit = Command state
Check 'Second native double tap returns to whole-image fit' (!$refit.viewerDetails.zoomed) $refit.viewerDetails
& $Adb -s emulator-5554 shell screencap -p /sdcard/cloud-epub-full-fit.png
& $Adb -s emulator-5554 pull /sdcard/cloud-epub-full-fit.png "$PSScriptRoot/evidence/full-screen-fit.png" | Out-Null
Tap-Action 'Exit full screen'
$exit = Command state
Check 'Native Exit full screen returns to popup' ($exit.viewer -eq 'popup') $exit
Tap-Action 'Close image'
$closed = Command state
Check 'Native Close from popup preserves underlying viewport' ($closed.viewer -eq 'reading' -and $closed.reader.scrollY -eq $before.reader.scrollY -and $closed.reader.viewport -eq $before.reader.viewport) $closed
foreach ($image in @(2,3,4,5)) {
    Command eval "(spike.controls.get($image).click(),null)" | Out-Null
    $observed = Command state
    Check "Original image $image maps to native viewer" ($observed.viewer -eq 'popup') $observed.viewerDetails
    Command full-screen | Out-Null
    Tap-Action 'Close image'
}
$label = Command eval '(()=>{const r=document.createRange();r.selectNodeContents(spike.controls.get(0));getSelection().removeAllRanges();getSelection().addRange(r);return spike.selection();})()'
Check 'Generated viewer label is rejected as a Precise target' ($label.error -eq 'Generated content is not a Precise bookmark target') $label
Command eval '(getSelection().removeAllRanges(),null)' | Out-Null
Command chapter 0 | Out-Null
Command font 100 | Out-Null
$state = Command state
$point = Command eval '(()=>{const a=spike.select("unicode","well-being");spike.restore(a);const r=getSelection().getRangeAt(0).getBoundingClientRect();getSelection().removeAllRanges();return {x:(r.left+r.width/3)*devicePixelRatio,y:(r.top+r.height/2)*devicePixelRatio};})()'
$x=[int]$point.x; $y=[int]($point.y+$state.readerBounds[1])
& $Adb -s emulator-5554 shell input swipe $x $y $x $y 850
$selected = Command eval 'spike.selection()'
Check 'Native long press selects the complete hyphenated word' ($selected.quote -eq 'well-being') $selected
& $Adb -s emulator-5554 shell input swipe 930 950 150 950 300
$selectedState = Command state
Check 'Native selection blocks chapter swipes' ($selectedState.href -eq 'EPUB/one.xhtml') $selectedState
& $Adb -s emulator-5554 shell input tap 800 600
$tapOne = Command state
Check 'First native outside-text tap clears selection only' ($null -eq $tapOne.reader.selected -and !$tapOne.controls) $tapOne
& $Adb -s emulator-5554 shell input tap 800 600
$tapTwo = Command state
Check 'Second native tap shows reader controls' $tapTwo.controls $tapTwo
Command eval '(getSelection().removeAllRanges(),null)' | Out-Null
Command controls true | Out-Null
& $Adb -s emulator-5554 shell uiautomator dump /sdcard/cloud-epub-toolbar.xml | Out-Null
& $Adb -s emulator-5554 pull /sdcard/cloud-epub-toolbar.xml "$PSScriptRoot/evidence/native-toolbar.xml" | Out-Null
[xml]$toolbar = Get-Content -LiteralPath "$PSScriptRoot/evidence/native-toolbar.xml" -Raw
$targets = @($toolbar.SelectNodes('//node[@content-desc!=""]') | ForEach-Object {
    $b=[regex]::Matches($_.bounds,'\d+') | ForEach-Object { [int]$_.Value }
    [ordered]@{name=$_.'content-desc';width=$b[2]-$b[0];height=$b[3]-$b[1]}
})
Check 'Named native icon targets are at least 48dp at density 420' (@($targets | Where-Object { $_.width -lt 126 -or $_.height -lt 126 }).Count -eq 0 -and $targets.Count -eq 7) $targets
$themeBefore = Command state
Tap-Action 'Toggle light/dark theme'
$themeChanged = Command state
Check 'Native theme toggle changes theme' ($themeChanged.dark -ne $themeBefore.dark) $themeChanged
Command save-progress | Out-Null
& $Adb -s emulator-5554 shell am force-stop dev.cloudepub.spike
& $Adb -s emulator-5554 shell am start -n dev.cloudepub.spike/.MainActivity | Out-Null
$themeRestart = Command state
Check 'Theme persists through force-stop and restart' ($themeRestart.dark -eq $themeChanged.dark) $themeRestart
Command controls false | Out-Null
$anchor = Command eval 'spike.firstVisible()'
Command save-regular | Out-Null
& $Adb -s emulator-5554 shell settings put system font_scale 1.3
& $Adb -s emulator-5554 shell wm density 480
Start-Sleep -Milliseconds 900
Command restore regular | Out-Null
$scaled = Command eval "spike.snapshot($($anchor | ConvertTo-Json -Depth 10 -Compress))"
Check 'Original destination remains visible after system font/display scaling' $scaled.visible $scaled
& $Adb -s emulator-5554 shell settings put system font_scale 1.0
& $Adb -s emulator-5554 shell wm density reset
Start-Sleep -Milliseconds 900
Command eval '(scrollTo(0,0),null)' | Out-Null
& $Adb -s emulator-5554 shell input swipe 550 1400 550 500 450
$during = Command eval 'spike.state()'
Check 'Native user scrolling shows the progress indicator' ($during.indicator -eq '1') $during
Start-Sleep -Milliseconds 3500
$after = Command eval 'spike.state()'
Check 'Progress indicator hides after user scrolling stops' ($after.indicator -eq '0') $after
& $Adb -s emulator-5554 exec-out run-as dev.cloudepub.spike cat files/journal.jsonl | Set-Content -LiteralPath "$PSScriptRoot/evidence/runtime-extended.jsonl" -Encoding utf8
$checks | ConvertTo-Json -Depth 35
