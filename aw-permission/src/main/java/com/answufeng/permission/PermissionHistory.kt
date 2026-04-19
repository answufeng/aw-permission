package com.answufeng.permission

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

internal object PermissionHistory {

    private const val PREFS_NAME = "aw_permission_history"
    private const val KEY_REQUESTED_PERMISSIONS = "requested_permissions"

    private val requestedPermissions = ConcurrentHashMap.newKeySet<String>()
    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        val prefs = getPrefs(context)
        requestedPermissions.addAll(prefs.getStringSet(KEY_REQUESTED_PERMISSIONS, emptySet()) ?: emptySet())
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
            getPrefs(context).edit().putStringSet(KEY_REQUESTED_PERMISSIONS, requestedPermissions).apply()
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
