package com.answufeng.permission

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

/**
 * DSL 风格权限请求配置。
 *
 * ```kotlin
 * val result = buildPermissionRequest {
 *     permission(Manifest.permission.CAMERA)
 *     permissionGroup(PermissionGroups.LOCATION)
 *     rationale { permissions -> showRationaleDialog(permissions) }
 * }
 * ```
 *
 * @property permissions 要请求的权限名称列表
 * @property rationale 可选的挂起 Lambda，用于在请求前展示权限理由说明
 */
public class PermissionRequest internal constructor(
    public val permissions: List<String>,
    public val rationale: (suspend (List<String>) -> Boolean)?
) {
    /**
     * DSL 风格的 [PermissionRequest] 构建器。
     */
    public class Builder {
        private val permissions = mutableListOf<String>()
        private var rationale: (suspend (List<String>) -> Boolean)? = null

        /** 添加单个权限。 */
        public fun permission(permission: String) {
            permissions.add(permission)
        }

        /** 添加多个权限。 */
        public fun permissions(vararg permissions: String) {
            this.permissions.addAll(permissions)
        }

        /** 添加权限组中的所有权限（如 [PermissionGroups.LOCATION]）。 */
        public fun permissionGroup(permissions: Array<String>) {
            this.permissions.addAll(permissions)
        }

        /** 添加集合中的所有权限。 */
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

        internal fun build(): PermissionRequest {
            require(permissions.isNotEmpty()) { "At least one permission is required" }
            return PermissionRequest(permissions.toList(), rationale)
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
        AwPermission.requestWithRationale(this, *request.permissions.toTypedArray(), rationale = request.rationale!!)
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
        AwPermission.requestWithRationale(this, *request.permissions.toTypedArray(), rationale = request.rationale!!)
    } else {
        AwPermission.request(this, *request.permissions.toTypedArray())
    }
}
