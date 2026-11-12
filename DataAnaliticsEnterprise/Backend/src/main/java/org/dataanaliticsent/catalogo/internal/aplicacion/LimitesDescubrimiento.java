package org.dataanaliticsent.catalogo.internal.aplicacion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("tic.catalogo.discovery")
public class LimitesDescubrimiento {
    private int maxSchemas = 10, maxTables = 50, maxColumns = 2000, maxKeyMembers = 4000;
    private int connectTimeoutSeconds = 5, socketTimeoutSeconds = 15, queryTimeoutSeconds = 10, totalTimeoutSeconds = 60;
    public int getMaxSchemas() { return maxSchemas; }
    public void setMaxSchemas(int v) { maxSchemas = positivo(v); }
    public int getMaxTables() { return maxTables; }
    public void setMaxTables(int v) { maxTables = positivo(v); }
    public int getMaxColumns() { return maxColumns; }
    public void setMaxColumns(int v) { maxColumns = positivo(v); }
    public int getMaxKeyMembers() { return maxKeyMembers; }
    public void setMaxKeyMembers(int v) { maxKeyMembers = positivo(v); }
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int v) { connectTimeoutSeconds = positivo(v); }
    public int getSocketTimeoutSeconds() { return socketTimeoutSeconds; }
    public void setSocketTimeoutSeconds(int v) { socketTimeoutSeconds = positivo(v); }
    public int getQueryTimeoutSeconds() { return queryTimeoutSeconds; }
    public void setQueryTimeoutSeconds(int v) { queryTimeoutSeconds = positivo(v); }
    public int getTotalTimeoutSeconds() { return totalTimeoutSeconds; }
    public void setTotalTimeoutSeconds(int v) { totalTimeoutSeconds = positivo(v); }
    private static int positivo(int v) {
        if (v < 1 || v > 100000) throw new IllegalArgumentException("Límite de descubrimiento inválido");
        return v;
    }
}
