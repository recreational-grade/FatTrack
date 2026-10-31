package com.recreationalgrade.fattrack.ui.permissions

import android.content.Context

/**
 * Remembers which runtime permissions have been requested and denied, so a
 * permanent denial can be told apart from "never asked". This information
 * isn't exposed by the platform, and it needs to survive an app restart, so
 * rememberSaveable isn't enough.
 */
internal class PermissionRequestStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun deniedBefore(permissions: Collection<String>): Set<String> =
        permissions.filterTo(mutableSetOf()) { prefs.getBoolean(it, false) }

    /** Records a permission request result: denied sets the flag, granted clears it. */
    fun record(results: Map<String, Boolean>) {
        prefs.edit().apply {
            results.forEach { (permission, granted) ->
                if (granted) remove(permission) else putBoolean(permission, true)
            }
        }.apply()
    }

    fun clear(permissions: Collection<String>) {
        prefs.edit().apply { permissions.forEach { remove(it) } }.apply()
    }

    private companion object {
        const val PREFS_NAME = "permission_requests"
    }
}
