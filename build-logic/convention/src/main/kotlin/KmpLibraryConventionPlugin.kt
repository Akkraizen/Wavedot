import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

class KmpLibraryConventionPlugin : Plugin<Project> {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    override fun apply(target: Project) = with(target) {
        val withAndroid = !isServerOnlyBuild
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        if (withAndroid) pluginManager.apply("com.android.kotlin.multiplatform.library")

        extensions.configure<KotlinMultiplatformExtension> {
            jvmToolchain(jvmToolchainVersion)
            jvm()

            if (withAndroid) {
                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryExtension> {
                    namespace = androidNamespace
                    compileSdk = libs.version("android-compileSdk").toInt()
                    minSdk = libs.version("android-minSdk").toInt()
                    withHostTest { }
                }
            }

            applyDefaultHierarchyTemplate {
                common {
                    group("jvmShared") {
                        withCompilations {
                            it.platformType == KotlinPlatformType.jvm || it.platformType == KotlinPlatformType.androidJvm
                        }
                    }
                }
            }

            compilerOptions {
                freeCompilerArgs.addAll("-Xexpect-actual-classes", "-Xmodule-name=$uniqueModuleName")
            }

            sourceSets.getByName("commonMain").dependencies {
                implementation(libs.library("kotlinx-coroutines-core"))
            }
            sourceSets.getByName("commonTest").dependencies {
                implementation(kotlin("test"))
                implementation(libs.library("kotlinx-coroutines-test"))
                implementation(libs.library("turbine"))
            }
        }

        tasks.withType<Test>().configureEach {
            if (name.startsWith("jvm")) useJUnitPlatform()
            jvmArgs("--enable-native-access=ALL-UNNAMED")
        }
    }
}
