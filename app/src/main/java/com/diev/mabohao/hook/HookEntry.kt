package com.diev.mabohao.hook

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.diev.mabohao.BuildConfig
import com.diev.mabohao.data.Rule
import com.diev.mabohao.data.RuleRepository
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.toClass
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.highcapable.yukihookapi.hook.factory.prefs
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {

    companion object {
        private const val TAG = "DievMabohao"
        
        // 缓存的规则Map：key=暗码完整值，value=规则
        @Volatile
        private var cachedRulesMap: Map<String, Rule> = emptyMap()
        
        // 缓存的时间戳
        @Volatile
        private var cachedTimestamp: Long = 0L
        
        // 缓存的设置
        @Volatile
        private var cachedEnableLogging: Boolean = true
        
        @Volatile
        private var cachedEnableToast: Boolean = true
        
        @Volatile
        private var settingsLoaded: Boolean = false
        
        // 复用的Gson和TypeToken
        private val gson = Gson()
        private val ruleListType = object : TypeToken<List<Rule>>() {}.type
    }

    override fun onInit() {
        YukiHookAPI.configs {
            isDebug = BuildConfig.DEBUG
        }
        Log.i(TAG, "HookEntry onInit called")
    }

    override fun onHook() {
        Log.i(TAG, "HookEntry onHook called")
        YukiHookAPI.encase {
            Log.i(TAG, "encase started, packageName: $packageName")
            
            loadApp(name = "com.android.contacts") {
                Log.i(TAG, "Loading hook for com.android.contacts")
                
                try {
                    val targetClass = "com.android.contacts.SpecialCharSequenceMgr".toClass()
                    Log.i(TAG, "Found class: SpecialCharSequenceMgr")
                    
                    // 使用方法签名定位：public static boolean (Context, String)
                    val targetMethod = targetClass.declaredMethods.firstOrNull { method ->
                        method.modifiers and java.lang.reflect.Modifier.PUBLIC != 0 &&
                        method.modifiers and java.lang.reflect.Modifier.STATIC != 0 &&
                        method.returnType == Boolean::class.javaPrimitiveType &&
                        method.parameterTypes.size == 2 &&
                        method.parameterTypes[0] == Context::class.java &&
                        method.parameterTypes[1] == String::class.java
                    }
                    
                    if (targetMethod != null) {
                        Log.i(TAG, "Found target method: ${targetMethod.name}")
                        
                        targetMethod.hook {
                            before {
                                try {
                                    val context = args[0] as Context
                                    // 移除拨号盘自动生成的空格、连字符等分隔符
                                    val inputCode = (args[1] as String).replace(Regex("[\\s-]"), "")
                                    
                                    // 首次加载或更新设置
                                    if (!settingsLoaded) {
                                        loadSettings(context)
                                    }
                                    
                                    // 检查规则是否变化
                                    checkAndReloadRules(context)
                                    
                                    if (cachedEnableLogging) {
                                        Log.i(TAG, "Hook triggered! Input code: $inputCode, rules count: ${cachedRulesMap.size}")
                                    }
                                    
                                    if (cachedEnableToast) {
                                        Toast.makeText(context, "暗码检测: $inputCode", Toast.LENGTH_SHORT).show()
                                    }
                                    
                                    // O(1)查找规则
                                    val matchedRule = cachedRulesMap[inputCode]
                                    
                                    if (matchedRule != null) {
                                        if (cachedEnableLogging) {
                                            Log.i(TAG, "Rule matched! Launching: ${matchedRule.packageName}")
                                        }
                                        
                                        if (cachedEnableToast) {
                                            Toast.makeText(context, "启动应用: ${matchedRule.packageName}", Toast.LENGTH_SHORT).show()
                                        }
                                        
                                        try {
                                            val pm = context.packageManager
                                            val intent = pm.getLaunchIntentForPackage(matchedRule.packageName)
                                            
                                            if (intent != null) {
                                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                
                                                // 延迟150ms启动，让拨号盘有时间清空内容（TwelveKeyDialerFragment中清理逻辑延时了50ms）
                                                Handler(Looper.getMainLooper()).postDelayed({
                                                    try {
                                                        context.startActivity(intent)
                                                        if (cachedEnableLogging) {
                                                            Log.i(TAG, "App launched successfully: ${matchedRule.packageName}")
                                                        }
                                                    } catch (e: Exception) {
                                                        if (cachedEnableLogging) {
                                                            Log.e(TAG, "Failed to launch app in delayed task: ${matchedRule.packageName}", e)
                                                        }
                                                    }
                                                }, 150)
                                                
                                                result = true
                                            } else {
                                                if (cachedEnableLogging) {
                                                    Log.e(TAG, "No launch intent for package: ${matchedRule.packageName}")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            if (cachedEnableLogging) {
                                                Log.e(TAG, "Failed to launch app: ${matchedRule.packageName}", e)
                                            }
                                        }
                                    } else {
                                        if (cachedEnableLogging) {
                                            Log.i(TAG, "No matching rule for code: $inputCode")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error in hook callback", e)
                                }
                            }
                        }
                        
                        Log.i(TAG, "Hook installed successfully via method signature")
                    } else {
                        Log.e(TAG, "Could not find target method by signature")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to hook SpecialCharSequenceMgr", e)
                }
            }
        }
    }
    
    private fun loadSettings(context: Context) {
        try {
            cachedEnableLogging = context.prefs(RuleRepository.PREFS_NAME).getBoolean(RuleRepository.KEY_ENABLE_LOGGING, true)
            cachedEnableToast = context.prefs(RuleRepository.PREFS_NAME).getBoolean(RuleRepository.KEY_ENABLE_TOAST, true)
            settingsLoaded = true
            Log.i(TAG, "Settings loaded: logging=$cachedEnableLogging, toast=$cachedEnableToast")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading settings", e)
        }
    }
    
    private fun checkAndReloadRules(context: Context) {
        try {
            val currentTimestamp = context.prefs(RuleRepository.PREFS_NAME).getLong(RuleRepository.KEY_RULES_TIMESTAMP, 0L)
            
            if (currentTimestamp != cachedTimestamp) {
                Log.i(TAG, "Rules timestamp changed: $cachedTimestamp -> $currentTimestamp, reloading rules")
                cachedTimestamp = currentTimestamp
                
                val rulesJson = context.prefs(RuleRepository.PREFS_NAME).getString(RuleRepository.KEY_RULES, "") ?: ""
                
                if (rulesJson.isEmpty()) {
                    cachedRulesMap = emptyMap()
                    Log.i(TAG, "Rules cleared")
                    return
                }
                
                val rulesList: List<Rule> = gson.fromJson(rulesJson, ruleListType)
                
                // 转换为Map，以暗码完整值为key，只包含启用的规则
                val rulesMap = mutableMapOf<String, Rule>()
                for (rule in rulesList) {
                    if (rule.isEnabled) {
                        rulesMap[rule.getFullCode()] = rule
                    }
                }
                cachedRulesMap = rulesMap
                
                Log.i(TAG, "Rules reloaded: ${cachedRulesMap.size} enabled rules")
                
                // 重新加载设置
                loadSettings(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking rules", e)
        }
    }
}