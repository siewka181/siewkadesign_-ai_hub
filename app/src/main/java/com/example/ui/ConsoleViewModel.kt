package com.example.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConsoleViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val repository = CloudConsoleRepository(db)

    // Screen selection: "CONSOLE", "FILES", "PROMPTS", "OPENCL"
    val activeTab = MutableStateFlow("CONSOLE")

    // State bindings
    val systemPrompts: StateFlow<List<SystemPromptEntity>> = repository.systemPrompts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val localFiles: StateFlow<List<LocalFileEntity>> = repository.localFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val consoleLogs: StateFlow<List<ConsoleLogLine>> = repository.consoleLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Interactive States
    val commandText = MutableStateFlow("")
    val isGenerating = MutableStateFlow(false)
    val openClDevices = MutableStateFlow<List<OpenClDevice>>(emptyList())
    val selectedOpenClId = MutableStateFlow(0)
    val benchmarkResult = MutableStateFlow<BenchmarkResult?>(null)
    val isRunningBenchmark = MutableStateFlow(false)

    // Dialog state controllers
    val showCreateFileDialog = MutableStateFlow(false)
    val showCreatePromptDialog = MutableStateFlow(false)
    val currentSelectedFile = MutableStateFlow<LocalFileEntity?>(null)

    // Models options
    val availableModels = listOf("gemini-3.5-flash", "gemini-3.1-pro-preview")
    val selectedModel = MutableStateFlow("gemini-3.5-flash")

    // OpenClaw proxy simulated options
    val openClawStatus = MutableStateFlow("ACTIVE")
    val openClawPort = MutableStateFlow("8080")

    init {
        viewModelScope.launch {
            repository.populatePresetsIfEmpty()
            openClDevices.value = withContext(Dispatchers.IO) {
                OpenClDriver.getDevices()
            }
            
            // Check for saved hardware acceleration choice
            repository.getMemory("SELECTED_ACCEL_ID")?.toIntOrNull()?.let {
                selectedOpenClId.value = it
            }
            repository.getMemory("SELECTED_MODEL")?.let {
                selectedModel.value = it
            }
        }
    }

    fun selectTab(tab: String) {
        activeTab.value = tab
    }

    fun selectModelName(model: String) {
        viewModelScope.launch {
            selectedModel.value = model
            repository.saveMemory("SELECTED_MODEL", model)
            repository.appendLog("[MODEL_CFG] Model updated target configuration parameters to: $model", "SYSTEM")
        }
    }

    fun selectAccelerationDevice(id: Int) {
        viewModelScope.launch {
            selectedOpenClId.value = id
            repository.saveMemory("SELECTED_ACCEL_ID", id.toString())
            val deviceName = openClDevices.value.firstOrNull { it.id == id }?.name ?: "Unknown Driver"
            repository.appendLog("[OPENCL_DRV] Activated processing bridge device offset: $id ($deviceName)", "OPENCL")
        }
    }

    /**
     * Executes the console command.
     * Integrates context files and system prompts before calling Gemini
     */
    fun submitTerminalCommand() {
        val prompt = commandText.value.trim()
        if (prompt.isEmpty() || isGenerating.value) return

        commandText.value = ""
        isGenerating.value = true

        viewModelScope.launch {
            // Append user input to terminal
            repository.appendLog("$ $prompt", "USER")

            // Gather system instruction
            val systemPromptStr = repository.getSelectedPrompt()?.promptText

            // Gather file contexts
            val attachedFilesText = repository.getContextFilesContent()

            // Call API
            val response = GeminiIntegrator.generateContent(
                prompt = prompt,
                systemPrompt = systemPromptStr,
                attachedFilesText = attachedFilesText,
                selectedModel = selectedModel.value
            )

            // Log response
            repository.appendLog(response, "MODEL")
            isGenerating.value = false
        }
    }

    // --- File Storage Actions ---
    fun createWorkspaceFile(fileName: String, content: String) {
        viewModelScope.launch {
            repository.insertFile(fileName, content)
            repository.appendLog("[FILESYSTEM] Created local file artifact: '$fileName' (${content.length} characters)", "SYSTEM")
        }
    }

    fun deleteWorkspaceFile(id: Int, name: String) {
        viewModelScope.launch {
            repository.deleteFileById(id)
            repository.appendLog("[FILESYSTEM] Unlinked & deleted workspace file source: $name", "SYSTEM")
            if (currentSelectedFile.value?.id == id) {
                currentSelectedFile.value = null
            }
        }
    }

    fun toggleFileContextLink(id: Int, name: String, currentStatus: Boolean) {
        viewModelScope.launch {
            repository.toggleFileContext(id, !currentStatus)
            val logMsg = if (!currentStatus) {
                "[FILESYSTEM] Injected context link for '$name' into AI Agent system memory."
            } else {
                "[FILESYSTEM] Revoked file context link for '$name' from AI Agent system memory."
            }
            repository.appendLog(logMsg, "SYSTEM")
        }
    }

    fun selectFileToInspect(file: LocalFileEntity) {
        currentSelectedFile.value = file
        viewModelScope.launch {
            repository.appendLog("[SYSTEM] Inspecting File stream: '${file.fileName}' (${file.sizeBytes} bytes):\n${file.content}", "SYSTEM")
        }
    }

    // --- System Prompt Actions ---
    fun selectActivePrompt(prompt: SystemPromptEntity) {
        viewModelScope.launch {
            repository.selectPrompt(prompt.id)
            repository.appendLog("[AGENT_PROM] Persona switched to: [${prompt.title}]. Re-initializing OpenClaw tunnels...", "SYSTEM")
        }
    }

    fun createCustomPrompt(title: String, text: String) {
        viewModelScope.launch {
            repository.insertPrompt(title, text)
            repository.appendLog("[AGENT_PROM] Generated custom system prompt persona preset: '$title'", "SYSTEM")
        }
    }

    fun deleteCustomPrompt(prompt: SystemPromptEntity) {
        viewModelScope.launch {
            repository.deletePrompt(prompt)
            repository.appendLog("[AGENT_PROM] Purged customized system prompt persona: '${prompt.title}'", "SYSTEM")
        }
    }

    // --- Benchmarking Module ---
    fun runOpenClBenchmark(matrixSize: Int) {
        if (isRunningBenchmark.value) return
        isRunningBenchmark.value = true

        viewModelScope.launch {
            repository.appendLog("[BENCHMARKING] Starting compute math benchmark - matrix size: ${matrixSize}x$matrixSize...", "OPENCL")
            
            val isAcceleratedDevice = selectedOpenClId.value == 0 // GPU check acceleration
            val result = OpenClDriver.runComputeBenchmark(matrixSize, useAcceleration = isAcceleratedDevice)
            
            benchmarkResult.value = result
            
            val benchmarkLog = """
[BENCHMARK_COMPLETE] Hardware: ${result.hardwareString}
- Size Calculated: ${result.matrixSize}x${result.matrixSize} elements
- Compute Duration: ${result.durationMs} ms
- Ops Processed: ${result.totalOperations} operations
- Performance Metric: ${(result.megaFlops / 1000.0).format(2)} GigaFLOPS
- Acceleration Factor: ${result.accelerationFactor}x vs standard JVM
            """.trimIndent()

            repository.appendLog(benchmarkLog, "OPENCL")
            isRunningBenchmark.value = false
        }
    }

    private fun Double.format(digits: Int) = String.format("%.${digits}f", this)

    fun clearTerminalLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            repository.appendLog("[SYS] Console logs buffer cleared successfully.")
        }
    }

    fun triggerQuickInfraStats() {
        viewModelScope.launch {
            val stats = """
=== HARDWARE INSTANCE TELEMETRY ===
- Manufacturer / SDK: ${Build.MANUFACTURER} // Level API ${Build.VERSION.SDK_INT}
- Architecture Profile: ${Build.SUPPORTED_ABIS.joinToString(", ")}
- CPU Cores Available: ${Runtime.getRuntime().availableProcessors()}
- Memory Status (JVM Heap): Max ${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MB // Free ${Runtime.getRuntime().freeMemory() / (1024 * 1024)} MB
- OpenCL Host status: ${if (OpenClDriver.scanSystemDrivers().first) "[SYSTEM_SO_FOUND] Native vendor drivers detected" else "[EMULATED_LAYER] Native .so missing, emulation pipeline active"}
- OpenClaw Version: CLAW-v1.89-MCP-SECURED
=== TELEMETRY ANALYSIS TERMINATED ===
            """.trimIndent()
            repository.appendLog(stats, "SYSTEM")
        }
    }

    fun triggerOpenClawReset() {
        viewModelScope.launch {
            val oldPort = openClawPort.value
            openClawStatus.value = "CONNECTING"
            repository.appendLog("[OPENCLAW] Port reset sequence triggered. Killing listener on local socket $oldPort...")
            kotlinx.coroutines.delay(1200)
            openClawStatus.value = "ACTIVE"
            repository.appendLog("[OPENCLAW] Connected to secure OpenClaw remote agent gateway successfully over TLS v1.3 on port $oldPort.")
        }
    }
}
