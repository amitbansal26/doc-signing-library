package com.docsigning.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request parameters for document signing.
 */
public record SigningRequest(
        @NotBlank String signerName,
        String reason,
        String location
) {}
