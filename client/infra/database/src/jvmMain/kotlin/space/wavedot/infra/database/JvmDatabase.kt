package space.wavedot.infra.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

fun createJvmDatabase(file: File?): WavedotDatabase {
    file?.parentFile?.mkdirs()
    val url = if (file == null) JdbcSqliteDriver.IN_MEMORY else "jdbc:sqlite:${file.absolutePath}"
    val driver = JdbcSqliteDriver(url, Properties(), WavedotDatabase.Schema)
    return WavedotDatabase(driver)
}
