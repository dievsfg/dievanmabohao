package com.diev.mabohao.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Build

object LSPosedUtil {
    private const val MODULE_PREFS_NAME = "diev_mabohao_rules"

    fun isModuleActive(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences(MODULE_PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getBoolean("module_active", false)
        } catch (e: Exception) {
            false
        }
    }

    fun setModuleActive(context: Context, active: Boolean) {
        val prefs = context.getSharedPreferences(MODULE_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("module_active", active).apply()
    }

    fun isAndroid16OrHigher(): Boolean {
        return Build.VERSION.SDK_INT >= 36
    }
}