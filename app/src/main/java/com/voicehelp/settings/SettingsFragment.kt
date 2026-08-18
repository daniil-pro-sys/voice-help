package com.voicehelp.settings

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.Voice
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.voicehelp.R
import com.voicehelp.assistant.TtsManager
import com.voicehelp.data.SettingsStore
import com.voicehelp.llm.OpenAiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SettingsFragment : PreferenceFragmentCompat() {

    private var ttsManager: TtsManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = SettingsStore.PREFS_NAME
        val context = requireContext()
        if (!SettingsStore.prefs(context).contains("stt_language")) {
            SettingsStore.saveSttLanguage(context, SettingsStore.systemLanguage(context))
        }
        setPreferencesFromResource(R.xml.settings, rootKey)

        setupAssistantRolePreference()
        setupLlmPreferences()
        setupTtsPreferences()
        setupModelPreference()
    }

    override fun onResume() {
        super.onResume()
        updateAssistantRolePreference()
    }

    override fun onDestroyView() {
        ttsManager?.shutdown()
        ttsManager = null
        scope.cancel()
        super.onDestroyView()
    }

    private fun setupAssistantRolePreference() {
        val pref = findPreference<SwitchPreferenceCompat>("assistant_default") ?: return
        pref.setOnPreferenceChangeListener { _, _ ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            }
            false
        }
    }

    private fun updateAssistantRolePreference() {
        val pref = findPreference<SwitchPreferenceCompat>("assistant_default") ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            pref.isEnabled = false
            pref.summary = getString(R.string.assistant_default_unavailable)
            return
        }
        val roleManager = requireContext().getSystemService(RoleManager::class.java)
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            pref.isEnabled = false
            pref.summary = getString(R.string.assistant_default_unavailable)
            return
        }
        val held = roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
        pref.isChecked = held
        pref.summary = if (held) {
            getString(R.string.assistant_default_held)
        } else {
            getString(R.string.assistant_default_not_held)
        }
    }

    private fun setupLlmPreferences() {
        val refresh = findPreference<Preference>("llm_refresh_models") ?: return
        refresh.setOnPreferenceClickListener {
            refreshModels()
            true
        }
    }

    private fun refreshModels() {
        val baseUrl = SettingsStore.llmBaseUrl(requireContext())
        val apiKey = SettingsStore.llmApiKey(requireContext())
        val client = OpenAiClient(baseUrl, apiKey)
        scope.launch {
            val models = client.listModels()
            val pref = findPreference<ListPreference>("llm_model")
            if (models.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.llm_refresh_fail),
                    Toast.LENGTH_SHORT
                ).show()
                pref?.summary = getString(R.string.llm_models_empty)
                return@launch
            }
            pref?.entries = models.toTypedArray()
            pref?.entryValues = models.toTypedArray()
            SettingsStore.saveLlModels(requireContext(), models)
            val current = SettingsStore.llmModel(requireContext())
            pref?.value = if (current in models) current else models.first()
            Toast.makeText(
                requireContext(),
                getString(R.string.llm_refresh_ok, models.size),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupTtsPreferences() {
        val enginePref = findPreference<ListPreference>("tts_engine") ?: return
        val voicePref = findPreference<ListPreference>("tts_voice") ?: return

        val manager = TtsManager(requireContext())
        ttsManager = manager

        val engines = manager.listEngines()
        if (engines.isNotEmpty()) {
            enginePref.entries = engines.toTypedArray()
            enginePref.entryValues = engines.toTypedArray()
        }
        enginePref.setOnPreferenceChangeListener { _, newValue ->
            val engine = newValue as? String
            manager.shutdown()
            val fresh = TtsManager(requireContext())
            ttsManager = fresh
            fresh.init(engine) { _ ->
                activity?.runOnUiThread {
                    populateVoices(fresh, voicePref)
                }
            }
            true
        }

        manager.init(SettingsStore.ttsEngine(requireContext())) { _ ->
            activity?.runOnUiThread {
                populateVoices(manager, voicePref)
            }
        }
    }

    private fun populateVoices(manager: TtsManager, voicePref: ListPreference) {
        val voices: List<Voice> = manager.listVoices()
        val names = voices.map { it.name }
        val labels = voices.map { voice ->
            val lang = voice.locale.toLanguageTag()
            "${voice.name} ($lang)"
        }
        val allNames = listOf("") + names
        val allLabels = listOf(getString(R.string.tts_voice_default)) + labels
        voicePref.entries = allLabels.toTypedArray()
        voicePref.entryValues = allNames.toTypedArray()
        val current = SettingsStore.ttsVoice(requireContext())
        voicePref.value = if (current in names) current else ""
    }

    private fun setupModelPreference() {
        val modelPref = findPreference<Preference>("stt_model") ?: return
        modelPref.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), ModelActivity::class.java))
            true
        }
        val langPref = findPreference<ListPreference>("stt_language")
        langPref?.setOnPreferenceChangeListener { _, _ ->
            updateModelSummary()
            true
        }
        updateModelSummary()
    }

    private fun updateModelSummary() {
        val modelPref = findPreference<Preference>("stt_model") ?: return
        val lang = SettingsStore.sttLanguage(requireContext())
        val downloaded = com.voicehelp.assistant.VoskModelManager.isDownloaded(requireContext(), lang)
        modelPref.summary = getString(R.string.stt_model_summary) +
            " · " + if (downloaded) getString(R.string.model_status_downloaded) else getString(R.string.model_status_not_downloaded)
    }
}