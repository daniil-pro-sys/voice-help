package com.voicehelp.settings

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.voicehelp.R
import com.voicehelp.assistant.VoskModelManager
import com.voicehelp.databinding.ActivityModelBinding
import com.voicehelp.databinding.ItemModelBinding

class ModelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityModelBinding

    private val modelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val lang = intent.getStringExtra(ModelDownloadService.EXTRA_LANG) ?: return
            when (intent.action) {
                ModelDownloadService.ACTION_MODEL_PROGRESS -> {
                    val percent = intent.getIntExtra("percent", 0)
                    findCard(lang)?.let { card ->
                        card.textModelStatus.text =
                            getString(R.string.model_status_downloading, percent)
                        card.progressModel.progress = percent
                    }
                }
                ModelDownloadService.ACTION_MODEL_FINISHED -> {
                    val ok = intent.getBooleanExtra("ok", false)
                    findCard(lang)?.let { card ->
                        if (ok) {
                            card.textModelStatus.text = getString(R.string.model_status_downloaded)
                            card.progressModel.isVisible = false
                            card.btnDownload.isEnabled = false
                            card.btnDelete.isEnabled = true
                        } else {
                            card.textModelStatus.text = getString(
                                R.string.model_download_failed,
                                intent.getStringExtra("error") ?: "?"
                            )
                            card.progressModel.isVisible = false
                            card.btnDownload.isEnabled = true
                            card.btnDelete.isEnabled = false
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityModelBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setTitle(R.string.model_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        addModelCard("ru", getString(R.string.model_lang_ru))
        addModelCard("en", getString(R.string.model_lang_en))
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(ModelDownloadService.ACTION_MODEL_PROGRESS)
            addAction(ModelDownloadService.ACTION_MODEL_FINISHED)
        }
        ContextCompat.registerReceiver(this, modelReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        refreshAll()
    }

    override fun onPause() {
        unregisterReceiver(modelReceiver)
        super.onPause()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun addModelCard(lang: String, title: String) {
        val itemBinding = ItemModelBinding.inflate(layoutInflater, binding.modelContainer, false)
        itemBinding.textModelTitle.text = title
        binding.modelContainer.addView(itemBinding.root)

        itemBinding.btnDownload.setOnClickListener {
            startDownload(lang, itemBinding)
        }
        itemBinding.btnDelete.setOnClickListener {
            VoskModelManager.delete(this, lang)
            refreshCard(lang, itemBinding)
        }
        refreshCard(lang, itemBinding)
    }

    private fun refreshAll() {
        for (lang in VoskModelManager.MODELS.keys) {
            val card = findCard(lang) ?: continue
            refreshCard(lang, card)
        }
    }

    private fun findCard(lang: String): ItemModelBinding? {
        for (i in 0 until binding.modelContainer.childCount) {
            val tag = binding.modelContainer.getChildAt(i).tag
            if (tag == lang) {
                return ItemModelBinding.bind(binding.modelContainer.getChildAt(i))
            }
        }
        return null
    }

    private fun refreshCard(lang: String, item: ItemModelBinding) {
        item.root.tag = lang
        val downloading = ModelDownloadService.isDownloading(lang)
        val downloaded = VoskModelManager.isDownloaded(this, lang)
        if (downloading) {
            item.textModelStatus.text = getString(R.string.model_status_downloading, 0)
            item.progressModel.isVisible = true
        } else if (downloaded) {
            item.textModelStatus.text = getString(R.string.model_status_downloaded)
            item.progressModel.isVisible = false
        } else {
            item.textModelStatus.text = getString(R.string.model_status_not_downloaded)
            item.progressModel.isVisible = false
        }
        item.btnDownload.isEnabled = !downloading && !downloaded
        item.btnDelete.isEnabled = downloaded && !downloading
    }

    private fun startDownload(lang: String, item: ItemModelBinding) {
        if (ModelDownloadService.isDownloading(lang)) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }
        item.progressModel.isVisible = true
        item.btnDownload.isEnabled = false
        item.textModelStatus.text = getString(R.string.model_status_downloading, 0)
        ModelDownloadService.start(this, lang)
    }
}