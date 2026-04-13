package com.answufeng.permission

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Checks whether a single permission has been granted.
 *
 * Delegates to [AwPermission.isGranted].
 *
 * @receiver Any [Context]
 * @param permission The permission name to check
 * @return `true` if the permission is granted
 */
public fun Context.hasPermission(permission: String): Boolean {
    return AwPermission.isGranted(this, permission)
}

/**
 * Checks whether all specified permissions have been granted.
 *
 * Delegates to [AwPermission.isAllGranted].
 *
 * @receiver Any [Context]
 * @param permissions The permission names to check
 * @return `true` if all permissions are granted
 */
public fun Context.hasPermissions(vararg permissions: String): Boolean {
    return AwPermission.isAllGranted(this, *permissions)
}

/**
 * Requests permissions asynchronously with a callback.
 *
 * @receiver [FragmentActivity]
 * @param permissions The permissions to request
 * @param callback Called with the [PermissionResult] when the request completes
 */
public fun FragmentActivity.requestPermissions(
    vararg permissions: String,
    callback: (PermissionResult) -> Unit
) {
    lifecycleScope.launch {
        val result = AwPermission.request(this@requestPermissions, *permissions)
        callback(result)
    }
}

/**
 * Requests permissions asynchronously with a callback.
 *
 * @receiver [Fragment]
 * @param permissions The permissions to request
 * @param callback Called with the [PermissionResult] when the request completes
 */
public fun Fragment.requestPermissions(
    vararg permissions: String,
    callback: (PermissionResult) -> Unit
) {
    lifecycleScope.launch {
        val result = AwPermission.request(this@requestPermissions, *permissions)
        callback(result)
    }
}

/**
 * Requests permissions and invokes the appropriate callback based on the result.
 *
 * @receiver [FragmentActivity]
 * @param permissions The permissions to request
 * @param onGranted Called when all permissions are granted
 * @param onDenied Called when any permission is denied (default does nothing)
 */
public fun FragmentActivity.requirePermissions(
    vararg permissions: String,
    onGranted: () -> Unit,
    onDenied: (PermissionResult) -> Unit = {}
) {
    lifecycleScope.launch {
        val result = AwPermission.request(this@requirePermissions, *permissions)
        if (result.isAllGranted) {
            onGranted()
        } else {
            onDenied(result)
        }
    }
}

/**
 * Requests permissions and invokes the appropriate callback based on the result.
 *
 * @receiver [Fragment]
 * @param permissions The permissions to request
 * @param onGranted Called when all permissions are granted
 * @param onDenied Called when any permission is denied (default does nothing)
 */
public fun Fragment.requirePermissions(
    vararg permissions: String,
    onGranted: () -> Unit,
    onDenied: (PermissionResult) -> Unit = {}
) {
    lifecycleScope.launch {
        val result = AwPermission.request(this@requirePermissions, *permissions)
        if (result.isAllGranted) {
            onGranted()
        } else {
            onDenied(result)
        }
    }
}

/**
 * Observes permission state as a [Flow] that emits on every Activity resume.
 *
 * Useful for detecting when a user returns from the system settings page after
 * granting or denying permissions. The Flow emits the current permission state
 * each time the Activity reaches the [Lifecycle.State.RESUMED] state.
 *
 * Note: The [PermissionResult] from this Flow does not distinguish between
 * denied and permanently denied permissions. Use [AwPermission.request] for
 * detailed classification.
 *
 * @receiver [FragmentActivity]
 * @param permissions The permissions to observe
 * @return A [Flow] that emits [PermissionResult] on each Activity resume
 */
public fun FragmentActivity.observePermissions(vararg permissions: String): Flow<PermissionResult> =
    callbackFlow {
        val job = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val activity = this@observePermissions
                val granted = permissions.filter { AwPermission.isGranted(activity, it) }
                val notGranted = permissions.filter { !AwPermission.isGranted(activity, it) }
                trySend(
                    PermissionResult(
                        granted = granted,
                        denied = notGranted,
                        permanentlyDenied = emptyList()
                    )
                )
            }
        }
        awaitClose { job.cancel() }
    }
