package dev.radiocycle.llmhub.tools

import dev.radiocycle.llmhub.core.XdgPaths
import dev.radiocycle.llmhub.data.repo.SettingsRepository
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Resolves the agent's working directory and the paths the file tools operate on.
 *
 * The workspace is a real filesystem path. When blank it falls back to the XDG default
 * workspace directory ($XDG_DATA_HOME/llmhub/workspace).
 */
class WorkspaceManager(
    private val settings: SettingsRepository,
) {
    /** The workspace directory, created on demand. */
    fun root(): File {
        val configured = settings.current.tools.workspacePath.trim()
        val dir = if (configured.isNotEmpty()) File(configured) else defaultRoot()
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun defaultRoot(): File = XdgPaths.defaultWorkspaceDir

    /** The user's home directory. */
    fun homeRoot(): File = XdgPaths.homeDir

    /**
     * Turns a tool-supplied path into a concrete [File]. Relative paths resolve against the
     * workspace; absolute paths are taken as-is. Throws when [restrictToWorkspace] is on and the
     * result would sit outside the workspace, so a confined agent cannot climb out with `..` or an
     * absolute path.
     */
    fun resolve(path: String): File {
        val trimmed = path.trim()
        require(trimmed.isNotEmpty()) { "path is required" }
        val root = root()
        val target = File(trimmed).let { if (it.isAbsolute) it else File(root, trimmed) }

        if (settings.current.tools.restrictToWorkspace) {
            val rootCanon = root.canonicalFile
            val targetCanon = target.canonicalFile
            val within = targetCanon == rootCanon ||
                targetCanon.path.startsWith(rootCanon.path + File.separator)
            require(within) {
                "path escapes the workspace ($trimmed). Turn off \"restrict to workspace\" in " +
                    "Settings to allow paths outside ${rootCanon.path}."
            }
        }
        return target
    }

    /** A workspace-relative label for display, falling back to the absolute path. */
    fun label(file: File): String = runCatching {
        val rootPath = root().canonicalFile.path
        val filePath = file.canonicalFile.path
        when {
            filePath == rootPath -> "."
            filePath.startsWith(rootPath + File.separator) -> filePath.substring(rootPath.length + 1)
            else -> filePath
        }
    }.getOrDefault(file.path)
}

/**
 * Detects and caches root access on Linux (sudo / su / direct root).
 */
object RootAccess {
    @Volatile private var probed = false
    @Volatile private var prefix: List<String>? = null

    private val CANDIDATES = listOf(
        listOf("sudo", "-n", "sh", "-c"),
        listOf("su", "-c"),
    )

    fun isCurrentUserRoot(): Boolean =
        System.getProperty("user.name") == "root" || runCatching {
            ProcessBuilder("id", "-u").start().inputStream.bufferedReader().readText().trim() == "0"
        }.getOrDefault(false)

    fun binaryPresent(): Boolean = isCurrentUserRoot() || SU_PATHS.any { File(it).exists() }

    @Synchronized
    fun commandPrefix(): List<String>? {
        if (probed) return prefix
        probed = true
        if (isCurrentUserRoot()) {
            prefix = emptyList()
            return prefix
        }
        prefix = CANDIDATES.firstOrNull { tryPrefix(it) }
        return prefix
    }

    fun isGranted(): Boolean = isCurrentUserRoot() || commandPrefix() != null

    fun modeLabel(): String? = when {
        isCurrentUserRoot() -> "root"
        commandPrefix()?.contains("sudo") == true -> "sudo"
        commandPrefix()?.contains("su") == true -> "su"
        else -> null
    }

    private fun tryPrefix(prefix: List<String>): Boolean = runCatching {
        val process = ProcessBuilder(prefix + "id -u").redirectErrorStream(true).start()
        val finished = process.waitFor(5, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            false
        } else {
            val output = process.inputStream.bufferedReader().readText().trim()
            process.exitValue() == 0 && output == "0"
        }
    }.getOrDefault(false)

    fun invalidate() {
        probed = false
        prefix = null
    }

    private val SU_PATHS = listOf(
        "/usr/bin/sudo", "/bin/sudo", "/usr/bin/su", "/bin/su",
    )
}
