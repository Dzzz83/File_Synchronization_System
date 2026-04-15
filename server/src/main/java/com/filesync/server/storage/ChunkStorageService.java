package com.filesync.server.storage;

import java.io.InputStream;
import java.util.Set;

public interface ChunkStorageService
{
    void saveChunk(String fileId, int chunkIndex, InputStream data, long length);
    InputStream readChunk(String fileId, int chunkIndex);
    Set<Integer> getUploadedChunks(String fileId);
    void deleteChunks(String fileId);
    void assembleFile(String fileId, String destinationFileId);
}
