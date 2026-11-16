package org.dataanaliticsent.analitica.servicios;

import java.util.UUID;
import org.dataanaliticsent.analitica.dto.KpiEvaluateRequest;
import org.dataanaliticsent.analitica.dto.KpiEvaluateResponse;

public interface KpiService {

    /**
     * Evalúa los KPIs solicitados dentro del proyecto indicado. Exige identidad verificada y
     * permiso de analítica sobre {@code projectId} antes de leer cualquier dato.
     */
    KpiEvaluateResponse evaluar(UUID projectId, KpiEvaluateRequest solicitud);
}
