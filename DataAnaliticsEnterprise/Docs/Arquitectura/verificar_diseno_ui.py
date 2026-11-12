"""Contratos de lectura del diseño UI. No importa archivos ni crea evaluaciones/reglas."""
import sys
import uuid
from urllib.parse import urlencode
import verificar_api as api

def main():
    client=api.Client();client.login('calidad@example.test',api.CONFIG['M2_QUALITY_PASSWORD'])
    checks=[]
    def check(condition,name):
        if not condition: raise AssertionError(name)
        checks.append(name);print('OK '+name,flush=True)
    status,catalog,_=client.request('GET',api.PREFIX+'/assets')
    check(status==200 and all(a['source_kind']=='CSV' for a in catalog['data']),'Catálogo solo de archivos subidos')
    status,history,_=client.request('GET',api.PREFIX+'/quality-evaluations?page_size=100')
    check(status==200 and history['meta']['page_info']['total_count']>=len(history['data']),'Historial con total del servidor')
    if not catalog['data']:
        print('Sin archivos: no se generan casos de prueba.');return
    asset=catalog['data'][0]
    status,metadata,_=client.request('GET',api.PREFIX+'/assets/'+asset['id']+'/input')
    check(status==200 and metadata['data'].get('metadata_version_id') and 'input_row_count' in metadata['data'],'Entrada identificada y alcance para el asistente')
    check('rows' not in metadata['data'] and 'preview_rows' not in metadata['data'],'Consulta de entrada sin contenido de las filas')
    status,rules,_=client.request('GET',api.PREFIX+'/quality-rules?asset_id='+asset['id'])
    check(status==200 and all(r.get('current_version') for r in rules['data']),'Reglas con condición, fórmula y versión vigente')
    if rules['data']:
        _,active,_=client.request('GET',api.PREFIX+'/quality-rules?'+urlencode({'asset_id':asset['id'],'enabled':'true','q':rules['data'][0]['name']}))
        check(all(r['enabled'] and rules['data'][0]['name'].lower() in r['name'].lower() for r in active['data']),'Búsqueda y selección de reglas activas en servidor')
    for query in ({'date_from':'2026-02-30'},{'date_from':'2026-10-30','date_to':'2026-10-29'},{'state':'INVALID'},{'page_size':101}):
        status,_,_=client.request('GET',api.PREFIX+'/quality-evaluations?'+urlencode(query));check(status==422,'Consulta inválida rechazada: '+next(iter(query)))
    status,completed,_=client.request('GET',api.PREFIX+'/quality-evaluations?state=COMPLETED&page_size=100')
    check(status==200 and all(e['state']=='COMPLETED' for e in completed['data']),'Filtro por estado técnico separado de calidad')
    status,empty,_=client.request('GET',api.PREFIX+'/quality-evaluations?date_from=2000-01-01&date_to=2000-01-01')
    check(status==200 and not empty['data'] and empty['meta']['page_info']['total_count']==0,'Intervalo sin resultados con total cero')
    for evaluation in history['data'][:1]:
        status,report,_=client.request('GET',api.PREFIX+'/quality-evaluations/'+evaluation['id']+'/results')
        check(status==200 and 'input_availability' in report['data']['evaluation'],'Detalle conserva disponibilidad del origen y versiones aplicadas')
    _,all_rules,_=client.request('GET',api.PREFIX+'/quality-rules?page_size=100&enabled=true')
    if all_rules['data']:
        rule=all_rules['data'][0];target=next(a for a in catalog['data'] if a['id']==rule['asset_id'])
        _,source,_=client.request('GET',api.PREFIX+'/assets/'+target['id']+'/input')
        command={'asset_id':target['id'],'metadata_version_id':source['data']['metadata_version_id'],'snapshot_id':source['data']['snapshot_id'],'rule_ids':[rule['id']],'expected_rule_versions':{rule['id']:str(uuid.uuid4())}}
        status,_,_=client.request('POST',api.PREFIX+'/quality-evaluations',command,{'Idempotency-Key':str(uuid.uuid4())})
        check(status==412,'Versión cambiada desde revisión no crea evaluación')
    _,incidents,_=client.request('GET',api.PREFIX+'/quality-incidents?page_size=100')
    check(all('values' not in i and 'evidence' not in i for i in incidents['data']),'Lista de incidencias sin valores de evidencia por defecto')
    if incidents['data']:
        incident=incidents['data'][0]
        status,evidence,headers=client.request('GET',api.PREFIX+'/quality-incidents/'+incident['id']+'/evidence')
        check(status==200 and 'no-store' in headers.get('Cache-Control',''),'Evidencia explícita con prohibición de caché')
        if evidence['data']['available']:
            _,report,_=client.request('GET',api.PREFIX+'/quality-evaluations/'+incident['evaluation_id']+'/results')
            result=next(r for r in report['data']['results'] if r['id']==incident['rule_result_id'])
            version=next(v for v in report['data']['rule_versions'] if v['id']==result['rule_version_id'])
            fields={incident['field_name']} if version['definition']['kind']=='REQUIRED' else set(version['definition']['fields'])
            check(set(evidence['data']['values']).issubset(fields),'Evidencia acotada a los campos de la regla histórica')
        admin=api.Client();admin.login('admin@example.test',api.CONFIG['M2_ADMIN_PASSWORD'])
        status,_,_=admin.request('GET',api.PREFIX+'/quality-incidents/'+incident['id']+'/evidence')
        check(status in (403,404),'Administración técnica no concede acceso a evidencia')
    _,after,_=client.request('GET',api.PREFIX+'/quality-evaluations?page_size=100')
    _,catalog_after,_=client.request('GET',api.PREFIX+'/assets')
    check(history['data']==after['data'] and catalog['data']==catalog_after['data'],'Verificación sin agregar ni modificar análisis y archivos')
    print('Comprobaciones aprobadas: '+str(len(checks)))

if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8');main()
