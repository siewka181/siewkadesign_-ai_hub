package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var mainViewModel: ConsoleViewModel

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (::mainViewModel.isInitialized) {
                mainViewModel.startAudioRecordingReal()
            }
        } else {
            Toast.makeText(this, "Microphone access is required for real-time voice chat.", Toast.LENGTH_LONG).show()
        }
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val metrics = resources.displayMetrics
            val intent = Intent(this, ScreenShareService::class.java).apply {
                putExtra(ScreenShareService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenShareService.EXTRA_DATA_INTENT, result.data)
                putExtra(ScreenShareService.EXTRA_WIDTH, metrics.widthPixels)
                putExtra(ScreenShareService.EXTRA_HEIGHT, metrics.heightPixels)
                putExtra(ScreenShareService.EXTRA_DPI, metrics.densityDpi)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            if (::mainViewModel.isInitialized) {
                mainViewModel.startScreenShareReal()
            }
        } else {
            Toast.makeText(this, "Screen capture permission declined.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle uncaught exceptions gracefully with diagnostic logcat messages
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("CRASH_HANDLER", "CRITICAL ERROR: Uncaught exception in thread ${thread.name}", throwable)
            System.exit(1)
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: ConsoleViewModel = viewModel()
                mainViewModel = viewModel
                CloudConsoleApp(viewModel)
            }
        }
    }

    fun checkAndStartVoiceRecording(viewModel: ConsoleViewModel) {
        mainViewModel = viewModel
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startAudioRecordingReal()
        } else {
            requestAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    fun checkAndStartScreenSharing(viewModel: ConsoleViewModel) {
        mainViewModel = viewModel
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mpManager.createScreenCaptureIntent()
        mediaProjectionLauncher.launch(intent)
    }

    fun stopScreenSharing(viewModel: ConsoleViewModel) {
        mainViewModel = viewModel
        val serviceIntent = Intent(this, ScreenShareService::class.java)
        stopService(serviceIntent)
        viewModel.stopScreenShareReal()
    }
}

@Composable
fun CloudConsoleApp(viewModel: ConsoleViewModel) {
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_root_scaffold")
            .background(RetroBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
        topBar = {
            ConsoleHeader(
                activeTab = activeTab,
                selectedModel = selectedModel,
                isGenerating = isGenerating
            )
        },
        bottomBar = {
            ConsoleNavigationFooter(
                activeTab = activeTab,
                onTabSelected = { viewModel.selectTab(it) }
            )
        },
        containerColor = RetroBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .background(RetroBackground)
        ) {
            when (activeTab) {
                "CONSOLE" -> ConsoleTabContent(viewModel)
                "TERMUX" -> TermuxTabContent(viewModel)
                "LIVE" -> LiveModeTabContent(viewModel)
                "SKILLS" -> SkillsTabContent(viewModel)
                "FILES" -> FilesTabContent(viewModel)
                "PROMPTS" -> PromptsTabContent(viewModel)
                "OPENCL" -> OpenClTabContent(viewModel)
                "SETTINGS" -> SettingsTabContent(viewModel)
            }
        }
    }
}

