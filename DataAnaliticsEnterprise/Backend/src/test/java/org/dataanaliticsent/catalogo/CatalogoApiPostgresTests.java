package org.dataanaliticsent.catalogo;

import java.util.List;
import java.util.UUID;
import org.dataanaliticsent.autorizacion.AccesoDenegadoException;
import org.dataanaliticsent.catalogo.CatalogoApi.*;
import org.dataanaliticsent.identidad.IdentidadActualApi;
import org.dataanaliticsent.identidad.IdentidadNoVerificadaException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** Consumidor de la fachada pública: no importa internals de catálogo ni de otros módulos. */
@Tag("postgres")
@SpringBootTest
class CatalogoApiPostgresTests {
    @Autowired CatalogoApi api;
    @Autowired JdbcClient jdbc;
    @MockitoBean IdentidadActualApi identidad;

    @Test void registraYRecuperaActivoCamposVersionYAuditoriaPorInterfazPublica() {
        Fixture f = fixture("GESTOR_CATALOGO");
        var recibido = api.registrarActivo(alta(f, "clientes"));
        var activo = api.consultarActivo(f.project(), recibido.id()).orElseThrow();
        var campos = api.listarCampos(f.project(), recibido.id());
        assertEquals(recibido.metadataVersionId(), activo.metadataVersionId());
        assertEquals(f.source(), activo.sourceId());
        assertEquals(EstadoActivo.ACTIVE, activo.status());
        assertEquals(0, activo.revision());
        assertEquals(2, campos.size());
        assertEquals(List.of("id", "nombre"), campos.stream().map(CampoCatalogo::name).toList());
        assertEquals(recibido.id(), campos.getFirst().assetId());
        assertEquals(f.project(), campos.getFirst().projectId());
        assertEquals(Boolean.FALSE, campos.getFirst().nullable());
        assertNull(campos.getLast().nullable());
        assertNotEquals(campos.getFirst().id(), campos.getLast().id());
        assertEquals(f.user(), jdbc.sql("SELECT actor_id FROM auditoria.evento WHERE resource_id=:id")
                .param("id", recibido.id()).query(UUID.class).single());
        assertEquals(recibido.requestId(), jdbc.sql("SELECT request_id FROM auditoria.evento WHERE resource_id=:id")
                .param("id", recibido.id()).query(UUID.class).single());
        assertEquals(1L, jdbc.sql("SELECT number FROM versionado.version_recurso WHERE id=:id")
                .param("id", recibido.metadataVersionId()).query(Long.class).single());
        assertFalse(activo.toString().contains("referencia-fixture"));
        assertFalse(activo.toString().contains("host"));
    }

    @Test void identidadAusenteNoRegistraNiConsulta() {
        Fixture f = fixture("GESTOR_CATALOGO");
        when(identidad.usuarioId()).thenThrow(new IdentidadNoVerificadaException());
        assertThrows(IdentidadNoVerificadaException.class, () -> api.registrarActivo(alta(f, "clientes")));
        assertThrows(IdentidadNoVerificadaException.class, () -> api.consultarActivo(f.project(), UUID.randomUUID()));
        assertEquals(0, activos(f));
    }

    @Test void rolDeCalidadNoPublicaNiLeeCatalogoAunqueTengaMembresia() {
        Fixture gestor = fixture("GESTOR_CATALOGO");
        var activo = api.registrarActivo(alta(gestor, "clientes"));
        Fixture calidad = fixture("ANALISTA_CALIDAD");
        assertThrows(AccesoDenegadoException.class, () -> api.registrarActivo(alta(calidad, "clientes")));
        assertThrows(AccesoDenegadoException.class, () -> api.consultarActivo(gestor.project(), activo.id()));
        assertEquals(0, activos(calidad));
    }

    @Test void alcanceDeActivoNoPermitePublicarUnHermanoEnSuFuente() {
        Fixture f = fixture("GESTOR_CATALOGO");
        var activo = api.registrarActivo(alta(f, "clientes"));
        jdbc.sql("DELETE FROM autorizacion.alcance_asignacion WHERE role_assignment_id=:id")
                .param("id", f.assignment()).update();
        jdbc.sql("""
                INSERT INTO autorizacion.alcance_asignacion(id,role_assignment_id,project_id,resource_scope,asset_id)
                VALUES(:id,:assignment,:project,'ASSET',:asset)
                """).param("id", UUID.randomUUID()).param("assignment", f.assignment())
                .param("project", f.project()).param("asset", activo.id()).update();
        assertTrue(api.consultarActivo(f.project(), activo.id()).isPresent());
        assertThrows(AccesoDenegadoException.class, () -> api.registrarActivo(alta(f, "otro")));
        assertEquals(1, activos(f));
    }

