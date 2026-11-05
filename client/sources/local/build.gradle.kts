plugins {
    id("wavedot.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.client.core.contract)
        }
        commonTest.dependencies {
            implementation(projects.client.core.testing)
        }
        jvmMain.dependencies {
            implementation(libs.jaudiotagger)
        }
    }
}
