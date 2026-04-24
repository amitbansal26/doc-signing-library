package com.docsigning.controller;

import com.docsigning.dto.ObjectStorageSigningRequest;
import com.docsigning.dto.ObjectStorageSigningResponse;
import com.docsigning.model.SigningAuditRecord;
import com.docsigning.service.AuditService;
import com.docsigning.service.ObjectStorageService;
import com.docsigning.service.PdfSigningService;
import com.docsigning.service.PdfSigningService.SignPdfResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Document Signing", description = "PDF signing operations using Vault Transit and PKI")
public class SigningController {

    private final PdfSigningService pdfSigningService;
    private final AuditService auditService;
    private final ObjectStorageService objectStorageService;

    @Operation(summary = "Sign a PDF document",
               description = "Uploads a PDF, signs it using a per-document Vault Transit RSA-3072 key, and returns the signed PDF.")
    @PostMapping(value = "/sign",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
                 produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> signPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam("signerName") String signerName,
            @RequestParam(value = "reason", defaultValue = "Document Signing") String reason,
            @RequestParam(value = "location", defaultValue = "") String location) throws Exception {

        String documentId = UUID.randomUUID().toString();
        log.info("Received signing request: documentId={}, signerName={}", documentId, signerName);

        byte[] pdfBytes = file.getBytes();

        // Compute input document SHA-256
        byte[] docHashBytes = MessageDigest.getInstance("SHA-256").digest(pdfBytes);
        String docSha256 = HexFormat.of().formatHex(docHashBytes);

        // Perform signing
        SignPdfResult result = pdfSigningService.signPdf(pdfBytes, documentId, signerName, reason, location);

        // Compute signed PDF SHA-256
        byte[] signedHashBytes = MessageDigest.getInstance("SHA-256").digest(result.signedPdf());
        String signedSha256 = HexFormat.of().formatHex(signedHashBytes);

        // Build and persist audit record
        SigningAuditRecord auditRecord = buildAuditRecord(
                documentId, docSha256, signedSha256, result, signerName, reason, location);
        SigningAuditRecord saved = auditService.saveAuditRecord(auditRecord);

        log.info("Signing complete: documentId={}, auditRecordId={}", documentId, saved.getId());

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"signed-" + documentId + ".pdf\"")
                .header("X-Document-Id", documentId)
                .header("X-Audit-Record-Id", saved.getId().toString())
                .body(result.signedPdf());
    }

    @Operation(summary = "Sign a PDF stored in object storage",
               description = "Fetches a PDF from S3-compatible object storage, signs it using a per-document "
                       + "Vault Transit RSA-3072 key, uploads the signed PDF back to object storage, and "
                       + "returns the location of the signed document.")
    @PostMapping(value = "/sign/object-storage",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ObjectStorageSigningResponse> signPdfFromObjectStorage(
            @Valid @RequestBody ObjectStorageSigningRequest request) throws Exception {

        String documentId = UUID.randomUUID().toString();
        log.info("Received object-storage signing request: documentId={}, bucket={}, key={}, signerName={}",
                documentId, request.bucket(), request.objectKey(), request.signerName());

        // Download the source PDF from object storage
        byte[] pdfBytes = objectStorageService.downloadPdf(request.bucket(), request.objectKey());

        // Compute input document SHA-256
        byte[] docHashBytes = MessageDigest.getInstance("SHA-256").digest(pdfBytes);
        String docSha256 = HexFormat.of().formatHex(docHashBytes);

        String reason = request.reason() != null ? request.reason() : "Document Signing";
        String location = request.location() != null ? request.location() : "";

        // Perform signing
        SignPdfResult result = pdfSigningService.signPdf(pdfBytes, documentId, request.signerName(), reason, location);

        // Compute signed PDF SHA-256
        byte[] signedHashBytes = MessageDigest.getInstance("SHA-256").digest(result.signedPdf());
        String signedSha256 = HexFormat.of().formatHex(signedHashBytes);

        // Resolve output bucket and key
        String outputBucket = (request.outputBucket() != null && !request.outputBucket().isBlank())
                ? request.outputBucket() : request.bucket();
        String outputObjectKey = objectStorageService.resolveOutputObjectKey(
                request.objectKey(), request.outputObjectKey());

        // Upload the signed PDF back to object storage
        objectStorageService.uploadPdf(outputBucket, outputObjectKey, result.signedPdf());

        // Build and persist audit record
        SigningAuditRecord auditRecord = buildAuditRecord(
                documentId, docSha256, signedSha256, result, request.signerName(), reason, location);
        SigningAuditRecord saved = auditService.saveAuditRecord(auditRecord);

        log.info("Object-storage signing complete: documentId={}, outputBucket={}, outputKey={}, auditRecordId={}",
                documentId, outputBucket, outputObjectKey, saved.getId());

        String signedAt = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
                saved.getSignedAt() != null ? saved.getSignedAt() : LocalDateTime.now());

        return ResponseEntity.ok(new ObjectStorageSigningResponse(
                documentId,
                outputBucket,
                outputObjectKey,
                signedAt,
                result.signerCert().getSerialNumber().toString(16),
                saved.getId().toString()));
    }

    @Operation(summary = "List all audit records")
    @GetMapping("/audit")
    public ResponseEntity<List<SigningAuditRecord>> getAllAuditRecords() {
        return ResponseEntity.ok(auditService.findAll());
    }

    @Operation(summary = "Get audit records for a specific document")
    @GetMapping("/audit/{documentId}")
    public ResponseEntity<List<SigningAuditRecord>> getAuditRecordsByDocumentId(
            @PathVariable String documentId) {
        return ResponseEntity.ok(auditService.findByDocumentId(documentId));
    }

    private SigningAuditRecord buildAuditRecord(
            String documentId, String docSha256, String signedSha256,
            SignPdfResult result, String signerName, String reason, String location) {

        var cert = result.signerCert();
        SigningAuditRecord record = new SigningAuditRecord();
        record.setDocumentId(documentId);
        record.setDocumentSha256(docSha256);
        record.setSignedPdfSha256(signedSha256);
        record.setVaultTransitKeyName(result.keyName());
        record.setCertificateSerialNumber(cert.getSerialNumber().toString(16));
        record.setCertificateSubject(cert.getSubjectX500Principal().getName());
        record.setCertificateIssuer(cert.getIssuerX500Principal().getName());
        record.setCertificateValidFrom(
                cert.getNotBefore().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        record.setCertificateValidTo(
                cert.getNotAfter().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        record.setSignedAt(LocalDateTime.now());
        record.setSignedBy(signerName);
        record.setReason(reason);
        record.setLocation(location);
        return record;
    }
}
