package space.wavedot.server.kernel

sealed class AppError(message: String) : RuntimeException(message) {
    class NotFound(what: String) : AppError("$what not found")
    class Unauthorized(reason: String = "Authentication required") : AppError(reason)
    class Forbidden(reason: String = "Access denied") : AppError(reason)
    class Validation(reason: String) : AppError(reason)
    class Conflict(reason: String) : AppError(reason)
}

fun validate(condition: Boolean, message: () -> String) {
    if (!condition) throw AppError.Validation(message())
}
