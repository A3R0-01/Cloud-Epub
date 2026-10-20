# TalkBack test dependency

Public Google source commit [229212fdf5842191d0a93fc95d9ca1423b346866](https://github.com/google/talkback/tree/229212fdf5842191d0a93fc95d9ca1423b346866), downloaded as the GitHub source archive. Version `TfPu_release_16_2`; locally generated version name `TfPu_release_16_2-2026_10_20_1256`. Package `com.android.talkback`. [APK hash/metadata](google-talkback-apk-verification.txt) identify the resulting local binary.

The source tree is unchanged. An [external Gradle init script](google-talkback-build.gradle) restricts native builds to x86_64 and aligns Kotlin JVM bytecode with Java 17. Without the latter, the first build failed with Java17/Kotlin21 target mismatch. Native symbol-stripping and annotation warnings in [the successful log](google-talkback-build.log) do not prevent APK assembly.

Reproduce using the source tree and separately installed Gradle 8.14.3, Microsoft JDK 21.0.12.1+1, Android platform 36, Build Tools 35.0.0 and NDK 21.4.7075529. The NDK archive completed and matched official SHA-1 `fc44fea8bb3f5a6789821f40f41dce2d2cd5dc30`.

```powershell
$env:JAVA_HOME='D:/Apps/Android/cloud-epub-spike/tools/jdk-21.0.12.1+1'
$env:ANDROID_HOME='D:/Apps/Android/cloud-epub-spike/sdk'
$env:GRADLE_USER_HOME='D:/Apps/Android/cloud-epub-spike/gradle-home'
$source='D:/Apps/Android/cloud-epub-spike/tools/talkback-229212fdf5842191d0a93fc95d9ca1423b346866'
$init='<absolute path to google-talkback-build.gradle from this evidence directory>'
& D:/Apps/Android/cloud-epub-spike/tools/gradle-8.14.3/bin/gradle.bat -p $source -I $init assemblePhoneDebug --max-workers=2 --console=plain
# Only on the disposable AVD: remove a differently signed old TalkBack test APK.
$adb="$env:ANDROID_HOME/platform-tools/adb.exe"
& $adb uninstall com.android.talkback
& $adb install -t "$source/build/outputs/apk/phone/debug/talkback-229212fdf5842191d0a93fc95d9ca1423b346866-phone-debug.apk"
& $adb shell settings put secure enabled_accessibility_services com.android.talkback/com.google.android.marvin.talkback.TalkBackService
& $adb shell settings put secure accessibility_enabled 1
```

Finish service onboarding and resume the fixture reader before running the calibrated suite. Native suites run with this service disabled. Reset accessibility settings afterward. This is source/debug-build compatibility evidence, not Google Play binary certification, broader device qualification or a supported-version decision. Toolchain/source archives and APKs remain outside git.
