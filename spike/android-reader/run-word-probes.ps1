param([switch]$CorrectionOnly, [string]$Adb = 'D:/Apps/Android/cloud-epub-spike/sdk/platform-tools/adb.exe')
$ErrorActionPreference = 'Stop'
function Command([string]$Name, [string]$Data = '') { (& "$PSScriptRoot/invoke-spike.ps1" -Command $Name -Data $Data -Adb $Adb).data.result }
function Wait-Word([string]$Quote) {
    $deadline=[DateTime]::UtcNow.AddSeconds(10)
    do { $selected=Command eval 'spike.wordSelection()'; if ($selected.quote -eq $Quote) { return $selected }; Start-Sleep -Milliseconds 150 } while ([DateTime]::UtcNow -lt $deadline)
    return $selected
}
function Tap-Action([string]$Name) {
    $deadline=[DateTime]::UtcNow.AddSeconds(10)
    do {
        $node=@(Command a11y-tree) | Where-Object {($_.name -eq $Name -or $_.text -eq $Name) -and $_.bounds[2] -gt $_.bounds[0] -and $_.bounds[3] -gt $_.bounds[1]} | Select-Object -First 1
        if($node){break}; Start-Sleep -Milliseconds 150
    } while([DateTime]::UtcNow -lt $deadline)
    if(!$node){throw "Native action not found: $Name"}
    $points=$node.bounds
    & $Adb shell input tap ([int](($points[0]+$points[2])/2)) ([int](($points[1]+$points[3])/2))
}
function Tap-PreciseAction {
    $deadline=[DateTime]::UtcNow.AddSeconds(10)
    do {
        $windows=(& $Adb shell dumpsys window)-join "`n"
        $blocks=[regex]::Split($windows,'(?m)(?=^[ \t]+Window #\d+ Window\{)')
        foreach($block in $blocks) {
            if($block -notmatch '^\s*Window #\d+ Window\{[^\n]*PopupWindow' -or $block -notmatch 'isVisible=true' -or $block -notmatch 'package=dev.cloudepub.spike') {continue}
            $rect=[regex]::Match($block,'mAttrs=\{\((-?\d+),(-?\d+)\)\((\d+)x(\d+)\)')
            if(!$rect.Success -or [int]$rect.Groups[3].Value -lt 250 -or [int]$rect.Groups[4].Value -gt 400) {continue}
            $x=[int]$rect.Groups[1].Value; $y=[int]$rect.Groups[2].Value
            $width=[int]$rect.Groups[3].Value; $height=[int]$rect.Groups[4].Value
            $block | Set-Content "$PSScriptRoot/evidence/word-toolbar-window.txt" -Encoding utf8
            & $Adb shell input tap ([int]($x+$width*.4)) ([int]($y+$height/2))
            return @{x=$x;y=$y;width=$width;height=$height}
        }
        Start-Sleep -Milliseconds 150
    } while([DateTime]::UtcNow -lt $deadline)
    throw 'Visible native selection toolbar not found.'
}
Command clear-selection | Out-Null
Command controls false | Out-Null
Command chapter 0 | Out-Null
Command font 100 | Out-Null
$checks = [Collections.Generic.List[object]]::new()
$cases=if($CorrectionOnly){@()}else{@(@('unicode',"don’t"),@('unicode','well-being'),@('unicode','café'),@('unicode','café'),@('unicode','lantern'),@('unicode','soft­hyphen'),@('unicode','ofﬁce'),@('split','well-being'))}
foreach ($case in $cases) {
    Command clear-selection | Out-Null
    Command controls false | Out-Null
    Start-Sleep -Milliseconds 300
    $state = Command state
    $id=$case[0] | ConvertTo-Json -Compress; $quote=$case[1] | ConvertTo-Json -Compress
    $point = Command eval "(()=>{const a=spike.select($id,$quote);spike.restore(a);const r=getSelection().getRangeAt(0).getClientRects()[0];getSelection().removeAllRanges();return {x:(r.left+r.width/3)*devicePixelRatio,y:(r.top+r.height/2)*devicePixelRatio};})()"
    $x=[int]$point.x; $y=[int]($point.y+$state.readerBounds[1])
    & $Adb shell input swipe $x $y $x $y 850
    $selected = Wait-Word $case[1]
    $checks.Add([ordered]@{name="Native selection resolves original word: $($case[0]) $($case[1])";pass=$selected.quote -eq $case[1];observed=@{native=(Command eval 'spike.selection()');resolved=$selected}})
    $checks | ConvertTo-Json -Depth 30 | Set-Content "$PSScriptRoot/evidence/word-followup.json" -Encoding utf8
    Command clear-selection | Out-Null
}
Command clear-selection | Out-Null
Command controls false | Out-Null
Start-Sleep -Milliseconds 400
$mark=Command eval 'Date.now()'
# Re-select well-being, then correct to the adjacent exact occurrence through native UI.
$state=Command state
$point=Command eval '(()=>{const a=spike.select("unicode","well-being");spike.restore(a);const r=getSelection().getRangeAt(0).getClientRects()[0];getSelection().removeAllRanges();return {x:(r.left+r.width/3)*devicePixelRatio,y:(r.top+r.height/2)*devicePixelRatio};})()'
$x=[int]$point.x; $y=[int]($point.y+$state.readerBounds[1])
& $Adb shell input swipe $x $y $x $y 850
Wait-Word 'well-being' | Out-Null
Start-Sleep -Milliseconds 700
& $Adb shell screencap -p /sdcard/cloud-epub-word-menu.png
& $Adb pull /sdcard/cloud-epub-word-menu.png "$PSScriptRoot/evidence/word-menu.png" | Out-Null
# The floating toolbar is not consistently exposed by UiAutomation. Read its actual
# visible popup frame rather than assuming a fixed position after font reflow.
$toolbar=Tap-PreciseAction
Start-Sleep -Milliseconds 700
Tap-Action 'Next word'
$deadline=[DateTime]::UtcNow.AddSeconds(10)
do {
    $events=& $Adb exec-out run-as dev.cloudepub.spike cat files/journal.jsonl | ForEach-Object {$_|ConvertFrom-Json}
    $corrected=($events | Where-Object {$_.time -ge $mark -and $_.type -eq 'word-correction'} | Select-Object -Last 1).data
    if($corrected.quote -eq 'café'){break}; Start-Sleep -Milliseconds 150
} while([DateTime]::UtcNow -lt $deadline)
Tap-Action 'Save'
$deadline=[DateTime]::UtcNow.AddSeconds(10)
do {
    $events=& $Adb exec-out run-as dev.cloudepub.spike cat files/journal.jsonl | ForEach-Object {$_|ConvertFrom-Json}
    $saved=($events | Where-Object {$_.time -ge $mark -and $_.type -eq 'bookmark-saved-precise'} | Select-Object -Last 1).data.anchor
    if($saved.quote -eq 'café'){break}; Start-Sleep -Milliseconds 150
} while([DateTime]::UtcNow -lt $deadline)
$checks.Add([ordered]@{name='Native confirmation and correction before save retain the original occurrence';pass=$corrected.quote -eq 'café' -and $saved.quote -eq 'café' -and $saved.start.node -eq $corrected.start.node -and $saved.start.offset -eq $corrected.start.offset;observed=@{corrected=$corrected;saved=$saved;toolbar=$toolbar}})
$checks | ConvertTo-Json -Depth 30 | Set-Content "$PSScriptRoot/evidence/word-followup.json" -Encoding utf8
$checks | ForEach-Object { "$($_['name']): $($_['pass'])" }

& $Adb exec-out run-as dev.cloudepub.spike cat files/journal.jsonl | Set-Content "$PSScriptRoot/evidence/runtime-word-followup.jsonl" -Encoding utf8
