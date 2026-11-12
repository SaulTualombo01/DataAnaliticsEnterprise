package org.dataanaliticsent.Calidad.Infraestructura.Configuracion;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.*;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.UUID;
import org.dataanaliticsent.Calidad.Dominio.CanonicalUuid;

@Configuration
public class ApiConfiguration implements WebMvcConfigurer {
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, UUID.class, CanonicalUuid::parse);
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer strictUuid() {
        return builder -> builder.deserializerByType(UUID.class, new JsonDeserializer<UUID>() {
            @Override
            public UUID deserialize(JsonParser parser, DeserializationContext context) throws java.io.IOException {
                try {
                    return CanonicalUuid.parse(parser.getValueAsString());
                } catch (IllegalArgumentException ex) {
                    throw InvalidFormatException.from(parser, "El identificador debe ser un UUID en minúsculas.", parser.getValueAsString(), UUID.class);
                }
            }
        });
    }
}
