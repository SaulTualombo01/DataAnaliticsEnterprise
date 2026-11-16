package org.dataanaliticsent.contexto.internal.aplicacion;

import java.util.List;
import java.util.UUID;
import org.dataanaliticsent.contexto.internal.dominio.ConflictoContextoException;
import org.dataanaliticsent.contexto.internal.dominio.Estado;
import org.dataanaliticsent.contexto.internal.dominio.Membresia;
import org.dataanaliticsent.contexto.internal.dominio.Organizacion;
import org.dataanaliticsent.contexto.internal.dominio.Pagina;
import org.dataanaliticsent.contexto.internal.dominio.Proyecto;
import org.dataanaliticsent.contexto.internal.dominio.RecursoNoEncontradoException;
import org.dataanaliticsent.contexto.internal.dominio.RepositorioContexto;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Vincula cuentas a proyectos. La asignación de roles pertenece al módulo de autorización. */
@Service
public class ServicioMembresias {
    static final String LEER = "tc.memberships.read";
    static final String ASIGNAR = "tc.memberships.assign";
    static final String REVOCAR = "tc.memberships.revoke";

    private final GuardiaPlataforma guardia;
    private final RepositorioContexto repositorio;

    public ServicioMembresias(GuardiaPlataforma guardia, RepositorioContexto repositorio) {
        this.guardia = guardia;
        this.repositorio = repositorio;
    }

    public List<Membresia> listar(UUID proyectoId, Pagina pagina) {
        guardia.exigir(LEER);
        proyecto(proyectoId);
        return repositorio.membresias(proyectoId, pagina);
    }

    public Membresia asignar(UUID proyectoId, UUID usuarioId) {
        UUID actorId = guardia.exigir(ASIGNAR);
        if (usuarioId == null) {
            throw new IllegalArgumentException("user_id es obligatorio");
        }
        Proyecto proyecto = proyecto(proyectoId);
        Organizacion organizacion = repositorio.organizacion(proyecto.organizationId())
                .orElseThrow(RecursoNoEncontradoException::new);
        if (proyecto.status() != Estado.ACTIVE || organizacion.status() != Estado.ACTIVE) {
            throw new ConflictoContextoException("El proyecto o su organización no están activos");
        }
        try {
            return repositorio.vincular(usuarioId, proyecto, actorId);
        } catch (DataIntegrityViolationException referenciaInvalida) {
            throw new IllegalArgumentException("user_id no corresponde a una cuenta existente");
        }
    }

    public Membresia revocar(UUID proyectoId, UUID usuarioId) {
        guardia.exigir(REVOCAR);
        return repositorio.revocar(usuarioId, proyectoId).orElseThrow(RecursoNoEncontradoException::new);
    }

    private Proyecto proyecto(UUID id) {
        return repositorio.proyecto(id).orElseThrow(RecursoNoEncontradoException::new);
    }
}
