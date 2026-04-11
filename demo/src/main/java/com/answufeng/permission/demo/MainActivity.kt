package com.answufeng.permission.demo

import android.Manifest
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.answufeng.permission.BrickPermission
import com.answufeng.permission.hasPermission
import com.answufeng.permission.requirePermissions
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvLog = TextView(this).apply { textSize = 14f }
        val container = findViewById<LinearLayout>(R.id.container)
        container.addView(tvLog)

        container.addView(button("Request Camera") {
            lifecycleScope.launch {
                val result = BrickPermission.request(this@MainActivity, Manifest.permission.CAMERA)
                log("Camera: granted=${result.granted}, denied=${result.denied}, permanent=${result.permanentlyDenied}")
            }
        })

        container.addView(button("Request Location") {
            lifecycleScope.launch {
                val result = BrickPermission.request(
                    this@MainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                log("Location: allGranted=${result.isAllGranted}")
            }
        })

        container.addView(button("Request Multiple") {
            requirePermissions(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                onGranted = { log("All permissions granted!") },
                onDenied = { result ->
                    log("Denied: granted=${result.granted}, denied=${result.denied}")
                    if (result.hasPermanentlyDenied) {
                        log("Permanently denied: ${result.permanentlyDenied}")
                        BrickPermission.openAppSettings(this@MainActivity)
                    }
                }
            )
        })

        container.addView(button("Check Permission") {
            val hasCamera = hasPermission(Manifest.permission.CAMERA)
            log("Camera permission: $hasCamera")
        })

        container.addView(button("Open App Settings") {
            BrickPermission.openAppSettings(this)
        })
    }

    private fun button(text: String, onClick: () -> Unit): Button {
        return Button(this).apply { this.text = text; setOnClickListener { onClick() } }
    }

    private fun log(msg: String) { tvLog.append("$msg\n") }
}
