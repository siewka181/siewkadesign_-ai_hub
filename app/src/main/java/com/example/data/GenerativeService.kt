package com.example.data

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// 1. Moshi Generation Models
@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    @Json(name = "generationConfig") val generationConfig: GenerationConfig? = null,
    @Json(name = "systemInstruction") val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content? = null
)

// 2. Retrofit Client Setup
interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

// 3. API Integrator & Fallback Runner
object GeminiIntegrator {
    
    fun isApiKeyPlaceholder(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNullOrBlank() || key == "MY_GEMINI_API_KEY" || key.contains("placeholder", ignoreCase = true)
    }

    suspend fun generateContent(
        prompt: String,
        systemPrompt: String?,
        attachedFilesText: String,
        selectedModel: String = "gemini-3.5-flash"
    ): String = withContext(Dispatchers.IO) {
        if (isApiKeyPlaceholder()) {
            return@withContext runSimulationMockResponse(prompt, systemPrompt, attachedFilesText)
        }

        // Prepare the combined text prompt with injected files context if applicable
        val fullPrompt = if (attachedFilesText.isNotEmpty()) {
            "$attachedFilesText\n\n[USER COMMAND / QUESTION]\n$prompt"
        } else {
            prompt
        }

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = fullPrompt)))),
            systemInstruction = systemPrompt?.let { Content(parts = listOf(Part(text = it))) },
            generationConfig = GenerationConfig(temperature = 0.7f)
        )

        try {
            val response = RetrofitClient.service.generateContent(
                selectedModel,
                BuildConfig.GEMINI_API_KEY,
                request
            )
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "Empty response candidates returned from Gemini API."
        } catch (e: Exception) {
            "API_ERROR: ${e.localizedMessage}. Please verify your network and that the GEMINI_API_KEY has appropriate privileges."
        }
    }

    private fun runSimulationMockResponse(
        prompt: String,
        systemPrompt: String?,
        attachedFilesText: String
    ): String {
        val formattedFilesCount = if (attachedFilesText.isNotEmpty()) "Loaded attached workspace files context successfully." else "No local files linked."
        
        return """
[SIMULATOR MODE - GEMINI_API_KEY DECLARED IN SECRETS PANEL]
WARNING: No active GEMINI_API_KEY found in BuildConfig (Placeholder 'MY_GEMINI_API_KEY' detected). Ensure you insert your key in AI Studio Secrets panel.

[EXECUTION RESULTS via Simulated Agent]
System Persona Config: "${systemPrompt?.take(60)}..."
Workpace Context: $formattedFilesCount

Model Output Simulation for prompt: "$prompt"
--------------------------------------------------
[CONSOLE_AGENT_REPLY] Recieved user command input. Initiated Gemini multi-model analyzer pipeline.
Analyzing cloud logs and query metrics...

* Diagnostic Report:
- Found 2 GCP storage instances running at us-central1
- Tunnel with OpenClaw bridge status: [CONNECTED] on port 8080
- Simulated computational latency: 12ms

How would you like to update the config files in your workspace next? Use the 'Files' explorer to tweak configurations!
        """.trimIndent()
    }
}
