package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.ConsoleViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: ConsoleViewModel = viewModel()
                CloudConsoleApp(viewModel)
            }
        }
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
                .background(RetroBackground)
        ) {
            when (activeTab) {
                "CONSOLE" -> ConsoleTabContent(viewModel)
                "FILES" -> FilesTabContent(viewModel)
                "PROMPTS" -> PromptsTabContent(viewModel)
                "OPENCL" -> OpenClTabContent(viewModel)
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
                                style = MaterialTheme.typography.bodySmall
                            )

                            Text(
                                text = logLine.text,
                                color = if (logLine.type == "USER") RetroGreen else RetroText,
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
    NavigationBar(
        containerColor = RetroBackground,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.05f))
            .height(72.dp),
        tonalElevation = 8.dp
    ) {
        val navTabs = listOf(
            Triple("CONSOLE", "Console", Icons.Default.Terminal),
            Triple("FILES", "Files", Icons.Default.Folder),
            Triple("PROMPTS", "Prompts", Icons.Default.Memory),
            Triple("OPENCL", "Acceleration", Icons.Default.Hardware)
        )

        navTabs.forEach { (route, name, icon) ->
            val isSelected = activeTab == route
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(route) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = name,
                        tint = if (isSelected) RetroGreen else Color.White.copy(alpha = 0.5f)
                    )
                },
                label = {
                    Text(
                        text = name,
                        color = if (isSelected) RetroGreen else Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = RetroOlive
                ),
                modifier = Modifier.testTag("nav_${name.lowercase()}")
            )
        }
    }
}

