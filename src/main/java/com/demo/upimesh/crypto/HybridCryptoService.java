package com.demo.upimesh.crypto;

import com.demo.upimesh.model.PaymentInstruction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class HybridCryptoService {

    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final int AES_KEY_BITS = 256;
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;

    private final ServerKeyHolder serverKeyHolder;
    private final ObjectMapper objectMapper;

    public HybridCryptoService(ServerKeyHolder serverKeyHolder, ObjectMapper objectMapper) {
        this.serverKeyHolder = serverKeyHolder;
        this.objectMapper = objectMapper;
    }

    public String encrypt(PaymentInstruction instruction) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(instruction);

            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(AES_KEY_BITS);
            SecretKey aesKey = keyGenerator.generateKey();

            byte[] iv = new byte[GCM_IV_BYTES];
            java.security.SecureRandom.getInstanceStrong().nextBytes(iv);

            Cipher aesCipher = Cipher.getInstance(AES_TRANSFORMATION);
            aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encryptedPayload = aesCipher.doFinal(payload);

            Cipher rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION);
            rsaCipher.init(Cipher.ENCRYPT_MODE, serverKeyHolder.getPublicKey());
            byte[] encryptedAesKey = rsaCipher.doFinal(aesKey.getEncoded());

            ByteBuffer packet = ByteBuffer.allocate(encryptedAesKey.length + iv.length + encryptedPayload.length);
            packet.put(encryptedAesKey);
            packet.put(iv);
            packet.put(encryptedPayload);
            return Base64.getEncoder().encodeToString(packet.array());
        } catch (GeneralSecurityException | JsonProcessingException ex) {
            throw new CryptoException("Unable to encrypt payment instruction", ex);
        }
    }

    public PaymentInstruction decrypt(String ciphertextBase64) {
        try {
            byte[] packet = Base64.getDecoder().decode(ciphertextBase64);
            int rsaKeyBytes = rsaKeyLengthBytes();
            if (packet.length <= rsaKeyBytes + GCM_IV_BYTES) {
                throw new GeneralSecurityException("Ciphertext is too short");
            }

            byte[] encryptedAesKey = Arrays.copyOfRange(packet, 0, rsaKeyBytes);
            byte[] iv = Arrays.copyOfRange(packet, rsaKeyBytes, rsaKeyBytes + GCM_IV_BYTES);
            byte[] encryptedPayload = Arrays.copyOfRange(packet, rsaKeyBytes + GCM_IV_BYTES, packet.length);

            Cipher rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION);
            rsaCipher.init(Cipher.DECRYPT_MODE, serverKeyHolder.getPrivateKey());
            byte[] aesKey = rsaCipher.doFinal(encryptedAesKey);

            Cipher aesCipher = Cipher.getInstance(AES_TRANSFORMATION);
            aesCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] payload = aesCipher.doFinal(encryptedPayload);

            return objectMapper.readValue(payload, PaymentInstruction.class);
        } catch (GeneralSecurityException | IllegalArgumentException | IOException ex) {
            throw new CryptoException("Ciphertext failed authentication or parsing", ex);
        }
    }

    public String computeCiphertextHash(String ciphertextBase64) {
        byte[] bytesToHash;
        try {
            bytesToHash = Base64.getDecoder().decode(ciphertextBase64);
        } catch (IllegalArgumentException ex) {
            bytesToHash = ciphertextBase64.getBytes(StandardCharsets.UTF_8);
        }
        return HexFormat.of().formatHex(MessageDigestHolder.sha256(bytesToHash));
    }

    private int rsaKeyLengthBytes() {
        RSAPrivateKey privateKey = (RSAPrivateKey) serverKeyHolder.getPrivateKey();
        return (privateKey.getModulus().bitLength() + 7) / 8;
    }

    public int rsaPublicKeyLengthBytes() {
        RSAPublicKey publicKey = (RSAPublicKey) serverKeyHolder.getPublicKey();
        return (publicKey.getModulus().bitLength() + 7) / 8;
    }

    private static final class MessageDigestHolder {
        private static byte[] sha256(byte[] bytes) {
            try {
                return MessageDigest.getInstance("SHA-256").digest(bytes);
            } catch (GeneralSecurityException ex) {
                throw new IllegalStateException("SHA-256 is unavailable", ex);
            }
        }
    }
}
