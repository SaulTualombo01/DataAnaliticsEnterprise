// Genera gobierno-http.openapi.json. Uso, desde la raíz del repositorio:
//   node docs/contratos/m3/generar-openapi.js docs/contratos/m3/gobierno-http.openapi.json
// ContratoHttpTests falla si las rutas de este contrato y las de los controladores difieren.
const fs = require('fs');
const salida = process.argv[2];

const ref = n => ({ $ref: `#/components/schemas/${n}` });
const uuid = { $ref: '#/components/schemas/Uuid' };
const uuidN = { oneOf: [uuid, { type: 'null' }] };
const fecha = { $ref: '#/components/schemas/Timestamp' };
const fechaN = { oneOf: [fecha, { type: 'null' }] };
const str = (extra = {}) => ({ type: 'string', ...extra });
const strN = { type: ['string', 'null'] };
const int = (extra = {}) => ({ type: 'integer', ...extra });
const en = (...v) => ({ type: 'string', enum: v });
const enN = (...v) => ({ type: ['string', 'null'], enum: [...v, null] });
const obj = (props, req = Object.keys(props), extra = {}) => ({ type: 'object', required: req, properties: props, additionalProperties: false, ...extra });
const arr = items => ({ type: 'array', items });

const ESTADO_POLITICA = ['DRAFT', 'ACTIVE', 'INACTIVE', 'RETIRED'];
const CATEGORIA = ['CLASIFICACION', 'PRIVACIDAD', 'RETENCION', 'CALIDAD', 'ACCESO', 'OTRA'];
const TIPO_REGLA = ['REQUIERE_RESPONSABLE', 'REQUIERE_CLASIFICACION', 'CLASIFICACION_MINIMA', 'CALIDAD_APROBADA'];
const CLASIFICACION = ['PUBLICA', 'INTERNA', 'CONFIDENCIAL', 'RESTRINGIDA'];
const CUMPLIMIENTO = ['COMPLIANT', 'NON_COMPLIANT', 'INDETERMINATE', 'NOT_APPLICABLE'];

