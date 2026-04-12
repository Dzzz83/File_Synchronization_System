package com.filesync.server.repository;

import com.filesync.server.domain.FileMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.util.List;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadataEntity, String>
{
    List<FileMetadataEntity> findByOwnerId(String ownerId);

}
