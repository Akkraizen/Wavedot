plugins {
    id("wavedot.kmp.library")
}

val generateBuildInfo = tasks.register("generateBuildInfo") {
    val version = project.version.toString()
    val updateSigningKey = providers.gradleProperty("wavedot.updateSigningKey").orElse("")
    val updatesUrl = providers.gradleProperty("wavedot.updatesUrl").orElse("")
    val output = layout.buildDirectory.dir("generated/buildInfo/commonMain/kotlin")
    inputs.property("version", version)
    inputs.property("updateSigningKey", updateSigningKey)
    inputs.property("updatesUrl", updatesUrl)
    outputs.dir(output)
    doLast {
        fun literal(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""
        val file = output.get().file("space/wavedot/app/shell/BuildInfo.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package space.wavedot.app.shell
            |
            |object BuildInfo {
            |    const val VERSION: String = ${literal(version)}
            |    const val UPDATES_URL: String = ${literal(updatesUrl.get())}
            |    const val UPDATE_SIGNING_KEYS: String = ${literal(updateSigningKey.get())}
            |}
            |
            """.trimMargin(),
        )
    }
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(generateBuildInfo)
        }
        commonMain.dependencies {
            api(projects.client.core.data)
            api(projects.client.infra.database)
            implementation(projects.client.infra.hubClient)
            api(projects.client.infra.updater)
            api(projects.client.sources.local)
            implementation(projects.client.plugins.yandexMusic)
            implementation(projects.client.plugins.lrclib)
            implementation(projects.client.plugins.audius)
            implementation(projects.client.plugins.radioBrowser)
            implementation(projects.client.plugins.subsonic)
            api(projects.client.presentation.player)
            api(projects.client.presentation.library)
            api(projects.client.presentation.search)
            api(projects.client.presentation.settings)
            api(projects.client.presentation.extensions)
            api(libs.koin.core)
            api(libs.koin.core.viewmodel)
        }
        jvmTest.dependencies {
            implementation(projects.client.core.testing)
            implementation(libs.koin.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
