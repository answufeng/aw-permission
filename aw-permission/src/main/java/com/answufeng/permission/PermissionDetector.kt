package com.answufeng.permission

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.fragment.app.FragmentActivity

/**
 * Enhanced permission denial detection with custom ROM compatibility.
 *
 * Standard AOSP uses `shouldShowRequestPermissionRationale` to distinguish
 * between "denied" and "permanently denied" permissions. However, this method
 * has known inconsistencies on custom ROMs (MIUI, EMUI, ColorOS, Flyme, etc.).
 *
 * This detector provides:
 * - Standard AOSP logic: `wasRationale=true → isRationale=false` = permanently denied
 * - AppOpsManager fallback: when both rationale states are `false` (ambiguous case),
 *   uses `AppOpsManager.checkOpNoThrow` to detect `MODE_IGNORED` as an indicator
 *   of permanent denial
 */
internal object PermissionDetector {

    private val knownProblematicRoms = setOf(
        "miui", "emui", "harmony", "coloros", "funtouch",
        "flyme", "oneui", "smartisan", "nubia", "rog",
        "vivo", "oppo", "huawei", "xiaomi", "samsung",
        "meizu", "lenovo", "zte", "asus"
    )

    /**
     * Checks whether the current device runs a known problematic custom ROM.
     *
     * Detects via [Build.MANUFACTURER], [Build.BRAND], [Build.DISPLAY], and [Build.FINGERPRINT].
     */
    fun isProblematicRom(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val display = Build.DISPLAY ?: ""
        val fingerprint = Build.FINGERPRINT ?: ""
        return knownProblematicRoms.any { rom ->
            manufacturer.contains(rom) ||
                brand.contains(rom) ||
                display.contains(rom, ignoreCase = true) ||
                fingerprint.contains(rom, ignoreCase = true)
        }
    }

    /**
     * Determines whether a permission was permanently denied.
     *
     * Uses a two-tier detection strategy:
     * 1. **Standard AOSP**: If `wasRationaleBefore=true` and `isRationaleAfter=false`,
     *    the permission is permanently denied.
     * 2. **AppOpsManager fallback**: If both rationale states are `false` (ambiguous case
     *    on first request or custom ROMs), checks `AppOpsManager.checkOpNoThrow`.
     *    If the result is `MODE_IGNORED`, the permission is considered permanently denied.
     *
     * @param activity The [FragmentActivity] for context
     * @param permission The permission to check
     * @param wasRationaleBefore Whether `shouldShowRequestPermissionRationale` returned `true` before the request
     * @param isRationaleAfter Whether `shouldShowRequestPermissionRationale` returns `true` after the request
     * @return `true` if the permission is considered permanently denied
     */
    fun isPermanentlyDenied(
        activity: FragmentActivity,
        permission: String,
        wasRationaleBefore: Boolean,
        isRationaleAfter: Boolean
    ): Boolean {
        if (wasRationaleBefore && !isRationaleAfter) return true
        if (isRationaleAfter) return false
        if (!wasRationaleBefore && !isRationaleAfter) {
            return checkPermanentlyDeniedViaAppOps(activity, permission)
        }
        return false
    }

    @Suppress("TooGenericExceptionCaught", "DEPRECATION")
    private fun checkPermanentlyDeniedViaAppOps(
        context: Context,
        permission: String
    ): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false
            val op = AppOpsManager.permissionToOp(permission) ?: return false
            val result = appOps.checkOpNoThrow(
                op,
                Process.myUid(),
                context.packageName
            )
            result == AppOpsManager.MODE_IGNORED
        } catch (_: Exception) {
            false
        }
    }
}