const schemas = {
  Uuid: { type: 'string', format: 'uuid', pattern: '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' },
  Timestamp: { type: 'string', format: 'date-time', pattern: 'Z$' },
  ResponseMeta: { type: 'object', required: ['request_id'], properties: { request_id: uuid, page_info: ref('PageInfo') }, additionalProperties: true },
  PageInfo: obj({ page: int({ minimum: 1 }), page_size: int({ minimum: 1, maximum: 100 }), returned_count: int({ minimum: 0 }), has_next: { type: 'boolean' } }),
  Problem: {
    type: 'object', required: ['type', 'title', 'status', 'detail', 'instance', 'code', 'request_id'], additionalProperties: true,
    properties: {
      type: str({ format: 'uri' }), title: str(), status: int({ minimum: 400, maximum: 599 }), detail: str(),
      instance: str({ format: 'uri' }), request_id: uuid,
      code: en('INVALID_JSON', 'VALIDATION_ERROR', 'ACCESS_DENIED', 'RESOURCE_NOT_FOUND', 'METHOD_NOT_ALLOWED', 'CONFLICT',
        'PRECONDITION_FAILED', 'UNSUPPORTED_MEDIA_TYPE', 'PRECONDITION_REQUIRED', 'INTERNAL_ERROR'),
      errors: arr(obj({ location: en('body', 'query', 'path'), field: str(), code: str(), message: str() })),
    },
  },
  Regla: obj({ id: uuid, type: en(...TIPO_REGLA), parameter: enN(...CLASIFICACION) }),
  Politica: obj({
    id: uuid, project_id: uuid, name: str({ minLength: 1, maxLength: 200 }), description: str({ maxLength: 2000 }),
    category: en(...CATEGORIA), owner_id: uuid, status: en(...ESTADO_POLITICA), revision: int({ minimum: 0 }),
    current_version_id: uuid, asset_ids: arr(uuid), rules: arr(ref('Regla')), created_at: fecha, created_by: uuid, updated_at: fecha,
  }),
  PoliticaAplicable: obj({ policy: ref('Politica'), applicable: { type: 'boolean', description: 'true solo si la política está ACTIVE' } }),
  Ficha: obj({
    project_id: uuid, asset_id: uuid, owner_id: uuidN, classification: enN(...CLASIFICACION), revision: int({ minimum: 0 }),
    current_version_id: uuid, updated_at: fecha, updated_by: uuid,
  }),
  ResultadoRegla: obj({ rule_id: uuid, type: en(...TIPO_REGLA), parameter: enN(...CLASIFICACION), status: en(...CUMPLIMIENTO.slice(0, 3)),
    reason: en('CUMPLE', 'SIN_FICHA', 'FALTA_RESPONSABLE', 'FALTA_CLASIFICACION', 'CLASIFICACION_INSUFICIENTE', 'CALIDAD_NO_APROBADA', 'CALIDAD_SIN_EVALUAR') }),
  ResultadoPolitica: obj({ policy_id: uuid, policy_name: str(), policy_version_id: uuid, status: en(...CUMPLIMIENTO.slice(0, 3)),
    reason: enN('SIN_REGLAS'), rules: arr(ref('ResultadoRegla')) }),
  Evaluacion: obj({ id: uuid, project_id: uuid, asset_id: uuid, status: en(...CUMPLIMIENTO), evaluated_at: fecha, evaluated_by: uuid,
    profile_version_id: uuidN, audit_event_id: uuid, policies: arr(ref('ResultadoPolitica')) }),
  Relacion: obj({ id: uuid, project_id: uuid, origin_asset_id: uuid, target_asset_id: uuid, type: en('COPIA', 'TRANSFORMA', 'AGREGA', 'ALIMENTA'),
    description: str({ maxLength: 1000 }), status: en('ACTIVE', 'RETIRED'), current_version_id: uuid, created_at: fecha, created_by: uuid,
    retired_at: fechaN, retired_by: uuidN }),
  Grafo: obj({ asset_id: uuid, direction: en('ORIGENES', 'DESTINOS', 'AMBOS'), depth: int({ minimum: 1, maximum: 5 }),
    nodes: arr(obj({ asset_id: uuid, distance: int({ description: '0 = activo consultado; negativa = orígenes; positiva = destinos' }) })),
    relations: arr(ref('Relacion')), truncated: { type: 'boolean' } }),
  Evento: obj({ id: uuid, actor_type: en('USER', 'SERVICE', 'ANONYMOUS'), actor_id: uuidN, organization_id: uuidN, project_id: uuidN,
    resource_type: str(), resource_id: uuidN, action: str(), occurred_at: fecha, outcome: en('SUCCESS', 'FAILURE', 'DENIED'),
    request_id: uuidN, safe_details: str({ description: 'Objeto JSON serializado como texto' }) }),
  Version: obj({ id: uuid, project_id: uuid, resource_type: str(), resource_id: uuid, number: int({ minimum: 1 }), previous_version_id: uuidN,
    created_at: fecha, actor_type: en('USER', 'SERVICE'), actor_id: uuid, reason: str(), representation_version: int({ minimum: 1 }),
    state: str({ description: 'Objeto JSON serializado como texto' }), content_hash: str({ pattern: '^[0-9a-f]{64}$' }), audit_event_id: uuidN }),
  Comparacion: obj({ from: ref('Version'), to: ref('Version'),
    changes: arr(obj({ field: str(), change_type: en('AGREGADO', 'ELIMINADO', 'MODIFICADO'), old_value: strN, new_value: strN })) }),
  Resumen: obj({ project_id: uuid, asset_id: uuid, governance_profile: { oneOf: [ref('Ficha'), { type: 'null' }] },
    policies: arr(ref('PoliticaAplicable')), latest_compliance: { oneOf: [ref('Evaluacion'), { type: 'null' }] },
    direct_lineage: { oneOf: [ref('Grafo'), { type: 'null' }] }, unauthorized_sections: arr(en('FICHA', 'POLITICAS', 'CUMPLIMIENTO', 'LINAJE')) }),
  EntradaCronologia: obj({ event: ref('Evento'), version_id: uuidN, created_version: { type: 'boolean' } }),
  // Solicitudes
  NuevaPolitica: obj({ name: str({ minLength: 1, maxLength: 200 }), description: strN, category: en(...CATEGORIA), owner_id: uuid }, ['name', 'category', 'owner_id']),
  CambiosPolitica: obj({ name: str({ minLength: 1, maxLength: 200 }), description: { type: ['string', 'null'], description: 'null la deja vacía' },
    category: en(...CATEGORIA), owner_id: uuid, reason: str({ minLength: 1, maxLength: 500 }) }, ['reason'],
    { description: 'JSON Merge Patch: un campo ausente no cambia' }),
  CambioEstado: obj({ status: en(...ESTADO_POLITICA), reason: str({ minLength: 1, maxLength: 500 }) }),
  ActivoConMotivo: obj({ asset_id: uuid, reason: str({ minLength: 1, maxLength: 500 }) }),
  NuevaRegla: obj({ type: en(...TIPO_REGLA), parameter: enN(...CLASIFICACION), reason: str({ minLength: 1, maxLength: 500 }) }, ['type', 'reason'],
    { description: 'parameter solo para CLASIFICACION_MINIMA, y ahí es obligatorio' }),
  QuitarRegla: obj({ rule_id: uuid, reason: str({ minLength: 1, maxLength: 500 }) }),
  DatosFicha: obj({ owner_id: uuidN, classification: enN(...CLASIFICACION), reason: str({ minLength: 1, maxLength: 500 }) }, ['reason']),
  NuevaRelacion: obj({ origin_asset_id: uuid, target_asset_id: uuid, type: en('COPIA', 'TRANSFORMA', 'AGREGA', 'ALIMENTA'),
    description: strN, reason: str({ minLength: 1, maxLength: 500 }) }, ['origin_asset_id', 'target_asset_id', 'type', 'reason']),
  Motivo: obj({ reason: str({ minLength: 1, maxLength: 500 }) }),
};

