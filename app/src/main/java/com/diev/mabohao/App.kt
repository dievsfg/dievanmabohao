package com.diev.mabohao

import android.app.Application
import android.content.Intent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.diev.mabohao.data.RuleRepository
import com.diev.mabohao.ui.PatternLockActivity
import com.highcapable.yukihookapi.hook.factory.prefs

class App : Application(), DefaultLifecycleObserver {
    
    companion object {
        private var isAppInForeground = false
    }

    override fun onCreate() {
        super<Application>.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        isAppInForeground = true
        val isLockEnabled = prefs(RuleRepository.PREFS_NAME).getBoolean(RuleRepository.KEY_APP_LOCK_ENABLED, false)
        
        if (isLockEnabled && !PatternLockActivity.isAppUnlocked) {
            val intent = Intent(this, PatternLockActivity::class.java).apply {
                putExtra(PatternLockActivity.EXTRA_ACTION, PatternLockActivity.ACTION_VERIFY_PATTERN)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        isAppInForeground = false
        // When app goes to background, require unlock next time
        PatternLockActivity.isAppUnlocked = false
    }
}