package com.answufeng.permission.demo

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.answufeng.permission.AwPermission
import com.answufeng.permission.PermissionGroups
import com.answufeng.permission.hasPermission
import com.answufeng.permission.observePermissions
import com.answufeng.permission.requestPermissions
import com.answufeng.permission.requestPermissionsResult
import com.answufeng.permission.requestPermissionsResultWithRationale
import com.answufeng.permission.requirePermissions
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * aw-permission 库功能演示
 * 包含：权限请求、权限检查、权限监听等功能
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView
    private lateinit var logScrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化视图
        tvLog = findViewById(R.id.tvLog)
        logScrollView = findViewById(R.id.logScrollView)

        // 基本权限请求
        findViewById<Button>(R.id.btnCamera).setOnClickListener { requestCamera() }
        findViewById<Button>(R.id.btnLocation).setOnClickListener { requestLocation() }
        findViewById<Button>(R.id.btnMultiple).setOnClickListener { requestMultiple() }

        // 高级功能
        findViewById<Button>(R.id.btnRationale).setOnClickListener { requestWithRationale() }
        findViewById<Button>(R.id.btnDsl).setOnClickListener { dslRequest() }
        findViewById<Button>(R.id.btnCheck).setOnClickListener { checkPermission() }
        findViewById<Button>(R.id.btnSettings).setOnClickListener { openSettings() }

        // 管理
        findViewById<Button>(R.id.btnClearLog).setOnClickListener { clearLog() }

        // 权限监听
        lifecycleScope.launch {
            observePermissions(Manifest.permission.CAMERA).collect { result ->
                log("[Flow] 相机权限: 已授予=${result.granted}, 已拒绝=${result.denied}, 永久拒绝=${result.permanentlyDenied}")
            }
        }
    }

    private fun log(msg: String) {
        tvLog.append("$msg\n")
        logScrollView.post { logScrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        Log.d("AwPermissionDemo", msg)
    }

    private fun clearLog() {
        tvLog.text = "日志已清除\n"
    }

    private fun requestCamera() {
        lifecycleScope.launch {
            log("🔄 开始请求相机权限...")
            val result = AwPermission.request(this@MainActivity, Manifest.permission.CAMERA)
            log("📷 相机权限: 已授予=${result.granted}, 已拒绝=${result.denied}, 永久拒绝=${result.permanentlyDenied}")
            if (result.isGranted(Manifest.permission.CAMERA)) {
                log("✅ 相机权限已授予！")
            }
            if (result.hasPermanentlyDenied) {
                log("❌ 相机权限已被永久拒绝，请去设置中开启")
            }
        }
    }

    private fun requestLocation() {
        lifecycleScope.launch {
            log("🔄 开始请求定位权限...")
            val result = AwPermission.request(
                this@MainActivity,
                *PermissionGroups.location()
            )
            log("📍 定位权限: 全部授予=${result.isAllGranted}, 状态=${result.status}")
        }
    }

    private fun requestMultiple() {
        log("🔄 开始请求多个权限...")
        requirePermissions(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            onGranted = { log("✅ 所有权限已授予！") },
            onDenied = { result ->
                log("❌ 权限被拒绝: 已授予=${result.granted}, 已拒绝=${result.denied}")
                if (result.hasPermanentlyDenied) {
                    log("❌ 永久拒绝: ${result.permanentlyDenied}")
                    AwPermission.openAppSettings(this@MainActivity)
                }
            }
        )
    }

    private fun requestWithRationale() {
        lifecycleScope.launch {
            log("🔄 开始请求带说明的权限...")
            val result = requestPermissionsResultWithRationale(
                Manifest.permission.CAMERA,
            ) { permissions ->
                showRationaleDialog(permissions)
            }
            if (result != null) {
                log("📝 说明结果: 全部授予=${result.isAllGranted}")
            } else {
                log("❌ 用户取消了说明对话框")
            }
        }
    }

    private fun dslRequest() {
        lifecycleScope.launch {
            log("🔄 开始 DSL 风格权限请求...")
            val result = requestPermissions {
                permission(Manifest.permission.CAMERA)
                permissionGroup(PermissionGroups.LOCATION)
                rationale { permissions ->
                    showRationaleDialog(permissions)
                }
            }
            if (result != null) {
                log("📝 DSL 结果: 全部授予=${result.isAllGranted}, 状态=${result.status}")
                if (result.isGranted(Manifest.permission.CAMERA)) {
                    log("  📷 相机: 已授予")
                }
                if (result.isPermanentlyDenied(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    log("  📍 定位: 永久拒绝")
                }
            } else {
                log("❌ DSL: 用户取消了说明")
            }
        }
    }

    private fun checkPermission() {
        val hasCamera = hasPermission(Manifest.permission.CAMERA)
        log("🔍 相机权限状态: $hasCamera")

        lifecycleScope.launch {
            val result = requestPermissionsResult(Manifest.permission.CAMERA)
            log("🔍 挂起结果: ${result.status}")
        }
    }

    private fun openSettings() {
        val success = AwPermission.openAppSettings(this)
        log("⚙️ 打开设置页面: $success")
    }

    private suspend fun showRationaleDialog(permissions: List<String>): Boolean {
        return suspendCancellableCoroutine { cont ->
            val dialog = AlertDialog.Builder(this)
                .setTitle("需要权限")
                .setMessage("需要以下权限: ${permissions.joinToString()}\n\n是否继续？")
                .setPositiveButton("允许") { _, _ -> cont.resume(true) }
                .setNegativeButton("拒绝") { _, _ -> cont.resume(false) }
                .setOnCancelListener { cont.resume(false) }
                .show()
            cont.invokeOnCancellation { dialog.dismiss() }
        }
    }
}
