import org.gradle.api.Plugin
import org.gradle.api.Project

class ServerServiceConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("wavedot.jvm.library")
        pluginManager.apply("application")
    }
}
