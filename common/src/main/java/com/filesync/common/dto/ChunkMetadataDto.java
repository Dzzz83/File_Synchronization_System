package com.filesync.common.dto;

public class ChunkMetadataDto {
    private final String fileId;
    private final int chunkIndex;
    private final int totalChunks;
    private final long chunkSize;

    public ChunkMetadataDto(String fileId, int chunkIndex, int totalChunks, long chunkSize) {
        this.fileId = fileId;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.chunkSize = chunkSize;
    }

    public String getFileId() {
        return fileId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public long getChunkSize() {
        return chunkSize;
    }
}
