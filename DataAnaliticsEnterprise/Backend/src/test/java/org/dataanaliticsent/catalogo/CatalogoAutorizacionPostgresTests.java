package org.dataanaliticsent.catalogo;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.dataanaliticsent.autorizacion.*;
import org.dataanaliticsent.autorizacion.DecisionAcceso.Motivo;
import org.dataanaliticsent.autorizacion.ProveedorRecursosAutorizacion.Jerarquia;
import org.dataanaliticsent.autorizacion.SolicitudAcceso.TipoRecurso;
import org.dataanaliticsent.catalogo.internal.dominio.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
@SpringBootTest
class CatalogoAutorizacionPostgresTests {
    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");
    @Autowired AutorizacionApi autorizacion;
    @Autowired RepositorioCatalogo catalogo;
    @Autowired ObjectProvider<ProveedorRecursosAutorizacion> proveedores;
    @Autowired JdbcClient jdbc;

    @Test void resuelveJerarquiaPersistidaConUnSoloProveedor() {
        Fixture f = fixture("GESTOR_CATALOGO");
        UUID source = fuente(f), asset = activo(f, source);
        var candidatos = proveedores.stream().filter(p -> p.soporta(TipoRecurso.ASSET)).toList();
        assertEquals(1, candidatos.size());
        var proveedor = candidatos.getFirst();
        assertEquals(new Jerarquia(f.project(), source, null),
                proveedor.resolver(new SolicitudAcceso.Recurso(TipoRecurso.SOURCE, source)).orElseThrow());
        assertEquals(new Jerarquia(f.project(), source, asset),
                proveedor.resolver(new SolicitudAcceso.Recurso(TipoRecurso.ASSET, asset)).orElseThrow());
        assertTrue(proveedor.resolver(new SolicitudAcceso.Recurso(TipoRecurso.ASSET, UUID.randomUUID())).isEmpty());
        assertTrue(proveedor.resolver(new SolicitudAcceso.Recurso(TipoRecurso.SOURCE, asset)).isEmpty());
    }

    @Test void alcanceDeFuenteCubreSusActivosPeroNoOtroOrigen() {
        Fixture f = fixture("GESTOR_CATALOGO");
        UUID source = fuente(f), first = activo(f, source), second = activo(f, source);
        UUID otherSource = fuente(f), otherAsset = activo(f, otherSource);
        alcance(f, "SOURCE", source);
        assertTrue(decision(f, TipoRecurso.SOURCE, source).permitido());
        assertTrue(decision(f, TipoRecurso.ASSET, first).permitido());
        assertTrue(decision(f, TipoRecurso.ASSET, second).permitido());
        assertFalse(decision(f, TipoRecurso.SOURCE, otherSource).permitido());
        assertFalse(decision(f, TipoRecurso.ASSET, otherAsset).permitido());
        assertEquals(List.of(new AlcanceAutorizado(f.project(), AlcanceAutorizado.Tipo.SOURCE, source)),
                autorizacion.alcancesPermitidos(f.user(), "m1.assets.search", f.project()));
    }

    @Test void alcanceDeActivoNoAsciendeAFuenteNiHermanosNiListaTodo() {
        Fixture f = fixture("GESTOR_CATALOGO");
        UUID source = fuente(f), asset = activo(f, source), sibling = activo(f, source);
        alcance(f, "ASSET", asset);
        assertTrue(decision(f, TipoRecurso.ASSET, asset).permitido());
        assertFalse(decision(f, TipoRecurso.ASSET, sibling).permitido());
        assertFalse(decision(f, TipoRecurso.SOURCE, source).permitido());
        assertFalse(autorizacion.evaluar(f.user(), SolicitudAcceso.proyecto("m1.assets.search", f.project())).permitido());
        assertEquals(List.of(new AlcanceAutorizado(f.project(), AlcanceAutorizado.Tipo.ASSET, asset)),
                autorizacion.alcancesPermitidos(f.user(), "m1.assets.search", f.project()));
    }

    @Test void proyectoDeclaradoNoSustituyePertenenciaRealDelRecurso() {
        Fixture f = fixture("GESTOR_CATALOGO"), other = fixture("GESTOR_CATALOGO");
        UUID source = fuente(f), asset = activo(f, source);
        UUID otherSource = fuente(other), otherAsset = activo(other, otherSource);
        assertTrue(decision(f, TipoRecurso.ASSET, asset).permitido());
        assertEquals(Motivo.RECURSO_NO_VALIDADO, decision(f, TipoRecurso.ASSET, otherAsset).motivo());
        assertEquals(Motivo.RECURSO_NO_VALIDADO, decision(f, TipoRecurso.SOURCE, otherSource).motivo());
        assertEquals(Motivo.RECURSO_NO_VALIDADO, decision(f, TipoRecurso.ASSET, UUID.randomUUID()).motivo());
        assertFalse(autorizacion.evaluar(f.user(),
                SolicitudAcceso.recurso("m1.assets.read", other.project(), TipoRecurso.ASSET, otherAsset)).permitido());
    }

    @Test void revocarAsignacionRetiraAccesoAlActivoPersistido() {
        Fixture f = fixture("GESTOR_CATALOGO");
        UUID asset = activo(f, fuente(f));
        assertTrue(decision(f, TipoRecurso.ASSET, asset).permitido());
        jdbc.sql("UPDATE autorizacion.asignacion_rol SET revoked_at=now() WHERE id=:id")
                .param("id", f.assignment()).update();
        assertFalse(decision(f, TipoRecurso.ASSET, asset).permitido());
        assertTrue(autorizacion.alcancesPermitidos(f.user(), "m1.assets.search", f.project()).isEmpty());
    }

