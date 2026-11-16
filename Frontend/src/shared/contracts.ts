/**
 * Tipos de los contratos HTTP de otros módulos que consume el frontend de M4.
 *
 * Catálogo (M1): transcritos de `AssetRead` en docs/contratos/catalogo-http.schema.json,
 * contrato urn:tic:m1:consultas-http:0.3.0 (rama modulo1_catalogo). Si M1 cambia su esquema,
 * este archivo debe actualizarse a la par.
 */

/** UUID en minúsculas. */
export type Identifier = string

/** Fecha y hora ISO 8601 en UTC, terminada en `Z`. */
export type Timestamp = string

export type AssetKind = 'table' | 'csv_dataset' | 'json_collection' | 'api_collection'

export type AssetStatus = 'active' | 'missing' | 'retired'

export type StructureOrigin = 'declared' | 'inferred'

export type OriginLocator = {
  namespace: string | null
  object_path: string
}

export type Asset = {
  id: Identifier
  source_id: Identifier
  organization_id: Identifier
  project_id: Identifier
  name: string
  kind: AssetKind
  origin: OriginLocator
  status: AssetStatus
  metadata_version_id: Identifier
  structure_origin: StructureOrigin
  business_description: string | null
  tags: string[]
  last_discovery_at: Timestamp
  updated_at: Timestamp
}
