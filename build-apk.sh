#!/usr/bin/env bash
set -euo pipefail
task_root="$(cd "$(dirname "$0")" && pwd)"
cd "$task_root"
task_sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
if [ ! -d "$task_sdk/platforms/android-35" ]; then
  echo "SDK Platform 35 belum ada di $task_sdk. Pasang melalui Android Studio SDK Manager."
  exit 1
fi
if ! command -v java >/dev/null 2>&1; then
  echo "Java belum tersedia. Gunakan JDK 17; lihat README."
  exit 1
fi
mkdir -p .build-tools
if [ ! -x .build-tools/gradle-8.9/bin/gradle ]; then
  task_archive=".build-tools/gradle-8.9-bin.zip"
  curl --fail --location --retry 2 "https://services.gradle.org/distributions/gradle-8.9-bin.zip" -o "$task_archive"
  task_checksum="$(curl --fail --location --retry 2 "https://services.gradle.org/distributions/gradle-8.9-bin.zip.sha256")"
  if [[ ! "$task_checksum" =~ ^[0-9a-fA-F]{64}$ ]]; then
    echo "Checksum Gradle tidak valid."
    exit 1
  fi
  printf '%s  %s\n' "$task_checksum" "$task_archive" | shasum -a 256 -c -
  unzip -q -o "$task_archive" -d .build-tools
fi
ANDROID_HOME="$task_sdk" .build-tools/gradle-8.9/bin/gradle --no-daemon :app:assembleDebug
echo "APK: $task_root/app/build/outputs/apk/debug/app-debug.apk"
