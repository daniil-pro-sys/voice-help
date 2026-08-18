package com.voicehelp.help

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.voicehelp.data.CustomTrigger
import com.voicehelp.databinding.ItemHelpBuiltinBinding
import com.voicehelp.databinding.ItemHelpHeaderBinding
import com.voicehelp.databinding.ItemHelpTriggerBinding

sealed class HelpItem {
    data class Header(val text: String) : HelpItem()
    data class Builtin(val name: String, val examples: String, val desc: String) : HelpItem()
    data class Trigger(val trigger: CustomTrigger, val actionLabel: String) : HelpItem()
}

class HelpAdapter(
    private val items: List<HelpItem>,
    private val onTriggerClick: (CustomTrigger) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_BUILTIN = 1
        private const val TYPE_TRIGGER = 2
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is HelpItem.Header -> TYPE_HEADER
        is HelpItem.Builtin -> TYPE_BUILTIN
        is HelpItem.Trigger -> TYPE_TRIGGER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(ItemHelpHeaderBinding.inflate(inflater, parent, false))
            TYPE_BUILTIN -> BuiltinHolder(ItemHelpBuiltinBinding.inflate(inflater, parent, false))
            else -> TriggerHolder(ItemHelpTriggerBinding.inflate(inflater, parent, false))
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is HelpItem.Header -> (holder as HeaderHolder).bind(item)
            is HelpItem.Builtin -> (holder as BuiltinHolder).bind(item)
            is HelpItem.Trigger -> (holder as TriggerHolder).bind(item, onTriggerClick)
        }
    }

    class HeaderHolder(private val binding: ItemHelpHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HelpItem.Header) {
            binding.root.text = item.text
        }
    }

    class BuiltinHolder(private val binding: ItemHelpBuiltinBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HelpItem.Builtin) {
            binding.textCmdName.text = item.name
            binding.textCmdExamples.text = item.examples
            binding.textCmdDesc.text = item.desc
        }
    }

    class TriggerHolder(private val binding: ItemHelpTriggerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HelpItem.Trigger, onClick: (CustomTrigger) -> Unit) {
            binding.textTriggerName.text = item.trigger.name
            binding.textTriggerAction.text = item.actionLabel
            binding.textTriggerPhrases.text = item.trigger.phrases.joinToString(", ")
            binding.root.setOnClickListener { onClick(item.trigger) }
        }
    }
}