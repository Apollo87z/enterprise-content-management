ALTER TABLE documents
    ADD COLUMN storage_key VARCHAR(255),
    ADD COLUMN file_size BIGINT;

ALTER TABLE documents
    ADD CONSTRAINT chk_document_file_size
    CHECK (file_size IS NULL OR file_size >= 0);

CREATE UNIQUE INDEX idx_documents_storage_key
    ON documents (storage_key);
