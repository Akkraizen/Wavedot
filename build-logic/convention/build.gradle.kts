plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "wavedot.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpCompose") {
            id = "wavedot.kmp.compose"
            implementationClass = "KmpComposeConventionPlugin"
        }
        register("jvmLibrary") {
            id = "wavedot.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = "wavedot.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "wavedot.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("architectureCheck") {
            id = "wavedot.architecture"
            implementationClass = "ArchitectureCheckPlugin"
        }
        register("serverService") {
            id = "wavedot.server.service"
            implementationClass = "ServerServiceConventionPlugin"
        }
    }
}
