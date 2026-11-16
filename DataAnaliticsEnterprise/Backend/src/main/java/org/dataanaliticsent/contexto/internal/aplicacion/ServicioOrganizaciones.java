package org.dataanaliticsent.contexto.internal.aplicacion;

import java.util.List;
import java.util.UUID;
import org.dataanaliticsent.contexto.internal.dominio.Estado;
import org.dataanaliticsent.contexto.internal.dominio.NombreContexto;
import org.dataanaliticsent.contexto.internal.dominio.Organizacion;
import org.dataanaliticsent.contexto.internal.dominio.Pagina;
import org.dataanaliticsent.contexto.internal.dominio.RecursoNoEncontradoException;
import org.dataanaliticsent.contexto.internal.dominio.RepositorioContexto;
import org.springframework.stereotype.Service;

@Service
public class ServicioOrganizaciones {
    static final String LEER = "tc.organizations.read";
    static final String CREAR = "tc.organizations.create";
    static final String ACTUALIZAR = "tc.organizations.update";
    static final String ACTIVAR = "tc.organizations.activate";
    static final String DESACTIVAR = "tc.organizations.deactivate";

    private final GuardiaPlataforma guardia;
    private final RepositorioContexto repositorio;

    public ServicioOrganizaciones(GuardiaPlataforma guardia, RepositorioContexto repositorio) {
        this.guardia = guardia;
        this.repositorio = repositorio;
    }

    public List<Organizacion> listar(Pagina pagina) {
        guardia.exigir(LEER);
        return repositorio.organizaciones(pagina);
    }

    public Organizacion obtener(UUID id) {
        guardia.exigir(LEER);
        return repositorio.organizacion(id).orElseThrow(RecursoNoEncontradoException::new);
    }

    public Organizacion crear(String nombre) {
        guardia.exigir(CREAR);
        return repositorio.crearOrganizacion(UUID.randomUUID(), NombreContexto.normalizar(nombre));
    }

    public Organizacion renombrar(UUID id, String nombre) {
        guardia.exigir(ACTUALIZAR);
        return repositorio.renombrarOrganizacion(id, NombreContexto.normalizar(nombre))
                .orElseThrow(RecursoNoEncontradoException::new);
    }

    public Organizacion activar(UUID id) {
        guardia.exigir(ACTIVAR);
        return repositorio.cambiarEstadoOrganizacion(id, Estado.ACTIVE).orElseThrow(RecursoNoEncontradoException::new);
    }

    public Organizacion desactivar(UUID id) {
        guardia.exigir(DESACTIVAR);
        return repositorio.cambiarEstadoOrganizacion(id, Estado.INACTIVE).orElseThrow(RecursoNoEncontradoException::new);
    }
}
