package com.diev.mabohao

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.diev.mabohao.data.RuleRepository
import com.diev.mabohao.ui.PatternLockActivity
import com.highcapable.yukihookapi.hook.factory.prefs

class App : Application() {

    companion object {
        private const val TAG = "DievMabohao"

        @Volatile
        private var isUnlocked = false

        @Volatile
        private var isLockActivityShowing = false

        private var backgroundTime = 0L
        private val handler = Handler(Looper.getMainLooper())
        private var delayLockRunnable: Runnable? = null

        fun isLocked(context: Context): Boolean {
            val prefs = context.prefs(RuleRepository.PREFS_NAME)
            val lockEnabled = prefs.getBoolean(RuleRepository.KEY_LOCK_ENABLED, false)
            val pattern = prefs.getString(RuleRepository.KEY_LOCK_PATTERN, "")
            if (!lockEnabled || pattern.isNullOrEmpty()) return false
            return !isUnlocked
        }

        fun setUnlocked() {
            isUnlocked = true
            isLockActivityShowing = false
        }

        fun setLocked() {
            isUnlocked = false
        }

        fun showLockScreenIfNeeded(activity: Activity) {
            if (activity is PatternLockActivity) return
            if (isLockActivityShowing) return
            if (!isLocked(activity)) return

            isLockActivityShowing = true
            val intent = Intent(activity, PatternLockActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            activity.startActivity(intent)
        }

        fun applyTheme(context: Context) {
            val themeMode = context.prefs(RuleRepository.PREFS_NAME).getInt(RuleRepository.KEY_THEME_MODE, 0)
            AppCompatDelegate.setDefaultNightMode(
                when (themeMode) {
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
    }

    private var activityCount = 0

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                val strategy = prefs(RuleRepository.PREFS_NAME).getInt(RuleRepository.KEY_LOCK_STRATEGY, 0)
                if (strategy == 2) {
                    Log.i(TAG, "Screen off, locking app (strategy=2)")
                    setLocked()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // 应用主题设置
        applyTheme(this)

        // 注册锁屏广播
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOffReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOffReceiver, filter)
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var changingConfigurationCount = 0

            override fun onActivityStarted(activity: Activity) {
                if (changingConfigurationCount > 0) {
                    changingConfigurationCount--
                } else {
                    activityCount++
                    if (activityCount == 1) {
                        // 从后台回到前台
                        cancelDelayLock()
                        onAppForeground(activity)
                    }
                }
            }

            override fun onActivityStopped(activity: Activity) {
                if (activity.isChangingConfigurations) {
                    changingConfigurationCount++
                } else {
                    activityCount--
                    if (activityCount == 0) {
                        // 进入后台
                        onAppBackground()
                    }
                }
            }

            override fun onActivityResumed(activity: Activity) {
                if (!activity.isChangingConfigurations && changingConfigurationCount == 0) {
                    showLockScreenIfNeeded(activity)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (activity.isChangingConfigurations) {
                    // 当Activity因配置改变（如切换主题）而销毁时，减少计数
                    // 因为在onActivityStopped中我们已经增加了它
                    // 等待新的Activity重建并调用onActivityStarted
                }
            }
        })
    }

    private fun onAppForeground(activity: Activity) {
        val lockEnabled = prefs(RuleRepository.PREFS_NAME).getBoolean(RuleRepository.KEY_LOCK_ENABLED, false)
        if (!lockEnabled) return

        val strategy = prefs(RuleRepository.PREFS_NAME).getInt(RuleRepository.KEY_LOCK_STRATEGY, 0)
        when (strategy) {
            0 -> {
                // 立即锁定 - 已经在onAppBackground中处理
            }
            1 -> {
                // 延时锁定 - 检查是否已超时
                val delay = prefs(RuleRepository.PREFS_NAME).getLong(RuleRepository.KEY_LOCK_DELAY, 5 * 60 * 1000L)
                val elapsed = System.currentTimeMillis() - backgroundTime
                if (elapsed >= delay) {
                    Log.i(TAG, "Delay expired ($elapsed >= $delay), locking")
                    setLocked()
                }
            }
            // 策略2在screenOffReceiver中处理
        }
    }

    private fun onAppBackground() {
        backgroundTime = System.currentTimeMillis()
        val lockEnabled = prefs(RuleRepository.PREFS_NAME).getBoolean(RuleRepository.KEY_LOCK_ENABLED, false)
        if (!lockEnabled) return

        val strategy = prefs(RuleRepository.PREFS_NAME).getInt(RuleRepository.KEY_LOCK_STRATEGY, 0)
        when (strategy) {
            0 -> {
                // 立即锁定
                Log.i(TAG, "App background, locking immediately (strategy=0)")
                setLocked()
            }
            1 -> {
                // 延时锁定 - 启动倒计时
                val delay = prefs(RuleRepository.PREFS_NAME).getLong(RuleRepository.KEY_LOCK_DELAY, 5 * 60 * 1000L)
                Log.i(TAG, "App background, will lock after ${delay}ms (strategy=1)")
                delayLockRunnable = Runnable {
                    Log.i(TAG, "Delay lock timer expired, locking")
                    setLocked()
                }
                handler.postDelayed(delayLockRunnable!!, delay)
            }
            // 策略2在screenOffReceiver中处理
        }
    }

    private fun cancelDelayLock() {
        delayLockRunnable?.let {
            handler.removeCallbacks(it)
            delayLockRunnable = null
        }
    }
}