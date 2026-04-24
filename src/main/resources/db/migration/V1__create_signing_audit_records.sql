CREATE TABLE signing_audit_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id VARCHAR(255) NOT NULL,
    document_sha256 VARCHAR(64) NOT NULL,
    signed_pdf_sha256 VARCHAR(64) NOT NULL,
    vault_transit_key_name VARCHAR(255) NOT NULL,
    certificate_serial_number VARCHAR(255),
    certificate_subject VARCHAR(1000),
    certificate_issuer VARCHAR(1000),
    certificate_valid_from TIMESTAMP,
    certificate_valid_to TIMESTAMP,
    signed_at TIMESTAMP NOT NULL,
    signed_by VARCHAR(255),
    reason VARCHAR(500),
    location VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_signing_audit_document_id ON signing_audit_records(document_id);
CREATE INDEX idx_signing_audit_signed_at ON signing_audit_records(signed_at);
