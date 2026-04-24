package com.docsigning.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for S3-compatible object storage.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.object-storage")
public class ObjectStorageProperties {

    /** Custom endpoint URL for S3-compatible storage (e.g. MinIO). Leave blank for AWS S3. */
    private String endpoint;

    /** AWS region or equivalent. */
    private String region = "us-east-1";

    /** Access key ID. */
    private String accessKeyId;

    /** Secret access key. */
    private String secretAccessKey;

    /** When true, path-style access is used (required for MinIO). */
    private boolean pathStyleAccess = true;

    /** Default bucket to read source PDFs from when none is supplied in the request. */
    private String defaultBucket;

    /** Prefix applied to the object key when writing the signed PDF back to storage. */
    private String signedObjectKeyPrefix = "signed/";
}