const sobre = (dataSchema, pagina = false) => ({
  type: 'object', required: ['data', 'meta'], additionalProperties: false,
  properties: { data: pagina ? arr(dataSchema) : dataSchema, meta: ref('ResponseMeta') },
});
const err = c => ({ $ref: `#/components/responses/${c}` });
const responses = {};
for (const [c, d] of Object.entries({ E400: 'JSON ilegible', E403: 'Sin permiso en el proyecto', E404: 'Recurso inexistente o de otro proyecto',
  E409: 'Conflicto con el estado vigente', E412: 'Revisión obsoleta (If-Match no coincide)', E415: 'Content-Type no admitido',
  E422: 'Validación de cuerpo, query o ruta', E428: 'Falta If-Match', E500: 'Error interno' })) {
  responses[c] = { description: d, content: { 'application/problem+json': { schema: ref('Problem') } } };
}
const hdr = { 'X-Request-Id': { $ref: '#/components/headers/XRequestId' }, 'Cache-Control': { $ref: '#/components/headers/CacheControl' } };
const ok = (desc, schema, { pagina = false, etag = false, location = false, codigo = '200' } = {}) => ({
  [codigo]: { description: desc,
    headers: { ...hdr, ...(etag ? { ETag: { $ref: '#/components/headers/ETag' } } : {}), ...(location ? { Location: { $ref: '#/components/headers/Location' } } : {}) },
    content: { 'application/json': { schema: sobre(schema, pagina) } } },
});
const P = n => ({ $ref: `#/components/parameters/${n}` });
const cuerpo = (n, tipo = 'application/json', extra = []) => ({ required: true,
  content: Object.fromEntries([tipo, ...extra].map(t => [t, { schema: ref(n) }])) });
const op = (id, resumen, permiso, params, respuestasOk, errores, body) => ({
  operationId: id, summary: resumen, tags: [id.split('.')[0]],
  description: `Permiso: \`${permiso}\` en el proyecto.`,
  parameters: params.map(P), ...(body ? { requestBody: body } : {}),
  responses: { ...respuestasOk, ...Object.fromEntries(errores.map(e => [e.slice(1), err(e)])), '500': err('E500') },
});

