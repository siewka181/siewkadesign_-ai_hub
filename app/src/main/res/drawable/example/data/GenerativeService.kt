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
            return@withContext runSimulationMockResponse(prompt, systemPrompt, attachedFilesText, selectedModel)
        }

        // Map foreign models to Gemini equivalent but inject persona simulators
        val actualModelToCall = when {
            selectedModel == "gemini-3.1-pro-preview" -> "gemini-1.5-pro"
            selectedModel == "gemini-1.5-pro" -> "gemini-1.5-pro"
            selectedModel == "gemini-3.5-flash" -> "gemini-1.5-flash"
            else -> "gemini-1.5-flash" // fallback
        }

        val enhancedSystemPrompt = buildString {
            if (!systemPrompt.isNullOrBlank()) {
                append(systemPrompt)
                append("\n\n")
            }
            if (!selectedModel.startsWith("gemini")) {
                append("SIMULATOR DIRECTIVE: You are simulating the target model \"$selectedModel\". Adopt their exact traits, tone, and traits (e.g. Claude is helpful, academic, highly detailed, structured; DeepSeek is precise, highly logical, fast, technical; Grok is witty, slightly rebellious, funny). Start your answer with [$selectedModel Output].")
            }
        }

        // Prepare the combined text prompt with injected files context if applicable
        val fullPrompt = if (attachedFilesText.isNotEmpty()) {
            "$attachedFilesText\n\n[USER COMMAND / QUESTION]\n$prompt"
        } else {
            prompt
        }

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = fullPrompt)))),
            systemInstruction = Content(parts = listOf(Part(text = enhancedSystemPrompt))),
            generationConfig = GenerationConfig(temperature = 0.7f)
        )

        try {
            val response = RetrofitClient.service.generateContent(
                actualModelToCall,
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
        attachedFilesText: String,
        selectedModel: String
    ): String {
        val formattedFilesCount = if (attachedFilesText.isNotEmpty()) "Loaded attached workspace files context successfully." else "No local files linked."
        val modelHeader = selectedModel.uppercase()
        val customGreeting = when {
            selectedModel.contains("grok") -> "[GROK CORES] *Evaluating funny subroutines* // Grok active simulation: 'Let's skip the boring stuff. Here is the pure lowdown!'"
            selectedModel.contains("deepseek") -> "[DEEPSEEK REASONER] *DeepSeek-R1-V3 matrix active* // Reasoning trajectory: [Analyzing token values... OK]"
            selectedModel.contains("claude") -> "[CLAUDE HELPER] Claude-3.5-Sonnet active simulation: 'I will be happy to assist you with structured code and precise retro configurations.'"
            else -> "[GEMINI AGENT] Real-time Gemini-core simulation pipeline established."
        }
        
        return """
[SIMULATOR MODE - $modelHeader ACTIVE ENGINE]
$customGreeting

[EXECUTION RESULTS via Simulated Multi-Model Host Container]
System Persona Config: "${systemPrompt?.take(100)}..."
Workspace Files Context: $formattedFilesCount

Answer to prompt "$prompt":
--------------------------------------------------
[$modelHeader RESPONSE]: Action complete. Retrieved target parameters.
- Mode: Multi-vector neural routing on $selectedModel CPU matrix.
- Simulated token context processing latency: 18ms.

Based on your system memory rules and file contexts, the terminal has configured the remote ports and executed. Specify further shell instructions, MCP endpoint handshakes, or file operations in appropriate tabs as required!
        """.trimIndent()
    }
}
