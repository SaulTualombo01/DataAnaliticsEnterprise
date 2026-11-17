package org.dataanaliticsent.Gobierno.cumplimiento.internal.dominio;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.dataanaliticsent.Gobierno.comun.PuertoCalidad.EstadoCalidad;
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi.EstadoCumplimiento;
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi.ResultadoPolitica;
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi.ResultadoRegla;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi.Clasificacion;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi.Ficha;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.Politica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.ReglaGobierno;

/**
 * Motor determinista: con la misma política, ficha y estado de calidad da siempre el mismo
 * resultado. Solo usa metadatos de gobierno; nunca lee registros de negocio.
 */
public final class MotorCumplimiento {
    private MotorCumplimiento() { }

    public static ResultadoRegla evaluarRegla(ReglaGobierno regla, Optional<Ficha> ficha, Optional<EstadoCalidad> calidad) {
        return switch (regla.tipo()) {
            case REQUIERE_RESPONSABLE -> ficha.isEmpty() ? sinFicha(regla)
                    : ficha.get().ownerId() != null ? resultado(regla, EstadoCumplimiento.COMPLIANT, "CUMPLE")
                    : resultado(regla, EstadoCumplimiento.NON_COMPLIANT, "FALTA_RESPONSABLE");
            case REQUIERE_CLASIFICACION -> ficha.isEmpty() ? sinFicha(regla)
                    : ficha.get().classification() != null ? resultado(regla, EstadoCumplimiento.COMPLIANT, "CUMPLE")
                    : resultado(regla, EstadoCumplimiento.NON_COMPLIANT, "FALTA_CLASIFICACION");
            case CLASIFICACION_MINIMA -> {
                if (ficha.isEmpty()) yield sinFicha(regla);
                Clasificacion actual = ficha.get().classification();
                if (actual == null) yield resultado(regla, EstadoCumplimiento.NON_COMPLIANT, "FALTA_CLASIFICACION");
                yield actual.alMenos(Clasificacion.valueOf(regla.parametro()))
                        ? resultado(regla, EstadoCumplimiento.COMPLIANT, "CUMPLE")
                        : resultado(regla, EstadoCumplimiento.NON_COMPLIANT, "CLASIFICACION_INSUFICIENTE");
            }
            case CALIDAD_APROBADA -> calidad.isEmpty()
                    ? resultado(regla, EstadoCumplimiento.INDETERMINATE, "CALIDAD_SIN_EVALUAR")
                    : calidad.get() == EstadoCalidad.APROBADO ? resultado(regla, EstadoCumplimiento.COMPLIANT, "CUMPLE")
                    : resultado(regla, EstadoCumplimiento.NON_COMPLIANT, "CALIDAD_NO_APROBADA");
        };
    }

    public static ResultadoPolitica evaluarPolitica(Politica p, Optional<Ficha> ficha, Optional<EstadoCalidad> calidad) {
        if (p.reglas().isEmpty()) {
            return new ResultadoPolitica(p.id(), p.name(), p.currentVersionId(), EstadoCumplimiento.INDETERMINATE,
                    "SIN_REGLAS", List.of());
        }
        List<ResultadoRegla> reglas = p.reglas().stream().map(r -> evaluarRegla(r, ficha, calidad)).toList();
        EstadoCumplimiento estado = peor(reglas.stream().map(ResultadoRegla::estado).toList());
        return new ResultadoPolitica(p.id(), p.name(), p.currentVersionId(), estado, null, reglas);
    }

    /** Sin políticas aplicables el activo queda NOT_APPLICABLE. */
    public static EstadoCumplimiento global(List<ResultadoPolitica> politicas) {
        return politicas.isEmpty() ? EstadoCumplimiento.NOT_APPLICABLE
                : peor(politicas.stream().map(ResultadoPolitica::estado).toList());
    }

    /** NON_COMPLIANT pesa más que INDETERMINATE, y este más que COMPLIANT. */
    static EstadoCumplimiento peor(List<EstadoCumplimiento> estados) {
        return estados.stream().max(Comparator.comparingInt(MotorCumplimiento::gravedad)).orElseThrow();
    }

    private static int gravedad(EstadoCumplimiento e) {
        return switch (e) {
            case NOT_APPLICABLE -> 0;
            case COMPLIANT -> 1;
            case INDETERMINATE -> 2;
            case NON_COMPLIANT -> 3;
        };
    }

    private static ResultadoRegla sinFicha(ReglaGobierno r) {
        return resultado(r, EstadoCumplimiento.INDETERMINATE, "SIN_FICHA");
    }

    private static ResultadoRegla resultado(ReglaGobierno r, EstadoCumplimiento estado, String motivo) {
        return new ResultadoRegla(r.id(), r.tipo(), r.parametro(), estado, motivo);
    }
}
