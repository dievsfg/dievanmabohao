package com.diev.mabohao.util

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

object AppCacheHelper {
    private const val TAG = "DievMabohao"
    
    data class CachedApp(
        val packageName: String,
        val appName: String,
        val icon: android.graphics.drawable.Drawable,
        val isSystem: Boolean,
        val firstInstallTime: Long,
        val lastUpdateTime: Long
    )
    
    enum class SortMethod {
        BY_LABEL, BY_PACKAGE_NAME, BY_INSTALL_TIME, BY_UPDATE_TIME
    }
    
    private val _appList = MutableStateFlow<List<CachedApp>>(emptyList())
    val appList: StateFlow<List<CachedApp>> = _appList
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing
    
    // 筛选和排序设置
    var showSystemApps: Boolean = false
    var sortMethod: SortMethod = SortMethod.BY_LABEL
    var reverseOrder: Boolean = false
    
    private var cachedApps: List<CachedApp> = emptyList()
    
    suspend fun loadApps(pm: PackageManager) {
        _isRefreshing.value = true
        
        val apps = withContext(Dispatchers.IO) {
            try {
                val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                packages.mapNotNull { appInfo ->
                    try {
                        CachedApp(
                            packageName = appInfo.packageName,
                            appName = pm.getApplicationLabel(appInfo).toString(),
                            icon = pm.getApplicationIcon(appInfo),
                            isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                            firstInstallTime = try {
                                pm.getPackageInfo(appInfo.packageName, 0).firstInstallTime
                            } catch (e: Exception) { 0L },
                            lastUpdateTime = try {
                                pm.getPackageInfo(appInfo.packageName, 0).lastUpdateTime
                            } catch (e: Exception) { 0L }
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading apps", e)
                emptyList()
            }
        }
        
        cachedApps = apps
        Log.i(TAG, "Loaded ${apps.size} apps")
        
        applyFilterAndSort()
        _isRefreshing.value = false
    }
    
    fun applyFilterAndSort(query: String = "") {
        var filtered = if (showSystemApps) {
            cachedApps
        } else {
            cachedApps.filter { !it.isSystem }
        }
        
        if (query.isNotEmpty()) {
            val queryLower = query.lowercase(Locale.getDefault())
            filtered = filtered.filter { app ->
                app.appName.lowercase(Locale.getDefault()).contains(queryLower) ||
                app.packageName.lowercase(Locale.getDefault()).contains(queryLower)
            }
        }
        
        val collator = Collator.getInstance(Locale.getDefault())
        val sorted = when (sortMethod) {
            SortMethod.BY_LABEL -> filtered.sortedWith(compareBy(collator) { it.appName.lowercase(Locale.getDefault()) })
            SortMethod.BY_PACKAGE_NAME -> filtered.sortedWith(compareBy(collator) { it.packageName.lowercase(Locale.getDefault()) })
            SortMethod.BY_INSTALL_TIME -> filtered.sortedByDescending { it.firstInstallTime }
            SortMethod.BY_UPDATE_TIME -> filtered.sortedByDescending { it.lastUpdateTime }
        }
        
        _appList.value = if (reverseOrder) sorted.reversed() else sorted
    }
}