package com.answufeng.permission

/**
 * 单次权限请求的结果。
 *
 * 包含三个列表，将每个请求的权限分类：
 * - [granted]：用户允许的权限
 * - [denied]：用户拒绝的权限（仍可再次请求）
 * - [permanentlyDenied]：用户选择"不再询问"而拒绝的权限
 *
 * ### 示例
 * ```kotlin
 * val result = AwPermission.request(activity, Manifest.permission.CAMERA)
 * when (result.status) {
 *     PermissionResult.Status.Granted -> openCamera()
 *     PermissionResult.Status.Denied -> showRetryDialog()
 *     PermissionResult.Status.PermanentlyDenied -> AwPermission.openAppSettings(context)
 * }
 * // 查询单个权限状态
 * if (result.isGranted(Manifest.permission.CAMERA)) { openCamera() }
 * if (result.isPermanentlyDenied(Manifest.permission.RECORD_AUDIO)) { goToSettings() }
 * ```
 *
 * @param granted 已授权的权限名称列表
 * @param denied 被拒绝的权限名称列表（用户选择了"拒绝"）
 * @param permanentlyDenied 被永久拒绝的权限名称列表（用户选择了"不再询问"）
 *
 * ### [isEmpty] 与 [status] / [isAllGranted] 的语义
 * 若 [granted]、[denied]、[permanentlyDenied] 均为空，则 [isEmpty] 为 `true`；此时
 * [isAllGranted] 为 `false`（表示无分项），而 [status] 为 [Status.Granted]（历史行为：空结果不落入 Denied/PermanentlyDenied 分支）。
 * 请优先用 [isAllGranted] 判断业务是否可继续，或先判断 [isEmpty] 再处理。
 */
public data class PermissionResult(
    public val granted: List<String>,
    public val denied: List<String>,
    public val permanentlyDenied: List<String>
) {

    private val grantedSet: Set<String> by lazy { granted.toSet() }
    private val deniedSet: Set<String> by lazy { denied.toSet() }
    private val permanentlyDeniedSet: Set<String> by lazy { permanentlyDenied.toSet() }

    /**
     * 结果是否为空（三个列表全为空）。通常只在异常或边界路径中出现；若三表全空，[isAllGranted] 为 `false` 而 [status] 仍为 [Status.Granted]。
     */
    public val isEmpty: Boolean get() = granted.isEmpty() && denied.isEmpty() && permanentlyDenied.isEmpty()

    /**
     * 是否所有「请求涉及的」权限均已授权。三表全空时返回 `false`（与 [status] 需配合 [isEmpty] 解读）。
     */
    public val isAllGranted: Boolean get() = !isEmpty && denied.isEmpty() && permanentlyDenied.isEmpty()

    /** 是否存在被永久拒绝的权限（"不再询问"）。 */
    public val hasPermanentlyDenied: Boolean get() = permanentlyDenied.isNotEmpty()

    /** 是否存在被拒绝的权限（非永久）。 */
    public val hasDenied: Boolean get() = denied.isNotEmpty()

    /** 已授权的权限数量。 */
    public val grantedCount: Int get() = granted.size

    /** 被拒绝的权限数量（非永久）。 */
    public val deniedCount: Int get() = denied.size

    /** 被永久拒绝的权限数量。 */
    public val permanentlyDeniedCount: Int get() = permanentlyDenied.size

    /**
     * 权限请求的整体状态。
     *
     * 优先级：[Status.PermanentlyDenied] > [Status.Denied] > [Status.Granted]。
     * 如果有任何权限被永久拒绝，状态为 [Status.PermanentlyDenied]。
     * 当 [isEmpty] 为 `true` 时，本属性为 [Status.Granted]（历史兼容）；与 [isAllGranted] 不同，见类说明。
     */
    public val status: Status
        get() = when {
            isEmpty -> Status.Granted
            hasPermanentlyDenied -> Status.PermanentlyDenied
            hasDenied -> Status.Denied
            else -> Status.Granted
        }

    /** 第一个被拒绝的权限，若没有则为 `null`。 */
    public val firstDenied: String? get() = denied.firstOrNull()

    /** 第一个被永久拒绝的权限，若没有则为 `null`。 */
    public val firstPermanentlyDenied: String? get() = permanentlyDenied.firstOrNull()

    /** 所有被拒绝的权限（denied + permanentlyDenied）。 */
    public val allDenied: List<String> get() = denied + permanentlyDenied

    /** 检查指定权限是否已授权。 */
    public fun isGranted(permission: String): Boolean = permission in grantedSet

    /** 检查指定权限是否被拒绝（非永久）。 */
    public fun isDenied(permission: String): Boolean = permission in deniedSet

    /** 检查指定权限是否被永久拒绝。 */
    public fun isPermanentlyDenied(permission: String): Boolean = permission in permanentlyDeniedSet

    override fun toString(): String {
        return "PermissionResult(granted=$granted, denied=$denied, permanentlyDenied=$permanentlyDenied, status=$status)"
    }

    /**
     * 权限请求的整体状态。
     */
    public sealed interface Status {
        /** 所有请求的权限均已授权。 */
        public data object Granted : Status {
            override fun toString(): String = "Granted"
        }

        /** 部分权限被拒绝，但仍可再次请求。 */
        public data object Denied : Status {
            override fun toString(): String = "Denied"
        }

        /** 部分权限被永久拒绝（"不再询问"）。 */
        public data object PermanentlyDenied : Status {
            override fun toString(): String = "PermanentlyDenied"
        }
    }
}
