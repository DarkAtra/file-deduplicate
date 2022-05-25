package de.darkatra.filededuplicate

import kotlinx.serialization.Serializable
import org.kodein.db.model.Id

@Serializable
data class FileModelEntity(
	@Id
	val absolutePath: String,
	val checksum: String,
)
