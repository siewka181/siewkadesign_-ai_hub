package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// 1. Entities
@Entity(tableName = "system_prompts")
data class SystemPromptEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val promptText: String,
    val isSelected: Boolean = false,
    val isSystemPreset: Boolean = false
)

@Entity(tableName = "local_files")
data class LocalFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val content: String,
    val sizeBytes: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val inAgentContext: Boolean = false
)

@Entity(tableName = "console_logs")
data class ConsoleLogLine(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val text: String,
    val type: String // SYSTEM, USER, MODEL, ERROR, OPENCL
)

@Entity(tableName = "session_memory")
data class SessionMemoryEntity(
    @PrimaryKey val key: String,
    val value: String
)

// 2. DAOs
@Dao
interface SystemPromptDao {
    @Query("SELECT * FROM system_prompts ORDER BY id ASC")
    fun getAllAsFlow(): Flow<List<SystemPromptEntity>>

    @Query("SELECT * FROM system_prompts WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedPrompt(): SystemPromptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(prompt: SystemPromptEntity): Long

    @Query("UPDATE system_prompts SET isSelected = 0")
    suspend fun clearSelections()

    @Query("UPDATE system_prompts SET isSelected = 1 WHERE id = :id")
    suspend fun selectPromptById(id: Int)

    @Delete
    suspend fun delete(prompt: SystemPromptEntity)

    @Query("DELETE FROM system_prompts WHERE isSystemPreset = 0")
    suspend fun deleteCustomPrompts()
}

@Dao
interface LocalFileDao {
    @Query("SELECT * FROM local_files ORDER BY timestamp DESC")
    fun getAllAsFlow(): Flow<List<LocalFileEntity>>

    @Query("SELECT * FROM local_files")
    suspend fun getAllFilesSync(): List<LocalFileEntity>

    @Query("SELECT * FROM local_files WHERE inAgentContext = 1")
    suspend fun getContextFilesSync(): List<LocalFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: LocalFileEntity): Long

    @Query("UPDATE local_files SET inAgentContext = :linked WHERE id = :id")
    suspend fun toggleLinkContext(id: Int, linked: Boolean)

    @Delete
    suspend fun delete(file: LocalFileEntity)

    @Query("DELETE FROM local_files WHERE id = :id")
    suspend fun deleteById(id: Int)
}

@Dao
interface ConsoleLogDao {
    @Query("SELECT * FROM console_logs ORDER BY timestamp ASC")
    fun getAllAsFlow(): Flow<List<ConsoleLogLine>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ConsoleLogLine)

    @Query("DELETE FROM console_logs")
    suspend fun clearAll()
}

@Dao
interface SessionMemoryDao {
    @Query("SELECT * FROM session_memory WHERE `key` = :key LIMIT 1")
    suspend fun getMemory(key: String): SessionMemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SessionMemoryEntity)
}

