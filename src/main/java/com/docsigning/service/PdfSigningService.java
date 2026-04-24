package com.docsigning.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.cms.*;
import org.bouncycastle.asn1.ess.ESSCertIDv2;
import org.bouncycastle.asn1.ess.SigningCertificateV2;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfSigningService {

    private final VaultTransitService vaultTransitService;
    private final VaultPkiService vaultPkiService;

    /**
     * Container for the result of a PDF signing operation.
     */
    public record SignPdfResult(
            byte[] signedPdf,
            String keyName,
            X509Certificate signerCert,
            List<X509Certificate> certChain
    ) {}

    /**
     * Signs the given PDF bytes, returning the signed PDF along with certificate metadata.
     */
    public SignPdfResult signPdf(byte[] pdfBytes, String documentId, String signerName,
                                  String reason, String location) throws Exception {
        String keyName = "doc-key-" + documentId;
        log.info("Starting PDF signing: documentId={}, keyName={}", documentId, keyName);

        // 1. Create per-document Transit key (private key stays in Vault)
        vaultTransitService.createKey(keyName);

        // 2. Retrieve the public key
        PublicKey publicKey = vaultTransitService.getPublicKey(keyName);

        // 3. Build CSR signed via Transit and issue a short-lived cert from Vault PKI
        String subjectDN = "CN=" + signerName + ", O=Doc Signing Organization";
        PKCS10CertificationRequest csr = vaultPkiService.createCsr(keyName, publicKey, subjectDN);
        List<X509Certificate> certChain = vaultPkiService.issueCertificate(csr, signerName);
        X509Certificate signerCert = certChain.get(0);

        // 4. Prepare PDFBox external signing
        PDDocument document = Loader.loadPDF(pdfBytes);

        PDSignature pdSignature = new PDSignature();
        pdSignature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
        pdSignature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
        pdSignature.setName(signerName);
        pdSignature.setReason(reason);
        pdSignature.setLocation(location);
        pdSignature.setSignDate(Calendar.getInstance());

        SignatureOptions signatureOptions = new SignatureOptions();
        signatureOptions.setPreferredSignatureSize(SignatureOptions.DEFAULT_SIGNATURE_SIZE * 4);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        document.addSignature(pdSignature, signatureOptions);
        ExternalSigningSupport externalSigning = document.saveIncrementalForExternalSigning(outputStream);

        // 5. Get the byte-range content to be signed
        byte[] contentToBeSigned = IOUtils.toByteArray(externalSigning.getContent());

        // 6. Build detached CMS/PKCS#7 signature via Vault Transit
        byte[] cmsSignature = buildCmsSignature(contentToBeSigned, keyName, signerCert, certChain);

        // 7. Embed the CMS signature into the PDF
        externalSigning.setSignature(cmsSignature);
        document.close();

        byte[] signedPdf = outputStream.toByteArray();
        log.info("PDF signing complete: documentId={}", documentId);
        return new SignPdfResult(signedPdf, keyName, signerCert, certChain);
    }

    /**
     * Builds a detached CMS SignedData structure using BouncyCastle ASN.1 primitives.
     * The actual signing is delegated to Vault Transit — no private key material in the JVM.
     */
    private byte[] buildCmsSignature(byte[] contentToBeSigned, String keyName,
                                      X509Certificate signerCert, List<X509Certificate> certChain) throws Exception {
        // Compute SHA-256 digest of the PDF byte-range content
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] contentDigest = sha256.digest(contentToBeSigned);

        Date signingTime = new Date();

        // Build signed attributes
        ASN1EncodableVector signedAttrsVec = new ASN1EncodableVector();

        // ContentType = data
        signedAttrsVec.add(new Attribute(
                PKCSObjectIdentifiers.pkcs_9_at_contentType,
                new DERSet(PKCSObjectIdentifiers.data)));

        // MessageDigest
        signedAttrsVec.add(new Attribute(
                PKCSObjectIdentifiers.pkcs_9_at_messageDigest,
                new DERSet(new DEROctetString(contentDigest))));

        // SigningTime
        signedAttrsVec.add(new Attribute(
                PKCSObjectIdentifiers.pkcs_9_at_signingTime,
                new DERSet(new ASN1UTCTime(signingTime))));

        // SigningCertificateV2 (ESS – ensures certificate binding)
        byte[] certHash = MessageDigest.getInstance("SHA-256").digest(signerCert.getEncoded());
        ESSCertIDv2 essCertIDv2 = new ESSCertIDv2(certHash);
        SigningCertificateV2 signingCertV2 = new SigningCertificateV2(new ESSCertIDv2[]{essCertIDv2});
        signedAttrsVec.add(new Attribute(
                new ASN1ObjectIdentifier("1.2.840.113549.1.9.16.2.47"),
                new DERSet(signingCertV2)));

        DERSet signedAttrSet = new DERSet(signedAttrsVec);
        byte[] encodedSignedAttrs = signedAttrSet.getEncoded("DER");

        // Sign the DER-encoded signed attributes via Vault Transit
        byte[] signature = vaultTransitService.sign(keyName, encodedSignedAttrs);

        // --- Assemble CMS SignedData manually ---

        AlgorithmIdentifier digestAlgorithm = new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256);
        AlgorithmIdentifier signatureAlgorithm = new AlgorithmIdentifier(
                PKCSObjectIdentifiers.sha256WithRSAEncryption, DERNull.INSTANCE);

        // SignerInfo
        SignerIdentifier signerIdentifier = new SignerIdentifier(
                new IssuerAndSerialNumber(
                        new X500Name(signerCert.getIssuerX500Principal().getName()),
                        signerCert.getSerialNumber()));

        SignerInfo signerInfo = new SignerInfo(
                signerIdentifier,
                digestAlgorithm,
                signedAttrSet,
                signatureAlgorithm,
                new DEROctetString(signature),
                null);

        // Certificate set
        ASN1EncodableVector certsVec = new ASN1EncodableVector();
        for (X509Certificate cert : certChain) {
            certsVec.add(Certificate.getInstance(cert.getEncoded()));
        }

        // Digest algorithms set
        ASN1EncodableVector digestAlgsVec = new ASN1EncodableVector();
        digestAlgsVec.add(digestAlgorithm);

        // Signer infos set
        ASN1EncodableVector signersVec = new ASN1EncodableVector();
        signersVec.add(signerInfo);

        // SignedData
        SignedData signedData = new SignedData(
                new DERSet(digestAlgsVec),
                new ContentInfo(PKCSObjectIdentifiers.data, null),
                new BERSet(certsVec),
                null,
                new DERSet(signersVec));

        ContentInfo contentInfo = new ContentInfo(PKCSObjectIdentifiers.signedData, signedData);
        return contentInfo.getEncoded("DER");
    }
}
