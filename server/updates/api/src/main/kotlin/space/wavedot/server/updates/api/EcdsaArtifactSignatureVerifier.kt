package space.wavedot.server.updates.api

import space.wavedot.protocol.updates.UpdateManifest
import space.wavedot.server.updates.domain.Artifact
import space.wavedot.server.updates.domain.ArtifactSignatureVerifier
import space.wavedot.server.updates.domain.SemVer
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class EcdsaArtifactSignatureVerifier(publicKey: String) : ArtifactSignatureVerifier {
    private val key: PublicKey = KeyFactory.getInstance(UpdateManifest.KEY_ALGORITHM)
        .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKey.trim())))

    override fun isValid(version: SemVer, artifact: Artifact, signature: String): Boolean = runCatching {
        val manifest = UpdateManifest.canonical(
            version = version.toString(),
            platform = artifact.platform.toDto(),
            arch = artifact.arch.toDto(),
            fileName = artifact.fileName,
            size = artifact.size,
            sha256 = artifact.sha256,
        )
        Signature.getInstance(UpdateManifest.SIGNATURE_ALGORITHM).run {
            initVerify(key)
            update(manifest.toByteArray())
            verify(Base64.getDecoder().decode(signature.trim()))
        }
    }.getOrDefault(false)
}
