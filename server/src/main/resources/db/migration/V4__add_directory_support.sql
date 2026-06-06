ALTER TABLE file_metadata ADD COLUMN is_directory BOOLEAN DEFAULT FALSE;
ALTER TABLE file_metadata ADD COLUMN parent_id UUID;
CREATE INDEX idx_file_metadata_parent_id ON file_metadata(parent_id);