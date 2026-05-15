package com.name.flashlight.integration.metrics;

import android.util.Base64;

import java.security.spec.AlgorithmParameterSpec;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class MetricsCodeUtils {

    public static byte[] decode(byte[] input) {
        if (input == null || input.length == 0) {
            return new byte[0];
        }
        return Base64.decode(input, Base64.NO_WRAP);
    }

    public static String base64Encode2String(final byte[] input) {
        if (input == null || input.length == 0) return "";
        return Base64.encodeToString(input, Base64.NO_WRAP);
    }

    public static byte[] decryptBase64AES(
            final byte[] data,
            final byte[] key,
            final String transformation,
            final byte[] iv
    ) {
        return decryptAES(decode(data), key, transformation, iv);
    }

    public static byte[] decryptAES(
            final byte[] data,
            final byte[] key,
            final String transformation,
            final byte[] iv
    ) {
        return symmetricTemplate(data, key, MetricsConstant.ALGO_AES, transformation, iv, false);
    }

    /**
     * @noinspection SameParameterValue
     */
    private static byte[] symmetricTemplate(
            final byte[] data,
            final byte[] key,
            final String algorithm,
            final String transformation,
            final byte[] iv,
            final boolean isEncrypt
    ) {
        if (data == null || data.length == 0 || key == null || key.length == 0) return null;
        try {
            SecretKey secretKey;
            if (Objects.equals(MetricsConstant.ALGO_DES, algorithm)) {
                final DESKeySpec desKey = new DESKeySpec(key);
                final SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(algorithm);
                secretKey = keyFactory.generateSecret(desKey);
            } else {
                secretKey = new SecretKeySpec(key, algorithm);
            }
            final Cipher cipher = Cipher.getInstance(transformation);
            if (iv == null || iv.length == 0) {
                cipher.init(isEncrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, secretKey);
            } else {
                final AlgorithmParameterSpec params = new IvParameterSpec(iv);
                cipher.init(isEncrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, secretKey, params);
            }
            return cipher.doFinal(data);
        } catch (Throwable e) {
            return null;
        }
    }
}