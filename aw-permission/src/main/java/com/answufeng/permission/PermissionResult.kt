package com.answufeng.permission

/**
 * 单次权限请求的结果。
 *
 * @param granted 已授予的权限列表
 * @param denied 被拒绝的权限列表（用户选择了「拒绝」）
 * @param permanentlyDenied 被永久拒绝的权限列表（用户选择了「不再询问」）
 */
data class PermissionResult(
    val granted: List<String>,
    val denied: List<String>,
    val permanentlyDenied: List<String>
) {
    /** 是否全部权限已授予 */
    val isAllGranted: Boolean get() = denied.isEmpty() && permanentlyDenied.isEmpty()

    /** 是否有被永久拒绝的权限 */
    val hasPermanentlyDenied: Boolean get() = permanentlyDenied.isNotEmpty()
}
