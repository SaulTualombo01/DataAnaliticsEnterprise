package org.dataanaliticsent.Calidad.Infraestructura.Seguridad;

import org.dataanaliticsent.Calidad.Presentacion.Errores.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.*;

/**
 * Límite acotado para el monolito de una instancia. No se confía en X-Forwarded-For.
 */
@Component
public class LoginRateLimiter {
    private record Window(long start, int count) {
    }

    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    private Window anonymous = new Window(0, 0);

    public LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized void check(String address) {
        checkWindow("login:" + address, 10);
    }

    public synchronized void checkAnonymousSession(String address) {
        long now = clock.millis();
        if (now - anonymous.start >= 60000) anonymous = new Window(now, 0);
        if (anonymous.count >= 1000) throw limited();
        checkWindow("anonymous:" + address, 20);
        anonymous = new Window(anonymous.start, anonymous.count + 1);
    }

    private ApiException limited() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Demasiados intentos.", "Espera un minuto antes de volver a intentar.");
    }

    private void checkWindow(String address, int maximum) {
        long now = clock.millis();
        windows.entrySet().removeIf(e -> now - e.getValue().start >= 60000);
        Window window = windows.getOrDefault(address, new Window(now, 0));
        if (window.count >= maximum || !windows.containsKey(address) && windows.size() >= 10000)
            throw limited();
        windows.put(address, new Window(window.start, window.count + 1));
    }
}
