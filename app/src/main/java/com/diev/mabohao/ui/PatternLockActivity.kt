package com.diev.mabohao.ui

import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.diev.mabohao.App
import com.diev.mabohao.data.RuleRepository
import com.diev.mabohao.databinding.ActivityPatternLockBinding
import com.diev.mabohao.ui.widget.PatternLockView
import com.highcapable.yukihookapi.hook.factory.prefs

class PatternLockActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DievMabohao"
        private const val MAX_ATTEMPTS = 5
        private const val COOLDOWN_MS = 30_000L
    }

    private lateinit var binding: ActivityPatternLockBinding
    private var failedAttempts = 0
    private var cooldownEndTime = 0L
    private var cooldownTimer: CountDownTimer? = null
    private var biometricAvailable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPatternLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 问题1: 拦截返回键
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 完全拦截返回键，什么都不做
            }
        })

        setupWindowInsets()
        setupPatternView()
        setupBiometric()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = insets.top, bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cooldownTimer?.cancel()
    }

    private fun setupPatternView() {
        binding.patternLockView.setOnPatternListener(object : PatternLockView.OnPatternListener {
            override fun onPatternStart() {
                if (System.currentTimeMillis() < cooldownEndTime) return
                binding.tvHint.text = "请绘制解锁图案"
            }

            override fun onPatternTooShort(count: Int) {
                binding.tvHint.text = "至少连接4个点，当前$count 个"
            }

            override fun onPatternComplete(pattern: List<Int>) {
                // 冷却中
                if (System.currentTimeMillis() < cooldownEndTime) {
                    binding.patternLockView.setState(PatternLockView.State.ERROR)
                    binding.patternLockView.postDelayed({
                        binding.patternLockView.clearPattern()
                    }, 500)
                    return
                }

                val inputHash = PatternSetupActivity.hashPattern(pattern)
                val savedHash = prefs(RuleRepository.PREFS_NAME).getString(RuleRepository.KEY_LOCK_PATTERN, "")

                if (inputHash == savedHash) {
                    // 解锁成功
                    binding.patternLockView.setState(PatternLockView.State.SUCCESS)
                    binding.tvHint.text = "解锁成功"
                    failedAttempts = 0
                    App.setUnlocked()
                    finish()
                } else {
                    // 解锁失败
                    failedAttempts++
                    binding.patternLockView.setState(PatternLockView.State.ERROR)
                    binding.patternLockView.shakeAnimation()
                    binding.patternLockView.vibrateError()

                    if (failedAttempts >= MAX_ATTEMPTS) {
                        cooldownEndTime = System.currentTimeMillis() + COOLDOWN_MS
                        failedAttempts = 0
                        startCooldownTimer()
                    } else {
                        binding.tvHint.text = "图案错误，还剩${MAX_ATTEMPTS - failedAttempts}次机会"
                    }

                    binding.patternLockView.postDelayed({
                        binding.patternLockView.clearPattern()
                    }, 1000)
                }
            }
        })
    }

    private fun startCooldownTimer() {
        cooldownTimer?.cancel()
        val remaining = cooldownEndTime - System.currentTimeMillis()
        cooldownTimer = object : CountDownTimer(remaining, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000).toInt()
                binding.tvHint.text = "错误次数过多，请${seconds}秒后再试"
            }

            override fun onFinish() {
                binding.tvHint.text = "请绘制解锁图案"
                cooldownEndTime = 0L
            }
        }.start()
    }

    private fun setupBiometric() {
        val fingerprintEnabled = prefs(RuleRepository.PREFS_NAME).getBoolean(RuleRepository.KEY_FINGERPRINT_ENABLED, false)
        if (!fingerprintEnabled) return

        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)

        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            biometricAvailable = true
            binding.btnFingerprint.visibility = View.VISIBLE
            binding.tvFingerprintHint.visibility = View.VISIBLE

            // 点击指纹按钮唤起指纹
            binding.btnFingerprint.setOnClickListener {
                showBiometricPrompt()
            }

            // 自动弹出指纹
            showBiometricPrompt()
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.i(TAG, "Biometric authentication succeeded")
                binding.tvHint.text = "解锁成功"
                binding.patternLockView.setState(PatternLockView.State.SUCCESS)
                App.setUnlocked()
                finish()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.i(TAG, "Biometric authentication error: $errString")
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.i(TAG, "Biometric authentication failed")
            }
        }

        val biometricPrompt = BiometricPrompt(this, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("暗码启动器")
            .setSubtitle("使用指纹解锁")
            .setNegativeButtonText("使用图案")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}