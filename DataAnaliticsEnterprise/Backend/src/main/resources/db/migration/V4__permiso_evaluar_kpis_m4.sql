INSERT INTO autorizacion.permiso (id, code, description, operation_scope, available)
VALUES ('c43215cc-e5de-4f86-a4f6-e1a9369c30d3', 'm4.kpis.evaluate',
        'Evaluar indicadores analíticos en un proyecto autorizado', 'PROJECT', true);

INSERT INTO autorizacion.rol_permiso (role_id, permission_id)
VALUES (
    (SELECT id FROM autorizacion.rol WHERE code = 'ANALISTA_NEGOCIO'),
    (SELECT id FROM autorizacion.permiso WHERE code = 'm4.kpis.evaluate')
);
