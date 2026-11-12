package space.wavedot.app.shell

import org.koin.core.module.Module
import org.koin.dsl.module
import space.wavedot.infra.updater.EcdsaUpdateSignatureVerifier
import space.wavedot.infra.updater.UpdateSignatureVerifier
import java.io.File
import java.util.UUID

val jvmPlatformModule: Module = module {
    single<UpdateSignatureVerifier> {
        EcdsaUpdateSignatureVerifier(BuildInfo.UPDATE_SIGNING_KEYS.split(',').map { it.trim() })
    }
}

fun loadInstallId(directory: File): String {
    val file = File(directory, "install-id")
    return file.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
        ?: UUID.randomUUID().toString().also {
            directory.mkdirs()
            file.writeText(it)
        }
}
