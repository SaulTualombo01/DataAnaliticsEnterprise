# Contrato y mock de catálogo y diccionario de datos — M1

Sí, ya tengo una **base técnica del contrato y un JSON mock** para que puedas avanzar con el formulario en React. En el repositorio están estos archivos:

- [Contrato OpenAPI de M1](contratos/m1/catalogo-http.openapi.json).
- [Ejemplos JSON del catálogo y diccionario](contratos/m1/catalogo.examples.json).
- [Tipos TypeScript para el frontend](../Frontend/src/shared/contracts.ts).

El flujo es consultar primero el activo:

```http
GET /api/v1/projects/{project_id}/assets/{asset_id}
```

Esa respuesta incluye `metadata_version_id`, que debes enviar al consultar sus campos:

```http
GET /api/v1/projects/{project_id}/assets/{asset_id}/fields?metadata_version_id={version_id}&page=1&page_size=25
```

Cada campo devuelve su `id`, nombre, ruta, tipo nativo, tipo lógico, nulabilidad, descripción y estado. La respuesta viene en `data`, con información de paginación en `meta.page_info`.

Puedes empezar usando `asset_ok.body` y `fields_ok.body` del mock. También hay ejemplos de lista vacía y errores.

**El alcance actual cubre el detalle de un activo y su diccionario; todavía no incluye el listado de fuentes o activos.** Para integrar contra el backend real se necesita incorporar M1-04. Podemos usar este contrato como punto de partida y acordar cualquier dato adicional que necesite el formulario de KPIs.
