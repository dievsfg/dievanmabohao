package com.diev.mabohao.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.diev.mabohao.data.RuleRepository
import com.diev.mabohao.databinding.ActivityPatternLockBinding
import com.diev.mabohao.ui.widget.PatternLockView
import com.highcapable.yukihookapi.hook.factory.prefs
import java.security.MessageDigest

class PatternLockActivity : AppCompatActivity() {

    companion object {
        const val ACTION_CREATE_PATTERN = "create_pattern"
        const val ACTION_VERIFY_PATTERN = "verify_pattern"
        
        const val EXTRA_ACTION = "action"
        const val RESULT_SUCCESS = 1
        const val RESULT_FAILED = 0

        // Use SHA-256 for pattern hashing
        fun hashPattern(pattern: List<Int>): String {
            val bytes = pattern.joinToString("").toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            return digest.fold("") { str, it -> str + "%02x".format(it) }
        }
        
        var isAppUnlocked = false
    }

    private lateinit var binding: ActivityPatternLockBinding
    private var action: String = ACTION_VERIFY_PATTERN
    private var firstPattern: List<Int>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure nothing can bypass this if it's meant to be full screen
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        
        binding = ActivityPatternLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        action = intent.getStringExtra(EXTRA_ACTION) ?: ACTION_VERIFY_PATTERN

        setupUI()
        setupPatternLock()
        
        if (action == ACTION_VERIFY_PATTERN) {
            checkAndPromptBiometric()
        }
    }

    override fun onBackPressed() {
        if (action == ACTION_VERIFY_PATTERN) {
            // Cannot bypass lock, close the app
            finishAffinity()
        } else {
            super.onBackPressed()
        }
    }

    private fun setupUI() {
        when (action) {
            ACTION_CREATE_PATTERN -> {
                binding.tvTitle.text = "绘制解锁图案"
                binding.tvSubtitle.text = "至少连接4个点"
                binding.btnFingerprint.visibility = View.GONE
            }
            ACTION_VERIFY_PATTERN -> {
                binding.tvTitle.text = "输入密码"
                binding.tvSubtitle.text = "请绘制您的解锁图案"
                
                val useFingerprint = prefs(RuleRepository.PREFS_NAME).getBoolean(RuleRepository.KEY_APP_LOCK_FINGERPRINT, false)
                if (useFingerprint) {
                    binding.btnFingerprint.visibility = View.VISIBLE
                    binding.btnFingerprint.setOnClickListener {
                        showBiometricPrompt()
                    }
                }
            }
        }
    }

    private fun setupPatternLock() {
        binding.patternLockView.onPatternListener = object : PatternLockView.OnPatternListener {
            override fun onComplete(pattern: List<Int>) {
                handlePattern(pattern)
            }

            override fun onCleared() {
                // Clear any error states if necessary
                binding.tvTitle.text = "绘制解锁图案"
                binding.tvSubtitle.text = ""
            }
        }
    }

    private fun handlePattern(pattern: List<Int>) {
        if (pattern.size < 4) {
            binding.patternLockView.setErrorState()
            binding.tvTitle.text = "图案太短"
            binding.tvSubtitle.text = "至少连接4个点，请重试"
            Handler(Looper.getMainLooper()).postDelayed({
                binding.patternLockView.resetPattern()
                binding.tvTitle.text = "绘制解锁图案"
                binding.tvSubtitle.text = ""
            }, 1000)
            return
        }

        when (action) {
            ACTION_CREATE_PATTERN -> {
                if (firstPattern == null) {
                    firstPattern = pattern
                    binding.tvTitle.text = "再次绘制图案"
                    binding.tvSubtitle.text = "请再次绘制以确认"
                    Handler(Looper.getMainLooper()).postDelayed({
                        binding.patternLockView.resetPattern()
                    }, 500)
                } else {
                    if (firstPattern == pattern) {
                        savePattern(pattern)
                        Toast.makeText(this, "密码设置成功", Toast.LENGTH_SHORT).show()
                        setResult(Activity.RESULT_OK)
                        finish()
                    } else {
                        binding.patternLockView.setErrorState()
                        binding.tvTitle.text = "图案不一致"
                        binding.tvSubtitle.text = "与第一次绘制的图案不同，请重试"
                        firstPattern = null
                        Handler(Looper.getMainLooper()).postDelayed({
                            binding.patternLockView.resetPattern()
                            binding.tvTitle.text = "绘制解锁图案"
                            binding.tvSubtitle.text = "至少连接4个点"
                        }, 1000)
                    }
                }
            }
            ACTION_VERIFY_PATTERN -> {
                val savedHash = prefs(RuleRepository.PREFS_NAME).getString(RuleRepository.KEY_APP_LOCK_PATTERN, "")
                val currentHash = hashPattern(pattern)

                if (savedHash == currentHash) {
                    unlockSuccess()
                } else {
                    binding.patternLockView.setErrorState()
                    binding.tvTitle.text = "密码错误"
                    binding.tvSubtitle.text = "请重新绘制"
                    Handler(Looper.getMainLooper()).postDelayed({
                        binding.patternLockView.resetPattern()
                        binding.tvTitle.text = "输入密码"
                        binding.tvSubtitle.text = "请绘制您的解锁图案"
                    }, 1000)
                }
            }
        }
    }

    private fun savePattern(pattern: List<Int>) {
        val hash = hashPattern(pattern)
        prefs(RuleRepository.PREFS_NAME).edit {
            putString(RuleRepository.KEY_APP_LOCK_PATTERN, hash)
            putBoolean(RuleRepository.KEY_APP_LOCK_ENABLED, true)
        }
    }

    private fun unlockSuccess() {
        isAppUnlocked = true
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun checkAndPromptBiometric() {
        val useFingerprint = prefs(RuleRepository.PREFS_NAME).getBoolean(RuleRepository.KEY_APP_LOCK_FINGERPRINT, false)
        if (useFingerprint) {
            val biometricManager = BiometricManager.from(this)
            if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS) {
                showBiometricPrompt()
            }
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // If error (like user cancelled), they can still use pattern lock
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    unlockSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(this@PatternLockActivity, "指纹识别失败", Toast.LENGTH_SHORT).show()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("指纹解锁")
            .setSubtitle("使用您的指纹解锁应用")
            .setNegativeButtonText("使用图案密码")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}