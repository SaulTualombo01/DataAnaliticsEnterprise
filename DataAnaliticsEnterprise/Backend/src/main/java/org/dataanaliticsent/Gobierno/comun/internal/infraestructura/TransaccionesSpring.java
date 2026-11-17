package org.dataanaliticsent.Gobierno.comun.internal.infraestructura;

import java.util.function.Supplier;
import org.dataanaliticsent.Gobierno.comun.Transacciones;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class TransaccionesSpring implements Transacciones {
    private final TransactionTemplate plantilla;

    TransaccionesSpring(PlatformTransactionManager gestor) { this.plantilla = new TransactionTemplate(gestor); }

    @Override
    public <T> T ejecutar(Supplier<T> bloque) {
        return plantilla.execute(estado -> bloque.get());
    }
}
