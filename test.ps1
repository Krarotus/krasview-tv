[CmdletBinding()]
param([string]$JavaHome)
$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
if(-not $JavaHome){$JavaHome=$env:JAVA_HOME}
if(-not $JavaHome){$JavaHome=Join-Path (Split-Path $PSScriptRoot -Parent) 'tv-tools/jdk/jdk-17.0.20.1+1'}
$testOut=Join-Path $PSScriptRoot 'out/unit-tests'
New-Item -ItemType Directory -Force $testOut | Out-Null
$deps='libs/providers/jsoup-1.18.3.jar;libs/providers/json-20240303.jar'
& "$JavaHome/bin/javac.exe" -encoding UTF-8 -cp $deps -d $testOut app/src/local/krasview/tv/SeekRamp.java app/src/local/krasview/tv/MovieChoices.java app/src/local/krasview/tv/Media.java app/src/local/krasview/tv/SourceUrls.java app/src/local/krasview/tv/SourceJson.java app/src/local/krasview/tv/LampacParser.java tests/SeekRampTest.java tests/MovieChoicesTest.java tests/LampacParserTest.java
if($LASTEXITCODE -ne 0){throw 'Unit test compilation failed'}
foreach($testClass in @('local.krasview.tv.SeekRampTest','local.krasview.tv.MovieChoicesTest','local.krasview.tv.LampacParserTest')){
    & "$JavaHome/bin/java.exe" -cp "$testOut;$deps" $testClass
    if($LASTEXITCODE -ne 0){throw "Test failed: $testClass"}
}
