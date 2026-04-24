package com.docsigning.dto;

/**
 * Response returned after successfully signing a PDF that was stored in object storage.
 */
public record ObjectStorageSigningResponse(
        /** Internal document identifier used in the audit trail. */
        String documentId,

        /** Bucket where the signed PDF was written. */
        String outputBucket,

        /** Object key of the signed PDF within the output bucket. */
        String outputObjectKey,

        /** ISO-8601 timestamp of when the PDF was signed. */
        String signedAt,

        /** Hex serial number of the certificate used to sign the document. */
        String certificateSerialNumber,

        /** UUID of the audit record created for this signing operation. */
        String auditRecordId
) {}
