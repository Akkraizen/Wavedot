import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("wavedot.kmp.library")
        pluginManager.apply("org.jetbrains.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.configureEach {
                languageSettings.optIn("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
                languageSettings.optIn("androidx.compose.material3.ExperimentalMaterial3Api")
            }
            sourceSets.getByName("commonMain").dependencies {
                implementation("org.jetbrains.compose.runtime:runtime:${libs.version("compose-multiplatform")}")
                implementation("org.jetbrains.compose.foundation:foundation:${libs.version("compose-multiplatform")}")
                implementation("org.jetbrains.compose.ui:ui:${libs.version("compose-multiplatform")}")
                implementation(libs.library("compose-material3"))
            }
        }
    }
}
