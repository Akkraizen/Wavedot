package space.wavedot.protocol.core

import kotlinx.serialization.json.Json

val ProtocolJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}
