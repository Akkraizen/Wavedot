package space.wavedot.infra.updater

import space.wavedot.protocol.updates.UpdateManifest
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class EcdsaUpdateSignatureVerifier(trustedKeys: List<String>) : UpdateSignatureVerifier {
    private val keys: List<PublicKey> = trustedKeys.filter { it.isNotBlank() }.map { encoded ->
        KeyFactory.getInstance(UpdateManifest.KEY_ALGORITHM)
            .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(encoded.trim())))
    }

    override fun isTrusted(manifest: String, signature: String): Boolean {
        val decoded = runCatching { Base64.getDecoder().decode(signature.trim()) }.getOrNull() ?: return false
        return keys.any { key ->
            runCatching {
                Signature.getInstance(UpdateManifest.SIGNATURE_ALGORITHM).run {
                    initVerify(key)
                    update(manifest.encodeToByteArray())
                    verify(decoded)
                }
            }.getOrDefault(false)
        }
    }
}
