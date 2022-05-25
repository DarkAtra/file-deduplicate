package de.darkatra.filededuplicate

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.kodein.db.DB
import org.kodein.db.getById
import org.kodein.db.impl.open
import org.kodein.db.orm.kotlinx.KotlinxSerializer
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

class FileDeduplicateService {

	private val database = DB.open(System.getProperty("user.home") + "/Desktop/db", KotlinxSerializer {
		FileModelEntity.serializer()
	})

	private val filesChannel = MutableSharedFlow<FileModel>()
	private val checksumsCalculatedChannel = MutableSharedFlow<FileModel>()
	private val duplicatesChannel = MutableSharedFlow<FileModel>()
	private val files = CopyOnWriteArrayList<FileModel>()

	@OptIn(ExperimentalCoroutinesApi::class)
	private val fileScanScope = CoroutineScope(Dispatchers.IO.limitedParallelism(1))

	@OptIn(ExperimentalCoroutinesApi::class)
	private val checksumCalculationScope = CoroutineScope(Dispatchers.IO.limitedParallelism(32))

	private val duplicateScanScope = CoroutineScope(Dispatchers.Unconfined)

	init {

		getFilesChannel()
			.buffer(100000)
			.filterNot { file ->
				val entity = database.getById<FileModelEntity>(file.absolutePath)
				if (entity != null) {
					file.checksum = entity.checksum
					checksumsCalculatedChannel.emit(file)
				}
				entity != null
			}
			.onEach { file ->
				checksumCalculationScope.launch {
					file.checksum = file.sha256()
					database.put(FileModelEntity(
						absolutePath = file.absolutePath,
						checksum = file.checksum!!
					))
					checksumsCalculatedChannel.emit(file)
				}
			}
			.launchIn(duplicateScanScope)

		getChecksumsCalculatedChannel()
			.onEach { file ->
				val filesWithSameChecksum = files.filter { it.checksum == file.checksum }
				when {
					// publish both matches when we first detect a duplicate
					filesWithSameChecksum.size == 2 -> filesWithSameChecksum.forEach { duplicatesChannel.emit(it) }
					// publish the new match when further duplicates are detected
					filesWithSameChecksum.size > 2 -> duplicatesChannel.emit(file)
				}
			}
			.launchIn(duplicateScanScope)
	}

	fun getFilesChannel(): Flow<FileModel> {
		return filesChannel.asSharedFlow()
	}

	fun getChecksumsCalculatedChannel(): SharedFlow<FileModel> {
		return checksumsCalculatedChannel.asSharedFlow()
	}

	fun getDuplicatesChannel(): SharedFlow<FileModel> {
		return duplicatesChannel.asSharedFlow()
	}

	fun scanFiles(path: Path) {
		fileScanScope.launch {
			path.toFile().walk()
				.filter(File::isFile)
				.map { file -> FileModel(path = file.toPath()) }
				.forEach { file ->
					files.add(file)
					filesChannel.emit(file)
				}
		}
	}
}
