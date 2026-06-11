-- Add folder_id to file_metadata (nullable for backward compatibility)
ALTER TABLE file_metadata ADD COLUMN folder_id UUID;
CREATE INDEX idx_file_metadata_folder_id ON file_metadata(folder_id);

-- Create shared_folders table
CREATE TABLE shared_folders (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    owner_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- Create shared_folder_members table
CREATE TABLE shared_folder_members (
    id BIGSERIAL PRIMARY KEY,
    folder_id UUID NOT NULL REFERENCES shared_folders(id) ON DELETE CASCADE,
    user_id VARCHAR(100) NOT NULL,
    permission VARCHAR(10) NOT NULL,
    UNIQUE(folder_id, user_id)
);

-- Create shared_folder_requests table
CREATE TABLE shared_folder_requests (
    id UUID PRIMARY KEY,
    folder_id UUID NOT NULL REFERENCES shared_folders(id) ON DELETE CASCADE,
    requester_id VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_at TIMESTAMP NOT NULL
);