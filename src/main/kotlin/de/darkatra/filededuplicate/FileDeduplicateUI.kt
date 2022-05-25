package de.darkatra.filededuplicate

import com.formdev.flatlaf.FlatDarculaLaf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.UIManager
import javax.swing.table.DefaultTableModel

class FileDeduplicateUI {

	private val fileDeduplicateService = FileDeduplicateService()
	private val updateScope = CoroutineScope(Dispatchers.Unconfined)

	private val frame: JFrame
	private val statusLabel: JLabel

	private val checksumsCalculated = AtomicInteger(0)
	private val duplicatedFound = AtomicInteger(0)
	private val totalFiles = AtomicInteger(0)
	private val duplicateFiles = DefaultTableModel().apply {
		addColumn("File")
		addColumn("Checksum")
	}

	init {
		FlatDarculaLaf.setup()
		UIManager.put("Component.focusWidth", 0)
		UIManager.put("ScrollBar.showButtons", true)

		frame = JFrame().apply {
			title = "File Deduplicate"
			setSize(1400, 800)
			setLocationRelativeTo(null)
			defaultCloseOperation = JFrame.EXIT_ON_CLOSE
		}

		frame.add(JPanel().apply {
			layout = BorderLayout()

			statusLabel = JLabel("Status: -/-")
			add(statusLabel, BorderLayout.NORTH)

			add(JScrollPane(JTable(duplicateFiles)))
		})

		frame.isVisible = true
	}

	suspend fun scan(path: Path) = runBlocking {

		fileDeduplicateService.getFilesChannel()
			.onEach { totalFiles.addAndGet(1) }
			.launchIn(updateScope)

		fileDeduplicateService.getChecksumsCalculatedChannel()
			.onEach { checksumsCalculated.addAndGet(1) }
			.launchIn(updateScope)

		fileDeduplicateService.getDuplicatesChannel()
			.onEach { file ->
				duplicatedFound.addAndGet(1)
				launch(Dispatchers.Main) {
					duplicateFiles.addRow(arrayOf(
						file.absolutePath,
						file.checksum
					))
				}
			}
			.launchIn(updateScope)

		fileDeduplicateService.scanFiles(path)

		// ui updates
		launch(Dispatchers.Unconfined) {
			while (true) {
				launch(Dispatchers.Main) {
					statusLabel.text = "Status: ${duplicatedFound.get()}/${checksumsCalculated.get()}/${totalFiles.get()}"
				}
				delay(100)
			}
		}
	}
}

suspend fun main() {
	FileDeduplicateUI().also {
		it.scan(Path.of("H:/"))
	}
}
