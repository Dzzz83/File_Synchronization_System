package com.filesync.server.storage;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class FileStorageService {
    private final Path rootLocation = Paths.get("./uploads");

    public FileStorageService()
    {
        try
        {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory", e);
        }
    }

    public void save(String fileId, InputStream data, long size) {
        try {
            Path destination = rootLocation.resolve(fileId);
            System.out.println("Saving assembled file to: " + destination.toAbsolutePath());
            long copied = Files.copy(data, destination, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Bytes copied: " + copied);
            if (Files.exists(destination)) {
                System.out.println("File exists after copy, size: " + Files.size(destination));
            } else {
                System.err.println("ERROR: File does not exist after copy!");
            }
        } catch (IOException e) {
            System.err.println("Save failed for " + fileId + ": " + e.getMessage());
            throw new RuntimeException("Failed to save file " + fileId, e);
        }
    }
    public void save(String fileId, MultipartFile file) {
        try {
            Path destination = rootLocation.resolve(fileId);
            System.out.println("Saving file to: " + destination.toAbsolutePath());
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("File saved, size: " + file.getSize());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file " + fileId, e);
        }
    }
    public boolean exists(String fileId)
    {
        // check if the file exists
        return Files.exists(rootLocation.resolve(fileId));
    }

    public void delete(String fileId) {
        try {
            Path filePath = rootLocation.resolve(fileId);
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                System.out.println("Deleted file: " + filePath.toAbsolutePath());
            } else {
                System.out.println("File not found: " + filePath.toAbsolutePath());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file " + fileId, e);
        }
    }

    public byte[] load(String fileId)
    {
        try
        {
            // get the path
            Path file = rootLocation.resolve(fileId);
            // read all the file into a byte array
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load file " + fileId, e);
        }
    }

}
