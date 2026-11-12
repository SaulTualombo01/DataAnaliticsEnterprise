package org.dataanaliticsent.Calidad.Presentacion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.util.Map;

@Tag(name = "Estado del servicio")
@RestController
public class HealthController {
    private final DataSource dataSource;
    private volatile boolean ready;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void started() {
        ready = true;
    }

    @GetMapping("/api/v1/health")
    @Operation(
            summary = "Comprobar salud",
            description = "200 UP si PostgreSQL y aplicación están disponibles; 503 mientras inicia o si falla la dependencia.")
    public ResponseEntity<?> health() {
        if (!ready) return ResponseEntity.status(503).body(Map.of("status", "STARTING"));
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("SELECT 1")) {
            statement.setQueryTimeout(2);
            statement.execute();
            return ResponseEntity.ok(Map.of("status", "UP"));
        } catch (Exception ex) {
            return ResponseEntity.status(503).body(Map.of("status", "UNAVAILABLE"));
        }
    }
}
