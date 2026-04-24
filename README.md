# doc-signing-library

Client
  |
  | Upload PDF
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
Signed PDF
