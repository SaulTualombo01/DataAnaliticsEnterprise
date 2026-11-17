-- M3 Gobierno: permisos del catálogo RBAC común (tablas del tronco común, V1_1 y V6).
-- Todos con ámbito PROJECT. RESPONSABLE_GOBIERNO recibe todos; los demás roles funcionales
-- solo las lecturas que la matriz de permisos (P12) les concede: políticas aplicables, ficha
-- del activo, cumplimiento y linaje. Las lecturas siguen disponibles en proyectos archivados
-- (archived_read); las escrituras no. ADMIN_SISTEMA no recibe ninguno.
-- IDs: UUID v5 de "tic:permiso:<código>", estables. Generado por la misma lista que PermisosGobierno.

INSERT INTO autorizacion.permiso(id,code,description,operation_scope,available,archived_read) VALUES('3c6666be-e423-5449-86c4-c2aabedcd4ce','m3.audit.read','Consultar eventos de auditoría del proyecto','PROJECT',true,true);
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'3c6666be-e423-5449-86c4-c2aabedcd4ce' FROM autorizacion.rol WHERE code IN ('RESPONSABLE_GOBIERNO');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope,available,archived_read) VALUES('137fb553-0e45-5932-80e3-21b18b452eb9','m3.history.read','Consultar versiones, comparaciones, resumen y cronología de gobierno','PROJECT',true,true);
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'137fb553-0e45-5932-80e3-21b18b452eb9' FROM autorizacion.rol WHERE code IN ('RESPONSABLE_GOBIERNO');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope,available,archived_read) VALUES('ddc93219-febc-5370-8f85-34c1290a3b41','m3.policies.read','Consultar políticas de gobierno y las aplicables a un activo','PROJECT',true,true);
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'ddc93219-febc-5370-8f85-34c1290a3b41' FROM autorizacion.rol WHERE code IN ('RESPONSABLE_GOBIERNO','GESTOR_CATALOGO','ANALISTA_CALIDAD','ANALISTA_NEGOCIO');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope,available,archived_read) VALUES('ec44036d-4789-5153-a225-6fa80b59294b','m3.policies.create','Crear políticas de gobierno','PROJECT',true,false);
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'ec44036d-4789-5153-a225-6fa80b59294b' FROM autorizacion.rol WHERE code IN ('RESPONSABLE_GOBIERNO');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope,available,archived_read) VALUES('2bca4ac5-323b-5077-aa2c-9f7643878c26','m3.policies.update','Editar nombre, descripción, categoría o responsable de una política','PROJECT',true,false);
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'2bca4ac5-323b-5077-aa2c-9f7643878c26' FROM autorizacion.rol WHERE code IN ('RESPONSABLE_GOBIERNO');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope,available,archived_read) VALUES('52ca92bf-f721-5546-bf30-4d15e5db6282','m3.policies.lifecycle','Activar, desactivar o retirar políticas','PROJECT',true,false);
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'52ca92bf-f721-5546-bf30-4d15e5db6282' FROM autorizacion.rol WHERE code IN ('RESPONSABLE_GOBIERNO');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope,available,archived_read) VALUES('50745436-0bf3-5e45-b838-3c06610d042c','m3.policies.assets.manage','Asociar o quitar activos de una política','PROJECT',true,false);
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'50745436-0bf3-5e45-b838-3c06610d042c' FROM autorizacion.rol WHERE code IN ('RESPONSABLE_GOBIERNO');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope,available,archived_read) VALUES('f8cb7e9c-c1fb-58d8-9735-1035f2457d05','m3.policies.rules.manage','Configurar reglas verificables de una política','PROJECT',true,false);
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'f8cb7e9c-c1fb-58d8-9735-1035f2457d05' FROM autorizacion.rol WHERE code IN ('RESPONSABLE_GOBIERNO');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope,available,archived_read) VALUES('3c3d22b8-8227-5665-8c41-6d6a6ef358af','m3.asset_profiles.read','Consultar responsable y clasificación formal de un activo','PROJECT',true,true);
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'3c3d22b8-8227-5665-8c41-6d6a6ef358af' FROM autorizacion.rol WHERE code IN ('RESPONSABLE_GOBIERNO','GESTOR_CATALOGO','ANALISTA_CALIDAD','ANALISTA_NEGOCIO');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope,available,archived_read) VALUES('7bff45b9-990b-5150-81e2-a36e2c35b8c7','m3.asset_profiles.update','Definir responsable y clasificación formal de un activo','PROJECT',true,false);
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'7bff45b9-990b-5150-81e2-a36e2c35b8c7' FROM autorizacion.rol WHERE code IN ('RESPONSABLE_GOBIERNO');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope,available,archived_read) VALUES('1516a03f-b281-5f6d-9c6e-973538e5df77','m3.compliance.read','Consultar el estado de cumplimiento de un activo','PROJECT',true,true);
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'1516a03f-b281-5f6d-9c6e-973538e5df77' FROM autorizacion.rol WHERE code IN ('RESPONSABLE_GOBIERNO','GESTOR_CATALOGO','ANALISTA_CALIDAD','ANALISTA_NEGOCIO');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope,available,archived_read) VALUES('1969acd5-f7e0-5442-bc8b-43389ed43651','m3.compliance.evaluate','Evaluar el cumplimiento de un activo contra sus políticas activas','PROJECT',true,false);
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'1969acd5-f7e0-5442-bc8b-43389ed43651' FROM autorizacion.rol WHERE code IN ('RESPONSABLE_GOBIERNO');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope,available,archived_read) VALUES('7411ada8-387d-5e87-96b9-86817910b665','m3.lineage.read','Consultar relaciones y grafo de linaje','PROJECT',true,true);
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'7411ada8-387d-5e87-96b9-86817910b665' FROM autorizacion.rol WHERE code IN ('RESPONSABLE_GOBIERNO','GESTOR_CATALOGO','ANALISTA_CALIDAD','ANALISTA_NEGOCIO');

INSERT INTO autorizacion.permiso(id,code,description,operation_scope,available,archived_read) VALUES('0ef90371-91c8-5d56-908d-c5e489a07730','m3.lineage.manage','Registrar o retirar relaciones de linaje','PROJECT',true,false);
INSERT INTO autorizacion.rol_permiso(role_id,permission_id) SELECT id,'0ef90371-91c8-5d56-908d-c5e489a07730' FROM autorizacion.rol WHERE code IN ('RESPONSABLE_GOBIERNO');
