package com.example.data

import android.util.Log
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

class OpenClawClient(private val gatewayUrl: String = "wss://siewka.taild53118.ts.net") {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
        
    private var webSocket: WebSocket? = null
    
    fun connect() {
        val request = Request.Builder()
            .url(gatewayUrl)
            // .addHeader("Authorization", "Bearer YOUR_TOKEN") // Tu dodamy token autoryzacyjny z QR
            .build()
            
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("OpenClaw", "Połączono z serwerem OpenClaw Gateway!")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("OpenClaw", "Otrzymano wiadomość: \$text")
                // Tutaj będziemy odbierać wiadomości tekstowe od Agenta
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d("OpenClaw", "Otrzymano dane binarne (np. fragmenty audio)")
                // Tutaj będziemy strumieniować audio z powrotem do głośnika (AudioTrack)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("OpenClaw", "Błąd połączenia: \${t.message}")
            }
        })
    }

    fun sendMessage(text: String) {
        webSocket?.send(text)
    }

    fun sendAudioFrame(pcmData: ByteArray) {
        webSocket?.send(ByteString.of(*pcmData))
    }
    
    fun sendVideoFrame(jpegData: ByteArray) {
        // Docelowo wysyłanie klatek z MediaProjection
        webSocket?.send(ByteString.of(*jpegData))
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
    }
}
