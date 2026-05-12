package com.filesync.server.storage;

import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.io.OutputStream;

public interface FileStorage {
    void save(String fileId, InputStream data, long size);
    void save(String fileId, MultipartFile file);
    boolean exists(String fileId);
    void delete(String fileId);
    byte[] load(String fileId);
    void stream(String fileId, OutputStream outputStream);
}
