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
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "r2")
public class R2ChunkStorageService implements ChunkStorageService
{
    private static final Logger logger = LoggerFactory.getLogger(R2ChunkStorageService.class);
    private final S3Client s3Client;
    private final String bucketName;

    // store multipart upload ids per fileId
    private final Map<String, String> uploadIds = new ConcurrentHashMap<>();
    // track the parts that has been uploaded
    private final Map<String, Set<Integer>> uploadedParts = new ConcurrentHashMap<>();
    // store ETags for each part
    private final Map<String, Map<Integer, String>> partETags = new ConcurrentHashMap<>();

    public R2ChunkStorageService(S3Client s3Client, @Value("${r2.bucket-name}") String bucketName)
    {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        logger.info("R2ChunkStorageService initialized - using S3 multipart upload");
    }

    @Override
    public void saveChunk(String fileId, int chunkIndex, InputStream data, long length)
    {
        try
        {
            // get or creat upload id
            String uploadId = uploadIds.computeIfAbsent(fileId, id -> {
                        CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
                                .bucket(bucketName)
                                .key(id)
                                .build();
                        CreateMultipartUploadResponse response = s3Client.createMultipartUpload(request);
                        String newId = response.uploadId();
                        logger.info("Initiated multipart upload for fileId={}, uploadId={}", id, newId);
                        uploadedParts.put(id, ConcurrentHashMap.newKeySet());
                        partETags.put(id, new ConcurrentHashMap<>());
                        return newId;
            });

            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                    .bucket(bucketName)
                    .key(fileId)
                    .uploadId(uploadId)
                    .partNumber(chunkIndex + 1)
                    .build();

            UploadPartResponse uploadPartResponse = s3Client.uploadPart(uploadPartRequest,
                    RequestBody.fromInputStream(data, length));

            // get the ETags
            partETags.get(fileId).put(chunkIndex, uploadPartResponse.eTag());
            // add the uploaded part
            uploadedParts.get(fileId).add(chunkIndex);

            logger.debug("Uploaded chunk {} for fileId {} ({} bytes)", chunkIndex, fileId, length);

        } catch (S3Exception e) {
            throw new RuntimeException("Failed to upload chunk " + chunkIndex + " for " + fileId, e);
        }
    }

    @Override
    public InputStream readChunk(String fileId, int chunkIndex) {
        throw new UnsupportedOperationException("Reading individual chunks is not supported in R2 mode");
    }

    @Override
    public Set<Integer> getUploadedChunks(String fileId) {
        Set<Integer> uploaded = uploadedParts.get(fileId);
        if (uploaded == null)
        {
            return new HashSet<>();
        }
        // return a copy
        return new HashSet<>(uploaded);
    }

    @Override
    public void deleteChunks(String fileId)
    {
        // remove the file
        String uploadId = uploadIds.remove(fileId);
        if (uploadId != null)
        {
            // remove all uploaded parts
            AbortMultipartUploadRequest abortMultipartUploadRequest = AbortMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(fileId)
                    .uploadId(uploadId)
                    .build();
            s3Client.abortMultipartUpload(abortMultipartUploadRequest);
            logger.info("Aborted multipart upload for fileId={}", fileId);
        }
        uploadedParts.remove(fileId);
        partETags.remove(fileId);
    }

    @Override
    public void assembleFile(String fileId, String destinationFileId, int totalChunks)
    {
        // retrieve upload id
        String uploadId = uploadIds.get(fileId);
        if (uploadId == null)
        {
            throw new IllegalStateException("No multipart upload in progress for " + fileId);
        }

        // check if all chunks have been uploaded
        Set<Integer> uploaded = getUploadedChunks(fileId);
        for (int i = 0; i < totalChunks; i++)
        {
            if (!uploaded.contains(i))
            {
                throw new IllegalStateException("Missing chunk " + i + " for " + fileId);
            }
        }
        // list of completed parts
        List<CompletedPart> completedParts = new ArrayList<>();
        // get the etags
        Map<Integer, String> etags = partETags.get(fileId);
        // create the completed part
        for (int i = 0; i < totalChunks; i++)
        {
            CompletedPart part = CompletedPart.builder()
                    .partNumber(i + 1)
                    .eTag(etags.get(i))
                    .build();
            completedParts.add(part);
        }
        // send the asemble file request
        CompleteMultipartUploadRequest completeMultipartUploadRequest = CompleteMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(fileId)
                .uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                .build();

        s3Client.completeMultipartUpload(completeMultipartUploadRequest);
        logger.info("Multipart upload completed for fileId={}, object size={} chunks", fileId, totalChunks);

        // delete maps of this fileId
        uploadIds.remove(fileId);
        uploadedParts.remove(fileId);
        partETags.remove(fileId);
    }
}
