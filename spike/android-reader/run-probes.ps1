param([string]$Adb = "$env:ANDROID_HOME/platform-tools/adb.exe")
$ErrorActionPreference = 'Stop'
# This resets only the disposable fixture app and its emulator viewport.
& $Adb -s emulator-5554 shell wm size reset
& $Adb -s emulator-5554 shell pm clear dev.cloudepub.spike | Out-Null
& $Adb -s emulator-5554 shell am start -n dev.cloudepub.spike/.MainActivity | Out-Null
Start-Sleep -Seconds 2
$results = [Collections.Generic.List[object]]::new()
function Command([string]$Name, [string]$Data = '') {
    (& "$PSScriptRoot/invoke-spike.ps1" -Command $Name -Data $Data -Adb $Adb).data.result
}
function Check([string]$Name, [bool]$Pass, $Observed) {
    $results.Add([ordered]@{name=$Name; pass=$Pass; observed=$Observed})
    "${Name}: $Pass"
    $results | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath "$PSScriptRoot/evidence/probes.json" -Encoding utf8
}
Command chapter 0 | Out-Null
Start-Sleep -Milliseconds 600
$state = Command state
Check 'Reader opens with hidden controls' (!$state.controls) $state
Check 'Injected margins survive Readium CSS' ($state.reader.bodyPadding -eq '10px 31px 24px') $state.reader
Command eval '(scrollTo(0,document.documentElement.scrollHeight),spike.state())' | Out-Null
& $Adb -s emulator-5554 shell input swipe 550 1400 550 350 300
Start-Sleep -Milliseconds 500
$state = Command state
Check 'Native vertical scroll at bottom does not advance' ($state.href -eq 'EPUB/one.xhtml') $state
& $Adb -s emulator-5554 shell input swipe 930 950 150 950 300
Start-Sleep -Milliseconds 700
$next = Command state
Check 'Native horizontal next chapter lands at beginning' ($next.href -eq 'EPUB/two.xhtml' -and $next.reader.scrollY -lt 2) $next
& $Adb -s emulator-5554 shell input swipe 150 950 930 950 300
Start-Sleep -Milliseconds 700
$previous = Command state
Check 'Native horizontal previous chapter lands at beginning' ($previous.href -eq 'EPUB/one.xhtml' -and $previous.reader.scrollY -lt 2) $previous
Command chapter 0 | Out-Null
Start-Sleep -Milliseconds 500
$anchor = Command eval "spike.select('duplicate-b','lantern',1)"
Command save-precise | Out-Null
Command eval '(getSelection().removeAllRanges(),scrollTo(0,0),null)' | Out-Null
$fontBefore = Command eval 'spike.state()'
Command font 150 | Out-Null
$fontAfter = Command eval 'spike.state()'
Check 'Requested font reflow changes computed size and family' ($fontAfter.fontPixels -gt $fontBefore.fontPixels*1.4 -and $fontAfter.fontPixels -lt $fontBefore.fontPixels*1.6 -and $fontAfter.fontFamily -ne $fontBefore.fontFamily) @{before=$fontBefore;after=$fontAfter}
Command restore precise | Out-Null
$restored = Command eval "({target:spike.snapshot($($anchor | ConvertTo-Json -Depth 10 -Compress)),parent:spike.nodes[$($anchor.start.node)].parentElement.id,highlight:spike.state().highlight})"
Check 'Programmatic exact duplicate occurrence survives font reflow' ($restored.target.visible -and $restored.parent -eq 'duplicate-b' -and $restored.target.anchor.start.offset -eq $anchor.start.offset) $restored
Check 'Precise arrival highlight exists briefly' $restored.highlight $restored
foreach ($probe in @(@('unicode',"don’t"),@('unicode','well-being'),@('unicode','café'),@('unicode','café'),@('unicode','lantern'),@('unicode','soft­hyphen'),@('unicode','non breaking'),@('unicode','ofﬁce'),@('split','well-being'))) {
    $selected = Command eval "spike.select('$($probe[0])','$($probe[1])')"
    $result = Command eval "spike.restore($($selected | ConvertTo-Json -Depth 10 -Compress),true)"
    Check "Programmatic original-range round trip: $($probe[1])" ($result.visible -and $result.anchor.quote -eq $probe[1]) $result
}
Command eval '(getSelection().removeAllRanges(),null)' | Out-Null
Command chapter 1 | Out-Null
Start-Sleep -Milliseconds 600
& $Adb -s emulator-5554 shell wm size 1080x1000
Start-Sleep -Milliseconds 600
$images = Command eval 'spike.state()'
Check 'Ten original images become nine meaningful-image buttons' ($images.images -eq 10 -and $images.buttons -eq 9) $images
Check 'Generated labels excluded from original text' ($images.canonicalUnchanged -and !$images.generatedLabelsInOriginal) $images
$caption = Command eval "spike.select('caption','lantern')"
$captionResult = Command eval "spike.restore($($caption | ConvertTo-Json -Depth 10 -Compress),true)"
Check 'Original caption range restores' $captionResult.visible $captionResult
Command eval '(getSelection().removeAllRanges(),spike.controls.get(0).scrollIntoView(),null)' | Out-Null
Start-Sleep -Milliseconds 1000
$setup=Command eval '(spike.controls.get(0).scrollIntoView(),spike.firstVisible())'
if ($setup.kind -ne 'image' -or $setup.image -ne 0) { throw 'Image-first-visible setup did not settle after resize.' }
Command save-regular | Out-Null
$before = Command state
Command eval '(spike.controls.get(0).click(),null)' | Out-Null
$popup = Command state
Check 'Programmatic image button opens native popup' ($popup.viewer -eq 'popup') $popup
Command full-screen | Out-Null
$full = Command state
Check 'Native full-screen presentation opens' ($full.viewer -eq 'full-screen') $full
& $Adb -s emulator-5554 shell input keyevent 4
$back = Command state
Check 'First native Back returns to popup' ($back.viewer -eq 'popup') $back
& $Adb -s emulator-5554 shell input keyevent 4
$closed = Command state
Check 'Second native Back returns to unchanged reading viewport' ($closed.viewer -eq 'reading' -and $closed.reader.scrollY -eq $before.reader.scrollY -and $closed.reader.viewport -eq $before.reader.viewport) $closed
$focus = Command eval '({kind:document.activeElement.dataset.spikeGenerated,tag:document.activeElement.tagName})'
Check 'Popup returns DOM focus to opening image control' ($focus.kind -eq 'image') $focus
Command eval '(scrollTo(0,document.documentElement.scrollHeight),null)' | Out-Null
Command restore regular | Out-Null
$regular = Command eval 'spike.firstVisible()'
Check 'Image Regular bookmark restores original image destination' ($regular.kind -eq 'image' -and $regular.image -eq 0) $regular
& $Adb -s emulator-5554 shell wm size reset
Start-Sleep -Milliseconds 600
Command chapter 2 | Out-Null
Start-Sleep -Milliseconds 500
& $Adb -s emulator-5554 shell input swipe 930 950 150 950 300
Start-Sleep -Milliseconds 500
$boundary = Command state
Check 'Native outward swipe at final chapter does not wrap' ($boundary.href -eq 'EPUB/three.xhtml') $boundary
Command save-progress | Out-Null
& $Adb -s emulator-5554 exec-out run-as dev.cloudepub.spike cat files/journal.jsonl | Set-Content -LiteralPath "$PSScriptRoot/evidence/runtime.jsonl" -Encoding utf8
$results | ConvertTo-Json -Depth 30
