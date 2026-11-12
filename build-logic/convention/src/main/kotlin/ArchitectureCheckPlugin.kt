import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

class ArchitectureCheckPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val edges = provider {
            subprojects.flatMap { module ->
                module.configurations
                    .filter { isProductionConfiguration(it.name) }
                    .flatMap { configuration ->
                        configuration.dependencies.withType(ProjectDependency::class.java).map { "${module.path}$SEPARATOR${it.path}" }
                    }
            }.distinct().sorted()
        }
        tasks.register("checkArchitecture", ArchitectureCheckTask::class.java) {
            group = "verification"
            description = "Fails when a module depends on a layer it must not see."
            this.edges.set(edges)
        }
        Unit
    }

    private fun isProductionConfiguration(name: String): Boolean {
        if (name.contains("test", ignoreCase = true)) return false
        return name in PLAIN_CONFIGURATIONS || name.endsWith("MainApi") || name.endsWith("MainImplementation")
    }

    private companion object {
        val PLAIN_CONFIGURATIONS = setOf("api", "implementation", "compileOnly", "runtimeOnly")
    }
}

internal const val SEPARATOR = " -> "

internal class LayerRule(module: String, allowed: List<String>? = null, denied: List<String> = emptyList()) {
    private val module = Regex(module)
    private val allowed = allowed?.map(::Regex)
    private val denied = denied.map(::Regex)

    fun appliesTo(path: String): Boolean = module.matches(path)

    fun permits(dependency: String): Boolean =
        denied.none { it.matches(dependency) } && (allowed == null || allowed.any { it.matches(dependency) })
}

internal val LAYER_RULES = listOf(
    LayerRule(":shared:.*", allowed = listOf(":shared:.*")),
    LayerRule(":client:core:(model|common)", allowed = emptyList()),
    LayerRule(":client:core:contract", allowed = listOf(":client:core:(model|common)")),
    LayerRule(":client:core:playback-api", allowed = listOf(":client:core:model")),
    LayerRule(":client:core:plugin-api", allowed = listOf(":client:core:contract")),
    LayerRule(":client:core:domain", allowed = listOf(":client:core:(contract|playback-api)")),
    LayerRule(":client:core:data", allowed = listOf(":client:core:(domain|plugin-api)")),
    LayerRule(":client:plugins:.*", allowed = listOf(":client:core:plugin-api")),
    LayerRule(":client:sources:.*", allowed = listOf(":client:core:contract")),
    LayerRule(":client:infra:.*", allowed = listOf(":client:core:.*", ":shared:.*")),
    LayerRule(":client:presentation:.*", allowed = listOf(":client:core:domain")),
    LayerRule(":client:ui", allowed = listOf(":client:core:model", ":client:presentation:.*")),
    LayerRule(":client:app:shell", denied = listOf(":client:ui", ":client:app:.*")),
    LayerRule(":server:common:kernel", allowed = emptyList()),
    LayerRule(":server:common:.*", allowed = listOf(":server:common:.*", ":shared:protocol-core")),
    LayerRule(":server:hub:domain", allowed = listOf(":server:common:kernel")),
    LayerRule(":server:updates:domain", allowed = listOf(":server:common:kernel")),
    LayerRule(":server:hub:data", allowed = listOf(":server:hub:domain", ":server:common:persistence")),
    LayerRule(":server:updates:data", allowed = listOf(":server:updates:domain", ":server:common:persistence")),
    LayerRule(":server:hub:api", allowed = listOf(":server:hub:domain", ":server:common:ktor", ":shared:.*")),
    LayerRule(":server:updates:api", allowed = listOf(":server:updates:domain", ":server:common:ktor", ":shared:.*")),
)

abstract class ArchitectureCheckTask : DefaultTask() {
    @get:Input
    abstract val edges: ListProperty<String>

    @TaskAction
    fun check() {
        val violations = edges.get().filter { edge ->
            val (module, dependency) = edge.split(SEPARATOR)
            val rule = LAYER_RULES.firstOrNull { it.appliesTo(module) }
            rule != null && !rule.permits(dependency)
        }
        if (violations.isNotEmpty()) {
            throw GradleException("Layer boundaries are broken (see docs/ARCHITECTURE.md):\n" + violations.joinToString("\n") { "  $it" })
        }
        logger.lifecycle("Checked ${edges.get().size} module dependencies against the layer rules.")
    }
}
