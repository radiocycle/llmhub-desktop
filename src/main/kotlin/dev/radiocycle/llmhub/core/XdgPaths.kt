package dev.radiocycle.llmhub.core

import java.io.File

object XdgPaths {
    private val userHome = System.getProperty("user.home") ?: "."

    val configDir: File by lazy {
        val base = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
            ?: "$userHome/.config"
        File(base, "llmhub").apply { mkdirs() }
    }

    val dataDir: File by lazy {
        val base = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
            ?: "$userHome/.local/share"
        File(base, "llmhub").apply { mkdirs() }
    }

    val defaultWorkspaceDir: File by lazy {
        File(dataDir, "workspace").apply { mkdirs() }
    }

    val homeDir: File by lazy {
        File(userHome)
    }
}
