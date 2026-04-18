package com.answufeng.permission

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.annotation.CheckResult
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * 基于协程 + 隐藏 Fragment 构建的 Android 运行时权限请求工具。
 *
 * ### 并发安全
 * - 所有权限请求（包括 rationale 展示）通过单个 [Mutex] 进行序列化
 * - 同一时刻只有一个请求流程处于活跃状态，不会同时出现多个 rationale 对话框
 * - 每次请求创建独立的 [PermissionFragment] 实例，请求完成后自动移除
 * - 若 Activity 被销毁（如配置变更），挂起的协程会自动取消
 *
 * ### 国产 ROM 兼容
 * - 通过 [PermissionDetector] 使用 AppOpsManager 增强永久拒绝检测
 * - [openAppSettings] 支持华为/小米等国产 ROM 的多重 Intent 回退
 * - 60 秒超时保护防止权限对话框异常关闭导致协程永久挂起
 *
 * ### 基本用法（在 Activity 中）
 * ```kotlin
 * lifecycleScope.launch {
 *     val result = AwPermission.request(this@MainActivity, Manifest.permission.CAMERA)
 *     if (result.isAllGranted) {
 *         openCamera()
 *     } else if (result.hasPermanentlyDenied) {
 *         AwPermission.openAppSettings(context)
 *     }
 * }
 * ```
 *
 * ### 多个权限
 * ```kotlin
 * val result = AwPermission.request(
 *     activity,
 *     Manifest.permission.CAMERA,
 *     Manifest.permission.RECORD_AUDIO
 * )
 * // 查询单个权限状态
 * if (result.isGranted(Manifest.permission.CAMERA)) { openCamera() }
 * ```
 *
 * ### 使用权限组
 * ```kotlin
 * val result = AwPermission.request(activity, *PermissionGroups.LOCATION)
 * // 版本自适应（推荐）
 * val result = AwPermission.request(activity, *PermissionGroups.storage())
 * ```
 *
 * ### 带理由的请求
 * ```kotlin
 * val result = AwPermission.requestWithRationale(
 *     activity,
 *     Manifest.permission.CAMERA,
 * ) { permissions ->
 *     showRationaleDialog(permissions)
 * }
 * ```
 *
 * ### 从 Fragment 请求
 * ```kotlin
 * val result = AwPermission.request(fragment, Manifest.permission.CAMERA)
 * val result = AwPermission.requestWithRationale(fragment, Manifest.permission.CAMERA) { ... }
 * ```
 *
 * ### 检查权限
 * ```kotlin
 * if (AwPermission.isGranted(context, Manifest.permission.CAMERA)) { ... }
 * ```
 *
 * ### 打开应用设置
 * ```kotlin
 * val success = AwPermission.openAppSettings(context)
 * ```
 */
object AwPermission {

    internal val tagCounter: AtomicLong = AtomicLong(0)

    private val mutex = Mutex()

    /**
     * 检查单个权限是否已授权。
     *
     * @param context 任意 [Context]
     * @param permission 权限名称（如 `Manifest.permission.CAMERA`）
     * @return 已授权返回 `true`，否则返回 `false`
     */
    fun isGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查所有指定权限是否已授权。
     *
     * @param context 任意 [Context]
     * @param permissions 要检查的权限名称
     * @return 全部已授权返回 `true`，否则返回 `false`
     */
    fun isAllGranted(context: Context, vararg permissions: String): Boolean {
        return permissions.all { isGranted(context, it) }
    }

    /**
     * 检查系统是否应为某个权限展示理由说明。
     *
     * 若用户之前拒绝过该权限（未勾选"不再询问"），返回 `true`，
     * 表示应在再次请求前展示理由。
     *
     * @param activity [FragmentActivity]
     * @param permission 要检查的权限名称
     * @return 应展示理由返回 `true`，否则返回 `false`
     */
    fun shouldShowRationale(activity: FragmentActivity, permission: String): Boolean {
        return activity.shouldShowRequestPermissionRationale(permission)
    }

    /**
     * 返回需要理由说明的权限列表。
     *
     * @param activity [FragmentActivity]
     * @param permissions 要检查的权限名称
     * @return 需要理由说明的权限列表
     */
    fun permissionsRequiringRationale(activity: FragmentActivity, vararg permissions: String): List<String> {
        return permissions.filter { activity.shouldShowRequestPermissionRationale(it) }
    }

