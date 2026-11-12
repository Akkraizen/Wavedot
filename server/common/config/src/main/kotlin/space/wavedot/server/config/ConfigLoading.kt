package space.wavedot.server.config

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addFileSource
import com.sksamuel.hoplite.addResourceSource

data class ServerConfig(
    val host: String = "0.0.0.0",
    val port: Int = 8080,
    val trustForwardedHeaders: Boolean = false,
)

data class DatabaseConfig(
    val url: String,
    val user: String = "",
    val password: String = "",
    val maxPoolSize: Int = 10,
)

@OptIn(ExperimentalHoplite::class)
inline fun <reified T : Any> loadServiceConfig(resource: String, overrideFile: String? = System.getenv("WAVEDOT_CONFIG")): T =
    ConfigLoaderBuilder.default()
        .withExplicitSealedTypes()
        .apply { if (overrideFile != null) addFileSource(overrideFile, optional = true) }
        .addResourceSource(resource)
        .build()
        .loadConfigOrThrow<T>()
