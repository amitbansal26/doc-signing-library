package com.docsigning.dto;

/**
 * Response returned after a successful document signing operation.
 */
public record SigningResponse(
        String documentId,
        String signedAt,
        String certificateSerialNumber,
        String auditRecordId
) {}
