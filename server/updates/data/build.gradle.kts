plugins {
    id("wavedot.jvm.library")
}

dependencies {
    api(projects.server.updates.domain)
    api(projects.server.common.persistence)
    testImplementation(projects.server.common.testing)
}
