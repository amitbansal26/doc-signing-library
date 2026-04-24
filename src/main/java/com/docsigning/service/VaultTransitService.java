package com.docsigning.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.io.pem.PemReader;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.io.StringReader;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VaultTransitService {

    private final VaultTemplate vaultTemplate;

    /**
     * Creates a per-document RSA-3072 key in Vault Transit.
     * exportable=false and allow_plaintext_backup=false ensure the key never leaves Vault.
     */
    public void createKey(String keyName) {
        log.info("Creating Transit key: {}", keyName);
        Map<String, Object> request = new HashMap<>();
        request.put("type", "rsa-3072");
        request.put("exportable", false);
        request.put("allow_plaintext_backup", false);
        vaultTemplate.write("transit/keys/" + keyName, request);
        log.info("Transit key created: {}", keyName);
    }

    /**
     * Retrieves the public key for the given Transit key name.
     */
    public PublicKey getPublicKey(String keyName) {
        log.info("Fetching public key for Transit key: {}", keyName);
        VaultResponse response = vaultTemplate.read("transit/keys/" + keyName);
        if (response == null || response.getData() == null) {
            throw new IllegalStateException("No response from Vault when reading key: " + keyName);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> keys = (Map<String, Object>) response.getData().get("keys");
        if (keys == null || !keys.containsKey("1")) {
            throw new IllegalStateException("No key version 1 found for key: " + keyName);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> keyVersion = (Map<String, Object>) keys.get("1");
        String publicKeyPem = (String) keyVersion.get("public_key");
        if (publicKeyPem == null || publicKeyPem.isBlank()) {
            throw new IllegalStateException("Empty public key PEM for key: " + keyName);
        }

        try (PemReader pemReader = new PemReader(new StringReader(publicKeyPem))) {
            byte[] encoded = pemReader.readPemObject().getContent();
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(keySpec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse public key PEM for key: " + keyName, e);
        }
    }

    /**
     * Signs data using Vault Transit. The data is hashed by Vault (SHA-256) and signed with PKCS#1v1.5.
     * prehashed=false means Vault will hash the input itself.
     */
    public byte[] sign(String keyName, byte[] data) {
        log.debug("Signing data with Transit key: {}", keyName);
        Map<String, Object> request = new HashMap<>();
        request.put("input", Base64.getEncoder().encodeToString(data));
        request.put("hash_algorithm", "sha2-256");
        request.put("signature_algorithm", "pkcs1v15");
        request.put("prehashed", false);

        VaultResponse response = vaultTemplate.write("transit/sign/" + keyName, request);
        if (response == null || response.getData() == null) {
            throw new IllegalStateException("No response from Vault when signing with key: " + keyName);
        }

        String vaultSig = (String) response.getData().get("signature");
        if (vaultSig == null || vaultSig.isBlank()) {
            throw new IllegalStateException("Empty signature from Vault for key: " + keyName);
        }

        // Vault returns "vault:v1:<base64>" — strip the prefix
        String base64Sig = vaultSig.replaceFirst("^vault:v\\d+:", "");
        return Base64.getDecoder().decode(base64Sig);
    }

    /**
     * Deletes a Transit key. First enables deletion, then deletes.
     */
    public void deleteKey(String keyName) {
        log.info("Deleting Transit key: {}", keyName);
        Map<String, Object> config = new HashMap<>();
        config.put("deletion_allowed", true);
        vaultTemplate.write("transit/keys/" + keyName + "/config", config);
        vaultTemplate.delete("transit/keys/" + keyName);
        log.info("Transit key deleted: {}", keyName);
    }
}