    @Test void clavesForaneasImpidenAsignarRecursosDeOtroProyecto() {
        Fixture f = fixture("GESTOR_CATALOGO"), other = fixture("GESTOR_CATALOGO");
        UUID source = fuente(other), asset = activo(other, source);
        assertThrows(DataIntegrityViolationException.class, () -> insertarAlcance(f, "SOURCE", source));
        assertThrows(DataIntegrityViolationException.class, () -> insertarAlcance(f, "ASSET", asset));
    }

    @Test void administradorGlobalNoRecibeAccesoFuncionalAlCatalogo() {
        Fixture admin = fixture("ADMIN_SISTEMA"), gestor = fixture("GESTOR_CATALOGO");
        UUID asset = activo(gestor, fuente(gestor));
        jdbc.sql("""
                INSERT INTO autorizacion.asignacion_rol(id,user_id,role_id,scope_type,assigned_by)
                SELECT :id,:user,id,'PLATFORM',:user FROM autorizacion.rol WHERE code='ADMIN_SISTEMA'
                """).param("id", UUID.randomUUID()).param("user", admin.user()).update();
        assertTrue(autorizacion.evaluar(admin.user(), SolicitudAcceso.plataforma("tc.users.create")).permitido());
        assertFalse(autorizacion.evaluar(admin.user(),
                SolicitudAcceso.recurso("m1.assets.read", gestor.project(), TipoRecurso.ASSET, asset)).permitido());
    }

    @Test void retiroLogicoConservaJerarquiaSinAutorizarOperacionesDeOrigen() {
        Fixture f = fixture("GESTOR_CATALOGO");
        UUID source = fuente(f), asset = activo(f, source);
        jdbc.sql("UPDATE catalogo.fuente SET status='disabled' WHERE id=:id").param("id", source).update();
        jdbc.sql("UPDATE catalogo.activo SET status='retired' WHERE id=:id").param("id", asset).update();
        var proveedor = proveedores.stream().filter(p -> p.soporta(TipoRecurso.ASSET)).findFirst().orElseThrow();
        assertEquals(new Jerarquia(f.project(), source, asset),
                proveedor.resolver(new SolicitudAcceso.Recurso(TipoRecurso.ASSET, asset)).orElseThrow());
        // Resolver pertenencia no habilita descubrir una fuente deshabilitada.
        assertThrows(IllegalArgumentException.class, () -> activo(f, source));
    }

    private DecisionAcceso decision(Fixture f, TipoRecurso tipo, UUID resource) {
        return autorizacion.evaluar(f.user(), SolicitudAcceso.recurso(
                tipo == TipoRecurso.SOURCE ? "m1.sources.read" : "m1.assets.read", f.project(), tipo, resource));
    }

    private void alcance(Fixture f, String tipo, UUID resource) {
        jdbc.sql("DELETE FROM autorizacion.alcance_asignacion WHERE role_assignment_id=:id")
                .param("id", f.assignment()).update();
        insertarAlcance(f, tipo, resource);
    }

    private void insertarAlcance(Fixture f, String tipo, UUID resource) {
        jdbc.sql("""
                INSERT INTO autorizacion.alcance_asignacion
                    (id,role_assignment_id,project_id,resource_scope,source_id,asset_id)
                VALUES(:id,:assignment,:project,:scope,:source,:asset)
                """).param("id", UUID.randomUUID()).param("assignment", f.assignment())
                .param("project", f.project()).param("scope", tipo)
                .param("source", "SOURCE".equals(tipo) ? resource : null)
                .param("asset", "ASSET".equals(tipo) ? resource : null).update();
    }

    private Fixture fixture(String role) {
        UUID user = UUID.randomUUID(), org = UUID.randomUUID(), project = UUID.randomUUID();
        UUID om = UUID.randomUUID(), pm = UUID.randomUUID(), assignment = UUID.randomUUID();
        jdbc.sql("INSERT INTO identidad.usuario(id,email,display_name) VALUES(:id,:email,'Integración M1')")
                .param("id", user).param("email", user+"@example.test").update();
        jdbc.sql("INSERT INTO contexto.organizacion(id,name) VALUES(:id,'Integración M1')").param("id", org).update();
        jdbc.sql("INSERT INTO contexto.proyecto(id,organization_id,name) VALUES(:id,:org,'Integración M1')")
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
        Fixture f = new Fixture(user, project, assignment);
        insertarAlcance(f, "PROJECT", null);
        return f;
    }

    private UUID fuente(Fixture f) {
        UUID id = UUID.randomUUID();
        catalogo.registrarFuente(new FuenteDatos(id, f.project(), "Origen "+id, FuenteDatos.Tipo.POSTGRESQL,
                FuenteDatos.Estado.ENABLED, Map.of("host", "localhost", "port", "5432", "database", "fixture"),
                "referencia-fixture", f.user(), NOW, NOW, 0));
        return id;
    }

    private UUID activo(Fixture f, UUID source) {
        UUID id = UUID.randomUUID();
        var asset = new ActivoDatos(id, f.project(), source, "clientes", ActivoDatos.Tipo.TABLE,
                "public", "clientes_"+id, ActivoDatos.Estado.ACTIVE, ActivoDatos.Origen.DECLARED,
                null, List.of(), UUID.randomUUID(), NOW, NOW, 0);
        var field = new CampoDatos(UUID.randomUUID(), f.project(), id, "id", "id", 1, "integer",
                CampoDatos.TipoLogico.INTEGER, false, null, List.of(), CampoDatos.Estado.ACTIVE);
        catalogo.registrarActivo(new EstadoCatalogo(asset, List.of(field)), f.user(), UUID.randomUUID());
        return id;
    }

    private record Fixture(UUID user, UUID project, UUID assignment) { }
}
