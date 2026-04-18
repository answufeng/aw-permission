package com.answufeng.permission

import android.Manifest
import android.os.Build

/**
 * 预定义的权限组常量，涵盖常见的 Android 权限集合。
 *
 * 配合展开运算符（`*`）使用，请求组内所有权限：
 * ```kotlin
 * val result = AwPermission.request(activity, *PermissionGroups.LOCATION)
 * ```
 *
 * ### 版本自适应函数（推荐使用）
 * ```kotlin
 * // 根据 API 级别自动选择正确的权限
 * val result = AwPermission.request(activity, *PermissionGroups.storage())
 * val result = AwPermission.request(activity, *PermissionGroups.location())
 * ```
 *
 * ### Android 版本说明
 * - [STORAGE] 在 Android 13+（API 33）已废弃。使用 [storage()] 或 [MEDIA_VISUAL] 和 [MEDIA_AUDIO]。
 * - [NOTIFICATIONS] 需要 Android 13+（API 33）。
 * - [NEARBY_DEVICES] 需要 Android 12+（API 31）。
 * - [MEDIA_VISUAL] 和 [MEDIA_AUDIO] 需要 Android 13+（API 33）。
 * - [MEDIA_PARTIAL] 需要 Android 14+（API 34）。
 * - [SENSORS_BACKGROUND] 需要 Android 13+（API 33）。
 * - [ACTIVITY_RECOGNITION] 需要 Android 10+（API 29）。
 * - [BACKGROUND_LOCATION] 需要 Android 10+（API 29）。
 * - [NEARBY_WIFI] 需要 Android 13+（API 33）。
 */
public object PermissionGroups {

    /** 相机权限。 */
    @JvmField
    public val CAMERA: Array<String> = arrayOf(Manifest.permission.CAMERA)

    /** 麦克风/录音权限。 */
    @JvmField
    public val MICROPHONE: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)

    /** 通讯录相关权限（读取、写入、账户）。 */
    @JvmField
    public val CONTACTS: Array<String> = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.GET_ACCOUNTS
    )

    /** 定位权限（精确定位和粗略定位）。 */
    @JvmField
    public val LOCATION: Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    /**
     * 存储权限（读取和写入外部存储）。
     *
     * 在 Android 13+（API 33）已废弃。使用 [storage()] 代替。
     */
    @JvmField
    @Suppress("DEPRECATION")
    public val STORAGE: Array<String> = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    /** 电话相关权限。 */
    @JvmField
    @Suppress("DEPRECATION")
    public val PHONE: Array<String> = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG,
        Manifest.permission.PROCESS_OUTGOING_CALLS
    )

    /** 身体传感器权限。 */
    @JvmField
    public val SENSORS: Array<String> = arrayOf(Manifest.permission.BODY_SENSORS)

    /** 短信相关权限。 */
    @JvmField
    public val SMS: Array<String> = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_WAP_PUSH,
        Manifest.permission.RECEIVE_MMS
    )

    /** 日历权限（读取和写入）。 */
    @JvmField
    public val CALENDAR: Array<String> = arrayOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )

    /**
     * Android 13+（API 33）媒体视觉权限（图片和视频）。
     *
     * 在 Android 13+ 上替代 [STORAGE]。
     */
    @JvmField
    public val MEDIA_VISUAL: Array<String> = arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO
    )

    /**
     * Android 13+（API 33）媒体音频权限。
     *
     * 在 Android 13+ 上替代 [STORAGE]。
     */
    @JvmField
    public val MEDIA_AUDIO: Array<String> = arrayOf(Manifest.permission.READ_MEDIA_AUDIO)

    /**
     * Android 13+（API 33）通知权限。
     */
    @JvmField
    public val NOTIFICATIONS: Array<String> = arrayOf(Manifest.permission.POST_NOTIFICATIONS)

    /**
     * Android 12+（API 31）蓝牙/附近设备权限。
     */
    @JvmField
    public val NEARBY_DEVICES: Array<String> = arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE
    )

    /** Android 10+（API 29）活动识别权限。 */
    @JvmField
    public val ACTIVITY_RECOGNITION: Array<String> = arrayOf(
        Manifest.permission.ACTIVITY_RECOGNITION
    )

    /** Android 10+（API 29）后台定位权限。 */
    @JvmField
    public val BACKGROUND_LOCATION: Array<String> = arrayOf(
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    )

    /** Android 13+（API 33）附近 WiFi 设备权限。 */
    @JvmField
    public val NEARBY_WIFI: Array<String> = arrayOf(
        Manifest.permission.NEARBY_WIFI_DEVICES
    )

    /**
     * Android 14+（API 34）部分媒体访问权限（用户选择特定照片/视频）。
     *
     * 注意：此权限的行为与其他权限不同，用户可以选择"选择照片和视频"实现部分授权。
     * 即使获得此权限，也不代表获得了完整的媒体访问权限。
     */
    @JvmField
    public val MEDIA_PARTIAL: Array<String> = arrayOf(
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )

    /** Android 13+（API 33）后台身体传感器权限。 */
    @JvmField
    public val SENSORS_BACKGROUND: Array<String> = arrayOf(
        Manifest.permission.BODY_SENSORS_BACKGROUND
    )

    private val cachedStorage: Array<String> by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            MEDIA_VISUAL + MEDIA_AUDIO
        } else {
            STORAGE
        }
    }

    private val cachedLocation: Array<String> by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            LOCATION
        }
    }

    /**
     * 版本自适应的存储权限。
     *
     * Android 13+（API 33）返回 [MEDIA_VISUAL] + [MEDIA_AUDIO]，
     * 更早版本返回 [STORAGE]。
     *
     * 这是请求存储/媒体权限的推荐方式。
     */
    public fun storage(): Array<String> = cachedStorage

    /**
     * 版本自适应的定位权限。
     *
     * Android 12+（API 31）只返回 `ACCESS_FINE_LOCATION`，
     * 因为系统会自动处理粗略定位的降级。
     * 更早版本返回 [LOCATION]（精确定位 + 粗略定位）。
     */
    public fun location(): Array<String> = cachedLocation
}
