[CmdletBinding()]
param([string]$JavaHome)
$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
if(-not $JavaHome){$JavaHome=$env:JAVA_HOME}
if(-not $JavaHome){$JavaHome=Join-Path (Split-Path $PSScriptRoot -Parent) 'tv-tools/jdk/jdk-17.0.20.1+1'}
$testOut=Join-Path $PSScriptRoot 'out/unit-tests'
New-Item -ItemType Directory -Force $testOut | Out-Null
& "$JavaHome/bin/javac.exe" -encoding UTF-8 -d $testOut app/src/local/krasview/tv/SeekRamp.java app/src/local/krasview/tv/MovieChoices.java app/src/local/krasview/tv/SubtitleFiles.java app/src/local/krasview/tv/VideoPreview.java app/src/local/krasview/tv/RecommendationRanker.java tests/SeekRampTest.java tests/MovieChoicesTest.java tests/SubtitleFilesTest.java tests/VideoPreviewTest.java tests/RecommendationRankerTest.java
if($LASTEXITCODE -ne 0){throw 'Unit test compilation failed'}
foreach($testClass in @('local.krasview.tv.SeekRampTest','local.krasview.tv.MovieChoicesTest','local.krasview.tv.SubtitleFilesTest','local.krasview.tv.VideoPreviewTest','local.krasview.tv.RecommendationRankerTest')){
    & "$JavaHome/bin/java.exe" -cp $testOut $testClass
    if($LASTEXITCODE -ne 0){throw "Test failed: $testClass"}
}
