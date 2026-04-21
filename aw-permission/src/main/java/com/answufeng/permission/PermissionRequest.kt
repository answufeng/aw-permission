package com.answufeng.permission

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

@DslMarker
annotation class AwPermissionDsl

/**
 * DSL 风格权限请求配置。
 *
 * ```kotlin
 * val result = buildPermissionRequest {
 *     permission(Manifest.permission.CAMERA)
 *     permissionGroup(PermissionGroups.LOCATION)
 *     strategy(RationaleStrategy.OnDenied)
 *     rationale { permissions -> showRationaleDialog(permissions) }
 * }
 * ```
 *
 * @property permissions 要请求的权限名称列表
 * @property rationale 可选的挂起 Lambda，用于在请求前展示权限理由说明
 * @property strategy rationale 触发策略，默认 [RationaleStrategy.OnShouldShow]
 */
public class PermissionRequest internal constructor(
    public val permissions: List<String>,
    public val rationale: (suspend (List<String>) -> Boolean)?,
    public val strategy: RationaleStrategy
) {
    /**
     * DSL 风格的 [PermissionRequest] 构建器。
     */
    @AwPermissionDsl
    public class Builder {
        private val permissions = mutableListOf<String>()
        private var rationale: (suspend (List<String>) -> Boolean)? = null
        private var strategy: RationaleStrategy = RationaleStrategy.OnShouldShow

        /**
         * 添加单个权限。
         *
         * @param permission 权限名称（如 `Manifest.permission.CAMERA`）
         */
        public fun permission(permission: String) {
            permissions.add(permission)
        }

        /**
         * 添加多个权限。
         *
         * @param permissions 权限名称列表
         */
        public fun permissions(vararg permissions: String) {
            this.permissions.addAll(permissions)
        }

        /**
         * 添加权限组中的所有权限（如 [PermissionGroups.LOCATION]）。
         *
         * @param permissions 权限数组
         */
        public fun permissionGroup(permissions: Array<String>) {
            this.permissions.addAll(permissions)
        }

        /**
         * 添加集合中的所有权限。
         *
         * @param permissions 权限集合
         */
        public fun permissions(permissions: Collection<String>) {
            this.permissions.addAll(permissions)
        }

        /**
         * 设置权限理由说明回调。
         *
         * 在请求前被调用，传入需要理由说明的权限列表。
         * 返回 `true` 继续请求，返回 `false` 取消。
         */
        public fun rationale(block: suspend (List<String>) -> Boolean) {
            rationale = block
        }

        /**
         * 设置 rationale 触发策略，默认 [RationaleStrategy.OnShouldShow]。
         *
         * 仅在设置了 [rationale] 回调时生效。
         */
        public fun strategy(strategy: RationaleStrategy) {
            this.strategy = strategy
        }

        internal fun build(): PermissionRequest {
            require(permissions.isNotEmpty()) { "At least one permission is required" }
            return PermissionRequest(permissions.toList(), rationale, strategy)
        }
    }
}

/**
 * DSL 风格权限请求（[FragmentActivity] 扩展）。
 *
 * ```kotlin
 * val result = buildPermissionRequest {
 *     permission(Manifest.permission.CAMERA)
 *     permissionGroup(PermissionGroups.LOCATION)
 *     strategy(RationaleStrategy.OnDenied)
 *     rationale { permissions -> showRationaleDialog(permissions) }
 * }
 * ```
 *
 * @receiver [FragmentActivity]
 * @param block 应用于 [PermissionRequest.Builder] 的配置块
 * @return 请求继续则返回 [PermissionResult]，用户取消理由说明则返回 `null`
 */
public suspend fun FragmentActivity.buildPermissionRequest(
    block: PermissionRequest.Builder.() -> Unit
): PermissionResult? {
    val request = PermissionRequest.Builder().apply(block).build()
    return if (request.rationale != null) {
        AwPermission.requestWithRationale(this, *request.permissions.toTypedArray(), strategy = request.strategy, rationale = request.rationale!!)
    } else {
        AwPermission.request(this, *request.permissions.toTypedArray())
    }
}

/**
 * DSL 风格权限请求（[Fragment] 扩展）。
 *
 * @receiver [Fragment]
 * @param block 应用于 [PermissionRequest.Builder] 的配置块
 * @return 请求继续则返回 [PermissionResult]，用户取消理由说明则返回 `null`
 */
public suspend fun Fragment.buildPermissionRequest(
    block: PermissionRequest.Builder.() -> Unit
): PermissionResult? {
    val request = PermissionRequest.Builder().apply(block).build()
    return if (request.rationale != null) {
        AwPermission.requestWithRationale(this, *request.permissions.toTypedArray(), strategy = request.strategy, rationale = request.rationale!!)
    } else {
        AwPermission.request(this, *request.permissions.toTypedArray())
    }
}
