ALTER TABLE documents
    ADD COLUMN description TEXT,
    ADD COLUMN category VARCHAR(100),
    ADD COLUMN tags TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX idx_documents_category
    ON documents (category);

CREATE INDEX idx_documents_tags
    ON documents USING GIN (tags);
