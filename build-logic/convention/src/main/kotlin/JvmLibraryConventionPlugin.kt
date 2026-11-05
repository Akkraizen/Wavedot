import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")

        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(jvmToolchainVersion)
            compilerOptions {
                moduleName.set(uniqueModuleName)
            }
        }

        dependencies {
            add("implementation", libs.library("kotlinx-coroutines-core"))
            add("testImplementation", "org.jetbrains.kotlin:kotlin-test")
            add("testImplementation", libs.library("kotlinx-coroutines-test"))
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            jvmArgs("--enable-native-access=ALL-UNNAMED")
        }
    }
}
