#!/usr/bin/env bash
set -euo pipefail

# One-command local Android debug build for this environment.
# Usage:
#   bash scripts/build_debug_local.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLCHAINS_DIR="${HOME}/.hermes/toolchains"
JDK_DIR="${TOOLCHAINS_DIR}/jdk-17.0.12+7"
GRADLE_DIR="${TOOLCHAINS_DIR}/gradle-8.7"
ANDROID_SDK_ROOT="${TOOLCHAINS_DIR}/android-sdk"
CMDLINE_TOOLS_DIR="${ANDROID_SDK_ROOT}/cmdline-tools/latest"

JDK_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.12%2B7/OpenJDK17U-jdk_x64_linux_hotspot_17.0.12_7.tar.gz"
GRADLE_URL="https://services.gradle.org/distributions/gradle-8.7-bin.zip"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

mkdir -p "${TOOLCHAINS_DIR}" "${ANDROID_SDK_ROOT}/cmdline-tools"

echo "[1/6] Checking JDK..."
if [[ ! -x "${JDK_DIR}/bin/java" ]]; then
  echo "  - Downloading Temurin JDK 17"
  curl -L -o "${TOOLCHAINS_DIR}/temurin17.tar.gz" "${JDK_URL}"
  tar -xzf "${TOOLCHAINS_DIR}/temurin17.tar.gz" -C "${TOOLCHAINS_DIR}"
fi

export JAVA_HOME="${JDK_DIR}"
export PATH="${JAVA_HOME}/bin:${PATH}"

echo "[2/6] Checking Gradle toolchain..."
if [[ ! -x "${GRADLE_DIR}/bin/gradle" ]]; then
  echo "  - Downloading Gradle 8.7"
  curl -L -o "${TOOLCHAINS_DIR}/gradle-8.7-bin.zip" "${GRADLE_URL}"
  (
    cd "${TOOLCHAINS_DIR}"
    "${JAVA_HOME}/bin/jar" xf "gradle-8.7-bin.zip"
  )
  chmod +x "${GRADLE_DIR}/bin/gradle"
fi

echo "[3/6] Checking Android cmdline-tools..."
if [[ ! -x "${CMDLINE_TOOLS_DIR}/bin/sdkmanager" ]]; then
  echo "  - Downloading Android cmdline-tools"
  curl -L -o "${TOOLCHAINS_DIR}/cmdline-tools.zip" "${CMDLINE_TOOLS_URL}"
  (
    cd "${ANDROID_SDK_ROOT}/cmdline-tools"
    "${JAVA_HOME}/bin/jar" xf "${TOOLCHAINS_DIR}/cmdline-tools.zip"
    if [[ -d "cmdline-tools" && ! -d "latest" ]]; then
      mv "cmdline-tools" "latest"
    fi
  )
  chmod +x "${CMDLINE_TOOLS_DIR}/bin/"*
fi

export ANDROID_HOME="${ANDROID_SDK_ROOT}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT}"

SDKMANAGER="${CMDLINE_TOOLS_DIR}/bin/sdkmanager"

echo "[4/6] Installing required Android SDK packages (if missing)..."
# sdkmanager may finish quickly without reading stdin; with pipefail enabled that can yield code 141 from `yes`.
set +o pipefail
yes | "${SDKMANAGER}" --sdk_root="${ANDROID_SDK_ROOT}" \
  "platform-tools" \
  "platforms;android-34" \
  "build-tools;34.0.0" >/dev/null
set -o pipefail

echo "[5/6] Preparing project local.properties + wrapper..."
printf 'sdk.dir=%s\n' "${ANDROID_SDK_ROOT}" > "${ROOT_DIR}/local.properties"

if [[ ! -f "${ROOT_DIR}/gradlew" ]]; then
  "${GRADLE_DIR}/bin/gradle" -p "${ROOT_DIR}" wrapper --gradle-version 8.7
fi
chmod +x "${ROOT_DIR}/gradlew"

echo "[6/6] Building :app:assembleDebug ..."
"${ROOT_DIR}/gradlew" -p "${ROOT_DIR}" :app:assembleDebug --no-daemon

APK_PATH="${ROOT_DIR}/app/build/outputs/apk/debug/app-debug.apk"
if [[ -f "${APK_PATH}" ]]; then
  echo ""
  echo "✅ Build OK"
  echo "APK: ${APK_PATH}"
else
  echo ""
  echo "❌ Build finished but APK not found at expected path"
  exit 1
fi
