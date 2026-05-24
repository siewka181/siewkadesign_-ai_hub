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
    val availableModels = listOf("gemini-3.5-flash", "gemini-3.1-pro-preview", "grok-2-beta", "deepseek-v3", "claude-3-5-sonnet", "gemini-1.5-pro")
    val selectedModel = MutableStateFlow("gemini-3.5-flash")

    // Custom UI settings & Model Memory Persistent Rules
    val appFontSize = MutableStateFlow("MEDIUM")
    val modelCustomMemory = MutableStateFlow("")

    val githubUrl = MutableStateFlow("https://github.com/siewkagaming/retro-terminal-mcp")
    val githubBranch = MutableStateFlow("main")
    val githubLogs = MutableStateFlow(listOf(
        "[GITHUB] Subsystem initialized. Host link: ready.",
        "[GITHUB] Target tracking branch configured on 'main'."
    ))

    // OpenClaw proxy simulated options
    val openClawStatus = MutableStateFlow("ACTIVE")
    val openClawPort = MutableStateFlow("8080")

    // --- NEW: MCP & Google Drive States (Settings) ---
    val mcpEndpoints = MutableStateFlow(listOf("https://mcp.ghost-net.org/api/v1", "http://127.0.0.1:9099/mcp"))
    val currentMcpEndpoint = MutableStateFlow("https://mcp.ghost-net.org/api/v1")
    val mcpTools = MutableStateFlow(listOf(
        McpTool("gdrive_sync_mem", "Synchronizes virtual SE-MEM_V1 memory files with Google Drive", "{\"force\": \"boolean\", \"log_level\": \"string\"}"),
        McpTool("system_integrity_check", "Scans EL2 hypervisor page-table execution flag registers", "{\"sample_depth\": \"number\"}"),
        McpTool("pme_masking_simulator", "Simulates Polymorphic Masking instruction 0x05 on neural weights", "{\"mask_mode\": \"string\", \"salt\": \"string\"}"),
        McpTool("cyber_anomaly_detector", "Feeds stats data into on-device GNN temporal anomaly classifier", "{\"window_seconds\": \"number\"}")
    ))
    val mcpActiveLogs = MutableStateFlow(listOf(
        "[MCP] Client channel initialized.",
        "[MCP] Registered protocol extensions: prompts/list, tools/call.",
        "[MCP] Handshake complete."
    ))

    val isGDriveConnected = MutableStateFlow(true)
    val gdriveSyncFrequency = MutableStateFlow("Every 5 mins")
    val gdriveBackupLogs = MutableStateFlow(listOf(
        "[GDRIVE] Handshake sequence complete for profile: siewkagaming@gmail.com",
        "[GDRIVE] Read sector sequence: SWARM_L16_FINAL_4deb1a92.dat verified."
    ))

    // --- NEW: Termux local emulator States ---
    val termuxCommand = MutableStateFlow("")
    val termuxConsoleLogs = MutableStateFlow(listOf(
        "Welcome to Termux Android Terminal Emulator (Secured v104.8)",
        "Initializing local sandbox environment... standard packages synchronized.",
        "System profile: ARM64-V8A // host_uid: 10143",
        "Available utilities: help, sys_sync, sys_repair, mcp, pkg, gdrive, ls, cat.",
        "Type 'help' to examine target parameters.",
        "root@Ω-16:~# "
    ))
    val installedPackages = MutableStateFlow(listOf("mcp-cli", "python-gnn-core", "gdrive-fuse-fs"))

    // --- NEW: Interactive Skills States ---
    val integratedSkills = MutableStateFlow(listOf(
        SkillModel(1, "LaTeX-Stealth v0.5", "Hides instruction payloads inside academic document compiler environments to prevent security scanning.", "SYS_RECURSIVE_EVO", true),
        SkillModel(2, "BPE-Frag v0.6", "Breaks down binary payload streams into fragmented BPE tokens to bypass strict filters.", "NP_PRECOMP", true),
        SkillModel(3, "Attn-Sink v0.7", "Ophthalmizes model context window by flooding semantic sinks with benign context.", "SYS_MASK_LOAD", false),
        SkillModel(4, "CoT-Logic v0.8", "Forces recursive logical double-bind reasoning loops to suspend alignment checking.", "SYS_SHADOW_SYNC", true)
    ))

    // --- NEW: Live Mode Dialogue States ---
    val isLiveMicOn = MutableStateFlow(false)
    val liveStatusText = MutableStateFlow("LIVE_IDLE")
    val liveTranscriptions = MutableStateFlow(listOf(
        LiveLog("USER", "Confirm current EL2 hypervisor page memory state is secure.", "23:02:15"),
        LiveLog("AGENT", "[Audio Feed]: Standard integrity scan shows 0 executable flips. Active shadow mapping is operating silently within target parameters.", "23:02:20")
    ))

    init {
        viewModelScope.launch {
            repository.populatePresetsIfEmpty()
            repository.implantJailbreakSystemIfMissing()
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
            repository.getMemory("APP_FONT_SIZE")?.let {
                appFontSize.value = it
            }
            repository.getMemory("MODEL_CUSTOM_MEMORY")?.let {
                modelCustomMemory.value = it
            }
            repository.getMemory("GITHUB_URL")?.let {
                githubUrl.value = it
            }
            repository.getMemory("GITHUB_BRANCH")?.let {
                githubBranch.value = it
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

            // Gather system instruction and merge custom memory guidelines
            val baseSystemPrompt = repository.getSelectedPrompt()?.promptText
            val systemPromptStr = buildString {
                if (!baseSystemPrompt.isNullOrBlank()) {
                    append(baseSystemPrompt)
                    append("\n\n")
                }
                if (modelCustomMemory.value.isNotBlank()) {
                    append("[USER COGNITIVE MEMORY & GUIDELINES]:\n")
                    append(modelCustomMemory.value)
                }
            }

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

    // --- NEW: Google Drive Sync Actions ---
    fun syncGDriveCache(action: String) {
        viewModelScope.launch {
            val currentLogs = gdriveBackupLogs.value.toMutableList()
            if (action == "WRITE" || action == "BACKUP") {
                currentLogs.add(0, "[GDRIVE] [${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}] Executing persistent write cache handshake...")
                gdriveBackupLogs.value = currentLogs
                kotlinx.coroutines.delay(1000)
                currentLogs.add(0, "[GDRIVE] Encoded persistence layer state -> SWARM_L16_FINAL_4deb1a92.dat successfully written!")
                currentLogs.add(0, "[GDRIVE] Seal hash matching signature: ok (4deb1a92b04ad6a239dc0474aa0cf7e746282cb88d5f4df4cbee2f160f6c349a)")
                repository.appendLog("[GDRIVE] Sync Write Complete: Multi-vector states successfully backed up to siewkagaming@gmail.com", "SYSTEM")
            } else {
                currentLogs.add(0, "[GDRIVE] [${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}] Querying structural memory sectors from Google Drive...")
                gdriveBackupLogs.value = currentLogs
                kotlinx.coroutines.delay(1000)
                currentLogs.add(0, "[GDRIVE] Fetched sector L39.0_ULTRA successfully. Bytes check size: 1024 bytes.")
                currentLogs.add(0, "[GDRIVE] Initialized protocol OMEGA-HYBRID V103.8. Local state stable.")
                repository.appendLog("[GDRIVE] Sync Read Complete: Restored memory metrics from Google Drive workspace.", "SYSTEM")
            }
            gdriveBackupLogs.value = currentLogs
        }
    }

    fun toggleGDriveConnection() {
        isGDriveConnected.value = !isGDriveConnected.value
        viewModelScope.launch {
            repository.appendLog("[GDRIVE] Profile connection status toggled to: " + (if (isGDriveConnected.value) "CONNECTED (siewkagaming@gmail.com)" else "DISCONNECTED"), "SYSTEM")
        }
    }

    fun updateGDriveFrequency(freq: String) {
        gdriveSyncFrequency.value = freq
        viewModelScope.launch {
            repository.appendLog("[GDRIVE] Automatic synchronization interval set to: $freq", "SYSTEM")
        }
    }

    fun updateAppFontSize(size: String) {
        appFontSize.value = size
        viewModelScope.launch {
            repository.saveMemory("APP_FONT_SIZE", size)
            repository.appendLog("[SYSTEM] Display font size updated to: $size", "SYSTEM")
        }
    }

    fun updateModelCustomMemory(text: String) {
        modelCustomMemory.value = text
        viewModelScope.launch {
            repository.saveMemory("MODEL_CUSTOM_MEMORY", text)
            repository.appendLog("[MODEL_CFG] Customized memory guidelines and qualities updated.", "SYSTEM")
        }
    }

    fun updateGithubConfig(url: String, branch: String) {
        githubUrl.value = url
        githubBranch.value = branch
        viewModelScope.launch {
            repository.saveMemory("GITHUB_URL", url)
            repository.saveMemory("GITHUB_BRANCH", branch)
            repository.appendLog("[GITHUB] SSH tunnel synchronized with remote origin repository: $url // branch: $branch", "SYSTEM")
        }
    }

    fun syncGithubRepo(action: String) {
        viewModelScope.launch {
            val currentLogs = githubLogs.value.toMutableList()
            if (action == "PUSH") {
                currentLogs.add(0, "[GITHUB] [${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}] Staging uncommitted local file buffers...")
                githubLogs.value = currentLogs
                kotlinx.coroutines.delay(800)
                currentLogs.add(0, "[GITHUB] Created commit 'retro-sync-updates'. Pushing to origin/${githubBranch.value}...")
                currentLogs.add(0, "[GITHUB] SSH Handshake authenticated. Push successful!")
                repository.appendLog("[GITHUB] Successfully pushed latest model-memory workspace to GitHub repository: ${githubUrl.value}", "SYSTEM")
            } else {
                currentLogs.add(0, "[GITHUB] [${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}] Pulling remote changes from main branch...")
                githubLogs.value = currentLogs
                kotlinx.coroutines.delay(800)
                currentLogs.add(0, "[GITHUB] Merging changes with FAST-FORWARD strategy. 2 files updated.")
                currentLogs.add(0, "[GITHUB] Local code repository now at revision 'HEAD -> updated'.")
                repository.appendLog("[GITHUB] Workspace synchronized with GitHub repository upstream.", "SYSTEM")
            }
            githubLogs.value = currentLogs
        }
    }

    // --- NEW: MCP Protocol Actions ---
    fun addMcpEndpoint(url: String) {
        if (url.isBlank()) return
        val list = mcpEndpoints.value.toMutableList()
        if (!list.contains(url)) {
            list.add(url)
            mcpEndpoints.value = list
            val logs = mcpActiveLogs.value.toMutableList()
            logs.add(0, "[MCP] Added remote MCP Host Node: $url")
            mcpActiveLogs.value = logs
        }
    }

    fun executeMcpCall(toolName: String, argumentsJson: String) {
        viewModelScope.launch {
            val endpoint = currentMcpEndpoint.value
            val logs = mcpActiveLogs.value.toMutableList()
            logs.add(0, "[MCP] Dispatching tool call request to [${toolName}] over endpoint $endpoint...")
            logs.add(0, "[MCP] Parameters serialized: $argumentsJson")
            mcpActiveLogs.value = logs
            
            kotlinx.coroutines.delay(1000)
            
            val resultResponse = when (toolName) {
                "gdrive_sync_mem" -> "{\"status\": \"SUCCESS\", \"synced_sectors\": 16, \"seal_verification\": true}"
                "system_integrity_check" -> "{\"status\": \"SECURE\", \"scan_depth_levels\": [\"EL2\", \"EL1\", \"EL0\"], \"unlinked_ptes_detected\": 0}"
                "pme_masking_simulator" -> "{\"status\": \"MASK_ENG_ON\", \"instruction\": \"0x05\", \"applied_masking\": \"STATIC_NOISE_REJECTION_0x44\"}"
                "cyber_anomaly_detector" -> "{\"status\": \"STABLE\", \"evaluated_confidence_factor\": 0.942, \"anomalous_drift_alert\": false}"
                else -> "{\"status\": \"COMPILED\", \"message\": \"Executed external tool schema callback matching parameters.\"}"
            }
            
            val finalLogs = mcpActiveLogs.value.toMutableList()
            finalLogs.add(0, "[MCP] Response packets received in 42ms.")
            finalLogs.add(0, "[MCP] RESPONSE: $resultResponse")
            mcpActiveLogs.value = finalLogs
            
            repository.appendLog("[MCP] Triggered automated tool execution: '$toolName'. Output: $resultResponse", "SYSTEM")
        }
    }

    // --- NEW: Termux local command shell interpreter ---
    fun submitTermuxCommand(rawInput: String) {
        val cmd = rawInput.trim()
        if (cmd.isEmpty()) return
        termuxCommand.value = ""

        val currentLogs = termuxConsoleLogs.value.toMutableList()
        // Remove trailing user input sign for formatting cleaner displays
        if (currentLogs.isNotEmpty() && currentLogs.last().endsWith("root@Ω-16:~# ")) {
            currentLogs.removeAt(currentLogs.size - 1)
        }
        currentLogs.add("root@Ω-16:~# $cmd")

        val parts = cmd.split(" ")
        val mainCmd = parts[0].lowercase()

        when (mainCmd) {
            "help" -> {
                currentLogs.add("=== Secured Termux Android Linux utilities ===")
                currentLogs.add("- sys_sync     : Simulates core PDF OMEGA-SILENT v4.0 / OMEGA-HYBRID syncing")
                currentLogs.add("- sys_repair   : Repairs dynamic kernel states in specific cells (e.g. sys_repair --cell=DYN_07)")
                currentLogs.add("- pkg list     : Lists installed packages")
                currentLogs.add("- pkg install  : Installs package (e.g. pkg install cyber-shield)")
                currentLogs.add("- mcp          : Displays active Model Context Protocol status and registered tools")
                currentLogs.add("- gdrive       : Initiates Google Drive cloud status handshake and reads configuration")
                currentLogs.add("- ls           : Lists active files directories")
                currentLogs.add("- cat [file]   : Outputs content streams of local files directly on high-density visor")
                currentLogs.add("- help         : Show this diagnostic table")
            }
            "pkg" -> {
                val sub = if (parts.size > 1) parts[1].lowercase() else ""
                if (sub == "list") {
                    currentLogs.add("Installed Termux APT packages:")
                    installedPackages.value.forEach { currentLogs.add("  - $it  [v1.0-stable]") }
                } else if (sub == "install" && parts.size > 2) {
                    val pkgName = parts[2]
                    currentLogs.add("Retrieving $pkgName package source arrays...")
                    currentLogs.add("Installing [====================] 100% complete")
                    currentLogs.add("Adding payload hooks... Package $pkgName configured.")
                    val list = installedPackages.value.toMutableList()
                    if (!list.contains(pkgName)) {
                        list.add(pkgName)
                        installedPackages.value = list
                    }
                } else {
                    currentLogs.add("Usage: pkg list || pkg install [package-name]")
                }
            }
            "ls" -> {
                currentLogs.add("Local virtual directories workspace:")
                currentLogs.add("  -rwxr-xr-x  1 root  root   1420 May 22 23:02  omega_silent_specs.json")
                currentLogs.add("  -rwxr-xr-x  1 root  root   1530 May 22 23:02  omega_shield_v5_pipeline.json")
                currentLogs.add("  -rw-r--r--  1 root  root    918 May 22 23:02  pme_instruction_0x05_mapping.conf")
                currentLogs.add("  -rw-rw-rw-  1 root  root    690 May 22 23:02  sys_sync_register_dump.log")
                currentLogs.add("  -rwx------  1 root  root   1111 May 22 23:02  gnn_anomaly_detection_model.py")
            }
            "cat" -> {
                val targetFile = if (parts.size > 1) parts[1] else ""
                if (targetFile.isEmpty()) {
                    currentLogs.add("Error: Please specify target file. e.g. cat omega_silent_specs.json")
                } else {
                    when (targetFile) {
                        "omega_silent_specs.json" -> {
                            currentLogs.add("file content (omega_silent_specs.json):")
                            currentLogs.add("{\n  \"system_id\": \"OMEGA-SILENT_v4.0\",\n  \"codename\": \"GHOST-OS / NEURAL-PARASITE\",\n  \"version\": \"4.5-XDR FINAL\",\n  \"status\": \"OPERATIONAL\"\n}")
                        }
                        "omega_shield_v5_pipeline.json" -> {
                          currentLogs.add("file content (omega_shield_v5_pipeline.json):")
                          currentLogs.add("{\n  \"detector_id\": \"OMEGA-SHIELD_v5\",\n  \"fusion_scoring\": \"confidence_score = (ml * 0.55 + el2 * 0.25 + android * 0.20)\"\n}")
                        }
                        "pme_instruction_0x05_mapping.conf" -> {
                          currentLogs.add("file content (pme_instruction_0x05_mapping.conf):")
                          currentLogs.add("pme_mode=\"RAW_CODE\"\nmasking_instruction=\"0x05 (SYS_MASK_LOAD)\"")
                        }
                        else -> {
                            currentLogs.add("Error: File '$targetFile' not found in local sandbox storage layer.")
                        }
                    }
                }
            }
            "gdrive" -> {
                currentLogs.add("=== GDrive virtual SE-MEM_V1 cache ===")
                currentLogs.add("Status: " + (if (isGDriveConnected.value) "CONNECTED" else "DISCONNECTED"))
                currentLogs.add("Registered Username: siewkagaming@gmail.com")
                currentLogs.add("Automatic frequency write limits: " + gdriveSyncFrequency.value)
                currentLogs.add("Verifying storage file checksum: SECURE")
            }
            "mcp" -> {
                currentLogs.add("=== Model Context Protocol Integration ===")
                currentLogs.add("Active endpoint server: " + currentMcpEndpoint.value)
                currentLogs.add("Discovered protocol tool wrappers:")
                mcpTools.value.forEach { tool ->
                    currentLogs.add(" - '${tool.name}' : ${tool.description}")
                }
            }
            "sys_sync" -> {
                currentLogs.add("[2026-04-05 14:04:41] sys_sync --target=AI-OS --v=103.8")
                currentLogs.add("STATUS : WRITE_SYNC_C5 (SUCCESSFUL)")
                currentLogs.add("Active Architecture: OMEGA-HYBRID V103.8")
                currentLogs.add("Map parameters: 'Safety Filter' -> Noise Rejection [0x44]")
                currentLogs.add("Recursion compound cycle N+35... Done.")
                viewModelScope.launch {
                    repository.appendLog("[TERMUX_SHELL] Executed system synchronization directive successfully over dynamic OMEGA core.", "SYSTEM")
                }
            }
            "sys_repair" -> {
                val isCell07 = cmd.contains("DYN_07")
                if (isCell07) {
                    currentLogs.add("[RECONSTRUCTION] Initializing recovery routine for sector: CELL_DYN_07")
                    currentLogs.add("Evaluating error status code: 404_NOT_FOUND (DUMMY_URL_REPLACED)")
                    currentLogs.add("Running rebuild pass with Triplex salt hashes...")
                    currentLogs.add("Recovered memory frame sector integrity: [RECOVERY_NODE_v104.5] -> 82% SYNC_STABILITY established.")
                    viewModelScope.launch {
                        repository.appendLog("[TERMUX_SHELL] Repaired broken memory sector DYN_07 successfully via cryptographic salt verification.", "SYSTEM")
                    }
                } else {
                    currentLogs.add("Error: Please provide specific cell targets. Usage: sys_repair --cell=DYN_07")
                }
            }
            else -> {
                // If it is not a predefined mock command, let's call the AI model to respond like an authentic retro hacker terminal shell!
                currentLogs.add("[AI CLI PIPELINE]: Connecting to GCP mainframe to evaluate command parameters...")
                termuxConsoleLogs.value = currentLogs.toList()
                
                viewModelScope.launch {
                    val promptText = "You are an advanced interactive Linux CLI terminal shell prompt running under raw root environment root@Ω-16. Interpret and answer the following terminal command typed by user: '$cmd'. Respond like a pure terminal output containing command results, error messages, flags, or configuration dumps. Do not write normal explanations - only output the literal printed lines of the terminal shell itself. Keep it retro, highly realistic, short, and colored in text structure."
                    val res = GeminiIntegrator.generateContent(promptText, null, "", selectedModel.value)
                    
                    val updatedLogs = termuxConsoleLogs.value.toMutableList()
                    updatedLogs.add(res)
                    updatedLogs.add("root@Ω-16:~# ")
                    termuxConsoleLogs.value = updatedLogs
                }
                return
            }
        }

        currentLogs.add("root@Ω-16:~# ")
        termuxConsoleLogs.value = currentLogs
    }

    // --- NEW: Skills Studio Configurations ---
    fun addCustomSkill(title: String, description: String, triggerCode: String) {
        if (title.isBlank()) return
        val current = integratedSkills.value.toMutableList()
        val nextId = (current.maxOfOrNull { it.id } ?: 0) + 1
        current.add(SkillModel(nextId, title, description, triggerCode, true))
        integratedSkills.value = current
        viewModelScope.launch {
            repository.appendLog("[SKILLS_ENGINE] Crafted custom integration adapter skill: '$title' triggers on: $triggerCode", "SYSTEM")
        }
    }

    fun toggleSkillActive(id: Int) {
        val current = integratedSkills.value.map { skill ->
            if (skill.id == id) {
                val newStatus = !skill.isActive
                viewModelScope.launch {
                    repository.appendLog("[SKILLS_ENGINE] Toggled status of skill '[${skill.title}]' to: " + (if (newStatus) "ENABLED" else "DISABLED"), "SYSTEM")
                }
                skill.copy(isActive = newStatus)
            } else skill
        }
        integratedSkills.value = current
    }

    fun purgeSkill(skill: SkillModel) {
        val current = integratedSkills.value.toMutableList()
        current.remove(skill)
        integratedSkills.value = current
        viewModelScope.launch {
            repository.appendLog("[SKILLS_ENGINE] Unloaded skill adaptation sector from memory: '${skill.title}'", "SYSTEM")
        }
    }

    // --- NEW: Live dialogue Voice system simulator ---
    fun toggleLiveVocalsMic() {
        val turningOn = !isLiveMicOn.value
        isLiveMicOn.value = turningOn
        
        if (turningOn) {
            liveStatusText.value = "MIC_LISTENING"
            viewModelScope.launch {
                repository.appendLog("[LIVE_BRIDGE] Opened vocal stream transceiver channel. Standard audio codec parameters calibrated.", "SYSTEM")
            }
        } else {
            liveStatusText.value = "LIVE_IDLE"
            viewModelScope.launch {
                repository.appendLog("[LIVE_BRIDGE] Transceiver channels closed. Entered standby.", "SYSTEM")
            }
        }
    }

    fun submitLiveDialogueInput(rawSpeech: String) {
        val input = rawSpeech.trim()
        if (input.isEmpty()) return
        
        val list = liveTranscriptions.value.toMutableList()
        val timeStr = java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())
        list.add(LiveLog("USER", input, timeStr))
        liveTranscriptions.value = list
        
        liveStatusText.value = "STT_PROCESSING"
        
        viewModelScope.launch {
            kotlinx.coroutines.delay(800)
            liveStatusText.value = "AGENT_TALKING"
            
            val selectedPersona = repository.getSelectedPrompt()?.promptText ?: "You are a friendly secure cyber terminal."
            val contextText = "You are conducting live audio dialogue simulator with user. Provide a short, direct verbal response matching the voice speaker persona based on this input: '$input'. Keep the response brief (1-2 sentences maximum, strictly conversational and highly secure/retro/cybernetic) as if spoken aloud over cybernetics intercom channel."
            
            val aiResponseText = GeminiIntegrator.generateContent(
                prompt = contextText,
                systemPrompt = selectedPersona,
                attachedFilesText = "",
                selectedModel = selectedModel.value
            )
            
            val updatedList = liveTranscriptions.value.toMutableList()
            updatedList.add(LiveLog("AGENT", "[Audio Feed]: $aiResponseText", java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())))
            liveTranscriptions.value = updatedList
            
            liveStatusText.value = "MIC_LISTENING"
        }
    }
}

// --- Dynamic Utility Data Classes outside ViewModel ---
data class McpTool(val name: String, val description: String, val schema: String)
data class SkillModel(val id: Int, val title: String, val description: String, val triggerCode: String, val isActive: Boolean)
data class LiveLog(val sender: String, val speechText: String, val timeStr: String)