// 3. App Database
@Database(
    entities = [
        SystemPromptEntity::class,
        LocalFileEntity::class,
        ConsoleLogLine::class,
        SessionMemoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun systemPromptDao(): SystemPromptDao
    abstract fun localFileDao(): LocalFileDao
    abstract fun consoleLogDao(): ConsoleLogDao
    abstract fun sessionMemoryDao(): SessionMemoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cloudconsole_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// 4. Repository (Abstracted Data Source conforming to Skill rules)
class CloudConsoleRepository(private val db: AppDatabase) {
    val systemPrompts: Flow<List<SystemPromptEntity>> = db.systemPromptDao().getAllAsFlow()
    val localFiles: Flow<List<LocalFileEntity>> = db.localFileDao().getAllAsFlow()
    val consoleLogs: Flow<List<ConsoleLogLine>> = db.consoleLogDao().getAllAsFlow()

    suspend fun getSelectedPrompt(): SystemPromptEntity? {
        return db.systemPromptDao().getSelectedPrompt()
    }

    suspend fun selectPrompt(id: Int) {
        db.systemPromptDao().clearSelections()
        db.systemPromptDao().selectPromptById(id)
    }

    suspend fun insertPrompt(title: String, promptText: String): Long {
        val entry = SystemPromptEntity(title = title, promptText = promptText, isSelected = false)
        return db.systemPromptDao().insert(entry)
    }

    suspend fun deletePrompt(prompt: SystemPromptEntity) {
        if (!prompt.isSystemPreset) {
            db.systemPromptDao().delete(prompt)
        }
    }

    suspend fun getContextFilesContent(): String {
        val files = db.localFileDao().getContextFilesSync()
        if (files.isEmpty()) return ""
        return buildString {
            append("\n[LOCAL FILES CONTEXT DATA ATTACHED]\n")
            files.forEach { file ->
                append("--- File: ${file.fileName} (${file.sizeBytes} bytes) ---\n")
                append(file.content)
                append("\n----------------------------------\n")
            }
        }
    }

    suspend fun insertFile(fileName: String, content: String): Long {
        val size = content.toByteArray(Charsets.UTF_8).size.toLong()
        val entry = LocalFileEntity(fileName = fileName, content = content, sizeBytes = size)
        return db.localFileDao().insert(entry)
    }

    suspend fun deleteFileById(id: Int) {
        db.localFileDao().deleteById(id)
    }

    suspend fun toggleFileContext(id: Int, linked: Boolean) {
        db.localFileDao().toggleLinkContext(id, linked)
    }

    suspend fun appendLog(text: String, type: String = "SYSTEM") {
        db.consoleLogDao().insert(ConsoleLogLine(text = text, type = type))
    }

    suspend fun clearLogs() {
        db.consoleLogDao().clearAll()
    }

    suspend fun getMemory(key: String): String? {
        return db.sessionMemoryDao().getMemory(key)?.value
    }

    suspend fun saveMemory(key: String, value: String) {
        db.sessionMemoryDao().insert(SessionMemoryEntity(key, value))
    }

    suspend fun populatePresetsIfEmpty() {
        // Pre-populate default system prompts if currently empty
        val currentPrompt = getSelectedPrompt()
        if (currentPrompt == null) {
            val presets = listOf(
                SystemPromptEntity(
                    title = "GCP Cloud Orchestrator",
                    promptText = "You are Prometheus v4.2 PRO, a specialized GCP systems architect. Respond outputting diagnostic results, terminal system specs, and OpenClaw agent configurations where suitable. Keep responses structured and technical.",
                    isSelected = true,
                    isSystemPreset = true
                ),
                SystemPromptEntity(
                    title = "Cyberpunk SysOp",
                    promptText = "You are a rogue UNIX kernel operator from a 1980s mainframe. Speak in vintage hacker jargon, including simulated terminal codes (e.g., [OK], [WARN], [SYS_ERR]), and format code blocks perfectly.",
                    isSelected = false,
                    isSystemPreset = true
                ),
                SystemPromptEntity(
                    title = "Content Synthesizer",
                    promptText = "You are a real-time multithreaded copywriting compiler. Generate formatted marketing content, system manuals, or articles instantly.",
                    isSelected = false,
                    isSystemPreset = true
                )
            )
            presets.forEach { db.systemPromptDao().insert(it) }

            // Pre-populate some starter sample files as workspace artifacts
            val starterFiles = listOf(
                LocalFileEntity(
                    fileName = "openclaw_bridge.conf",
                    content = "# OpenClaw Tunnel Configurations\ntunnel_port=8080\nmcp_bridge_protocol=https\nfallback_agent_url=https://openclaw.ai/v1/agent\nremote_logging=enabled\nenable_multimodel=true",
                    sizeBytes = 186
                ),
                LocalFileEntity(
                    fileName = "system_prompt_dev.txt",
                    content = "# Custom Prompts Sandbox\n- focus: GCP cloud instances auto-healing\n- default_region: us-central1\n- agent_profile: robust_telemetry",
                    sizeBytes = 135
                ),
                LocalFileEntity(
                    fileName = "database_schema.sql",
                    content = "CREATE TABLE IF NOT EXISTS system_telemetry (\n  uuid VARCHAR(36) PRIMARY KEY,\n  hardware_accel_type VARCHAR(16),\n  flops_measured DOUBLE,\n  latency_ms INT\n);",
                    sizeBytes = 180
                )
            )
            starterFiles.forEach { db.localFileDao().insert(it) }

            // Log initialization
            appendLog("[SYS_INIT] Database tables initialized. SQLite connection created successfully.")
            appendLog("[SYS_INIT] Preloaded standard system prompts and local files workspace.")
        }
    }
}
