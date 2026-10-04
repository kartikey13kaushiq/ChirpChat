package com.chirpchat.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The RSA key that signs access tokens. Only its public half is published (JWKS), so other services
 * verify tokens without sharing a secret. The key id is the RFC 7638 thumbprint.
 */
@Component
public class SigningKeys {

    private static final Logger log = LoggerFactory.getLogger(SigningKeys.class);

    private final RSAKey key;

    public SigningKeys(AuthProperties properties) {
        KeyPair pair = properties.signingKey() == null || properties.signingKey().isBlank()
                ? generate()
                : load(properties.signingKey());
        try {
            this.key = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey(pair.getPrivate())
                    .keyIDFromThumbprint()
                    .build();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    public RSAKey signingKey() {
        return key;
    }

    public JWKSet publicKeys() {
        return new JWKSet(key.toPublicJWK());
    }

    private static KeyPair generate() {
        log.warn("AUTH_SIGNING_KEY is not set: using an ephemeral signing key. Issued tokens stop working on restart.");
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static KeyPair load(String pem) {
        String base64 = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) factory.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
            RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            return new KeyPair(publicKey, privateKey);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | ClassCastException | IllegalArgumentException e) {
            throw new IllegalStateException("AUTH_SIGNING_KEY must be an RSA private key in PKCS#8 PEM format", e);
        }
    }
}
