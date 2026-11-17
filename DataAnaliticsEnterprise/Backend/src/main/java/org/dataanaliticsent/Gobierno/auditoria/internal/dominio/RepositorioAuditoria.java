package org.dataanaliticsent.Gobierno.auditoria.internal.dominio;

import java.util.List;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Evento;
import org.dataanaliticsent.Gobierno.auditoria.AuditoriaApi.Filtro;

public interface RepositorioAuditoria {
    void guardar(Evento evento);

    /** Devuelve hasta limite eventos ordenados por occurred_at y id descendentes. */
    List<Evento> buscar(Filtro filtro, int limite, int desplazamiento);
}
