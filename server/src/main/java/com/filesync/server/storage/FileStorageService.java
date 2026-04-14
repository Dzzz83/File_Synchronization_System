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

    public void save(String fileId, InputStream data, long size)
    {
        try
        {
            // root location is ./uploads , fileId = "file1" ==> destination = ./uploads/file1
            Path destination = rootLocation.resolve(fileId);
            // get file as stream of bytes, save in destination, overwrite existing file
            Files.copy(data, destination, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Saving file to: " + destination.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file " + fileId, e);
        }
    }

    public void save(String fileId, MultipartFile file) {
        try {
            Path destination = rootLocation.resolve(fileId);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Saving file to: " + destination.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file " + fileId, e);
        }
    }

    public boolean exists(String fileId)
    {
        // check if the file exists
        return Files.exists(rootLocation.resolve(fileId));
    }

    public void delete(String fileId)
    {
        try
        {
            // delete the file
            Files.deleteIfExists(rootLocation.resolve(fileId));
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
