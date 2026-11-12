package org.dataanaliticsent.catalogo.internal.dominio;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Restricción declarada y miembros en el orden de la clave, no el orden de columnas. */
public record RestriccionDatos(UUID id, UUID projectId, UUID assetId, String name, Tipo kind,
        UUID targetAssetId, LocalizadorTabla targetLocator, Resolucion resolution, List<Miembro> members) {
    public enum Tipo { PK, UNIQUE, FK }
    public enum Resolucion { RESOLVED, UNRESOLVED, NOT_ACCESSIBLE }
    public record Miembro(int position, UUID sourceFieldId, String targetFieldName) {
        public Miembro {
            if (position < 1) throw new IllegalArgumentException("Posición de clave inválida");
            Objects.requireNonNull(sourceFieldId);
        }
    }
    public RestriccionDatos {
        Objects.requireNonNull(id); Objects.requireNonNull(projectId); Objects.requireNonNull(assetId);
        Objects.requireNonNull(kind); Objects.requireNonNull(resolution);
        members = List.copyOf(members);
        if (members.isEmpty()) throw new IllegalArgumentException("Clave sin miembros");
        var fields = new HashSet<UUID>();
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).position() != i + 1 || !fields.add(members.get(i).sourceFieldId())) {
                throw new IllegalArgumentException("Orden o campo de clave inválido");
            }
        }
        if (kind != Tipo.FK) {
            if (targetAssetId != null || targetLocator != null || resolution != Resolucion.RESOLVED
                    || members.stream().anyMatch(m -> m.targetFieldName() != null)) {
                throw new IllegalArgumentException("Destino inválido en clave local");
            }
        } else if (targetLocator == null || (resolution == Resolucion.RESOLVED) != (targetAssetId != null)) {
            throw new IllegalArgumentException("Destino FK inválido");
        }
    }
}
