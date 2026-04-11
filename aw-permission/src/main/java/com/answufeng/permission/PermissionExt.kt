package com.answufeng.permission

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

fun Context.hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

fun Context.hasPermissions(vararg permissions: String): Boolean {
    return permissions.all { hasPermission(it) }
}

fun FragmentActivity.requestRuntimePermissions(
    vararg permissions: String,
    callback: (PermissionResult) -> Unit
) {
    lifecycleScope.launch {
        val result = BrickPermission.request(this@requestRuntimePermissions, *permissions)
        callback(result)
    }
}

fun Fragment.requestRuntimePermissions(
    vararg permissions: String,
    callback: (PermissionResult) -> Unit
) {
    viewLifecycleOwner.lifecycleScope.launch {
        val result = BrickPermission.request(this@requestRuntimePermissions, *permissions)
        callback(result)
    }
}

fun FragmentActivity.requirePermissions(
    vararg permissions: String,
    onGranted: () -> Unit,
    onDenied: (PermissionResult) -> Unit = {}
) {
    lifecycleScope.launch {
        val result = BrickPermission.request(this@requirePermissions, *permissions)
        if (result.isAllGranted) {
            onGranted()
        } else {
            onDenied(result)
        }
    }
}

fun Fragment.requirePermissions(
    vararg permissions: String,
    onGranted: () -> Unit,
    onDenied: (PermissionResult) -> Unit = {}
) {
    viewLifecycleOwner.lifecycleScope.launch {
        val result = BrickPermission.request(this@requirePermissions, *permissions)
        if (result.isAllGranted) {
            onGranted()
        } else {
            onDenied(result)
        }
    }
}
