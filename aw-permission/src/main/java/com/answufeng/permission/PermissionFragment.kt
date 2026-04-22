package com.answufeng.permission

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * 无界面的隐藏 Fragment，作为权限请求的代理。
 *
 * 每次权限请求创建一个新的 Fragment 实例。Fragment 使用
 * [ActivityResultContracts.RequestMultiplePermissions] 启动权限对话框，
 * 并通过 [suspendCancellableCoroutine] 挂起调用方的协程，直到用户响应。
 *
 * ### 生命周期
 * - 每次请求开始时通过 [create] 创建并添加到 Activity。
 * - 请求完成后自动从 Activity 中移除。
 * - 如果 Fragment 从保存状态恢复（配置变更），会取消挂起的续体并通过 [scheduleRemoval] 安排移除。
 * - [onDestroy] 作为兜底，取消仍然挂起的续体。
 *
 * ### 超时保护
 * - [requestPermissions] 使用 [withTimeoutOrNull] 设置 60 秒超时。
 *   如果系统权限对话框异常关闭（某些 ROM 上可能出现），
 *   协程不会无限挂起。超时的权限会被标记为拒绝。
 *
 * ### 线程安全
 * - 续体存储在 [AtomicReference] 中，确保从 UI 线程（launcher 回调运行的位置）
 *   和其他线程（可能发生取消的位置）的线程安全访问。
 * - 使用 [java.util.concurrent.atomic.AtomicLong] 生成标签，保证 Fragment 标签唯一。
 *
 * 此类为内部类，由 [AwPermission] 管理。
 */
internal class PermissionFragment : Fragment() {

    private val continuationRef = AtomicReference<CancellableContinuation<Map<String, Boolean>>?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultMap ->
        val cont = continuationRef.getAndSet(null)
        cont?.resume(resultMap)
        removeFromParent()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            val cont = continuationRef.getAndSet(null)
            cont?.cancel()
            scheduleRemoval()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val cont = continuationRef.getAndSet(null)
        cont?.cancel()
    }

    /**
     * 启动权限请求并挂起直到用户响应。
     *
     * 包含 60 秒超时保护。如果权限对话框异常关闭（某些定制 ROM 上可能出现），
     * 所有权限会被标记为拒绝，而不是让协程无限挂起。
     *
     * @param permissions 要请求的权限
     * @return 权限名称到授权状态的映射
     */
    internal suspend fun requestPermissions(permissions: Array<String>): Map<String, Boolean> {
        return withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                continuationRef.set(cont)
                cont.invokeOnCancellation { continuationRef.compareAndSet(cont, null) }
                try {
                    permissionLauncher.launch(permissions)
                } catch (e: Exception) {
                    continuationRef.getAndSet(null)
                    cont.cancel(CancellationException("Failed to launch permission request", e))
                }
            }
        } ?: permissions.associateWith { false }
    }

    private fun removeFromParent() {
        if (isAdded) {
            parentFragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
        }
    }

    /**
     * 安排 Fragment 移除，处理 Fragment 尚未添加到 Activity 的情况。
     *
     * 配置变更后，Fragment 可能在附加到 Activity 之前被恢复。
     * 此时 [commitAllowingStateLoss] 会静默失败。
     * 使用 [DefaultLifecycleObserver] 延迟移除到 [onResume] 时机。
     */
    private fun scheduleRemoval() {
        if (isAdded) {
            removeFromParent()
        } else {
            lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
                override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
                    removeFromParent()
                    lifecycle.removeObserver(this)
                }
            })
        }
    }

    internal companion object {
        private const val REQUEST_TIMEOUT_MS = 60_000L

        internal fun create(activity: FragmentActivity): PermissionFragment {
            val fm = activity.supportFragmentManager
            val tag = "AwPermissionFragment_${AwPermission.tagCounter.incrementAndGet()}"
            val fragment = PermissionFragment()
            fm.beginTransaction()
                .add(fragment, tag)
                .commitNowAllowingStateLoss()
            return fragment
        }
    }
}
