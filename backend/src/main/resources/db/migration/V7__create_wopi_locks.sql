CREATE TABLE wopi_locks (
    document_id BIGINT PRIMARY KEY REFERENCES documents(id) ON DELETE CASCADE,
    lock_value VARCHAR(1024) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_wopi_locks_expires_at ON wopi_locks(expires_at);
