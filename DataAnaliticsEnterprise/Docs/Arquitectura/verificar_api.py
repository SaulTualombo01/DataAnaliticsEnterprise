"""Prueba integrada contra el Compose local. Solo utiliza el proyecto ficticio de desarrollo."""
import http.cookiejar
import json
import sys
import time
import uuid
import os
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, build_opener, HTTPCookieProcessor

ROOT = Path(__file__).resolve().parents[2]
CONFIG_PATH = Path(os.environ.get('M2_TEST_ENV_FILE', ROOT / 'target/docker.env')).resolve()
CONFIG = dict(line.split('=', 1) for line in CONFIG_PATH.read_text(encoding='utf-8-sig').splitlines() if line and not line.startswith('#'))
BASE = os.environ.get('M2_TEST_BASE_URL', 'http://127.0.0.1:' + CONFIG.get('M2_HTTP_PORT', '8080'))
PROJECT = os.environ.get('M2_TEST_PROJECT_ID', 'b19b98b7-e715-42ca-b5af-02d665db48ad')
PREFIX = '/api/v1/projects/' + PROJECT
checks = []

def require_isolated_test_environment():
    if (os.environ.get('M2_TEST_ISOLATED') != '1' or not os.environ.get('M2_TEST_BASE_URL') or CONFIG_PATH == (ROOT / 'target/docker.env').resolve()
            or PROJECT == 'b19b98b7-e715-42ca-b5af-02d665db48ad'):
        raise SystemExit('Las pruebas que crean datos requieren un entorno separado: M2_TEST_ISOLATED=1, M2_TEST_ENV_FILE, M2_TEST_BASE_URL y M2_TEST_PROJECT_ID propios. No se generan pruebas en la base de uso diario.')


def check(condition, name):
    if not condition:
        raise AssertionError(name)
    checks.append(name)
    print('OK ' + name, flush=True)


class Client:
    def __init__(self):
        self.cookies = http.cookiejar.CookieJar()
        self.opener = build_opener(HTTPCookieProcessor(self.cookies))
        self.csrf = None

    def request(self, method, path, body=None, headers=None, protect=True):
        headers = dict(headers or {})
        if body is not None:
            headers.setdefault('Content-Type', 'application/json')
        if protect and method not in ('GET', 'HEAD') and self.csrf:
            headers[self.csrf['header_name']] = self.csrf['token']
        request = Request(BASE + path, data=json.dumps(body).encode() if body is not None else None, headers=headers, method=method)
        try:
            response = self.opener.open(request, timeout=15)
        except HTTPError as error:
            response = error
        raw = response.read()
        payload = json.loads(raw) if raw and 'json' in response.headers.get('Content-Type', '') else raw.decode('utf-8', errors='replace')
        return response.status, payload, response.headers

    def token(self):
        status, body, _ = self.request('GET', '/api/v1/session/csrf')
        assert status == 200
        self.csrf = body['data']

    def login(self, email, password):
        self.token()
        status, body, _ = self.request('POST', '/api/v1/sessions', {'email': email, 'password': password})
        assert status == 200, (status, body)
        self.token()


def poll(client, evaluation_id):
    for _ in range(60):
        status, body, _ = client.request('GET', PREFIX + '/quality-evaluations/' + evaluation_id)
        assert status == 200, (status, body)
        if body['data']['state'] in ('COMPLETED', 'FAILED'):
            return body['data']
        time.sleep(.5)
    raise AssertionError('La evaluación no llegó a un estado final en 30 segundos.')