const parametros = {
  ProjectId: { name: 'project_id', in: 'path', required: true, schema: uuid },
  PolicyId: { name: 'policy_id', in: 'path', required: true, schema: uuid },
  AssetId: { name: 'asset_id', in: 'path', required: true, schema: uuid },
  RelationId: { name: 'relation_id', in: 'path', required: true, schema: uuid },
  VersionId: { name: 'version_id', in: 'path', required: true, schema: uuid },
  ResourceType: { name: 'resource_type', in: 'path', required: true, schema: str({ pattern: '^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*$' }),
    example: 'gobierno.politica' },
  ResourceId: { name: 'resource_id', in: 'path', required: true, schema: uuid },
  IfMatch: { name: 'If-Match', in: 'header', required: true, schema: str(), example: '"3"',
    description: 'ETag leído del recurso. Ausente: 428. Distinto del vigente: 412.' },
  IfMatchOpcional: { name: 'If-Match', in: 'header', required: false, schema: str(),
    description: 'Ausente: crea la ficha (201; 412 si ya existe). Presente: edita la vigente (200).' },
  Page: { name: 'page', in: 'query', schema: int({ minimum: 1, default: 1 }) },
  PageSize: { name: 'page_size', in: 'query', schema: int({ minimum: 1, maximum: 100, default: 25 }) },
  Q: { name: 'q', in: 'query', schema: str({ maxLength: 100 }), description: 'Texto en nombre o descripción; % y _ se buscan literalmente' },
  StatusPolitica: { name: 'status', in: 'query', schema: en(...ESTADO_POLITICA) },
  Category: { name: 'category', in: 'query', schema: en(...CATEGORIA) },
  OwnerId: { name: 'owner_id', in: 'query', schema: uuid },
  AssetIdQuery: { name: 'asset_id', in: 'query', schema: uuid },
  Direction: { name: 'direction', in: 'query', schema: { ...en('ORIGENES', 'DESTINOS', 'AMBOS'), default: 'AMBOS' } },
  Depth: { name: 'depth', in: 'query', schema: int({ minimum: 1, maximum: 5, default: 1 }) },
  ActorId: { name: 'actor_id', in: 'query', schema: uuid },
  Action: { name: 'action', in: 'query', schema: str(), example: 'm3.policies.create' },
  ResourceTypeQuery: { name: 'resource_type', in: 'query', schema: str() },
  ResourceIdQuery: { name: 'resource_id', in: 'query', schema: uuid },
  Outcome: { name: 'outcome', in: 'query', schema: en('SUCCESS', 'FAILURE', 'DENIED') },
  From: { name: 'from', in: 'query', schema: fecha, description: 'Desde (incluido), UTC con Z' },
  To: { name: 'to', in: 'query', schema: fecha, description: 'Hasta (excluido), UTC con Z' },
  FromVersion: { name: 'from', in: 'query', required: true, schema: uuid },
  ToVersion: { name: 'to', in: 'query', required: true, schema: uuid },
};

const B = '/api/v1/projects/{project_id}';
const editaPolitica = (id, resumen, permiso, solicitud) => ({ post: op(id, resumen, permiso, ['ProjectId', 'PolicyId', 'IfMatch'],
  ok('Política actualizada', ref('Politica'), { etag: true }), ['E400', 'E403', 'E404', 'E409', 'E412', 'E415', 'E422', 'E428'], cuerpo(solicitud)) });