    /**
     * 请求运行时权限（挂起函数，用户响应后恢复）。
     *
     * 已授权的权限会自动跳过，仅向用户展示未授权的权限。
     * 请求在 [Mutex] 保护下执行，保证并发安全。
     * 永久拒绝检测通过 [PermissionDetector] 增强，兼容国产 ROM。
     *
     * @param activity 用于发起请求的 [FragmentActivity]
     * @param permissions 要请求的权限
     * @return [PermissionResult]，包含已授权、被拒绝和被永久拒绝的权限
     * @throws IllegalArgumentException 若 [permissions] 为空或包含空白字符串
     * @throws IllegalStateException 若 Activity 正在结束或已销毁
     */
    @CheckResult
    suspend fun request(activity: FragmentActivity, vararg permissions: String): PermissionResult =
        mutex.withLock {
            require(permissions.isNotEmpty()) { "permissions must not be empty" }
            require(permissions.all { it.isNotBlank() }) { "permission names must not be blank" }
            checkActivityState(activity)
            requestInternal(activity, permissions)
        }

    /**
     * 请求运行时权限（接受 [List] 参数，方便传入权限组）。
     *
     * ```kotlin
     * val result = AwPermission.request(activity, PermissionGroups.LOCATION.toList())
     * ```
     *
     * @param activity 用于发起请求的 [FragmentActivity]
     * @param permissions 要请求的权限列表
     * @return [PermissionResult]
     * @throws IllegalArgumentException 若 [permissions] 为空或包含空白字符串
     * @throws IllegalStateException 若 Activity 正在结束或已销毁
     */
    @CheckResult
    suspend fun request(activity: FragmentActivity, permissions: List<String>): PermissionResult =
        request(activity, *permissions.toTypedArray())

    /**
     * 从 Fragment 请求运行时权限。
     *
     * 委托给 [request]，使用 Fragment 的宿主 Activity。
     *
     * @param fragment 用于发起请求的 [Fragment]
     * @param permissions 要请求的权限
     * @return [PermissionResult]，包含请求结果
     * @throws IllegalArgumentException 若 [permissions] 为空或包含空白字符串
     * @throws IllegalStateException 若 Activity 正在结束或已销毁
     */
    @CheckResult
    suspend fun request(fragment: Fragment, vararg permissions: String): PermissionResult {
        val activity = fragment.requireActivity()
        return request(activity, *permissions)
    }

    /**
     * 从 Fragment 请求运行时权限（接受 [List] 参数）。
     *
     * @param fragment 用于发起请求的 [Fragment]
     * @param permissions 要请求的权限列表
     * @return [PermissionResult]
     */
    @CheckResult
    suspend fun request(fragment: Fragment, permissions: List<String>): PermissionResult {
        return request(fragment, *permissions.toTypedArray())
    }

    /**
     * 带理由说明的权限请求。
     *
     * 整个流程（包括 rationale 展示和权限请求）在 [Mutex] 保护下执行，
     * 保证同一时刻不会有其他请求流程并发执行。
     *
     * 默认使用 [RationaleStrategy.OnShouldShow]：仅当系统建议展示理由时触发。
     * 如果希望在权限被拒绝时也触发（包括首次拒绝），使用
     * [requestWithRationale] 的 [RationaleStrategy.OnDenied] 重载版本。
     *
     * @param activity 用于发起请求的 [FragmentActivity]
     * @param permissions 要请求的权限
     * @param rationale 接收需要理由说明的权限列表的挂起 Lambda。
     *                  返回 `true` 继续请求，返回 `false` 取消。
     * @return 若请求继续则返回 [PermissionResult]，若用户取消理由对话框则返回 `null`
     * @throws IllegalArgumentException 若 [permissions] 为空或包含空白字符串
     * @throws IllegalStateException 若 Activity 正在结束或已销毁
     */
    @CheckResult
    suspend fun requestWithRationale(
        activity: FragmentActivity,
        vararg permissions: String,
        rationale: suspend (permissions: List<String>) -> Boolean
    ): PermissionResult? = requestWithRationale(activity, *permissions, strategy = RationaleStrategy.OnShouldShow, rationale = rationale)

