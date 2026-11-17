package org.dataanaliticsent.Gobierno.cumplimiento;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.comun.Pagina;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.TipoRegla;

/**
 * Evaluación de cumplimiento de un activo contra sus políticas activas (RF-M3.2.2). Cada
 * evaluación queda guardada, no se edita y se audita. Es lo que consume M4 para sus semáforos.
 */
public interface CumplimientoApi {

    /**
     * COMPLIANT: cumple todo. NON_COMPLIANT: incumple al menos una regla. INDETERMINATE: falta
     * evidencia para decidir. NOT_APPLICABLE: el activo no tiene políticas activas.
     */
    enum EstadoCumplimiento { COMPLIANT, NON_COMPLIANT, INDETERMINATE, NOT_APPLICABLE }

    /** motivo es un código estable: CUMPLE, SIN_FICHA, FALTA_RESPONSABLE, FALTA_CLASIFICACION,
     *  CLASIFICACION_INSUFICIENTE, CALIDAD_NO_APROBADA o CALIDAD_SIN_EVALUAR. */
    record ResultadoRegla(UUID ruleId, TipoRegla tipo, String parametro, EstadoCumplimiento estado, String motivo) { }

    /** Una política sin reglas queda INDETERMINATE con motivo SIN_REGLAS: es solo documental. */
    record ResultadoPolitica(UUID policyId, String policyName, UUID policyVersionId, EstadoCumplimiento estado,
            String motivo, List<ResultadoRegla> reglas) {
        public ResultadoPolitica { reglas = List.copyOf(reglas); }
    }

    /** profileVersionId es la versión de la ficha de gobierno usada; null si no había ficha. */
    record Evaluacion(UUID id, UUID projectId, UUID assetId, EstadoCumplimiento estado, Instant evaluatedAt,
            UUID evaluatedBy, UUID profileVersionId, List<ResultadoPolitica> politicas, UUID auditEventId) {
        public Evaluacion { politicas = List.copyOf(politicas); }
    }

    /**
     * Exige m3.compliance.evaluate, y además m3.policies.read y m3.asset_profiles.read porque lee
     * las políticas y la ficha del activo con la identidad del actor.
     */
    Evaluacion evaluar(UUID projectId, UUID assetId);

    /** Exige m3.compliance.read. La evaluación más reciente del activo. */
    Optional<Evaluacion> ultima(UUID projectId, UUID assetId);

    /** Exige m3.compliance.read. De la más reciente a la más antigua. */
    Pagina<Evaluacion> historial(UUID projectId, UUID assetId, int page, int pageSize);
}