    @Test void idsDeProyectoYFuenteAjenosNoAmplianAcceso() {
        Fixture f = fixture("GESTOR_CATALOGO"), otro = fixture("GESTOR_CATALOGO");
        when(identidad.usuarioId()).thenReturn(f.user());
        var solicitud = new AltaActivo(f.project(), otro.source(), "clientes", TipoActivo.TABLE,
                "public", "clientes", OrigenEstructura.DECLARED, List.of());
        assertThrows(AccesoDenegadoException.class, () -> api.registrarActivo(solicitud));
        assertThrows(AccesoDenegadoException.class, () -> api.registrarActivo(alta(otro, "clientes")));
        assertEquals(0, activos(f));
        assertEquals(0, activos(otro));
    }

    @Test void revocacionImpideNuevasConsultasYAltasSinReiniciar() {
        Fixture f = fixture("GESTOR_CATALOGO");
        var activo = api.registrarActivo(alta(f, "clientes"));
        assertTrue(api.consultarActivo(f.project(), activo.id()).isPresent());
        jdbc.sql("UPDATE autorizacion.asignacion_rol SET revoked_at=now() WHERE id=:id")
                .param("id", f.assignment()).update();
        assertThrows(AccesoDenegadoException.class, () -> api.consultarActivo(f.project(), activo.id()));
        assertThrows(AccesoDenegadoException.class, () -> api.listarCampos(f.project(), activo.id()));
        assertThrows(AccesoDenegadoException.class, () -> api.registrarActivo(alta(f, "otro")));
        assertEquals(1, activos(f));
    }

    @ParameterizedTest
    @ValueSource(strings = {"disabled", "retired"})
    void fuenteNoHabilitadaNoPublicaMetadatos(String estado) {
        Fixture f = fixture("GESTOR_CATALOGO");
        jdbc.sql("UPDATE catalogo.fuente SET status=:status WHERE id=:id")
                .param("status", estado).param("id", f.source()).update();
        assertThrows(IllegalArgumentException.class, () -> api.registrarActivo(alta(f, "clientes")));
        assertEquals(0, activos(f));
        assertEquals(0, versiones(f));
    }

    @Test void publicacionDuplicadaNoCreaActivoVersionNiEventoAdicional() {
        Fixture f = fixture("GESTOR_CATALOGO");
        var solicitud = alta(f, "clientes");
        var original = api.registrarActivo(solicitud);
        assertThrows(ConflictoCatalogoException.class, () -> api.registrarActivo(solicitud));
        assertEquals(1, activos(f));
        assertEquals(1, versiones(f));
        assertEquals(1L, jdbc.sql("SELECT count(*) FROM auditoria.evento WHERE project_id=:project AND action='m1.assets.register'")
                .param("project", f.project()).query(Long.class).single());
        assertEquals(original.metadataVersionId(), api.consultarActivo(f.project(), original.id()).orElseThrow().metadataVersionId());
    }

    @Test void camposConRutaDuplicadaNoDejanPublicacionParcial() {
        Fixture f = fixture("GESTOR_CATALOGO");
        var campos = List.of(new AltaCampo("id", "id", 1, "integer", TipoLogico.INTEGER, false),
                new AltaCampo("otro", "id", 2, "text", TipoLogico.STRING, null));
        var alta = new AltaActivo(f.project(), f.source(), "clientes", TipoActivo.TABLE, "public", "clientes",
                OrigenEstructura.DECLARED, campos);
        assertThrows(IllegalArgumentException.class, () -> api.registrarActivo(alta));
        assertEquals(0, activos(f));
        assertEquals(0, versiones(f));
    }

    @Test void permisoDeDetalleNoConcedeDiccionarioAutomaticamente() {
        Fixture f = fixture("GESTOR_CATALOGO");
        var activo = api.registrarActivo(alta(f, "clientes"));
        quitarPermiso("m1.dictionary.read");
        try {
            assertTrue(api.consultarActivo(f.project(), activo.id()).isPresent());
            assertThrows(AccesoDenegadoException.class, () -> api.listarCampos(f.project(), activo.id()));
        } finally { reponerPermiso("m1.dictionary.read"); }
        assertEquals(2, api.listarCampos(f.project(), activo.id()).size());
    }

    @Test void confirmacionDeAltaNoConcedeLecturaSiSeRetiroEsePermiso() {
        Fixture f = fixture("GESTOR_CATALOGO");
        quitarPermiso("m1.assets.read");
        try {
            var recibido = api.registrarActivo(alta(f, "clientes"));
            assertNotNull(recibido.id());
            assertThrows(AccesoDenegadoException.class, () -> api.consultarActivo(f.project(), recibido.id()));
        } finally { reponerPermiso("m1.assets.read"); }
        assertEquals(1, activos(f));
    }

