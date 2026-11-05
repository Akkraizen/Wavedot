CREATE TABLE accounts (
    id VARCHAR(64) PRIMARY KEY,
    created_at BIGINT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE devices (
    id VARCHAR(64) PRIMARY KEY,
    account_id VARCHAR(64) NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    platform VARCHAR(32) NOT NULL,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    created_at BIGINT NOT NULL,
    last_seen_at BIGINT NOT NULL
);

CREATE INDEX devices_account_idx ON devices (account_id);

CREATE TABLE library_items (
    account_id VARCHAR(64) NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    collection VARCHAR(64) NOT NULL,
    item_key VARCHAR(512) NOT NULL,
    revision BIGINT NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    deleted BOOLEAN NOT NULL,
    payload TEXT,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (account_id, collection, item_key)
);

CREATE INDEX library_items_revision_idx ON library_items (account_id, revision);
