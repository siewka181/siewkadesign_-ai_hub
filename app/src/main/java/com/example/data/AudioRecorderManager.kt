package com.example.data

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*

class AudioRecorderManager {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @SuppressLint("MissingPermission")
    fun startRecording(onAudioData: (ByteArray) -> Unit): Boolean {
        if (isRecording) return true

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e("AudioRecorderManager", "Invalid buffer size generated for PCM raw stream.")
            return false
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 2
            )
        } catch (e: SecurityException) {
            Log.e("AudioRecorderManager", "Insufficient permissions to capture MIC.", e)
            return false
        } catch (e: Exception) {
            Log.e("AudioRecorderManager", "Couldn't launch AudioRecord", e)
            return false
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("AudioRecorderManager", "AudioRecord initialization error.")
            return false
        }

        try {
            audioRecord?.startRecording()
        } catch (e: Exception) {
            Log.e("AudioRecorderManager", "AudioRecord raw startRecording crashed.", e)
            return false
        }

        isRecording = true
        recordingJob = scope.launch {
            val buffer = ByteArray(2048) // 64ms frame chunks (2048 bytes)
            while (isRecording && isActive) {
                val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (readBytes > 0) {
                    val actualData = ByteArray(readBytes)
                    System.arraycopy(buffer, 0, actualData, 0, readBytes)
                    onAudioData(actualData)
                }
            }
        }

        return true
    }

    fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            if (audioRecord?.state == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e("AudioRecorderManager", "Failure stopping AudioRecord system", e)
        } finally {
            audioRecord = null
        }
    }
}
