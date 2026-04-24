package com.docsigning.service;

import com.docsigning.config.ObjectStorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Service that handles reading and writing PDF objects from/to S3-compatible object storage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObjectStorageService {

    private final S3Client s3Client;
    private final ObjectStorageProperties props;

    /**
     * Downloads a PDF from object storage and returns its raw bytes.
     *
     * @param bucket     the bucket (or container) that holds the object
     * @param objectKey  the full key (path) of the object within the bucket
     * @return raw PDF bytes
     */
    public byte[] downloadPdf(String bucket, String objectKey) {
        log.info("Downloading PDF from object storage: bucket={}, key={}", bucket, objectKey);
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
        byte[] bytes = response.asByteArray();
        log.info("Downloaded {} bytes from bucket={}, key={}", bytes.length, bucket, objectKey);
        return bytes;
    }

    /**
     * Uploads a signed PDF to object storage.
     *
     * @param bucket     destination bucket
     * @param objectKey  destination key
     * @param pdfBytes   signed PDF content
     */
    public void uploadPdf(String bucket, String objectKey, byte[] pdfBytes) {
        log.info("Uploading signed PDF to object storage: bucket={}, key={}, size={}", bucket, objectKey, pdfBytes.length);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType("application/pdf")
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(pdfBytes));
        log.info("Signed PDF uploaded to bucket={}, key={}", bucket, objectKey);
    }

    /**
     * Derives the output object key for a signed PDF.
     * If {@code outputObjectKey} is provided it is used as-is; otherwise the configured
     * prefix is prepended to the source {@code sourceObjectKey}.
     *
     * @param sourceObjectKey  original object key
     * @param outputObjectKey  explicit output key (may be null or blank)
     * @return resolved output object key
     */
    public String resolveOutputObjectKey(String sourceObjectKey, String outputObjectKey) {
        if (outputObjectKey != null && !outputObjectKey.isBlank()) {
            return outputObjectKey;
        }
        // Strip any leading path separator so we don't produce double slashes.
        String prefix = props.getSignedObjectKeyPrefix();
        if (prefix == null) {
            prefix = "";
        }
        return prefix + sourceObjectKey;
    }
}
