package com.artech.portfolio.credential.util;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * CRED_V1 - every cryptographic primitive of the vault, in one place:
 *
 * <pre>
 * Master Password --Argon2id(salt)--> KEK --AES-256-GCM--> encrypted DEK
 * DEK --AES-256-GCM(random IV)--> encrypted credential password
 * </pre>
 *
 * <p>Nothing here logs, caches or returns a key in plaintext; the derived bytes
 * are wiped as soon as the {@link SecretKey} owns its own copy. A fresh random
 * IV is generated for every encryption, as CRED_V1 requires - reusing one would
 * break GCM completely.</p>
 */
public final class VaultCrypto {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private VaultCrypto() {
    }

    /** One ciphertext together with the IV it was produced with. */
    public record Sealed(byte[] iv, byte[] cipherText) {
    }

    public static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    /** The DEK: 256 random bits, the only key that ever touches a password. */
    public static SecretKey randomDek() {
        return new SecretKeySpec(randomBytes(VaultConstants.DEK_BYTES), "AES");
    }

    /**
     * Master Password + salt -> KEK (Argon2id). The password bytes are wiped
     * afterwards, and the raw derived bytes are wiped once the key has copied
     * them, so no plaintext key material survives the call.
     */
    public static SecretKey deriveKek(String masterPassword, byte[] salt) {
        byte[] password = masterPassword.getBytes(StandardCharsets.UTF_8);
        byte[] derived = new byte[VaultConstants.DEK_BYTES];
        try {
            Argon2Parameters parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                    .withIterations(VaultConstants.ARGON2_ITERATIONS)
                    .withMemoryAsKB(VaultConstants.ARGON2_MEMORY_KIB)
                    .withParallelism(VaultConstants.ARGON2_PARALLELISM)
                    .withSalt(salt)
                    .build();

            Argon2BytesGenerator generator = new Argon2BytesGenerator();
            generator.init(parameters);
            generator.generateBytes(password, derived);

            return new SecretKeySpec(derived, "AES");
        } finally {
            Arrays.fill(password, (byte) 0);
            Arrays.fill(derived, (byte) 0);
        }
    }

    /** AES-256-GCM with a fresh random IV for this operation only. */
    public static Sealed encrypt(SecretKey key, byte[] plainText) {
        try {
            byte[] iv = randomBytes(VaultConstants.IV_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key,
                    new GCMParameterSpec(VaultConstants.GCM_TAG_BITS, iv));
            return new Sealed(iv, cipher.doFinal(plainText));
        } catch (GeneralSecurityException e) {
            // Never include the value or the key in the message.
            throw new VaultCryptoException("Could not encrypt the value", e);
        }
    }

    /**
     * Decrypts and authenticates. A wrong key surfaces as a
     * {@link VaultCryptoException} - that is how a wrong Master Password is
     * detected, without ever storing a password hash to compare against.
     */
    public static byte[] decrypt(SecretKey key, byte[] iv, byte[] cipherText) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(VaultConstants.GCM_TAG_BITS, iv));
            return cipher.doFinal(cipherText);
        } catch (GeneralSecurityException e) {
            throw new VaultCryptoException("Could not decrypt the value", e);
        }
    }

    public static String base64(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    public static byte[] unbase64(String value) {
        return Base64.getDecoder().decode(value);
    }

    public static String base64(SecretKey key) {
        return base64(key.getEncoded());
    }

    public static SecretKey keyFromBase64(String value) {
        return new SecretKeySpec(unbase64(value), "AES");
    }

    /** No secret ever appears in this message. */
    public static class VaultCryptoException extends RuntimeException {
        public VaultCryptoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
