package com.answufeng.permission

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * 国产 ROM 特殊权限引导工具。
 *
 * 国产 ROM 有很多非标准权限需要引导用户手动开启，这些权限无法通过标准
 * 运行时权限请求获得，必须跳转到系统设置页面由用户手动操作。
 *
 * 与 [AwPermission.openAppSettings] 不同：本类面向「自启动、悬浮窗、电池白名单」等厂商页面；
 * 运行时危险权限请使用 [AwPermission]。
 *
 * ### 支持的特殊权限
 * - [SpecialPermission.AUTO_START]：自启动权限
 * - [SpecialPermission.NOTIFICATION_LISTENER]：通知栏权限
 * - [SpecialPermission.FLOAT_WINDOW]：悬浮窗权限
 * - [SpecialPermission.BACKGROUND_POPUP]：后台弹出界面权限
 * - [SpecialPermission.BATTERY_SAVING]：省电策略白名单
 * - [SpecialPermission.IGNORE_BATTERY_OPTIMIZATION]：电池优化白名单
 *
 * ### 示例
 * ```kotlin
 * val success = SpecialPermission.openSettings(context, SpecialPermission.AUTO_START)
 * if (success) {
 *     // 成功打开设置页，等待用户返回
 * } else {
 *     // 无法打开设置页
 * }
 * ```
 */
public object SpecialPermission {

    /**
     * 特殊权限类型。
     */
    public enum class PermissionType {
        /** 自启动权限（小米、华为、OPPO、vivo 等）。 */
        AUTO_START,
        /** 通知栏监听权限。 */
        NOTIFICATION_LISTENER,
        /** 悬浮窗权限。 */
        FLOAT_WINDOW,
        /** 后台弹出界面权限（小米、OPPO、vivo 等）。 */
        BACKGROUND_POPUP,
        /** 省电策略白名单（华为、小米等）。 */
        BATTERY_SAVING,
        /** 电池优化白名单（标准 Android）。 */
        IGNORE_BATTERY_OPTIMIZATION,
        /** 系统设置修改权限（标准 Android，API 23+）。 */
        WRITE_SETTINGS,
        /** 安装未知应用权限（标准 Android，API 26+）。 */
        REQUEST_INSTALL_PACKAGES
    }

    /**
     * 检查特殊权限是否已授权。
     *
     * 对于可以程序化检查的权限类型，返回当前授权状态。
     * 对于无法程序化检查的权限类型（如 [PermissionType.AUTO_START]、
     * [PermissionType.BACKGROUND_POPUP]、[PermissionType.BATTERY_SAVING]），
     * 始终返回 `false`，需要用户手动确认。
     *
     * @param context 任意 [Context]
     * @param permissionType 特殊权限类型
     * @return 已授权返回 `true`，未授权或无法检查返回 `false`
     */
    public fun isGranted(context: Context, permissionType: PermissionType): Boolean {
        return when (permissionType) {
            PermissionType.FLOAT_WINDOW -> checkFloatWindowPermission(context)
            PermissionType.NOTIFICATION_LISTENER -> checkNotificationListenerPermission(context)
            PermissionType.IGNORE_BATTERY_OPTIMIZATION -> checkIgnoreBatteryOptimization(context)
            PermissionType.WRITE_SETTINGS -> checkWriteSettingsPermission(context)
            PermissionType.REQUEST_INSTALL_PACKAGES -> checkInstallPackagesPermission(context)
            PermissionType.AUTO_START -> false
            PermissionType.BACKGROUND_POPUP -> false
            PermissionType.BATTERY_SAVING -> false
        }
    }

    @Suppress("DEPRECATION")
    private fun checkFloatWindowPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            try {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
                    ?: return false
                val method = android.app.AppOpsManager::class.java.getMethod(
                    "checkOpNoThrow",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    String::class.java
                )
                val op = 24 // OP_SYSTEM_ALERT_WINDOW
                val result = method.invoke(appOps, op, android.os.Process.myUid(), context.packageName) as? Int
                    ?: android.app.AppOpsManager.MODE_IGNORED
                result == android.app.AppOpsManager.MODE_ALLOWED
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun checkNotificationListenerPermission(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        val packageName = context.packageName
        return flat.split(':').any { component ->
            val trimmed = component.trim()
            if (trimmed.isEmpty()) return@any false
            val pkg = trimmed.substringBefore('/')
            pkg == packageName
        }
    }

    private fun checkIgnoreBatteryOptimization(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else {
            false
        }
    }

    private fun checkWriteSettingsPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else {
            true
        }
    }