// --- TOP SYSTEM BAR HEADER ---
@Composable
fun ConsoleHeader(
    activeTab: String,
    selectedModel: String,
    isGenerating: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(RetroBackground)
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rounded cyber avatar badge
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(RetroOlive),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = "GCP CLI Badge",
                tint = RetroGreen,
                modifier = Modifier.size(16.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1.0f)) {
            Text(
                text = "CloudConsole v4.2-PRO",
                color = RetroText,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isGenerating) Color.Yellow else RetroGreen)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "GCP-NODE: US-CENTRAL1 // AI: $selectedModel",
                    color = RetroText.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(end = 4.dp)
        ) {
            Text(
                text = "STATUS",
                color = RetroGreen,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold)
            )
            Text(
                text = if (isGenerating) "AI_WRITING" else "CLI_READY",
                color = if (isGenerating) Color.Yellow else RetroGreen,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

// --- CONSOLE TAB CONTENT ---
@Composable
fun ConsoleTabContent(viewModel: ConsoleViewModel) {
    val consoleLogs by viewModel.consoleLogs.collectAsStateWithLifecycle()
    val commandText by viewModel.commandText.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val availableModels = viewModel.availableModels
    val appFontSize by viewModel.appFontSize.collectAsStateWithLifecycle()

    val fontSizeSp = when (appFontSize) {
        "SMALL" -> 11.sp
        "MEDIUM" -> 13.sp
        "LARGE" -> 16.sp
        "HUGE" -> 19.sp
        else -> 13.sp
    }
    
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Auto scrolling lists
    LaunchedEffect(consoleLogs.size, isGenerating) {
        if (consoleLogs.isNotEmpty()) {
            listState.animateScrollToItem(consoleLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Fast GCP Model Swapper Pill Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "CHOOSE ENGINE:",
                color = RetroText.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall
            )
            
            availableModels.forEach { m ->
                val isSelected = selectedModel == m
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) RetroGreen else RetroGray)
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .clickable { viewModel.selectModelName(m) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = m,
                        color = if (isSelected) RetroBackground else RetroText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        // Main Black Terminal Window Box
        Box(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(TerminalBlack)
                .border(2.dp, RetroOlive.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                .padding(12.dp)
        ) {
            // Screen Window status accents (vintage UNIX look)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(start = 0.dp, top = 2.dp, end = 4.dp, bottom = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color.Red.copy(alpha = 0.6f)))
                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color.Yellow.copy(alpha = 0.6f)))
                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(RetroGreen.copy(alpha = 0.6f)))
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    Text(
                        text = "GCP PARALLEL MULTI-MODEL KERNEL [ACTIVE].\n" +
                                "ACCESS SECURED FROM GATEWAY // TERMINAL READY.\n" +
                                "TYPE QUESTIONS FOR THE REAL-TIME GENERATION STREAM.",
                        color = RetroGreen,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(consoleLogs) { logLine ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.Top) {
                            val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(logLine.timestamp))
                            Text(
                                text = "[$timeStr] ",
                                color = Color.White.copy(alpha = 0.25f),
                                fontSize = fontSizeSp,
                                style = MaterialTheme.typography.bodySmall
                            )
                            
                            val (prefixText, colorAttr) = when (logLine.type) {
                                "USER" -> "$" to RetroGreen
                                "SYSTEM" -> "[SYS]" to TerminalBlue
                                "MODEL" -> "[AI]" to RetroText
                                "ERROR" -> "[ERR]" to Color.Red
                                "OPENCL" -> "[CORES]" to RetroGreen.copy(alpha = 0.5f)
                                else -> ">>>" to RetroText
                            }

                            Text(
                                text = "$prefixText ",
                                color = colorAttr,
                                fontWeight = FontWeight.Bold,
                                fontSize = fontSizeSp,
                                style = MaterialTheme.typography.bodySmall
                            )

                            Text(
                                text = logLine.text,
                                color = if (logLine.type == "USER") RetroGreen else RetroText,
                                fontSize = fontSizeSp,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                if (isGenerating) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.5.dp,
                                color = RetroGreen
                            )
                            Text(
                                text = "Tunneling OpenClaw matrix packet arrays... generating stream...",
                                color = RetroGreen.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Fast In-Console actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TerminalToolButton(
                label = "CLEAR BUFFER",
                icon = Icons.Default.DeleteSweep,
                onClick = { viewModel.clearTerminalLogs() },
                modifier = Modifier.weight(1.0f)
            )
            TerminalToolButton(
                label = "SYS DICT",
                icon = Icons.Default.Dns,
                onClick = { viewModel.triggerQuickInfraStats() },
                modifier = Modifier.weight(1.0f)
            )
            TerminalToolButton(
                label = "RESET CLAW",
                icon = Icons.Default.RotateLeft,
                onClick = { viewModel.triggerOpenClawReset() },
                modifier = Modifier.weight(1.0f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // CommandLine input bar
        TerminalCommandLineBox(
            value = commandText,
            onValueChange = { viewModel.commandText.value = it },
            onSubmit = { viewModel.submitTerminalCommand() },
            isGenerating = isGenerating
        )
    }
}

@Composable
fun TerminalToolButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(RetroGray)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = RetroGreen, modifier = Modifier.size(14.dp))
            Text(text = label, color = RetroText, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
fun TerminalCommandLineBox(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    isGenerating: Boolean
) {
    val kbController = LocalSoftwareKeyboardController.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(RetroCard)
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = Icons.Default.Bolt, contentDescription = null, tint = RetroGreen, modifier = Modifier.size(16.dp))
        }

        Spacer(modifier = Modifier.width(10.dp))

        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = "Request AI analysis or enter UNIX lines...",
                    color = Color.White.copy(alpha = 0.3f),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            modifier = Modifier
                .weight(1.0f)
                .testTag("terminal_input_field"),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = RetroText,
                unfocusedTextColor = RetroText
            ),
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                onSubmit()
                kbController?.hide()
            })
        )

        Spacer(modifier = Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (isGenerating || value.isBlank()) RetroGreen.copy(alpha = 0.4f) else RetroGreen)
                .clickable(enabled = !isGenerating && value.isNotBlank()) {
                    onSubmit()
                    kbController?.hide()
                }
                .testTag("terminal_send_btn"),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = "Enter CMD", tint = RetroBackground, modifier = Modifier.size(18.dp))
        }
    }
}

