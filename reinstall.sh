#!/bin/sh
rm -f app-debug.apk
gh run download $(gh run list --workflow Build --status success -L1 --json databaseId --jq '.[].databaseId') --name app-debug

adb uninstall com.locomocktion
adb install app-debug.apk

# Permissions
adb shell pm grant com.locomocktion android.permission.POST_NOTIFICATIONS \
    android.permission.FOREGROUND_SERVICE_LOCATION \
    android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS \
    android.permission.ACCESS_COARSE_LOCATION \
    android.permission.ACCESS_FINE_LOCATION

adb shell appops set com.locomocktion android:mock_location allow

# adb shell dumpsys package com.locomocktion | grep permission
