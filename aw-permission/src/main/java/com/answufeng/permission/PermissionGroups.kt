package com.answufeng.permission

import android.Manifest

/**
 * Pre-defined permission group constants for common Android permission sets.
 *
 * Use these with the spread operator (`*`) to request all permissions in a group:
 * ```kotlin
 * val result = AwPermission.request(activity, *PermissionGroups.LOCATION)
 * ```
 *
 * ### Android Version Notes
 * - [STORAGE] is deprecated on Android 13+ (API 33). Use [MEDIA_VISUAL] and [MEDIA_AUDIO] instead.
 * - [NOTIFICATIONS] requires Android 13+ (API 33).
 * - [NEARBY_DEVICES] requires Android 12+ (API 31).
 * - [MEDIA_VISUAL] and [MEDIA_AUDIO] require Android 13+ (API 33).
 */
public object PermissionGroups {

    /** Camera permission. */
    @JvmField
    public val CAMERA: Array<String> = arrayOf(Manifest.permission.CAMERA)

    /** Microphone / audio recording permission. */
    @JvmField
    public val MICROPHONE: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)

    /** Contact-related permissions (read, write, accounts). */
    @JvmField
    public val CONTACTS: Array<String> = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.GET_ACCOUNTS
    )

    /** Location permissions (fine and coarse). */
    @JvmField
    public val LOCATION: Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    /**
     * Storage permissions (read and write external storage).
     *
     * Deprecated on Android 13+ (API 33). Use [MEDIA_VISUAL] and [MEDIA_AUDIO] instead.
     */
    @JvmField
    @Suppress("DEPRECATION")
    public val STORAGE: Array<String> = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    /** Phone-related permissions. */
    @JvmField
    @Suppress("DEPRECATION")
    public val PHONE: Array<String> = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG,
        Manifest.permission.PROCESS_OUTGOING_CALLS
    )

    /** Body sensors permission. */
    @JvmField
    public val SENSORS: Array<String> = arrayOf(Manifest.permission.BODY_SENSORS)

    /** SMS-related permissions. */
    @JvmField
    public val SMS: Array<String> = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_WAP_PUSH,
        Manifest.permission.RECEIVE_MMS
    )

    /** Calendar permissions (read and write). */
    @JvmField
    public val CALENDAR: Array<String> = arrayOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )

    /**
     * Android 13+ (API 33) media visual permissions (images and video).
     *
     * Replaces [STORAGE] on Android 13+.
     */
    @JvmField
    public val MEDIA_VISUAL: Array<String> = arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO
    )

    /**
     * Android 13+ (API 33) media audio permission.
     *
     * Replaces [STORAGE] on Android 13+.
     */
    @JvmField
    public val MEDIA_AUDIO: Array<String> = arrayOf(Manifest.permission.READ_MEDIA_AUDIO)

    /**
     * Android 13+ (API 33) notification permission.
     */
    @JvmField
    public val NOTIFICATIONS: Array<String> = arrayOf(Manifest.permission.POST_NOTIFICATIONS)

    /**
     * Android 12+ (API 31) Bluetooth / nearby device permissions.
     */
    @JvmField
    public val NEARBY_DEVICES: Array<String> = arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE
    )
}
