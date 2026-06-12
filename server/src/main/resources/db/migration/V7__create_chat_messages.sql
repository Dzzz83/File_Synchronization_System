CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGSERIAL PRIMARY KEY,
    folder_id UUID NOT NULL,
    sender VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    timestamp TIMESTAMP NOT NULL
);

CREATE INDEX idx_chat_messages_folder_id ON chat_messages(folder_id);
CREATE INDEX idx_chat_messages_timestamp ON chat_messages(timestamp);