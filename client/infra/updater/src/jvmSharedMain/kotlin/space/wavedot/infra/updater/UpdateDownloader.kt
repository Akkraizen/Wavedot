package space.wavedot.infra.updater

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import space.wavedot.core.common.SecureUrl
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException
import space.wavedot.core.domain.update.AvailableUpdate
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class UpdateDownloader(private val http: HttpClient) {
    suspend fun download(update: AvailableUpdate, target: File, onProgress: (Long, Long) -> Unit = { _, _ -> }): File =
        withContext(Dispatchers.IO) {
            SecureUrl.require(update.downloadUrl, "The update download URL")
            if (update.size <= 0) throw WavedotException(WavedotError.Invalid("The update does not state its size"))
            target.parentFile?.mkdirs()
            val partial = File(target.parentFile, "${target.name}.part")
            try {
                val actual = fetch(update, partial, onProgress)
                if (!actual.equals(update.sha256, ignoreCase = true)) {
                    throw WavedotException(WavedotError.Invalid("Checksum mismatch for ${update.version}"))
                }
                Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                target
            } finally {
                partial.delete()
            }
        }

    private suspend fun fetch(update: AvailableUpdate, partial: File, onProgress: (Long, Long) -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var received = 0L
        http.prepareGet(update.downloadUrl).execute { response ->
            if (!response.status.isSuccess()) {
                throw WavedotException(WavedotError.Network("Download failed: ${response.status.value}"))
            }
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(BUFFER_SIZE)
            partial.outputStream().use { out ->
                while (true) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read < 0) break
                    if (read == 0) continue
                    received += read
                    if (received > update.size) {
                        throw WavedotException(WavedotError.Invalid("The download is larger than the announced ${update.size} bytes"))
                    }
                    out.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    onProgress(received, update.size)
                }
            }
        }
        if (received != update.size) {
            throw WavedotException(WavedotError.Invalid("The download is incomplete: $received of ${update.size} bytes"))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
