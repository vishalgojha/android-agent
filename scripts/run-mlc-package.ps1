param(
  [Parameter(Mandatory = $true)]
  [string]$PythonExecutable,
  [Parameter(Mandatory = $true)]
  [string]$MlcLlmSourceDir,
  [Parameter(Mandatory = $true)]
  [string]$PackageConfigPath,
  [Parameter(Mandatory = $true)]
  [string]$OutputDir,
  [string]$PythonPath,
  [string]$MlcLibraryPath,
  [string]$AdditionalPath,
  [string]$JavaHome,
  [string]$AndroidNdk,
  [string]$TvmNdkCc,
  [string]$MlcJitPolicy
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$env:MLC_LLM_SOURCE_DIR = $MlcLlmSourceDir

if (-not [string]::IsNullOrWhiteSpace($PythonPath)) {
  $env:PYTHONPATH = $PythonPath
}

if (-not [string]::IsNullOrWhiteSpace($MlcLibraryPath)) {
  $env:MLC_LIBRARY_PATH = $MlcLibraryPath
}

if (-not [string]::IsNullOrWhiteSpace($JavaHome)) {
  $env:JAVA_HOME = $JavaHome
}

if (-not [string]::IsNullOrWhiteSpace($AndroidNdk)) {
  $env:ANDROID_NDK = $AndroidNdk
}

if (-not [string]::IsNullOrWhiteSpace($TvmNdkCc)) {
  $env:TVM_NDK_CC = $TvmNdkCc
}

if (-not [string]::IsNullOrWhiteSpace($MlcJitPolicy)) {
  $env:MLC_JIT_POLICY = $MlcJitPolicy
}

if (-not [string]::IsNullOrWhiteSpace($AdditionalPath)) {
  $env:PATH = "$AdditionalPath$([System.IO.Path]::PathSeparator)$env:PATH"
}

& $PythonExecutable -m mlc_llm package --package-config $PackageConfigPath --output $OutputDir
if ($LASTEXITCODE -ne 0) {
  exit $LASTEXITCODE
}
