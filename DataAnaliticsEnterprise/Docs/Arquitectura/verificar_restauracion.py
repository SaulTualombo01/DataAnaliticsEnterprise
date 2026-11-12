"""Compara trazabilidad y autorización antes/después de restaurar un entorno ficticio aislado."""
import importlib.util
import json
import sys
from pathlib import Path

spec = importlib.util.spec_from_file_location('verification', Path(__file__).with_name('verificar_api.py'))
api = importlib.util.module_from_spec(spec)
spec.loader.exec_module(api)
api.require_isolated_test_environment()


def read(client, path):
    status, body, _ = client.request('GET', path)
    assert status == 200, (status, path)
    return body['data']


def main():
    mode, snapshot_file = sys.argv[1:3]
    quality = api.Client()
    quality.login('calidad@example.test', api.CONFIG['M2_QUALITY_PASSWORD'])
    if mode == 'snapshot':
        project = api.PREFIX
        evaluations = read(quality, project + '/quality-evaluations')
        assert evaluations and evaluations[0]['state'] == 'COMPLETED'
        evaluation = evaluations[0]
        incidents = read(quality, project + '/quality-incidents?evaluation_id=' + evaluation['id'])
        assert incidents
        snapshot = {'evaluation_id': evaluation['id'], 'asset_id': evaluation['asset_id'], 'incident_id': incidents[0]['id']}
        snapshot['cookie'] = '; '.join(f'{c.name}={c.value}' for c in quality.cookies)
    else:
        snapshot = json.loads(Path(snapshot_file).read_text(encoding='utf-8'))
        old = api.Client()
        status, _, _ = old.request('GET', '/api/v1/me', headers={'Cookie': snapshot['cookie']})
        assert status == 401, 'El respaldo no debe resucitar la sesión anterior'
    prefix = api.PREFIX
    paths = {
        'evaluation': prefix + '/quality-evaluations/' + snapshot['evaluation_id'],
        'results': prefix + '/quality-evaluations/' + snapshot['evaluation_id'] + '/results',
        'incidents': prefix + '/quality-incidents?evaluation_id=' + snapshot['evaluation_id'],
        'overview': prefix + '/assets/' + snapshot['asset_id'] + '/quality-overview',
        'evidence': prefix + '/quality-incidents/' + snapshot['incident_id'] + '/evidence',
    }
    actual = {name: read(quality, path) for name, path in paths.items()}
    assert actual['evidence']['available']
    assert actual['evidence']['values'] is not None
    assert actual['results']['results'] and actual['results']['rule_versions']
    admin = api.Client()
    admin.login('admin@example.test', api.CONFIG['M2_ADMIN_PASSWORD'])
    status, _, _ = admin.request('GET', paths['evidence'])
    assert status in (403,404), f'El administrador técnico no puede obtener la evidencia: {status}'
    if mode == 'snapshot':
        snapshot['data'] = actual
        Path(snapshot_file).write_text(json.dumps(snapshot, ensure_ascii=False), encoding='utf-8')
    else:
        assert actual == snapshot['data'], 'La restauración cambió resultados, versiones, referencias o evidencia'
    print('OK trazabilidad y permisos de evidencia ' + mode)


if __name__ == '__main__':
    main()
