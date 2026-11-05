package space.wavedot.plugin.yandexmusic.api

import space.wavedot.plugin.yandexmusic.crypto.PlatformCrypto
import kotlin.io.encoding.Base64

data class ClientProfile(
    val signKey: String,
    val clientHeader: String,
) {
    companion object {
        val ANDROID: ClientProfile = ClientProfile("p93jhgh689SBReK6ghtw62", "YandexMusicAndroid/24023621")
        val DESKTOP: ClientProfile = ClientProfile("kzqU4XhfCaY6B6JTHODeq5", "YandexMusicDesktopAppWindows/5.95.0")
    }
}

internal object FileInfoSigner {
    fun sign(
        key: String,
        timestamp: Long,
        trackId: String,
        quality: String,
        codecs: List<String>,
        transport: String,
    ): String {
        val message = "$timestamp$trackId$quality${codecs.joinToString("")}$transport"
        val digest = PlatformCrypto.hmacSha256(key.encodeToByteArray(), message.encodeToByteArray())
        return Base64.encode(digest).dropLast(1)
    }
}