    /**
     * 带理由说明的权限请求（可指定触发策略）。
     *
     * @param activity 用于发起请求的 [FragmentActivity]
     * @param permissions 要请求的权限
     * @param strategy rationale 触发策略，参见 [RationaleStrategy]
     * @param rationale 接收需要理由说明的权限列表的挂起 Lambda。
     *                  返回 `true` 继续请求，返回 `false` 取消。
     * @return 若请求继续则返回 [PermissionResult]，若用户取消理由对话框则返回 `null`
     * @throws IllegalArgumentException 若 [permissions] 为空或包含空白字符串
     * @throws IllegalStateException 若 Activity 正在结束或已销毁
     */
    @CheckResult
    suspend fun requestWithRationale(
        activity: FragmentActivity,
        vararg permissions: String,
        strategy: RationaleStrategy = RationaleStrategy.OnShouldShow,
        rationale: suspend (permissions: List<String>) -> Boolean
    ): PermissionResult? = mutex.withLock {
        require(permissions.isNotEmpty()) { "permissions must not be empty" }
        require(permissions.all { it.isNotBlank() }) { "permission names must not be blank" }
        checkActivityState(activity)

        val needRationale = when (strategy) {
            RationaleStrategy.OnShouldShow -> permissions.filter {
                !isGranted(activity, it) && activity.shouldShowRequestPermissionRationale(it)
            }
            RationaleStrategy.OnDenied -> permissions.filter { !isGranted(activity, it) }
        }

        if (needRationale.isNotEmpty()) {
            val shouldProceed = rationale(needRationale)
            if (!shouldProceed) return@withLock null
        }

        requestInternal(activity, permissions)
    }

    /**
     * 从 Fragment 带理由说明的权限请求。
     *
     * 委托给 Activity 版本的 [requestWithRationale]。
     *
     * @param fragment 用于发起请求的 [Fragment]
     * @param permissions 要请求的权限
     * @param rationale 接收需要理由说明的权限列表的挂起 Lambda
     * @return 若请求继续则返回 [PermissionResult]，若用户取消则返回 `null`
     */
    @CheckResult
    suspend fun requestWithRationale(
        fragment: Fragment,
        vararg permissions: String,
        rationale: suspend (permissions: List<String>) -> Boolean
    ): PermissionResult? = requestWithRationale(fragment, *permissions, strategy = RationaleStrategy.OnShouldShow, rationale = rationale)

    /**
     * 从 Fragment 带理由说明的权限请求（可指定触发策略）。
     *
     * 委托给 Activity 版本的 [requestWithRationale]。
     *
     * @param fragment 用于发起请求的 [Fragment]
     * @param permissions 要请求的权限
     * @param strategy rationale 触发策略
     * @param rationale 接收需要理由说明的权限列表的挂起 Lambda
     * @return 若请求继续则返回 [PermissionResult]，若用户取消则返回 `null`
     */
    @CheckResult
    suspend fun requestWithRationale(
        fragment: Fragment,
        vararg permissions: String,
        strategy: RationaleStrategy = RationaleStrategy.OnShouldShow,
        rationale: suspend (permissions: List<String>) -> Boolean
    ): PermissionResult? {
        val activity = fragment.requireActivity()
        return requestWithRationale(activity, *permissions, strategy = strategy, rationale = rationale)
    }

