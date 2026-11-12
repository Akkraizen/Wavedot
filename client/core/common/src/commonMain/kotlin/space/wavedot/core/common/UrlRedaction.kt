package space.wavedot.core.common

fun String.withoutUrlSecrets(): String {
    val base = substringBefore('?').substringBefore('#')
    val schemeEnd = base.indexOf("://")
    if (schemeEnd < 0) return base
    val authorityStart = schemeEnd + 3
    val authorityEnd = base.indexOf('/', authorityStart).let { if (it < 0) base.length else it }
    val userInfoEnd = base.lastIndexOf('@', authorityEnd - 1)
    return if (userInfoEnd >= authorityStart) base.removeRange(authorityStart, userInfoEnd + 1) else base
}
