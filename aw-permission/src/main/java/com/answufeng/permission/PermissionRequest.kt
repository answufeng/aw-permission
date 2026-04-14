package com.answufeng.permission

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

/**
 * A permission request configuration used by the DSL-style [requestPermissions] API.
 *
 * ```kotlin
 * val result = requestPermissions {
 *     permission(Manifest.permission.CAMERA)
 *     permissionGroup(PermissionGroups.LOCATION)
 *     rationale { permissions -> showRationaleDialog(permissions) }
 * }
 * ```
 *
 * @property permissions The list of permission names to request.
 * @property rationale An optional suspend lambda to show rationale for permissions that need it.
 */
public class PermissionRequest internal constructor(
    public val permissions: List<String>,
    public val rationale: (suspend (List<String>) -> Boolean)?
) {
    /**
     * Builder for constructing a [PermissionRequest] using a DSL-style API.
     */
    public class Builder {
        private val permissions = mutableListOf<String>()
        private var rationale: (suspend (List<String>) -> Boolean)? = null

        /** Adds a single permission to the request. */
        public fun permission(permission: String) {
            permissions.add(permission)
        }

        /** Adds multiple permissions to the request. */
        public fun permissions(vararg permissions: String) {
            this.permissions.addAll(permissions)
        }

        /** Adds all permissions from a permission group (e.g., [PermissionGroups.LOCATION]). */
        public fun permissionGroup(permissions: Array<String>) {
            this.permissions.addAll(permissions)
        }

        /** Adds all permissions from a collection. */
        public fun permissions(permissions: Collection<String>) {
            this.permissions.addAll(permissions)
        }

        /**
         * Sets the rationale callback.
         *
         * Called with the list of permissions that need rationale before the request proceeds.
         * Return `true` to continue with the request, `false` to cancel.
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
 * DSL-style permission request for [FragmentActivity].
 *
 * ```kotlin
 * val result = requestPermissions {
 *     permission(Manifest.permission.CAMERA)
 *     permissionGroup(PermissionGroups.LOCATION)
 *     rationale { permissions -> showRationaleDialog(permissions) }
 * }
 * ```
 *
 * @receiver [FragmentActivity]
 * @param block Configuration block applied to a [PermissionRequest.Builder]
 * @return [PermissionResult] if the request proceeded, `null` if the user cancelled the rationale
 */
public suspend fun FragmentActivity.requestPermissions(
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
 * DSL-style permission request for [Fragment].
 *
 * @receiver [Fragment]
 * @param block Configuration block applied to a [PermissionRequest.Builder]
 * @return [PermissionResult] if the request proceeded, `null` if the user cancelled the rationale
 */
public suspend fun Fragment.requestPermissions(
    block: PermissionRequest.Builder.() -> Unit
): PermissionResult? {
    val request = PermissionRequest.Builder().apply(block).build()
    return if (request.rationale != null) {
        AwPermission.requestWithRationale(this, *request.permissions.toTypedArray(), rationale = request.rationale!!)
    } else {
        AwPermission.request(this, *request.permissions.toTypedArray())
    }
}
