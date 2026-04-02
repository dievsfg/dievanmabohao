package com.diev.mabohao.ui

import android.os.Bundle
import android.util.Log
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
        
        binding.etPrefix.setText(prefix)
        binding.etSuffix.setText(suffix)
        binding.switchLogging.isChecked = enableLogging
        binding.switchToast.isChecked = enableToast
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener {
            saveSettings()
        }
    }

    private fun saveSettings() {
        val prefix = binding.etPrefix.text.toString()
        val suffix = binding.etSuffix.text.toString()
        val enableLogging = binding.switchLogging.isChecked
        val enableToast = binding.switchToast.isChecked
        
        Log.i(TAG, "Saving settings: prefix=$prefix, suffix=$suffix, logging=$enableLogging, toast=$enableToast")
        
        prefs(RuleRepository.PREFS_NAME).edit {
            putString(RuleRepository.KEY_PREFIX, prefix)
            putString(RuleRepository.KEY_SUFFIX, suffix)
            putBoolean(RuleRepository.KEY_ENABLE_LOGGING, enableLogging)
            putBoolean(RuleRepository.KEY_ENABLE_TOAST, enableToast)
            putLong(RuleRepository.KEY_RULES_TIMESTAMP, System.currentTimeMillis())
        }
        
        Snackbar.make(binding.root, "设置已保存", Snackbar.LENGTH_SHORT).show()
        finish()
    }
}