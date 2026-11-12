package org.dataanaliticsent.catalogo.internal.aplicacion;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.dataanaliticsent.autorizacion.AutorizacionApi;
import org.dataanaliticsent.autorizacion.SolicitudAcceso;
import org.dataanaliticsent.catalogo.ConflictoCatalogoException;
import org.dataanaliticsent.catalogo.internal.dominio.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transacción corta de plataforma, posterior a cerrar la conexión del origen. */
@Service
public class PublicadorDescubrimiento {
    private final AutorizacionApi autorizacion;
    private final RepositorioCatalogo datos;
    public PublicadorDescubrimiento(AutorizacionApi autorizacion, RepositorioCatalogo datos) {
        this.autorizacion=autorizacion; this.datos=datos;
    }
    @Transactional
    public void publicar(UUID actor, FuenteDatos fuente, List<String> schemas, List<EstadoCatalogo> estados,
            UUID requestId, Instant instante) {
        autorizacion.exigir(actor,SolicitudAcceso.recurso("m1.discovery.execute",fuente.projectId(),
                SolicitudAcceso.TipoRecurso.SOURCE,fuente.id()));
        try { datos.registrarDescubrimiento(fuente,schemas,estados,actor,requestId,instante); }
        catch (DataIntegrityViolationException conflicto) { throw new ConflictoCatalogoException(); }
    }
}