const paths = {
  [`${B}/governance-policies`]: {
    get: op('politicas.buscar', 'Buscar políticas', 'm3.policies.read', ['ProjectId', 'Page', 'PageSize', 'Q', 'StatusPolitica', 'Category', 'OwnerId', 'AssetIdQuery'],
      ok('Página de políticas (creación e id ascendentes)', ref('Politica'), { pagina: true }), ['E403', 'E422']),
    post: op('politicas.crear', 'Crear política (nace DRAFT, revisión 0)', 'm3.policies.create', ['ProjectId'],
      ok('Creada', ref('Politica'), { etag: true, location: true, codigo: '201' }), ['E400', 'E403', 'E409', 'E415', 'E422'], cuerpo('NuevaPolitica')),
  },
  [`${B}/governance-policies/{policy_id}`]: {
    get: op('politicas.consultar', 'Consultar política', 'm3.policies.read', ['ProjectId', 'PolicyId'],
      ok('Política', ref('Politica'), { etag: true }), ['E403', 'E404', 'E422']),
    patch: op('politicas.actualizar', 'Editar nombre, descripción, categoría o responsable', 'm3.policies.update', ['ProjectId', 'PolicyId', 'IfMatch'],
      ok('Actualizada (sin cambios efectivos no sube la revisión)', ref('Politica'), { etag: true }),
      ['E400', 'E403', 'E404', 'E409', 'E412', 'E415', 'E422', 'E428'], cuerpo('CambiosPolitica', 'application/merge-patch+json', ['application/json'])),
  },
  [`${B}/governance-policies/{policy_id}/status-changes`]: editaPolitica('politicas.cambiarEstado',
    'Cambiar estado: DRAFT→ACTIVE↔INACTIVE; cualquiera→RETIRED', 'm3.policies.lifecycle', 'CambioEstado'),
  [`${B}/governance-policies/{policy_id}/assets`]: editaPolitica('politicas.asociarActivo', 'Asociar un activo del catálogo', 'm3.policies.assets.manage', 'ActivoConMotivo'),
  [`${B}/governance-policies/{policy_id}/asset-removals`]: editaPolitica('politicas.retirarActivo', 'Quitar un activo', 'm3.policies.assets.manage', 'ActivoConMotivo'),
  [`${B}/governance-policies/{policy_id}/rules`]: editaPolitica('politicas.agregarRegla', 'Añadir regla verificable', 'm3.policies.rules.manage', 'NuevaRegla'),
  [`${B}/governance-policies/{policy_id}/rule-removals`]: editaPolitica('politicas.quitarRegla', 'Quitar regla', 'm3.policies.rules.manage', 'QuitarRegla'),
  [`${B}/assets/{asset_id}/governance-policies`]: {
    get: op('politicas.deActivo', 'Políticas asociadas a un activo y si aplican', 'm3.policies.read', ['ProjectId', 'AssetId'],
      ok('Lista', ref('PoliticaAplicable'), { pagina: true }), ['E403', 'E422']),
  },
  [`${B}/assets/{asset_id}/governance-profile`]: {
    get: op('fichas.consultar', 'Ficha de gobierno del activo', 'm3.asset_profiles.read', ['ProjectId', 'AssetId'],
      ok('Ficha', ref('Ficha'), { etag: true }), ['E403', 'E404', 'E422']),
    put: op('fichas.guardar', 'Crear o sustituir la ficha (responsable y clasificación)', 'm3.asset_profiles.update', ['ProjectId', 'AssetId', 'IfMatchOpcional'],
      { ...ok('Editada', ref('Ficha'), { etag: true }), ...ok('Creada', ref('Ficha'), { etag: true, location: true, codigo: '201' }) },
      ['E400', 'E403', 'E404', 'E412', 'E415', 'E422'], cuerpo('DatosFicha')),
  },
  [`${B}/assets/{asset_id}/compliance-evaluations`]: {
    post: op('cumplimiento.evaluar', 'Evaluar el activo contra sus políticas activas (síncrono, sin cuerpo)',
      'm3.compliance.evaluate (y m3.policies.read, m3.asset_profiles.read)', ['ProjectId', 'AssetId'],
      ok('Evaluación guardada', ref('Evaluacion'), { location: true, codigo: '201' }), ['E403', 'E404', 'E422']),
    get: op('cumplimiento.historial', 'Evaluaciones anteriores (más reciente primero)', 'm3.compliance.read', ['ProjectId', 'AssetId', 'Page', 'PageSize'],
      ok('Página', ref('Evaluacion'), { pagina: true }), ['E403', 'E422']),
  },
  [`${B}/assets/{asset_id}/compliance`]: {
    get: op('cumplimiento.ultima', 'Estado de cumplimiento vigente (lo consume M4)', 'm3.compliance.read', ['ProjectId', 'AssetId'],
      ok('Última evaluación', ref('Evaluacion')), ['E403', 'E404', 'E422']),
  },
  [`${B}/lineage-relations`]: {
    post: op('linaje.registrar', 'Registrar relación origen → destino (sin ciclos; una igual vigente se devuelve)', 'm3.lineage.manage', ['ProjectId'],
      ok('Relación', ref('Relacion'), { location: true, codigo: '201' }), ['E400', 'E403', 'E404', 'E409', 'E415', 'E422'], cuerpo('NuevaRelacion')),
  },
  [`${B}/lineage-relations/{relation_id}`]: {
    get: op('linaje.consultar', 'Consultar relación (vigente o retirada)', 'm3.lineage.read', ['ProjectId', 'RelationId'],
      ok('Relación', ref('Relacion')), ['E403', 'E404', 'E422']),
  },
  [`${B}/lineage-relations/{relation_id}/retirements`]: {
    post: op('linaje.retirar', 'Retirar relación (se conserva en el historial)', 'm3.lineage.manage', ['ProjectId', 'RelationId'],
      ok('Retirada', ref('Relacion')), ['E400', 'E403', 'E404', 'E415', 'E422'], cuerpo('Motivo')),
  },
  [`${B}/assets/{asset_id}/lineage`]: {
    get: op('linaje.grafo', 'Grafo de linaje vigente alrededor del activo', 'm3.lineage.read', ['ProjectId', 'AssetId', 'Direction', 'Depth'],
      ok('Grafo (hasta 200 nodos; truncated indica si se cortó)', ref('Grafo')), ['E403', 'E422']),
  },
  [`${B}/audit-events`]: {
    get: op('auditoria.consultar', 'Eventos de auditoría del proyecto (más reciente primero)', 'm3.audit.read',
      ['ProjectId', 'Page', 'PageSize', 'ActorId', 'Action', 'ResourceTypeQuery', 'ResourceIdQuery', 'Outcome', 'From', 'To'],
      ok('Página de eventos', ref('Evento'), { pagina: true }), ['E403', 'E422']),
  },
  [`${B}/versions/{resource_type}/{resource_id}`]: {
    get: op('versiones.historial', 'Versiones del recurso (más reciente primero)', 'm3.history.read', ['ProjectId', 'ResourceType', 'ResourceId', 'Page', 'PageSize'],
      ok('Página de versiones', ref('Version'), { pagina: true }), ['E403', 'E422']),
  },
  [`${B}/versions/{resource_type}/{resource_id}/comparison`]: {
    get: op('versiones.comparar', 'Comparar dos versiones campo por campo', 'm3.history.read', ['ProjectId', 'ResourceType', 'ResourceId', 'FromVersion', 'ToVersion'],
      ok('Cambios ordenados por campo; vacío si son iguales', ref('Comparacion')), ['E403', 'E404', 'E422']),
  },
  [`${B}/versions/{resource_type}/{resource_id}/{version_id}`]: {
    get: op('versiones.consultar', 'Una versión concreta', 'm3.history.read', ['ProjectId', 'ResourceType', 'ResourceId', 'VersionId'],
      ok('Versión', ref('Version')), ['E403', 'E404', 'E422']),
  },
  [`${B}/assets/{asset_id}/governance-summary`]: {
    get: op('historial.resumen', 'Todo lo que M3 sabe del activo; las secciones sin permiso se omiten', 'm3.history.read', ['ProjectId', 'AssetId'],
      ok('Resumen', ref('Resumen')), ['E403', 'E404', 'E422']),
  },
  [`${B}/assets/{asset_id}/governance-history`]: {
    get: op('historial.cronologia', 'Cronología del activo; created_version distingue cambios efectivos', 'm3.history.read y m3.audit.read',
      ['ProjectId', 'AssetId', 'Page', 'PageSize'], ok('Página', ref('EntradaCronologia'), { pagina: true }), ['E403', 'E404', 'E422']),
  },
};

