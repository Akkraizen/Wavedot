package space.wavedot.plugin.subsonic

internal data class SubsonicConfig(
    val serverUrl: String,
    val username: String,
    val password: String,
) {
    val restUrl: String get() = serverUrl.trim().trimEnd('/') + "/rest"
}
