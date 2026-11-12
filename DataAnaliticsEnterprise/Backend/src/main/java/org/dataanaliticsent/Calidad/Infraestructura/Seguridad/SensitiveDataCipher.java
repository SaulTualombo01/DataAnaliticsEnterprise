package org.dataanaliticsent.Calidad.Infraestructura.Seguridad;
import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.M2Properties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
public class SensitiveDataCipher {

    private static final String ENVELOPE_VERSION = "v1";
    private static final String AES_GCM_ALGO = "AES/GCM/NoPadding";
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final int NONCE_LENGTH_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final String activeKeyId;
    private final Map<String, SecretKey> keyRing;
    private final SecretKey hmacLookupKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public SensitiveDataCipher(M2Properties properties) {
        this(
                properties.getSecurity().getDataKeyId(),
                properties.getSecurity().dataKeys(),
                Base64.getDecoder().decode(properties.getSecurity().getEmailLookupKeyBase64())
        );
    }

    public SensitiveDataCipher(String activeKeyId, Map<String, byte[]> rawKeyRing, byte[] rawHmacKey) {
        this.activeKeyId = Objects.requireNonNull(activeKeyId, "activeKeyId no puede ser nulo");
        this.keyRing = new HashMap<>();
        rawKeyRing.forEach((id, bytes) -> {
            if (bytes == null || bytes.length != 32) {
                throw new IllegalArgumentException("Cada clave AES debe tener 32 bytes.");
            }
            this.keyRing.put(id, new SecretKeySpec(bytes, "AES"));
        });
        if (!this.keyRing.containsKey(activeKeyId)) {
            throw new IllegalArgumentException("El anillo de claves no contiene la clave activa: " + activeKeyId);
        }
        if (rawHmacKey == null || rawHmacKey.length < 32) {
            throw new IllegalArgumentException("La clave HMAC debe tener al menos 32 bytes.");
        }
        this.hmacLookupKey = new SecretKeySpec(rawHmacKey, HMAC_ALGO);
    }

    public String encrypt(String plaintext, String purpose, String contextId, String recordId) {
        Objects.requireNonNull(plaintext, "plaintext no puede ser nulo");
        try {
            byte[] nonce = new byte[NONCE_LENGTH_BYTES];
            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(AES_GCM_ALGO);
            SecretKey key = keyRing.get(activeKeyId);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(buildAad(purpose, contextId, recordId));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return String.join(":",
                    ENVELOPE_VERSION,
                    activeKeyId,
                    Base64.getUrlEncoder().withoutPadding().encodeToString(nonce),
                    Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext)
            );
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el dato sensible.", e);
        }
    }

    public String decrypt(String envelope, String purpose, String contextId, String recordId) {
        Objects.requireNonNull(envelope, "envelope no puede ser nulo");
        String[] parts = envelope.split(":", 4);
        if (parts.length != 4 || !ENVELOPE_VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("Formato de sobre criptográfico inválido.");
        }
        String keyId = parts[1];
        SecretKey key = keyRing.get(keyId);
        if (key == null) {
            throw new IllegalArgumentException("Clave de cifrado desconocida: " + keyId);
        }
        try {
            byte[] nonce = Base64.getUrlDecoder().decode(parts[2]);
            byte[] ciphertext = Base64.getUrlDecoder().decode(parts[3]);

            Cipher cipher = Cipher.getInstance(AES_GCM_ALGO);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(buildAad(purpose, contextId, recordId));

            byte[] plainBytes = cipher.doFinal(ciphertext);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new SecurityException("Fallo de integridad o contexto incorrecto al descifrar el dato.", e);
        }
    }

    public String computeEmailLookupHmac(String rawEmail) {
        Objects.requireNonNull(rawEmail, "rawEmail no puede ser nulo");
        String normalized = rawEmail.trim().toLowerCase(Locale.ROOT);
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(hmacLookupKey);
            byte[] digest = mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error calculando HMAC de correo.", e);
        }
    }

    private byte[] buildAad(String purpose, String contextId, String recordId) {
        String combined = Objects.requireNonNull(purpose) + "|"
                + Objects.toString(contextId, "global") + "|"
                + Objects.requireNonNull(recordId);
        return combined.getBytes(StandardCharsets.UTF_8);
    }
}
