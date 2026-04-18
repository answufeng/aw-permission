package com.answufeng.permission.demo

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvLog = findViewById(R.id.tvLog)

        AwPermission.setLogger { level, tag, msg ->
            when (level) {
                AwPermission.LogLevel.WARN -> Log.w(tag, msg)
                AwPermission.LogLevel.ERROR -> Log.e(tag, msg)
                else -> Log.d(tag, msg)
            }
        }

        findViewById<Button>(R.id.btnCamera).setOnClickListener { requestCamera() }
        findViewById<Button>(R.id.btnLocation).setOnClickListener { requestLocation() }
        findViewById<Button>(R.id.btnMultiple).setOnClickListener { requestMultiple() }
        findViewById<Button>(R.id.btnRationale).setOnClickListener { requestWithRationale() }
        findViewById<Button>(R.id.btnDsl).setOnClickListener { dslRequest() }
        findViewById<Button>(R.id.btnCheck).setOnClickListener { checkPermission() }
        findViewById<Button>(R.id.btnSettings).setOnClickListener { openSettings() }
        findViewById<Button>(R.id.btnSpecialFloat).setOnClickListener { checkSpecialFloatWindow() }
        findViewById<Button>(R.id.btnSpecialBattery).setOnClickListener { checkSpecialBattery() }
        findViewById<Button>(R.id.btnSpecialNotify).setOnClickListener { checkSpecialNotification() }
        findViewById<Button>(R.id.btnStorage).setOnClickListener { requestStorage() }
        findViewById<Button>(R.id.btnClearLog).setOnClickListener { clearLog() }

        lifecycleScope.launch {
            observePermissions(Manifest.permission.CAMERA).collect { result ->
                log("[Flow] 相机权限: 已授予=${result.granted}, 已拒绝=${result.denied}, 永久拒绝=${result.permanentlyDenied}")
            }
        }

        log("权限库初始化完成")
        log("点击按钮测试各项功能")
    }

    private fun log(msg: String) {
        tvLog.append("$msg\n")
        Log.d("AwPermissionDemo", msg)
    }

    private fun clearLog() {
        tvLog.text = "日志已清除\n"
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
        }
    }

    private fun requestLocation() {
        lifecycleScope.launch {
            log("开始请求定位权限...")
            val result = AwPermission.request(this@MainActivity, *PermissionGroups.location())
            log("定位权限: 全部授予=${result.isAllGranted}, 状态=${result.status}")
        }
    }

    private fun requestStorage() {
        lifecycleScope.launch {
            log("开始请求存储/媒体权限...")
            val result = AwPermission.request(this@MainActivity, *PermissionGroups.storage())
            log("存储权限: 全部授予=${result.isAllGranted}, 已授予=${result.granted}")
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
}
