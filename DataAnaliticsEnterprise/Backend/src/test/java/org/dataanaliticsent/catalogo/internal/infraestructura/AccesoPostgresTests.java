package org.dataanaliticsent.catalogo.internal.infraestructura;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.dataanaliticsent.catalogo.DescubrimientoFallidoException;
import org.dataanaliticsent.catalogo.internal.aplicacion.LimitesDescubrimiento;
import org.dataanaliticsent.catalogo.internal.dominio.CampoDatos;
import org.dataanaliticsent.catalogo.internal.dominio.FuenteDatos;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class AccesoPostgresTests {
    private MockEnvironment entorno() {
        return new MockEnvironment().withProperty("TIC_SOURCE_TEST_HOST","localhost")
                .withProperty("TIC_SOURCE_TEST_PORT","5432").withProperty("TIC_SOURCE_TEST_DATABASE","origen/a?b")
                .withProperty("TIC_SOURCE_TEST_USER","fixture").withProperty("TIC_SOURCE_TEST_PASSWORD","synthetic-test-only");
    }
    private FuenteDatos fuente(String host,String ref) {
        var id=UUID.randomUUID(); var now=Instant.EPOCH;
        return new FuenteDatos(id,id,"Prueba",FuenteDatos.Tipo.POSTGRESQL,FuenteDatos.Estado.ENABLED,
                Map.of("host",host,"port","5432","database","origen/a?b"),ref,id,now,now,0);
    }
    @Test void urlCodificaNombreYSeparaSecretosConTlsPredeterminado() {
        var acceso=new AccesoPostgresConfigurado(entorno()).resolver(fuente("localhost","test"),new LimitesDescubrimiento());
        assertEquals("jdbc:postgresql://localhost:5432/origen%2Fa%3Fb",acceso.url());
        assertEquals("verify-full",acceso.properties().getProperty("sslmode"));
        assertFalse(acceso.url().contains("synthetic-test-only"));
        assertFalse(acceso.toString().contains("synthetic-test-only"));
    }
    @Test void destinoModificadoNoRecibeUnaCredencialConfigurada() {
        var error=assertThrows(DescubrimientoFallidoException.class,()->new AccesoPostgresConfigurado(entorno())
                .resolver(fuente("otro.example.test","test"),new LimitesDescubrimiento()));
        assertEquals(DescubrimientoFallidoException.Codigo.CONFIGURACION_ORIGEN,error.codigo());
        assertNull(error.getCause());
    }
    @Test void referenciaNoCanonicaNoLeeOtrasPropiedades() {
        assertThrows(DescubrimientoFallidoException.class,()->new AccesoPostgresConfigurado(entorno())
                .resolver(fuente("localhost","../DB_PASSWORD"),new LimitesDescubrimiento()));
    }
    @Test void claveAusenteRechazaConexion() {
        var env=entorno(); env.getPropertySources().remove("mockProperties");
        assertThrows(DescubrimientoFallidoException.class,()->new AccesoPostgresConfigurado(env)
                .resolver(fuente("localhost","test"),new LimitesDescubrimiento()));
    }
    @Test void tipoNativoDesconocidoNoSeInventaALeerDatos() {
        assertEquals(CampoDatos.TipoLogico.UNKNOWN,ExtractorMetadatosPostgres.tipo("jsonb","U"));
        assertEquals(CampoDatos.TipoLogico.UNKNOWN,ExtractorMetadatosPostgres.tipo("bytea","U"));
        assertEquals(CampoDatos.TipoLogico.ARRAY,ExtractorMetadatosPostgres.tipo("_int4","A"));
        assertEquals(CampoDatos.TipoLogico.DATETIME,ExtractorMetadatosPostgres.tipo("timestamptz","D"));
    }
}
