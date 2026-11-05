package space.wavedot.core.common

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

interface Logger {
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null)
}

fun Logger.debug(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
fun Logger.info(tag: String, message: String) = log(LogLevel.INFO, tag, message)
fun Logger.warn(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.WARN, tag, message, throwable)
fun Logger.error(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.ERROR, tag, message, throwable)

object NoopLogger : Logger {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) = Unit
}

class ConsoleLogger(private val minLevel: LogLevel = LogLevel.INFO) : Logger {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (level < minLevel) return
        println("${level.name.first()}/$tag: $message")
        throwable?.let { println(it.stackTraceToString()) }
    }
}
