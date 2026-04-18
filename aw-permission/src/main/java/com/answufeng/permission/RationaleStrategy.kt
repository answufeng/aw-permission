package com.answufeng.permission

/**
 * 权限理由说明（rationale）的触发策略。
 *
 * 控制 [AwPermission.requestWithRationale] 中 rationale 回调的触发时机。
 */
public enum class RationaleStrategy {

    /**
     * 仅当系统建议展示理由时触发。
     *
     * 即 `shouldShowRequestPermissionRationale` 返回 `true` 时触发。
     * 这意味着用户之前拒绝过该权限（但未勾选"不再询问"）。
     *
     * 首次请求被拒绝后不会触发，因为首次前 `shouldShowRequestPermissionRationale` 返回 `false`。
     */
    OnShouldShow,

    /**
     * 任何未授权的权限都触发。
     *
     * 只要权限未授权就触发 rationale 回调，包括：
     * - 首次请求（从未交互过）
     * - 之前被拒绝
     * - 被永久拒绝
     *
     * 适用于希望在请求权限前总是向用户解释原因的场景。
     */
    OnDenied
}
