package com.answufeng.permission.demo

import android.Manifest
import android.os.Bundle
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

class MainActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvLog = findViewById(R.id.tvLog)

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRequestCamera).setOnClickListener {
            requestCamera()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRequestLocation).setOnClickListener {
            requestLocation()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRequestMultiple).setOnClickListener {
            requestMultiple()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRequestWithRationale).setOnClickListener {
            requestWithRationale()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDslRequest).setOnClickListener {
            dslRequest()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCheckPermission).setOnClickListener {
            checkPermission()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnOpenSettings).setOnClickListener {
            openSettings()
        }

        lifecycleScope.launch {
            observePermissions(Manifest.permission.CAMERA).collect { result ->
                log("[Flow] Camera: granted=${result.granted}, denied=${result.denied}, permanentlyDenied=${result.permanentlyDenied}")
            }
        }
    }

    private fun requestCamera() {
        lifecycleScope.launch {
            val result = AwPermission.request(this@MainActivity, Manifest.permission.CAMERA)
            log("Camera: granted=${result.granted}, denied=${result.denied}, permanent=${result.permanentlyDenied}")
            if (result.isGranted(Manifest.permission.CAMERA)) {
                log("Camera is granted!")
            }
            if (result.hasPermanentlyDenied) {
                log("Camera permanently denied, please go to settings to enable")
            }
        }
    }

    private fun requestLocation() {
        lifecycleScope.launch {
            val result = AwPermission.request(
                this@MainActivity,
                *PermissionGroups.location()
            )
            log("Location: allGranted=${result.isAllGranted}, status=${result.status}")
        }
    }

    private fun requestMultiple() {
        requirePermissions(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            onGranted = { log("All permissions granted!") },
            onDenied = { result ->
                log("Denied: granted=${result.granted}, denied=${result.denied}")
                if (result.hasPermanentlyDenied) {
                    log("Permanently denied: ${result.permanentlyDenied}")
                    AwPermission.openAppSettings(this@MainActivity)
                }
            }
        )
    }

    private fun requestWithRationale() {
        lifecycleScope.launch {
            val result = requestPermissionsResultWithRationale(
                Manifest.permission.CAMERA,
            ) { permissions ->
                showRationaleDialog(permissions)
            }
            if (result != null) {
                log("Rationale result: allGranted=${result.isAllGranted}")
            } else {
                log("User cancelled rationale dialog")
            }
        }
    }

    private fun dslRequest() {
        lifecycleScope.launch {
            val result = requestPermissions {
                permission(Manifest.permission.CAMERA)
                permissionGroup(PermissionGroups.LOCATION)
                rationale { permissions ->
                    showRationaleDialog(permissions)
                }
            }
            if (result != null) {
                log("DSL result: allGranted=${result.isAllGranted}, status=${result.status}")
                if (result.isGranted(Manifest.permission.CAMERA)) {
                    log("  Camera: granted")
                }
                if (result.isPermanentlyDenied(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    log("  Location: permanently denied")
                }
            } else {
                log("DSL: user cancelled rationale")
            }
        }
    }

    private fun checkPermission() {
        val hasCamera = hasPermission(Manifest.permission.CAMERA)
        log("Camera permission: $hasCamera")

        lifecycleScope.launch {
            val result = requestPermissionsResult(Manifest.permission.CAMERA)
            log("Suspend result: ${result.status}")
        }
    }

    private fun openSettings() {
        val success = AwPermission.openAppSettings(this)
        log("Open settings: $success")
    }

    private suspend fun showRationaleDialog(permissions: List<String>): Boolean {
        return suspendCancellableCoroutine { cont ->
            val dialog = AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("The following permissions are needed: ${permissions.joinToString()}. Would you like to proceed?")
                .setPositiveButton("Allow") { _, _ -> cont.resume(true) }
                .setNegativeButton("Deny") { _, _ -> cont.resume(false) }
                .setOnCancelListener { cont.resume(false) }
                .show()
            cont.invokeOnCancellation { dialog.dismiss() }
        }
    }

    private fun log(msg: String) {
        tvLog.append("$msg\n")
    }
}
