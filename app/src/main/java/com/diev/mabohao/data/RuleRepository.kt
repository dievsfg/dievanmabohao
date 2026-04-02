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