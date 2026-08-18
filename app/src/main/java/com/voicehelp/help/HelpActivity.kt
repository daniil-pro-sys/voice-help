package com.voicehelp.help

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.voicehelp.R
import com.voicehelp.data.CustomTrigger
import com.voicehelp.data.CustomTriggersStore
import com.voicehelp.databinding.ActivityHelpBinding

class HelpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHelpBinding
    private lateinit var adapter: HelpAdapter

    private val actionKeys = listOf(
        "reply", "call", "sms", "timer", "alarm",
        "flashlight", "photo", "open_app", "search", "time"
    )

    private fun actionLabel(action: String): String = when (action) {
        "call" -> getString(R.string.action_call)
        "sms" -> getString(R.string.action_sms)
        "timer" -> getString(R.string.action_timer)
        "alarm" -> getString(R.string.action_alarm)
        "flashlight" -> getString(R.string.action_flashlight)
        "photo" -> getString(R.string.action_photo)
        "open_app" -> getString(R.string.action_open_app)
        "search" -> getString(R.string.action_search)
        "time" -> getString(R.string.action_time)
        else -> getString(R.string.action_reply)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setTitle(R.string.help_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.recyclerHelp.layoutManager = LinearLayoutManager(this)
        refreshList()

        binding.fabAdd.setOnClickListener { showTriggerDialog(null) }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun refreshList() {
        val triggers = CustomTriggersStore.load(this)
        val items = mutableListOf<HelpItem>()
        items.add(HelpItem.Header(getString(R.string.help_builtin_header)))
        builtinCommands().forEach { items.add(it) }
        items.add(HelpItem.Header(getString(R.string.help_custom_header)))
        if (triggers.isEmpty()) {
            items.add(HelpItem.Builtin("—", "", getString(R.string.help_custom_empty)))
        } else {
            triggers.forEach {
                items.add(HelpItem.Trigger(it, actionLabel(it.action)))
            }
        }
        adapter = HelpAdapter(items) { trigger -> showTriggerDialog(trigger) }
        binding.recyclerHelp.adapter = adapter
    }

    private fun builtinCommands(): List<HelpItem.Builtin> = listOf(
        HelpItem.Builtin(
            getString(R.string.action_call),
            "«Позвони маме», «Позвони 89161234567»",
            getString(R.string.help_desc_call)
        ),
        HelpItem.Builtin(
            getString(R.string.action_sms),
            "«Отправь сообщение маме приду в семь»",
            getString(R.string.help_desc_sms)
        ),
        HelpItem.Builtin(
            getString(R.string.action_timer),
            "«Поставь таймер на 5 минут», «Таймер на 30 секунд»",
            getString(R.string.help_desc_timer)
        ),
        HelpItem.Builtin(
            getString(R.string.action_alarm),
            "«Будильник на 7:30», «Будильник на 8 утра»",
            getString(R.string.help_desc_alarm)
        ),
        HelpItem.Builtin(
            getString(R.string.action_flashlight),
            "«Включи фонарик», «Выключи фонарик»",
            getString(R.string.help_desc_flashlight)
        ),
        HelpItem.Builtin(
            getString(R.string.action_photo),
            "«Сделай фото»",
            getString(R.string.help_desc_photo)
        ),
        HelpItem.Builtin(
            getString(R.string.action_open_app),
            "«Открой телеграм»",
            getString(R.string.help_desc_open_app)
        ),
        HelpItem.Builtin(
            getString(R.string.action_search),
            "«Найди в интернете погоду в Москве»",
            getString(R.string.help_desc_search)
        ),
        HelpItem.Builtin(
            getString(R.string.action_time),
            "«Который час?»",
            getString(R.string.help_desc_time)
        ),
        HelpItem.Builtin(
            getString(R.string.action_stop),
            "«Стоп», «Хватит»",
            getString(R.string.help_desc_stop)
        )
    )

    private fun showTriggerDialog(existing: CustomTrigger?) {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_trigger, null)
        val nameInput = view.findViewById<EditText>(R.id.inputName)
        val phrasesInput = view.findViewById<EditText>(R.id.inputPhrases)
        val spinner = view.findViewById<Spinner>(R.id.spinnerAction)
        val paramInput = view.findViewById<EditText>(R.id.inputParam)
        val replyInput = view.findViewById<EditText>(R.id.inputReply)

        val labels = actionKeys.map { actionLabel(it) }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        if (existing != null) {
            nameInput.setText(existing.name)
            phrasesInput.setText(existing.phrases.joinToString(", "))
            val idx = actionKeys.indexOf(existing.action).coerceAtLeast(0)
            spinner.setSelection(idx)
            paramInput.setText(existing.param)
            replyInput.setText(existing.reply)
        }

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) R.string.help_add_trigger else R.string.help_edit_trigger)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameInput.text.toString().trim()
                val phrases = phrasesInput.text.toString().split(",")
                    .map { it.trim() }.filter { it.isNotEmpty() }
                if (name.isEmpty() || phrases.isEmpty()) return@setPositiveButton
                val action = actionKeys[spinner.selectedItemPosition]
                val trigger = CustomTrigger(
                    id = existing?.id ?: CustomTriggersStore.newId(),
                    name = name,
                    phrases = phrases,
                    action = action,
                    param = paramInput.text.toString().trim(),
                    reply = replyInput.text.toString().trim()
                )
                if (existing == null) {
                    CustomTriggersStore.add(this, trigger)
                } else {
                    CustomTriggersStore.update(this, trigger)
                }
                refreshList()
            }

        if (existing != null) {
            builder.setNeutralButton(R.string.delete) { _, _ ->
                CustomTriggersStore.remove(this, existing.id)
                refreshList()
            }
        }
        builder.show()
    }
}