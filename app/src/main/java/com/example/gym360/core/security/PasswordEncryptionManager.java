
package com.example.gym360.core.security;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class PasswordEncryptionManager {

    private static final String KEY_ALIAS =
            "gym360_remember_password_key";

    private static final String TRANSFORMATION =
            "AES/GCM/NoPadding";

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private PasswordEncryptionManager() {}

    private static SecretKey getOrCreateKey()
            throws Exception {

        KeyStore keyStore = KeyStore.getInstance(
                "AndroidKeyStore"
        );

        keyStore.load(null);
        SecretKey key = (SecretKey) keyStore.getKey(
                KEY_ALIAS,
                null
        );
        if (key != null) {
            return key;
        }

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
        );

        KeyGenParameterSpec spec =
                new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT |
                                KeyProperties.PURPOSE_DECRYPT
                )
                        .setBlockModes(
                                KeyProperties.BLOCK_MODE_GCM
                        )
                        .setEncryptionPaddings(
                                KeyProperties.ENCRYPTION_PADDING_NONE
                        )
                        .setKeySize(256)
                        .build();

        generator.init(spec);

        return generator.generateKey();
    }

    public static String encrypt(String plaintext)
            throws Exception {

        Cipher cipher = Cipher.getInstance(
                TRANSFORMATION
        );

        cipher.init(
                Cipher.ENCRYPT_MODE,
                getOrCreateKey()
        );

        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(
                plaintext.getBytes(StandardCharsets.UTF_8)
        );

        ByteBuffer buffer = ByteBuffer.allocate(
                iv.length + encrypted.length
        );
        buffer.put(iv);
        buffer.put(encrypted);

        return Base64.encodeToString(
                buffer.array(),
                Base64.NO_WRAP
        );
    }

    public static String decrypt(String encoded)
            throws Exception {

        byte[] combined = Base64.decode(
                encoded,
                Base64.NO_WRAP
        );

        if (combined.length <= IV_LENGTH + 16) {
            throw new IllegalArgumentException(
                    "Datos cifrados inválidos"
            );
        }

        ByteBuffer buffer = ByteBuffer.wrap(combined);

        byte[] iv = new byte[IV_LENGTH];
        buffer.get(iv);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        Cipher cipher = Cipher.getInstance(
                TRANSFORMATION
        );

        cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                new GCMParameterSpec(
                        TAG_LENGTH_BITS,
                        iv
                )
        );

        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(
                plaintext,
                StandardCharsets.UTF_8
        );
    }
}
