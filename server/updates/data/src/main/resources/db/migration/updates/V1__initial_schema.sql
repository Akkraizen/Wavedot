CREATE TABLE releases (
    id VARCHAR(64) PRIMARY KEY,
    version VARCHAR(64) NOT NULL UNIQUE,
    channel VARCHAR(16) NOT NULL,
    notes TEXT NOT NULL,
    mandatory BOOLEAN NOT NULL,
    rollout_percent INT NOT NULL,
    published_at BIGINT NOT NULL,
    yanked BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE artifacts (
    release_id VARCHAR(64) NOT NULL REFERENCES releases (id) ON DELETE CASCADE,
    platform VARCHAR(16) NOT NULL,
    arch VARCHAR(16) NOT NULL,
    file_name VARCHAR(128) NOT NULL,
    size BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    storage_key VARCHAR(256) NOT NULL,
    PRIMARY KEY (release_id, platform, arch)
);
