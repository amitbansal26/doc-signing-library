package com.docsigning.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for signing a PDF that is already stored in object storage.
 */
public record ObjectStorageSigningRequest(

        /** Source bucket containing the PDF to sign. */
        @NotBlank String bucket,

        /** Object key (path) of the PDF to sign within the source bucket. */
        @NotBlank String objectKey,

        /**
         * Destination bucket for the signed PDF.
         * Defaults to the source {@code bucket} when not provided.
         */
        String outputBucket,

        /**
         * Object key for the signed PDF in the destination bucket.
         * Defaults to {@code <signedObjectKeyPrefix>/<objectKey>} when not provided.
         */
        String outputObjectKey,

        /** Name of the person or system requesting the signature. */
        @NotBlank String signerName,

        /** Reason for signing (visible in PDF signature metadata). */
        String reason,

        /** Geographical location of signing (visible in PDF signature metadata). */
        String location
) {}
