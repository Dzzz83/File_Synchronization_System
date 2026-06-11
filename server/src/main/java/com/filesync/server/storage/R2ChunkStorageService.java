package com.filesync.server.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.util.*;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "r2")
public class R2ChunkStorageService implements ChunkStorageService
{
    private static final Logger logger = LoggerFactory.getLogger(R2ChunkStorageService.class);
    private final S3Client s3Client;
    private final String bucketName;
    private final RedisUploadStateService redisUploadStateService;

    public R2ChunkStorageService(S3Client s3Client, @Value("${r2.bucket-name}") String bucketName,
                                 RedisUploadStateService redisUploadStateService)
    {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.redisUploadStateService = redisUploadStateService;
        logger.info("R2ChunkStorageService initialized with Redis shared state");
    }

    @Override
    public void saveChunk(String fileId, int chunkIndex, InputStream data, long length) {
        try {
            String uploadId = redisUploadStateService.getUploadId(fileId);

            // Initiate multipart upload only once across instances using distributed lock
            if (uploadId == null) {
                if (redisUploadStateService.tryLock(fileId, 30)) {
                    try {
                        uploadId = redisUploadStateService.getUploadId(fileId);
                        if (uploadId == null) {
                            CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
                                    .bucket(bucketName)
                                    .key(fileId)
                                    .build();
                            CreateMultipartUploadResponse response = s3Client.createMultipartUpload(request);
                            uploadId = response.uploadId();
                            redisUploadStateService.setUploadId(fileId, uploadId);
                            logger.info("Initiated multipart upload for fileId={}, uploadId={}", fileId, uploadId);
                        }
                    } finally {
                        redisUploadStateService.unlock(fileId);
                    }
                } else {
                    // Wait for another instance to create the upload
                    int retries = 10;
                    while (retries-- > 0 && uploadId == null) {
                        Thread.sleep(100);
                        uploadId = redisUploadStateService.getUploadId(fileId);
                    }
                    if (uploadId == null) {
                        throw new RuntimeException("Failed to get uploadId for fileId " + fileId);
                    }
                }
            }

            // Upload chunk
            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                    .bucket(bucketName)
                    .key(fileId)
                    .uploadId(uploadId)
                    .partNumber(chunkIndex + 1)
                    .build();

            UploadPartResponse uploadPartResponse = s3Client.uploadPart(uploadPartRequest,
                    RequestBody.fromInputStream(data, length));

            redisUploadStateService.setPartETag(fileId, chunkIndex, uploadPartResponse.eTag());
            redisUploadStateService.addUploadedChunk(fileId, chunkIndex);
            logger.debug("Uploaded chunk {} for fileId {} ({} bytes)", chunkIndex, fileId, length);

        } catch (S3Exception e) {
            throw new RuntimeException("Failed to upload chunk " + chunkIndex + " for " + fileId, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for uploadId for " + fileId, e);
        }
    }

    @Override
    public InputStream readChunk(String fileId, int chunkIndex) {
        throw new UnsupportedOperationException("Reading individual chunks is not supported in R2 mode");
    }

    @Override
    public Set<Integer> getUploadedChunks(String fileId) {
        return redisUploadStateService.getUploadedChunks(fileId);
    }

    @Override
    public void deleteChunks(String fileId) {
        String uploadId = redisUploadStateService.getUploadId(fileId);
        if (uploadId != null) {
            AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(fileId)
                    .uploadId(uploadId)
                    .build();
            s3Client.abortMultipartUpload(abortRequest);
            logger.info("Aborted multipart upload for fileId={}", fileId);
        }
        redisUploadStateService.cleanup(fileId);
    }

    @Override
    public void assembleFile(String fileId, String destinationFileId, int totalChunks) {
        String uploadId = redisUploadStateService.getUploadId(fileId);
        if (uploadId == null) {
            throw new IllegalStateException("No multipart upload in progress for " + fileId);
        }

        Set<Integer> uploaded = getUploadedChunks(fileId);
        for (int i = 0; i < totalChunks; i++) {
            if (!uploaded.contains(i)) {
                throw new IllegalStateException("Missing chunk " + i + " for " + fileId);
            }
        }

        Map<Integer, String> etags = redisUploadStateService.getPartETags(fileId);
        List<CompletedPart> completedParts = new ArrayList<>();
        for (int i = 0; i < totalChunks; i++) {
            completedParts.add(CompletedPart.builder()
                    .partNumber(i + 1)
                    .eTag(etags.get(i))
                    .build());
        }

        CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(fileId)
                .uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                .build();

        s3Client.completeMultipartUpload(completeRequest);
        logger.info("Multipart upload completed for fileId={}, totalChunks={}", fileId, totalChunks);
        redisUploadStateService.cleanup(fileId);
    }
}