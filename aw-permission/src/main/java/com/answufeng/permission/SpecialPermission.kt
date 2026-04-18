package com.answufeng.permission

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 国产 ROM 特殊权限引导工具。
 *
 * 国产 ROM 有很多非标准权限需要引导用户手动开启，这些权限无法通过标准
 * 运行时权限请求获得，必须跳转到系统设置页面由用户手动操作。
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
        IGNORE_BATTERY_OPTIMIZATION
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
            try {
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
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
        }
    }

    private fun MutableList<Intent>.addAutoStartIntents(context: Context) {
        add(Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        add(Intent().apply {
            component = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        add(Intent().apply {
            component = ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        add(Intent().apply {
            component = ComponentName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        add(Intent().apply {
            component = ComponentName(
                "com.vivo.abe.uniui",
                "com.vivo.abe.uniui.BgStartUpManagerActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        add(Intent().apply {
            component = ComponentName(
                "com.meizu.safe",
                "com.meizu.safe.security.SHOW_APPSEC"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun MutableList<Intent>.addNotificationListenerIntents() {
        add(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun MutableList<Intent>.addFloatWindowIntents(context: Context) {
        add(Intent().apply {
            component = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.addviewmonitor.AddViewMonitorActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        add(Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity"
            )
            putExtra("extra_pkgname", context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        add(Intent().apply {
            component = ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.FloatWindowActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        add(Intent().apply {
            component = ComponentName(
                "com.vivo.abe.uniui",
                "com.vivo.abe.uniui.FloatWindowActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            add(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        add(Intent().apply {
            component = ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.BackgroundPopupActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        add(Intent().apply {
            component = ComponentName(
                "com.vivo.abe.uniui",
                "com.vivo.abe.uniui.BackgroundPopupActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun MutableList<Intent>.addBatterySavingIntents(context: Context) {
        add(Intent().apply {
            component = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.power.ui.HwPowerManagerActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        add(Intent().apply {
            component = ComponentName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
            )
            putExtra("package_name", context.packageName)
            putExtra("package_label", getAppName(context))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun MutableList<Intent>.addIgnoreBatteryOptimizationIntent(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            add(Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
        add(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
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
