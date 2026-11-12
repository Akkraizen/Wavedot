package space.wavedot.infra.storage

import com.sun.jna.platform.win32.Crypt32Util

object DesktopSecretCipher {
    fun forCurrentSystem(osName: String = System.getProperty("os.name")): SecretCipher? =
        if (osName.lowercase().contains("win")) WindowsDataProtectionCipher else null
}

object WindowsDataProtectionCipher : SecretCipher {
    override fun encrypt(plain: ByteArray): ByteArray = Crypt32Util.cryptProtectData(plain)

    override fun decrypt(sealed: ByteArray): ByteArray = Crypt32Util.cryptUnprotectData(sealed)
}
