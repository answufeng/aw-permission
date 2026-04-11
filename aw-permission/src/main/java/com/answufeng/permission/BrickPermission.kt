package com.answufeng.permission

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/**
 * 运行时权限请求工具，基于协程 + 隐藏 Fragment 实现，无需覆写 `onRequestPermissionsResult`。
 *
 * ### 并发安全
 * - 所有权限请求通过单个 [Mutex] 全局串行化，不会出现并发覆盖。
 * - 每次请求创建独立的 [PermissionFragment] 实例，请求完成后自动移除，无共享状态。
 * - 如果发起请求时 Activity 已销毁（配置变更等），挂起协程会被取消。
 *
 * ### 基本用法（Activity 中）
 * ```kotlin
 * lifecycleScope.launch {
 *     val result = BrickPermission.request(this@MainActivity, Manifest.permission.CAMERA)
 *     if (result.isAllGranted) {
 *         openCamera()
 *     } else if (result.hasPermanentlyDenied) {
 *         showSettingsDialog()
 *     }
 * }
 * ```
 *
 * ### 请求多个权限
 * ```kotlin
 * val result = BrickPermission.request(
 *     activity,
 *     Manifest.permission.CAMERA,
 *     Manifest.permission.RECORD_AUDIO
 * )
 * ```
 *
 * ### 带理由（Rationale）的请求
 * ```kotlin
 * BrickPermission.requestWithRationale(
 *     activity = this,
 *     permissions = arrayOf(Manifest.permission.CAMERA),
 *     rationale = { permissions, proceed, cancel ->
 *         AlertDialog.Builder(this)
 *             .setTitle("需要相机权限")
 *             .setMessage("拍照功能需要相机权限，是否允许？")
 *             .setPositiveButton("允许") { _, _ -> proceed() }
 *             .setNegativeButton("拒绝") { _, _ -> cancel() }
 *             .setOnCancelListener { cancel() }
 *             .show()
 *     }
 * )
 * ```
 *
 * ### 检查权限
 * ```kotlin
 * if (BrickPermission.isGranted(context, Manifest.permission.CAMERA)) {
 *     // 已有权限
 * }
 * ```
 *
 * ### 跳转到应用设置页
 * ```kotlin
 * BrickPermission.openAppSettings(context)
 * ```
 */
object BrickPermission {

    /** 串行化所有权限请求，防止并发请求导致 continuation 被覆盖 */
    private val mutex = Mutex()

    /**
     * 检查单个权限是否已授予。
     *
     * @param context 任意 Context
     * @param permission 权限名称（如 `Manifest.permission.CAMERA`）
     * @return 是否已授予
     */
    fun isGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查多个权限是否全部已授予。
     *
     * @param context 任意 Context
     * @param permissions 权限名称数组
     * @return 是否全部已授予
     */
    fun isAllGranted(context: Context, vararg permissions: String): Boolean {
        return permissions.all { isGranted(context, it) }
    }

    /**
     * 请求运行时权限（协程挂起直到用户响应）。
     *
     * 已授予的权限会自动跳过，只请求未授予的部分。
     *
     * @param activity FragmentActivity
     * @param permissions 要请求的权限
     * @return [PermissionResult] 请求结果
     */
    suspend fun request(activity: FragmentActivity, vararg permissions: String): PermissionResult = mutex.withLock {
        require(permissions.isNotEmpty()) { "permissions must not be empty" }
        require(permissions.all { it.isNotBlank() }) { "permission names must not be blank" }

        // 先过滤出未授予的权限
        val granted = mutableListOf<String>()
        val needRequest = mutableListOf<String>()

        for (permission in permissions) {
            if (isGranted(activity, permission)) {
                granted.add(permission)
            } else {
                needRequest.add(permission)
            }
        }

        // 全部已授予
        if (needRequest.isEmpty()) {
            return@withLock PermissionResult(granted, emptyList(), emptyList())
        }

        // 通过隐藏 Fragment 请求（每次创建新实例，避免共享状态）
        val fragment = PermissionFragment.create(activity)
        val result = fragment.requestPermissions(needRequest.toTypedArray())

        // 合并已授予的权限
        result.copy(granted = granted + result.granted)
    }

    /**
     * 在 Fragment 中请求权限。
     *
     * @param fragment 当前 Fragment
     * @param permissions 要请求的权限
     * @return [PermissionResult] 请求结果
     */
    suspend fun request(fragment: Fragment, vararg permissions: String): PermissionResult {
        val activity = fragment.requireActivity()
        return request(activity, *permissions)
    }

    /**
     * 请求权限，带理由说明。
     *
     * 如果需要显示理由（shouldShowRequestPermissionRationale 返回 true），
     * 会先调用 [rationale] 回调让用户展示说明，用户确认后再发起请求。
     *
     * @param activity FragmentActivity
     * @param permissions 要请求的权限数组
     * @param rationale 理由回调 (需要解释的权限列表, 继续请求的回调函数)
     * @return [PermissionResult] 请求结果，如果用户在理由对话框中拒绝则返回 null
     */
    suspend fun requestWithRationale(
        activity: FragmentActivity,
        permissions: Array<String>,
        rationale: (permissions: List<String>, proceed: () -> Unit, cancel: () -> Unit) -> Unit
    ): PermissionResult? {
        val needRationale = permissions.filter {
            !isGranted(activity, it) && activity.shouldShowRequestPermissionRationale(it)
        }

        if (needRationale.isNotEmpty()) {
            val shouldProceed = suspendCancellableCoroutine { cont ->
                rationale(
                    needRationale,
                    { if (cont.isActive) cont.resume(true) },  // proceed
                    { if (cont.isActive) cont.resume(false) },  // cancel
                )
            }
            if (!shouldProceed) return null
        }

        return request(activity, *permissions)
    }

    /**
     * 打开当前应用的系统设置页面。
     *
     * 当权限被永久拒绝时，引导用户手动开启。
     *
     * @param context 任意 Context
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
