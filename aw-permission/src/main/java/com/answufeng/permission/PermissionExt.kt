package com.answufeng.permission

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * 检查单个权限是否已授权。
 *
 * 委托给 [AwPermission.isGranted]。
 *
 * @receiver 任意 [Context]
 * @param permission 权限名称
 * @return 已授权返回 `true`
 */
public fun Context.hasPermission(permission: String): Boolean {
    return AwPermission.isGranted(this, permission)
}

/**
 * 检查所有指定权限是否已授权。
 *
 * 委托给 [AwPermission.isAllGranted]。
 *
 * @receiver 任意 [Context]
 * @param permissions 权限名称
 * @return 全部已授权返回 `true`
 */
public fun Context.hasPermissions(vararg permissions: String): Boolean {
    return AwPermission.isAllGranted(this, *permissions)
}

/**
 * 请求权限（suspend 扩展函数）。
 *
 * 委托给 [AwPermission.request]。
 *
 * @receiver [FragmentActivity]
 * @param permissions 要请求的权限
 * @return [PermissionResult]
 */
public suspend fun FragmentActivity.requestRuntimePermissions(vararg permissions: String): PermissionResult {
    return AwPermission.request(this, *permissions)
}

/**
 * 从 Fragment 请求权限（suspend 扩展函数）。
 *
 * 委托给 [AwPermission.request]。
 *
 * @receiver [Fragment]
 * @param permissions 要请求的权限
 * @return [PermissionResult]
 */
public suspend fun Fragment.requestRuntimePermissions(vararg permissions: String): PermissionResult {
    return AwPermission.request(this, *permissions)
}

/**
 * 带理由说明的权限请求（suspend 扩展函数）。
 *
 * 委托给 [AwPermission.requestWithRationale]。
 *
 * @receiver [FragmentActivity]
 * @param permissions 要请求的权限
 * @param rationale 接收需要理由说明的权限列表的挂起 Lambda
 * @return [PermissionResult] 或 `null`（用户取消 rationale 时）
 */
public suspend fun FragmentActivity.requestRuntimePermissionsWithRationale(
    vararg permissions: String,
    strategy: RationaleStrategy = RationaleStrategy.OnShouldShow,
    rationale: suspend (permissions: List<String>) -> Boolean
): PermissionResult? {
    return AwPermission.requestWithRationale(this, *permissions, strategy = strategy, rationale = rationale)
}

/**
 * 从 Fragment 带理由说明的权限请求（suspend 扩展函数）。
 *
 * 委托给 [AwPermission.requestWithRationale]。
 *
 * @receiver [Fragment]
 * @param permissions 要请求的权限
 * @param rationale 接收需要理由说明的权限列表的挂起 Lambda
 * @return [PermissionResult] 或 `null`（用户取消 rationale 时）
 */
public suspend fun Fragment.requestRuntimePermissionsWithRationale(
    vararg permissions: String,
    strategy: RationaleStrategy = RationaleStrategy.OnShouldShow,
    rationale: suspend (permissions: List<String>) -> Boolean
): PermissionResult? {
    return AwPermission.requestWithRationale(this, *permissions, strategy = strategy, rationale = rationale)
}

/**
 * 请求权限并自动调用授权/拒绝回调。
 *
 * 回调执行前会检查 Activity 是否已销毁或正在结束，确保生命周期安全。
 * 拒绝时默认打印警告日志，建议显式处理拒绝情况。
 *
 * @receiver [FragmentActivity]
 * @param permissions 要请求的权限
 * @param onGranted 全部授权时调用
 * @param onDenied 有权限被拒绝时调用
 */
public fun FragmentActivity.runWithPermissions(
    vararg permissions: String,
    onGranted: () -> Unit,
    onDenied: (PermissionResult) -> Unit = {
        AwPermission.log(AwPermission.LogLevel.WARN, "权限被拒绝但未处理: ${it.allDenied}")
    }
) {
    lifecycleScope.launch {
        val result = AwPermission.request(this@runWithPermissions, *permissions)
        if (!isDestroyed && !isFinishing) {
            if (result.isAllGranted) {
                onGranted()
            } else {
                onDenied(result)
            }
        }
    }
}

/**
 * 从 Fragment 请求权限并自动调用授权/拒绝回调。
 *
 * 回调执行前会检查宿主 Activity 是否已销毁或正在结束，确保生命周期安全。
 * 拒绝时默认打印警告日志，建议显式处理拒绝情况。
 *
 * @receiver [Fragment]
 * @param permissions 要请求的权限
 * @param onGranted 全部授权时调用
 * @param onDenied 有权限被拒绝时调用
 */
