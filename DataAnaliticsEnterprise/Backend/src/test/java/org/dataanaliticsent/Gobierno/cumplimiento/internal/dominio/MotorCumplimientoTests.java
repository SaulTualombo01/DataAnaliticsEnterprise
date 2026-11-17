package org.dataanaliticsent.Gobierno.cumplimiento.internal.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.comun.PuertoCalidad.EstadoCalidad;
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi.EstadoCumplimiento;
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi.ResultadoPolitica;
import org.dataanaliticsent.Gobierno.cumplimiento.CumplimientoApi.ResultadoRegla;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi.Clasificacion;
import org.dataanaliticsent.Gobierno.fichas.FichasGobiernoApi.Ficha;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.CategoriaPolitica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.EstadoPolitica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.Politica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.ReglaGobierno;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.TipoRegla;
import org.junit.jupiter.api.Test;

class MotorCumplimientoTests {
    private static final Optional<EstadoCalidad> SIN_CALIDAD = Optional.empty();

    private static ReglaGobierno regla(TipoRegla tipo, String parametro) {
        return new ReglaGobierno(UUID.randomUUID(), tipo, parametro);
    }

    private static Optional<Ficha> ficha(UUID responsable, Clasificacion clasificacion) {
        return Optional.of(new Ficha(UUID.randomUUID(), UUID.randomUUID(), responsable, clasificacion, 0,
                UUID.randomUUID(), Instant.EPOCH, UUID.randomUUID()));
    }

    private static Politica politica(ReglaGobierno... reglas) {
        return new Politica(UUID.randomUUID(), UUID.randomUUID(), "Política", "", CategoriaPolitica.PRIVACIDAD,
                UUID.randomUUID(), EstadoPolitica.ACTIVE, 0, UUID.randomUUID(), List.of(), List.of(reglas),
                Instant.EPOCH, UUID.randomUUID(), Instant.EPOCH);
    }

    private static ResultadoRegla evaluar(ReglaGobierno r, Optional<Ficha> f, Optional<EstadoCalidad> c) {
        return MotorCumplimiento.evaluarRegla(r, f, c);
    }

    @Test
    void sinFichaFaltaEvidenciaYQuedaIndeterminado() {
        ResultadoRegla r = evaluar(regla(TipoRegla.REQUIERE_RESPONSABLE, null), Optional.empty(), SIN_CALIDAD);
        assertEquals(EstadoCumplimiento.INDETERMINATE, r.estado());
        assertEquals("SIN_FICHA", r.motivo());
    }

    @Test
    void responsableYClasificacion() {
        assertEquals("CUMPLE", evaluar(regla(TipoRegla.REQUIERE_RESPONSABLE, null),
                ficha(UUID.randomUUID(), null), SIN_CALIDAD).motivo());
        assertEquals("FALTA_RESPONSABLE", evaluar(regla(TipoRegla.REQUIERE_RESPONSABLE, null),
                ficha(null, Clasificacion.INTERNA), SIN_CALIDAD).motivo());
        assertEquals("FALTA_CLASIFICACION", evaluar(regla(TipoRegla.REQUIERE_CLASIFICACION, null),
                ficha(UUID.randomUUID(), null), SIN_CALIDAD).motivo());
    }

    @Test
    void clasificacionMinimaComparaSensibilidad() {
        ReglaGobierno confidencial = regla(TipoRegla.CLASIFICACION_MINIMA, "CONFIDENCIAL");
        assertEquals(EstadoCumplimiento.COMPLIANT,
                evaluar(confidencial, ficha(null, Clasificacion.RESTRINGIDA), SIN_CALIDAD).estado());
        assertEquals(EstadoCumplimiento.COMPLIANT,
                evaluar(confidencial, ficha(null, Clasificacion.CONFIDENCIAL), SIN_CALIDAD).estado());
        assertEquals("CLASIFICACION_INSUFICIENTE",
                evaluar(confidencial, ficha(null, Clasificacion.INTERNA), SIN_CALIDAD).motivo());
    }

    @Test
    void calidadSinResultadoNoSeInventa() {
        ReglaGobierno calidad = regla(TipoRegla.CALIDAD_APROBADA, null);
        assertEquals("CALIDAD_SIN_EVALUAR", evaluar(calidad, Optional.empty(), SIN_CALIDAD).motivo());
        assertEquals(EstadoCumplimiento.COMPLIANT,
                evaluar(calidad, Optional.empty(), Optional.of(EstadoCalidad.APROBADO)).estado());
        assertEquals(EstadoCumplimiento.NON_COMPLIANT,
                evaluar(calidad, Optional.empty(), Optional.of(EstadoCalidad.NO_APROBADO)).estado());
    }

    @Test
    void elIncumplimientoPesaMasQueLaFaltaDeEvidencia() {
        Politica p = politica(regla(TipoRegla.REQUIERE_RESPONSABLE, null), regla(TipoRegla.CALIDAD_APROBADA, null));
        ResultadoPolitica r = MotorCumplimiento.evaluarPolitica(p, ficha(null, null), SIN_CALIDAD);

        assertEquals(EstadoCumplimiento.NON_COMPLIANT, r.estado());
        assertEquals(2, r.reglas().size());
    }

    @Test
    void politicaSinReglasEsSoloDocumentalYSinPoliticasNoAplica() {
        ResultadoPolitica documental = MotorCumplimiento.evaluarPolitica(politica(), ficha(null, null), SIN_CALIDAD);

        assertEquals(EstadoCumplimiento.INDETERMINATE, documental.estado());
        assertEquals("SIN_REGLAS", documental.motivo());
        assertEquals(EstadoCumplimiento.NOT_APPLICABLE, MotorCumplimiento.global(List.of()));
        assertEquals(EstadoCumplimiento.INDETERMINATE, MotorCumplimiento.global(List.of(documental)));
    }
}
