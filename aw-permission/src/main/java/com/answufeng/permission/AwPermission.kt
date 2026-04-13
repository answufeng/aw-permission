package com.answufeng.permission

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * Android runtime permission request utility built on coroutines + hidden Fragment.
 *
 * ### Concurrency Safety
 * - All permission requests are serialized through a single [Mutex], preventing
 *   concurrent requests from overwriting each other's continuation.
 * - Each request creates an independent [PermissionFragment] instance that is
 *   automatically removed after the request completes.
 * - If the Activity is destroyed (e.g., configuration change), the suspended
 *   coroutine is cancelled automatically.
 *
 * ### Basic Usage (in Activity)
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
 * ### Multiple Permissions
 * ```kotlin
 * val result = AwPermission.request(
 *     activity,
 *     Manifest.permission.CAMERA,
 *     Manifest.permission.RECORD_AUDIO
 * )
 * ```
 *
 * ### Using Permission Groups
 * ```kotlin
 * val result = AwPermission.request(activity, *PermissionGroups.LOCATION)
 * ```
 *
 * ### Request with Rationale
 * ```kotlin
 * val result = AwPermission.requestWithRationale(
 *     activity,
 *     Manifest.permission.CAMERA,
 * ) { permissions ->
 *     showRationaleDialog(permissions) // suspend function returning Boolean
 * }
 * ```
 *
 * ### Check Permission
 * ```kotlin
 * if (AwPermission.isGranted(context, Manifest.permission.CAMERA)) {
 *     // Already granted
 * }
 * ```
 *
 * ### Open App Settings
 * ```kotlin
 * val success = AwPermission.openAppSettings(context)
 * ```
 */
public object AwPermission {

    internal val tagCounter: AtomicLong = AtomicLong(0)

    private val mutex = Mutex()

    /**
     * Checks whether a single permission has been granted.
     *
     * @param context Any [Context]
     * @param permission The permission name (e.g., `Manifest.permission.CAMERA`)
     * @return `true` if the permission is granted, `false` otherwise
     */
    public fun isGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks whether all specified permissions have been granted.
     *
     * @param context Any [Context]
     * @param permissions The permission names to check
     * @return `true` if all permissions are granted, `false` otherwise
     */
    public fun isAllGranted(context: Context, vararg permissions: String): Boolean {
        return permissions.all { isGranted(context, it) }
    }

    /**
     * Checks whether the system should show a permission rationale for a single permission.
     *
     * Returns `true` if the user has previously denied this permission (without selecting
     * "Don't ask again"), indicating that you should show a rationale before requesting again.
     *
     * @param activity The [FragmentActivity]
     * @param permission The permission name to check
     * @return `true` if rationale should be shown, `false` otherwise
     */
    public fun shouldShowRationale(activity: FragmentActivity, permission: String): Boolean {
        return activity.shouldShowRequestPermissionRationale(permission)
    }

    /**
     * Returns the list of permissions from the input that require a rationale explanation.
     *
     * A permission requires rationale if the user has previously denied it without selecting
     * "Don't ask again". Use this to determine which permissions need explanation before
     * requesting them again.
     *
     * @param activity The [FragmentActivity]
     * @param permissions The permission names to check
     * @return List of permissions that require rationale
     */
    public fun permissionsRequiringRationale(activity: FragmentActivity, vararg permissions: String): List<String> {
        return permissions.filter { activity.shouldShowRequestPermissionRationale(it) }
    }

    /**
     * Requests runtime permissions (suspend function that resumes when the user responds).
     *
     * Already-granted permissions are automatically skipped; only ungranted permissions
     * are presented to the user.
     *
     * @param activity The [FragmentActivity] to use for the request
     * @param permissions The permissions to request
     * @return [PermissionResult] containing the granted, denied, and permanently denied permissions
     * @throws IllegalArgumentException if [permissions] is empty or contains blank strings
     * @throws IllegalStateException if the Activity is finishing or destroyed
     */
    public suspend fun request(activity: FragmentActivity, vararg permissions: String): PermissionResult =
        mutex.withLock {
            require(permissions.isNotEmpty()) { "permissions must not be empty" }
            require(permissions.all { it.isNotBlank() }) { "permission names must not be blank" }
            checkActivityState(activity)

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
                return@withLock PermissionResult(granted = alreadyGranted, denied = emptyList(), permanentlyDenied = emptyList())
            }

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
                    val wasRationale = rationaleStateBefore[permission] == true
                    val isRationale = activity.shouldShowRequestPermissionRationale(permission)
                    when {
                        isRationale -> denied.add(permission)
                        wasRationale -> permanentlyDenied.add(permission)
                        else -> denied.add(permission)
                    }
                }
            }

            PermissionResult(
                granted = alreadyGranted + granted,
                denied = denied,
                permanentlyDenied = permanentlyDenied
            )
        }

    /**
     * Requests runtime permissions from a Fragment.
     *
     * Delegates to [request] using the Fragment's host Activity.
     *
     * @param fragment The [Fragment] to use for the request
     * @param permissions The permissions to request
     * @return [PermissionResult] containing the request outcome
     * @throws IllegalArgumentException if [permissions] is empty or contains blank strings
     * @throws IllegalStateException if the Activity is finishing or destroyed
     */
    public suspend fun request(fragment: Fragment, vararg permissions: String): PermissionResult {
        val activity = fragment.requireActivity()
        return request(activity, *permissions)
    }

    /**
     * Requests permissions with a rationale explanation.
     *
     * If any of the specified permissions require a rationale (i.e., the user has previously
     * denied them), the [rationale] suspend lambda is called with the list of permissions
     * needing explanation. Return `true` from the lambda to proceed with the request,
     * or `false` to cancel.
     *
     * @param activity The [FragmentActivity] to use for the request
     * @param permissions The permissions to request
     * @param rationale A suspend lambda that receives the permissions needing rationale.
     *                  Return `true` to proceed with the request, `false` to cancel.
     * @return [PermissionResult] if the request proceeded, or `null` if the user cancelled
     *         the rationale dialog
     * @throws IllegalArgumentException if [permissions] is empty or contains blank strings
     * @throws IllegalStateException if the Activity is finishing or destroyed
     */
    public suspend fun requestWithRationale(
        activity: FragmentActivity,
        vararg permissions: String,
        rationale: suspend (permissions: List<String>) -> Boolean
    ): PermissionResult? {
        val needRationale = permissions.filter {
            !isGranted(activity, it) && activity.shouldShowRequestPermissionRationale(it)
        }

        if (needRationale.isNotEmpty()) {
            val shouldProceed = rationale(needRationale)
            if (!shouldProceed) return null
        }

        return request(activity, *permissions)
    }

    /**
     * Opens the system application settings page for the current app.
     *
     * Use this to guide users to manually grant permissions that have been permanently denied.
     *
     * @param context Any [Context]
     * @return `true` if the settings page was successfully opened, `false` if no Activity
     *         could handle the Intent (rare, possible on custom ROMs)
     */
    public fun openAppSettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resolved = intent.resolveActivity(context.packageManager)
        return if (resolved != null) {
            context.startActivity(intent)
            true
        } else {
            false
        }
    }

    private fun checkActivityState(activity: FragmentActivity) {
        check(!activity.isFinishing) { "Activity is finishing, cannot request permissions" }
        check(!activity.isDestroyed) { "Activity is destroyed, cannot request permissions" }
    }
}
