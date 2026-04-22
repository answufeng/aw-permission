package com.answufeng.permission.demo

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import androidx.lifecycle.lifecycleScope
import com.answufeng.permission.AwPermission
import com.answufeng.permission.PermissionGroups
import com.answufeng.permission.PermissionInfo
import com.answufeng.permission.RationaleStrategy
import com.answufeng.permission.SpecialPermission
import com.answufeng.permission.buildPermissionRequest
import com.answufeng.permission.hasPermission
import com.answufeng.permission.observePermissions
import com.answufeng.permission.openAppSettingsAndWait
import com.answufeng.permission.requestRuntimePermissions
import com.answufeng.permission.requestRuntimePermissionsWithRationale
import com.answufeng.permission.runWithPermissions
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MainActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView
    private lateinit var scrollLog: ScrollView

    private lateinit var chipCamera: Chip
    private lateinit var chipLocation: Chip
    private lateinit var chipMicrophone: Chip
    private lateinit var chipStorage: Chip
    private lateinit var chipNotifications: Chip
    private lateinit var chipContacts: Chip
    private lateinit var chipCalendar: Chip
    private lateinit var chipPhone: Chip
    private lateinit var chipSensors: Chip

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvLog = findViewById(R.id.tvLog)
        scrollLog = findViewById(R.id.scrollLog)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { openSettings() }
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_about -> {
                    showAbout()
                    true
                }
                R.id.action_scroll_log -> {
                    scrollToLogBottom()
                    true
                }
                else -> false
            }
        }

        AwPermission.setLogger { level, tag, msg ->
            when (level) {
                AwPermission.LogLevel.WARN -> Log.w(tag, msg)
                AwPermission.LogLevel.ERROR -> Log.e(tag, msg)
                else -> Log.d(tag, msg)
            }
        }

        bindChips()

        findViewById<MaterialButton>(R.id.btnRefreshAll).setOnClickListener { refreshAllStatus() }

        findViewById<Button>(R.id.btnCamera).setOnClickListener { requestCamera() }
        findViewById<Button>(R.id.btnLocation).setOnClickListener { requestLocation() }
        findViewById<Button>(R.id.btnStorage).setOnClickListener { requestStorage() }
        findViewById<Button>(R.id.btnMultiple).setOnClickListener { requestMultiple() }

        findViewById<MaterialButton>(R.id.btnRequestSelected).setOnClickListener { requestSelected() }
        findViewById<MaterialButton>(R.id.btnCheckSelected).setOnClickListener { checkSelected() }

        findViewById<Button>(R.id.btnRationale).setOnClickListener { requestWithRationale() }
        findViewById<Button>(R.id.btnDsl).setOnClickListener { dslRequest() }
        findViewById<Button>(R.id.btnCheck).setOnClickListener { checkPermission() }
        findViewById<Button>(R.id.btnSettings).setOnClickListener { openSettings() }
        findViewById<Button>(R.id.btnSpecialFloat).setOnClickListener { checkSpecialFloatWindow() }
        findViewById<Button>(R.id.btnSpecialBattery).setOnClickListener { checkSpecialBattery() }
        findViewById<Button>(R.id.btnSpecialNotify).setOnClickListener { checkSpecialNotification() }

        findViewById<MaterialButton>(R.id.btnCopyLog).setOnClickListener { copyLog() }
        findViewById<MaterialButton>(R.id.btnShareLog).setOnClickListener { shareLog() }
        findViewById<Button>(R.id.btnClearLog).setOnClickListener { clearLog() }

        lifecycleScope.launch {
            observePermissions(Manifest.permission.CAMERA).collect { result ->
                log("[Flow] 相机权限: 已授予=${result.granted}, 已拒绝=${result.denied}, 永久拒绝=${result.permanentlyDenied}")
            }
        }

        log("权限库初始化完成")
        log("提示：点击工具栏左侧图标可跳转设置页")
        refreshAllStatus()
    }

    private fun log(msg: String) {
        tvLog.append("$msg\n")
        Log.d("AwPermissionDemo", msg)
        scrollToLogBottom()
    }

    private fun clearLog() {
        tvLog.text = "日志已清除\n"
    }

    private fun scrollToLogBottom() {
        scrollLog.post { scrollLog.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun bindChips() {
        chipCamera = findViewById(R.id.chipCamera)
        chipLocation = findViewById(R.id.chipLocation)
        chipMicrophone = findViewById(R.id.chipMicrophone)
        chipStorage = findViewById(R.id.chipStorage)
        chipNotifications = findViewById(R.id.chipNotifications)
        chipContacts = findViewById(R.id.chipContacts)
        chipCalendar = findViewById(R.id.chipCalendar)
        chipPhone = findViewById(R.id.chipPhone)
        chipSensors = findViewById(R.id.chipSensors)
    }

    private fun refreshAllStatus() {
        updateChipStatus(chipCamera, "相机", arrayOf(Manifest.permission.CAMERA))
        updateChipStatus(chipLocation, "定位", PermissionGroups.location())
        updateChipStatus(chipMicrophone, "录音", PermissionGroups.MICROPHONE)
        updateChipStatus(chipStorage, "存储/媒体", PermissionGroups.storage())
        updateChipStatus(chipContacts, "通讯录", PermissionGroups.CONTACTS)
        updateChipStatus(chipCalendar, "日历", PermissionGroups.CALENDAR)
        updateChipStatus(chipPhone, "电话", PermissionGroups.PHONE)
        updateChipStatus(chipSensors, "传感器", PermissionGroups.SENSORS)
        updateChipStatus(
            chipNotifications,
            "通知",
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) PermissionGroups.NOTIFICATIONS else emptyArray()
        )
        log("已刷新权限状态（基于当前系统授权）")
    }

    private fun updateChipStatus(chip: Chip, label: String, permissions: Array<String>) {
        if (permissions.isEmpty()) {
            chip.text = "$label · 不适用"
            chip.isChecked = false
            return
        }

        val grantedAll = permissions.all { hasPermission(it) }
        chip.text = if (grantedAll) "$label · 已授权" else "$label · 未授权"
        chip.isChecked = grantedAll
    }

    private fun selectedPermissionGroups(): List<Array<String>> {
        val groups = mutableListOf<Array<String>>()
        if (chipCamera.isChecked) groups += PermissionGroups.CAMERA
        if (chipLocation.isChecked) groups += PermissionGroups.location()
        if (chipMicrophone.isChecked) groups += PermissionGroups.MICROPHONE
        if (chipStorage.isChecked) groups += PermissionGroups.storage()
        if (chipContacts.isChecked) groups += PermissionGroups.CONTACTS
        if (chipCalendar.isChecked) groups += PermissionGroups.CALENDAR
        if (chipPhone.isChecked) groups += PermissionGroups.PHONE
        if (chipSensors.isChecked) groups += PermissionGroups.SENSORS
        if (chipNotifications.isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            groups += PermissionGroups.NOTIFICATIONS
        }
        return groups
    }

    private fun requestSelected() {
        val permissions = selectedPermissionGroups().flatMap { it.toList() }.distinct().toTypedArray()
        if (permissions.isEmpty()) {
            log("未选择任何权限（Chip 可点选）")
            return
        }
        lifecycleScope.launch {
            log("开始请求已选权限（共 ${permissions.size} 项）...")
            val result = AwPermission.request(this@MainActivity, *permissions)
            log("已选权限结果: 全部授予=${result.isAllGranted}, 状态=${result.status}")
            refreshAllStatus()
        }
    }

    private fun checkSelected() {
        val permissions = selectedPermissionGroups().flatMap { it.toList() }.distinct()
        if (permissions.isEmpty()) {
            log("未选择任何权限（Chip 可点选）")
            return
        }
        val granted = permissions.count { hasPermission(it) }
        log("已选权限检查: $granted/${permissions.size} 已授予")
        refreshAllStatus()
    }

    private fun requestCamera() {
        lifecycleScope.launch {
            log("开始请求相机权限...")
            val result = AwPermission.request(this@MainActivity, Manifest.permission.CAMERA)
            log("相机权限: 已授予=${result.granted}, 已拒绝=${result.denied}, 永久拒绝=${result.permanentlyDenied}")
            if (result.isGranted(Manifest.permission.CAMERA)) {
                log("相机权限已授予！")
            }
            if (result.hasPermanentlyDenied) {
                log("相机权限已被永久拒绝，请去设置中开启")
            }
            refreshAllStatus()
        }
    }

    private fun requestLocation() {
        lifecycleScope.launch {
            log("开始请求定位权限...")
            val result = AwPermission.request(this@MainActivity, *PermissionGroups.location())
            log("定位权限: 全部授予=${result.isAllGranted}, 状态=${result.status}")
            refreshAllStatus()
        }
    }

    private fun requestStorage() {
        lifecycleScope.launch {
            log("开始请求存储/媒体权限...")
            val result = AwPermission.request(this@MainActivity, *PermissionGroups.storage())
            log("存储权限: 全部授予=${result.isAllGranted}, 已授予=${result.granted}")
            refreshAllStatus()
        }
    }

    private fun requestMultiple() {
        log("开始请求多个权限...")
        runWithPermissions(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            onGranted = { log("所有权限已授予！") },
            onDenied = { result ->
                log("权限被拒绝: 已授予=${result.granted}, 已拒绝=${result.denied}")
                if (result.hasPermanentlyDenied) {
                    log("永久拒绝: ${result.permanentlyDenied}")
                    AwPermission.openAppSettings(this@MainActivity)
                }
            }
        )
    }

    private fun requestWithRationale() {
        lifecycleScope.launch {
            log("开始请求带说明的权限（OnDenied 策略）...")
            val result = requestRuntimePermissionsWithRationale(
                Manifest.permission.CAMERA,
                strategy = RationaleStrategy.OnDenied
            ) { permissions ->
                val descriptions = permissions.map {
                    val info = PermissionInfo.getInfo(it)
                    "${info?.label ?: it}: ${info?.description ?: ""}"
                }
                showRationaleDialog("需要以下权限：\n${descriptions.joinToString("\n")}")
            }
            if (result != null) {
                log("说明结果: 全部授予=${result.isAllGranted}")
            } else {
                log("用户取消了说明对话框")
            }
            refreshAllStatus()
        }
    }

    private fun dslRequest() {
        lifecycleScope.launch {
            log("开始 DSL 风格权限请求...")
            val result = buildPermissionRequest {
                permission(Manifest.permission.CAMERA)
                permissionGroup(PermissionGroups.LOCATION)
                strategy(RationaleStrategy.OnDenied)
                rationale { permissions ->
                    val descriptions = permissions.mapNotNull { PermissionInfo.getInfo(it)?.label }
                    showRationaleDialog("需要以下权限：${descriptions.joinToString("、")}")
                }
            }
            if (result != null) {
                log("DSL 结果: 全部授予=${result.isAllGranted}, 状态=${result.status}")
                if (result.isGranted(Manifest.permission.CAMERA)) {
                    log("  相机: 已授予")
                }
                if (result.isPermanentlyDenied(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    log("  定位: 永久拒绝")
                }
            } else {
                log("DSL: 用户取消了说明")
            }
            refreshAllStatus()
        }
    }

    private fun checkPermission() {
        val hasCamera = hasPermission(Manifest.permission.CAMERA)
        log("相机权限状态: $hasCamera")

        val info = PermissionInfo.getInfo(Manifest.permission.CAMERA)
        if (info != null) {
            log("权限信息: ${info.label} - ${info.description} (${info.riskLevel})")
        }

        lifecycleScope.launch {
            val result = requestRuntimePermissions(Manifest.permission.CAMERA)
            log("挂起结果: ${result.status}")
            refreshAllStatus()
        }
    }

    private fun openSettings() {
        lifecycleScope.launch {
            log("打开设置页面并等待返回...")
            val result = openAppSettingsAndWait(Manifest.permission.CAMERA)
            log("返回后权限状态: 已授予=${result.granted}, 已拒绝=${result.denied}, 永久拒绝=${result.permanentlyDenied}")
            if (result.isGranted(Manifest.permission.CAMERA)) {
                log("用户已在设置中授予相机权限！")
            }
            refreshAllStatus()
        }
    }

    private fun checkSpecialFloatWindow() {
        val granted = SpecialPermission.isGranted(this, SpecialPermission.PermissionType.FLOAT_WINDOW)
        log("悬浮窗权限状态: $granted")
        if (!granted) {
            val opened = SpecialPermission.openSettings(this, SpecialPermission.PermissionType.FLOAT_WINDOW)
            log("打开悬浮窗设置: ${if (opened) "成功" else "失败"}")
        }
    }

    private fun checkSpecialBattery() {
        val granted = SpecialPermission.isGranted(this, SpecialPermission.PermissionType.IGNORE_BATTERY_OPTIMIZATION)
        log("电池优化白名单状态: $granted")
        if (!granted) {
            val opened = SpecialPermission.openSettings(this, SpecialPermission.PermissionType.IGNORE_BATTERY_OPTIMIZATION)
            log("打开电池优化设置: ${if (opened) "成功" else "失败"}")
        }
    }

    private fun checkSpecialNotification() {
        val granted = SpecialPermission.isGranted(this, SpecialPermission.PermissionType.NOTIFICATION_LISTENER)
        log("通知栏权限状态: $granted")
        if (!granted) {
            val opened = SpecialPermission.openSettings(this, SpecialPermission.PermissionType.NOTIFICATION_LISTENER)
            log("打开通知栏设置: ${if (opened) "成功" else "失败"}")
        }
    }

    private suspend fun showRationaleDialog(message: String): Boolean {
        return suspendCancellableCoroutine { cont ->
            val dialog = AlertDialog.Builder(this)
                .setTitle("需要权限")
                .setMessage(message)
                .setPositiveButton("允许") { _, _ -> cont.resume(true) }
                .setNegativeButton("拒绝") { _, _ -> cont.resume(false) }
                .setOnCancelListener { cont.resume(false) }
                .show()
            cont.invokeOnCancellation { dialog.dismiss() }
        }
    }

    private fun copyLog() {
        val text = tvLog.text?.toString().orEmpty()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("AwPermission Log", text))
        log("日志已复制到剪贴板")
    }

    private fun shareLog() {
        val text = tvLog.text?.toString().orEmpty()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "AwPermission Demo Log")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching {
            startActivity(Intent.createChooser(intent, "分享日志"))
        }.onFailure {
            log("分享失败：${it.message}")
        }
    }

    private fun showAbout() {
        val msg = buildString {
            appendLine("AwPermission Demo")
            appendLine("compileSdk=35, minSdk=24")
            appendLine("支持：协程请求、Rationale、DSL、设置页等待、特殊权限引导")
            appendLine()
            appendLine("提示：可通过 Chip 选择权限后批量请求/检查")
        }
        AlertDialog.Builder(this)
            .setTitle("关于")
            .setMessage(msg)
            .setPositiveButton("知道了", null)
            .show()
    }
}
