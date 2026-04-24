package com.docsigning.service;

import com.docsigning.signer.VaultTransitContentSigner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.io.StringReader;
import java.io.StringWriter;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VaultPkiService {

    private final VaultTemplate vaultTemplate;
    private final VaultTransitService vaultTransitService;

    /**
     * Builds a CSR using the Transit public key.
     * The CSR is signed via VaultTransitContentSigner — the private key never leaves Vault.
     */
    public PKCS10CertificationRequest createCsr(String keyName, PublicKey publicKey, String subjectDN) {
        log.info("Creating CSR for keyName={}, subject={}", keyName, subjectDN);
        try {
            JcaPKCS10CertificationRequestBuilder csrBuilder = new JcaPKCS10CertificationRequestBuilder(
                    new X500Name(subjectDN), publicKey);
            VaultTransitContentSigner contentSigner = new VaultTransitContentSigner(vaultTransitService, keyName);
            return csrBuilder.build(contentSigner);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create CSR for key: " + keyName, e);
        }
    }

    /**
     * Issues a short-lived document-signing certificate from Vault PKI using the provided CSR.
     *
     * @return list where index 0 = signer cert, index 1 = CA cert
     */
    public List<X509Certificate> issueCertificate(PKCS10CertificationRequest csr, String commonName) {
        log.info("Issuing certificate for commonName={}", commonName);
        try {
            String pemCsr = toPemString(csr);

            Map<String, Object> request = new HashMap<>();
            request.put("csr", pemCsr);
            request.put("common_name", commonName);
            request.put("ttl", "24h");

            VaultResponse response = vaultTemplate.write("pki/sign/doc-signer", request);
            if (response == null || response.getData() == null) {
                throw new IllegalStateException("No response from Vault PKI when issuing certificate");
            }

            Map<String, Object> data = response.getData();
            String certPem = (String) data.get("certificate");
            String issuingCaPem = (String) data.get("issuing_ca");

            List<X509Certificate> chain = new ArrayList<>();
            chain.add(parseCertificate(certPem));
            chain.add(parseCertificate(issuingCaPem));

            // Optionally include additional chain certs
            Object caChainObj = data.get("ca_chain");
            if (caChainObj instanceof List<?> caChainList) {
                for (Object entry : caChainList) {
                    if (entry instanceof String pem && !pem.equals(issuingCaPem) && !pem.equals(certPem)) {
                        chain.add(parseCertificate(pem));
                    }
                }
            }

            log.info("Certificate issued, serial={}", chain.get(0).getSerialNumber());
            return chain;
        } catch (Exception e) {
            throw new RuntimeException("Failed to issue certificate from Vault PKI", e);
        }
    }

    /**
     * Retrieves the CA certificate from Vault PKI.
     */
    public X509Certificate getCaCertificate() {
        VaultResponse response = vaultTemplate.read("pki/cert/ca");
        if (response == null || response.getData() == null) {
            throw new IllegalStateException("No response from Vault PKI when fetching CA cert");
        }
        String certPem = (String) response.getData().get("certificate");
        try {
            return parseCertificate(certPem);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse CA certificate from Vault PKI", e);
        }
    }

    private String toPemString(PKCS10CertificationRequest csr) throws Exception {
        StringWriter sw = new StringWriter();
        try (org.bouncycastle.openssl.jcajce.JcaPEMWriter pw = new org.bouncycastle.openssl.jcajce.JcaPEMWriter(sw)) {
            pw.writeObject(csr);
        }
        return sw.toString();
    }

    private X509Certificate parseCertificate(String pem) throws Exception {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object obj = parser.readObject();
            if (obj instanceof X509CertificateHolder holder) {
                return new JcaX509CertificateConverter().getCertificate(holder);
            }
            throw new IllegalArgumentException("PEM does not contain an X.509 certificate: " + obj);
        }
    }
}
