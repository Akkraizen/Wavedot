package space.wavedot.infra.updater

fun interface UpdateSignatureVerifier {
    fun isTrusted(manifest: String, signature: String): Boolean
}
