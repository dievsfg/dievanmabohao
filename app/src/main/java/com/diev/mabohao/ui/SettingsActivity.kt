package com.diev.mabohao.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.diev.mabohao.App
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

    private val patternSetupLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            updateSecurityUI()
        } else {
            val lockEnabled = prefs(RuleRepository.PREFS_NAME).getBoolean(RuleRepository.KEY_LOCK_ENABLED, false)
            binding.switchLock.isChecked = lockEnabled
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupWindowInsets()
        setupToolbar()
        loadSettings()
        setupThemeSettings()
        setupButtons()
        setupSecuritySettings()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // 状态栏高度的 padding 加给 AppBarLayout
            val appBarLayout = binding.root.getChildAt(0)
            appBarLayout.updatePadding(top = insets.top)
            
            // 滚动视图底部增加导航栏高度的 padding
            val scrollView = binding.root.getChildAt(1)
            scrollView.updatePadding(bottom = insets.bottom)
            
            WindowInsetsCompat.CONSUMED
        }
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

    private fun setupThemeSettings() {
        updateThemeText()

        binding.layoutThemeMode.setOnClickListener {
            showThemeModeDialog()
        }
    }

    private fun updateThemeText() {
        val themeMode = prefs(RuleRepository.PREFS_NAME).getInt(RuleRepository.KEY_THEME_MODE, 0)
        val themeNames = arrayOf("跟随系统", "浅色模式", "暗色模式")
        binding.tvThemeMode.text = themeNames.getOrElse(themeMode) { themeNames[0] }
    }

    private fun showThemeModeDialog() {
        val currentMode = prefs(RuleRepository.PREFS_NAME).getInt(RuleRepository.KEY_THEME_MODE, 0)
        val items = arrayOf("跟随系统", "浅色模式", "暗色模式")

        AlertDialog.Builder(this)
            .setTitle("主题模式")
            .setSingleChoiceItems(items, currentMode) { dialog, which ->
                prefs(RuleRepository.PREFS_NAME).edit {
                    putInt(RuleRepository.KEY_THEME_MODE, which)
                }
                updateThemeText()
                App.applyTheme(this)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener {
            saveSettings()
        }
    }

    private fun setupSecuritySettings() {
        val lockEnabled = prefs(RuleRepository.PREFS_NAME).getBoolean(RuleRepository.KEY_LOCK_ENABLED, false)
        val fingerprintEnabled = prefs(RuleRepository.PREFS_NAME).getBoolean(RuleRepository.KEY_FINGERPRINT_ENABLED, false)

        binding.switchLock.isChecked = lockEnabled
        binding.switchFingerprint.isChecked = fingerprintEnabled

        updateSecurityUI()

        // 密码保护开关
        binding.switchLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val pattern = prefs(RuleRepository.PREFS_NAME).getString(RuleRepository.KEY_LOCK_PATTERN, "")
                if (pattern.isNullOrEmpty()) {
                    val intent = Intent(this, PatternSetupActivity::class.java)
                    patternSetupLauncher.launch(intent)
                } else {
                    prefs(RuleRepository.PREFS_NAME).edit {
                        putBoolean(RuleRepository.KEY_LOCK_ENABLED, true)
                    }
                    updateSecurityUI()
                }
            } else {
                prefs(RuleRepository.PREFS_NAME).edit {
                    putBoolean(RuleRepository.KEY_LOCK_ENABLED, false)
                }
                updateSecurityUI()
            }
        }

        // 修改图案密码
        binding.layoutChangePattern.setOnClickListener {
            val intent = Intent(this, PatternSetupActivity::class.java)
            patternSetupLauncher.launch(intent)
        }

        // 指纹开关
        binding.switchFingerprint.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val biometricManager = BiometricManager.from(this)
                val canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
                    binding.switchFingerprint.isChecked = false
                    Snackbar.make(binding.root, "设备不支持指纹或未注册指纹", Snackbar.LENGTH_SHORT).show()
                    return@setOnCheckedChangeListener
                }
            }
            prefs(RuleRepository.PREFS_NAME).edit {
                putBoolean(RuleRepository.KEY_FINGERPRINT_ENABLED, isChecked)
            }
        }

        // 锁定方式
        binding.layoutLockStrategy.setOnClickListener {
            showLockStrategyDialog()
        }

        // 延时时间
        binding.layoutLockDelay.setOnClickListener {
            showLockDelayDialog()
        }
    }

    private fun updateSecurityUI() {
        val lockEnabled = prefs(RuleRepository.PREFS_NAME).getBoolean(RuleRepository.KEY_LOCK_ENABLED, false)
        val strategy = prefs(RuleRepository.PREFS_NAME).getInt(RuleRepository.KEY_LOCK_STRATEGY, 0)

        val visibility = if (lockEnabled) View.VISIBLE else View.GONE
        binding.layoutChangePattern.visibility = visibility
        binding.dividerFingerprint.visibility = visibility
        binding.layoutFingerprint.visibility = visibility
        binding.dividerLockStrategy.visibility = visibility
        binding.layoutLockStrategy.visibility = visibility

        binding.layoutLockDelay.visibility = if (lockEnabled && strategy == 1) View.VISIBLE else View.GONE

        val strategyNames = arrayOf("返回后台立即锁定", "返回后台延时锁定", "返回后台锁屏后锁定")
        binding.tvLockStrategy.text = strategyNames.getOrElse(strategy) { strategyNames[0] }

        val delay = prefs(RuleRepository.PREFS_NAME).getLong(RuleRepository.KEY_LOCK_DELAY, 5 * 60 * 1000L)
        binding.tvLockDelay.text = formatDelay(delay)
    }

    private fun showLockStrategyDialog() {
        val currentStrategy = prefs(RuleRepository.PREFS_NAME).getInt(RuleRepository.KEY_LOCK_STRATEGY, 0)
        val items = arrayOf("返回后台立即锁定", "返回后台延时锁定", "返回后台锁屏后锁定")

        AlertDialog.Builder(this)
            .setTitle("锁定方式")
            .setSingleChoiceItems(items, currentStrategy) { dialog, which ->
                prefs(RuleRepository.PREFS_NAME).edit {
                    putInt(RuleRepository.KEY_LOCK_STRATEGY, which)
                }
                updateSecurityUI()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showLockDelayDialog() {
        val currentDelay = prefs(RuleRepository.PREFS_NAME).getLong(RuleRepository.KEY_LOCK_DELAY, 5 * 60 * 1000L)
        val presets = arrayOf(
            "1分钟" to 60_000L,
            "2分钟" to 120_000L,
            "5分钟" to 300_000L,
            "10分钟" to 600_000L,
            "30分钟" to 1_800_000L,
            "自定义" to -1L
        )
        val items = presets.map { it.first }.toTypedArray()
        val currentIndex = presets.indexOfFirst { it.second == currentDelay }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("延时时间")
            .setSingleChoiceItems(items, currentIndex) { dialog, which ->
                if (presets[which].second == -1L) {
                    dialog.dismiss()
                    showCustomDelayDialog()
                } else {
                    prefs(RuleRepository.PREFS_NAME).edit {
                        putLong(RuleRepository.KEY_LOCK_DELAY, presets[which].second)
                    }
                    updateSecurityUI()
                    dialog.dismiss()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCustomDelayDialog() {
        val editText = EditText(this).apply {
            hint = "请输入分钟数"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        AlertDialog.Builder(this)
            .setTitle("自定义延时时间")
            .setView(editText)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val minutes = editText.text.toString().toLongOrNull()
                if (minutes != null && minutes > 0) {
                    prefs(RuleRepository.PREFS_NAME).edit {
                        putLong(RuleRepository.KEY_LOCK_DELAY, minutes * 60 * 1000L)
                    }
                    updateSecurityUI()
                } else {
                    Snackbar.make(binding.root, "请输入有效的分钟数", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun formatDelay(delayMs: Long): String {
        val minutes = delayMs / 60_000
        return if (minutes >= 60) {
            val hours = minutes / 60
            val remainMinutes = minutes % 60
            if (remainMinutes > 0) "${hours}小时${remainMinutes}分钟" else "${hours}小时"
        } else {
            "${minutes}分钟"
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
        
        val toast = Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT)
        // 位置：底部向上偏移 1/4 屏幕高度
        val yOffset = resources.displayMetrics.heightPixels / 4
        toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, yOffset)
        toast.show()

        // 1秒后自动隐藏
        Handler(Looper.getMainLooper()).postDelayed({
            toast.cancel()
        }, 1000)
    }
}