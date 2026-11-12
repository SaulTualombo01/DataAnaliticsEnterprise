package org.dataanaliticsent.catalogo.internal.infraestructura;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.dataanaliticsent.catalogo.DescubrimientoFallidoException;
import org.dataanaliticsent.catalogo.DescubrimientoFallidoException.Codigo;
import org.dataanaliticsent.catalogo.internal.aplicacion.LimitesDescubrimiento;
import org.dataanaliticsent.catalogo.internal.dominio.*;
import org.springframework.stereotype.Component;

@Component
public class ExtractorMetadatosPostgres implements ExtractorPostgres {
    private final AccesoPostgresConfigurado acceso;
    private final LimitesDescubrimiento limites;
    public ExtractorMetadatosPostgres(AccesoPostgresConfigurado acceso, LimitesDescubrimiento limites) {
        this.acceso = acceso; this.limites = limites;
    }

    @Override
    public List<Tabla> extraer(FuenteDatos fuente, List<String> schemas) {
        var configurado = acceso.resolver(fuente, limites);
        long deadline = System.nanoTime() + limites.getTotalTimeoutSeconds() * 1_000_000_000L;
        try (Connection conn = DriverManager.getConnection(configurado.url(), configurado.properties())) {
            conn.setReadOnly(true);
            conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            conn.setAutoCommit(false);
            List<TablaRef> tablas = new ArrayList<>();
            for (String schema : schemas) {
                try (var q = preparar(conn, """
                        SELECT EXISTS(SELECT 1 FROM pg_catalog.pg_namespace n
                            WHERE n.nspname=? AND pg_catalog.has_schema_privilege(n.oid,'USAGE'))
                        """, deadline, 1)) {
                    q.setString(1, schema);
                    try (var rs = q.executeQuery()) {
                        if (!rs.next() || !rs.getBoolean(1)) throw new DescubrimientoFallidoException(Codigo.ALCANCE_NO_ACCESIBLE);
                    }
                }
                try (var q = preparar(conn, """
                        SELECT c.oid,n.nspname,c.relname FROM pg_catalog.pg_class c
                        JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
                        WHERE n.nspname=? AND c.relkind IN ('r','p')
                            AND (pg_catalog.has_table_privilege(c.oid,'SELECT')
                                OR pg_catalog.has_table_privilege(c.oid,'REFERENCES'))
                        ORDER BY c.relname,c.oid
                        """, deadline, limites.getMaxTables() + 1)) {
                    q.setString(1, schema);
                    try (var rs = q.executeQuery()) {
                        while (rs.next()) {
                            tablas.add(new TablaRef(rs.getLong(1), new LocalizadorTabla(rs.getString(2),rs.getString(3))));
                            if (tablas.size() > limites.getMaxTables()) throw limite();
                        }
                    }
                }
            }
            List<Tabla> resultado = new ArrayList<>();
            for (TablaRef tabla : tablas) {
                resultado.add(new Tabla(tabla.locator(), columnas(conn,tabla.oid(),deadline), claves(conn,tabla.oid(),deadline)));
            }
            if (System.nanoTime() >= deadline) throw limite();
            conn.commit();
            return List.copyOf(resultado);
        } catch (SQLException error) {
            // No se propaga el mensaje del driver: puede incluir host, usuario o SQL de origen.
            throw new DescubrimientoFallidoException(Codigo.ORIGEN_NO_DISPONIBLE);
        } finally {
            configurado.properties().clear();
        }
    }

    private List<Columna> columnas(Connection conn, long oid, long deadline) throws SQLException {
        List<Columna> resultado = new ArrayList<>();
        try (var q = preparar(conn, """
                SELECT a.attname,a.attnum,pg_catalog.format_type(a.atttypid,a.atttypmod),
                    bt.typname,bt.typcategory,NOT(a.attnotnull OR t.typnotnull) AS nullable
                FROM pg_catalog.pg_attribute a JOIN pg_catalog.pg_type t ON t.oid=a.atttypid
                JOIN pg_catalog.pg_type bt ON bt.oid=CASE WHEN t.typtype='d' THEN t.typbasetype ELSE t.oid END
                WHERE a.attrelid=CAST(? AS oid) AND a.attnum>0 AND NOT a.attisdropped ORDER BY a.attnum
                """, deadline, limites.getMaxColumns() + 1)) {
            q.setLong(1,oid);
            try (var rs = q.executeQuery()) {
                while (rs.next()) {
                    resultado.add(new Columna(rs.getString(1),rs.getInt(2),rs.getString(3),
                            tipo(rs.getString(4),rs.getString(5)),rs.getBoolean(6)));
                    if (resultado.size() > limites.getMaxColumns()) throw limite();
                }
            }
        }
        return resultado;
    }

