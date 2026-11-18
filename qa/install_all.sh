#!/bin/bash
# Installe l'APK debug sur tous les appareils connectés
A=~/Android/sdk/platform-tools/adb; APK="$(dirname "$0")/../android/app/build/outputs/apk/debug/app-debug.apk"
for s in $($A devices | awk 'NR>1 && $2=="device"{print $1}'); do echo -n "$s: "; $A -s $s install -r "$APK" | tail -1; done
