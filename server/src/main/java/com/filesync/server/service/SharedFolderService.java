package com.filesync.server.service;

import com.filesync.common.enums.Permission;
import com.filesync.server.domain.SharedFolderEntity;
import com.filesync.server.domain.SharedFolderMemberEntity;
import com.filesync.server.domain.SharedFolderRequestEntity;
import com.filesync.server.repository.SharedFolderMemberRepository;
import com.filesync.server.repository.SharedFolderRepository;
import com.filesync.server.repository.SharedFolderRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SharedFolderService {
    private final SharedFolderRepository folderRepository;
    private final SharedFolderMemberRepository memberRepository;
    private final SharedFolderRequestRepository requestRepository;

    public SharedFolderService(SharedFolderRepository folderRepository,
                               SharedFolderMemberRepository memberRepository,
                               SharedFolderRequestRepository requestRepository) {
        this.folderRepository = folderRepository;
        this.memberRepository = memberRepository;
        this.requestRepository = requestRepository;
    }

    @Transactional
    public SharedFolderEntity createFolder(String name, String ownerId) {
        SharedFolderEntity folder = new SharedFolderEntity();
        folder.setName(name);
        folder.setOwnerId(ownerId);
        return folderRepository.save(folder);
    }

    @Transactional
    public void addMember(UUID folderId, String userId, Permission permission) {
        SharedFolderMemberEntity member = new SharedFolderMemberEntity();
        member.setFolderId(folderId);
        member.setUserId(userId);
        member.setPermission(permission);
        memberRepository.save(member);
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
    public void createRequest(UUID folderId, String requesterId) {
        // Check if request already exists
        if (requestRepository.findByFolderIdAndRequesterId(folderId, requesterId).isPresent()) {
            throw new RuntimeException("Request already pending");
        }
        SharedFolderRequestEntity request = new SharedFolderRequestEntity();
        request.setFolderId(folderId);
        request.setRequesterId(requesterId);
        requestRepository.save(request);
    }

    @Transactional
    public void approveRequest(UUID requestId, String ownerId) {
        SharedFolderRequestEntity request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        SharedFolderEntity folder = folderRepository.findById(request.getFolderId())
                .orElseThrow(() -> new RuntimeException("Folder not found"));
        if (!folder.getOwnerId().equals(ownerId)) {
            throw new RuntimeException("Only folder owner can approve");
        }
        // Add user as READ-only by default (owner can later upgrade)
        addMember(folder.getId(), request.getRequesterId(), Permission.READ);
        request.setStatus("APPROVED");
        requestRepository.save(request);
    }
}