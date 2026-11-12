package space.wavedot.core.common

sealed interface WavedotError {
    val message: String

    data class NotFound(val what: String) : WavedotError {
        override val message: String get() = "Not found: $what"
    }

    data class Unauthorized(val source: String) : WavedotError {
        override val message: String get() = "Authorization required for $source"
    }

    data class Unsupported(val operation: String) : WavedotError {
        override val message: String get() = "Unsupported operation: $operation"
    }

    data class Network(override val message: String) : WavedotError

    data class Invalid(override val message: String) : WavedotError

    data class Unknown(override val message: String) : WavedotError
}

class WavedotException(
    val error: WavedotError,
    cause: Throwable? = null,
) : Exception(error.message, cause)

fun Throwable.toWavedotError(): WavedotError = when (this) {
    is WavedotException -> error
    else -> WavedotError.Unknown(message ?: this::class.simpleName.orEmpty())
}
