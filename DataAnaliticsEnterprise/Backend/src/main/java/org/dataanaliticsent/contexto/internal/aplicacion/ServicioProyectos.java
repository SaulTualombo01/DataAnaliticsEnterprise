package org.dataanaliticsent.contexto.internal.aplicacion;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.dataanaliticsent.contexto.internal.dominio.ConflictoContextoException;
import org.dataanaliticsent.contexto.internal.dominio.Estado;
import org.dataanaliticsent.contexto.internal.dominio.NombreContexto;
import org.dataanaliticsent.contexto.internal.dominio.Organizacion;
import org.dataanaliticsent.contexto.internal.dominio.Pagina;
import org.dataanaliticsent.contexto.internal.dominio.Proyecto;
import org.dataanaliticsent.contexto.internal.dominio.RecursoNoEncontradoException;
import org.dataanaliticsent.contexto.internal.dominio.RepositorioContexto;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class ServicioProyectos {
    static final String LEER = "tc.projects.read";
    static final String CREAR = "tc.projects.create";
    static final String ACTUALIZAR = "tc.projects.update";
    static final String ACTIVAR = "tc.projects.activate";
    static final String DESACTIVAR = "tc.projects.deactivate";

    private final GuardiaPlataforma guardia;
    private final RepositorioContexto repositorio;

    public ServicioProyectos(GuardiaPlataforma guardia, RepositorioContexto repositorio) {
        this.guardia = guardia;
        this.repositorio = repositorio;
    }

    public List<Proyecto> listar(UUID organizacionId, Pagina pagina) {
        guardia.exigir(LEER);
        organizacion(organizacionId);
        return repositorio.proyectos(organizacionId, pagina);
    }

    public Proyecto obtener(UUID id) {
        guardia.exigir(LEER);
        return proyecto(id);
    }

    public Proyecto crear(UUID organizacionId, String nombre) {
        guardia.exigir(CREAR);
        String limpio = NombreContexto.normalizar(nombre);
        exigirActiva(organizacion(organizacionId));
        return sinNombreDuplicado(() -> repositorio.crearProyecto(UUID.randomUUID(), organizacionId, limpio));
    }

    public Proyecto renombrar(UUID id, String nombre) {
        guardia.exigir(ACTUALIZAR);
        String limpio = NombreContexto.normalizar(nombre);
        return sinNombreDuplicado(() -> repositorio.renombrarProyecto(id, limpio)
                .orElseThrow(RecursoNoEncontradoException::new));
    }

    /** Un proyecto solo se reactiva dentro de una organización activa. */
    public Proyecto activar(UUID id) {
        guardia.exigir(ACTIVAR);
        exigirActiva(organizacion(proyecto(id).organizationId()));
        return repositorio.cambiarEstadoProyecto(id, Estado.ACTIVE).orElseThrow(RecursoNoEncontradoException::new);
    }

    public Proyecto desactivar(UUID id) {
        guardia.exigir(DESACTIVAR);
        return repositorio.cambiarEstadoProyecto(id, Estado.INACTIVE).orElseThrow(RecursoNoEncontradoException::new);
    }

    private Proyecto proyecto(UUID id) {
        return repositorio.proyecto(id).orElseThrow(RecursoNoEncontradoException::new);
    }

    private Organizacion organizacion(UUID id) {
        return repositorio.organizacion(id).orElseThrow(RecursoNoEncontradoException::new);
    }

    private static void exigirActiva(Organizacion organizacion) {
        if (organizacion.status() != Estado.ACTIVE) {
            throw new ConflictoContextoException("La organización no está activa");
        }
    }

    private static Proyecto sinNombreDuplicado(Supplier<Proyecto> operacion) {
        try {
            return operacion.get();
        } catch (DuplicateKeyException duplicado) {
            throw new ConflictoContextoException("Ya existe un proyecto con ese nombre en la organización");
        }
    }
}
