plugins {
    id("wavedot.jvm.library")
}

dependencies {
    api(projects.server.hub.domain)
    api(projects.server.common.persistence)
    testImplementation(projects.server.common.testing)
}
