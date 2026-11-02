[CmdletBinding()]
param([string]$Serial='emulator-5580',[string]$ToolsRoot)
$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if(-not $ToolsRoot){$ToolsRoot=Join-Path (Split-Path $PWD -Parent) 'tv-tools'}
$jdk=Join-Path $ToolsRoot 'jdk/jdk-17.0.20.1+1'
$bt=Join-Path $ToolsRoot 'build-tools/android-14'
$aj=Join-Path $ToolsRoot 'platform/android-34-ext12/android.jar'
$adb=Join-Path $ToolsRoot 'platform-tools/adb.exe'
$env:JAVA_HOME=$jdk
$env:PATH="$jdk/bin;$env:PATH"
function Run([string]$exe,[string[]]$arguments){& $exe @arguments;if($LASTEXITCODE -ne 0){throw "$exe failed ($LASTEXITCODE)"}}
# Build the real signed APK first; the fixture runner is never included in it.
./build.ps1 -Flavor Mobile -ToolsRoot $ToolsRoot
$compiled=Get-ChildItem out -Recurse -Filter classes.jar | Sort-Object LastWriteTime -Descending | Select-Object -First 1
$qa=Join-Path $PWD 'out/mobile-smoke'
New-Item -ItemType Directory -Force "$qa/classes","$qa/gen" | Out-Null
'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="local.krasview.mobile.tests"><application android:label="Mobile QA"/><instrumentation android:name="local.krasview.tv.MobileSmoke" android:targetPackage="local.krasview.mobile"/></manifest>' | Set-Content "$qa/AndroidManifest.xml"
Run "$bt/aapt2.exe" @('link','-o',"$qa/base.apk",'-I',$aj,'--manifest',"$qa/AndroidManifest.xml",'--min-sdk-version','26','--target-sdk-version','30')
Run "$jdk/bin/javac.exe" @('-source','8','-target','8','-encoding','UTF-8','-classpath',"$aj;$($compiled.FullName)",'-d',"$qa/classes",'tests/android/MobileSmoke.java')
Run "$jdk/bin/jar.exe" @('cf',"$qa/tests.jar",'-C',"$qa/classes",'.')
Run "$bt/d8.bat" @('--min-api','26','--lib',$aj,'--classpath',$compiled.FullName,'--output',$qa,"$qa/tests.jar")
Run "$jdk/bin/jar.exe" @('-u','-M','-f',"$qa/base.apk",'-C',$qa,'classes.dex')
Run "$bt/zipalign.exe" @('-f','4',"$qa/base.apk","$qa/aligned.apk")
$env:KRASVIEW_BUILD_STORE_PASS=if($env:KRASVIEW_KEYSTORE_PASSWORD){$env:KRASVIEW_KEYSTORE_PASSWORD}else{'localtv'}
Run "$bt/apksigner.bat" @('sign','--ks','krasview-tv.keystore','--ks-pass','env:KRASVIEW_BUILD_STORE_PASS','--out',"$qa/test.apk","$qa/aligned.apk")
Run $adb @('-s',$Serial,'install','-r','KrasView-Mobile.apk')
Run $adb @('-s',$Serial,'install','-r',"$qa/test.apk")
$report=& $adb -s $Serial shell am instrument -w local.krasview.mobile.tests/local.krasview.tv.MobileSmoke
$report | Write-Output
if($LASTEXITCODE -ne 0 -or (($report -join "`n") -notmatch 'PASS: portrait home')){throw 'Mobile instrumentation did not pass'}
Run $adb @('-s',$Serial,'pull','/sdcard/Android/data/local.krasview.mobile/files/mobile-qa',"$qa/screenshots")
