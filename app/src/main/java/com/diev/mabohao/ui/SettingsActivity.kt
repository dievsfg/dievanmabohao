package com.diev.mabohao.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.diev.mabohao.R
import com.diev.mabohao.data.RuleRepository
import com.diev.mabohao.databinding.ActivitySettingsBinding
import com.highcapable.yukihookapi.hook.factory.prefs
import com.google.android.material.snackbar.Snackbar

class SettingsActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "DievMabohao"
    }
    
    private lateinit var binding: ActivitySettingsBinding
    
    private val setPatternLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            binding.switchAppLock.isChecked = true
            saveSettings()
            Snackbar.make(binding.root, "图案密码设置成功", Snackbar.LENGTH_SHORT).show()
        } else {
            val savedHash = prefs(RuleRepository.PREFS_NAME).getString(RuleRepository.KEY_APP_LOCK_PATTERN, "")
            if (savedHash.isNullOrEmpty()) {
                binding.switchAppLock.isChecked = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        loadSettings()
        setupButtons()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun loadSettings() {
        val prefix = prefs(RuleRepository.PREFS_NAME).getString(RuleRepository.KEY_PREFIX, "*#*#") ?: "*#*#"
        val suffix = prefs(RuleRepository.PREFS_NAME).getString(RuleRepository.KEY_SUFFIX, "#*#*") ?: "#*#*"
        val enableLogging = prefs(RuleRepository.PREFS_NAME).getBoolean(RuleRepository.KEY_ENABLE_LOGGING, true)
        val enableToast = prefs(RuleRepository.PREFS_NAME).getBoolean(RuleRepository.KEY_ENABLE_TOAST, true)
        
        val enableAppLock = prefs(RuleRepository.PREFS_NAME).getBoolean(RuleRepository.KEY_APP_LOCK_ENABLED, false)
        val enableFingerprint = prefs(RuleRepository.PREFS_NAME).getBoolean(RuleRepository.KEY_APP_LOCK_FINGERPRINT, false)
        
        binding.etPrefix.setText(prefix)
        binding.etSuffix.setText(suffix)
        binding.switchLogging.isChecked = enableLogging
        binding.switchToast.isChecked = enableToast
        
        binding.switchAppLock.isChecked = enableAppLock
        binding.switchFingerprint.isChecked = enableFingerprint
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener {
            saveSettings()
            Snackbar.make(binding.root, "设置已保存", Snackbar.LENGTH_SHORT).show()
            finish()
        }
        
        binding.switchAppLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val savedHash = prefs(RuleRepository.PREFS_NAME).getString(RuleRepository.KEY_APP_LOCK_PATTERN, "")
                if (savedHash.isNullOrEmpty()) {
                    // No pattern set, force user to set one
                    binding.switchAppLock.isChecked = false
                    launchPatternSetup()
                } else {
                    saveSettings()
                }
            } else {
                saveSettings()
            }
        }
        
        binding.btnSetPattern.setOnClickListener {
            launchPatternSetup()
        }
        
        binding.switchFingerprint.setOnCheckedChangeListener { _, isChecked ->
            saveSettings()
        }
    }
    
    private fun launchPatternSetup() {
        val intent = Intent(this, PatternLockActivity::class.java).apply {
            putExtra(PatternLockActivity.EXTRA_ACTION, PatternLockActivity.ACTION_CREATE_PATTERN)
        }
        setPatternLauncher.launch(intent)
    }

    private fun saveSettings() {
        val prefix = binding.etPrefix.text.toString()
        val suffix = binding.etSuffix.text.toString()
        val enableLogging = binding.switchLogging.isChecked
        val enableToast = binding.switchToast.isChecked
        val enableAppLock = binding.switchAppLock.isChecked
        val enableFingerprint = binding.switchFingerprint.isChecked
        
        Log.i(TAG, "Saving settings: prefix=$prefix, suffix=$suffix, logging=$enableLogging, toast=$enableToast, appLock=$enableAppLock, fingerprint=$enableFingerprint")
        
        prefs(RuleRepository.PREFS_NAME).edit {
            putString(RuleRepository.KEY_PREFIX, prefix)
            putString(RuleRepository.KEY_SUFFIX, suffix)
            putBoolean(RuleRepository.KEY_ENABLE_LOGGING, enableLogging)
            putBoolean(RuleRepository.KEY_ENABLE_TOAST, enableToast)
            putBoolean(RuleRepository.KEY_APP_LOCK_ENABLED, enableAppLock)
            putBoolean(RuleRepository.KEY_APP_LOCK_FINGERPRINT, enableFingerprint)
            putLong(RuleRepository.KEY_RULES_TIMESTAMP, System.currentTimeMillis())
        }
        
        // Removed Snackbar here to avoid annoying toast on every switch toggle.
    }
}