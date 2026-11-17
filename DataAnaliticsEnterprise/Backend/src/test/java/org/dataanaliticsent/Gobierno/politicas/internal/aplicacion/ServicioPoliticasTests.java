package org.dataanaliticsent.Gobierno.politicas.internal.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Resultado;
import org.dataanaliticsent.Gobierno.auditoria.internal.aplicacion.ServicioAuditoria;
import org.dataanaliticsent.Gobierno.comun.AccesoDenegadoGobiernoException;
import org.dataanaliticsent.Gobierno.comun.DoblesGobierno;
import org.dataanaliticsent.Gobierno.comun.Pagina;
import org.dataanaliticsent.Gobierno.comun.PermisosGobierno;
import org.dataanaliticsent.Gobierno.comun.RecursoNoEncontradoGobiernoException;
import org.dataanaliticsent.Gobierno.politicas.ConflictoPoliticaException;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.CambiosPolitica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.CategoriaPolitica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.EstadoPolitica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.FiltroPoliticas;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.NuevaPolitica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.Politica;
import org.dataanaliticsent.Gobierno.politicas.PoliticasApi.PoliticaAplicable;
import org.dataanaliticsent.Gobierno.politicas.internal.dominio.RepositorioPoliticas;
import org.dataanaliticsent.Gobierno.versionado.internal.aplicacion.ServicioVersionado;
import org.junit.jupiter.api.Test;

class ServicioPoliticasTests {
    private static final UUID P = DoblesGobierno.PROYECTO;
    private static final UUID RESPONSABLE = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    private static final UUID CLIENTES = UUID.fromString("00000000-0000-4000-8000-0000000000d1");

    private final DoblesGobierno.Auditoria eventos = new DoblesGobierno.Auditoria();
    private final DoblesGobierno.Versiones versiones = new DoblesGobierno.Versiones();
    private final DoblesGobierno.Autorizacion autorizacion = new DoblesGobierno.Autorizacion();
    private final DoblesGobierno.Catalogo catalogo = new DoblesGobierno.Catalogo().registrar(P, CLIENTES);
    private final EnMemoria politicas = new EnMemoria();
    private final ServicioPoliticas servicio = new ServicioPoliticas(politicas,
            new ServicioAuditoria(eventos, DoblesGobierno.identidad(), autorizacion),
            new ServicioVersionado(versiones, DoblesGobierno.identidad(), autorizacion),
            DoblesGobierno.identidad(), autorizacion, catalogo, DoblesGobierno.sinTransaccion(), DoblesGobierno.RELOJ);

    private void concederTodo() {
        for (String permiso : List.of(PermisosGobierno.POLITICAS_LEER, PermisosGobierno.POLITICAS_CREAR,
                PermisosGobierno.POLITICAS_ACTUALIZAR, PermisosGobierno.POLITICAS_CICLO_VIDA, PermisosGobierno.POLITICAS_ACTIVOS)) {
            autorizacion.conceder(permiso, P);
        }
    }

    private Politica crearPii() {
        return servicio.crear(new NuevaPolitica(P, "  Datos personales protegidos ", "Clientes con PII",
                CategoriaPolitica.PRIVACIDAD, RESPONSABLE));
    }

    @Test
    void crearDejaBorradorAuditadoYVersionado() {
        concederTodo();
        Politica p = crearPii();

        assertEquals("Datos personales protegidos", p.name());
        assertEquals(EstadoPolitica.DRAFT, p.status());
        assertEquals(0, p.revision());
        assertEquals(DoblesGobierno.ACTOR, p.createdBy());
        assertEquals("m3.policies.create", eventos.eventos.getFirst().action());
        assertEquals(1, versiones.versiones.size());
        assertEquals(p.currentVersionId(), versiones.versiones.getFirst().id());
        assertEquals(eventos.eventos.getFirst().id(), versiones.versiones.getFirst().auditEventId());
    }

    @Test
    void crearSinPermisoSeDeniegaYQuedaAuditado() {
        assertThrows(AccesoDenegadoGobiernoException.class, this::crearPii);

        assertTrue(politicas.datos.isEmpty());
        assertEquals(1, eventos.eventos.size());
        assertEquals(Resultado.DENIED, eventos.eventos.getFirst().outcome());
        assertTrue(versiones.versiones.isEmpty());
    }