    /**
     * 打开当前应用的系统设置页。
     *
     * 支持国产 ROM 多重回退：标准设置页 → 华为安全中心 → 小米安全中心 → 最终回退。
     *
     * @param context 任意 [Context]
     * @return 设置页成功打开返回 `true`，无法打开返回 `false`
     */
    fun openAppSettings(context: Context): Boolean {
        val intents = buildAppSettingsIntents(context)

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

        return try {
            val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 打开当前应用的系统设置页，等待用户返回后检查权限状态。
     *
     * 打开设置页后，协程会挂起直到 Activity 恢复到 RESUMED 状态，
     * 然后自动重新检查指定权限的授权状态。
     *
     * 适用于权限被永久拒绝后引导用户去设置页开启权限的场景。
     *
     * @param activity [FragmentActivity]
     * @param permissions 要在返回后检查的权限
     * @return 用户从设置页返回后的 [PermissionResult]
     * @throws IllegalStateException 若 Activity 正在结束或已销毁
     */
    suspend fun openAppSettingsAndWait(
        activity: FragmentActivity,
        vararg permissions: String
    ): PermissionResult {
        checkActivityState(activity)
        openAppSettings(activity)
        return waitForActivityResumedAndCheck(activity, permissions)
    }

    /**
     * 从 Fragment 打开应用设置页并等待返回后检查权限状态。
     *
     * 委托给 Activity 版本的 [openAppSettingsAndWait]。
     *
     * @param fragment [Fragment]
     * @param permissions 要在返回后检查的权限
     * @return 用户从设置页返回后的 [PermissionResult]
     */
    suspend fun openAppSettingsAndWait(
        fragment: Fragment,
        vararg permissions: String
    ): PermissionResult {
        return openAppSettingsAndWait(fragment.requireActivity(), *permissions)
    }

    private suspend fun waitForActivityResumedAndCheck(
        activity: FragmentActivity,
        permissions: Array<out String>
    ): PermissionResult {
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val observer = object : androidx.lifecycle.DefaultLifecycleObserver {
                override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
                    activity.lifecycle.removeObserver(this)
                    if (cont.isActive) {
                        val granted = mutableListOf<String>()
                        val denied = mutableListOf<String>()
                        val permanentlyDenied = mutableListOf<String>()
                        for (permission in permissions) {
                            if (isGranted(activity, permission)) {
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
                                    if (isPermDenied) permanentlyDenied.add(permission) else denied.add(permission)
                                }
                            }
                        }
                        cont.resume(PermissionResult(granted = granted, denied = denied, permanentlyDenied = permanentlyDenied)) {}
                    }
                }
            }
            activity.lifecycle.addObserver(observer)
            cont.invokeOnCancellation { activity.lifecycle.removeObserver(observer) }
        }
    }

    private fun buildAppSettingsIntents(context: Context): List<Intent> = buildList {
        add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        add(Intent().apply {
            component = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.permissionmanager.ui.MainActivity"
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
                "com.coloros.safecenter.permission.PermissionTopActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        add(Intent().apply {
            component = ComponentName(
                "com.oppo.safe",
                "com.oppo.safe.permission.PermissionTopActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        add(Intent().apply {
            component = ComponentName(
                "com.vivo.abe.uniui",
                "com.vivo.abe.uniui.UriPermissionActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        add(Intent().apply {
            component = ComponentName(
                "com.meizu.safe",
                "com.meizu.safe.security.PermissionActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private suspend fun requestInternal(
        activity: FragmentActivity,
        permissions: Array<out String>
    ): PermissionResult {
        val alreadyGranted = mutableListOf<String>()
        val needRequest = mutableListOf<String>()

        for (permission in permissions) {
            if (isGranted(activity, permission)) {
                alreadyGranted.add(permission)
            } else {
                needRequest.add(permission)
            }
        }

        if (needRequest.isEmpty()) {
            return PermissionResult(granted = alreadyGranted, denied = emptyList(), permanentlyDenied = emptyList())
        }

        val rationaleStateBefore = needRequest.associateWith {
            activity.shouldShowRequestPermissionRationale(it)
        }

        val fragment = PermissionFragment.create(activity)
        val rawResult = fragment.requestPermissions(needRequest.toTypedArray())

        val granted = mutableListOf<String>()
        val denied = mutableListOf<String>()
        val permanentlyDenied = mutableListOf<String>()

        for ((permission, isGranted) in rawResult) {
            if (isGranted) {
                granted.add(permission)
            } else {
                val isPermDenied = PermissionDetector.isPermanentlyDenied(
                    activity,
                    permission,
                    wasRationaleBefore = rationaleStateBefore[permission] == true,
                    isRationaleAfter = activity.shouldShowRequestPermissionRationale(permission)
                )
                if (isPermDenied) permanentlyDenied.add(permission) else denied.add(permission)
            }
        }

        return PermissionResult(
            granted = alreadyGranted + granted,
            denied = denied,
            permanentlyDenied = permanentlyDenied
        )
    }

    private fun checkActivityState(activity: FragmentActivity) {
        check(!activity.isFinishing) { "Activity is finishing, cannot request permissions" }
        check(!activity.isDestroyed) { "Activity is destroyed, cannot request permissions" }
    }
}
