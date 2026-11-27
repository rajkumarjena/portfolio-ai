package com.artech.portfolio.credential.util;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRED_V1 - the properties the vault's security rests on:
 *
 * <ol>
 *   <li>Argon2id turns the Master Password + salt into one deterministic KEK,</li>
 *   <li>a different password or salt gives a different KEK,</li>
 *   <li>AES-256-GCM round-trips a password,</li>
 *   <li>every encryption uses a fresh IV,</li>
 *   <li>a wrong key or a tampered ciphertext is rejected instead of returning
 *       garbage,</li>
 *   <li>the DEK can be wrapped by the KEK and unwrapped again - the setup and
 *       unlock flows of the screen.</li>
 * </ol>
 */
class VaultCryptoTest {

    private static final String MASTER = "MyPortfolio@2026";
    private static final String OTHER_MASTER = "MyPortfolio@2027";

    private SecretKey kek(String masterPassword, byte[] salt) {
        return VaultCrypto.deriveKek(masterPassword, salt);
    }

    @Test
    void theSameMasterPasswordAndSaltAlwaysDeriveTheSameKek() {
        byte[] salt = VaultCrypto.randomBytes(VaultConstants.SALT_BYTES);

        assertArrayEquals(kek(MASTER, salt).getEncoded(), kek(MASTER, salt).getEncoded());
    }

    @Test
    void aDifferentSaltDerivesADifferentKek() {
        assertFalse(java.util.Arrays.equals(
                kek(MASTER, VaultCrypto.randomBytes(VaultConstants.SALT_BYTES)).getEncoded(),
                kek(MASTER, VaultCrypto.randomBytes(VaultConstants.SALT_BYTES)).getEncoded()));
    }

    @Test
    void aDifferentMasterPasswordDerivesADifferentKek() {
        byte[] salt = VaultCrypto.randomBytes(VaultConstants.SALT_BYTES);

        assertFalse(java.util.Arrays.equals(
                kek(MASTER, salt).getEncoded(),
                kek(OTHER_MASTER, salt).getEncoded()));
    }

    @Test
    void aPasswordEncryptedWithTheDekCanBeDecryptedAgain() {
        SecretKey dek = VaultCrypto.randomDek();
        String password = "MySecretPassword123";

        VaultCrypto.Sealed sealed = VaultCrypto.encrypt(dek, password.getBytes(StandardCharsets.UTF_8));
        byte[] plain = VaultCrypto.decrypt(dek, sealed.iv(), sealed.cipherText());

        assertEquals(password, new String(plain, StandardCharsets.UTF_8));
        assertEquals(VaultConstants.IV_BYTES, sealed.iv().length);
    }

    @Test
    void everyEncryptionUsesAFreshIv() {
        SecretKey dek = VaultCrypto.randomDek();
        byte[] plain = "same value".getBytes(StandardCharsets.UTF_8);

        VaultCrypto.Sealed first = VaultCrypto.encrypt(dek, plain);
        VaultCrypto.Sealed second = VaultCrypto.encrypt(dek, plain);

        // A reused IV would repeat the ciphertext; CRED_V1 forbids it.
        assertFalse(java.util.Arrays.equals(first.iv(), second.iv()));
        assertFalse(java.util.Arrays.equals(first.cipherText(), second.cipherText()));
    }

    @Test
    void aWrongKeyIsRejectedAndNeverYieldsPlaintext() {
        VaultCrypto.Sealed sealed = VaultCrypto.encrypt(
                VaultCrypto.randomDek(), "secret".getBytes(StandardCharsets.UTF_8));

        assertThrows(VaultCrypto.VaultCryptoException.class,
                () -> VaultCrypto.decrypt(VaultCrypto.randomDek(), sealed.iv(), sealed.cipherText()));
    }

    @Test
    void aTamperedCiphertextIsRejectedByTheGcmTag() {
        SecretKey dek = VaultCrypto.randomDek();
        VaultCrypto.Sealed sealed = VaultCrypto.encrypt(dek, "secret".getBytes(StandardCharsets.UTF_8));
        byte[] tampered = sealed.cipherText().clone();
        tampered[0] ^= 0x01;

        assertThrows(VaultCrypto.VaultCryptoException.class,
                () -> VaultCrypto.decrypt(dek, sealed.iv(), tampered));
    }

    @Test
    void theDekIsWrappedByTheKekAndOnlyTheRightMasterPasswordUnwrapsIt() {
        // CRED_V1 "Create Vault": salt -> KEK -> random DEK -> wrapped DEK.
        byte[] salt = VaultCrypto.randomBytes(VaultConstants.SALT_BYTES);
        SecretKey dek = VaultCrypto.randomDek();
        VaultCrypto.Sealed wrapped = VaultCrypto.encrypt(kek(MASTER, salt), dek.getEncoded());

        // "Unlock Vault" with the right Master Password recovers the same DEK.
        byte[] unwrapped = VaultCrypto.decrypt(kek(MASTER, salt), wrapped.iv(), wrapped.cipherText());
        assertArrayEquals(dek.getEncoded(), unwrapped);

        // Any other Master Password derives a KEK that fails the GCM tag, which
        // is exactly how a wrong Master Password is reported - no hash stored.
        assertThrows(VaultCrypto.VaultCryptoException.class,
                () -> VaultCrypto.decrypt(kek(OTHER_MASTER, salt), wrapped.iv(), wrapped.cipherText()));
    }

    @Test
    void base64HelpersRoundTripKeysAndBytes() {
        SecretKey dek = VaultCrypto.randomDek();

        SecretKey copy = VaultCrypto.keyFromBase64(VaultCrypto.base64(dek));

        assertArrayEquals(dek.getEncoded(), copy.getEncoded());
        assertTrue(VaultCrypto.base64(VaultCrypto.randomBytes(12)).length() > 0);
    }
}
