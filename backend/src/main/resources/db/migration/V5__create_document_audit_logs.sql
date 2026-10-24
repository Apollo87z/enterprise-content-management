CREATE TABLE document_audit_logs (
                                     id BIGSERIAL PRIMARY KEY,
                                     document_id BIGINT NOT NULL,
                                     action VARCHAR(50) NOT NULL,
                                     actor VARCHAR(255) NOT NULL,
                                     details TEXT,
                                     created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_document_audit_logs_document_id
    ON document_audit_logs (document_id);

CREATE INDEX idx_document_audit_logs_created_at
    ON document_audit_logs (created_at DESC);

CREATE INDEX idx_document_audit_logs_action
    ON document_audit_logs (action);