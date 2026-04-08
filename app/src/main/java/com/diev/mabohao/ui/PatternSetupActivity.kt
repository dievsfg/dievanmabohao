package com.diev.mabohao.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.diev.mabohao.data.RuleRepository
import com.diev.mabohao.databinding.ActivityPatternSetupBinding
import com.diev.mabohao.ui.widget.PatternLockView
import com.highcapable.yukihookapi.hook.factory.prefs
import java.security.MessageDigest

class PatternSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPatternSetupBinding
    private var firstPattern: List<Int>? = null
    private var isConfirmStep = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPatternSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupPatternView()
        setupButtons()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = insets.top, bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupPatternView() {
        binding.patternLockView.setOnPatternListener(object : PatternLockView.OnPatternListener {
            override fun onPatternComplete(pattern: List<Int>) {
                if (!isConfirmStep) {
                    // 第一次绘制
                    firstPattern = pattern
                    isConfirmStep = true
                    binding.tvHint.text = "请再次绘制相同图案以确认"
                    binding.patternLockView.setState(PatternLockView.State.SUCCESS)
                    binding.patternLockView.postDelayed({
                        binding.patternLockView.clearPattern()
                    }, 500)
                } else {
                    // 确认绘制
                    if (pattern == firstPattern) {
                        // 匹配成功，保存密码
                        savePattern(pattern)
                        binding.patternLockView.setState(PatternLockView.State.SUCCESS)
                        Toast.makeText(this@PatternSetupActivity, "图案密码设置成功", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        // 不匹配
                        binding.tvHint.text = "图案不一致，请重新绘制"
                        binding.patternLockView.setState(PatternLockView.State.ERROR)
                        isConfirmStep = false
                        firstPattern = null
                        binding.patternLockView.postDelayed({
                            binding.patternLockView.clearPattern()
                            binding.tvHint.text = "请绘制至少连接4个点的图案"
                        }, 1000)
                    }
                }
            }
        })
    }

    private fun setupButtons() {
        binding.btnCancel.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        binding.btnReset.setOnClickListener {
            firstPattern = null
            isConfirmStep = false
            binding.patternLockView.clearPattern()
            binding.tvHint.text = "请绘制至少连接4个点的图案"
        }
    }

    private fun savePattern(pattern: List<Int>) {
        val hash = hashPattern(pattern)
        prefs(RuleRepository.PREFS_NAME).edit {
            putString(RuleRepository.KEY_LOCK_PATTERN, hash)
            putBoolean(RuleRepository.KEY_LOCK_ENABLED, true)
        }
    }

    companion object {
        fun hashPattern(pattern: List<Int>): String {
            val patternString = pattern.joinToString("-")
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(patternString.toByteArray())
            return hash.joinToString("") { "%02x".format(it) }
        }
    }
}