// --- STORAGE EXPLORER TAB CONTENT ---
@Composable
fun FilesTabContent(viewModel: ConsoleViewModel) {
    val files by viewModel.localFiles.collectAsStateWithLifecycle()
    val currentSelectedFile by viewModel.currentSelectedFile.collectAsStateWithLifecycle()
    
    var showCreateDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("📁 VIRTUAL ARCHIVE DATABASE", color = RetroGreen, style = MaterialTheme.typography.titleLarge)
                Text("Select and link files to feed context to active system prompt", color = RetroText.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = RetroOlive, contentColor = RetroGreen),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "New Doc", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("NEW FILE", style = MaterialTheme.typography.labelSmall)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().weight(1.0f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left directory
            Card(
                modifier = Modifier.weight(0.5f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = RetroCard),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("ACTIVE FILE DIRECTORIES", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(files) { file ->
                            val isInspected = currentSelectedFile?.id == file.id
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isInspected) RetroOlive.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.03f))
                                    .border(1.dp, if (isInspected) RetroGreen else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable { viewModel.selectFileToInspect(file) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (file.inAgentContext) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = "Active Context Link",
                                    tint = if (file.inAgentContext) RetroGreen else Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { viewModel.toggleFileContextLink(file.id, file.fileName, file.inAgentContext) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1.0f)) {
                                    Text(file.fileName, color = RetroText, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${file.sizeBytes} Bytes", color = RetroText.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(
                                    onClick = { viewModel.deleteWorkspaceFile(file.id, file.fileName) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Right interactive inspector
            Card(
                modifier = Modifier.weight(0.5f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = TerminalBlack),
                border = BorderStroke(1.dp, RetroOlive.copy(alpha = 0.5f))
            ) {
                if (currentSelectedFile == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.Visibility, contentDescription = null, tint = RetroGreen, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("INSPECTOR BLANK", color = RetroText.copy(alpha = 0.4f), style = MaterialTheme.typography.bodySmall)
                            Text("Select a file on the left", color = RetroText.copy(alpha = 0.3f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else {
                    val inspected = currentSelectedFile!!
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(inspected.fileName.uppercase(), color = RetroGreen, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text(
                                text = if (inspected.inAgentContext) "CONTEXT_LINK_ON" else "OFFLINE",
                                color = if (inspected.inAgentContext) RetroGreen else Color.White.copy(alpha = 0.3f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        
                        Divider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 8.dp))
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1.0f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.02f))
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            LazyColumn {
                                item {
                                    Text(
                                        text = inspected.content,
                                        color = RetroText,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Workspace File Content", inspected.content)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied payload to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RetroGray),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("COPY PAYLOAD", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var nameInput by remember { mutableStateOf("") }
        var contInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = RetroBackground,
            title = { Text("CREATE SQL WORKSPACE SOURCE", color = RetroGreen) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("File Name", color = RetroText.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RetroGreen, focusedLabelColor = RetroGreen),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = contInput,
                        onValueChange = { contInput = it },
                        label = { Text("Content Matrix String", color = RetroText.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RetroGreen, focusedLabelColor = RetroGreen),
                        modifier = Modifier.fillMaxWidth().height(140.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameInput.isNotBlank()) {
                            viewModel.createWorkspaceFile(nameInput, contInput)
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RetroGreen, contentColor = RetroBackground)
                ) {
                    Text("ADD FILE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("CANCEL", color = RetroText)
                }
            }
        )
    }
}

// --- SYSTEM PERSONA PROMPTS TAB CONTENT ---
@Composable
fun PromptsTabContent(viewModel: ConsoleViewModel) {
    val prompts by viewModel.systemPrompts.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("🧠 AGENT DIRECTIVE MANIFESTS", color = RetroGreen, style = MaterialTheme.typography.titleLarge)
                Text("Tweak core systems system instructions or switch profiles live", color = RetroText.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = { showDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = RetroOlive, contentColor = RetroGreen),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add preset", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("NEW PERSONA", style = MaterialTheme.typography.labelSmall)
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1.0f)
        ) {
            items(prompts) { prompt ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = RetroCard),
                    border = BorderStroke(
                        width = if (prompt.isSelected) 2.dp else 1.dp,
                        color = if (prompt.isSelected) RetroGreen else Color.White.copy(alpha = 0.05f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (prompt.isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = "Adopt Profile",
                                    tint = if (prompt.isSelected) RetroGreen else Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { viewModel.selectActivePrompt(prompt) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = prompt.title,
                                    color = if (prompt.isSelected) RetroGreen else RetroText,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            
                            Row {
                                if (prompt.isSystemPreset) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.White.copy(alpha = 0.05f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("PRESET", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp))
                                    }
                                } else {
                                    IconButton(
                                        onClick = { viewModel.deleteCustomPrompt(prompt) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Custom Preset", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.4f))
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = prompt.promptText,
                                color = RetroText.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        var customTitle by remember { mutableStateOf("") }
        var customText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = RetroBackground,
            title = { Text("CREATE AGENT SYSTEM DIRECTIVE", color = RetroGreen) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customTitle,
                        onValueChange = { customTitle = it },
                        label = { Text("Directive Title (e.g. Sarcastic GLaDOS)", color = RetroText.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RetroGreen, focusedLabelColor = RetroGreen),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it },
                        label = { Text("Active System Instructions", color = RetroText.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RetroGreen, focusedLabelColor = RetroGreen),
                        modifier = Modifier.fillMaxWidth().height(140.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customTitle.isNotBlank() && customText.isNotBlank()) {
                            viewModel.createCustomPrompt(customTitle, customText)
                            showDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RetroGreen, contentColor = RetroBackground)
                ) {
                    Text("SAVE PROMPT")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("CANCEL", color = RetroText)
                }
            }
        )
    }
}

// --- OPENCL DRIVERS & COMPUTE BENCHTAB CONTENT ---
@Composable
fun OpenClTabContent(viewModel: ConsoleViewModel) {
    val devices by viewModel.openClDevices.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedOpenClId.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunningBenchmark.collectAsStateWithLifecycle()
    val benchmarkResult by viewModel.benchmarkResult.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("⚡ PARALLEL GRAPHICS CORE DRIVER GATEWAYS", color = RetroGreen, style = MaterialTheme.typography.titleLarge)
        Text("Trigger simulated OpenCL pipeline matrix calculations measuring GFLOPS output.", color = RetroText.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Devices panel
            Card(
                modifier = Modifier.weight(1.0f),
                colors = CardDefaults.cardColors(containerColor = RetroCard),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("SCAN HOST HARDWARE PORTALS", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall)
                    
                    devices.forEach { dev ->
                        val isChosenDevice = selectedId == dev.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isChosenDevice) RetroOlive.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.02f))
                                .border(1.dp, if (isChosenDevice) RetroGreen else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { viewModel.selectAccelerationDevice(dev.id) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (dev.type == "GPU") Icons.Default.Memory else Icons.Default.Dns,
                                contentDescription = null,
                                tint = if (isChosenDevice) RetroGreen else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1.0f)) {
                                Text(dev.name, color = RetroText, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                Text("Vendor: ${dev.vendor} // Type: ${dev.type}", color = RetroText.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp))
                            }
                        }
                    }
                }
            }

            // Benchmark Launcher Portal
            Card(
                modifier = Modifier.weight(1.0f),
                colors = CardDefaults.cardColors(containerColor = TerminalBlack),
                border = BorderStroke(1.dp, RetroOlive.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("STRESS CALCULATOR PIPELINE", color = RetroGreen, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    Text("Performs nested loops float ops counting benchmark results inside local memory sandbox.", color = RetroText.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                    
                    Button(
                        onClick = { viewModel.runOpenClBenchmark(100) },
                        colors = ButtonDefaults.buttonColors(containerColor = RetroGreen, contentColor = RetroBackground),
                        enabled = !isRunning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = RetroBackground)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RUNNING...")
                        } else {
                            Text("RUN 100x100 FLOAT BENCHMARK")
                        }
                    }

                    Button(
                        onClick = { viewModel.runOpenClBenchmark(200) },
                        colors = ButtonDefaults.buttonColors(containerColor = RetroOlive, contentColor = RetroGreen),
                        enabled = !isRunning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("RUN HEAVY 200x200 STRESS")
                    }
                }
            }
        }

        // Benchmark result report
        Card(
            modifier = Modifier.fillMaxWidth().weight(1.00f),
            colors = CardDefaults.cardColors(containerColor = RetroCard),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("📊 COMPILED FLOPS REPORT", color = RetroGreen, style = MaterialTheme.typography.labelSmall)
                Divider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 6.dp))
                
                if (benchmarkResult == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No benchmark statistics calculated yet.", color = RetroText.copy(alpha = 0.3f), style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    val report = benchmarkResult!!
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        item {
                            Text("Target Processor Hardware: ${report.hardwareString}", color = RetroText, style = MaterialTheme.typography.bodySmall)
                            Text("Matrix Calculation Grid: ${report.matrixSize} x ${report.matrixSize}", color = RetroText, style = MaterialTheme.typography.bodySmall)
                            Text("Mathematical Float CPU Loop iterations: ${report.totalOperations} passes", color = RetroText, style = MaterialTheme.typography.bodySmall)
                            Text("Aggregated Performance Measurement: ${report.megaFlops} MFLOPS", color = RetroGreen, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                            Text("Hardware Acceleration Gain multiplier: ${report.accelerationFactor}x", color = RetroGreen, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Calculated in: ${report.durationMs} ms", color = TerminalBlue, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

// --- BOTTOM PERSISTENT NAVIGATION BAR ---
@Composable
fun ConsoleNavigationFooter(activeTab: String, onTabSelected: (String) -> Unit) {
    Surface(
        color = RetroBackground,
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.05f))
            .height(64.dp)
    ) {
        val navTabs = listOf(
            Triple("CONSOLE", "Console", Icons.Default.Terminal),
            Triple("TERMUX", "Termux", Icons.Default.Code),
            Triple("LIVE", "Live Mode", Icons.Default.Mic),
            Triple("SKILLS", "Skills", Icons.Default.Extension),
            Triple("FILES", "Files", Icons.Default.Folder),
            Triple("PROMPTS", "Prompts", Icons.Default.Memory),
            Triple("OPENCL", "Accel", Icons.Default.Hardware),
            Triple("SETTINGS", "Settings", Icons.Default.Settings)
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            navTabs.forEach { (route, name, icon) ->
                val isSelected = activeTab == route
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) RetroOlive else Color.Transparent)
                        .clickable { onTabSelected(route) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("nav_${name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = name,
                        tint = if (isSelected) RetroGreen else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = name,
                        color = if (isSelected) RetroGreen else Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                }
            }
        }
    }
}

// ==========================================
// --- NEW TAB: EMBEDDED TERMUX TERMINAL ---
// ==========================================
@Composable
fun TermuxTabContent(viewModel: ConsoleViewModel) {
    val logs by viewModel.termuxConsoleLogs.collectAsStateWithLifecycle()
    val command by viewModel.termuxCommand.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val appFontSize by viewModel.appFontSize.collectAsStateWithLifecycle()

    val fontSizeSp = when (appFontSize) {
        "SMALL" -> 10.sp
        "MEDIUM" -> 12.sp
        "LARGE" -> 15.sp
        "HUGE" -> 18.sp
        else -> 12.sp
    }
    
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Text(
            text = "💻 RETRO TERMUX TERMINAL SECURED GATEWAY",
            color = RetroGreen,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Connected profiles: siewkagaming@gmail.com // Sandbox container active",
            color = RetroText.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Monospaced virtual screen
        Box(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .border(2.dp, RetroGreen.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(logs) { line ->
                    Text(
                        text = line,
                        color = if (line.startsWith("root@Ω-16:~#")) RetroGreen else RetroText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSizeSp,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick macro keys row (like Termux macro row!)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val macroKeys = listOf("help", "sys_sync", "sys_repair --cell=DYN_07", "gdrive", "mcp", "ls")
            macroKeys.forEach { key ->
                Box(
                    modifier = Modifier
                        .weight(1.0f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(RetroGray.copy(alpha = 0.4f))
                        .clickable { viewModel.submitTermuxCommand(key) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (key.contains("sys_repair")) "repair" else key,
                        color = RetroGreen,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(RetroCard)
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "root@Ω-16:~# ",
                color = RetroGreen,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
            val kbController = LocalSoftwareKeyboardController.current
            TextField(
                value = command,
                onValueChange = { viewModel.termuxCommand.value = it },
                modifier = Modifier
                    .weight(1.0f)
                    .testTag("termux_input_field"),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = RetroText,
                    unfocusedTextColor = RetroText
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                placeholder = {
                    Text(
                        text = "pkg install, sys_sync ...",
                        color = Color.White.copy(alpha = 0.25f),
                        fontSize = 12.sp,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    viewModel.submitTermuxCommand(command)
                    kbController?.hide()
                })
            )
            IconButton(
                onClick = {
                    viewModel.submitTermuxCommand(command)
                    kbController?.hide()
                },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Send Command",
                    tint = RetroGreen,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ==========================================
// --- NEW TAB: LIVE COGNITIVE INTERCOM ---
// ==========================================
@Composable
fun LiveModeTabContent(viewModel: ConsoleViewModel) {
    val transcriptions by viewModel.liveTranscriptions.collectAsStateWithLifecycle()
    val isMicOn by viewModel.isLiveMicOn.collectAsStateWithLifecycle()
    val liveStatus by viewModel.liveStatusText.collectAsStateWithLifecycle()
    
    // Custom states
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val isVoiceActive by viewModel.isVoiceActive.collectAsStateWithLifecycle()
    val isScreenSharingActive by viewModel.isScreenSharingActive.collectAsStateWithLifecycle()
    
    val listState = rememberLazyListState()
    
    var vocalInput by remember { mutableStateOf("") }
    
    LaunchedEffect(transcriptions.size) {
        if (transcriptions.isNotEmpty()) {
            listState.animateScrollToItem(transcriptions.size - 1)
        }
    }

    // Dynamic wave animation scale
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val audioWaveHeight by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🎙️ OMEGA-SILENT VOCAL COGNITIVE LINK",
            color = RetroGreen,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Start)
        )
        Text(
            text = "Continuous neural simulation stream. Simulated zero-trace vocoder active.",
            color = RetroText.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 12.dp).align(Alignment.Start)
        )

        // Pulsing Soundwave Visualizer Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = TerminalBlack),
            border = BorderStroke(1.dp, RetroGreen.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Pulse indicator
                Box(
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val numBars = 12
                        for (i in 0 until numBars) {
                            val pulseMultiplier = if (isVoiceActive || isMicOn || liveStatus == "AGENT_TALKING") {
                                val phaseShift = (i * 0.15f)
                                kotlin.math.sin(audioWaveHeight + phaseShift) * 1.2f + 1.5f
                            } else {
                                0.2f
                            }
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height((10.dp + (22.dp * pulseMultiplier)))
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (isVoiceActive || isMicOn) RetroGreen else if (liveStatus == "AGENT_TALKING") TerminalBlue else RetroGray)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "STATUS: ${liveStatus.uppercase()}",
                    color = if (isVoiceActive || isMicOn) RetroGreen else if (liveStatus == "AGENT_TALKING") TerminalBlue else RetroText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Network Link status
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LINK: ${if (isConnected) "ONLINE" else "OFFLINE"}",
                        color = if (isConnected) RetroGreen else Color.Red.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "MIC: ${if (isVoiceActive) "ACTIVE" else "STANDBY"}",
                        color = if (isVoiceActive) RetroGreen else RetroGray,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "MATRIX: ${if (isScreenSharingActive) "STREAMING" else "STANDBY"}",
                        color = if (isScreenSharingActive) RetroGreen else RetroGray,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Transcription feed
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f),
            colors = CardDefaults.cardColors(containerColor = RetroCard),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(12.dp).fillMaxSize()) {
                Text("SPEECH TRANSCRIPTIONS FEED", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1.0f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(transcriptions) { log ->
                        val isUser = log.sender == "USER"
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isUser) RetroOlive.copy(alpha = 0.3f) else TerminalBlack)
                                    .border(1.dp, if (isUser) RetroGreen.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isUser) "You: " else "Claw: ",
                                    color = if (isUser) RetroGreen else TerminalBlue,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = log.speechText,
                                    color = RetroText,
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = log.timeStr,
                                color = RetroText.copy(alpha = 0.3f),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Vocal triggers & custom simulated speech input
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val context = LocalContext.current
            val activity = context as? MainActivity

            // 1. WS Connect Button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) TerminalBlue else RetroGray)
                    .clickable { viewModel.toggleWebSocketConnection() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudQueue,
                    contentDescription = "Socket Toggle",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 2. Real-time Microphone Button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isVoiceActive) RetroGreen else RetroGray)
                    .clickable {
                        if (isVoiceActive) {
                            viewModel.stopVoiceVoice()
                        } else {
                            activity?.checkAndStartVoiceRecording(viewModel)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Mic Toggle",
                    tint = if (isVoiceActive) RetroBackground else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 3. Screen Sharing Toggle Button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isScreenSharingActive) RetroGreen else RetroGray)
                    .clickable {
                        if (isScreenSharingActive) {
                            activity?.stopScreenSharing(viewModel)
                        } else {
                            activity?.checkAndStartScreenSharing(viewModel)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ScreenShare,
                    contentDescription = "Screen Share Toggle",
                    tint = if (isScreenSharingActive) RetroBackground else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Keyboard speech input simulator
            Row(
                modifier = Modifier
                    .weight(1.0f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(RetroCard)
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val kb = LocalSoftwareKeyboardController.current
                TextField(
                    value = vocalInput,
                    onValueChange = { vocalInput = it },
                    placeholder = { Text("Simulate spoken word query...", fontSize = 12.sp, color = RetroText.copy(alpha = 0.3f)) },
                    modifier = Modifier.weight(1.0f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = RetroText,
                        unfocusedTextColor = RetroText
                    ),
                    textStyle = MaterialTheme.typography.bodySmall,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (vocalInput.isNotBlank()) {
                            viewModel.submitLiveDialogueInput(vocalInput)
                            vocalInput = ""
                            kb?.hide()
                        }
                    })
                )
                IconButton(
                    onClick = {
                        if (vocalInput.isNotBlank()) {
                            viewModel.submitLiveDialogueInput(vocalInput)
                            vocalInput = ""
                            kb?.hide()
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Simulate Speak", tint = RetroGreen, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ==========================================
// --- NEW TAB: CORE SKILLS STUDIO ---
// ==========================================
@Composable
fun SkillsTabContent(viewModel: ConsoleViewModel) {
    val skills by viewModel.integratedSkills.collectAsStateWithLifecycle()
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }
    var newTrigger by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("🧠 CORE SKILLS SYSTEM ACTIVE", color = RetroGreen, style = MaterialTheme.typography.titleMedium)
                Text("Toggle operational triggers / create system skill adaptors", color = RetroText.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
            }
            
            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = RetroOlive, contentColor = RetroGreen),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Skill", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("NEW SKILL", style = MaterialTheme.typography.labelSmall)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1.0f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(skills) { skill ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = RetroCard),
                    border = BorderStroke(1.dp, if (skill.isActive) RetroGreen.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1.0f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (skill.isActive) RetroGreen else RetroGray)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = skill.title, 
                                    color = if (skill.isActive) RetroGreen else RetroText, 
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = skill.description, color = RetroText.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Black.copy(alpha = 0.4f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "TRIGGER CODE: ${skill.triggerCode}",
                                    color = Color.Green.copy(alpha = 0.4f),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = skill.isActive,
                                onCheckedChange = { viewModel.toggleSkillActive(skill.id) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = RetroBackground,
                                    checkedTrackColor = RetroGreen,
                                    uncheckedThumbColor = RetroGray,
                                    uncheckedTrackColor = Color.Black
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(onClick = { viewModel.purgeSkill(skill) }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Purge", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = RetroCard,
            title = { Text("CRAFT NEW SYSTEM SKILL", color = RetroGreen) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Skill Identifier Profile") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RetroGreen, focusedLabelColor = RetroGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newDesc,
                        onValueChange = { newDesc = it },
                        label = { Text("Scope Description") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RetroGreen, focusedLabelColor = RetroGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newTrigger,
                        onValueChange = { newTrigger = it },
                        label = { Text("Dynamic Trigger Code (Trigger)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RetroGreen, focusedLabelColor = RetroGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            viewModel.addCustomSkill(newTitle, newDesc, newTrigger)
                            newTitle = ""
                            newDesc = ""
                            newTrigger = ""
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("SAVE", color = RetroGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("ABORT", color = Color.Red)
                }
            }
        )
    }
}

// ==========================================
// --- NEW TAB: SETTINGS & MCP / CLOUD SYS ---
// ==========================================
@Composable
fun SettingsTabContent(viewModel: ConsoleViewModel) {
    val isGDriveConnected by viewModel.isGDriveConnected.collectAsStateWithLifecycle()
    val syncFreq by viewModel.gdriveSyncFrequency.collectAsStateWithLifecycle()
    val gdrawLogs by viewModel.gdriveBackupLogs.collectAsStateWithLifecycle()
    
    val mcpEndpoints by viewModel.mcpEndpoints.collectAsStateWithLifecycle()
    val currentMcpEndpoint by viewModel.currentMcpEndpoint.collectAsStateWithLifecycle()
    val mcpTools by viewModel.mcpTools.collectAsStateWithLifecycle()
    val mcpLogs by viewModel.mcpActiveLogs.collectAsStateWithLifecycle()

    val appFontSize by viewModel.appFontSize.collectAsStateWithLifecycle()
    val modelCustomMemory by viewModel.modelCustomMemory.collectAsStateWithLifecycle()

    val gitUrl by viewModel.githubUrl.collectAsStateWithLifecycle()
    val gitBranch by viewModel.githubBranch.collectAsStateWithLifecycle()
    val gitLogs by viewModel.githubLogs.collectAsStateWithLifecycle()

    var showMcpAddDialog by remember { mutableStateOf(false) }
    var showMcpCallDialog by remember { mutableStateOf(false) }
    var newEndpointUrl by remember { mutableStateOf("") }
    
    var selectedToolCall by remember { mutableStateOf<McpTool?>(null) }
    var customArgsJson by remember { mutableStateOf("{\"force\": true}") }

    var customInstructionsText by remember { mutableStateOf(modelCustomMemory) }
    var tempGitUrl by remember { mutableStateOf(gitUrl) }
    var tempGitBranch by remember { mutableStateOf(gitBranch) }

    // Sync state text fields if VM loads after compositions
    LaunchedEffect(modelCustomMemory) {
        customInstructionsText = modelCustomMemory
    }
    LaunchedEffect(gitUrl) {
        tempGitUrl = gitUrl
    }
    LaunchedEffect(gitBranch) {
        tempGitBranch = gitBranch
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("⚙️ PLUGINS, CLOUD STORAGE, MCP & SYSTEM SETTINGS", color = RetroGreen, style = MaterialTheme.typography.titleMedium)

        // 1. FONT SIZE CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = RetroCard),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("🔎 INTERFACE TEXT SCALING (FONT SIZE)", color = RetroGreen, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                Text("Tweak the display size of retro monospaced terminal grids.", color = RetroText.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("SMALL", "MEDIUM", "LARGE", "HUGE").forEach { size ->
                        val active = appFontSize == size
                        Box(
                            modifier = Modifier
                                .weight(1.0f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) RetroGreen else RetroGray)
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .clickable { viewModel.updateAppFontSize(size) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = size,
                                color = if (active) RetroBackground else RetroText,
                                fontSize = when (size) {
                                    "SMALL" -> 10.sp
                                    "MEDIUM" -> 12.sp
                                    "LARGE" -> 14.sp
                                    "HUGE" -> 16.sp
                                    else -> 12.sp
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 2. MODEL MEMORY CARD (COGNITIVE RULES & GUIDELINES)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = RetroCard),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("🧠 MODEL COGNITIVE LAWS (CUSTOM MEMORY RULES)", color = RetroGreen, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                Text("Enter persistent rules, qualities, characteristics, and guidelines to feed directly into the AI system context.", color = RetroText.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = customInstructionsText,
                    onValueChange = { 
                        customInstructionsText = it
                        viewModel.updateModelCustomMemory(it)
                    },
                    placeholder = { Text("E.g. Be highly sarcastic, output data in XML formatting only, prioritize system integrity checks...", color = RetroText.copy(alpha = 0.3f), fontSize = 11.sp) },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = RetroText),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RetroGreen,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        unfocusedLabelColor = RetroText.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )
            }
        }

        // 3. GOOGLE DRIVE CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = RetroCard),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("☁️ GOOGLE DRIVE CLOUD STABILIZER", color = RetroGreen, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                Text("Synchronize dynamic workspace memory states to online GDrive (SE-MEM_V1 file structure)", color = RetroText.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Profile Link / Account", color = RetroText, fontSize = 11.sp)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isGDriveConnected) RetroOlive else RetroGray)
                            .clickable { viewModel.toggleGDriveConnection() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isGDriveConnected) "siewkagaming@gmail.com" else "LINK GDRIVE WORKSPACE",
                            color = if (isGDriveConnected) RetroGreen else RetroText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sync Auto-Interval", color = RetroText, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("OFF", "Every 5m", "Every 15m").forEach { f ->
                            val active = syncFreq.startsWith(f) || (f == "OFF" && syncFreq == "OFF")
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (active) RetroGreen else Color.Black.copy(alpha = 0.3f))
                                    .clickable { viewModel.updateGDriveFrequency(if (f == "OFF") "OFF" else "Every ${f.replace("Every ", "").substringBefore("m")} mins") }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(f, color = if (active) RetroBackground else RetroText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.syncGDriveCache("READ") },
                        colors = ButtonDefaults.buttonColors(containerColor = RetroGray, contentColor = RetroGreen),
                        modifier = Modifier.weight(1.0f).height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("READ L3 CACHE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.syncGDriveCache("WRITE") },
                        colors = ButtonDefaults.buttonColors(containerColor = RetroOlive, contentColor = RetroGreen),
                        modifier = Modifier.weight(1.0f).height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("WRITE CLOUD", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("GDRAW SYNC ACTIVITY LOGS:", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall)
                Box(
                    modifier = Modifier
                        .height(60.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .padding(6.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(gdrawLogs) { item ->
                            Text(item, color = RetroText, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        // 4. GITHUB REPOSITORY SYNC CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = RetroCard),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("🐙 GITHUB CORES BRIDGE", color = RetroGreen, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Push or merge local memory sectors to upstream git structures", color = RetroText.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(RetroOlive)
                            .clickable { viewModel.updateGithubConfig(tempGitUrl, tempGitBranch) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("SAVE CONFIG", color = RetroGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = tempGitUrl,
                        onValueChange = { tempGitUrl = it },
                        label = { Text("Git Repo Link", fontSize = 10.sp) },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = RetroText),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RetroGreen),
                        modifier = Modifier.weight(2.0f)
                    )
                    OutlinedTextField(
                        value = tempGitBranch,
                        onValueChange = { tempGitBranch = it },
                        label = { Text("Target Branch", fontSize = 10.sp) },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = RetroText),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RetroGreen),
                        modifier = Modifier.weight(1.0f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.syncGithubRepo("PULL") },
                        colors = ButtonDefaults.buttonColors(containerColor = RetroGray, contentColor = RetroGreen),
                        modifier = Modifier.weight(1.0f).height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("PULL FROM UPSTREAM", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.syncGithubRepo("PUSH") },
                        colors = ButtonDefaults.buttonColors(containerColor = RetroOlive, contentColor = RetroGreen),
                        modifier = Modifier.weight(1.0f).height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("PUSH TO REPOSITORY", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("GITHUB MERGE ACTIONS ENGINE LOGS:", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall)
                Box(
                    modifier = Modifier
                        .height(60.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .padding(6.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(gitLogs) { logLine ->
                            Text(logLine, color = RetroGreen, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        // 5. MCP DOCK PORT CONNECTOR CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = RetroCard),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("🔌 MCP TOOL CONNECTOR", color = TerminalBlue, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Secure model context protocol execution tunnels", color = RetroText.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { showMcpAddDialog = true }, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add MCP Protocol Host", tint = TerminalBlue, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("ACTIVE MCP ENGINE GATEPORT:", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall)
                LazyColumn(
                    modifier = Modifier.height(60.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(mcpEndpoints) { hostUrl ->
                        val active = currentMcpEndpoint == hostUrl
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) TerminalBlue.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.02f))
                                .border(1.dp, if (active) TerminalBlue else Color.Transparent, RoundedCornerShape(6.dp))
                                .clickable { viewModel.currentMcpEndpoint.value = hostUrl }
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (active) TerminalBlue else RetroGray))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(hostUrl, color = RetroText, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.0f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("DISCOVERED ACTIONS IN MODEL MATRIX (CALL):", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    mcpTools.forEach { tool ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedToolCall = tool
                                    customArgsJson = tool.schema
                                    showMcpCallDialog = true
                                },
                            colors = CardDefaults.cardColors(containerColor = TerminalBlack),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                Text(tool.name, color = TerminalBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(tool.description, color = RetroText.copy(alpha = 0.6f), fontSize = 8.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("MCP CHANNEL TELEMETRY TRACES:", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall)
                Box(
                    modifier = Modifier
                        .height(60.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .padding(6.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(mcpLogs) { ent ->
                            Text(ent, color = TerminalBlue, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        // 6. HELP DESK & DIAGNOSTICS INTEL MANUAL CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = RetroCard),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("📖 HELP DESK & DIAGNOSTICS INTEL MANUAL", color = TerminalBlue, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                Text("Operational manuals and specifications guidelines for dynamic services.", color = RetroText.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(10.dp))
                
                var expandedManualTopic by remember { mutableStateOf("OVERVIEW") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    listOf("OVERVIEW", "GDRIVE", "GITHUB", "MCP", "MODELS").forEach { topic ->
                        val active = expandedManualTopic == topic
                        Box(
                            modifier = Modifier
                                .weight(1.0f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) TerminalBlue else RetroGray)
                                .clickable { expandedManualTopic = topic }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(topic, color = if (active) RetroBackground else RetroText, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                val instructionsManual = when (expandedManualTopic) {
                    "OVERVIEW" -> """
                        WELCOME TO THE CLOUD RETRO CONSOLE MANUAL
                        -----------------------------------------
                        This unified Retro Terminal console merges neural compiler execution
                        layers, advanced on-device and remote MCP tooling bridges,
                        local Termux shell sandboxes, and file persistence models.
                        
                        - Configure UI scaling via 'Font Size Settings' dynamically.
                        - Use the 'Console' to execute queries against target systems.
                        - Tweak model memories to enforce persistent characteristics.
                    """.trimIndent()
                    "GDRIVE" -> """
                        GOOGLE DRIVE CLOUD SYNCHRONIZATION
                        ----------------------------------
                        Syncs your local offline database records (SE-MEM_V1) with cloud storage.
                        
                        1. LINK USER PROFILE: Verifies active cloud Google Drive credentials.
                        2. SYNC INTERVALS: Specify custom intervals for automatic write cycles.
                        3. WRITE CLOUD: Formats and serializes local states to secure cloud storage.
                        4. READ CACHE: Query and pull your remote sector profiles into device.
                    """.trimIndent()
                    "GITHUB" -> """
                        GITHUB UPSTREAM INTEGRATION BRIDGE
                        ----------------------------------
                        Configure and commit prompt states directly to target Git repositories.
                        
                        - Repositories are authenticated via secure virtual SSH keys.
                        - PULL: Performs automated branch merge with fast-forward.
                        - PUSH: Automatically stages, commits, and pushes model workspace configurations to remote branch.
                    """.trimIndent()
                    "MCP" -> """
                        MODEL CONTEXT PROTOCOL (MCP) INTERFACE
                        --------------------------------------
                        MCP enables client-server structures where AI models
                        invoke secure dynamic on-device and remote software tools.
                        
                        - ACTIVE ENDPOINTS: Specify target router endpoints.
                        - REMOTE ACTIONS: Tap any discovered tool (e.g. gdrive_sync_mem,
                          cyber_anomaly_detector) to pop up arguments JSON execution window.
                    """.trimIndent()
                    "MODELS" -> """
                        DYNAMIC MULTI-MODEL KERNEL SWITCHER
                        -----------------------------------
                        Seamless switcher targeting elite AI networks:
                        
                        - GEMINI: High-context native multimodal capabilities.
                        - CLAUDE: Highly precise, academic, and layout perfect reasoning style.
                        - DEEPSEEK: Fast, mathematically sound reasoning graphs.
                        - GROK: Witty, funny responses suited for terminal diagnostics.
                        
                        * Foreign model engines automatically simulate custom characteristics.
                    """.trimIndent()
                    else -> ""
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .padding(8.dp)
                ) {
                    Text(
                        text = instructionsManual,
                        color = TerminalBlue,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        lineHeight = 12.sp
                    )
                }
            }
        }
    }

    if (showMcpAddDialog) {
        AlertDialog(
            onDismissRequest = { showMcpAddDialog = false },
            containerColor = RetroCard,
            title = { Text("REGISTER NEW MCP ENDPOINT", color = TerminalBlue) },
            text = {
                OutlinedTextField(
                    value = newEndpointUrl,
                    onValueChange = { newEndpointUrl = it },
                    label = { Text("Server protocol URL (HTTP/HTTPS)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TerminalBlue, focusedLabelColor = TerminalBlue),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newEndpointUrl.isNotBlank()) {
                            viewModel.addMcpEndpoint(newEndpointUrl)
                            newEndpointUrl = ""
                            showMcpAddDialog = false
                        }
                    }
                ) {
                    Text("ADD", color = TerminalBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMcpAddDialog = false }) {
                    Text("ABORT", color = Color.Red)
                }
            }
        )
    }

    if (showMcpCallDialog && selectedToolCall != null) {
        AlertDialog(
            onDismissRequest = { showMcpCallDialog = false },
            containerColor = RetroCard,
            title = { Text("EXECUTE RPC TOOL: ${selectedToolCall?.name}", color = TerminalBlue) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Input Argument parameters (JSON format):", color = RetroText, fontSize = 11.sp)
                    OutlinedTextField(
                        value = customArgsJson,
                        onValueChange = { customArgsJson = it },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TerminalBlue, focusedLabelColor = TerminalBlue),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.executeMcpCall(selectedToolCall!!.name, customArgsJson)
                        showMcpCallDialog = false
                        selectedToolCall = null
                    }
                ) {
                    Text("EXECUTE", color = TerminalBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showMcpCallDialog = false
                    selectedToolCall = null
                }) {
                    Text("ABORT", color = Color.Red)
                }
            }
        )
    }
}

