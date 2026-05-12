package com.filesync.server.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Service
@ConditionalOnProperty(name="storage.type", havingValue = "r2")
public class R2StorageService implements FileStorage {

    private final S3Client s3Client;
    private final String bucketName;
    private static final Logger log = LoggerFactory.getLogger(R2StorageService.class);
    // constructor
    public R2StorageService(S3Client s3Client, @Value("${r2.bucket-name}") String bucketName)
    {
        log.info("=== R2StorageService created ===");
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        checkBucketStatus();
    }

    private void checkBucketStatus()
    {
        try
        {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        }catch (NoSuchBucketException e)
        {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        }
    }
    @Override
    public void save(String fileId, InputStream data, long size)
    {
        try
        {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileId)
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(data, size));
        } catch (Exception e) {
            throw new RuntimeException("Failed to save to R2: " + fileId, e);
        }
    }

    @Override
    public void save(String fileId, MultipartFile file) {
        try
        {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileId)
                    .build();
            // upload directly to R2
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to save multipart file to R2: " + fileId, e);
        }
    }

    @Override
    public boolean exists(String fileId) {
        try
        {
            // retrieve metadata without downloading
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileId)
                    .build();
            s3Client.headObject(request);
            return true;
        }
        catch (NoSuchKeyException e)
        {
            return false;
        }
        catch (S3Exception e)
        {
            throw new RuntimeException("Failed to check existence in R2L " + fileId, e);
        }
    }

    @Override
    public void delete(String fileId) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileId)
                    .build();
            s3Client.deleteObject(request);
        } catch (S3Exception e)
        {
            throw new RuntimeException("Failed to delete from R2: " + fileId, e);
        }
    }

    @Override
    public byte[] load(String fileId) {
        try
        {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileId)
                    .build();
            return s3Client.getObjectAsBytes(request).asByteArray();
        }
        catch (NoSuchKeyException e)
        {
            throw new RuntimeException("File not found in R2: "+ fileId, e);
        }
        catch (S3Exception e)
        {
            throw new RuntimeException("Failed to load from R2: " + fileId, e);
        }
    }

    @Override
    public void stream(String fileId, OutputStream outputStream)
    {
        try
        {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileId)
                    .build();
            // get the raw stream from R2
            try (InputStream s3Stream = s3Client.getObject(request))
            {
                // move to output stream
                s3Stream.transferTo(outputStream);
            }
        } catch (NoSuchKeyException e)
        {
            throw new RuntimeException("File not found: " + fileId, e);
        } catch (S3Exception | IOException e)
        {
            throw new RuntimeException("Failed to stream from R2: " + fileId, e);
        }
    }
}
