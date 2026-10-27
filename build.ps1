[CmdletBinding()]
param(
    [string]$ToolsRoot,
    [string]$JavaHome,
    [string]$AndroidSdkRoot,
    [string]$BuildToolsVersion='34.0.0',
    [string]$Platform='android-34',
    [string]$SigningKey
)
$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
if(-not $ToolsRoot){$ToolsRoot=Join-Path (Split-Path $PSScriptRoot -Parent) 'tv-tools'}
if(-not $JavaHome){$JavaHome=$env:JAVA_HOME}
if(-not $JavaHome){$JavaHome=Join-Path $ToolsRoot 'jdk/jdk-17.0.20.1+1'}
if(-not $AndroidSdkRoot){$AndroidSdkRoot=$env:ANDROID_SDK_ROOT}
if(-not $AndroidSdkRoot){$AndroidSdkRoot=$env:ANDROID_HOME}
$jdk=$JavaHome
if($AndroidSdkRoot){$bt=Join-Path $AndroidSdkRoot "build-tools/$BuildToolsVersion";$aj=Join-Path $AndroidSdkRoot "platforms/$Platform/android.jar"}
else{$bt=Join-Path $ToolsRoot 'build-tools/android-14';$aj=Join-Path $ToolsRoot 'platform/android-34-ext12/android.jar'}
if(-not $SigningKey){$SigningKey=Join-Path $PSScriptRoot 'krasview-tv.keystore'}
foreach($required in @("$jdk/bin/javac.exe","$bt/aapt2.exe",$aj,'libs/media3-lock.json')){if(-not(Test-Path -LiteralPath $required)){throw "Missing build prerequisite: $required. See README.md."}}
$env:KRASVIEW_BUILD_STORE_PASS=if($env:KRASVIEW_KEYSTORE_PASSWORD){$env:KRASVIEW_KEYSTORE_PASSWORD}else{'localtv'}
$env:KRASVIEW_BUILD_KEY_PASS=if($env:KRASVIEW_KEY_PASSWORD){$env:KRASVIEW_KEY_PASSWORD}else{$env:KRASVIEW_BUILD_STORE_PASS}
$env:JAVA_HOME=$jdk
$env:PATH="$jdk/bin;$env:PATH"
function Run([string]$exe,[string[]]$arguments) { & $exe @arguments; if($LASTEXITCODE -ne 0) { throw "$exe failed ($LASTEXITCODE)" } }
$out=Join-Path $PSScriptRoot ('out/'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force "$out/gen","$out/classes" | Out-Null
Run "$bt/aapt2.exe" @('compile','--dir','app/res','-o',"$out/res.zip")
$deps=Get-Content 'libs/media3-lock.json' -Raw | ConvertFrom-Json
$deps+=@(Get-Content 'libs/providers-lock.json' -Raw | ConvertFrom-Json | Where-Object {$_.scope -eq 'runtime'})
$dependencyJars=@();$resourceArgs=@();$packages=@()
foreach($dep in $deps) {
    if(-not(Test-Path -LiteralPath (Join-Path $PSScriptRoot $dep.jar))){throw 'Player dependencies are missing. Run: python fetch_dependencies.py'}
    $dependencyJars+=Join-Path $PSScriptRoot $dep.jar
    if($dep.type -eq 'aar') {
        $folder=Split-Path (Join-Path $PSScriptRoot $dep.jar) -Parent
        [xml]$libraryManifest=Get-Content (Join-Path $folder 'AndroidManifest.xml') -Raw
        $packages+=$libraryManifest.manifest.package
        if(Test-Path (Join-Path $folder 'res')) {
            $zip=Join-Path $out ($dep.artifact+'.zip')
            Run "$bt/aapt2.exe" @('compile','--dir',(Join-Path $folder 'res'),'-o',$zip)
            $resourceArgs+=@('-R',$zip)
        }
    }
}
Run "$bt/aapt2.exe" (@('link','--auto-add-overlay','--extra-packages',(($packages | Select-Object -Unique) -join ':'),'-o',"$out/base.apk",'-I',$aj,'--manifest','app/AndroidManifest.xml','--java',"$out/gen",'-A','app/assets',"$out/res.zip",'--min-sdk-version','26','--target-sdk-version','30')+$resourceArgs)
$sources=Get-ChildItem app/src,"$out/gen" -Recurse -Filter '*.java' | ForEach-Object { '"'+$_.FullName.Replace('\','/')+'"' }
[IO.File]::WriteAllLines("$out/sources.txt",$sources,[Text.UTF8Encoding]::new($false))
Run "$jdk/bin/javac.exe" @('-source','8','-target','8','-encoding','UTF-8','-bootclasspath',"$aj;$bt/core-lambda-stubs.jar",'-classpath',("$aj;$PSScriptRoot/libs/zxing-core-3.5.3.jar;"+($dependencyJars -join ';')),'-d',"$out/classes","@$out/sources.txt")
Run "$jdk/bin/jar.exe" @('cf',"$out/classes.jar",'-C',"$out/classes",'.')
Run "$bt/d8.bat" (@('--release','--min-api','26','--lib',$aj,'--output',$out,"$out/classes.jar",'libs/zxing-core-3.5.3.jar')+$dependencyJars)
foreach($dex in Get-ChildItem $out -Filter 'classes*.dex') { Run "$jdk/bin/jar.exe" @('-u','-M','-f',"$out/base.apk",'-C',$out,$dex.Name) }
Run "$bt/zipalign.exe" @('-f','-p','4',"$out/base.apk","$out/aligned.apk")
if(-not (Test-Path -LiteralPath $SigningKey)) { Run "$jdk/bin/keytool.exe" @('-genkeypair','-keystore',$SigningKey,'-alias','tv','-keyalg','RSA','-keysize','2048','-validity','20000','-storepass:env','KRASVIEW_BUILD_STORE_PASS','-keypass:env','KRASVIEW_BUILD_KEY_PASS','-dname','CN=Local TV Prototype') }
Run "$bt/apksigner.bat" @('sign','--ks',$SigningKey,'--ks-pass','env:KRASVIEW_BUILD_STORE_PASS','--key-pass','env:KRASVIEW_BUILD_KEY_PASS','--out','KrasView-TV.apk',"$out/aligned.apk")
Run "$bt/apksigner.bat" @('verify','KrasView-TV.apk')
Write-Output 'Built KrasView-TV.apk'