    @Test
    void validaNombreYMotivoAntesDeGuardar() {
        concederTodo();
        assertThrows(IllegalArgumentException.class,
                () -> servicio.crear(new NuevaPolitica(P, "  ", null, CategoriaPolitica.OTRA, RESPONSABLE)));
        Politica p = crearPii();
        assertThrows(IllegalArgumentException.class,
                () -> servicio.actualizar(P, p.id(), 0, new CambiosPolitica("Otro", null, null, null), " "));
        assertEquals(1, politicas.datos.size());
    }

    @Test
    void actualizarConCambioSubeRevisionYSinCambioNo() {
        concederTodo();
        Politica p = crearPii();

        Politica v2 = servicio.actualizar(P, p.id(), 0, new CambiosPolitica(null, "Nueva descripción", null, null), "Aclarar");
        Politica igual = servicio.actualizar(P, p.id(), 1, new CambiosPolitica(null, "Nueva descripción", null, null), "Repetir");

        assertEquals(1, v2.revision());
        assertEquals(p.currentVersionId(), versiones.versiones.get(1).previousVersionId());
        assertEquals(v2, igual);
        assertEquals(2, versiones.versiones.size());
        assertEquals(2, eventos.eventos.size());
    }

    @Test
    void unaRevisionObsoletaNoSobrescribe() {
        concederTodo();
        Politica p = crearPii();
        servicio.actualizar(P, p.id(), 0, new CambiosPolitica("Nombre A", null, null, null), "Primer cambio");

        assertThrows(ConflictoPoliticaException.class,
                () -> servicio.actualizar(P, p.id(), 0, new CambiosPolitica("Nombre B", null, null, null), "Cambio viejo"));
        assertEquals("Nombre A", politicas.buscar(P, p.id()).orElseThrow().name());
    }

    @Test
    void elNombreEsUnicoPorProyecto() {
        concederTodo();
        crearPii();
        assertThrows(ConflictoPoliticaException.class,
                () -> servicio.crear(new NuevaPolitica(P, "DATOS PERSONALES PROTEGIDOS", null, CategoriaPolitica.OTRA, RESPONSABLE)));
    }

    @Test
    void cicloDeVidaRespetaLasTransiciones() {
        concederTodo();
        Politica p = crearPii();

        assertThrows(ConflictoPoliticaException.class,
                () -> servicio.cambiarEstado(P, p.id(), 0, EstadoPolitica.INACTIVE, "Saltar a inactiva"));
        Politica activa = servicio.cambiarEstado(P, p.id(), 0, EstadoPolitica.ACTIVE, "Aprobada");
        Politica retirada = servicio.cambiarEstado(P, p.id(), 1, EstadoPolitica.RETIRED, "Ya no aplica");

        assertEquals(EstadoPolitica.ACTIVE, activa.status());
        assertEquals(EstadoPolitica.RETIRED, retirada.status());
        assertThrows(ConflictoPoliticaException.class,
                () -> servicio.cambiarEstado(P, p.id(), 2, EstadoPolitica.ACTIVE, "Revivir"));
        assertThrows(ConflictoPoliticaException.class,
                () -> servicio.actualizar(P, p.id(), 2, new CambiosPolitica("X", null, null, null), "Editar retirada"));
        assertEquals("{\"from\":\"DRAFT\",\"to\":\"ACTIVE\"}", eventos.eventos.get(1).safeDetailsJson());
    }

    @Test
    void asociarActivoDelCatalogoEsIdempotenteYSeVersiona() {
        concederTodo();
        Politica p = crearPii();

        Politica con = servicio.asociarActivo(P, p.id(), 0, CLIENTES, "Clientes tiene PII");
        Politica otraVez = servicio.asociarActivo(P, p.id(), 1, CLIENTES, "Repetir");

        assertEquals(List.of(CLIENTES), con.assetIds());
        assertEquals(con, otraVez);
        assertEquals(2, versiones.versiones.size());
        assertTrue(versiones.versiones.get(1).estadoJson().contains(CLIENTES.toString()));
        assertThrows(RecursoNoEncontradoGobiernoException.class,
                () -> servicio.asociarActivo(P, p.id(), 1, UUID.randomUUID(), "Activo inventado"));
    }

