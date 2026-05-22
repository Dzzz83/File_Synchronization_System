package com.filesync.server.controller;

import com.filesync.common.dto.CreateFolderDto;
import com.filesync.common.dto.MemberDto;
import com.filesync.common.dto.SharedFolderDto;
import com.filesync.common.enums.Permission;
import com.filesync.server.domain.SharedFolderEntity;
import com.filesync.server.domain.SharedFolderMemberEntity;
import com.filesync.server.repository.SharedFolderMemberRepository;
import com.filesync.server.service.SharedFolderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/shared-folders")
public class SharedFolderController
{
    private final SharedFolderService folderService;
    private final SharedFolderMemberRepository memberRepository;

    public SharedFolderController(SharedFolderService folderService,
                                  SharedFolderMemberRepository memberRepository) {
        this.folderService = folderService;
        this.memberRepository = memberRepository;
    }

    @PostMapping
    public ResponseEntity<?> createFolder(@RequestBody CreateFolderDto createFolderDto, Authentication authentication)
    {
        // verify user
        String owner = authentication.getName();
        // create a folder
        SharedFolderEntity folder = folderService.createFolder(createFolderDto.getName(), owner);
        // add owner with WRITE permission
        folderService.addMember(folder.getId(), owner, Permission.WRITE);
        // add initial members
        if (createFolderDto.getMembers() != null)
        {
            for (MemberDto memberDto : createFolderDto.getMembers())
            {
                folderService.addMember(folder.getId(), memberDto.getUserId(), memberDto.getPermission());
            }
        }
        return ResponseEntity.ok(folder);
    }

    @PostMapping("/{folderId}/members")
    public ResponseEntity<?> addMembers(@PathVariable("folderId") UUID folderId, @RequestBody MemberDto memberDto,
                                        Authentication authentication)
    {
        // verify if the user is the owner
        if (!folderService.isOwner(folderId, authentication.getName()))
        {
            return ResponseEntity.status(403).build();
        }
        // add the members with permission
        folderService.addMember(folderId, memberDto.getUserId(), memberDto.getPermission());
        return ResponseEntity.ok().build();
    }

    private Permission getUserPermission(UUID folderId, String userId) {
        Optional<SharedFolderMemberEntity> memberEntity = memberRepository.findByFolderIdAndUserId(folderId, userId);
        if (memberEntity.isPresent())
        {
            return memberEntity.get().getPermission();
        }
        else
        {
            return null;
        }
    }

    @GetMapping("/user/{userId}")
    public List<SharedFolderDto> getUserFolders(@PathVariable("userId") String userId, Authentication authentication) {
        if (!userId.equals(authentication.getName())) {
            throw new RuntimeException("Not authorized");
        }

        // get all shared folder for a user
        List<SharedFolderEntity> folders = folderService.getFoldersForUser(userId);
        List<SharedFolderDto> result = new ArrayList<>();

        // convert SharedFolderEntity to SharedFolderDto
        for (SharedFolderEntity folder : folders) {
            SharedFolderDto dto = convertToDto(folder, userId);
            result.add(dto);
        }

        return result;
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
