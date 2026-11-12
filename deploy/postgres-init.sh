#!/bin/sh
set -eu

create_service_database() {
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
        -v role="$1" -v password="$2" -v database="$3" <<'SQL'
CREATE ROLE :"role" LOGIN PASSWORD :'password';
CREATE DATABASE :"database" OWNER :"role";
REVOKE ALL ON DATABASE :"database" FROM PUBLIC;
SQL
}

create_service_database wavedot_hub "$HUB_DATABASE_PASSWORD" wavedot_hub
create_service_database wavedot_updates "$UPDATES_DATABASE_PASSWORD" wavedot_updates
