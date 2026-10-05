param([Parameter(Mandatory=$true)][string]$JdkPath,[Parameter(Mandatory=$true)][string]$SdkPath,[string]$GradlePath='')
$ErrorActionPreference='Stop'
$taskJdk=(Resolve-Path -LiteralPath $JdkPath).Path
$taskSdk=(Resolve-Path -LiteralPath $SdkPath).Path
if (!(Test-Path -LiteralPath (Join-Path $taskJdk 'bin\javac.exe'))) { throw 'JDK is missing javac.exe' }
if (!(Test-Path -LiteralPath (Join-Path $taskSdk 'platforms\android-36\android.jar'))) { throw 'Android platform 36 is incomplete' }
if (!(Test-Path -LiteralPath (Join-Path $taskSdk 'build-tools\36.0.0\aapt2.exe'))) { throw 'Build Tools 36.0.0 is missing' }
$taskOldJdk=$env:JAVA_HOME
try {
    $env:JAVA_HOME=$taskJdk
    [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'local.properties'),('sdk.dir='+$taskSdk.Replace('\','/').Replace(':','\:')))
    $taskGradle=if ($GradlePath) { (Resolve-Path -LiteralPath $GradlePath).Path } else { Join-Path $PSScriptRoot 'gradlew.bat' }
    & $taskGradle -p $PSScriptRoot testDebugUnitTest assembleDebug lintDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Build failed ($LASTEXITCODE)" }
} finally { $env:JAVA_HOME=$taskOldJdk }
