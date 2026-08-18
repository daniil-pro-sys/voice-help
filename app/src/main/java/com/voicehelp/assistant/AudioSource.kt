package com.voicehelp.assistant

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

@SuppressLint("MissingPermission")
class AudioSource(
    private val sampleRate: Int = 16000,
    frameSize: Int = 640
) {
    private val minBufferSize: Int = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private val bufferSize = maxOf(minBufferSize, frameSize)

    private val recorder: AudioRecord = AudioRecord(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        minBufferSize * 2
    )

    fun read(): ByteArray {
        if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            throw IllegalStateException("AudioRecord не записывает")
        }
        val buffer = ByteArray(bufferSize)
        val read = recorder.read(buffer, 0, buffer.size)
        if (read <= 0) return ByteArray(0)
        return if (read == buffer.size) buffer else buffer.copyOf(read)
    }

    fun start() {
        recorder.startRecording()
    }

    fun stop() {
        try {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
        } catch (_: Exception) {
        }
        recorder.release()
    }
}