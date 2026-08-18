package com.voicehelp

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.voicehelp.databinding.ActivityMainBinding
import com.voicehelp.help.HelpActivity
import com.voicehelp.settings.SettingsActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnMic.setOnClickListener {
            startActivity(Intent(this, AssistantActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnCommands.setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }
        binding.textAssistStatus.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startActivity(Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateAssistantRoleStatus()
    }

    private fun updateAssistantRoleStatus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            binding.textAssistStatus.text = getString(R.string.assistant_default_unavailable)
            return
        }
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            val held = roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
            binding.textAssistStatus.text = if (held) {
                getString(R.string.main_default_assistant_yes)
            } else {
                getString(R.string.main_default_assistant_no)
            }
        } else {
            binding.textAssistStatus.text = getString(R.string.assistant_default_unavailable)
        }
    }
}