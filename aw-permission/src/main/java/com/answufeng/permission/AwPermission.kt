package com.answufeng.permission

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.annotation.CheckResult
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * 基于协程 + 隐藏 Fragment 构建的 Android 运行时权限请求工具。
 *
 * ### 并发安全
 * - 所有权限请求（含 rationale 展示）与 [openAppSettingsAndWait] 通过单个 [Mutex] 序列化
 * - 同一时刻只有一个「请求/设置页等待」流程处于活跃状态，不会与另一路并发交错
 * - 每次请求创建独立的 [PermissionFragment] 实例，请求完成后自动移除
 * - 若 Activity 被销毁（如配置变更），挂起的协程会自动取消
 *
 * ### 国产 ROM 兼容
 * - 通过 [PermissionDetector] 使用 AppOpsManager 增强永久拒绝检测
 * - [openAppSettings] 在疑似定制 ROM 上默认 [AppSettingsLaunchStrategy.AUTO] 优先尝试厂商权限页，再回退应用详情
 * - 库 Manifest 合并 [queries](https://developer.android.com/training/package-visibility) 以提高 Android 11+ 上解析厂商设置页的成功率
 * - 权限系统对话框 60 秒超时（见 [PermissionFragment]），[openAppSettingsAndWait] 等待返回最多 120 秒
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
 * // 或显式指定策略
 * AwPermission.openAppSettings(context, AwPermission.AppSettingsLaunchStrategy.OEM_FIRST)
 * ```
 */
object AwPermission {

    internal val tagCounter: AtomicLong = AtomicLong(0)

    private const val SETTINGS_WAIT_TIMEOUT_MS = 120_000L
    private const val LOG_TAG = "AwPermission"

    private val mutex = Mutex()

    /**
     * 與 [request] / [openAppSettingsAndWait] 共用同一 [Mutex]，供特殊權限等擴展 API 串行化使用。
     */
    internal suspend fun <T> withPermissionSequenceLock(block: suspend () -> T): T = mutex.withLock { block() }

    /**
     * 日志级别。
     */
    public enum class LogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    /**
     * 打开应用详情 / 权限相关设置时的 Intent 尝试顺序。
     *
     * 定制系统上 [OEM_FIRST] 往往更接近「权限列表」；接近 AOSP 的设备用 [STANDARD_FIRST] 更稳妥。
     */
    public enum class AppSettingsLaunchStrategy {
        /** 先 [Settings.ACTION_APPLICATION_DETAILS_SETTINGS]，再尝试各厂商权限入口。 */
        STANDARD_FIRST,

        /** 先尝试各厂商权限管理页，最后回退到应用详情页。 */
        OEM_FIRST,

        /**
         * 由 [isLikelyCustomRom] 决定：疑似定制 ROM 时为 [OEM_FIRST]，否则 [STANDARD_FIRST]。
         */
        AUTO,
    }

    private val loggerRef = AtomicReference<((level: LogLevel, tag: String, msg: String) -> Unit)?>(null)

    /**
     * 设置自定义日志输出。
     *
     * 传入 `null` 禁用日志输出（默认行为）。
     *
     * ```kotlin
     * AwPermission.setLogger { level, tag, msg ->
     *     when (level) {
     *         AwPermission.LogLevel.WARN -> Log.w(tag, msg)
     *         AwPermission.LogLevel.ERROR -> Log.e(tag, msg)
     *         else -> Log.d(tag, msg)
     *     }
     * }
     * ```
     *
     * @param logger 日志回调，接收级别、标签和消息
     */
    public fun setLogger(logger: ((level: LogLevel, tag: String, msg: String) -> Unit)?) {
        loggerRef.set(logger)
    }

    internal fun log(level: LogLevel, msg: String) {
        loggerRef.get()?.invoke(level, LOG_TAG, msg)
    }

    /**
     * 当前设备是否疑似常见定制 ROM（华为/小米/OPPO/vivo 等）。
     *
     * 用于默认设置跳转策略等；与 [PermissionDetector] 的增强检测使用同一套启发式规则。
     */
    public fun isLikelyCustomRom(): Boolean = PermissionDetector.isProblematicRom()

    /**
     * 检查单个权限是否已授权。
     *
     * 此方法可在任意线程调用。
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
     * 此方法可在任意线程调用。
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
            checkActivityState(activity)
            val deduplicated = normalizePermissionArgs(permissions)
            requestInternal(activity, deduplicated)
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
        checkActivityState(activity)
        val deduplicated = normalizePermissionArgs(permissions)

        val needRationale = when (strategy) {
            RationaleStrategy.OnShouldShow -> deduplicated.filter {
                !isGranted(activity, it) && activity.shouldShowRequestPermissionRationale(it)
            }
            RationaleStrategy.OnDenied -> deduplicated.filter { !isGranted(activity, it) }
        }

        if (needRationale.isNotEmpty()) {
            val shouldProceed = rationale(needRationale)
            if (!shouldProceed) return@withLock null
        }

        requestInternal(activity, deduplicated)
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
     * 打开当前应用的系统设置页（权限相关）。
     *
     * 默认 [AppSettingsLaunchStrategy.AUTO]：在 [isLikelyCustomRom] 为 true 时优先尝试厂商权限管理页，
     * 否则优先标准应用详情页；任一成功即返回。
     *
     * @param context 任意 [Context]
     * @return 设置页成功打开返回 `true`，无法打开返回 `false`
     */
    fun openAppSettings(context: Context): Boolean =
        openAppSettings(context, AppSettingsLaunchStrategy.AUTO)

    /**
     * 打开当前应用的系统设置页，并指定 Intent 尝试顺序。
     *
     * @param context 任意 [Context]
     * @param strategy 启动策略，参见 [AppSettingsLaunchStrategy]
     * @return 设置页成功打开返回 `true`，无法打开返回 `false`
     */
    fun openAppSettings(context: Context, strategy: AppSettingsLaunchStrategy): Boolean {
        val intents = buildAppSettingsIntents(context, strategy)

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
            context.startActivity(buildStandardAppDetailsIntent(context))
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
     * @throws IllegalArgumentException 若 [permissions] 为空或包含空白字符串
     * @throws IllegalStateException 若 Activity 正在结束或已销毁
     */
    suspend fun openAppSettingsAndWait(
        activity: FragmentActivity,
        vararg permissions: String
    ): PermissionResult =
        openAppSettingsAndWait(activity, AppSettingsLaunchStrategy.AUTO, *permissions)

    /**
     * 打开应用设置页并等待返回，且指定与 [openAppSettings] 相同的启动策略。
     *
     * 与 [request] 共用 [Mutex]；在持有锁期间会挂起直至下一次 [onResume] 或等待超时，期间其他请求会排队。
     * 若 [openAppSettings] 无法打开任何页面，仍可能因其它原因触发 [onResume]，请结合返回值与权限状态判断。
     *
     * @throws IllegalArgumentException 若 [permissions] 为空或包含空白字符串
     * @throws IllegalStateException 若 Activity 正在结束或已销毁
     */
    suspend fun openAppSettingsAndWait(
        activity: FragmentActivity,
        strategy: AppSettingsLaunchStrategy,
        vararg permissions: String
    ): PermissionResult = mutex.withLock {
        checkActivityState(activity)
        val deduplicated = normalizePermissionArgs(permissions)
        if (!openAppSettings(activity, strategy)) {
            return@withLock classifyPermissionsAfterSettingsVisit(activity, deduplicated)
        }
        waitForActivityResumedAndCheck(activity, deduplicated)
    }

    /**
     * 从 Fragment 打开应用设置页并等待返回后检查权限状态。
     *
     * 委托给 Activity 版本的 [openAppSettingsAndWait]。
     *
     * @param fragment [Fragment]
     * @param permissions 要在返回后检查的权限
     * @return 用户从设置页返回后的 [PermissionResult]
     * @throws IllegalArgumentException 若 [permissions] 为空或包含空白字符串
     * @throws IllegalStateException 若宿主 Activity 正在结束或已销毁
     */
    suspend fun openAppSettingsAndWait(
        fragment: Fragment,
        vararg permissions: String
    ): PermissionResult =
        openAppSettingsAndWait(fragment.requireActivity(), *permissions)

    /**
     * 从 Fragment 打开应用设置页并等待返回，且可指定与 [openAppSettings] 相同的启动策略。
     *
     * @param fragment [Fragment]
     * @param strategy 启动策略
     * @param permissions 要在返回后检查的权限
     * @return 用户从设置页返回后的 [PermissionResult]
     * @throws IllegalArgumentException 若 [permissions] 为空或包含空白字符串
     * @throws IllegalStateException 若宿主 Activity 正在结束或已销毁
     */
    suspend fun openAppSettingsAndWait(
        fragment: Fragment,
        strategy: AppSettingsLaunchStrategy,
        vararg permissions: String
    ): PermissionResult =
        openAppSettingsAndWait(fragment.requireActivity(), strategy, *permissions)

    private suspend fun waitForActivityResumedAndCheck(
        activity: FragmentActivity,
        permissions: Array<out String>
    ): PermissionResult {
        return kotlinx.coroutines.withTimeoutOrNull(SETTINGS_WAIT_TIMEOUT_MS) {
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                val observer = object : androidx.lifecycle.DefaultLifecycleObserver {
                    override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
                        activity.lifecycle.removeObserver(this)
                        if (cont.isActive) {
                            @Suppress("DEPRECATION")
                            cont.resume(
                                classifyPermissionsAfterSettingsVisit(activity, permissions),
                                onCancellation = { }
                            )
                        }
                    }
                }
                activity.lifecycle.addObserver(observer)
                cont.invokeOnCancellation { activity.lifecycle.removeObserver(observer) }
            }
        } ?: if (activity.isDestroyed || activity.isFinishing) {
            val granted = permissions.filter { isGranted(activity.applicationContext, it) }
            val rest = permissions.filterNot { it in granted }
            PermissionResult(granted = granted, denied = rest, permanentlyDenied = emptyList())
        } else {
            classifyPermissionsAfterSettingsVisit(activity, permissions)
        }
    }

    /**
     * 用户从设置返回后（或等待超时后）根据当前状态分类权限，与 onResume 路径一致，避免超时低估永久拒绝。
     */
    private fun classifyPermissionsAfterSettingsVisit(
        activity: FragmentActivity,
        permissions: Array<out String>
    ): PermissionResult {
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
        return PermissionResult(granted = granted, denied = denied, permanentlyDenied = permanentlyDenied)
    }

    private fun resolveLaunchStrategy(strategy: AppSettingsLaunchStrategy): AppSettingsLaunchStrategy =
        when (strategy) {
            AppSettingsLaunchStrategy.AUTO ->
                if (PermissionDetector.isProblematicRom()) {
                    AppSettingsLaunchStrategy.OEM_FIRST
                } else {
                    AppSettingsLaunchStrategy.STANDARD_FIRST
                }
            AppSettingsLaunchStrategy.OEM_FIRST,
            AppSettingsLaunchStrategy.STANDARD_FIRST -> strategy
        }

    private fun ensureNewTaskForNonActivity(context: Context, intent: Intent) {
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun buildStandardAppDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            ensureNewTaskForNonActivity(context, this)
        }

    private fun buildOemAppSettingsIntents(context: Context): List<Intent> =
        appSettingsIntentTemplates.map { template ->
            Intent(template).apply {
                if (template.hasExtra("extra_pkgname")) {
                    putExtra("extra_pkgname", context.packageName)
                }
                ensureNewTaskForNonActivity(context, this)
            }
        }

    private fun intentDedupKey(intent: Intent): String {
        val c = intent.component
        return "${intent.action}|${intent.dataString}|${c?.packageName}|${c?.className}"
    }

    private fun buildAppSettingsIntents(context: Context, strategy: AppSettingsLaunchStrategy): List<Intent> {
        val resolved = resolveLaunchStrategy(strategy)
        val standard = buildStandardAppDetailsIntent(context)
        val oem = buildOemAppSettingsIntents(context)
        val merged = when (resolved) {
            AppSettingsLaunchStrategy.OEM_FIRST -> oem + standard
            AppSettingsLaunchStrategy.STANDARD_FIRST -> listOf(standard) + oem
            AppSettingsLaunchStrategy.AUTO -> error("resolved strategy must not be AUTO")
        }
        return merged.distinctBy { intentDedupKey(it) }
    }

    private val appSettingsIntentTemplates: List<Intent> by lazy {
        buildList {
            add(Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.permissionmanager.ui.MainActivity"
                )
            })
            add(Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                putExtra("extra_pkgname", "")
            })
            add(Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.PermissionTopActivity"
                )
            })
            add(Intent().apply {
                component = ComponentName(
                    "com.oppo.safe",
                    "com.oppo.safe.permission.PermissionTopActivity"
                )
            })
            add(Intent().apply {
                component = ComponentName(
                    "com.vivo.abe.uniui",
                    "com.vivo.abe.uniui.UriPermissionActivity"
                )
            })
            add(Intent().apply {
                component = ComponentName(
                    "com.meizu.safe",
                    "com.meizu.safe.security.PermissionActivity"
                )
            })
        }
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
            log(LogLevel.DEBUG, "All permissions already granted: $alreadyGranted")
            return PermissionResult(granted = alreadyGranted, denied = emptyList(), permanentlyDenied = emptyList())
        }

        log(LogLevel.DEBUG, "Requesting permissions: needRequest=$needRequest, alreadyGranted=$alreadyGranted")
        PermissionHistory.recordRequested(activity, needRequest.toList())

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

    private fun normalizePermissionArgs(permissions: Array<out String>): Array<String> {
        require(permissions.isNotEmpty()) { "permissions must not be empty" }
        require(permissions.all { it.isNotBlank() }) { "permission names must not be blank" }
        return permissions.distinct().toTypedArray()
    }
}