const spec = {
  openapi: '3.1.1',
  info: {
    title: 'M3 Gobierno y Trazabilidad de los Datos — API HTTP', version: '1.0.0',
    description: 'Rama modulo3_gobierno. Generado desde el mismo contrato que implementan los controladores de '
      + 'org.dataanaliticsent.Gobierno.*.internal.presentacion. Convenciones del equipo: snake_case, UUID en minúsculas, '
      + 'fechas UTC con Z, éxito {data, meta.request_id}, errores Problem Details (RFC 9457), page/page_size (25/100), '
      + 'ETag/If-Match (412/428). Sesión TICSESSION y CSRF del tronco común; hasta integrarlo, la aplicación real responde 403.',
  },
  servers: [{ url: 'http://localhost:8080' }],
  security: [{ sesion: [] }],
  tags: ['politicas', 'fichas', 'cumplimiento', 'linaje', 'auditoria', 'versiones', 'historial'].map(name => ({ name })),
  paths,
  components: {
    securitySchemes: { sesion: { type: 'apiKey', in: 'cookie', name: 'TICSESSION', description: 'Sesión del tronco común; escrituras con CSRF' } },
    parameters: parametros,
    headers: {
      XRequestId: { schema: uuid, description: 'Igual a meta.request_id o request_id del error' },
      CacheControl: { schema: { const: 'no-store' } },
      ETag: { schema: str({ pattern: '^"[0-9]+"$' }), description: 'Revisión vigente del recurso' },
      Location: { schema: str({ format: 'uri-reference' }) },
    },
    responses,
    schemas,
  },
};
fs.writeFileSync(salida, JSON.stringify(spec, null, 2) + '\n');
console.log('rutas:', Object.keys(paths).length, 'operaciones:', Object.values(paths).reduce((n, p) => n + Object.keys(p).length, 0),
  'esquemas:', Object.keys(schemas).length);
