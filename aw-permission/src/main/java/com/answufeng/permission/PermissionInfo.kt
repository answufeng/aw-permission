package com.answufeng.permission

import android.Manifest

/**
 * 权限说明辅助工具，提供权限对应的中文说明、权限组归属和风险等级。
 *
 * 方便开发者在 rationale 对话框中展示用户友好的权限说明。
 *
 * ### 示例
 * ```kotlin
 * val info = PermissionInfo.getInfo(Manifest.permission.CAMERA)
 * // info.label = "相机"
 * // info.description = "用于拍照和录像"
 * // info.riskLevel = RiskLevel.DANGEROUS
 *
 * // 在 rationale 对话框中使用
 * val result = AwPermission.requestWithRationale(activity, Manifest.permission.CAMERA) { permissions ->
 *     val descriptions = permissions.mapNotNull { PermissionInfo.getInfo(it)?.description }
 *     showRationaleDialog("需要以下权限：\n${descriptions.joinToString("\n")}")
 * }
 * ```
 */
public object PermissionInfo {

    /**
     * 权限风险等级。
     */
    public enum class RiskLevel {
        /** 普通权限，系统自动授予。 */
        NORMAL,
        /** 危险权限，需要用户明确授权。 */
        DANGEROUS
    }

    /**
     * 权限信息。
     *
     * @property permission 权限名称
     * @property label 权限的中文简短标签
     * @property description 权限的中文说明
     * @property riskLevel 风险等级
     */
    public data class Info(
        public val permission: String,
        public val label: String,
        public val description: String,
        public val riskLevel: RiskLevel
    )