    @Test void recursoInexistenteNoRevelaDatosNiCambiaElProyecto() {
        Fixture f = fixture("GESTOR_CATALOGO");
        assertThrows(AccesoDenegadoException.class, () -> api.consultarActivo(f.project(), UUID.randomUUID()));
        assertThrows(AccesoDenegadoException.class, () -> api.listarCampos(f.project(), UUID.randomUUID()));
        assertEquals(0, activos(f));
    }

    private void quitarPermiso(String permiso) {
        jdbc.sql("""
                DELETE FROM autorizacion.rol_permiso
                WHERE role_id=(SELECT id FROM autorizacion.rol WHERE code='GESTOR_CATALOGO')
                AND permission_id=(SELECT id FROM autorizacion.permiso WHERE code=:permission)
                """).param("permission", permiso).update();
    }

    private void reponerPermiso(String permiso) {
        jdbc.sql("""
                INSERT INTO autorizacion.rol_permiso(role_id,permission_id)
                SELECT r.id,p.id FROM autorizacion.rol r CROSS JOIN autorizacion.permiso p
                WHERE r.code='GESTOR_CATALOGO' AND p.code=:permission
                """).param("permission", permiso).update();
    }

    private long activos(Fixture f) {
        return jdbc.sql("SELECT count(*) FROM catalogo.activo WHERE project_id=:id")
                .param("id", f.project()).query(Long.class).single();
    }

    private long versiones(Fixture f) {
        return jdbc.sql("SELECT count(*) FROM versionado.version_recurso WHERE project_id=:id")
                .param("id", f.project()).query(Long.class).single();
    }

    private AltaActivo alta(Fixture f, String localizador) {
        return new AltaActivo(f.project(), f.source(), "clientes", TipoActivo.TABLE, "public", localizador,
                OrigenEstructura.DECLARED, List.of(
                    new AltaCampo("nombre", "nombre", 2, "text", TipoLogico.STRING, null),
                    new AltaCampo("id", "id", 1, "integer", TipoLogico.INTEGER, false)));
    }

    private Fixture fixture(String role) {
        UUID user = UUID.randomUUID(), org = UUID.randomUUID(), project = UUID.randomUUID();
        UUID om = UUID.randomUUID(), pm = UUID.randomUUID(), assignment = UUID.randomUUID(), source = UUID.randomUUID();
        jdbc.sql("INSERT INTO identidad.usuario(id,email,display_name) VALUES(:id,:email,'API catálogo')")
                .param("id", user).param("email", user+"@example.test").update();
        jdbc.sql("INSERT INTO contexto.organizacion(id,name) VALUES(:id,'API catálogo')").param("id", org).update();
        jdbc.sql("INSERT INTO contexto.proyecto(id,organization_id,name) VALUES(:id,:org,'API catálogo')")
                .param("id", project).param("org", org).update();
        jdbc.sql("INSERT INTO contexto.membresia_organizacion(id,user_id,organization_id,assigned_by) VALUES(:id,:user,:org,:user)")
                .param("id", om).param("user", user).param("org", org).update();
        jdbc.sql("""
                INSERT INTO contexto.membresia_proyecto
                    (id,organization_membership_id,user_id,organization_id,project_id,assigned_by)
                VALUES(:id,:om,:user,:org,:project,:user)
                """).param("id", pm).param("om", om).param("user", user).param("org", org).param("project", project).update();
        jdbc.sql("""
                INSERT INTO autorizacion.asignacion_rol
                    (id,user_id,role_id,scope_type,project_membership_id,project_id,assigned_by)
                SELECT :id,:user,id,'PROJECT',:member,:project,:user FROM autorizacion.rol WHERE code=:role
                """).param("id", assignment).param("user", user).param("member", pm)
                .param("project", project).param("role", role).update();
        jdbc.sql("""
                INSERT INTO autorizacion.alcance_asignacion(id,role_assignment_id,project_id,resource_scope)
                VALUES(:id,:assignment,:project,'PROJECT')
                """).param("id", UUID.randomUUID()).param("assignment", assignment).param("project", project).update();
        jdbc.sql("""
                INSERT INTO catalogo.fuente(id,project_id,name,type,status,connection_configuration,credential_ref,created_by,created_at,updated_at)
                VALUES(:id,:project,'Origen de prueba','postgresql','enabled',
                    '{"host":"localhost","port":"5432","database":"fixture"}'::jsonb,'referencia-fixture',:actor,now(),now())
                """).param("id", source).param("project", project).param("actor", user).update();
        when(identidad.usuarioId()).thenReturn(user);
        return new Fixture(user, project, source, assignment);
    }

    private record Fixture(UUID user, UUID project, UUID source, UUID assignment) { }
}
