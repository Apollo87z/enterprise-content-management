CREATE TABLE app_users (
                           id BIGSERIAL PRIMARY KEY,
                           username VARCHAR(100) NOT NULL UNIQUE,
                           password_hash VARCHAR(255) NOT NULL,
                           role VARCHAR(20) NOT NULL
                               CHECK (role IN ('ADMIN', 'EDITOR', 'VIEWER')),
                           enabled BOOLEAN NOT NULL DEFAULT TRUE,
                           created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_app_users_role
    ON app_users (role);