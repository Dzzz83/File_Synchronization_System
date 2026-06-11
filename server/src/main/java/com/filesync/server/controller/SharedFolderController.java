package com.filesync.server.controller;

import com.filesync.common.dto.CreateFolderDto;
import com.filesync.common.dto.MemberDto;
import com.filesync.common.dto.SharedFolderDto;
import com.filesync.common.enums.Permission;
import com.filesync.server.domain.SharedFolderEntity;
import com.filesync.server.domain.SharedFolderMemberEntity;
import com.filesync.server.domain.SharedFolderRequestEntity;
import com.filesync.server.repository.SharedFolderMemberRepository;
import com.filesync.server.repository.SharedFolderRequestRepository;
import com.filesync.server.service.SharedFolderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/shared-folders")
public class SharedFolderController
{
    private final SharedFolderService folderService;
    private final SharedFolderMemberRepository memberRepository;
    private final SharedFolderRequestRepository requestRepository;

    public SharedFolderController(SharedFolderService folderService,
                                  SharedFolderRequestRepository sharedFolderRequestRepository,
                                  SharedFolderMemberRepository memberRepository) {
        this.folderService = folderService;
        this.memberRepository = memberRepository;
        this.requestRepository = sharedFolderRequestRepository;
    }

    @PostMapping
    public ResponseEntity<?> createFolder(@RequestBody CreateFolderDto createFolderDto, Authentication authentication)
    {
        String owner = authentication.getName();
        SharedFolderEntity folder = folderService.createFolder(createFolderDto.getName(), owner);
        folderService.addMember(folder.getId(), owner, Permission.WRITE);
        if (createFolderDto.getMembers() != null) {
            for (MemberDto memberDto : createFolderDto.getMembers()) {
                folderService.addMember(folder.getId(), memberDto.getUserId(), memberDto.getPermission());
            }
        }
        return ResponseEntity.ok(folder);
    }

    @PostMapping("/{folderId}/members")
    public ResponseEntity<?> addMembers(@PathVariable("folderId") UUID folderId,
                                        @RequestBody MemberDto memberDto,
                                        Authentication authentication) {
        if (!folderService.isOwner(folderId, authentication.getName())) {
            return ResponseEntity.status(403).build();
        }
        try {
            folderService.addMember(folderId, memberDto.getUserId(), memberDto.getPermission());
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{folderId}/requests/pending/count")
    public ResponseEntity<Integer> getPendingRequestsCount(@PathVariable("folderId") UUID folderId,
                                                           Authentication authentication) {
        String userId = authentication.getName();
        if (!folderService.isOwner(folderId, userId)) {
            return ResponseEntity.status(403).build();
        }
        int count = requestRepository.countByFolderIdAndStatus(folderId, "PENDING");
        return ResponseEntity.ok(count);
    }

    private Permission getUserPermission(UUID folderId, String userId) {
        Optional<SharedFolderMemberEntity> memberEntity = memberRepository.findByFolderIdAndUserId(folderId, userId);
        return memberEntity.map(SharedFolderMemberEntity::getPermission).orElse(null);
    }

    @GetMapping("/user/{userId}")
    public List<SharedFolderDto> getUserFolders(@PathVariable("userId") String userId, Authentication authentication) {
        if (!userId.equals(authentication.getName())) {
            throw new RuntimeException("Not authorized");
        }
        List<SharedFolderEntity> folders = folderService.getFoldersForUser(userId);
        List<SharedFolderDto> result = new ArrayList<>();
        for (SharedFolderEntity folder : folders) {
            SharedFolderDto dto = convertToDto(folder, userId);
            result.add(dto);
        }
        return result;
    }

    @PostMapping("/requests/{requestId}/approve")
    public ResponseEntity<?> approveRequest(@PathVariable("requestId") UUID requestId, Authentication authentication) {
        String userId = authentication.getName();
        folderService.approveRequest(requestId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{folderId}/request-access")
    public ResponseEntity<?> requestAccess(@PathVariable("folderId") UUID folderId, Authentication authentication) {
        String userId = authentication.getName();
        folderService.createRequest(folderId, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{folderId}/requests/pending")
    public ResponseEntity<?> getPendingRequests(@PathVariable("folderId") UUID folderId,
                                                Authentication authentication) {
        String userId = authentication.getName();
        if (!folderService.isOwner(folderId, userId)) {
            return ResponseEntity.status(403).body("Only owner can view pending requests");
        }
        List<SharedFolderRequestEntity> requests = requestRepository.findByFolderIdAndStatus(folderId, "PENDING");
        List<Map<String, String>> result = requests.stream()
                .map(req -> Map.of(
                        "requestId", req.getId().toString(),
                        "requesterId", req.getRequesterId()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchFoldersByName(@RequestParam("name") String name) {
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<SharedFolderEntity> folders = folderService.findByNameContainingIgnoreCase(name.trim());
        List<Map<String, String>> result = folders.stream()
                .map(f -> Map.of(
                        "folderId", f.getId().toString(),
                        "name", f.getName(),
                        "ownerId", f.getOwnerId()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{folderId}")
    public ResponseEntity<?> deleteFolder(@PathVariable("folderId") UUID folderId, Authentication authentication) {
        String userId = authentication.getName();
        if (!folderService.isOwner(folderId, userId)) {
            return ResponseEntity.status(403).body("Only folder owner can delete this folder");
        }
        folderService.deleteFolder(folderId);
        return ResponseEntity.ok().build();
    }

    private SharedFolderDto convertToDto(SharedFolderEntity folder, String userId) {
        SharedFolderDto dto = new SharedFolderDto();
        dto.setId(folder.getId());
        dto.setName(folder.getName());
        dto.setOwnerId(folder.getOwnerId());
        dto.setCreatedAt(folder.getCreatedAt());
        dto.setYourPermission(getUserPermission(folder.getId(), userId));
        return dto;
    }
}