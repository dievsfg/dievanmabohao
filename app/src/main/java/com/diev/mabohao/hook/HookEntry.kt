package com.diev.mabohao.hook

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.diev.mabohao.BuildConfig
import com.diev.mabohao.data.Rule
import com.diev.mabohao.data.RuleRepository
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.factory.toClass
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.highcapable.yukihookapi.hook.factory.prefs
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {

    companion object {
        private const val TAG = "DievMabohao"
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
                                    val inputCode = args[1] as String
                                    
                                    // 读取设置
                                    val enableLogging = prefs(RuleRepository.PREFS_NAME).getBoolean(RuleRepository.KEY_ENABLE_LOGGING, true)
                                    val enableToast = prefs(RuleRepository.PREFS_NAME).getBoolean(RuleRepository.KEY_ENABLE_TOAST, true)
                                    
                                    if (enableLogging) {
                                        Log.i(TAG, "Hook triggered! Method: ${targetMethod.name}, Input code: $inputCode")
                                    }
                                    
                                    if (enableToast) {
                                        Toast.makeText(context, "暗码检测: $inputCode", Toast.LENGTH_SHORT).show()
                                    }
                                    
                                    // 使用prefs()读取规则
                                    val rulesJson = prefs(RuleRepository.PREFS_NAME).getString(RuleRepository.KEY_RULES, "") ?: ""
                                    
                                    if (enableLogging) {
                                        Log.i(TAG, "Rules from prefs(): $rulesJson")
                                    }
                                    
                                    if (rulesJson.isEmpty()) {
                                        if (enableLogging) Log.i(TAG, "No rules found")
                                        return@before
                                    }
                                    
                                    val gson = Gson()
                                    val type = object : TypeToken<List<Rule>>() {}.type
                                    val rules: List<Rule> = gson.fromJson(rulesJson, type)
                                    
                                    if (enableLogging) Log.i(TAG, "Loaded ${rules.size} rules")
                                    
                                    for (rule in rules) {
                                        if (enableLogging) Log.i(TAG, "Checking rule: ${rule.getFullCode()}, enabled: ${rule.isEnabled}")
                                        
                                        if (!rule.isEnabled) continue
                                        
                                        val fullCode = rule.getFullCode()
                                        if (inputCode == fullCode) {
                                            if (enableLogging) Log.i(TAG, "Rule matched! Launching: ${rule.packageName}")
                                            
                                            if (enableToast) {
                                                Toast.makeText(context, "启动应用: ${rule.packageName}", Toast.LENGTH_SHORT).show()
                                            }
                                            
                                            try {
                                                val pm = context.packageManager
                                                val intent = pm.getLaunchIntentForPackage(rule.packageName)
                                                
                                                if (intent != null) {
                                                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    context.startActivity(intent)
                                                    if (enableLogging) Log.i(TAG, "App launched successfully: ${rule.packageName}")
                                                    result = true
                                                } else {
                                                    if (enableLogging) Log.e(TAG, "No launch intent for package: ${rule.packageName}")
                                                }
                                            } catch (e: Exception) {
                                                if (enableLogging) Log.e(TAG, "Failed to launch app: ${rule.packageName}", e)
                                            }
                                            return@before
                                        }
                                    }
                                    
                                    if (enableLogging) Log.i(TAG, "No matching rule for code: $inputCode")
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
}