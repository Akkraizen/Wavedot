package space.wavedot.plugin.subsonic

import java.security.MessageDigest

internal actual fun md5Hex(input: String): String =
    MessageDigest.getInstance("MD5").digest(input.encodeToByteArray()).joinToString("") { "%02x".format(it) }
