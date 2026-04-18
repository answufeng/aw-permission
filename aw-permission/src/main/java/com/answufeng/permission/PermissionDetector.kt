package com.answufeng.permission

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.fragment.app.FragmentActivity

/**
 * 增强的权限拒绝检测，兼容国产定制 ROM。
 *
 * 标准 AOSP 使用 `shouldShowRequestPermissionRationale` 来区分"拒绝"和"永久拒绝"。
 * 然而，此方法在定制 ROM（MIUI、EMUI、ColorOS、Flyme 等）上存在已知的不一致性。
 *
 * 此检测器提供：
 * - 标准 AOSP 逻辑：`wasRationale=true → isRationale=false` = 永久拒绝
 * - AppOpsManager 回退：当两个 rationale 状态都为 `false`（歧义情况）时，
 *   使用 `AppOpsManager.checkOpNoThrow` 检测 `MODE_IGNORED` 作为永久拒绝的指标
 *
 * ### AppOpsManager 工作原理
 * Android 系统通过 AppOpsService 跟踪每个应用对每个操作（op）的模式：
 * - `MODE_ALLOWED`：允许
 * - `MODE_IGNORED`：忽略（通常对应"不再询问"后的状态）
 * - `MODE_ERRORED`：错误
 *
 * 当用户选择"不再询问"时，系统会将对应的 op 模式设为 `MODE_IGNORED`。
 * 即使 `shouldShowRequestPermissionRationale` 在某些 ROM 上返回不准确，
 * AppOps 的模式通常仍然是可靠的。
 *
 * ### 局限性
 * - `AppOpsManager.permissionToOp()` 对某些权限可能返回 null（没有对应的 op）
 * - 某些 ROM 可能修改了 AppOps 的行为
 * - 此检测是尽力而为的，无法保证 100% 准确
 */
internal object PermissionDetector {

    /**
     * 检测当前设备是否运行已知的国产定制 ROM。
     *
     * 通过 [Build.MANUFACTURER]、[Build.BRAND]、[Build.DISPLAY] 和 [Build.FINGERPRINT] 检测。
     */
    internal fun isProblematicRom(): Boolean {
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
     * 判断权限是否被永久拒绝。
     *
     * 使用两层检测策略：
     * 1. **标准 AOSP**：如果 `wasRationaleBefore=true` 且 `isRationaleAfter=false`，
     *    则权限被永久拒绝。
     * 2. **AppOpsManager 回退**：如果两个 rationale 状态都为 `false`（首次请求或
     *    定制 ROM 上的歧义情况），检查 `AppOpsManager.checkOpNoThrow`。
     *    如果结果为 `MODE_IGNORED`，则认为权限被永久拒绝。
     *
     * @param activity [FragmentActivity]
     * @param permission 要检查的权限
     * @param wasRationaleBefore 请求前 `shouldShowRequestPermissionRationale` 是否返回 `true`
     * @param isRationaleAfter 请求后 `shouldShowRequestPermissionRationale` 是否返回 `true`
     * @return 如果权限被认为被永久拒绝则返回 `true`
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
            if (!PermissionHistory.hasRequested(activity, permission)) {
                return false
            }
            return checkPermanentlyDeniedViaAppOps(activity, permission)
        }
        return false
    }

    @Suppress("TooGenericExceptionCaught", "DEPRECATION")
    private fun checkPermanentlyDeniedViaAppOps(
        context: Context,
        permission: String
    ): Boolean {
        if (!isProblematicRom()) return false
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false
            val op = AppOpsManager.permissionToOp(permission) ?: return false
            val uid = Process.myUid()
            val packageName = context.packageName
            val checkResult = appOps.checkOpNoThrow(op, uid, packageName)
            if (checkResult == AppOpsManager.MODE_IGNORED) return true
            if (checkResult != AppOpsManager.MODE_ALLOWED) {
                val noteResult = checkViaNoteOp(appOps, op, uid, packageName)
                if (noteResult == AppOpsManager.MODE_IGNORED) return true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private val noteOpMethod: java.lang.reflect.Method? by lazy {
        try {
            AppOpsManager::class.java.getMethod(
                "noteOpNoThrow",
                String::class.java,
                Int::class.javaPrimitiveType,
                String::class.java
            )
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun checkViaNoteOp(
        appOps: AppOpsManager,
        op: String,
        uid: Int,
        packageName: String
    ): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                appOps.noteOpNoThrow(op, uid, packageName)
            } else {
                val method = noteOpMethod ?: return AppOpsManager.MODE_ALLOWED
                method.invoke(appOps, op, uid, packageName) as? Int
                    ?: AppOpsManager.MODE_ALLOWED
            }
        } catch (_: Exception) {
            AppOpsManager.MODE_ALLOWED
        }
    }

    private val knownProblematicRoms = setOf(
        "miui", "emui", "harmony", "coloros", "funtouch",
        "flyme", "oneui", "smartisan", "nubia", "rog",
        "vivo", "oppo", "huawei", "xiaomi", "samsung",
        "meizu", "lenovo", "zte", "asus"
    )
}
