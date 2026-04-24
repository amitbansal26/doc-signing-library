# doc-signing-library

## Direct Upload Flow

Client
  |
  | Upload PDF (multipart)
  | POST /api/v1/sign
  v
Spring Boot PDF Signing API
  |
  | 1. Create per-document Vault Transit key
  v
Vault Transit
  |
  | 2. Return public key only
  v
Spring Boot API
  |
  | 3. Build CSR using Transit public key
  | 4. Ask Vault Transit to sign CSR structure
  v
Vault PKI
  |
  | 5. Issue certificate from CSR
  v
Spring Boot API
  |
  | 6. Prepare PDF ByteRange using PDFBox
  | 7. Build CMS SignedAttributes
  | 8. Ask Vault Transit to sign digest / attributes
  v
Vault Transit
  |
  | 9. Return raw signature
  v
Spring Boot API
  |
  | 10. Build PKCS#7 / CMS detached signature
  | 11. Embed certificate chain
  v
Signed PDF (returned in response body)

---

## Object Storage Flow

Client
  |
  | POST /api/v1/sign/object-storage
  | { "bucket": "documents", "objectKey": "invoice.pdf",
  |   "signerName": "Alice", "reason": "Approved" }
  v
Spring Boot PDF Signing API
  |
  | 1. Download PDF from MinIO / S3 (bucket + key)
  v
Object Storage (MinIO / AWS S3)
  |
  | 2. Return raw PDF bytes
  v
Spring Boot API
  |
  | 3-11. Same Vault Transit + PKI signing flow as above
  v
Spring Boot API
  |
  | 12. Upload signed PDF to object storage
  |     (outputBucket / outputObjectKey, default: signed/<key>)
  v
Object Storage (MinIO / AWS S3)
  |
  | 13. Return { documentId, outputBucket, outputObjectKey,
  |             signedAt, certificateSerialNumber, auditRecordId }
  v
Client

---

## Configuration

### Environment variables (object storage)

| Variable                       | Default                  | Description                                  |
|-------------------------------|--------------------------|----------------------------------------------|
| `OBJECT_STORAGE_ENDPOINT`     | `http://localhost:9000`  | S3-compatible endpoint URL                   |
| `OBJECT_STORAGE_REGION`       | `us-east-1`              | AWS region (or any value for MinIO)          |
| `OBJECT_STORAGE_ACCESS_KEY`   | `minioadmin`             | Access key ID                                |
| `OBJECT_STORAGE_SECRET_KEY`   | `minioadmin`             | Secret access key                            |
| `OBJECT_STORAGE_PATH_STYLE`   | `true`                   | Use path-style access (required for MinIO)   |
| `OBJECT_STORAGE_DEFAULT_BUCKET` | `documents`            | Default bucket name                          |
| `OBJECT_STORAGE_SIGNED_PREFIX` | `signed/`              | Key prefix for signed PDFs                   |

### Example API call

```bash
curl -X POST http://localhost:8080/api/v1/sign/object-storage \
     -H "Content-Type: application/json" \
     -d '{
           "bucket": "documents",
           "objectKey": "contracts/invoice.pdf",
           "signerName": "Alice",
           "reason": "Approved",
           "location": "Berlin"
         }'
```

Response:
```json
{
  "documentId": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
  "outputBucket": "documents",
  "outputObjectKey": "signed/contracts/invoice.pdf",
  "signedAt": "2024-11-07T12:34:56",
  "certificateSerialNumber": "1a2b3c4d",
  "auditRecordId": "9a8b7c6d-..."
}
```

---

## Local development

```bash
docker compose up --build
```

This starts:
- **OpenBao** (Vault-compatible, port 8200)
- **PostgreSQL** (port 5432)
- **MinIO** (S3-compatible object storage, API port 9000, console port 9001)
- **doc-signing-app** (port 8080)

MinIO console: http://localhost:9001 (user: `minioadmin`, password: `minioadmin`)

Swagger UI: http://localhost:8080/swagger-ui.html

