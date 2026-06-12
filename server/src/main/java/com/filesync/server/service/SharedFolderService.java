package com.filesync.server.service;

import com.filesync.common.enums.Permission;
import com.filesync.server.domain.FileMetadataEntity;
import com.filesync.server.domain.SharedFolderEntity;
import com.filesync.server.domain.SharedFolderMemberEntity;
import com.filesync.server.domain.SharedFolderRequestEntity;
import com.filesync.server.repository.SharedFolderMemberRepository;
import com.filesync.server.repository.SharedFolderRepository;
import com.filesync.server.repository.SharedFolderRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.filesync.server.repository.FileMetadataRepository;
import com.filesync.server.storage.FileStorage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SharedFolderService {
    private final SharedFolderRepository folderRepository;
    private final SharedFolderMemberRepository memberRepository;
    private final SharedFolderRequestRepository requestRepository;
    private final FileMetadataRepository fileMetadataRepository;
    private final FileStorage fileStorage;

    public SharedFolderService(SharedFolderRepository folderRepository,
                               SharedFolderMemberRepository memberRepository,
                               SharedFolderRequestRepository requestRepository,
                               FileMetadataRepository fileMetadataRepository,
                               FileStorage fileStorage) {
        this.folderRepository = folderRepository;
        this.memberRepository = memberRepository;
        this.requestRepository = requestRepository;
        this.fileMetadataRepository = fileMetadataRepository;
        this.fileStorage = fileStorage;
    }

    public SharedFolderEntity createFolder(String name, String ownerId) {
        // Check if folder with same name already exists for this owner
        if (folderRepository.existsByOwnerIdAndName(ownerId, name)) {
            throw new RuntimeException("You already have a shared folder with this name.");
        }
        SharedFolderEntity folder = new SharedFolderEntity();
        folder.setName(name);
        folder.setOwnerId(ownerId);
        return folderRepository.save(folder);
    }

    public void addMember(UUID folderId, String userId, Permission permission) {
        System.out.println("SERVER: Adding member " + userId + " to folder " + folderId + " with permission " + permission);
        Optional<SharedFolderMemberEntity> existing = memberRepository.findByFolderIdAndUserId(folderId, userId);
        if (existing.isPresent()) {
            SharedFolderMemberEntity member = existing.get();
            member.setPermission(permission);
            memberRepository.save(member);
            System.out.println("SERVER: Updated existing member to " + permission);
        } else {
            SharedFolderMemberEntity member = new SharedFolderMemberEntity();
            member.setFolderId(folderId);
            member.setUserId(userId);
            member.setPermission(permission);
            memberRepository.save(member);
            System.out.println("SERVER: Added new member with " + permission);
        }
    }

    public boolean isOwner(UUID folderId, String userId) {
        return folderRepository.findById(folderId)
                .map(f -> f.getOwnerId().equals(userId))
                .orElse(false);
    }

    public List<SharedFolderEntity> getFoldersForUser(String userId) {
        // Find all folder IDs where user is a member
        List<UUID> folderIds = memberRepository.findByUserId(userId).stream()
                .map(SharedFolderMemberEntity::getFolderId)
                .collect(Collectors.toList());
        return folderRepository.findByIdIn(folderIds);
    }

    @Transactional
    public void createRequest(UUID folderId, String requesterId, Permission requestedPermission) {
        if (requestRepository.findByFolderIdAndRequesterId(folderId, requesterId).isPresent()) {
            throw new RuntimeException("Request already pending");
        }
        SharedFolderRequestEntity request = new SharedFolderRequestEntity();
        request.setFolderId(folderId);
        request.setRequesterId(requesterId);
        request.setRequestedPermission(requestedPermission);
        requestRepository.save(request);
    }

    @Transactional
    public void approveRequest(UUID requestId, String ownerId) {
        System.out.println("DEBUG: approveRequest called for requestId=" + requestId + ", ownerId=" + ownerId);

        SharedFolderRequestEntity request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        System.out.println("DEBUG: Found request - requesterId=" + request.getRequesterId()
                + ", requestedPermission=" + request.getRequestedPermission());

        SharedFolderEntity folder = folderRepository.findById(request.getFolderId())
                .orElseThrow(() -> new RuntimeException("Folder not found"));

        if (!folder.getOwnerId().equals(ownerId)) {
            throw new RuntimeException("Only folder owner can approve");
        }

        System.out.println("DEBUG: Adding member with permission=" + request.getRequestedPermission());
        addMember(folder.getId(), request.getRequesterId(), request.getRequestedPermission());

        request.setStatus("APPROVED");
        requestRepository.save(request);

        System.out.println("DEBUG: Request approved and member added.");
    }

    @Transactional
    public void deleteFolder(UUID folderId) {
        // Delete all files in this folder (metadata and actual storage)
        List<FileMetadataEntity> files = fileMetadataRepository.findByFolderId(folderId);
        for (FileMetadataEntity file : files) {
            fileStorage.delete(file.getId()); // delete from R2/local disk
            fileMetadataRepository.delete(file);
        }
        // Delete members
        memberRepository.deleteByFolderId(folderId);
        // Delete pending requests
        requestRepository.deleteByFolderId(folderId);
        // Delete folder itself
        folderRepository.deleteById(folderId);
    }

    public List<SharedFolderEntity> findByNameContainingIgnoreCase(String name) {
        return folderRepository.findByNameContainingIgnoreCase(name);
    }
}