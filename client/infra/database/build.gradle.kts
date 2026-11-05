plugins {
    id("wavedot.kmp.library")
    alias(libs.plugins.sqldelight)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.client.core.domain)
            api(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        jvmMain.dependencies {
            api(libs.sqldelight.sqlite.driver)
        }
        androidMain.dependencies {
            api(libs.sqldelight.android.driver)
        }
    }
}

sqldelight {
    databases {
        create("WavedotDatabase") {
            packageName.set("space.wavedot.infra.database")
        }
    }
}

dependencies {
    "jvmTestImplementation"(projects.client.core.testing)
}
