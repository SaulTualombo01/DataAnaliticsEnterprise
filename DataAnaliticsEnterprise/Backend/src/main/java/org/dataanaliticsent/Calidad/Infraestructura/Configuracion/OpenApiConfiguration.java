package org.dataanaliticsent.Calidad.Infraestructura.Configuracion;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.core.annotation.AnnotatedElementUtils;
import java.util.Arrays;
import java.util.List;
import org.dataanaliticsent.Calidad.Presentacion.Errores.ApiProblem;
import org.dataanaliticsent.Calidad.Presentacion.Utilidades.PageQuery;
import io.swagger.v3.oas.models.media.IntegerSchema;

@Configuration
public class OpenApiConfiguration {
    @Bean
    public io.swagger.v3.core.jackson.ModelResolver applicationModelResolver(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        return new io.swagger.v3.core.jackson.ModelResolver(mapper);
    }

    @Bean
    public OpenAPI qualityOpenApi(@Value("${server.servlet.session.cookie.name:__Host-M2SESSION}") String cookie) {
        Components components = new Components().addSecuritySchemes("session", new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name(cookie));
        io.swagger.v3.core.converter.ModelConverters.getInstance().readAll(ApiProblem.class).forEach(components::addSchemas);
        return new OpenAPI().info(new Info().title("DataAnaliticsEnterprise · Calidad M2").version("1.0")
                .description("API de archivos CSV, reglas versionadas, evaluaciones e incidencias. "
                        + "Inicia sesión en la aplicación desde este mismo origen para probar consultas. "
                        + "Antes de cada operación de escritura consulta GET /api/v1/session/csrf y copia data.token "
                        + "al encabezado X-CSRF-TOKEN. El inicio/cierre de sesión renueva ese token. "
                        + "If-Match protege revisiones e Idempotency-Key evita repetir evaluaciones. "
                        + "COMPLETED no implica que el activo esté aprobado."))
                .components(components)
                .addSecurityItem(new SecurityRequirement().addList("session"));
    }

    @Bean
    public OperationCustomizer apiOperationDocumentation() {
        return (operation, handler) -> {
            if (isPublic(handler)) operation.setSecurity(List.of());
            var mapping = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), RequestMapping.class);
            if (mapping != null && Arrays.stream(mapping.method()).anyMatch(method ->
                    method != RequestMethod.GET && method != RequestMethod.HEAD && method != RequestMethod.OPTIONS)) {
                operation.addParametersItem(new HeaderParameter().name("X-CSRF-TOKEN").required(true)
                        .description("data.token de GET /api/v1/session/csrf; renovar después de iniciar/cerrar sesión.")
                        .schema(new StringSchema()));
            }
            if (operation.getParameters() != null) operation.getParameters().forEach(parameter -> {
                switch (parameter.getName()) {
                    case "If-Match" -> parameter.required(true).description("Revisión ETag entre comillas, por ejemplo \"0\". 428 si falta; 412 si cambió.");
                    case "Idempotency-Key" -> parameter.required(true).description("UUID canónico en minúsculas. Reutilizar solo con el mismo cuerpo.").schema(new StringSchema().format("uuid"));
                    case "page" -> parameter.schema(pageSchema(1, null));
                    case "page_size" -> parameter.schema(pageSchema(25, PageQuery.MAX_SIZE));
                    case "date_from", "date_to" -> parameter.description("Día inclusivo en America/Guayaquil; formato yyyy-MM-dd.").schema(new StringSchema().format("date"));
                    default -> { }
                }
            });
            operation.getResponses().addApiResponse("default", new io.swagger.v3.oas.models.responses.ApiResponse()
                    .description("Problem Details: 401 sesión; 403 permiso/CSRF; 404 recurso; 409 conflicto; 412 revisión o versión cambiada; 422 validación; 428 falta If-Match; 429 límite; 503 dependencia; 500 fallo interno.")
                    .content(new io.swagger.v3.oas.models.media.Content().addMediaType("application/problem+json",
                            new io.swagger.v3.oas.models.media.MediaType().schema(
                                    new io.swagger.v3.oas.models.media.Schema<>().$ref("#/components/schemas/ApiProblem")))));
            return operation;
        };
    }

    private boolean isPublic(HandlerMethod handler) {
        String controller = handler.getBeanType().getSimpleName();
        return controller.equals("HealthController") || controller.equals("IdentityController")
                && !List.of("me", "logout").contains(handler.getMethod().getName());
    }

    private IntegerSchema pageSchema(int defaultValue, Integer maximum) {
        var schema = new IntegerSchema();
        schema.setMinimum(java.math.BigDecimal.ONE);
        schema.setDefault(defaultValue);
        if (maximum != null) schema.setMaximum(java.math.BigDecimal.valueOf(maximum));
        return schema;
    }
}
