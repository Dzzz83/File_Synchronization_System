package com.filesync.server.repository;

import com.filesync.server.domain.FileMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadataEntity, String>
{
    List<FileMetadataEntity> findByOwnerId(String ownerId);
    List<FileMetadataEntity> findByFolderId(UUID folderId);
    List<FileMetadataEntity> findByOwnerIdAndFolderIdIsNull(String ownerId);
    List<FileMetadataEntity> findByOwnerIdAndParentIdIsNull(String ownerId);
    List<FileMetadataEntity> findByFolderIdAndParentIdIsNull(UUID folderId);
    List<FileMetadataEntity> findByParentId(UUID parentId);
    Optional<FileMetadataEntity> findByIdAndIsDirectoryTrue(String id);
}
