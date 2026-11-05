import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

internal fun VersionCatalog.library(alias: String) = findLibrary(alias).get()

internal val Project.uniqueModuleName: String
    get() = "wavedot" + path.replace(':', '-')

internal val Project.androidNamespace: String
    get() = "space.wavedot" + path.replace(':', '.').replace('-', '_')

internal val Project.jvmToolchainVersion: Int
    get() = libs.version("jvm-toolchain").toInt()

internal val Project.isServerOnlyBuild: Boolean
    get() = providers.gradleProperty("wavedot.serverOnly").orNull == "true"
