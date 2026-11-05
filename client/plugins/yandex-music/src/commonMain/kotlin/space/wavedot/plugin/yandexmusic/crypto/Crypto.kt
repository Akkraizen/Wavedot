package space.wavedot.plugin.yandexmusic.crypto

internal interface CtrDecryptor {
    fun update(data: ByteArray, offset: Int, length: Int)
}

internal expect object PlatformCrypto {
    fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray

    fun aesCtrDecryptor(key: ByteArray, initialBlock: Long): CtrDecryptor
}

internal fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "Hex string must have even length" }
    return ByteArray(length / 2) { ((this[it * 2].digitToInt(16) shl 4) or this[it * 2 + 1].digitToInt(16)).toByte() }
}
