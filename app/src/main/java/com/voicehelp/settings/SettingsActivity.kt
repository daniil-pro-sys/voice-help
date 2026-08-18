package com.voicehelp.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.voicehelp.R
import com.voicehelp.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setTitle(R.string.settings_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}