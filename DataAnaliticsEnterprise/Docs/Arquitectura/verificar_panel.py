"""Solo lectura: compara el panel con evaluaciones existentes; nunca crea datos."""
import sys
import verificar_api as api


def main():
    checks = []

    def check(condition, message):
        if not condition:
            raise AssertionError(message)
        checks.append(message)
        print('OK ' + message)

    def history(client, asset):
        rows, page = [], 1
        while True:
            status, body, _ = client.request('GET', api.PREFIX + '/quality-evaluations?asset_id=' + asset + '&page_size=100&page=' + str(page))
            check(status == 200, 'Historial disponible')
            rows.extend(body['data'])
            if not body['meta']['page_info']['has_next']:
                return rows
            page += 1

    client = api.Client()
    client.login('calidad@example.test', api.CONFIG['M2_QUALITY_PASSWORD'])
    status, catalog, _ = client.request('GET', api.PREFIX + '/assets')
    check(status == 200, 'Catálogo autorizado disponible')
    combined = 0
    for asset in catalog['data']:
        path = api.PREFIX + '/assets/' + asset['id'] + '/quality-overview'
        before = history(client, asset['id'])
        status, response, _ = client.request('GET', path)
        check(status == 200, 'Panel del activo disponible')
        panel = response['data']
        check((panel['latest_evaluation'] or {}).get('id') == (before[0]['id'] if before else None), 'Última ejecución independiente de las dimensiones históricas')
        expected = {}
        for evaluation in before:
            if evaluation['state'] != 'COMPLETED':
                continue
            status, body, _ = client.request('GET', api.PREFIX + '/quality-evaluations/' + evaluation['id'] + '/results')
            check(status == 200, 'Medición histórica disponible')
            versions = {v['id']: v for v in body['data']['rule_versions']}
            groups = {}
            for result in body['data']['results']:
                groups.setdefault(versions[result['rule_version_id']]['dimension'], []).append(result)
            for dimension, results in groups.items():
                expected.setdefault(dimension, (evaluation, results))
        check(set(expected) == set(panel['dimension_evaluations']), 'Conserva todas las dimensiones completadas')
        result_ids = set()
        for dimension, (evaluation, results) in expected.items():
            source = panel['dimension_evaluations'][dimension]
            check(source['id'] == evaluation['id'] and source['asset_id'] == asset['id'] and source['state'] == 'COMPLETED', 'Última medición completada del mismo activo por dimensión')
            check(source['submitted_at'] == evaluation['submitted_at'], 'Fecha de origen trazable')
            result_ids.update(r['id'] for r in results)
        check(result_ids == {r['id'] for r in panel['results']}, 'Reemplaza la dimensión sin arrastrar reglas antiguas')
        check({r['rule_version_id'] for r in panel['results']} == {v['id'] for v in panel['rule_versions']}, 'Versiones históricas exactas')
        versions = {v['id']: v for v in panel['rule_versions']}
        check(all(r['evaluation_id'] == panel['dimension_evaluations'][versions[r['rule_version_id']]['dimension']]['id'] for r in panel['results']), 'Cada resultado conserva su evaluación de origen')
        check(before == history(client, asset['id']), 'Panel no crea ni modifica análisis')
        status, _, _ = api.Client().request('GET', path)
        check(status == 401, 'Consulta anónima rechazada')
        admin = api.Client()
        admin.login('admin@example.test', api.CONFIG['M2_ADMIN_PASSWORD'])
        status, _, _ = admin.request('GET', path)
        check(status == 403, 'Administración técnica no concede acceso a los datos')
        if len(expected) > 1:
            combined += 1
    print('Activos con varias dimensiones históricas verificadas: ' + str(combined))
    print('Comprobaciones aprobadas: ' + str(len(checks)))


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()
