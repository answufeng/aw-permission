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
public suspend fun FragmentActivity.requestPermissionsResult(vararg permissions: String): PermissionResult {
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
public suspend fun Fragment.requestPermissionsResult(vararg permissions: String): PermissionResult {
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
public suspend fun FragmentActivity.requestPermissionsResultWithRationale(
    vararg permissions: String,
    rationale: suspend (permissions: List<String>) -> Boolean
): PermissionResult? {
    return AwPermission.requestWithRationale(this, *permissions, rationale = rationale)
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
public suspend fun Fragment.requestPermissionsResultWithRationale(
    vararg permissions: String,
    rationale: suspend (permissions: List<String>) -> Boolean
): PermissionResult? {
    return AwPermission.requestWithRationale(this, *permissions, rationale = rationale)
}

/**
 * 请求权限并自动调用授权/拒绝回调。
 *
 * 回调执行前会检查 Activity 是否已销毁或正在结束，确保生命周期安全。
 *
 * @receiver [FragmentActivity]
 * @param permissions 要请求的权限
 * @param onGranted 全部授权时调用
 * @param onDenied 有权限被拒绝时调用，默认空实现
 */
public fun FragmentActivity.requirePermissions(
    vararg permissions: String,
    onGranted: () -> Unit,
    onDenied: (PermissionResult) -> Unit = {}
) {
    lifecycleScope.launch {
        val result = AwPermission.request(this@requirePermissions, *permissions)
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
 *
 * @receiver [Fragment]
 * @param permissions 要请求的权限
 * @param onGranted 全部授权时调用
 * @param onDenied 有权限被拒绝时调用，默认空实现
 */
public fun Fragment.requirePermissions(
    vararg permissions: String,
    onGranted: () -> Unit,
    onDenied: (PermissionResult) -> Unit = {}
) {
    lifecycleScope.launch {
        val result = AwPermission.request(this@requirePermissions, *permissions)
        val activity = this@requirePermissions.activity
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
 * - 未授权且 `shouldShowRequestPermissionRationale` 返回 `false` → `permanentlyDenied`
 *
 * **注意**：首次请求前 `shouldShowRequestPermissionRationale` 也返回 `false`，
 * 因此在用户从未交互过的场景中，`permanentlyDenied` 可能包含首次未请求的权限。
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
                        if (!rationale) {
                            permanentlyDenied.add(permission)
                        } else {
                            denied.add(permission)
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
