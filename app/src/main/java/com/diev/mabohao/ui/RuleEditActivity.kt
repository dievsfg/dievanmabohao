package com.diev.mabohao.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.diev.mabohao.R
import com.diev.mabohao.data.Rule
import com.diev.mabohao.data.RuleRepository
import com.diev.mabohao.databinding.ActivityRuleEditBinding
import com.highcapable.yukihookapi.hook.factory.prefs
import com.google.android.material.snackbar.Snackbar
import java.util.UUID

class RuleEditActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "DievMabohao"
        private const val REQUEST_CODE_SELECT_APP = 1001
    }
    
    private lateinit var binding: ActivityRuleEditBinding
    private var editingRule: Rule? = null
    private var selectedPackageName: String = ""
    private var selectedAppLabel: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRuleEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        loadRule()
        setupInputs()
        setupButtons()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun loadRule() {
        val ruleId = intent.getStringExtra("rule_id")
        if (ruleId != null) {
            val json = prefs(RuleRepository.PREFS_NAME).getString(RuleRepository.KEY_RULES, "")
            val rules = RuleRepository.parseRules(json)
            editingRule = rules.find { it.id == ruleId }
            editingRule?.let { rule ->
                binding.etPrefix.setText(rule.prefix)
                binding.etCode.setText(rule.code)
                binding.etSuffix.setText(rule.suffix)
                binding.etPackage.setText(rule.packageName)
                binding.switchEnabled.isChecked = rule.isEnabled
                selectedPackageName = rule.packageName
                selectedAppLabel = rule.appLabel
                binding.btnDelete.visibility = View.VISIBLE
                updateAppName()
            }
        } else {
            val prefix = prefs(RuleRepository.PREFS_NAME).getString(RuleRepository.KEY_PREFIX, "*#*#") ?: "*#*#"
            val suffix = prefs(RuleRepository.PREFS_NAME).getString(RuleRepository.KEY_SUFFIX, "#*#*") ?: "#*#*"
            binding.etPrefix.setText(prefix)
            binding.etSuffix.setText(suffix)
        }
        updatePreview()
    }

    private fun setupInputs() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updatePreview()
            }
        }
        
        binding.etPrefix.addTextChangedListener(textWatcher)
        binding.etCode.addTextChangedListener(textWatcher)
        binding.etSuffix.addTextChangedListener(textWatcher)
        binding.etPackage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                selectedPackageName = s.toString()
                updateAppName()
            }
        })
    }

    private fun updatePreview() {
        val prefix = binding.etPrefix.text.toString()
        val code = binding.etCode.text.toString()
        val suffix = binding.etSuffix.text.toString()
        binding.tvPreview.text = getString(R.string.code_format, prefix, code, suffix)
    }

    private fun updateAppName() {
        if (selectedPackageName.isNotEmpty()) {
            try {
                val pm = packageManager
                val appInfo = pm.getApplicationInfo(selectedPackageName, 0)
                selectedAppLabel = pm.getApplicationLabel(appInfo).toString()
                binding.tvAppName.text = selectedAppLabel
                binding.tvAppName.visibility = View.VISIBLE
            } catch (e: Exception) {
                binding.tvAppName.text = getString(R.string.app_not_found)
                binding.tvAppName.visibility = View.VISIBLE
            }
        } else {
            binding.tvAppName.visibility = View.GONE
        }
    }

    private fun setupButtons() {
        binding.btnManual.setOnClickListener {
            showManualInputDialog()
        }
        
        binding.btnSelect.setOnClickListener {
            val intent = Intent(this, AppSelectorActivity::class.java)
            startActivityForResult(intent, REQUEST_CODE_SELECT_APP)
        }
        
        binding.btnSave.setOnClickListener {
            saveRule()
        }
        
        binding.btnDelete.setOnClickListener {
            showDeleteConfirmDialog()
        }
    }

    private fun showManualInputDialog() {
        val editText = android.widget.EditText(this).apply {
            hint = getString(R.string.rule_app_hint)
            setText(selectedPackageName)
        }
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.manual_input))
            .setView(editText)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                selectedPackageName = editText.text.toString()
                binding.etPackage.setText(selectedPackageName)
                updateAppName()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SELECT_APP && resultCode == RESULT_OK) {
            val packageName = data?.getStringExtra("package_name") ?: return
            val appName = data.getStringExtra("app_name") ?: ""
            selectedPackageName = packageName
            selectedAppLabel = appName
            binding.etPackage.setText(packageName)
            updateAppName()
        }
    }

    private fun saveRule() {
        val prefix = binding.etPrefix.text.toString()
        val code = binding.etCode.text.toString()
        val suffix = binding.etSuffix.text.toString()
        val packageName = binding.etPackage.text.toString()
        val isEnabled = binding.switchEnabled.isChecked
        
        if (code.isEmpty()) {
            Snackbar.make(binding.root, R.string.invalid_input, Snackbar.LENGTH_SHORT).show()
            return
        }
        
        if (packageName.isEmpty()) {
            Snackbar.make(binding.root, R.string.invalid_input, Snackbar.LENGTH_SHORT).show()
            return
        }
        
        val rule = Rule(
            id = editingRule?.id ?: UUID.randomUUID().toString(),
            code = code,
            packageName = packageName,
            appLabel = selectedAppLabel,
            isEnabled = isEnabled,
            prefix = prefix,
            suffix = suffix
        )
        
        // 读取现有规则
        val json = prefs(RuleRepository.PREFS_NAME).getString(RuleRepository.KEY_RULES, "")
        val rules = RuleRepository.parseRules(json).toMutableList()
        
        // 更新或添加规则
        if (editingRule != null) {
            val index = rules.indexOfFirst { it.id == rule.id }
            if (index != -1) {
                rules[index] = rule
            }
        } else {
            rules.add(rule)
        }
        
        // 保存规则
        val newJson = RuleRepository.toJson(rules)
        Log.i(TAG, "Saving rule: $newJson")
        prefs(RuleRepository.PREFS_NAME).edit {
            putString(RuleRepository.KEY_RULES, newJson)
            putLong(RuleRepository.KEY_RULES_TIMESTAMP, System.currentTimeMillis())
        }
        
        Snackbar.make(binding.root, R.string.rule_saved, Snackbar.LENGTH_SHORT).show()
        finish()
    }

    private fun showDeleteConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_delete)
            .setMessage(R.string.confirm_delete_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                editingRule?.let { rule ->
                    // 读取现有规则
                    val json = prefs(RuleRepository.PREFS_NAME).getString(RuleRepository.KEY_RULES, "")
                    val rules = RuleRepository.parseRules(json).toMutableList()
                    rules.removeAll { it.id == rule.id }
                    
                    // 保存规则
                    val newJson = RuleRepository.toJson(rules)
                    Log.i(TAG, "After delete, rules: $newJson")
                    prefs(RuleRepository.PREFS_NAME).edit {
                        putString(RuleRepository.KEY_RULES, newJson)
                        putLong(RuleRepository.KEY_RULES_TIMESTAMP, System.currentTimeMillis())
                    }
                    
                    finish()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}