    private fun checkInstallPackagesPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * 打开特殊权限的设置页面。
     *
     * 根据当前设备的 ROM 类型自动选择合适的设置页 Intent。
     * 支持国产 ROM 多重回退。
     *
     * @param context 任意 [Context]
     * @param permissionType 特殊权限类型
     * @return 设置页成功打开返回 `true`，无法打开返回 `false`
     */
    public fun openSettings(context: Context, permissionType: PermissionType): Boolean {
        val intents = buildIntents(context, permissionType)
        for (intent in intents) {
            val launch = Intent(intent).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            try {
                if (launch.resolveActivity(context.packageManager) != null) {
                    context.startActivity(launch)
                    return true
                }
            } catch (_: Exception) {
                continue
            }
        }
        return false
    }

    private fun buildIntents(context: Context, permissionType: PermissionType): List<Intent> = buildList {
        when (permissionType) {
            PermissionType.AUTO_START -> addAutoStartIntents(context)
            PermissionType.NOTIFICATION_LISTENER -> addNotificationListenerIntents()
            PermissionType.FLOAT_WINDOW -> addFloatWindowIntents(context)
            PermissionType.BACKGROUND_POPUP -> addBackgroundPopupIntents(context)
            PermissionType.BATTERY_SAVING -> addBatterySavingIntents(context)
            PermissionType.IGNORE_BATTERY_OPTIMIZATION -> addIgnoreBatteryOptimizationIntent(context)
            PermissionType.WRITE_SETTINGS -> addWriteSettingsIntents(context)
            PermissionType.REQUEST_INSTALL_PACKAGES -> addInstallPackagesIntents(context)
        }
    }

    private fun MutableList<Intent>.addAutoStartIntents(context: Context) {
        add(Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        })
        add(Intent().apply {
            component = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
        })
        add(Intent().apply {
            component = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )
        })
        add(Intent().apply {
            component = ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )
        })
        add(Intent().apply {
            component = ComponentName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity"
            )
        })
        add(Intent().apply {
            component = ComponentName(
                "com.vivo.abe.uniui",
                "com.vivo.abe.uniui.BgStartUpManagerActivity"
            )
        })
        add(Intent().apply {
            component = ComponentName(
                "com.vivo.abe",
                "com.vivo.abe.BgStartUpManagerActivity"
            )
        })
        add(Intent().apply {
            component = ComponentName(
                "com.meizu.safe",
                "com.meizu.safe.security.SHOW_APPSEC"
            )
        })
        add(Intent().apply {
            component = ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            )
        })
        add(Intent().apply {
            component = ComponentName(
                "com.lenovo.securitycenter",
                "com.lenovo.securitycenter.bootpermission.BootPermissionActivity"
            )
        })
        add(Intent().apply {
            component = ComponentName(
                "com.asus.mobilemanager",
                "com.asus.mobilemanager.MainActivity"
            )
        })
    }

    private fun MutableList<Intent>.addNotificationListenerIntents() {
        add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun MutableList<Intent>.addFloatWindowIntents(context: Context) {
        add(Intent().apply {
            component = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.addviewmonitor.AddViewMonitorActivity"
            )
        })
        add(Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity"
            )
            putExtra("extra_pkgname", context.packageName)
        })
        add(Intent().apply {
            component = ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.FloatWindowActivity"
            )
        })
        add(Intent().apply {
            component = ComponentName(
                "com.vivo.abe.uniui",
                "com.vivo.abe.uniui.FloatWindowActivity"
            )
        })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            add(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.fromParts("package", context.packageName, null)
            })
        }
    }

    private fun MutableList<Intent>.addBackgroundPopupIntents(context: Context) {
        add(Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity"
            )
            putExtra("extra_pkgname", context.packageName)
        })
        add(Intent().apply {
            component = ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.BackgroundPopupActivity"
            )
        })
        add(Intent().apply {
            component = ComponentName(
                "com.vivo.abe.uniui",
                "com.vivo.abe.uniui.BackgroundPopupActivity"
            )
        })
    }

    private fun MutableList<Intent>.addBatterySavingIntents(context: Context) {
        add(Intent().apply {
            component = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.power.ui.HwPowerManagerActivity"
            )
        })
        add(Intent().apply {
            component = ComponentName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
            )
            putExtra("package_name", context.packageName)
            putExtra("package_label", getAppName(context))
        })
        add(Intent().apply {
            component = ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            )
        })
        add(Intent().apply {
            component = ComponentName(
                "com.asus.mobilemanager",
                "com.asus.mobilemanager.powersaver.PowerSaverActivity"
            )
        })
    }

    private fun MutableList<Intent>.addIgnoreBatteryOptimizationIntent(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            add(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            })
        }
        add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    private fun MutableList<Intent>.addWriteSettingsIntents(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            add(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            })
        }
    }

    private fun MutableList<Intent>.addInstallPackagesIntents(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.fromParts("package", context.packageName, null)
            })
        }
    }

    private fun getAppName(context: Context): String {
        return try {
            val appInfo = context.applicationInfo
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            context.packageName
        }
    }
}
