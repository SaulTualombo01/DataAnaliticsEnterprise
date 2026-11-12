"""Verifica documentación y seguridad por lectura, sin crear datos de negocio."""
import sys
import verificar_api as api


def main():
    client = api.Client()
    checks = []

    def check(condition, description):
        if not condition:
            raise AssertionError(description)
        checks.append(description)
        print('OK ' + description, flush=True)

    status, page, headers = client.request('GET', '/swagger-ui/index.html')
    check(status == 200 and 'Swagger UI' in page, 'Swagger UI servido por la aplicación')
    check("style-src 'self' 'unsafe-inline'" in headers.get('Content-Security-Policy', ''), 'CSP de Swagger admite su estilo local')
    status, document, _ = client.request('GET', '/v3/api-docs')
    check(status == 200 and str(document.get('openapi', '')).startswith('3.'), 'OpenAPI generado desde los controladores')
    paths = document['paths']
    root = '/api/v1/projects/{project}'
    check(root + '/quality-evaluations' in paths and root + '/csv-inputs' in paths, 'Documenta evaluaciones y carga CSV reales')
    check(root + '/assets/{asset}/quality-overview' in paths, 'Documenta el panel por dimensión')
    check(document['components']['securitySchemes']['session']['type'] == 'apiKey'
          and document['components']['securitySchemes']['session']['in'] == 'cookie', 'Documenta sesión por cookie sin inventar JWT')
    check(paths['/api/v1/session/csrf']['get'].get('security', []) == [], 'Consulta CSRF identificada como pública')
    operation = paths[root + '/quality-evaluations']['post']
    check('202' in operation['responses'], 'La solicitud de análisis documenta HTTP 202')
    check(any(p.get('name') == 'X-CSRF-TOKEN' and p.get('required') for p in operation['parameters']), 'Escrituras documentan CSRF obligatorio')
    check(any(p.get('name') == 'Idempotency-Key' and p.get('required') for p in operation['parameters']), 'Documenta idempotencia obligatoria')
    patch = paths[root + '/quality-rules/{id}']['patch']
    check(any(p.get('name') == 'If-Match' and p.get('required') for p in patch['parameters']), 'Documenta control de concurrencia')
    schemas = document['components']['schemas']
    body_schema = operation['requestBody']['content']['application/json']['schema']
    if '$ref' in body_schema:
        body_schema = schemas[body_schema['$ref'].rsplit('/', 1)[1]]
    check({'asset_id', 'metadata_version_id', 'rule_ids', 'expected_rule_versions'}.issubset(body_schema['properties']), 'DTO de evaluación usa los nombres JSON reales')
    references = []

    def visit(value):
        if isinstance(value, dict):
            if '$ref' in value:
                references.append(value['$ref'])
            for child in value.values():
                visit(child)
        elif isinstance(value, list):
            for child in value:
                visit(child)

    visit(document)
    check(all(not ref.startswith('#/components/schemas/') or ref.rsplit('/', 1)[1] in schemas for ref in references), 'Referencias de esquemas resueltas')
    check('request_id' in schemas['ApiProblem']['properties'], 'Error compartido documenta correlación real')
    status, _, app_headers = client.request('GET', '/')
    check(status == 200 and 'unsafe-inline' not in app_headers.get('Content-Security-Policy', ''), 'La aplicación mantiene su CSP estricta')
    status, denied, _ = client.request('GET', api.PREFIX + '/assets')
    check(status == 401 and denied['code'] == 'AUTHENTICATION_REQUIRED' and 'request_id' in denied, 'Swagger no abre acceso anónimo a datos')
    client.login('calidad@example.test', api.CONFIG['M2_QUALITY_PASSWORD'])
    status, _, _ = client.request('GET', api.PREFIX + '/assets')
    check(status == 200, 'Sesión y consulta de activos conservadas')
    print('Comprobaciones aprobadas: ' + str(len(checks)))


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()
