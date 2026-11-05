package space.wavedot.infra.updater

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException
import space.wavedot.core.domain.update.AvailableUpdate
import java.io.File
import java.security.MessageDigest

class UpdateDownloader(private val http: HttpClient) {
    suspend fun download(update: AvailableUpdate, target: File, onProgress: (Long, Long) -> Unit = { _, _ -> }): File =
        withContext(Dispatchers.IO) {
            target.parentFile?.mkdirs()
            val partial = File(target.parentFile, "${target.name}.part")
            val digest = MessageDigest.getInstance("SHA-256")
            http.prepareGet(update.downloadUrl).execute { response ->
                if (!response.status.isSuccess()) {
                    throw WavedotException(WavedotError.Network("Download failed: ${response.status.value}"))
                }
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(64 * 1024)
                var received = 0L
                partial.outputStream().use { out ->
                    while (true) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read < 0) break
                        if (read == 0) continue
                        out.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        received += read
                        onProgress(received, update.size)
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(update.sha256, ignoreCase = true)) {
                partial.delete()
                throw WavedotException(WavedotError.Unknown("Checksum mismatch for ${update.version}"))
            }
            if (target.exists()) target.delete()
            check(partial.renameTo(target)) { "Could not move update into place" }
            target
        }
}
