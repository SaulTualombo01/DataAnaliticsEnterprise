"""Carga acordada: cinco gestores simultáneos, un CSV de 10.000 registros, dos reglas."""
import concurrent.futures
import importlib.util
import json
import time
import uuid
from pathlib import Path

spec = importlib.util.spec_from_file_location('csv_verification', Path(__file__).with_name('verificar_csv.py'))
csv = importlib.util.module_from_spec(spec)
spec.loader.exec_module(csv)
api = csv.api


def main():
    api.require_isolated_test_environment()
    quality, admin = api.Client(), api.Client()
    quality.login('calidad@example.test', api.CONFIG['M2_QUALITY_PASSWORD'])
    admin.login('admin@example.test', api.CONFIG['M2_ADMIN_PASSWORD'])
    content = ('id,correo\n' + ''.join(f'{i},' + ('' if i % 100 == 0 else f'cliente{i}@example.test') + '\n' for i in range(10000))).encode()
    status, uploaded = csv.multipart(quality, api.PREFIX + '/csv-inputs', content, filename='carga-10000.csv')
    assert status == 201, status
    asset = uploaded['data']
    rules = []
    for name, dimension, kind, field in [('Correo carga', 'COMPLETENESS', 'REQUIRED', 'correo'), ('Clave carga', 'UNIQUENESS', 'UNIQUE_KEY', 'id')]:
        status, rule, _ = quality.request('POST', api.PREFIX + '/quality-rules', {
            'asset_id': asset['id'], 'name': name,
            'version': {'dimension': dimension, 'definition': {'kind': kind, 'fields': [field], 'blank_is_missing': True},
                        'threshold': 100, 'minimum_coverage': 100, 'critical': False}})
        assert status == 201, status
        rules.append(rule['data']['id'])
    clients = []
    for index in range(5):
        email, password = f'load-{index}-{uuid.uuid4().hex}@example.test', 'Load-Test-' + uuid.uuid4().hex
        status, _, _ = admin.request('POST', '/api/v1/users?project_id=' + api.PROJECT,
                                     {'email': email, 'password': password, 'role': 'QUALITY_MANAGER'})
        assert status == 201, status
        client = api.Client()
        client.login(email, password)
        clients.append(client)
    command = {'asset_id': asset['id'], 'metadata_version_id': asset['metadata_version_id'], 'snapshot_id': asset['snapshot_id'], 'rule_ids': rules}

    def evaluate(client):
        started = time.monotonic()
        status, response, _ = client.request('POST', api.PREFIX + '/quality-evaluations', command, {'Idempotency-Key': str(uuid.uuid4())})
        assert status == 202, status
        job = api.poll(client, response['data']['id'])
        assert job['state'] == 'COMPLETED', job.get('failure_code')
        status, report, _ = client.request('GET', api.PREFIX + '/quality-evaluations/' + job['id'] + '/results')
        assert status == 200
        counts = sorted((r['numerator'], r['denominator']) for r in report['data']['results'])
        assert counts == [(9900, 10000), (10000, 10000)], counts
        return round(time.monotonic() - started, 3)

    started = time.monotonic()
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as executor:
        elapsed = list(executor.map(evaluate, clients))
    summary = {'users': 5, 'rows_per_evaluation': 10000, 'rules': 2, 'evaluations_completed': len(elapsed),
               'total_seconds': round(time.monotonic() - started, 3), 'evaluation_seconds': elapsed}
    (api.ROOT / 'target/security-load-summary.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
    print('OK carga de referencia: ' + json.dumps(summary))


if __name__ == '__main__':
    main()
