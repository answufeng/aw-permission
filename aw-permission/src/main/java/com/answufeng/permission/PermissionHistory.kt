package com.answufeng.permission

import android.content.Context
import android.content.SharedPreferences
import java.util.HashSet

internal object PermissionHistory {

    private const val PREFS_NAME = "aw_permission_history"
    private const val KEY_REQUESTED_PERMISSIONS = "requested_permissions"

    private val requestedPermissions = mutableSetOf<String>()
    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        val prefs = getPrefs(context)
        val stored = prefs.getStringSet(KEY_REQUESTED_PERMISSIONS, emptySet()) ?: emptySet()
        requestedPermissions.addAll(HashSet(stored))
        initialized = true
    }

    @Synchronized
    fun hasRequested(context: Context, permission: String): Boolean {
        ensureInitialized(context)
        return permission in requestedPermissions
    }

    @Synchronized
    fun recordRequested(context: Context, permissions: Collection<String>) {
        ensureInitialized(context)
        val changed = requestedPermissions.addAll(permissions)
        if (changed) {
            getPrefs(context).edit()
                .putStringSet(KEY_REQUESTED_PERMISSIONS, HashSet(requestedPermissions))
                .apply()
        }
    }

    private fun ensureInitialized(context: Context) {
        if (!initialized) {
            initialize(context)
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
