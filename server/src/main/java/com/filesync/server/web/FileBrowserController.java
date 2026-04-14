package com.filesync.server.web;

import com.filesync.common.dto.ConflictContextDto;
import com.filesync.server.conflict.strategy.ConflictStrategyFactory;
import com.filesync.server.domain.FileMetadataEntity;
import com.filesync.server.repository.FileMetadataRepository;
import com.filesync.server.service.EditOrchestrator;
import com.filesync.server.storage.FileStorageService;
import com.filesync.server.service.HashCalculator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/files")
public class FileBrowserController {

    private final FileMetadataRepository fileMetadataRepository;
    private final FileStorageService fileStorageService;
    private final HashCalculator hashCalculator;
    private final EditOrchestrator editOrchestrator;
    private final ConflictStrategyFactory strategyFactory;

    public FileBrowserController(FileMetadataRepository fileMetadataRepository,
                                 FileStorageService fileStorageService,
                                 HashCalculator hashCalculator,
                                 EditOrchestrator editOrchestrator,
                                 ConflictStrategyFactory strategyFactory) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.fileStorageService = fileStorageService;
        this.hashCalculator = hashCalculator;
        this.editOrchestrator = editOrchestrator;
        this.strategyFactory = strategyFactory;
    }

    @GetMapping
    public String listFiles(Model model, Authentication authentication) {
        String ownerId = authentication.getName();
        List<FileMetadataEntity> files = fileMetadataRepository.findByOwnerId(ownerId);
        model.addAttribute("files", files);
        return "files";
    }

    @GetMapping("/upload")
    public String uploadForm() {
        return "upload";
    }

    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file, Authentication authentication) throws IOException {
        String ownerId = authentication.getName();
        String fileId = UUID.randomUUID().toString();
        String relativePath = file.getOriginalFilename();
        long size = file.getSize();
        byte[] bytes = file.getBytes();
        String sha256Hash = hashCalculator.computeHash(bytes);

        FileMetadataEntity entity = new FileMetadataEntity();
        entity.setId(fileId);
        entity.setRelativePath(relativePath);
        entity.setOwnerId(ownerId);
        entity.setSize(size);
        entity.setSha256Hash(sha256Hash);
        entity.setLastModified(Instant.now());
        entity.setStatus(com.filesync.common.enums.SyncStatus.SYNCED);

        fileMetadataRepository.save(entity);
        fileStorageService.save(fileId, new ByteArrayInputStream(bytes), (long) bytes.length);
        return "redirect:/files";
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable("fileId") String fileId) {
        byte[] data = fileStorageService.load(fileId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileId + "\"")
                .body(data);
    }

    @GetMapping("/edit/{fileId}")
    public String editForm(@PathVariable("fileId") String fileId, Model model) {
        var file = editOrchestrator.getFileMetadata(fileId);
        String content = editOrchestrator.getFileContent(fileId);
        model.addAttribute("file", file);
        model.addAttribute("content", content);
        model.addAttribute("originalHash", file.getSha256Hash());
        return "edit";
    }

    @PostMapping("/edit/{fileId}")
    public String saveEdit(@PathVariable("fileId") String fileId,
                           @RequestParam("content") String userContent,
                           @RequestParam("originalHash") String originalHash) throws IOException {
        ConflictContextDto conflict = editOrchestrator.trySave(fileId, originalHash, userContent);
        if (conflict != null) {
            String encodedServer = URLEncoder.encode(conflict.getServerContent(), StandardCharsets.UTF_8);
            String encodedUser = URLEncoder.encode(conflict.getUserContent(), StandardCharsets.UTF_8);
            return "redirect:/files/edit/conflict?fileId=" + fileId +
                    "&serverContent=" + encodedServer +
                    "&userContent=" + encodedUser;
        }
        return "redirect:/files";
    }

    @GetMapping("/edit/conflict")
    public String conflictPage(@RequestParam("fileId") String fileId,
                               @RequestParam("serverContent") String serverContent,
                               @RequestParam("userContent") String userContent,
                               Model model) {
        model.addAttribute("fileId", fileId);
        model.addAttribute("serverContent", serverContent);
        model.addAttribute("userContent", userContent);
        return "conflict";
    }

    @PostMapping("/edit/resolve")
    public String resolveConflict(@RequestParam("fileId") String fileId,
                                  @RequestParam("resolvedContent") String resolvedContent,
                                  @RequestParam(value = "strategy", required = false) String strategyType) {
        if (strategyType != null && !"merge".equals(strategyType)) {
            var strategy = strategyFactory.getStrategy(strategyType);
            var conflictContext = new ConflictContextDto(fileId, "", resolvedContent);
            String finalContent = strategy.resolve(conflictContext);
            editOrchestrator.resolveAndSave(fileId, finalContent);
        } else {
            editOrchestrator.resolveAndSave(fileId, resolvedContent);
        }
        return "redirect:/files";
    }
}