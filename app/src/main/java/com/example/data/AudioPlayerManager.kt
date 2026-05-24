package com.example.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue

class AudioPlayerManager {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    private var playbackThread: Thread? = null

    fun startPlayback() {
        if (isPlaying) return

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        try {
            audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(audioFormat)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
            }
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Failed to construct AudioTrack", e)
            return
        }

        if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            Log.e("AudioPlayerManager", "AudioTrack state remains uninitialized")
            return
        }

        try {
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Failed playing audio stream.", e)
            return
        }

        isPlaying = true
        audioQueue.clear()

        playbackThread = Thread {
            while (isPlaying) {
                try {
                    val chunk = audioQueue.take()
                    audioTrack?.write(chunk, 0, chunk.size)
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e("AudioPlayerManager", "AudioTrack stream write error", e)
                }
            }
        }.apply {
            name = "ClawAudioPlayerThread"
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun playAudioChunk(data: ByteArray) {
        if (isPlaying) {
            audioQueue.offer(data)
        }
    }

    fun stopPlayback() {
        isPlaying = false
        playbackThread?.interrupt()
        playbackThread = null
        audioQueue.clear()
        try {
            audioTrack?.apply {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    stop()
                    flush()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Failed closing AudioTrack resource.", e)
        } finally {
            audioTrack = null
        }
    }
}
