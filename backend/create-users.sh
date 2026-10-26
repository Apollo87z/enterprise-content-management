#!/bin/bash
set -euo pipefail

VIEWER_PASSWORD='ViewerLocal2026!'
EDITOR_PASSWORD='EditorLocal2026!'

hash_password() {
    docker run --rm httpd:2.4-alpine \
        htpasswd -nbB -C 12 "$1" "$2" | cut -d: -f2-
}

echo "Generating BCrypt password hashes..."

VIEWER_HASH=$(hash_password viewer "$VIEWER_PASSWORD")
EDITOR_HASH=$(hash_password editor "$EDITOR_PASSWORD")

echo "Creating PostgreSQL users..."

docker compose exec -T postgres \
    psql -v ON_ERROR_STOP=1 -U ecm -d ecmdb \
    -v viewer_hash="$VIEWER_HASH" \
    -v editor_hash="$EDITOR_HASH" <<'SQL'

INSERT INTO app_users (username, password_hash, role, enabled)
VALUES
    ('viewer', :'viewer_hash', 'VIEWER', TRUE),
    ('editor', :'editor_hash', 'EDITOR', TRUE)
ON CONFLICT (username)
DO UPDATE SET
    password_hash = EXCLUDED.password_hash,
    role = EXCLUDED.role,
    enabled = TRUE;

SELECT id, username, role, enabled
FROM app_users
ORDER BY id;

SQL

echo
echo "Users created successfully."
echo "Viewer: viewer / $VIEWER_PASSWORD"
echo "Editor: editor / $EDITOR_PASSWORD"
echo "Admin: existing credentials unchanged"
