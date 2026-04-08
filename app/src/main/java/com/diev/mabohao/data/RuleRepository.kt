package com.diev.mabohao.data

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object RuleRepository {
    private const val TAG = "DievMabohao"
    const val PREFS_NAME = "diev_mabohao_rules"
    const val KEY_RULES = "rules"
    const val KEY_PREFIX = "global_prefix"
    const val KEY_SUFFIX = "global_suffix"
    const val KEY_ENABLE_LOGGING = "enable_logging"
    const val KEY_ENABLE_TOAST = "enable_toast"
    const val KEY_RULES_TIMESTAMP = "rules_timestamp"
    
    // 安全设置
    const val KEY_LOCK_ENABLED = "lock_enabled"
    const val KEY_LOCK_PATTERN = "lock_pattern"
    const val KEY_FINGERPRINT_ENABLED = "fingerprint_enabled"
    const val KEY_LOCK_STRATEGY = "lock_strategy"       // 0=立即, 1=延时, 2=锁屏后
    const val KEY_LOCK_DELAY = "lock_delay"             // 延时时间(毫秒), 默认5分钟
    
    // 外观设置
    const val KEY_THEME_MODE = "theme_mode"             // 0=跟随系统, 1=浅色, 2=暗色
    
    private val gson = Gson()

    fun parseRules(json: String?): List<Rule> {
        return try {
            if (json.isNullOrEmpty()) return emptyList()
            val type = object : TypeToken<List<Rule>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing rules", e)
            emptyList()
        }
    }

    fun toJson(rules: List<Rule>): String {
        return gson.toJson(rules)
    }

    fun parseExportData(json: String): ExportData? {
        return try {
            gson.fromJson(json, ExportData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun exportToJson(rules: List<Rule>, prefix: String, suffix: String): String {
        val exportData = ExportData(
            prefix = prefix,
            suffix = suffix,
            rules = rules
        )
        return gson.toJson(exportData)
    }

    data class ExportData(
        val prefix: String,
        val suffix: String,
        val rules: List<Rule>
    )
}