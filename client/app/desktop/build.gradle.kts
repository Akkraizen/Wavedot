import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.material3.ExperimentalMaterial3Api",
        )
    }
}

dependencies {
    implementation(projects.client.app.shell)
    implementation(projects.client.ui)
    implementation(libs.compose.material3)
    implementation(projects.client.infra.network)
    implementation(projects.client.infra.storage)
    implementation(projects.client.infra.streamProxy)
    implementation(projects.client.infra.playbackDesktop)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "space.wavedot.app.desktop.MainKt"
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED")
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "Wavedot"
            packageVersion = "0.1.0"
            vendor = "Wavedot"
            description = "Modular music player"
            windows {
                menuGroup = "Wavedot"
                upgradeUuid = "0f6f2a8e-4c39-4b52-9d6a-5d5c1b7e9a10"
                iconFile.set(project.file("icons/wavedot.ico"))
            }
            linux {
                iconFile.set(project.file("icons/wavedot.png"))
            }
            macOS {
                iconFile.set(project.file("icons/wavedot.icns"))
            }
        }
    }
}
