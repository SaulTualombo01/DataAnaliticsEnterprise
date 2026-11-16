import type { Asset } from '@/shared/contracts'

const SIMULATED_DELAY_MS = 400

const ORGANIZATION_ID = '4b234512-6e90-5a3c-b494-a30f02eb117d'
const PROJECT_ID = '2f66e241-dd8b-508f-b5e1-85db372e9f2c'
const SOURCE_ID = 'a5ab298e-2fc1-5459-af10-c741dcda1635'
const DISCOVERED_AT = '2026-10-21T15:05:00Z'

const SYNTHETIC_ASSETS: Asset[] = [
  {
    id: '7c9e6679-7425-40de-944b-e07fc1f90ae7',
    source_id: SOURCE_ID,
    organization_id: ORGANIZATION_ID,
    project_id: PROJECT_ID,
    name: 'ventas_historicas',
    kind: 'table',
    origin: { namespace: 'public', object_path: 'ventas_historicas' },
    status: 'active',
    metadata_version_id: '3f2504e0-4f89-41d3-9a0c-0305e82c3301',
    structure_origin: 'declared',
    business_description: 'Pedidos cerrados del comercio de ejemplo.',
    tags: ['ventas'],
    last_discovery_at: DISCOVERED_AT,
    updated_at: DISCOVERED_AT,
  },
  {
    id: '9b2d4c1a-5e6f-4a7b-8c9d-0e1f2a3b4c5d',
    source_id: SOURCE_ID,
    organization_id: ORGANIZATION_ID,
    project_id: PROJECT_ID,
    name: 'sesiones_web',
    kind: 'table',
    origin: { namespace: 'public', object_path: 'sesiones_web' },
    status: 'active',
    metadata_version_id: '6ba7b810-9dad-41d1-80b4-00c04fd430c8',
    structure_origin: 'inferred',
    business_description: 'Sesiones de navegación de la tienda en línea.',
    tags: ['web'],
    last_discovery_at: DISCOVERED_AT,
    updated_at: DISCOVERED_AT,
  },
  {
    id: '1e4f8a2b-3c5d-4e6f-9a7b-8c9d0e1f2a3b',
    source_id: SOURCE_ID,
    organization_id: ORGANIZATION_ID,
    project_id: PROJECT_ID,
    name: 'inventario',
    kind: 'table',
    origin: { namespace: 'public', object_path: 'inventario' },
    status: 'active',
    metadata_version_id: '6ba7b811-9dad-41d1-80b4-00c04fd430c8',
    structure_origin: 'declared',
    business_description: null,
    tags: ['logistica'],
    last_discovery_at: DISCOVERED_AT,
    updated_at: DISCOVERED_AT,
  },
]

/**
 * Sintético: M1 aún no expone un listado de activos (su contrato 0.3.0 solo consulta uno por id).
 * Sustituir por la llamada real cuando exista ese endpoint.
 */
export function fetchCatalogAssets(): Promise<Asset[]> {
  return new Promise((resolve) => {
    setTimeout(() => resolve(SYNTHETIC_ASSETS), SIMULATED_DELAY_MS)
  })
}
