package com.answufeng.permission

/**
 * The result of a single permission request.
 *
 * Contains three lists categorizing each requested permission:
 * - [granted]: permissions the user allowed
 * - [denied]: permissions the user denied (can still request again)
 * - [permanentlyDenied]: permissions the user denied with "Don't ask again"
 *
 * ### Example
 * ```kotlin
 * val result = AwPermission.request(activity, Manifest.permission.CAMERA)
 * when (result.status) {
 *     PermissionResult.Status.Granted -> openCamera()
 *     PermissionResult.Status.Denied -> showRetryDialog()
 *     PermissionResult.Status.PermanentlyDenied -> AwPermission.openAppSettings(context)
 * }
 * ```
 *
 * @param granted List of granted permission names
 * @param denied List of denied permission names (user selected "Deny")
 * @param permanentlyDenied List of permanently denied permission names (user selected "Don't ask again")
 */
public data class PermissionResult(
    public val granted: List<String>,
    public val denied: List<String>,
    public val permanentlyDenied: List<String>
) {

    /** Whether all requested permissions were granted. */
    public val isAllGranted: Boolean get() = denied.isEmpty() && permanentlyDenied.isEmpty()

    /** Whether any permission was permanently denied ("Don't ask again"). */
    public val hasPermanentlyDenied: Boolean get() = permanentlyDenied.isNotEmpty()

    /** Whether any permission was denied (but not permanently). */
    public val hasDenied: Boolean get() = denied.isNotEmpty()

    /**
     * The overall status of the permission request.
     *
     * Priority: [Status.PermanentlyDenied] > [Status.Denied] > [Status.Granted].
     * If any permission is permanently denied, the status is [Status.PermanentlyDenied].
     */
    public val status: Status
        get() = when {
            isAllGranted -> Status.Granted
            hasPermanentlyDenied -> Status.PermanentlyDenied
            else -> Status.Denied
        }

    /** The first denied permission, or `null` if none were denied. */
    public val firstDenied: String? get() = denied.firstOrNull()

    /** The first permanently denied permission, or `null` if none were permanently denied. */
    public val firstPermanentlyDenied: String? get() = permanentlyDenied.firstOrNull()

    override fun toString(): String {
        return "PermissionResult(granted=$granted, denied=$denied, permanentlyDenied=$permanentlyDenied, status=$status)"
    }

    /**
     * The overall status of a permission request.
     */
    public sealed interface Status {
        /** All requested permissions were granted. */
        public data object Granted : Status {
            override fun toString(): String = "Granted"
        }

        /** Some permissions were denied but can be requested again. */
        public data object Denied : Status {
            override fun toString(): String = "Denied"
        }

        /** Some permissions were permanently denied ("Don't ask again"). */
        public data object PermanentlyDenied : Status {
            override fun toString(): String = "PermanentlyDenied"
        }
    }
}
