package space.wavedot.server.hub.data

import org.jetbrains.exposed.v1.core.Table

internal object AccountsTable : Table("accounts") {
    val id = varchar("id", 64)
    val createdAt = long("created_at")
    val revision = long("revision")
    override val primaryKey = PrimaryKey(id)
}

internal object DevicesTable : Table("devices") {
    val id = varchar("id", 64)
    val accountId = varchar("account_id", 64)
    val name = varchar("name", 120)
    val platform = varchar("platform", 32)
    val tokenHash = varchar("token_hash", 128)
    val createdAt = long("created_at")
    val lastSeenAt = long("last_seen_at")
    override val primaryKey = PrimaryKey(id)
}

internal object LibraryItemsTable : Table("library_items") {
    val accountId = varchar("account_id", 64)
    val collection = varchar("collection", 64)
    val itemKey = varchar("item_key", 512)
    val revision = long("revision")
    val deviceId = varchar("device_id", 64)
    val deleted = bool("deleted")
    val payload = text("payload").nullable()
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(accountId, collection, itemKey)
}
