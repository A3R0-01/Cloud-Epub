param([string]$Adb = 'D:/Apps/Android/cloud-epub-spike/sdk/platform-tools/adb.exe')
$ErrorActionPreference = 'Stop'
$checks = [Collections.Generic.List[object]]::new()
function Command([string]$Name, [string]$Data = '') { (& "$PSScriptRoot/invoke-spike.ps1" -Command $Name -Data $Data -Adb $Adb).data.result }
function Check([string]$Name, [bool]$Pass, $Observed) {
    $checks.Add([ordered]@{name=$Name;pass=$Pass;observed=$Observed})
    "${Name}: $Pass"
    $checks | ConvertTo-Json -Depth 35 | Set-Content "$PSScriptRoot/evidence/gap-followup.json" -Encoding utf8
}
function Wait-Viewer {
    $deadline=[DateTime]::UtcNow.AddSeconds(12)
    do { Start-Sleep -Milliseconds 250; $state=Command state } while ($state.viewer -ne 'popup' -and [DateTime]::UtcNow -lt $deadline)
    if ($state.viewer -ne 'popup') { throw 'Image viewer did not finish opening.' }
    return $state
}
function Tap-Action([string]$Name) {
    & $Adb shell uiautomator dump /sdcard/cloud-epub-gap.xml | Out-Null
    [xml]$tree = (& $Adb exec-out cat /sdcard/cloud-epub-gap.xml) -join "`n"
    $node = $tree.SelectNodes('//node') | Where-Object { $_.'content-desc' -eq $Name -or $_.text -eq $Name } | Select-Object -First 1
    if (!$node) { throw "Native action not found: $Name" }
    $points = [regex]::Matches($node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    & $Adb shell input tap ([int](($points[0]+$points[2])/2)) ([int](($points[1]+$points[3])/2))
}
Command close-image | Out-Null
Command eval '(getSelection().removeAllRanges(),null)' | Out-Null
Command controls false | Out-Null
Command chapter 1 | Out-Null
Command font 100 | Out-Null
foreach ($index in @(3,6,7,8,9)) {
    $point = Command eval "(()=>{const b=spike.controls.get($index);b.scrollIntoView({block:'center'});const r=b.getBoundingClientRect();return {x:(r.left+r.width/2)*devicePixelRatio,y:(r.top+r.height/2)*devicePixelRatio};})()"
    $before=Command state
    & $Adb shell input tap ([int]$point.x) ([int]($point.y+$before.readerBounds[1]))
    $opened=Wait-Viewer
    Check "Native image $index opens viewer before navigation" ($opened.viewer -eq 'popup' -and $opened.href -eq 'EPUB/two.xhtml') $opened.viewerDetails
    if ($opened.viewer -ne 'popup') { throw "Image $index did not open; stop before testing unavailable viewer data." }
    if ($index -ge 6) { Check "Rasterized image $index retains source dimensions" ($opened.viewerDetails.sourceWidth -eq 1200 -and $opened.viewerDetails.sourceHeight -eq 800) $opened.viewerDetails }
    if ($index -eq 7) { Check 'Picture uses the matching source rather than the fallback image' ($opened.viewerDetails.resolvedSrc.EndsWith('/equation.png')) $opened.viewerDetails }
    if ($index -eq 3) {
        Tap-Action 'Open image link: three.xhtml#end'
        Start-Sleep -Milliseconds 500
        $linked=Command state
        $fragment=Command eval '({visible:document.getElementById("end").getBoundingClientRect().top<innerHeight,scrollY})'
        Check 'Explicit native image link navigates to its fragment' ($linked.href -eq 'EPUB/three.xhtml' -and $linked.viewer -eq 'reading' -and $fragment.visible -and $fragment.scrollY -gt 0) @{state=$linked;fragment=$fragment}
        Command chapter 1 | Out-Null
    } else { Command close-image | Out-Null }
}
& $Adb shell wm size 1080x1000
Start-Sleep -Milliseconds 500
Command eval '(scrollTo(0,document.documentElement.scrollHeight),null)' | Out-Null
$before=Command state
Command eval '(spike.controls.get(9).click(),null)' | Out-Null
Wait-Viewer | Out-Null
& $Adb shell input tap 10 450
$backdrop=Command state
Check 'Backdrop tap retains the image popup' ($backdrop.viewer -eq 'popup') $backdrop
Command full-screen | Out-Null
$full=Command state
Check 'Full screen at chapter bottom keeps the mounted reader dimensions and offset' ($full.reader.viewport -eq $before.reader.viewport -and $full.reader.scrollY -eq $before.reader.scrollY) @{before=$before.reader;full=$full.reader}
Command close-image | Out-Null
$closed=Command state
Check 'Close from full screen at chapter bottom preserves reading position' ($closed.reader.viewport -eq $before.reader.viewport -and $closed.reader.scrollY -eq $before.reader.scrollY) $closed.reader
& $Adb shell wm size reset
Command chapter 1 | Out-Null
Command font 100 | Out-Null
Command eval '(scrollTo(0,0),spike.controls.get(0).click(),null)' | Out-Null
Start-Sleep -Milliseconds 500
Command close-image | Out-Null
Start-Sleep -Milliseconds 500
$nativeButton=Command state
$point=Command eval '(()=>{const r=spike.controls.get(0).getBoundingClientRect();return {x:(r.left+r.width/2)*devicePixelRatio,y:(r.top+r.height/2)*devicePixelRatio};})()'
$x=[int]$point.x; $y=[int]($point.y+$nativeButton.readerBounds[1])
& $Adb shell input swipe $x $y $x ([int][Math]::Max(100,$y-260)) 350
Start-Sleep -Milliseconds 500
$dragged=Command state
Check 'Vertical drag starting on the returned native image control scrolls the book' ($nativeButton.nativeImageControl -and !$dragged.nativeImageControl -and $dragged.reader.scrollY -gt $nativeButton.reader.scrollY -and $dragged.href -eq 'EPUB/two.xhtml') @{before=$nativeButton;after=$dragged}
Command chapter 1 | Out-Null
Command font 100 | Out-Null
Command eval '(scrollTo(0,0),spike.controls.get(0).click(),null)' | Out-Null
Start-Sleep -Milliseconds 500
Command close-image | Out-Null
Start-Sleep -Milliseconds 500
$nativeButton=Command state
$point=Command eval '(()=>{const r=spike.controls.get(0).getBoundingClientRect();return {x:(r.left+r.width/2)*devicePixelRatio,y:(r.top+r.height/2)*devicePixelRatio};})()'
$x=[int]$point.x; $y=[int]($point.y+$nativeButton.readerBounds[1])
& $Adb shell input swipe $x $y 930 $y 350
Start-Sleep -Milliseconds 600
$swiped=Command state
Check 'Horizontal swipe starting on the native image control changes chapter without opening an image' ($nativeButton.nativeImageControl -and $swiped.href -eq 'EPUB/one.xhtml' -and $swiped.reader.scrollY -lt 2 -and $swiped.viewer -eq 'reading') $swiped
Command chapter 0 | Out-Null
Command font 100 | Out-Null
Start-Sleep -Seconds 2
Command eval '(scrollTo(0,0),null)' | Out-Null
$mark=Command eval 'Date.now()'
& $Adb shell input swipe 550 1550 550 300 85
# Wait for the real last-scroll timer, rather than assuming the fling ends within 3s.
$deadline=[DateTime]::UtcNow.AddSeconds(15)
do {
    Start-Sleep -Milliseconds 800
    $events=@(& $Adb exec-out run-as dev.cloudepub.spike cat files/journal.jsonl | ForEach-Object { $_ | ConvertFrom-Json } | Where-Object { $_.time -ge $mark -and $_.type -eq 'bridge' -and $_.data.type -in @('user-scroll','indicator-hidden') })
} while (($events.Count -eq 0 -or $events[-1].data.type -ne 'indicator-hidden') -and [DateTime]::UtcNow -lt $deadline)
$scroll=@($events | Where-Object { $_.data.type -eq 'user-scroll' })
$duration=$scroll[-1].data.data-$scroll[0].data.data
$hideDelay=$events[-1].data.data-$scroll[-1].data.data
Check 'Uninterrupted fling remains classified beyond the former 500ms cutoff' ($duration -gt 500) @{durationMs=$duration;events=$scroll.Count}
Check 'Indicator hides three seconds after the last inertial scroll' ($hideDelay -ge 2990 -and $hideDelay -lt 3300) @{hideDelayMs=$hideDelay;events=$events}
$anchor=Command eval 'spike.firstVisible()'
& $Adb shell input keyevent 3
Start-Sleep -Milliseconds 700
& $Adb shell am force-stop dev.cloudepub.spike
& $Adb shell am start -n dev.cloudepub.spike/.MainActivity | Out-Null
Start-Sleep -Milliseconds 1400
$restored=Command eval "spike.snapshot($($anchor | ConvertTo-Json -Depth 10 -Compress))"
Check 'Background save and process restart restore the same original passage' $restored.visible $restored
Command controls true | Out-Null
Command title 'A very long original publication title that must truncate symmetrically while retaining its complete accessible name' | Out-Null
$title=Command state
Check 'Long title stays centered and truncates between symmetric action reserves' ($title.titleEllipsized -and [Math]::Abs($title.titleLeft+$title.titleWidth/2-540) -lt 2) $title
foreach ($dark in @($true,$false)) {
    if ((Command state).dark -ne $dark) { Tap-Action 'Toggle light/dark theme' }
    Start-Sleep -Milliseconds 1100
    $theme=Command eval 'spike.state()'
    $expected=if ($dark) {'rgb(24, 28, 33)'} else {'rgb(250, 247, 240)'}
    Check "Exact accepted reader palette: dark=$dark" ($theme.background -eq $expected) $theme
}
Command title 'Original-content anchor probes' | Out-Null
Command controls false | Out-Null
Command chapter 1 | Out-Null
Command font 100 | Out-Null
$anchor=Command eval '(scrollTo(0,250),spike.firstVisible())'
Command eval '(spike.controls.get(3).click(),null)' | Out-Null
Start-Sleep -Milliseconds 300
Command full-screen | Out-Null
& $Adb shell wm size 1920x1080
Start-Sleep -Milliseconds 900
Command close-image | Out-Null
Start-Sleep -Milliseconds 500
$rotated=Command eval "spike.snapshot($($anchor | ConvertTo-Json -Depth 10 -Compress))"
Check 'Reader reflow during image inspection restores the original passage on Close' $rotated.visible $rotated
& $Adb shell wm size reset
Command chapter 0 | Out-Null
Command font 100 | Out-Null
Command eval '(spike.select("duplicate-b","lantern",1),null)' | Out-Null
Command save-precise | Out-Null
Command eval '(getSelection().removeAllRanges(),scrollTo(0,400),null)' | Out-Null
Command corrupt-precise | Out-Null
$beforeInvalid=Command eval 'spike.state()'
Command restore precise | Out-Null
& $Adb shell uiautomator dump /sdcard/cloud-epub-unavailable.xml | Out-Null
$unavailable=(& $Adb exec-out cat /sdcard/cloud-epub-unavailable.xml) -join "`n"
$afterInvalid=Command eval 'spike.state()'
Check 'Unavailable same-resource bookmark explains the failure without moving to another occurrence' ($unavailable.Contains('Saved location unavailable') -and $beforeInvalid.scrollY -eq $afterInvalid.scrollY) @{before=$beforeInvalid;after=$afterInvalid}
Tap-Action 'OK'
& $Adb exec-out run-as dev.cloudepub.spike cat files/journal.jsonl | Set-Content "$PSScriptRoot/evidence/runtime-gap-followup.jsonl" -Encoding utf8
