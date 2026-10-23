#!/usr/bin/env bash
# La sonde est appelée en une seule ligne par android-emulator-runner.
set -u

# L'AVD peut conserver un paquet signé avec une clé debug éphémère.
adb uninstall com.missa.tv360.test >/dev/null 2>&1 || true
adb uninstall com.missa.tv360 >/dev/null 2>&1 || true

bash .github/scripts/gradle_ci.sh :app:assembleDebug :app:assembleDebugAndroidTest
status=$?
if [ "$status" -ne 0 ]; then
  echo "::error title=P3 Gradle probe::assemblage des APK en échec avec le code $status"
  exit "$status"
fi

for apk in \
  app/build/outputs/apk/debug/app-debug.apk \
  app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk; do
  if install_output=$(adb install -r -t "$apk" 2>&1); then
    install_status=0
  else
    install_status=$?
  fi
  printf '%s\n' "$install_output"
  if [ "$install_status" -ne 0 ]; then
    safe_output=$(printf '%s' "$install_output" | tr '\r\n' '  ' | sed 's/%/%25/g')
    echo "::error title=APK install::$safe_output"
    exit "$install_status"
  fi
done

bash .github/scripts/gradle_ci.sh :app:connectedDebugAndroidTest
status=$?
if [ "$status" -ne 0 ]; then
  echo "::error title=P3 Gradle probe::connectedDebugAndroidTest a échoué avec le code $status"
fi
exit "$status"