def main():
    require_isolated_test_environment()
    quality = Client()
    status, health, _ = quality.request('GET', '/api/v1/health')
    check(status == 200 and health['status'] == 'UP', 'Salud de aplicación y PostgreSQL')
    status, _, _ = quality.request('GET', '/')
    check(status == 200, 'React servido por Spring Boot')
    status, problem, headers = quality.request('GET', '/api/v1/me')
    check(status == 401 and problem['status'] == status and problem['request_id'] == headers['X-Request-ID'], 'Sesión obligatoria y correlación del error')
    status, _, _ = quality.request('POST', '/api/v1/sessions', {'email': 'calidad@example.test', 'password': CONFIG['M2_QUALITY_PASSWORD']}, protect=False)
    check(status == 403, 'Login protegido por CSRF')
    quality.login('calidad@example.test', CONFIG['M2_QUALITY_PASSWORD'])
    status, body, _ = quality.request('GET', PREFIX + '/assets')
    check(status == 200 and len([a for a in body['data'] if a.get('source_kind') == 'MOCK']) == 4, 'Catálogo ficticio autorizado')
    assets = body['data']
    customers = next(a for a in assets if '100 filas' in a['name'])
    status, rules, _ = quality.request('GET', PREFIX + '/quality-rules?asset_id=' + customers['id'])
    check(status == 200 and len(rules['data']) >= 2, 'Reglas iniciales persistidas')
    rule_ids = [r['id'] for r in rules['data'] if r['name'] in ('Correo obligatorio', 'Identificador único') and r['enabled']]
    key = str(uuid.uuid4())
    command = {'asset_id': customers['id'], 'metadata_version_id': customers['metadata_version_id'], 'rule_ids': rule_ids}
    status, accepted, headers = quality.request('POST', PREFIX + '/quality-evaluations', command, {'Idempotency-Key': key})
    check(status == 202 and headers.get('Location', '').endswith(accepted['data']['id']), 'Evaluación aceptada con Location')
    evaluation_id = accepted['data']['id']
    status, repeated, _ = quality.request('POST', PREFIX + '/quality-evaluations', command, {'Idempotency-Key': key})
    check(status == 202 and repeated['data']['id'] == evaluation_id, 'Reintento idempotente sin duplicación')
    changed = dict(command, rule_ids=rule_ids[:1])
    status, _, _ = quality.request('POST', PREFIX + '/quality-evaluations', changed, {'Idempotency-Key': key})
    check(status == 409, 'Clave idempotente con otra solicitud rechazada')
    completed = poll(quality, evaluation_id)
    check(completed['state'] == 'COMPLETED' and completed['quality_outcome'] == 'FAIL', 'Finalización técnica independiente de la calidad')
    status, body, _ = quality.request('GET', PREFIX + '/quality-evaluations/' + evaluation_id + '/results')
    versions = {v['id']: v for v in body['data']['rule_versions']}
    measured = {versions[r['rule_version_id']]['dimension']: r for r in body['data']['results']}
    check(measured['COMPLETENESS']['numerator'] == 95 and measured['COMPLETENESS']['metric_value'] == 95 and measured['UNIQUENESS']['metric_value'] == 98, 'Indicadores calculados y publicados en PostgreSQL')
    status, another, _ = quality.request('POST', PREFIX + '/quality-evaluations', command, {'Idempotency-Key': str(uuid.uuid4())})
    check(status == 202 and another['data']['id'] != evaluation_id, 'Nueva evaluación de la misma captura sin duplicar la referencia de entrada')
    repeat = poll(quality, another['data']['id'])
    check(repeat['quality_outcome'] == completed['quality_outcome'], 'Repetición de captura con la misma calidad')
    status, body, _ = quality.request('GET', PREFIX + '/quality-incidents?asset_id=' + customers['id'])
    check(status == 200 and body['meta']['page_info']['returned_count'] >= 7, 'Incidencias vinculadas y paginadas')
    incident = next(i for i in body['data'] if i['status'] == 'OPEN')
    status, _, _ = quality.request('POST', PREFIX + '/quality-incidents/' + incident['id'] + '/events', {'status': 'IN_PROGRESS', 'comment': 'Seguimiento ficticio de prueba'}, {'If-Match': '"' + str(incident['revision']) + '"'})
    check(status == 201, 'Seguimiento con control de revisión')
    status, history, _ = quality.request('GET', PREFIX + '/quality-incidents/' + incident['id'] + '/events')
    check(status == 200 and history['data'][-1]['comment'] == 'Seguimiento ficticio de prueba', 'Comentario cifrado recuperado por usuario autorizado')
    for word, final, outcome in [('vacía', 'COMPLETED', 'NO_DATA'), ('parcial', 'FAILED', 'INCOMPLETE')]:
        asset = next(a for a in assets if word in a['name'])
        _, body, _ = quality.request('GET', PREFIX + '/quality-rules?asset_id=' + asset['id'])
        request = {'asset_id': asset['id'], 'metadata_version_id': asset['metadata_version_id'], 'rule_ids': [body['data'][0]['id']]}
        status, body, _ = quality.request('POST', PREFIX + '/quality-evaluations', request, {'Idempotency-Key': str(uuid.uuid4())})
        assert status == 202, body
        result = poll(quality, body['data']['id'])
        check(result['state'] == final and result['quality_outcome'] == outcome, 'Entrada ' + word + ' sin aprobación artificial')
        _, body, _ = quality.request('GET', PREFIX + '/quality-evaluations/' + result['id'] + '/results')
        check(all(r['metric_value'] is None for r in body['data']['results']), 'Sin porcentaje final para entrada ' + word)
    rule = rules['data'][0]
    status, _, _ = quality.request('PATCH', PREFIX + '/quality-rules/' + rule['id'], {'enabled': True}, {'Content-Type': 'application/merge-patch+json'})
    check(status == 428, 'If-Match obligatorio')
    status, _, _ = quality.request('PATCH', PREFIX + '/quality-rules/' + rule['id'], {'enabled': True}, {'Content-Type': 'application/merge-patch+json', 'If-Match': '"99999"'})
    check(status == 412, 'Revisión obsoleta rechazada')
    config = {'dimension': 'COMPLETENESS', 'definition': {'kind': 'REQUIRED', 'fields': ['correo'], 'blank_is_missing': True}, 'threshold': 98, 'minimum_coverage': 100, 'critical': False}
    status, created, _ = quality.request('POST', PREFIX + '/quality-rules', {'asset_id': customers['id'], 'name': 'Versionado API ' + str(uuid.uuid4())[:8], 'version': config})
    assert status == 201, created
    new_rule = created['data']
    next_config = dict(config, threshold=97)
    status, new_version, _ = quality.request('POST', PREFIX + '/quality-rules/' + new_rule['id'] + '/versions', next_config, {'If-Match': '"' + str(new_rule['revision']) + '"'})
    check(status == 201 and new_version['data']['version_number'] == 2, 'Nueva versión de regla inmutable')
    status, versioned, _ = quality.request('GET', PREFIX + '/quality-rules/' + new_rule['id'])
    check(status == 200 and versioned['data']['revision'] > new_rule['revision'] and len(versioned['data']['versions']) == 2 and versioned['data']['versions'][1]['threshold'] == 98, 'Revisión avanzada y versión histórica conservada')
    status, _, _ = quality.request('GET', PREFIX + '/quality-rules?asset_id=' + customers['id'] + '&page_size=101')
    check(status == 422, 'Paginación fuera de rango rechazada')
    bad = dict(command, actor_id=str(uuid.uuid4()))
    status, _, _ = quality.request('POST', PREFIX + '/quality-evaluations', bad, {'Idempotency-Key': str(uuid.uuid4())})
    check(status == 422, 'Campo actor no admitido en el cuerpo')
    status, _, _ = quality.request('GET', '/api/v1/projects/' + str(uuid.uuid4()) + '/quality-evaluations')
    check(status in (403, 404), 'Aislamiento de proyecto ajeno')
    admin = Client()
    admin.login('admin@example.test', CONFIG['M2_ADMIN_PASSWORD'])
    status, _, _ = admin.request('GET', PREFIX + '/quality-evaluations')
    check(status == 403, 'Administrador sin permiso implícito de lectura empresarial')
    status, _, _ = admin.request('GET', '/api/v1/users?project_id=' + PROJECT)
    check(status == 200, 'Administración de cuentas autorizada')
    status, _, _ = quality.request('DELETE', '/api/v1/sessions/current')
    check(status == 204, 'Cierre de sesión')
    status, _, _ = quality.request('GET', '/api/v1/me')
    check(status == 401, 'Sesión cerrada sin acceso posterior')
    print(json.dumps({'passed': len(checks), 'checks': checks}, ensure_ascii=False), flush=True)


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()

