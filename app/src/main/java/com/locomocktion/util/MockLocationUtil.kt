package com.locomocktion.util

import android.app.AppOpsManager
import android.content.Context
import android.os.Process

fun isMockLocationEnabled(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    return appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_MOCK_LOCATION,
        Process.myUid(),
        context.packageName,
    ) == AppOpsManager.MODE_ALLOWED
}
