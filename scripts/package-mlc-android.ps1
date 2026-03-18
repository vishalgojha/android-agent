param(
  [string]$MlcLlmSourceDir = $env:MLC_LLM_SOURCE_DIR,
  [string]$PythonExecutable = $env:MLC_PYTHON,
  [string]$JavaHome = $env:JAVA_HOME,
  [string]$AndroidNdk = $env:ANDROID_NDK,
  [string]$TvmNdkCc = $env:TVM_NDK_CC,
  [string]$MlcJitPolicy = $env:MLC_JIT_POLICY
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Resolve-FirstExistingPath {
  param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Candidates
  )

  foreach ($candidate in $Candidates) {
    if (-not [string]::IsNullOrWhiteSpace($candidate) -and (Test-Path $candidate)) {
      return (Resolve-Path $candidate).Path
    }
  }

  return $null
}

function Resolve-LatestChildDirectory {
  param([string]$ParentPath)

  if (-not (Test-Path $ParentPath)) {
    return $null
  }

  $child = Get-ChildItem $ParentPath -Directory | Sort-Object Name -Descending | Select-Object -First 1
  if ($null -eq $child) {
    return $null
  }

  return $child.FullName
}

function Resolve-WingetToolDirectory {
  param(
    [string]$PackagePrefix,
    [string]$ToolName
  )

  $packagesRoot = Join-Path $env:LOCALAPPDATA "Microsoft\WinGet\Packages"
  if (-not (Test-Path $packagesRoot)) {
    return $null
  }

  $packageDir =
    Get-ChildItem $packagesRoot -Directory -Filter "${PackagePrefix}_*" |
    Sort-Object Name -Descending |
    Select-Object -First 1

  if ($null -eq $packageDir) {
    return $null
  }

  $tool =
    Get-ChildItem $packageDir.FullName -Recurse -File -Filter $ToolName -ErrorAction SilentlyContinue |
    Select-Object -First 1

  if ($null -eq $tool) {
    return $null
  }

  return $tool.DirectoryName
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$gradle = Join-Path $repoRoot "gradlew.bat"
$workspaceRoot = Split-Path $repoRoot -Parent

if (-not [System.IO.File]::Exists($gradle)) {
  throw "Could not find gradlew.bat at $gradle"
}

if ([string]::IsNullOrWhiteSpace($MlcLlmSourceDir)) {
  $MlcLlmSourceDir = Resolve-FirstExistingPath (Join-Path $workspaceRoot "mlc-llm")
}

if ([string]::IsNullOrWhiteSpace($MlcLlmSourceDir)) {
  throw "Set MLC_LLM_SOURCE_DIR or pass -MlcLlmSourceDir with the absolute path to the mlc-llm checkout."
}

$venvDir = Join-Path $repoRoot ".venv-mlc"
$venvPython = Join-Path $venvDir "Scripts\python.exe"
if ([string]::IsNullOrWhiteSpace($PythonExecutable)) {
  $PythonExecutable = Resolve-FirstExistingPath $venvPython
}
if ([string]::IsNullOrWhiteSpace($PythonExecutable)) {
  $PythonExecutable = "python"
}

$mlcPythonPath = Resolve-FirstExistingPath (Join-Path $MlcLlmSourceDir "python")
$mlcLibraryPath = Resolve-FirstExistingPath (Join-Path $venvDir "Lib\site-packages\mlc_llm")
$tvmFfiLibDir = Resolve-FirstExistingPath (Join-Path $venvDir "Lib\site-packages\tvm_ffi\lib")
$nativeDllDir = Join-Path $repoRoot ".native-dlls"
$zstdShimPath = Join-Path $nativeDllDir "zstd.dll"
if (-not (Test-Path $zstdShimPath)) {
  $zstdSource =
    Get-ChildItem (Join-Path $env:LOCALAPPDATA "Microsoft\WinGet\Packages") -Directory -Filter "Meta.Zstandard_*" -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending |
    Select-Object -First 1 |
    ForEach-Object {
      Get-ChildItem $_.FullName -Recurse -File -Filter "libzstd.dll" -ErrorAction SilentlyContinue |
      Select-Object -First 1
    }

  if ($null -ne $zstdSource) {
    New-Item -ItemType Directory -Force -Path $nativeDllDir | Out-Null
    Copy-Item $zstdSource.FullName $zstdShimPath -Force
  }
}

if ([string]::IsNullOrWhiteSpace($JavaHome)) {
  $JavaHome =
    Resolve-FirstExistingPath `
      "C:\AndroidJbr" `
      "C:\Program Files\Android\Android Studio\jbr"
}

if ([string]::IsNullOrWhiteSpace($AndroidNdk)) {
  $AndroidNdk = Resolve-LatestChildDirectory (Join-Path $env:LOCALAPPDATA "Android\Sdk\ndk")
}

if ([string]::IsNullOrWhiteSpace($TvmNdkCc) -and -not [string]::IsNullOrWhiteSpace($AndroidNdk)) {
  $TvmNdkCc =
    Resolve-FirstExistingPath (
      Join-Path $AndroidNdk "toolchains\llvm\prebuilt\windows-x86_64\bin\aarch64-linux-android24-clang"
    )
}

$pathEntries = @()
foreach ($candidate in @(
    (Resolve-FirstExistingPath $nativeDllDir),
    $tvmFfiLibDir,
    (Resolve-FirstExistingPath "C:\Program Files\LLVM\bin"),
    (Resolve-FirstExistingPath "C:\Program Files\CMake\bin"),
    (Resolve-WingetToolDirectory -PackagePrefix "Ninja-build.Ninja" -ToolName "ninja.exe")
  )) {
  if (-not [string]::IsNullOrWhiteSpace($candidate)) {
    $pathEntries += $candidate
  }
}

$env:JAVA_HOME = $JavaHome

$existingMlc4jBuildFile = Join-Path $repoRoot "dist\lib\mlc4j\build.gradle"
if (Test-Path $existingMlc4jBuildFile) {
  $existingMlc4jBuild = Get-Content $existingMlc4jBuildFile -Raw
  $normalizedMlc4jBuild =
    (
      $existingMlc4jBuild -replace `
        "(?m)^\s*id 'org\.jetbrains\.kotlin\.android'\s*\r?\n", `
        ""
    ) -replace `
      "(?ms)^\s*kotlinOptions\s*\{.*?^\s*\}\s*\r?\n", `
      "" -replace `
      "id 'org\.jetbrains\.kotlin\.plugin\.serialization'( version '[^']+')?", `
      "id 'org.jetbrains.kotlin.plugin.serialization'"

  if ($normalizedMlc4jBuild -notmatch "buildToolsVersion '36\.1\.0'") {
    $normalizedMlc4jBuild =
      $normalizedMlc4jBuild -replace `
        "compileSdk \d+", `
        "`$0`r`n    buildToolsVersion '36.1.0'"
  }

  if ($normalizedMlc4jBuild -ne $existingMlc4jBuild) {
    Set-Content -Path $existingMlc4jBuildFile -Value $normalizedMlc4jBuild
  }
}

$gradleArgs = @(
  "prepareMlcAndroid",
  "-Pandroidassistant.mlc.sourceDir=$MlcLlmSourceDir",
  "-Pandroidassistant.mlc.python=$PythonExecutable"
)

if (-not [string]::IsNullOrWhiteSpace($mlcPythonPath)) {
  $gradleArgs += "-Pandroidassistant.mlc.pythonPath=$mlcPythonPath"
}

if (-not [string]::IsNullOrWhiteSpace($mlcLibraryPath)) {
  $gradleArgs += "-Pandroidassistant.mlc.libraryPath=$mlcLibraryPath"
}

if ($pathEntries.Count -gt 0) {
  $gradleArgs += "-Pandroidassistant.mlc.path=$($pathEntries -join [System.IO.Path]::PathSeparator)"
}

if (-not [string]::IsNullOrWhiteSpace($JavaHome)) {
  $gradleArgs += "-Pandroidassistant.mlc.javaHome=$JavaHome"
}

if (-not [string]::IsNullOrWhiteSpace($AndroidNdk)) {
  $gradleArgs += "-Pandroidassistant.mlc.androidNdk=$AndroidNdk"
}

if (-not [string]::IsNullOrWhiteSpace($TvmNdkCc)) {
  $gradleArgs += "-Pandroidassistant.mlc.tvmNdkCc=$TvmNdkCc"
}

if (-not [string]::IsNullOrWhiteSpace($MlcJitPolicy)) {
  $gradleArgs += "-Pandroidassistant.mlc.jitPolicy=$MlcJitPolicy"
}

Write-Host "Preparing Android MLC runtime with:"
Write-Host "  repoRoot        = $repoRoot"
Write-Host "  mlcSourceDir    = $MlcLlmSourceDir"
Write-Host "  python          = $PythonExecutable"
Write-Host "  pythonPath      = $mlcPythonPath"
Write-Host "  mlcLibraryPath  = $mlcLibraryPath"
Write-Host "  javaHome        = $JavaHome"
Write-Host "  androidNdk      = $AndroidNdk"
Write-Host "  tvmNdkCc        = $TvmNdkCc"
Write-Host "  extraPath       = $($pathEntries -join [System.IO.Path]::PathSeparator)"

Push-Location $repoRoot
try {
  & $gradle @gradleArgs
} finally {
  Pop-Location
}

