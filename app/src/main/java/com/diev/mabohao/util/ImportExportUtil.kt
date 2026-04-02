package com.diev.mabohao.util

import android.app.Activity
import android.net.Uri
import android.util.Log
import com.diev.mabohao.data.RuleRepository
import com.highcapable.yukihookapi.hook.factory.prefs
import java.io.BufferedReader
import java.io.InputStreamReader

object ImportExportUtil {
    private const val TAG = "DievMabohao"
    
    fun exportRules(activity: Activity, uri: Uri): Boolean {
        return with(activity) {
            try {
                val json = prefs(RuleRepository.PREFS_NAME).getString(RuleRepository.KEY_RULES, "") ?: ""
                val prefix = prefs(RuleRepository.PREFS_NAME).getString(RuleRepository.KEY_PREFIX, "*#*#") ?: "*#*#"
                val suffix = prefs(RuleRepository.PREFS_NAME).getString(RuleRepository.KEY_SUFFIX, "#*#*") ?: "#*#*"
                
                val exportJson = RuleRepository.exportToJson(
                    rules = RuleRepository.parseRules(json),
                    prefix = prefix,
                    suffix = suffix
                )
                
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(exportJson.toByteArray())
                }
                Log.i(TAG, "Rules exported successfully")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting rules", e)
                false
            }
        }
    }

    fun importRules(activity: Activity, uri: Uri): Boolean {
        return with(activity) {
            try {
                val json = contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).readText()
                } ?: return@with false
                
                val exportData = RuleRepository.parseExportData(json) ?: return@with false
                
                prefs(RuleRepository.PREFS_NAME).edit {
                    putString(RuleRepository.KEY_RULES, RuleRepository.toJson(exportData.rules))
                    putString(RuleRepository.KEY_PREFIX, exportData.prefix)
                    putString(RuleRepository.KEY_SUFFIX, exportData.suffix)
                    putLong(RuleRepository.KEY_RULES_TIMESTAMP, System.currentTimeMillis())
                }
                
                Log.i(TAG, "Rules imported successfully")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error importing rules", e)
                false
            }
        }
    }
}