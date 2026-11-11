enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "wavedot"

include(
    ":shared:protocol-core",
    ":shared:protocol-hub",
    ":shared:protocol-updates",
)

val serverOnly = providers.gradleProperty("wavedot.serverOnly").orNull == "true"

if (!serverOnly) include(
    ":client:core:model",
    ":client:core:common",
    ":client:core:contract",
    ":client:core:plugin-api",
    ":client:core:playback-api",
    ":client:core:domain",
    ":client:core:data",
    ":client:core:testing",
)

if (!serverOnly) include(
    ":client:infra:database",
    ":client:infra:network",
    ":client:infra:storage",
    ":client:infra:stream-proxy",
    ":client:infra:playback-android",
    ":client:infra:playback-desktop",
    ":client:infra:hub-client",
    ":client:infra:updater",
)

if (!serverOnly) include(
    ":client:sources:local",
    ":client:plugins:yandex-music",
    ":client:plugins:lrclib",
    ":client:plugins:audius",
    ":client:plugins:radio-browser",
    ":client:plugins:subsonic",
)

if (!serverOnly) include(
    ":client:presentation:player",
    ":client:presentation:library",
    ":client:presentation:search",
    ":client:presentation:settings",
    ":client:ui",
    ":client:app:shell",
    ":client:app:android",
    ":client:app:desktop",
)

include(
    ":server:common:kernel",
    ":server:common:config",
    ":server:common:ktor",
    ":server:common:persistence",
    ":server:common:testing",
    ":server:hub:domain",
    ":server:hub:data",
    ":server:hub:api",
    ":server:hub:app",
    ":server:updates:domain",
    ":server:updates:data",
    ":server:updates:api",
    ":server:updates:app",
)
