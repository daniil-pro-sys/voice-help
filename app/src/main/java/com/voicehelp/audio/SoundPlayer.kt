package com.voicehelp.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.voicehelp.R
import com.voicehelp.data.SettingsStore

class SoundPlayer(context: Context) {

    enum class Kind { MIC_ON, LISTENING, RECOGNIZED, DIALOG_END, ERROR }

    private val appContext = context.applicationContext

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val sounds: Map<Kind, Int> = mapOf(
        Kind.MIC_ON to soundPool.load(appContext, R.raw.mic_on, 1),
        Kind.LISTENING to soundPool.load(appContext, R.raw.listening, 1),
        Kind.RECOGNIZED to soundPool.load(appContext, R.raw.recognized, 1),
        Kind.DIALOG_END to soundPool.load(appContext, R.raw.dialog_end, 1),
        Kind.ERROR to soundPool.load(appContext, R.raw.error, 1)
    )

    fun play(kind: Kind) {
        if (!SettingsStore.soundsEnabled(appContext)) return
        val id = sounds[kind] ?: return
        soundPool.play(id, 1.0f, 1.0f, 1, 0, 1.0f)
    }

    fun release() {
        soundPool.release()
    }
}