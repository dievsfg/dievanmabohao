package com.diev.mabohao.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.diev.mabohao.R
import com.diev.mabohao.data.Rule
import com.diev.mabohao.data.RuleRepository
import com.diev.mabohao.databinding.ActivityMainBinding
import com.diev.mabohao.util.ImportExportUtil
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.factory.prefs
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "DievMabohao"
    }
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: RuleAdapter

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            if (ImportExportUtil.importRules(this, it)) {
                loadRules()
                Snackbar.make(binding.root, R.string.import_success, Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(binding.root, R.string.import_failed, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            if (ImportExportUtil.exportRules(this, it)) {
                Snackbar.make(binding.root, R.string.export_success, Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(binding.root, R.string.export_failed, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRecyclerView()
        setupButtons()
        updateModuleStatus()
    }

    override fun onResume() {
        super.onResume()
        loadRules()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            onOptionsItemSelected(menuItem)
        }
    }

    private fun setupRecyclerView() {
        adapter = RuleAdapter(
            onToggle = { rule, isChecked ->
                toggleRule(rule.id, isChecked)
            },
            onEdit = { rule ->
                val intent = Intent(this, RuleEditActivity::class.java).apply {
                    putExtra("rule_id", rule.id)
                }
                startActivity(intent)
            }
        )
        
        binding.rvRules.layoutManager = LinearLayoutManager(this)
        binding.rvRules.adapter = adapter
    }

    private fun setupButtons() {
        binding.fabAdd.setOnClickListener {
            val intent = Intent(this, RuleEditActivity::class.java)
            startActivity(intent)
        }
        
        binding.btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
        
        binding.btnImportExport.setOnClickListener { view ->
            showImportExportMenu(view)
        }
    }

    private fun showImportExportMenu(view: View) {
        val popupMenu = PopupMenu(this, view)
        popupMenu.menu.add(0, 1, 0, R.string.import_rules)
        popupMenu.menu.add(0, 2, 0, R.string.export_rules)
        
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    importLauncher.launch("application/json")
                    true
                }
                2 -> {
                    exportLauncher.launch("diev_mabohao_rules.json")
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun updateModuleStatus() {
        val isActive = YukiHookAPI.Status.isXposedModuleActive
        val statusText = if (isActive) R.string.module_status_active else R.string.module_status_inactive
        binding.statusText.setText(statusText)
        
        val indicatorColor = if (isActive) R.color.status_active else R.color.status_inactive
        binding.statusIndicator.setBackgroundResource(R.drawable.circle_indicator)
        binding.statusIndicator.background.setTint(getColor(indicatorColor))
    }

    private fun loadRules() {
        val json = prefs(RuleRepository.PREFS_NAME).getString(RuleRepository.KEY_RULES, "")
        Log.i(TAG, "Loaded rules JSON: $json")
        val rules = RuleRepository.parseRules(json)
        adapter.submitList(rules)
        
        binding.tvEmpty.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
        binding.rvRules.visibility = if (rules.isEmpty()) View.GONE else View.VISIBLE
    }
    
    private fun toggleRule(ruleId: String, enabled: Boolean) {
        val json = prefs(RuleRepository.PREFS_NAME).getString(RuleRepository.KEY_RULES, "")
        val rules = RuleRepository.parseRules(json).toMutableList()
        val index = rules.indexOfFirst { it.id == ruleId }
        if (index != -1) {
            rules[index] = rules[index].copy(isEnabled = enabled)
            val newJson = RuleRepository.toJson(rules)
            Log.i(TAG, "Toggling rule $ruleId to $enabled, new JSON: $newJson")
            prefs(RuleRepository.PREFS_NAME).edit {
                putString(RuleRepository.KEY_RULES, newJson)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_import -> {
                importLauncher.launch("application/json")
                true
            }
            R.id.action_export -> {
                exportLauncher.launch("diev_mabohao_rules.json")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}