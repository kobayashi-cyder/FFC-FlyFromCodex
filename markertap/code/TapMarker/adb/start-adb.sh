#!/usr/bin/env bash
set -euo pipefail
script_dir=$(cd -- "$(dirname -- "$0")" && pwd)
adb_bin=${TAPMARKER_ADB:-adb}
if [[ -x "$script_dir/adb" && -z ${TAPMARKER_ADB:-} ]]; then adb_bin="$script_dir/adb"; fi
serial="";stop=false
while (($#)); do
  case "$1" in
    --serial) if (($#<2)); then echo "Missing device ID" >&2; exit 1; fi; serial=$2; shift 2 ;;
    --stop) stop=true; shift ;;
    *) echo "Usage: bash start-adb.sh [--serial DEVICE_ID] [--stop]" >&2; exit 1 ;;
  esac
done
if [[ -z "$serial" ]]; then
  device_output=$("$adb_bin" devices)
  devices=()
  while IFS= read -r line; do
    if [[ "$line" =~ ^([^[:space:]]+)[[:space:]]+device([[:space:]]|$) ]]; then devices+=("${BASH_REMATCH[1]}"); fi
  done <<< "$device_output"
  if ((${#devices[@]}!=1)); then
    echo "Connect one authorized device, or specify --serial DEVICE_ID. Authorize USB debugging on the phone." >&2
    exit 1
  fi
  serial=${devices[0]}
fi
state=$("$adb_bin" -s "$serial" get-state)
if [[ ${state//$'\r'/} != device ]]; then echo "Device not online or authorized" >&2; exit 1; fi
paths=$("$adb_bin" -s "$serial" shell pm path jp.tapmarker.lite)
apk_path=""
while IFS= read -r line; do
  line=${line//$'\r'/}
  if [[ "$line" =~ ^package:(/data/app/[A-Za-z0-9_./+=@~-]+/base\.apk)$ ]]; then apk_path=${BASH_REMATCH[1]}; break; fi
done <<< "$paths"
if [[ -z "$apk_path" ]]; then echo "Install TapMarker v1.52 or later in the primary Android user before starting." >&2; exit 1; fi
remote_command="CLASSPATH='$apk_path' app_process /system/bin jp.tapmarker.VirtualBootstrap"
if $stop; then remote_command+=" --stop"
else
  echo "Keep this terminal and the ADB connection open."
  echo "After TAPMARKER_ADB_CONNECTED, select the ADB virtual-display mode in the APK."
fi
"$adb_bin" -s "$serial" shell "$remote_command"
