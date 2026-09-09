package dev.radiocycle.llmhub.ui.common

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

object DesktopFilePicker {
    fun pickFile(title: String = "Select file"): File? {
        return try {
            val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
            dialog.isVisible = true
            val file = dialog.file
            val dir = dialog.directory
            if (file != null && dir != null) File(dir, file) else null
        } catch (_: Throwable) {
            val chooser = JFileChooser()
            chooser.dialogTitle = title
            val ret = chooser.showOpenDialog(null)
            if (ret == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
        }
    }
}
