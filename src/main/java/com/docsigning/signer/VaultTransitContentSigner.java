package com.docsigning.signer;

import com.docsigning.service.VaultTransitService;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.operator.ContentSigner;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

/**
 * BouncyCastle ContentSigner backed by Vault Transit — the private key never leaves the HSM.
 */
public class VaultTransitContentSigner implements ContentSigner {

    private static final AlgorithmIdentifier ALGORITHM_IDENTIFIER =
            new AlgorithmIdentifier(PKCSObjectIdentifiers.sha256WithRSAEncryption, DERNull.INSTANCE);

    private final VaultTransitService vaultTransitService;
    private final String keyName;
    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    public VaultTransitContentSigner(VaultTransitService vaultTransitService, String keyName) {
        this.vaultTransitService = vaultTransitService;
        this.keyName = keyName;
    }

    @Override
    public AlgorithmIdentifier getAlgorithmIdentifier() {
        return ALGORITHM_IDENTIFIER;
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public byte[] getSignature() {
        byte[] dataToSign = outputStream.toByteArray();
        return vaultTransitService.sign(keyName, dataToSign);
    }
}
