package space.wavedot.plugin.yandexmusic.crypto

import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal actual object PlatformCrypto {
    actual fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(message)
    }

    actual fun aesCtrDecryptor(key: ByteArray, initialBlock: Long): CtrDecryptor {
        val iv = ByteArray(16)
        var counter = initialBlock
        for (i in 15 downTo 8) {
            iv[i] = (counter and 0xFF).toByte()
            counter = counter ushr 8
        }
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return object : CtrDecryptor {
            override fun update(data: ByteArray, offset: Int, length: Int) {
                if (length == 0) return
                val written = cipher.update(data, offset, length, data, offset)
                check(written == length) { "CTR cipher produced $written of $length bytes" }
            }
        }
    }
}
