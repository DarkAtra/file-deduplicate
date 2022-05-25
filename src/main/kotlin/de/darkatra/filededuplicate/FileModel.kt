package de.darkatra.filededuplicate

import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import kotlin.io.path.absolutePathString

data class FileModel(
	val path: Path,
	val absolutePath: String = path.absolutePathString(),
	var checksum: String? = null
) {

	fun sha256(): String {

		val digest = MessageDigest.getInstance("SHA-256")

		val channel = Files.newByteChannel(path, StandardOpenOption.READ)
		val buffer = ByteBuffer.allocateDirect(DEFAULT_BUFFER_SIZE)

		var read: Int
		while (channel.read(buffer).also { read = it } > 0) {
			buffer.position(0).limit(read)
			digest.update(buffer)
			buffer.clear()
		}

		return digest.digest().fold("") { str, it -> str + "%02x".format(it) }
	}
}
