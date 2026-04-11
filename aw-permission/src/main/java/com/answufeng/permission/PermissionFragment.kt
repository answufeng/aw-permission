package com.answufeng.permission

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 无 UI 的隐藏 Fragment，用于代理权限请求。
 *
 * 每次权限请求创建一个新的 Fragment 实例，请求完成后自动移除。
 * 通过 [ActivityResultContracts] 发起权限请求，配合协程挂起等待结果。
 *
 * ### 线程安全 & 并发保护
 * - 外部调用方 [BrickPermission] 使用 [Mutex] 全局串行化请求，同一时刻
 *   只有一个权限请求处于进行中状态。
 * - 每个 Fragment 实例持有独立的 [continuation]，不存在共享状态覆盖风险。
 * - 如果 Fragment 被系统恢复（配置变更/进程终止），会立即移除自身并通过
 *   [invokeOnCancellation] 取消挂起的协程。
 *
 * 此类对外部不可见，由 [BrickPermission] 内部管理。
 */
internal class PermissionFragment : Fragment() {

    private var continuation: CancellableContinuation<PermissionResult>? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultMap ->
        val granted = mutableListOf<String>()
        val denied = mutableListOf<String>()
        val permanentlyDenied = mutableListOf<String>()

        for ((permission, isGranted) in resultMap) {
            when {
                isGranted -> granted.add(permission)
                !shouldShowRequestPermissionRationale(permission) -> permanentlyDenied.add(permission)
                else -> denied.add(permission)
            }
        }

        val result = PermissionResult(granted, denied, permanentlyDenied)
        continuation?.resume(result)
        continuation = null

        // 自动移除 Fragment
        parentFragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 如果 Fragment 被系统恢复，无法恢复协程挂起，直接移除
        if (savedInstanceState != null) {
            continuation?.cancel()
            continuation = null
            parentFragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 兜底：如果 Fragment 被意外销毁而 continuation 仍然挂起，取消它以避免协程永远挂起
        continuation?.cancel()
        continuation = null
    }

    internal suspend fun requestPermissions(permissions: Array<String>): PermissionResult {
        return suspendCancellableCoroutine { cont ->
            continuation = cont
            cont.invokeOnCancellation { continuation = null }
            permissionLauncher.launch(permissions)
        }
    }

    companion object {
        private const val TAG_PREFIX = "BrickPermissionFragment_"

        /**
         * 创建新的 PermissionFragment 并添加到 Activity。
         *
         * 每次请求使用唯一 tag，确保不复用上一个可能残留的 Fragment 实例。
         */
        internal fun create(activity: FragmentActivity): PermissionFragment {
            val fm = activity.supportFragmentManager
            val tag = TAG_PREFIX + System.nanoTime()
            val fragment = PermissionFragment()
            fm.beginTransaction()
                .add(fragment, tag)
                .commitNowAllowingStateLoss()
            return fragment
        }
    }
}