    private List<Clave> claves(Connection conn, long oid, long deadline) throws SQLException {
        var claves = new LinkedHashMap<Long, ClaveBuilder>();
        try (var q = preparar(conn, """
                SELECT k.oid,k.conname,k.contype,m.position,sa.attname AS source_name,
                    tn.nspname AS target_schema,tc.relname AS target_table,
                    CASE WHEN pg_catalog.has_schema_privilege(tn.oid,'USAGE')
                        AND (pg_catalog.has_table_privilege(tc.oid,'SELECT') OR pg_catalog.has_table_privilege(tc.oid,'REFERENCES'))
                        THEN ta.attname ELSE NULL END AS target_name,
                    COALESCE(pg_catalog.has_schema_privilege(tn.oid,'USAGE')
                        AND (pg_catalog.has_table_privilege(tc.oid,'SELECT') OR pg_catalog.has_table_privilege(tc.oid,'REFERENCES')),false)
                        AS target_accessible
                FROM pg_catalog.pg_constraint k
                JOIN LATERAL unnest(k.conkey) WITH ORDINALITY m(attnum,position) ON true
                JOIN pg_catalog.pg_attribute sa ON sa.attrelid=k.conrelid AND sa.attnum=m.attnum
                LEFT JOIN pg_catalog.pg_class tc ON tc.oid=k.confrelid
                LEFT JOIN pg_catalog.pg_namespace tn ON tn.oid=tc.relnamespace
                LEFT JOIN pg_catalog.pg_attribute ta ON ta.attrelid=k.confrelid AND ta.attnum=k.confkey[m.position::int]
                WHERE k.conrelid=CAST(? AS oid) AND k.contype IN ('p','u','f') ORDER BY k.oid,m.position
                """, deadline, limites.getMaxKeyMembers() + 1)) {
            q.setLong(1,oid);
            int miembros = 0;
            try (var rs = q.executeQuery()) {
                while (rs.next()) {
                    if (++miembros > limites.getMaxKeyMembers()) throw limite();
                    long key = rs.getLong("oid");
                    ClaveBuilder builder = claves.get(key);
                    if (builder == null) {
                        RestriccionDatos.Tipo tipo = switch (rs.getString("contype")) {
                            case "p" -> RestriccionDatos.Tipo.PK;
                            case "u" -> RestriccionDatos.Tipo.UNIQUE;
                            case "f" -> RestriccionDatos.Tipo.FK;
                            default -> throw new IllegalStateException("Tipo de clave no soportado");
                        };
                        LocalizadorTabla target = tipo == RestriccionDatos.Tipo.FK
                                ? new LocalizadorTabla(rs.getString("target_schema"),rs.getString("target_table")) : null;
                        builder = new ClaveBuilder(rs.getString("conname"),tipo,target,rs.getBoolean("target_accessible"));
                        claves.put(key,builder);
                    }
                    builder.members.add(new MiembroClave(rs.getInt("position"),rs.getString("source_name"),rs.getString("target_name")));
                }
            }
        }
        return claves.values().stream().map(c -> new Clave(c.name,c.kind,c.target,c.accessible,c.members)).toList();
    }

    private PreparedStatement preparar(Connection conn, String sql, long deadline, int maxRows) throws SQLException {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw limite();
        PreparedStatement query = conn.prepareStatement(sql);
        try {
            query.setQueryTimeout((int)Math.min(limites.getQueryTimeoutSeconds(),(remaining + 999_999_999L)/1_000_000_000L));
            query.setMaxRows(maxRows);
            return query;
        } catch (SQLException error) { query.close(); throw error; }
    }

    static CampoDatos.TipoLogico tipo(String name, String category) {
        if ("A".equals(category)) return CampoDatos.TipoLogico.ARRAY;
        if (List.of("int2","int4","int8","oid").contains(name)) return CampoDatos.TipoLogico.INTEGER;
        if ("N".equals(category)) return CampoDatos.TipoLogico.NUMBER;
        if ("bool".equals(name)) return CampoDatos.TipoLogico.BOOLEAN;
        if ("date".equals(name)) return CampoDatos.TipoLogico.DATE;
        if (List.of("timestamp","timestamptz").contains(name)) return CampoDatos.TipoLogico.DATETIME;
        if ("S".equals(category) || "E".equals(category) || "uuid".equals(name)) return CampoDatos.TipoLogico.STRING;
        return CampoDatos.TipoLogico.UNKNOWN;
    }
    private static DescubrimientoFallidoException limite() { return new DescubrimientoFallidoException(Codigo.LIMITE_EXCEDIDO); }
    private record TablaRef(long oid, LocalizadorTabla locator) { }
    private static class ClaveBuilder {
        final String name; final RestriccionDatos.Tipo kind; final LocalizadorTabla target; final boolean accessible;
        final List<MiembroClave> members = new ArrayList<>();
        ClaveBuilder(String name,RestriccionDatos.Tipo kind,LocalizadorTabla target,boolean accessible) {
            this.name=name; this.kind=kind; this.target=target; this.accessible=accessible;
        }
    }
}
