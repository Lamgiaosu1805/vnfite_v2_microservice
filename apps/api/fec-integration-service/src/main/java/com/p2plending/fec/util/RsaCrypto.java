package com.p2plending.fec.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class RsaCrypto {

    private RsaCrypto() {}

    public static String signSha256(String payload, String privateKeyPem) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(readPrivateKey(privateKeyPem));
            signature.update(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể ký dữ liệu FEC");
        }
    }

    public static boolean verifySha256(String payload, String signatureBase64, String publicKeyPem) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(readPublicKey(publicKeyPem));
            signature.update(payload.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception ex) {
            return false;
        }
    }

    public static String encryptPkcs1(String plainText, String publicKeyPem) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, readPublicKey(publicKeyPem));
            return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể mã hóa dữ liệu FEC");
        }
    }

    public static PrivateKey readPrivateKey(String value) throws Exception {
        byte[] bytes = decodePem(value, "PRIVATE KEY");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
    }

    public static PublicKey readPublicKey(String value) throws Exception {
        byte[] bytes = decodePem(value, "PUBLIC KEY");
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
    }

    private static byte[] decodePem(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is blank");
        }
        String normalized = value
                .replace("-----BEGIN " + label + "-----", "")
                .replace("-----END " + label + "-----", "")
                .replace("\\n", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(normalized);
    }

    public static String extractSignatureValue(String header) {
        if (header == null || header.isBlank()) return "";
        for (String part : header.split("&")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("signature=")) {
                return trimmed.substring("signature=".length());
            }
        }
        return header.trim();
    }
}
