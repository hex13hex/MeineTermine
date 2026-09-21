package com.example.meinetermine

import android.app.AppOpsManager
import android.content.Context
import android.os.Build

object AutoStartChecker {

    fun isAutoStartAllowed(context: Context): Boolean {

        if (
            !Build.MANUFACTURER.equals(
                "Xiaomi",
                ignoreCase = true
            )
        ) {
            return true
        }

        return try {
            val appOps =
                context.getSystemService(Context.APP_OPS_SERVICE)
                        as AppOpsManager

            val method = appOps.javaClass.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )

            val result = method.invoke(
                appOps,
                10008,
                android.os.Process.myUid(),
                context.packageName
            ) as Int

            result == AppOpsManager.MODE_ALLOWED

        } catch (e: Exception) {
            true
        }
    }
}