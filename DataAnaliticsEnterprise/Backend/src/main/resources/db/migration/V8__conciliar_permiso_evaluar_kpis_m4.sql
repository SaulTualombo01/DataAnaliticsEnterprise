-- Paso común: nuevo permiso en M0; conserva ajustes ya existentes en el linaje M4.
DO $$
DECLARE nuevo boolean := false;
BEGIN
    IF EXISTS (SELECT 1 FROM autorizacion.permiso
        WHERE (id='c43215cc-e5de-4f86-a4f6-e1a9369c30d3' AND code<>'m4.kpis.evaluate')
           OR (code='m4.kpis.evaluate' AND (id<>'c43215cc-e5de-4f86-a4f6-e1a9369c30d3' OR operation_scope<>'PROJECT'))) THEN
        RAISE EXCEPTION 'Definicion M4 incompatible; revisar antes de migrar' USING ERRCODE='23514';
    END IF;
    IF NOT EXISTS(SELECT 1 FROM autorizacion.permiso WHERE code='m4.kpis.evaluate') THEN
        INSERT INTO autorizacion.permiso(id,code,description,operation_scope,available,archived_read)
        VALUES('c43215cc-e5de-4f86-a4f6-e1a9369c30d3','m4.kpis.evaluate',
            'Evaluar indicadores analíticos en un proyecto autorizado','PROJECT',true,false);
        nuevo := true;
    END IF;
    -- Un permiso previamente deshabilitado o retirado del rol no se reactiva.
    IF nuevo THEN
        INSERT INTO autorizacion.rol_permiso(role_id,permission_id)
        SELECT id,'c43215cc-e5de-4f86-a4f6-e1a9369c30d3'::uuid FROM autorizacion.rol WHERE code='ANALISTA_NEGOCIO'
        ON CONFLICT(role_id,permission_id) DO NOTHING;
    END IF;
END;
$$;