    private val infoMap: Map<String, Info> = buildMap {
        put(Manifest.permission.CAMERA, Info(Manifest.permission.CAMERA, "相机", "用于拍照和录像", RiskLevel.DANGEROUS))
        put(Manifest.permission.RECORD_AUDIO, Info(Manifest.permission.RECORD_AUDIO, "麦克风", "用于录音和语音识别", RiskLevel.DANGEROUS))
        put(Manifest.permission.ACCESS_FINE_LOCATION, Info(Manifest.permission.ACCESS_FINE_LOCATION, "精确定位", "用于获取您的精确位置信息", RiskLevel.DANGEROUS))
        put(Manifest.permission.ACCESS_COARSE_LOCATION, Info(Manifest.permission.ACCESS_COARSE_LOCATION, "粗略定位", "用于获取您的大致位置信息", RiskLevel.DANGEROUS))
        put(Manifest.permission.ACCESS_BACKGROUND_LOCATION, Info(Manifest.permission.ACCESS_BACKGROUND_LOCATION, "后台定位", "用于在后台获取您的位置信息", RiskLevel.DANGEROUS))
        put(Manifest.permission.READ_CONTACTS, Info(Manifest.permission.READ_CONTACTS, "读取通讯录", "用于读取您的联系人信息", RiskLevel.DANGEROUS))
        put(Manifest.permission.WRITE_CONTACTS, Info(Manifest.permission.WRITE_CONTACTS, "写入通讯录", "用于修改您的联系人信息", RiskLevel.DANGEROUS))
        put(Manifest.permission.GET_ACCOUNTS, Info(Manifest.permission.GET_ACCOUNTS, "获取账户", "用于获取设备上的账户列表", RiskLevel.DANGEROUS))
        put(Manifest.permission.READ_CALENDAR, Info(Manifest.permission.READ_CALENDAR, "读取日历", "用于读取您的日历事件", RiskLevel.DANGEROUS))
        put(Manifest.permission.WRITE_CALENDAR, Info(Manifest.permission.WRITE_CALENDAR, "写入日历", "用于创建和修改日历事件", RiskLevel.DANGEROUS))
        put(Manifest.permission.READ_CALL_LOG, Info(Manifest.permission.READ_CALL_LOG, "读取通话记录", "用于读取您的通话历史", RiskLevel.DANGEROUS))
        put(Manifest.permission.WRITE_CALL_LOG, Info(Manifest.permission.WRITE_CALL_LOG, "写入通话记录", "用于修改您的通话历史", RiskLevel.DANGEROUS))
        put(Manifest.permission.READ_PHONE_STATE, Info(Manifest.permission.READ_PHONE_STATE, "读取手机状态", "用于读取设备标识和通话状态", RiskLevel.DANGEROUS))
        put(Manifest.permission.CALL_PHONE, Info(Manifest.permission.CALL_PHONE, "拨打电话", "用于直接拨打电话号码", RiskLevel.DANGEROUS))
        put(Manifest.permission.SEND_SMS, Info(Manifest.permission.SEND_SMS, "发送短信", "用于发送短信消息", RiskLevel.DANGEROUS))
        put(Manifest.permission.RECEIVE_SMS, Info(Manifest.permission.RECEIVE_SMS, "接收短信", "用于接收短信消息", RiskLevel.DANGEROUS))
        put(Manifest.permission.READ_SMS, Info(Manifest.permission.READ_SMS, "读取短信", "用于读取您的短信内容", RiskLevel.DANGEROUS))
        put(Manifest.permission.BODY_SENSORS, Info(Manifest.permission.BODY_SENSORS, "身体传感器", "用于访问心率等身体传感器数据", RiskLevel.DANGEROUS))
        put(Manifest.permission.BODY_SENSORS_BACKGROUND, Info(Manifest.permission.BODY_SENSORS_BACKGROUND, "后台身体传感器", "用于在后台访问身体传感器数据", RiskLevel.DANGEROUS))
        put(Manifest.permission.ACTIVITY_RECOGNITION, Info(Manifest.permission.ACTIVITY_RECOGNITION, "活动识别", "用于识别您的身体活动（如步行、跑步）", RiskLevel.DANGEROUS))
        put(Manifest.permission.POST_NOTIFICATIONS, Info(Manifest.permission.POST_NOTIFICATIONS, "发送通知", "用于向您发送通知消息", RiskLevel.DANGEROUS))
        put(Manifest.permission.BLUETOOTH_CONNECT, Info(Manifest.permission.BLUETOOTH_CONNECT, "蓝牙连接", "用于连接蓝牙设备", RiskLevel.DANGEROUS))
        put(Manifest.permission.BLUETOOTH_SCAN, Info(Manifest.permission.BLUETOOTH_SCAN, "蓝牙扫描", "用于扫描附近的蓝牙设备", RiskLevel.DANGEROUS))
        put(Manifest.permission.BLUETOOTH_ADVERTISE, Info(Manifest.permission.BLUETOOTH_ADVERTISE, "蓝牙广播", "用于向附近设备广播蓝牙信号", RiskLevel.DANGEROUS))
        put(Manifest.permission.NEARBY_WIFI_DEVICES, Info(Manifest.permission.NEARBY_WIFI_DEVICES, "附近WiFi设备", "用于发现和连接附近的WiFi设备", RiskLevel.DANGEROUS))

        @Suppress("DEPRECATION")
        put(Manifest.permission.READ_EXTERNAL_STORAGE, Info(Manifest.permission.READ_EXTERNAL_STORAGE, "读取存储", "用于读取设备上的文件和媒体", RiskLevel.DANGEROUS))
        @Suppress("DEPRECATION")
        put(Manifest.permission.WRITE_EXTERNAL_STORAGE, Info(Manifest.permission.WRITE_EXTERNAL_STORAGE, "写入存储", "用于保存文件到设备存储", RiskLevel.DANGEROUS))
        put(Manifest.permission.READ_MEDIA_IMAGES, Info(Manifest.permission.READ_MEDIA_IMAGES, "读取图片", "用于读取设备上的图片", RiskLevel.DANGEROUS))
        put(Manifest.permission.READ_MEDIA_VIDEO, Info(Manifest.permission.READ_MEDIA_VIDEO, "读取视频", "用于读取设备上的视频", RiskLevel.DANGEROUS))
        put(Manifest.permission.READ_MEDIA_AUDIO, Info(Manifest.permission.READ_MEDIA_AUDIO, "读取音频", "用于读取设备上的音频文件", RiskLevel.DANGEROUS))
        put(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Info(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, "选择媒体文件", "用于访问您选择的特定照片和视频", RiskLevel.DANGEROUS))
    }

    /**
     * 获取权限的说明信息。
     *
     * @param permission 权限名称（如 `Manifest.permission.CAMERA`）
     * @return 权限信息，如果权限未知则返回 `null`
     */
    public fun getInfo(permission: String): Info? = infoMap[permission]

    /**
     * 获取权限的中文标签。
     *
     * @param permission 权限名称
     * @return 中文标签，如果权限未知则返回权限名称本身
     */
    public fun getLabel(permission: String): String = infoMap[permission]?.label ?: permission

    /**
     * 获取权限的中文说明。
     *
     * @param permission 权限名称
     * @return 中文说明，如果权限未知则返回默认文本
     */
    public fun getDescription(permission: String): String =
        infoMap[permission]?.description ?: "需要使用此权限以提供相关功能"

    /**
     * 批量获取权限的说明信息。
     *
     * @param permissions 权限名称列表
     * @return 权限信息列表（跳过未知权限）
     */
    public fun getInfos(vararg permissions: String): List<Info> {
        return permissions.mapNotNull { infoMap[it] }
    }
}
