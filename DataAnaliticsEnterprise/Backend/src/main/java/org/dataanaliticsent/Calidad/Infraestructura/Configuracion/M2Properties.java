package org.dataanaliticsent.Calidad.Infraestructura.Configuracion;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "m2")
public class M2Properties {

    @Valid
    @NotNull
    private Security security = new Security();

    @Valid
    @NotNull
    private Limits limits = new Limits();

    @PostConstruct
    public void validateCryptographicKeys() {
        byte[] dataKey = Base64.getDecoder().decode(security.getDataKeyBase64());
        if (dataKey.length != 32) {
            throw new IllegalStateException("M2_DATA_KEY_BASE64 debe decodificar exactamente a 32 bytes (256 bits).");
        }
        byte[] hmacKey = Base64.getDecoder().decode(security.getEmailLookupKeyBase64());
        if (hmacKey.length < 32) {
            throw new IllegalStateException("M2_EMAIL_LOOKUP_KEY_BASE64 debe tener al menos 32 bytes.");
        }
        if (security.getDataKeyBase64().equals(security.getEmailLookupKeyBase64())) {
            throw new IllegalStateException("M2_DATA_KEY_BASE64 y M2_EMAIL_LOOKUP_KEY_BASE64 deben ser claves distintas.");
        }
        security.dataKeys();
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public Limits getLimits() {
        return limits;
    }

    public void setLimits(Limits limits) {
        this.limits = limits;
    }

    public static class Security {
        @NotBlank
        private String dataKeyId;

        @NotBlank
        private String dataKeyBase64;

        @NotBlank
        private String emailLookupKeyBase64;

        // Solo descifrado: se conservan mientras existan datos o respaldos con esos identificadores.
        private Map<String, String> previousDataKeys = new HashMap<>();

        public Map<String, String> getPreviousDataKeys() { return previousDataKeys; }
        public void setPreviousDataKeys(Map<String, String> keys) { previousDataKeys = keys; }

        public Map<String, byte[]> dataKeys() {
            Map<String, byte[]> keys = new HashMap<>();
            previousDataKeys.forEach((id, encoded) -> addKey(keys, id, encoded));
            if (keys.containsKey(dataKeyId))
                throw new IllegalStateException("La clave activa no debe repetirse entre las claves anteriores.");
            addKey(keys, dataKeyId, dataKeyBase64);
            return Map.copyOf(keys);
        }

        private void addKey(Map<String, byte[]> keys, String id, String encoded) {
            try {
                if (id == null || !id.matches("[A-Za-z0-9._-]{1,64}"))
                    throw new IllegalArgumentException();
                byte[] bytes = Base64.getDecoder().decode(encoded);
                if (bytes.length != 32 || java.util.Arrays.equals(bytes, Base64.getDecoder().decode(emailLookupKeyBase64)))
                    throw new IllegalArgumentException();
                keys.put(id, bytes);
            } catch (RuntimeException error) {
                throw new IllegalStateException("Configuración inválida del anillo de claves de datos.");
            }
        }

        @NotNull
        private Duration sessionIdleTimeout = Duration.ofMinutes(30);

        @NotNull
        private Duration sessionAbsoluteTimeout = Duration.ofHours(8);

        public String getDataKeyId() {
            return dataKeyId;
        }

        public void setDataKeyId(String dataKeyId) {
            this.dataKeyId = dataKeyId;
        }

        public String getDataKeyBase64() {
            return dataKeyBase64;
        }

        public void setDataKeyBase64(String dataKeyBase64) {
            this.dataKeyBase64 = dataKeyBase64;
        }

        public String getEmailLookupKeyBase64() {
            return emailLookupKeyBase64;
        }

        public void setEmailLookupKeyBase64(String emailLookupKeyBase64) {
            this.emailLookupKeyBase64 = emailLookupKeyBase64;
        }

        public Duration getSessionIdleTimeout() {
            return sessionIdleTimeout;
        }

        public void setSessionIdleTimeout(Duration sessionIdleTimeout) {
            this.sessionIdleTimeout = sessionIdleTimeout;
        }

        public Duration getSessionAbsoluteTimeout() {
            return sessionAbsoluteTimeout;
        }

        public void setSessionAbsoluteTimeout(Duration sessionAbsoluteTimeout) {
            this.sessionAbsoluteTimeout = sessionAbsoluteTimeout;
        }
    }

    public static class Limits {
        @Min(1) @Max(1000)
        private int maxPendingPerProject = 50;
        @Min(1) @Max(1000)
        private int maxPendingPerUser = 10;
        @Min(1) @Max(10000000)
        private long maxWorkUnits = 500000;
        @Min(1) @Max(1000000)
        private int maxFindings = 20000;
        @Min(20000000) @Max(1000000000000L)
        private long maxStoredCsvBytes = 1000000000L;
        @Min(0)
        private long minFreeDiskBytes = 100000000L;

        public int getMaxPendingPerProject() { return maxPendingPerProject; }
        public void setMaxPendingPerProject(int value) { maxPendingPerProject = value; }
        public int getMaxPendingPerUser() { return maxPendingPerUser; }
        public void setMaxPendingPerUser(int value) { maxPendingPerUser = value; }
        public long getMaxWorkUnits() { return maxWorkUnits; }
        public void setMaxWorkUnits(long value) { maxWorkUnits = value; }
        public int getMaxFindings() { return maxFindings; }
        public void setMaxFindings(int value) { maxFindings = value; }
        public long getMaxStoredCsvBytes() { return maxStoredCsvBytes; }
        public void setMaxStoredCsvBytes(long value) { maxStoredCsvBytes = value; }
        public long getMinFreeDiskBytes() { return minFreeDiskBytes; }
        public void setMinFreeDiskBytes(long value) { minFreeDiskBytes = value; }

        @Min(1)
        @Max(3650)
        private int csvRetentionDays = 30;

        public int getCsvRetentionDays() { return csvRetentionDays; }
        public void setCsvRetentionDays(int days) { csvRetentionDays = days; }
        @Min(1)
        @Max(100000)
        private int maxInputRows = 10000;

        @Min(1024)
        @Max(100000000L)
        private long maxInputBytes = 10000000L;

        @NotNull
        private Duration evaluationTimeout = Duration.ofSeconds(60);

        public int getMaxInputRows() {
            return maxInputRows;
        }

        public void setMaxInputRows(int maxInputRows) {
            this.maxInputRows = maxInputRows;
        }

        public long getMaxInputBytes() {
            return maxInputBytes;
        }

        public void setMaxInputBytes(long maxInputBytes) {
            this.maxInputBytes = maxInputBytes;
        }

        public Duration getEvaluationTimeout() {
            return evaluationTimeout;
        }

        public void setEvaluationTimeout(Duration evaluationTimeout) {
            this.evaluationTimeout = evaluationTimeout;
        }
    }
}
