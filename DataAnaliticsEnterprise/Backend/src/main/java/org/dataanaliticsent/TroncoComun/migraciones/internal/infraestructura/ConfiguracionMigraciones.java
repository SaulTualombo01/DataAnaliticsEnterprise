package org.dataanaliticsent.TroncoComun.migraciones.internal.infraestructura;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.*;

/** Selección de recursos por historial; no escribe en la base ni repara metadatos. */
@Configuration(proxyBeanMethods=false)
public class ConfiguracionMigraciones {
    public static final String COMUN="classpath:db/migration";
    public static final String M0="classpath:db/migration-m0";
    public static final String M4="classpath:db/migration-m4";

    @Bean FlywayConfigurationCustomizer historialV4() { return ConfiguracionMigraciones::preparar; }

    public static void preparar(FluentConfiguration configuration) {
        if(configuration.getDataSource()==null)throw fallo();
        String schema=configuration.getDefaultSchema();
        if(schema==null) {
            var schemas=configuration.getSchemas();
            schema=schemas.length==0?"public":schemas[0];
        }
        String table=configuration.getTable();
        if(!schema.matches("[a-z_][a-z0-9_]*")||!table.matches("[a-z_][a-z0-9_]*"))throw fallo();
        String selected=M0;
        try(var c=configuration.getDataSource().getConnection()) {
            boolean exists;
            try(var s=c.prepareStatement("""
                SELECT EXISTS(SELECT 1 FROM pg_catalog.pg_class cl JOIN pg_catalog.pg_namespace ns ON ns.oid=cl.relnamespace
                    WHERE ns.nspname=? AND cl.relname=? AND cl.relkind='r')
                """)) {
                s.setString(1,schema);s.setString(2,table);
                try(var rows=s.executeQuery()){rows.next();exists=rows.getBoolean(1);}
            }
            if(exists) {
                // Identificadores de configuración validados arriba; ninguna entrada HTTP.
                try(var s=c.prepareStatement("SELECT script,success FROM \""+schema+"\".\""+table+"\" WHERE version='4'")) {
                    try(var rows=s.executeQuery()) {
                        if(rows.next()) {
                            if(!rows.getBoolean(2))throw fallo();
                            selected=switch(rows.getString(1)) {
                                case "V4__administracion_de_empresa.sql" -> M0;
                                case "V4__permiso_evaluar_kpis_m4.sql" -> M4;
                                default -> throw fallo();
                            };
                            if(rows.next())throw fallo();
                        }
                    }
                }
            }
        } catch(SQLException e){throw fallo();}
        // Evitar una configuración mezclada o que omita recursos históricos sin advertir.
        Set<String> requested=new HashSet<>();
        for(var location:configuration.getLocations())requested.add(location.getDescriptor());
        if(!requested.equals(Set.of(COMUN))&&!requested.equals(Set.of(COMUN,selected)))throw fallo();
        configuration.locations(COMUN,selected).validateOnMigrate(true).cleanDisabled(true);
    }
    private static IllegalStateException fallo(){
        return new IllegalStateException("Historial Flyway no reconocido o no consultable; revisar configuracion y V4 antes de migrar");
    }
}
