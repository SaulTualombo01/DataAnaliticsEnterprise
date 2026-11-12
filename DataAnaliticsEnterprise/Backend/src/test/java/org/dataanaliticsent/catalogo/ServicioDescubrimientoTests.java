package org.dataanaliticsent.catalogo;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.autorizacion.*;
import org.dataanaliticsent.catalogo.internal.aplicacion.*;
import org.dataanaliticsent.catalogo.internal.dominio.*;
import org.dataanaliticsent.identidad.IdentidadActualApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServicioDescubrimientoTests {
    private final UUID actor=UUID.randomUUID(),project=UUID.randomUUID(),source=UUID.randomUUID();
    private final Instant now=Instant.parse("2026-11-11T20:00:00Z");
    private IdentidadActualApi identidad;
    private AutorizacionApi autorizacion;
    private RepositorioCatalogo datos;
    private ExtractorPostgres extractor;
    private PublicadorDescubrimiento publicador;
    private FuenteDatos fuente;
    private DescubrimientoPostgresApi api;
    @BeforeEach void preparar() {
        identidad=mock(IdentidadActualApi.class); autorizacion=mock(AutorizacionApi.class);
        datos=mock(RepositorioCatalogo.class); extractor=mock(ExtractorPostgres.class);
        publicador=mock(PublicadorDescubrimiento.class);
        when(identidad.usuarioId()).thenReturn(actor);
        fuente=new FuenteDatos(source,project,"Origen",FuenteDatos.Tipo.POSTGRESQL,FuenteDatos.Estado.ENABLED,
                Map.of("host","localhost","port","5432","database","origen"),"fixture",actor,now,now,0);
        when(datos.buscarFuente(project,source)).thenReturn(Optional.of(fuente));
        api=servicio(publicador);
    }
    private DescubrimientoPostgresApi servicio(PublicadorDescubrimiento publisher) {
        return new ServicioDescubrimientoPostgres(identidad,autorizacion,datos,extractor,publisher,
                Clock.fixed(now,ZoneOffset.UTC),new LimitesDescubrimiento());
    }
    private DescubrimientoPostgresApi.Solicitud solicitud() {
        return new DescubrimientoPostgresApi.Solicitud(project,source,List.of("ventas"));
    }

    @Test void denegacionNoLeeConfiguracionNiAbreOrigen() {
        doThrow(new AccesoDenegadoException(DecisionAcceso.Motivo.PERMISO_NO_CONCEDIDO)).when(autorizacion).exigir(eq(actor),any());
        assertThrows(AccesoDenegadoException.class,()->api.descubrir(solicitud()));
        verifyNoInteractions(datos,extractor,publicador);
    }
    @Test void esquemasDuplicadosOSistemaNoAccedenAlOrigen() {
        for (List<String> schemas:List.of(List.<String>of(),List.of("ventas","ventas"),List.of("pg_catalog"),List.of(""))) {
            assertThrows(IllegalArgumentException.class,()->api.descubrir(new DescubrimientoPostgresApi.Solicitud(project,source,schemas)));
        }
        verifyNoInteractions(extractor,publicador);
    }
    @Test void tipoDeFuenteNoCompatibleSeRechaza() {
        var otra=new FuenteDatos(source,project,"MySQL",FuenteDatos.Tipo.MYSQL,FuenteDatos.Estado.ENABLED,
                fuente.configuration(),"fixture",actor,now,now,0);
        when(datos.buscarFuente(project,source)).thenReturn(Optional.of(otra));
        assertThrows(IllegalArgumentException.class,()->api.descubrir(solicitud()));
        verifyNoInteractions(extractor,publicador);
    }
    @Test void falloDelOrigenNoPublicaEstadoParcial() {
        when(extractor.extraer(fuente,List.of("ventas"))).thenThrow(new DescubrimientoFallidoException(
                DescubrimientoFallidoException.Codigo.ORIGEN_NO_DISPONIBLE));
        assertThrows(DescubrimientoFallidoException.class,()->api.descubrir(solicitud()));
        verifyNoInteractions(publicador);
    }
    @Test void claveCompuestaConservaOrdenYResuelveActivoDelMismoLote() {
        var parent=new LocalizadorTabla("ventas","parent");
        var child=new LocalizadorTabla("ventas","child");
        var cols=List.of(new ExtractorPostgres.Columna("tenant",1,"integer",CampoDatos.TipoLogico.INTEGER,false),
                new ExtractorPostgres.Columna("code",2,"integer",CampoDatos.TipoLogico.INTEGER,false));
        var key=new ExtractorPostgres.Clave("fk",RestriccionDatos.Tipo.FK,parent,true,List.of(
                new ExtractorPostgres.MiembroClave(1,"code","code"),new ExtractorPostgres.MiembroClave(2,"tenant","tenant")));
        when(extractor.extraer(fuente,List.of("ventas"))).thenReturn(List.of(
                new ExtractorPostgres.Tabla(child,cols,List.of(key)),new ExtractorPostgres.Tabla(parent,cols,List.of())));
        var result=api.descubrir(solicitud());
        @SuppressWarnings("unchecked") ArgumentCaptor<List<EstadoCatalogo>> estados=ArgumentCaptor.forClass(List.class);
        verify(publicador).publicar(eq(actor),eq(fuente),eq(List.of("ventas")),estados.capture(),eq(result.requestId()),eq(now));
        var c=estados.getValue().getFirst(); var p=estados.getValue().getLast();
        var fk=c.restricciones().getFirst();
        assertEquals(p.activo().id(),fk.targetAssetId());
        assertEquals(RestriccionDatos.Resolucion.RESOLVED,fk.resolution());
        assertEquals(c.campos().get(1).id(),fk.members().get(0).sourceFieldId());
        assertEquals(c.campos().get(0).id(),fk.members().get(1).sourceFieldId());
        assertEquals(List.of("code","tenant"),fk.members().stream().map(RestriccionDatos.Miembro::targetFieldName).toList());
    }
    @Test void publicadorRevalidaPermisoDespuesDeLeerOrigen() {
        when(extractor.extraer(fuente,List.of("ventas"))).thenReturn(List.of());
        doNothing().doThrow(new AccesoDenegadoException(DecisionAcceso.Motivo.PERMISO_NO_CONCEDIDO))
                .when(autorizacion).exigir(eq(actor),any());
        api=servicio(new PublicadorDescubrimiento(autorizacion,datos));
        assertThrows(AccesoDenegadoException.class,()->api.descubrir(solicitud()));
        verify(datos,never()).registrarDescubrimiento(any(),any(),any(),any(),any(),any());
    }
    @Test void esquemaVacioPublicaSoloLaObservacionSinInventarActivos() {
        when(extractor.extraer(fuente,List.of("ventas"))).thenReturn(List.of());
        var result=api.descubrir(solicitud());
        assertTrue(result.tables().isEmpty());
        verify(publicador).publicar(actor,fuente,List.of("ventas"),List.of(),result.requestId(),now);
    }
}
