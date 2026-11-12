package org.dataanaliticsent.Calidad.Infraestructura.Configuracion;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class ProjectTransaction {

    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;

    public ProjectTransaction(TransactionTemplate transactionTemplate, EntityManager entityManager) {
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate);
        this.entityManager = Objects.requireNonNull(entityManager);
    }

    public <T> T execute(UUID userId, UUID projectId, Supplier<T> action) {
        Objects.requireNonNull(userId, "userId es obligatorio para el contexto RLS");
        Objects.requireNonNull(projectId, "projectId es obligatorio para el contexto RLS");
        Objects.requireNonNull(action, "action es obligatorio");

        return transactionTemplate.execute(status -> {
            entityManager.createNativeQuery(
                            "SELECT set_config('app.user_id', :userId, true), set_config('app.project_id', :projectId, true)"
                    )
                    .setParameter("userId", userId.toString())
                    .setParameter("projectId", projectId.toString())
                    .getSingleResult();

            return action.get();
        });
    }

    public void run(UUID userId, UUID projectId, Runnable action) {
        execute(userId, projectId, () -> {
            action.run();
            return null;
        });
    }
}
