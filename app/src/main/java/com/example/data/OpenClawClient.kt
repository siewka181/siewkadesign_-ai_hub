package com.example.data

import android.util.Log
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit

object OpenClawClient {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // infinite read timeout for WebSocket
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    var isConnected = false
        private set

    private var onConnectCallback: (() -> Unit)? = null
    private var onDisconnectCallback: (() -> Unit)? = null
    private var onAudioReceivedCallback: ((ByteArray) -> Unit)? = null
    private var onMessageReceivedCallback: ((String) -> Unit)? = null

    fun connect(
        url: String = "wss://siewka.taild53118.ts.net",
        onConnect: () -> Unit,
        onDisconnect: () -> Unit,
        onAudioReceived: (ByteArray) -> Unit,
        onMessageReceived: (String) -> Unit
    ) {
        if (isConnected) {
            Log.d("OpenClawClient", "Already connected to WebSocket.")
            return
        }

        onConnectCallback = onConnect
        onDisconnectCallback = onDisconnect
        onAudioReceivedCallback = onAudioReceived
        onMessageReceivedCallback = onMessageReceived

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                Log.d("OpenClawClient", "WebSocket: Connected successfully to $url")
                onConnectCallback?.invoke()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("OpenClawClient", "WebSocket text payload: $text")
                onMessageReceivedCallback?.invoke(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                // Check if gateway uses prefix or sends raw audio
                if (data.isNotEmpty()) {
                    if (data[0] == 0x01.toByte()) {
                        // Prefixed payload: strip first byte and play
                        val audioData = ByteArray(data.size - 1)
                        System.arraycopy(data, 1, audioData, 0, audioData.size)
                        onAudioReceivedCallback?.invoke(audioData)
                    } else {
                        // Raw payload (no prefix or other type): play directly
                        onAudioReceivedCallback?.invoke(data)
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                Log.d("OpenClawClient", "WebSocket relation closed: Code=$code Reason=$reason")
                onDisconnectCallback?.invoke()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                Log.e("OpenClawClient", "WebSocket transport failed: ${t.localizedMessage}", t)
                onDisconnectCallback?.invoke()
            }
        })
    }

    fun disconnect() {
        Log.d("OpenClawClient", "Disconnecting WebSocket manually")
        webSocket?.close(1000, "User triggered close")
        webSocket = null
        isConnected = false
        onConnectCallback = null
        onDisconnectCallback = null
        onAudioReceivedCallback = null
        onMessageReceivedCallback = null
    }

    fun sendAudioFrame(data: ByteArray) {
        if (!isConnected) return
        try {
            // Package binary with an audio type prefix 0x01
            val payload = ByteArray(data.size + 1)
            payload[0] = 0x01 // 0x01 prefix flags an audio stream segment
            System.arraycopy(data, 0, payload, 1, data.size)
            webSocket?.send(payload.toByteString())
        } catch (e: Exception) {
            Log.e("OpenClawClient", "Failed to send binary audio packet", e)
        }
    }

    fun sendVideoFrame(data: ByteArray) {
        if (!isConnected) return
        try {
            // Package binary with a video type prefix 0x02
            val payload = ByteArray(data.size + 1)
            payload[0] = 0x02 // 0x02 prefix flags a video stream segment (JPEG binary)
            System.arraycopy(data, 0, payload, 1, data.size)
            webSocket?.send(payload.toByteString())
        } catch (e: Exception) {
            Log.e("OpenClawClient", "Failed to send binary video JPEG packet", e)
        }
    }

    fun sendTextMessage(text: String) {
        if (!isConnected) return
        try {
            webSocket?.send(text)
        } catch (e: Exception) {
            Log.e("OpenClawClient", "Failed to send text payload", e)
        }
    }
}
