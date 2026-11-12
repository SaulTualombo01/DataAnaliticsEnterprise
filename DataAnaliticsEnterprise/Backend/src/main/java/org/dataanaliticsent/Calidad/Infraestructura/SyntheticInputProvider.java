package org.dataanaliticsent.Calidad.Infraestructura;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dataanaliticsent.Calidad.Aplicacion.InputProvider;
import org.dataanaliticsent.Calidad.Dominio.QualityEngine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

import static org.dataanaliticsent.Calidad.Aplicacion.AccessService.*;

/**
 * Datos ficticios reproducibles; se habilita solo con m2.integrations.mode=mock.
 */
@Component("externalInputProvider")
@ConditionalOnProperty(name = "m2.integrations.mode", havingValue = "mock")
public class SyntheticInputProvider implements InputProvider {
    public static final UUID CUSTOMERS = UUID.fromString("f7c83e25-073f-4ddb-b72b-e4669d242d37");
    public static final UUID ORDERS = UUID.fromString("f7c83e25-073f-4ddb-b72b-e4669d242d38");
    public static final UUID EMPTY = UUID.fromString("f7c83e25-073f-4ddb-b72b-e4669d242d39");
    public static final UUID PARTIAL = UUID.fromString("f7c83e25-073f-4ddb-b72b-e4669d242d40");
    public static final UUID METADATA = UUID.fromString("8660c613-f093-4a39-a5ed-a84f64fef022");
    public static final UUID SOURCE = UUID.fromString("e4968346-e8b2-4285-9fc5-1de10f73d690");
    private final ObjectMapper mapper;

    public SyntheticInputProvider(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<Asset> assets(UUID actor, UUID project) {
        return List.of(new Asset(CUSTOMERS, SOURCE, METADATA, "Clientes ficticios · 100 filas", Set.of("id_cliente", "correo", "estado", "actualizado"),"MOCK"),
                new Asset(ORDERS, SOURCE, METADATA, "Pedidos ficticios · 20 filas", Set.of("id_pedido", "id_cliente"),"MOCK"),
                new Asset(EMPTY, SOURCE, METADATA, "Entrada ficticia vacía", Set.of("id_cliente", "correo", "estado", "actualizado"),"MOCK"),
                new Asset(PARTIAL, SOURCE, METADATA, "Entrada ficticia parcial · debe fallar", Set.of("id_cliente", "correo", "estado", "actualizado"),"MOCK"));
    }

    public Capture acquire(UUID actor, UUID project, UUID asset, UUID metadata, UUID snapshot) {
        Asset description = assets(actor, project).stream().filter(a -> a.id().equals(asset) && a.metadataVersionId().equals(metadata)).findFirst().orElseThrow(() -> missing());
        UUID captureId = UUID.nameUUIDFromBytes(("m2-fixture-v1:" + project + ":" + asset).getBytes(StandardCharsets.UTF_8));
        if (snapshot != null && !snapshot.equals(captureId)) throw missing();
        List<Map<String, Object>> rows = new ArrayList<>();
        if (asset.equals(ORDERS)) {
            for (int i = 1; i <= 20; i++)
                rows.add(Map.of("id_pedido", "P" + i, "id_cliente", i <= 18 ? "C%03d".formatted(i) : "C999"));
        } else if (!asset.equals(EMPTY)) {
            for (int i = 1; i <= (asset.equals(PARTIAL) ? 70 : 100); i++) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id_cliente", "C%03d".formatted(i > 98 ? 1 : i));
                row.put("correo", i > 95 ? null : i > 93 ? "correo-invalido" : "cliente" + i + "@example.test");
                row.put("estado", i > 98 ? "desconocido" : "activo");
                row.put("actualizado", "2026-10-28T12:00:00Z");
                rows.add(row);
            }
        }
        Map<String, Object> reference = new LinkedHashMap<>();
        for (int i = 1; i <= 100; i++) reference.put("C%03d".formatted(i), "cliente" + i + "@example.test");
        try {
            byte[] content = mapper.writeValueAsBytes(rows);
            return new Capture(captureId, captureId, asset, metadata, Instant.parse("2026-10-28T12:00:00Z"), null,
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)), content.length,
                    new QualityEngine.Input(rows, description.fields(), !asset.equals(PARTIAL), Map.of("clientes-ficticios", new QualityEngine.Reference("1", "id_cliente", "correo", reference))));
        } catch (Exception ex) {
            throw new IllegalStateException("FIXTURE_UNAVAILABLE", ex);
        }
    }

    public Capture read(UUID actor, UUID project, UUID asset, UUID metadata, UUID read, UUID snapshot) {
        Capture capture = acquire(actor, project, asset, metadata, snapshot);
        if (!capture.readId().equals(read)) throw missing();
        return capture;
    }
}

