plugins {
    id("wavedot.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.client.core.domain)
            api(kotlin("test"))
            api(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(libs.kotlin.test.junit5)
        }
        androidMain.dependencies {
            implementation(libs.kotlin.test.junit)
        }
    }
}