public fun Fragment.runWithPermissions(
    vararg permissions: String,
    onGranted: () -> Unit,
    onDenied: (PermissionResult) -> Unit = {
        AwPermission.log(AwPermission.LogLevel.WARN, "权限被拒绝但未处理: ${it.allDenied}")
    }
) {
    lifecycleScope.launch {
        val result = AwPermission.request(this@runWithPermissions, *permissions)
        val activity = this@runWithPermissions.activity
        if (activity != null && !activity.isDestroyed && !activity.isFinishing) {
            if (result.isAllGranted) {
                onGranted()
            } else {
                onDenied(result)
            }
        }
    }
}

/**
 * 观察权限状态变化的 Flow。
 *
 * 每次 Activity 恢复到 [Lifecycle.State.RESUMED] 时发射当前权限状态。
 * 适用于检测用户从系统设置页返回后的权限变化。
 *
 * Flow 发射的 [PermissionResult] 会区分 `denied` 和 `permanentlyDenied`：
 * - 未授权且 `shouldShowRequestPermissionRationale` 返回 `true` → `denied`
 * - 未授权且 `shouldShowRequestPermissionRationale` 返回 `false` 且 AppOps 检测为 `MODE_IGNORED` → `permanentlyDenied`
 * - 未授权且 `shouldShowRequestPermissionRationale` 返回 `false` 且 AppOps 未检测到拒绝 → `denied`（可能是首次未请求）
 *
 * 通过 [PermissionDetector] 的 AppOps 增强检测，可以更准确地区分"从未请求"和"永久拒绝"。
 * 此 Flow 最适合用于「从设置页返回」的场景。
 *
 * @receiver [FragmentActivity]
 * @param permissions 要观察的权限
 * @return 每次 Activity 恢复时发射 [PermissionResult] 的 [Flow]
 */
public fun FragmentActivity.observePermissions(vararg permissions: String): Flow<PermissionResult> =
    callbackFlow {
        val job = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val activity = this@observePermissions
                val granted = mutableListOf<String>()
                val denied = mutableListOf<String>()
                val permanentlyDenied = mutableListOf<String>()

                for (permission in permissions) {
                    if (AwPermission.isGranted(activity, permission)) {
                        granted.add(permission)
                    } else {
                        val rationale = activity.shouldShowRequestPermissionRationale(permission)
                        if (rationale) {
                            denied.add(permission)
                        } else {
                            val isPermDenied = PermissionDetector.isPermanentlyDenied(
                                activity, permission,
                                wasRationaleBefore = false,
                                isRationaleAfter = false
                            )
                            if (isPermDenied) {
                                permanentlyDenied.add(permission)
                            } else {
                                denied.add(permission)
                            }
                        }
                    }
                }

                trySend(
                    PermissionResult(
                        granted = granted,
                        denied = denied,
                        permanentlyDenied = permanentlyDenied
                    )
                )
            }
        }
        awaitClose { job.cancel() }
    }

/**
 * 打开应用设置页并等待用户返回后检查权限状态（suspend 扩展函数）。
 *
 * 委托给 [AwPermission.openAppSettingsAndWait]。
 *
 * @receiver [FragmentActivity]
 * @param permissions 要在返回后检查的权限
 * @return 用户从设置页返回后的 [PermissionResult]
 */
public suspend fun FragmentActivity.openAppSettingsAndWait(vararg permissions: String): PermissionResult {
    return AwPermission.openAppSettingsAndWait(this, *permissions)
}

/**
 * 从 Fragment 打开应用设置页并等待返回后检查权限状态（suspend 扩展函数）。
 *
 * 委托给 [AwPermission.openAppSettingsAndWait]。
 *
 * @receiver [Fragment]
 * @param permissions 要在返回后检查的权限
 * @return 用户从设置页返回后的 [PermissionResult]
 */
public suspend fun Fragment.openAppSettingsAndWait(vararg permissions: String): PermissionResult {
    return AwPermission.openAppSettingsAndWait(this, *permissions)
}

/**
 * 批量查询权限授权状态。
 *
 * 一次调用获取所有权限的授权状态，返回权限名称到授权状态的映射。
 *
 * @receiver 任意 [Context]
 * @param permissions 要查询的权限
 * @return 权限名称到授权状态的映射（`true` 表示已授权）
 */
public fun Context.checkPermissions(vararg permissions: String): Map<String, Boolean> {
    return permissions.associateWith { AwPermission.isGranted(this, it) }
}
