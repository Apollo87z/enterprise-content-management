CREATE TABLE document_versions (
    id BIGSERIAL PRIMARY KEY,

    document_id BIGINT NOT NULL,

    version_number INTEGER NOT NULL,

    storage_key VARCHAR(255) NOT NULL,

    file_size BIGINT NOT NULL,

    content_type VARCHAR(255) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_document_versions_document
        FOREIGN KEY (document_id)
        REFERENCES documents(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_document_versions_document_version
        UNIQUE (document_id, version_number),

    CONSTRAINT uq_document_versions_storage_key
        UNIQUE (storage_key),

    CONSTRAINT chk_document_versions_version_number
        CHECK (version_number > 0),

    CONSTRAINT chk_document_versions_file_size
        CHECK (file_size >= 0)
);

CREATE INDEX idx_document_versions_document_id
    ON document_versions(document_id);

-- Register existing uploaded files as version 1.
-- Metadata-only documents will not receive a version yet.
INSERT INTO document_versions (
    document_id,
    version_number,
    storage_key,
    file_size,
    content_type
)
SELECT
    id,
    1,
    storage_key,
    file_size,
    content_type
FROM documents
WHERE storage_key IS NOT NULL
  AND file_size IS NOT NULL;
