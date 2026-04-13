package com.answufeng.permission.demo

import android.Manifest
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.answufeng.permission.AwPermission
import com.answufeng.permission.hasPermission
import com.answufeng.permission.observePermissions
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

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCheckPermission).setOnClickListener {
            checkPermission()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnOpenSettings).setOnClickListener {
            openSettings()
        }

        lifecycleScope.launch {
            observePermissions(Manifest.permission.CAMERA).collect { result ->
                log("[Flow] Camera permission: granted=${result.granted}, denied=${result.denied}")
            }
        }
    }

    private fun requestCamera() {
        lifecycleScope.launch {
            val result = AwPermission.request(this@MainActivity, Manifest.permission.CAMERA)
            log("Camera: granted=${result.granted}, denied=${result.denied}, permanent=${result.permanentlyDenied}")
            if (result.hasPermanentlyDenied) {
                log("Camera permanently denied, please go to settings to enable")
            }
        }
    }

    private fun requestLocation() {
        lifecycleScope.launch {
            val result = AwPermission.request(
                this@MainActivity,
                *com.answufeng.permission.PermissionGroups.LOCATION
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
            val result = AwPermission.requestWithRationale(
                activity = this@MainActivity,
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

    private fun checkPermission() {
        val hasCamera = hasPermission(Manifest.permission.CAMERA)
        log("Camera permission: $hasCamera")
    }

    private fun openSettings() {
        val success = AwPermission.openAppSettings(this)
        log("Open settings: $success")
    }

    private fun log(msg: String) {
        tvLog.append("$msg\n")
    }
}
