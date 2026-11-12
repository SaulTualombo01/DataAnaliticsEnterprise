package org.dataanaliticsent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Entrada compatible con mainClass del pom existente, sin sustituir el archivo original. */
@SpringBootApplication(exclude = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class DataAnaliticsEnterprise {
    public static void main(String[] args) {
        SpringApplication.run(DataAnaliticsEnterprise.class, args);
    }
}
