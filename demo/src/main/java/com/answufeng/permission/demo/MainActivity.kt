package com.answufeng.permission.demo

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
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
import com.google.android.material.card.MaterialCardView
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

        // 主布局
        val mainLayout = findViewById<LinearLayout>(R.id.mainLayout)

        // 标题
        mainLayout.addView(TextView(this).apply {
            text = "🔐 aw-permission 功能演示"
            textSize = 20f
            setPadding(0, 0, 0, 20)
        })

        // 基本权限卡片
        val basicCard = createCard("基本权限")
        val basicLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        basicLayout.addView(createButton("📷 相机权限", ::requestCamera))
        basicLayout.addView(createButton("📍 定位权限", ::requestLocation))
        basicLayout.addView(createButton("🎤 录音权限", ::requestRecordAudio))
        basicLayout.addView(createButton("📱 多个权限", ::requestMultiple))
        basicCard.addView(basicLayout)
        mainLayout.addView(basicCard)

        // 高级功能卡片
        val advancedCard = createCard("高级功能")
        val advancedLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        advancedLayout.addView(createButton("📝 带说明的权限", ::requestWithRationale))
        advancedLayout.addView(createButton("🎯 DSL 风格请求", ::dslRequest))
        advancedLayout.addView(createButton("🔍 检查权限", ::checkPermission))
        advancedLayout.addView(createButton("⚙️ 打开设置", ::openSettings))
        advancedCard.addView(advancedLayout)
        mainLayout.addView(advancedCard)

        // 管理功能卡片
        val manageCard = createCard("管理功能")
        val manageLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        manageLayout.addView(createButton("🗑️ 清除日志", ::clearLog))
        manageCard.addView(manageLayout)
        mainLayout.addView(manageCard)

        // 日志区域
        mainLayout.addView(TextView(this).apply {
            text = "操作日志："
            textSize = 16f
            setPadding(0, 20, 0, 10)
        })

        logScrollView = findViewById(R.id.logScrollView)
        tvLog = findViewById(R.id.tvLog)

        // 权限监听
        lifecycleScope.launch {
            observePermissions(Manifest.permission.CAMERA).collect { result ->
                log("[Flow] 相机权限: 已授予=${result.granted}, 已拒绝=${result.denied}, 永久拒绝=${result.permanentlyDenied}")
            }
        }

        log("✅ 权限库初始化完成")
        log("📊 点击按钮测试各项功能")
    }

    private fun createCard(title: String): MaterialCardView {
        return MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            setPadding(20, 20, 20, 20)

            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 16f
                setPadding(0, 0, 0, 12)
            })
        }
    }

    private fun createButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 4, 0, 4)
            }
            setOnClickListener { onClick() }
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

    private fun requestRecordAudio() {
        lifecycleScope.launch {
            log("🔄 开始请求录音权限...")
            val result = AwPermission.request(this@MainActivity, Manifest.permission.RECORD_AUDIO)
            log("🎤 录音权限: 已授予=${result.granted}, 已拒绝=${result.denied}, 永久拒绝=${result.permanentlyDenied}")
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
