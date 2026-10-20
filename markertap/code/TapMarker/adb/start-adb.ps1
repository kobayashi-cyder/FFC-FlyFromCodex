param([string]$Serial = "", [string]$AdbPath = "", [switch]$Stop)
$ErrorActionPreference = "Stop"
if (-not $AdbPath) {
    $localAdb = Join-Path $PSScriptRoot "adb.exe"
    if (Test-Path $localAdb) { $AdbPath = $localAdb }
    else { $AdbPath = (Get-Command adb -ErrorAction Stop).Source }
}
function Invoke-Adb([string[]]$CommandArguments) {
    $result = & $AdbPath @CommandArguments
    if ($LASTEXITCODE -ne 0) { throw "ADB failed. Check the cable and USB debugging authorization." }
    return $result
}
if (-not $Serial) {
    $deviceOutput = Invoke-Adb -CommandArguments @("devices")
    $devices = @($deviceOutput | ForEach-Object { if ($_ -match '^([^\s]+)\s+device(?:\s|$)') { $Matches[1] } })
    if ($devices.Count -ne 1) { throw "Connect one authorized device, or specify -Serial DEVICE_ID. Authorize USB debugging on the phone." }
    $Serial = $devices[0]
}
$state = Invoke-Adb -CommandArguments @("-s", $Serial, "get-state")
if (($state -join "").Trim() -ne "device") { throw "The selected device is not authorized or online." }
$paths = Invoke-Adb -CommandArguments @("-s", $Serial, "shell", "pm", "path", "jp.tapmarker.lite")
$apkPath = ""
foreach ($line in $paths) {
    if ($line.Trim() -match '^package:(/data/app/[A-Za-z0-9_./+=@~-]+/base\.apk)$') { $apkPath = $Matches[1]; break }
}
if (-not $apkPath) { throw "Install TapMarker v1.52 or later in the primary Android user before starting." }
$remoteCommand = "CLASSPATH='$apkPath' app_process /system/bin jp.tapmarker.VirtualBootstrap"
if ($Stop) { $remoteCommand += " --stop" }
else {
    Write-Host "Keep this terminal and the ADB connection open."
    Write-Host "After TAPMARKER_ADB_CONNECTED, select the ADB virtual-display mode in the APK."
}
& $AdbPath "-s" $Serial "shell" $remoteCommand
if ($LASTEXITCODE -ne 0) { throw "TapMarker backend ended with an error. Copy the TAPMARKER_ADB_ERROR message." }