    @Test
    void politicasDeActivoIndicaCualesAplican() {
        concederTodo();
        Politica borrador = crearPii();
        Politica activa = servicio.crear(new NuevaPolitica(P, "Retención 5 años", null, CategoriaPolitica.RETENCION, RESPONSABLE));
        servicio.asociarActivo(P, borrador.id(), 0, CLIENTES, "Asociar");
        servicio.asociarActivo(P, activa.id(), 0, CLIENTES, "Asociar");
        servicio.cambiarEstado(P, activa.id(), 1, EstadoPolitica.ACTIVE, "Aprobada");

        List<PoliticaAplicable> aplicables = servicio.politicasDeActivo(P, CLIENTES);

        assertEquals(2, aplicables.size());
        assertFalse(aplicables.get(0).aplicable());
        assertTrue(aplicables.get(1).aplicable());

        Politica sin = servicio.retirarActivo(P, borrador.id(), 1, CLIENTES, "Ya no aplica");
        assertTrue(sin.assetIds().isEmpty());
        assertEquals(1, servicio.politicasDeActivo(P, CLIENTES).size());
    }

    @Test
    void buscarFiltraYPagina() {
        concederTodo();
        crearPii();
        servicio.crear(new NuevaPolitica(P, "Retención 5 años", null, CategoriaPolitica.RETENCION, RESPONSABLE));

        Pagina<Politica> todas = servicio.buscar(P, FiltroPoliticas.ninguno(), 1, 1);
        Pagina<Politica> privacidad = servicio.buscar(P,
                new FiltroPoliticas(null, null, CategoriaPolitica.PRIVACIDAD, null, null), 1, 25);

        assertEquals(1, todas.items().size());
        assertTrue(todas.hasNext());
        assertEquals(1, privacidad.items().size());
        assertThrows(AccesoDenegadoGobiernoException.class,
                () -> servicio.buscar(UUID.randomUUID(), FiltroPoliticas.ninguno(), 1, 25));
    }

    /** Repositorio en memoria con las mismas reglas de unicidad y revisión que PostgreSQL. */
    static final class EnMemoria implements RepositorioPoliticas {
        final List<Politica> datos = new ArrayList<>();

        @Override public Optional<Politica> buscar(UUID projectId, UUID policyId) {
            return datos.stream().filter(p -> p.projectId().equals(projectId) && p.id().equals(policyId)).findFirst();
        }
        @Override public void insertar(Politica p) { exigirNombreLibre(p); datos.add(p); }
        @Override public void actualizar(Politica p, long revisionLeida) {
            Politica actual = buscar(p.projectId(), p.id()).orElseThrow();
            if (actual.revision() != revisionLeida) throw new ConflictoPoliticaException("revisión");
            exigirNombreLibre(p);
            datos.set(datos.indexOf(actual), p);
        }
        @Override public void asociarActivo(UUID pr, UUID po, UUID a, UUID actor, Instant t) { }
        @Override public void retirarActivo(UUID pr, UUID po, UUID a, UUID actor, Instant t) { }
        @Override public List<Politica> listar(UUID projectId, FiltroPoliticas f, int limite, int desplazamiento) {
            return datos.stream().filter(p -> p.projectId().equals(projectId))
                    .filter(p -> f.category() == null || p.category() == f.category())
                    .filter(p -> f.status() == null || p.status() == f.status())
                    .sorted(Comparator.comparing(Politica::createdAt).thenComparing(Politica::id))
                    .skip(desplazamiento).limit(limite).toList();
        }
        @Override public List<Politica> deActivo(UUID projectId, UUID assetId) {
            return datos.stream().filter(p -> p.projectId().equals(projectId) && p.assetIds().contains(assetId))
                    .sorted(Comparator.comparing(Politica::createdAt).thenComparing(Politica::name)).toList();
        }
        private void exigirNombreLibre(Politica p) {
            boolean repetido = datos.stream().anyMatch(o -> !o.id().equals(p.id()) && o.projectId().equals(p.projectId())
                    && o.status() != EstadoPolitica.RETIRED && o.name().equalsIgnoreCase(p.name()));
            if (repetido && p.status() != EstadoPolitica.RETIRED) throw new ConflictoPoliticaException("nombre");
        }
    }
}
