param(
    [string]$Sdk='D:/Apps/Android/cloud-epub-spike/sdk',
    [string]$Jdk='D:/Apps/Android/Android Studio/jbr',
    [string]$Adb="$Sdk/platform-tools/adb.exe"
)
$ErrorActionPreference='Stop'
$build=Join-Path ([IO.Path]::GetTempPath()) 'cloud-epub-touch-probe'
New-Item -ItemType Directory -Path "$build/classes","$build/dex" -Force | Out-Null
$env:JAVA_HOME=$Jdk
& "$Jdk/bin/javac.exe" --release 8 -cp "$Sdk/platforms/android-37.0/android.jar" -d "$build/classes" "$PSScriptRoot/probes/TouchProbe.java"
if($LASTEXITCODE -ne 0){throw 'Touch probe Java compilation failed'}
& "$Sdk/build-tools/36.0.0/d8.bat" --lib "$Sdk/platforms/android-37.0/android.jar" --min-api 24 --output "$build/dex" "$build/classes/TouchProbe.class"
if($LASTEXITCODE -ne 0){throw 'Touch probe dex compilation failed'}
& $Adb push "$build/dex/classes.dex" /data/local/tmp/cloud-epub-touch.dex | Out-Null
if($LASTEXITCODE -ne 0){throw 'Touch probe upload failed'}
