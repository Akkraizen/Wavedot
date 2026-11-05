package space.wavedot.infra.database

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

fun createAndroidDatabase(context: Context, name: String = "wavedot.db"): WavedotDatabase =
    WavedotDatabase(AndroidSqliteDriver(WavedotDatabase.Schema, context, name))
