package com.docsigning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;
@Entity
@Table(name = "signing_audit_records")
@Getter
@Setter
@NoArgsConstructor
public class SigningAuditRecord {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "document_sha256", nullable = false, length = 64)
    private String documentSha256;

    @Column(name = "signed_pdf_sha256", nullable = false, length = 64)
    private String signedPdfSha256;

    @Column(name = "vault_transit_key_name", nullable = false)
    private String vaultTransitKeyName;

    @Column(name = "certificate_serial_number")
    private String certificateSerialNumber;

    @Column(name = "certificate_subject", length = 1000)
    private String certificateSubject;

    @Column(name = "certificate_issuer", length = 1000)
    private String certificateIssuer;

    @Column(name = "certificate_valid_from")
    private LocalDateTime certificateValidFrom;

    @Column(name = "certificate_valid_to")
    private LocalDateTime certificateValidTo;

    @Column(name = "signed_at", nullable = false)
    private LocalDateTime signedAt;

    @Column(name = "signed_by")
    private String signedBy;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "location", length = 500)
    private String location;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
