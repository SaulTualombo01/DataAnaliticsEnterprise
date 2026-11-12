"""Recorrido real de carga y análisis CSV contra el Compose local; no usa servicios remotos."""
import importlib.util
import json
import sys
import uuid
from pathlib import Path
from urllib.request import Request
from urllib.error import HTTPError

spec=importlib.util.spec_from_file_location('verification',Path(__file__).with_name('verificar_api.py'))
api=importlib.util.module_from_spec(spec)
spec.loader.exec_module(api)
checks=[]

def check(condition,name):
    if not condition: raise AssertionError(name)
    checks.append(name)
    print('OK '+name,flush=True)

def multipart(client,path,content,filename='clientes.csv',fields=None):
    boundary='m2csv'+uuid.uuid4().hex
    body=bytearray()
    for name,value in (fields or {}).items():
        body.extend(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode())
    body.extend(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\nContent-Type: text/csv\r\n\r\n'.encode())
    body.extend(content)
    body.extend(f'\r\n--{boundary}--\r\n'.encode())
    headers={'Content-Type':'multipart/form-data; boundary='+boundary,client.csrf['header_name']:client.csrf['token']}
    try: response=client.opener.open(Request(api.BASE+path,data=bytes(body),headers=headers,method='POST'),timeout=20)
    except HTTPError as error: response=error
    return response.status,json.loads(response.read())

def main():
    api.require_isolated_test_environment()
    quality=api.Client();quality.login('calidad@example.test',api.CONFIG['M2_QUALITY_PASSWORD'])
    content='Cédula;Nombre Cliente;correo;edad\r\n001;"Ana; Pérez";ana@example.test;25\r\n002;"Dos\r\nlíneas";;invalido\r\n002;Otro;correo-invalido;150\r\n'.encode('utf-8')
    route=api.PREFIX+'/csv-inputs'
    status,preview=multipart(quality,route+'/preview',content,fields={'separator':'AUTO'})
    check(status==200 and preview['data']['row_count']==3 and preview['data']['headers'][0]=='Cédula','Vista previa con punto y coma, comillas y Unicode')
    check(preview['data']['preview_rows'][1][2]=='','Vacíos conservados en vista previa')
    status,uploaded=multipart(quality,route,content,fields={'name':'CSV API '+uuid.uuid4().hex[:8],'separator':'AUTO','column_types':json.dumps({'edad':'NUMBER'})})
    check(status in (200,201),'Archivo cifrado importado o activo idéntico reutilizado')
    asset=uploaded['data'];asset_id=asset['id']
    status,catalog,_=quality.request('GET',api.PREFIX+'/assets')
    check(status==200 and any(a['id']==asset_id and a['source_kind']=='CSV' for a in catalog['data']),'CSV visible como activo autorizado')
    rules=[]
    for name,dimension,kind,fields,extra in [('Correo requerido','COMPLETENESS','REQUIRED',['correo'],{}),('Correo válido','VALIDITY','EMAIL',['correo'],{}),('Cédula única','UNIQUENESS','UNIQUE_KEY',['Cédula'],{}),('Edad válida','VALIDITY','NUMBER_RANGE',['edad'],{'minimum':0,'maximum':120})]:
        version={'dimension':dimension,'definition':{'kind':kind,'fields':fields,'blank_is_missing':True,**extra},'threshold':100,'minimum_coverage':100,'critical':False}
        status,created,_=quality.request('POST',api.PREFIX+'/quality-rules',{'asset_id':asset_id,'name':name,'version':version})
        assert status==201,created
        rules.append(created['data']['id'])
    check(len(rules)==4,'Reglas sobre columnas reales, incluidos espacios y tildes')
    command={'asset_id':asset_id,'metadata_version_id':asset['metadata_version_id'],'snapshot_id':asset['snapshot_id'],'rule_ids':rules}
    status,accepted,_=quality.request('POST',api.PREFIX+'/quality-evaluations',command,{'Idempotency-Key':str(uuid.uuid4())})
    assert status==202,accepted
    evaluation=api.poll(quality,accepted['data']['id'])
    check(evaluation['state']=='COMPLETED' and evaluation['quality_outcome']=='FAIL','Evaluación real del CSV completada sin aprobar incumplimientos')
    _,body,_=quality.request('GET',api.PREFIX+'/quality-evaluations/'+evaluation['id']+'/results')
    check(sorted((r['numerator'],r['denominator']) for r in body['data']['results'])==[(1,3),(1,3),(2,3),(2,3)],'Conteos correctos de completitud, formato, unicidad y rango numérico')
    _,incidents,_=quality.request('GET',api.PREFIX+'/quality-incidents?asset_id='+asset_id+'&evaluation_id='+evaluation['id'])
    values=incidents['data']
    check(len(values)==6 and all(i.get('rule_name') for i in values),'Incidencias incluyen la regla que se incumple')
    check(any(i['field_name']=='correo' and i['problem_code']=='MISSING_VALUE' and i['record_reference']=='3' for i in values),'Faltante localizado en fila física 3')
    check(any(i['problem_code']=='DUPLICATE_KEY' and i['record_reference']=='5' for i in values),'Duplicado localizado en fila física 5 después de una celda multilínea')
    check(all(i['evaluation_id']==evaluation['id'] for i in values),'Filtro de incidencias por evaluación aplicado antes de paginar')
    status,again,_=quality.request('POST',api.PREFIX+'/quality-evaluations',command,{'Idempotency-Key':str(uuid.uuid4())})
    assert status==202,again
    repeated=api.poll(quality,again['data']['id'])
    check(repeated['state']=='COMPLETED' and repeated['quality_outcome']=='FAIL','La misma captura CSV se puede volver a evaluar')
    status,_=multipart(quality,route+'/preview',b'id,id\n1,2')
    check(status==422,'Encabezados duplicados rechazados')
    status,_=multipart(quality,route+'/preview',b'id,correo\n1')
    check(status==422,'Filas con estructura inconsistente rechazadas')
    status,_=multipart(quality,route+'/preview',b'\xff\xfe\x00\x00')
    check(status==422,'Codificación no UTF-8 rechazada')
    admin=api.Client();admin.login('admin@example.test',api.CONFIG['M2_ADMIN_PASSWORD'])
    status,_=multipart(admin,route+'/preview',content)
    check(status==403,'Administrador técnico sin permiso de contenido no puede cargar CSV')
    status,_,_=quality.request('GET','/api/v1/projects/'+str(uuid.uuid4())+'/quality-evaluations/'+evaluation['id'])
    check(status in (403,404),'CSV y resultados aislados de otros proyectos')
    (api.ROOT/'target/csv-verification.json').write_text(json.dumps({'asset_id':asset_id,'evaluation_id':evaluation['id'],'passed':len(checks),'checks':checks},ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'passed':len(checks),'asset_id':asset_id,'evaluation_id':evaluation['id']},ensure_ascii=False))

if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()
