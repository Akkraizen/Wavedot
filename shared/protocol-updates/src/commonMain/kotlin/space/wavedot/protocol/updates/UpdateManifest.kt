package space.wavedot.protocol.updates

import space.wavedot.protocol.core.ArchDto
import space.wavedot.protocol.core.PlatformDto
import space.wavedot.protocol.core.wireName

object UpdateManifest {
    const val SIGNATURE_ALGORITHM: String = "SHA256withECDSA"
    const val KEY_ALGORITHM: String = "EC"
    private const val HEADER = "wavedot-update-v1"

    fun canonical(
        version: String,
        platform: PlatformDto,
        arch: ArchDto,
        fileName: String,
        size: Long,
        sha256: String,
    ): String = listOf(HEADER, version, platform.wireName(), arch.wireName(), fileName, size.toString(), sha256.lowercase())
        .joinToString("\n")
}
