package com.voicehelp

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.animation.LinearInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.voicehelp.assistant.AssistantEngine
import com.voicehelp.databinding.ActivityAssistantBinding

class AssistantActivity : AppCompatActivity(), AssistantEngine.UiCallbacks {

    private lateinit var binding: ActivityAssistantBinding
    private var engine: AssistantEngine? = null
    private var pulseAnimator: ValueAnimator? = null
    private var circleAnimator: ValueAnimator? = null

    private val permissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.POST_NOTIFICATIONS
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startEngine()
        } else {
            showStatus(getString(R.string.assistant_status_mic_denied), getColor(com.voicehelp.R.color.mic_idle))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAssistantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnClose.setOnClickListener { finish() }
        binding.textFinal.isVisible = false
        binding.textPartial.isVisible = false

        requestNeededPermissions()
    }

    private fun requestNeededPermissions() {
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startEngine()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startEngine() {
        if (engine != null) return
        val e = AssistantEngine(applicationContext, this)
        engine = e
        e.startSession()
    }

    override fun onDestroy() {
        stopPulse()
        engine?.cancel()
        engine = null
        super.onDestroy()
    }

    override fun onStatus(status: AssistantEngine.Status) {
        val text = when (status) {
            AssistantEngine.Status.IDLE -> getString(R.string.assistant_status_idle)
            AssistantEngine.Status.LISTENING -> getString(R.string.assistant_status_listening)
            AssistantEngine.Status.RECOGNIZING -> getString(R.string.assistant_status_recognizing)
            AssistantEngine.Status.THINKING -> getString(R.string.assistant_status_thinking)
            AssistantEngine.Status.SPEAKING -> getString(R.string.assistant_status_speaking)
            AssistantEngine.Status.ERROR -> getString(R.string.assistant_status_error)
        }
        val color = when (status) {
            AssistantEngine.Status.LISTENING -> getColor(com.voicehelp.R.color.mic_listening)
            AssistantEngine.Status.RECOGNIZING,
            AssistantEngine.Status.THINKING -> getColor(com.voicehelp.R.color.mic_processing)
            AssistantEngine.Status.SPEAKING -> getColor(com.voicehelp.R.color.mic_speaking)
            else -> getColor(com.voicehelp.R.color.mic_idle)
        }
        showStatus(text, color)
        if (status == AssistantEngine.Status.LISTENING) {
            startPulse(color)
        } else {
            stopPulse()
        }
    }

    private fun showStatus(text: String, color: Int) {
        binding.textStatus.text = text
        binding.circleMic.backgroundTintList =
            android.content.res.ColorStateList.valueOf(color)
    }

    private fun startPulse(color: Int) {
        stopPulse()
        pulseAnimator = ValueAnimator.ofFloat(1.0f, 1.08f).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val scale = it.animatedValue as Float
                binding.circleMic.scaleX = scale
                binding.circleMic.scaleY = scale
            }
            start()
        }

        val darker = blend(color, 0x66000000)
        circleAnimator = ValueAnimator.ofObject(ArgbEvaluator(), color, darker).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                val c = it.animatedValue as Int
                binding.circleMic.backgroundTintList = android.content.res.ColorStateList.valueOf(c)
            }
            start()
        }
    }

    private fun blend(color: Int, mask: Int): Int {
        val a = (color shr 24 and 0xFF) * (mask shr 24 and 0xFF) / 255
        val r = (color shr 16 and 0xFF) * (mask shr 16 and 0xFF) / 255
        val g = (color shr 8 and 0xFF) * (mask shr 8 and 0xFF) / 255
        val b = (color and 0xFF) * (mask and 0xFF) / 255
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        circleAnimator?.cancel()
        circleAnimator = null
        binding.circleMic.scaleX = 1.0f
        binding.circleMic.scaleY = 1.0f
    }

    override fun onPartial(text: String) {
        binding.textPartial.isVisible = true
        binding.textPartial.text = text
    }

    override fun onFinalText(text: String) {
        if (text.isNotBlank()) {
            binding.textFinal.isVisible = true
            binding.textFinal.text = getString(R.string.assistant_you_prefix, text)
        }
    }

    override fun onReply(text: String) {
        binding.textFinal.isVisible = true
        binding.textPartial.isVisible = false
        binding.textPartial.text = ""
        binding.textFinal.text = getString(R.string.assistant_reply_prefix, text)
    }
}