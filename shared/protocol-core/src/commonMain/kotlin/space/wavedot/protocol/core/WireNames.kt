package space.wavedot.protocol.core

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive

inline fun <reified T> T.wireName(): String = ProtocolJson.encodeToJsonElement(this).jsonPrimitive.content

inline fun <reified T> fromWireName(name: String): T? =
    runCatching { ProtocolJson.decodeFromJsonElement<T>(JsonPrimitive(name)) }.getOrNull()
