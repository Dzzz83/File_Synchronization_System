package com.filesync.client.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileHasher {
    public static String computeHash(Path filePath) throws IOException
    {
        try
        {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(filePath);
            byte[] hashBytes = messageDigest.digest(fileBytes);
            StringBuilder stringBuilder = new StringBuilder();

            for (byte b : hashBytes)
            {
                stringBuilder.append(String.format("%02x", b));
            }
            return stringBuilder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}