package com.filesync.server.repository;

import com.filesync.server.domain.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {
    List<ChatMessageEntity> findByFolderIdOrderByTimestampAsc(UUID folderId);
    List<ChatMessageEntity> findTop100ByFolderIdOrderByTimestampDesc(UUID folderId);
}