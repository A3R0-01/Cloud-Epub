param(
    [Parameter(Mandatory)][string]$Command,
    [string]$Data = '',
    [string]$Adb = "$env:ANDROID_HOME/platform-tools/adb.exe"
)
$ErrorActionPreference = 'Stop'
$commandId = [Guid]::NewGuid().ToString('N')
$encodedData = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Data))
$arguments = @('shell', 'am', 'broadcast', '-a', 'dev.cloudepub.spike.COMMAND', '-p', 'dev.cloudepub.spike', '--es', 'command', $Command, '--es', 'id', $commandId)
if ($encodedData) { $arguments += @('--es', 'data', $encodedData) }
$adbResponse = & $Adb @arguments 2>&1
if ($LASTEXITCODE -ne 0) { throw "ADB command failed: $adbResponse" }
$lastSent = [DateTime]::UtcNow
$received = $false
$deadline = [DateTime]::UtcNow.AddSeconds(120)
do {
    $lines = & $Adb exec-out run-as dev.cloudepub.spike cat files/journal.jsonl
    foreach ($line in $lines) {
        if (!$line.Contains($commandId)) { continue }
        $event = $line | ConvertFrom-Json
        if ($event.type -eq 'command-received' -and $event.data.id -eq $commandId) { $received = $true }
        if ($event.type -eq 'command-error' -and $event.data.id -eq $commandId) { throw $event.data.error }
        if ($event.type -eq 'command-result' -and $event.data.id -eq $commandId) { return $event }
    }
    if (!$received -and ([DateTime]::UtcNow-$lastSent).TotalSeconds -ge 1) {
        $adbResponse = & $Adb @arguments 2>&1
        if ($LASTEXITCODE -ne 0) { throw "ADB command failed: $adbResponse" }
        $lastSent = [DateTime]::UtcNow
    }
    Start-Sleep -Milliseconds 250
} while ([DateTime]::UtcNow -lt $deadline)
throw "No result for command $Command. Inspect logcat and journal.jsonl for fatal/command-error events."
