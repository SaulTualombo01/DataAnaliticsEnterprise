package org.dataanaliticsent.Gobierno;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.dataanaliticsent.Gobierno.auditoria.internal.presentacion.AuditoriaController;
import org.dataanaliticsent.Gobierno.cumplimiento.internal.presentacion.CumplimientoController;
import org.dataanaliticsent.Gobierno.fichas.internal.presentacion.FichasController;
import org.dataanaliticsent.Gobierno.historial.internal.presentacion.HistorialController;
import org.dataanaliticsent.Gobierno.linaje.internal.presentacion.LinajeController;
import org.dataanaliticsent.Gobierno.politicas.internal.presentacion.PoliticasController;
import org.dataanaliticsent.Gobierno.versionado.internal.presentacion.VersionesController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import tools.jackson.databind.json.JsonMapper;

/**
 * El contrato publicado y el código no pueden contradecirse: cada ruta de los controladores de M3
 * está en gobierno-http.openapi.json y viceversa, con el mismo método HTTP.
 */
class ContratoHttpTests {
    private static final Path CONTRATO = Path.of("..", "docs", "contratos", "m3", "gobierno-http.openapi.json");
    private static final List<Class<?>> CONTROLADORES = List.of(PoliticasController.class, FichasController.class,
            CumplimientoController.class, LinajeController.class, AuditoriaController.class, VersionesController.class,
            HistorialController.class);

    @Test
    @SuppressWarnings("unchecked")
    void lasRutasDelCodigoSonExactamenteLasDelContrato() throws Exception {
        assertTrue(Files.exists(CONTRATO), "No se encontró " + CONTRATO.toAbsolutePath());
        Map<String, Object> spec = JsonMapper.builder().build().readValue(Files.readString(CONTRATO), Map.class);
        Set<String> contrato = new TreeSet<>();
        ((Map<String, Map<String, Object>>) spec.get("paths")).forEach((ruta, operaciones) ->
                operaciones.keySet().forEach(metodo -> contrato.add(metodo.toUpperCase() + " " + ruta)));

        assertEquals(contrato, rutasDelCodigo());
    }

    private static Set<String> rutasDelCodigo() {
        Set<String> rutas = new TreeSet<>();
        for (Class<?> c : CONTROLADORES) {
            String base = c.getAnnotation(RequestMapping.class).value()[0];
            for (Method m : c.getDeclaredMethods()) {
                agregar(rutas, base, "GET", m.getAnnotation(GetMapping.class) == null ? null : rutas(m.getAnnotation(GetMapping.class).path(), m.getAnnotation(GetMapping.class).value()));
                agregar(rutas, base, "POST", m.getAnnotation(PostMapping.class) == null ? null : rutas(m.getAnnotation(PostMapping.class).path(), m.getAnnotation(PostMapping.class).value()));
                agregar(rutas, base, "PUT", m.getAnnotation(PutMapping.class) == null ? null : rutas(m.getAnnotation(PutMapping.class).path(), m.getAnnotation(PutMapping.class).value()));
                agregar(rutas, base, "PATCH", m.getAnnotation(PatchMapping.class) == null ? null : rutas(m.getAnnotation(PatchMapping.class).path(), m.getAnnotation(PatchMapping.class).value()));
                agregar(rutas, base, "DELETE", m.getAnnotation(DeleteMapping.class) == null ? null : rutas(m.getAnnotation(DeleteMapping.class).path(), m.getAnnotation(DeleteMapping.class).value()));
            }
        }
        return rutas;
    }

    private static String[] rutas(String[] path, String[] value) {
        String[] r = path.length > 0 ? path : value;
        return r.length > 0 ? r : new String[] {""};
    }

    private static void agregar(Set<String> rutas, String base, String metodo, String[] subrutas) {
        if (subrutas == null) return;
        for (String s : subrutas) rutas.add(metodo + " " + base + s);
    }
}
