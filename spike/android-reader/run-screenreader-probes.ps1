param([string]$Adb = 'D:/Apps/Android/cloud-epub-spike/sdk/platform-tools/adb.exe', [string]$Serial = 'emulator-5554', [string]$CaptureName = 'backtalk-api35')
$ErrorActionPreference='Stop'
$env:ANDROID_SERIAL=$Serial
$checks=[Collections.Generic.List[object]]::new()
function Command([string]$Name,[string]$Data='') { (& "$PSScriptRoot/invoke-spike.ps1" -Command $Name -Data $Data -Adb $Adb).data.result }
function Check([string]$Name,[bool]$Pass,$Observed) {
    $checks.Add([ordered]@{name=$Name;pass=$Pass;observed=$Observed})
    "${Name}: $Pass"
    $checks | ConvertTo-Json -Depth 40 | Set-Content "$PSScriptRoot/evidence/screenreader-$CaptureName.json" -Encoding utf8
}
function Explore([double]$X,[double]$Y) {
    & $Adb -s $Serial shell su 0 sh /data/local/tmp/spike-touch.sh ([int]($X*32767/1080)) ([int]($Y*32767/1920)) 1 explore
    Start-Sleep -Milliseconds 500
}
function Activate {
    # Physical keyboard events enter the service's actual new-keymap activation path.
    & $Adb -s $Serial shell su 0 sh /data/local/tmp/screenreader-key.sh 125 57
    Start-Sleep -Milliseconds 500
}
function Choose-ImageAction([string]$Name,[int]$MenuY) {
    $image=@(Command a11y-tree) | Where-Object { $_.role -eq 'android.widget.ImageView' -and $_.name -like 'Map with river*' } | Select-Object -First 1
    Explore (($image.bounds[0]+$image.bounds[2])/2) (($image.bounds[1]+$image.bounds[3])/2)
    & $Adb -s $Serial shell su 0 sh /data/local/tmp/screenreader-key.sh 125 30 29
    Start-Sleep -Milliseconds 600
    # Screen-reader-owned Actions menu coordinates, captured at 1080x1920/density 420.
    Explore 540 $MenuY
    $screenName="screenreader-$CaptureName-$($Name.Replace(' ','-')).png"
    & $Adb -s $Serial shell screencap -p /sdcard/cloud-epub-action.png
    & $Adb -s $Serial pull /sdcard/cloud-epub-action.png "$PSScriptRoot/evidence/$screenName" | Out-Null
    Activate
}
$serviceBefore=(& $Adb -s $Serial shell dumpsys accessibility) -join "`n"
if ($serviceBefore -notmatch 'Bound services:\{Service\[label=(Backtalk|TalkBack)' ) { throw 'Enable the captured TalkBack/Backtalk 16.2 service before this primary-runtime test.' }
& $Adb -s $Serial push "$PSScriptRoot/raw-touch.sh" /data/local/tmp/spike-touch.sh | Out-Null
& $Adb -s $Serial push "$PSScriptRoot/screenreader-key.sh" /data/local/tmp/screenreader-key.sh | Out-Null
Command close-image | Out-Null
Command eval '(getSelection().removeAllRanges(),null)' | Out-Null
Command controls false | Out-Null
Command chapter 1 | Out-Null
Command font 100 | Out-Null
$point=Command eval '(()=>{const b=spike.controls.get(0);b.scrollIntoView({block:"center"});const r=b.getBoundingClientRect();return {x:(r.left+r.width/2)*devicePixelRatio,y:(r.top+r.height/2)*devicePixelRatio};})()'
$before=Command state
Command a11y-tree | Out-Null
Explore $point.x ($point.y+$before.readerBounds[1])
$focusBefore=@(Command a11y-tree)
$untouched=Command state
$focused=@($focusBefore | Where-Object { $_.accessibilityFocused -and ($_.name -like 'View image: Map*' -or $_.text -like 'View image: Map*') })
Check 'Real touch exploration focuses the image control without opening it' ($untouched.viewer -eq 'reading' -and $focused.Count -eq 1) $focusBefore
& $Adb -s $Serial shell screencap -p /sdcard/cloud-epub-focus.png
& $Adb -s $Serial pull /sdcard/cloud-epub-focus.png "$PSScriptRoot/evidence/screenreader-$CaptureName-focus-before-open.png" | Out-Null
if ($untouched.viewer -ne 'reading' -or $focused.Count -ne 1) { throw 'Screen-reader focus calibration failed; do not score ordinary taps as accessibility activation.' }
Activate
$opened=Command state
Check 'Screen-reader keyboard activation opens the exact original image' ($opened.viewer -eq 'popup' -and $opened.viewerDetails.description -like 'Map with river*') $opened
& $Adb -s $Serial shell input keyevent 4
Start-Sleep -Milliseconds 1200
$returned=Command state
$returnTree=@(Command a11y-tree)
Check 'Back restores actual accessibility focus to the same original-image control' ($returned.viewer -eq 'reading' -and $returned.nativeImageControlFocused -and $returned.nativeImageIndex -eq 0) @{state=$returned;tree=$returnTree}
& $Adb -s $Serial shell screencap -p /sdcard/cloud-epub-focus.png
& $Adb -s $Serial pull /sdcard/cloud-epub-focus.png "$PSScriptRoot/evidence/screenreader-$CaptureName-focus-return.png" | Out-Null
Check 'Screen-reader popup return preserves reading position' ($returned.reader.scrollY -eq $before.reader.scrollY -and $returned.reader.viewport -eq $before.reader.viewport) @{before=$before.reader;after=$returned.reader}
Activate
$reopened=Command state
Check 'Activation after return reopens the original image without another exploration gesture' ($reopened.viewer -eq 'popup' -and $reopened.viewerDetails.description -eq $opened.viewerDetails.description) $reopened
$close=@(Command a11y-tree) | Where-Object { $_.name -eq 'Close image' } | Select-Object -First 1
Explore (($close.bounds[0]+$close.bounds[2])/2) (($close.bounds[1]+$close.bounds[3])/2)
Activate
$closed=Command state
Check 'Screen-reader Close action returns to reading' ($closed.viewer -eq 'reading') $closed
Start-Sleep -Milliseconds 600
Activate
$full=@(Command a11y-tree) | Where-Object { $_.name -eq 'Full screen' } | Select-Object -First 1
Explore (($full.bounds[0]+$full.bounds[2])/2) (($full.bounds[1]+$full.bounds[3])/2)
Activate
$fullState=Command state
Check 'Screen-reader Full screen action hides visible metadata' ($fullState.viewer -eq 'full-screen' -and !$fullState.viewerDetails.metadataVisible) $fullState
Choose-ImageAction 'Zoom image in' 940
$zoom=Command state
Check 'Screen-reader Actions menu executes Zoom image in' $zoom.viewerDetails.zoomed $zoom.viewerDetails
foreach ($pan in @(@('Pan left',810,0,1),@('Pan right',940,0,-1),@('Pan up',1068,1,1),@('Pan down',1195,1,-1))) {
    $previous=Command state
    Choose-ImageAction $pan[0] $pan[1]
    $moved=Command state
    $delta=$moved.viewerDetails.imageBounds[$pan[2]]-$previous.viewerDetails.imageBounds[$pan[2]]
    Check "Screen-reader Actions menu executes $($pan[0])" ($delta*$pan[3] -gt 0) @{before=$previous.viewerDetails;after=$moved.viewerDetails}
}
Choose-ImageAction 'Fit whole image' 680
$fit=Command state
Check 'Screen-reader Actions menu restores whole-image fit' (!$fit.viewerDetails.zoomed) $fit.viewerDetails
& $Adb -s $Serial shell input keyevent 4
$popupAgain=Command state
Check 'Back exits full screen to the popup with the service active' ($popupAgain.viewer -eq 'popup') $popupAgain
& $Adb -s $Serial shell input keyevent 4
Start-Sleep -Milliseconds 1000
$readingAgain=Command state
Check 'Second Back restores the reading passage and original-image accessibility focus' ($readingAgain.viewer -eq 'reading' -and $readingAgain.nativeImageControlFocused -and $readingAgain.reader.scrollY -eq $before.reader.scrollY) $readingAgain
$serviceAfter=(& $Adb -s $Serial shell dumpsys accessibility) -join "`n"
Check 'Screen-reader service remains bound throughout the run' ($serviceAfter -match 'Bound services:\{Service\[label=(Backtalk|TalkBack)' ) $serviceAfter
$serviceBefore | Set-Content "$PSScriptRoot/evidence/screenreader-$CaptureName-before.txt" -Encoding utf8
$serviceAfter | Set-Content "$PSScriptRoot/evidence/screenreader-$CaptureName-after.txt" -Encoding utf8
& $Adb -s $Serial exec-out run-as dev.cloudepub.spike cat files/journal.jsonl | Set-Content "$PSScriptRoot/evidence/runtime-screenreader-$CaptureName.jsonl" -Encoding utf8
