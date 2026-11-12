"""Regresión real: deduplicación, concurrencia, filtros/paginación y permisos por activo. Solo Compose local."""
import concurrent.futures
import json
import sys
import uuid
from urllib.parse import urlencode
import verificar_csv as csv

api=csv.api
checks=[]

def check(condition,name):
    if not condition: raise AssertionError(name)
    checks.append(name);print('OK '+name,flush=True)

def query(client,**filters):
    status,body,_=client.request('GET',api.PREFIX+'/quality-incidents?'+urlencode(filters))
    assert status==200,(status,body)
    return body

def create_rule(client,asset,name,kind,dimension,critical):
    status,body,_=client.request('POST',api.PREFIX+'/quality-rules',{'asset_id':asset['id'],'name':name,
        'version':{'dimension':dimension,'definition':{'kind':kind,'fields':['correo'],'blank_is_missing':True},'threshold':100,'minimum_coverage':100,'critical':critical}})
    assert status==201,body
    return body['data']['id']

def evaluate(client,asset,rules):
    status,body,_=client.request('POST',api.PREFIX+'/quality-evaluations',{'asset_id':asset['id'],'metadata_version_id':asset['metadata_version_id'],'snapshot_id':asset['snapshot_id'],'rule_ids':rules},{'Idempotency-Key':str(uuid.uuid4())})
    assert status==202,body
    result=api.poll(client,body['data']['id']);assert result['state']=='COMPLETED',result
    return result['id']

def main():
    api.require_isolated_test_environment()
    quality=api.Client();quality.login('calidad@example.test',api.CONFIG['M2_QUALITY_PASSWORD'])
    admin=api.Client();admin.login('admin@example.test',api.CONFIG['M2_ADMIN_PASSWORD'])
    marker='Prueba filtros '+uuid.uuid4().hex[:10]
    route=api.PREFIX+'/csv-inputs'
    content=('id,correo,grupo\n'+''.join(f'{i},,{marker}\n' for i in range(1,31))).encode()
    status,first=csv.multipart(quality,route,content,filename='clientes.csv',fields={'name':marker,'column_types':'{}'})
    check(status==201 and first['data']['import_reused'] is False,'Primera carga crea un único activo')
    asset=first['data']
    _,before,_=quality.request('GET',api.PREFIX+'/assets')
    status,again=csv.multipart(quality,route,content,filename='otro-nombre.csv',fields={'name':'Nombre alternativo','separator':'COMMA','column_types':json.dumps({'id':'TEXT','correo':'TEXT','grupo':'TEXT'})})
    check(status==200 and again['data']['import_reused'] and again['data']['id']==asset['id'] and again['data']['metadata_version_id']==asset['metadata_version_id'],'Mismo contenido y tipos explícitos/implícitos reutiliza identidad, captura y metadatos')
    _,after,_=quality.request('GET',api.PREFIX+'/assets')
    check(len(before['data'])==len(after['data']),'Reimportar no añade opciones al catálogo')
    second_content=('id,correo,grupo\n1,,'+marker+' B\n2,bien@example.test,B\n').encode()
    clients=[api.Client(),api.Client()]
    for client in clients: client.login('calidad@example.test',api.CONFIG['M2_QUALITY_PASSWORD'])
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        results=list(pool.map(lambda client:csv.multipart(client,route,second_content,filename='clientes.csv',fields={'name':marker}),clients))
    check(sorted(r[0] for r in results)==[200,201] and len({r[1]['data']['id'] for r in results})==1,'Dos cargas simultáneas crean un solo activo')
    second=results[0][1]['data']
    _,catalog,_=quality.request('GET',api.PREFIX+'/assets')
    labels=[a['display_name'] for a in catalog['data'] if a['id'] in (asset['id'],second['id'])]
    check(len(labels)==2 and len(set(labels))==2,'Contenidos distintos con el mismo nombre tienen etiquetas diferentes')
    status,typed=csv.multipart(quality,route,content,filename='clientes.csv',fields={'name':marker+' numérico','column_types':'{"id":"NUMBER"}'})
    check(status==201 and typed['data']['id']!=asset['id'],'Tipos distintos no se fusionan con reglas de otro conjunto')
    rules=[create_rule(quality,asset,marker+' obligatorio','REQUIRED','COMPLETENESS',True),create_rule(quality,asset,marker+' formato','EMAIL','VALIDITY',False)]
    other_rule=create_rule(quality,second,marker+' otro','REQUIRED','COMPLETENESS',False)
    evaluations=[evaluate(quality,asset,rules),evaluate(quality,asset,rules),evaluate(quality,second,[other_rule])]
    page=query(quality,q=marker,page_size=25)
    check(page['meta']['page_info']['total_count']==121 and len(page['data'])==25 and page['meta']['page_info']['has_next'],'Vista global con total real y paginación')
    all_rows=[]
    for number in range(1,6): all_rows.extend(query(quality,q=marker,page=number,page_size=25)['data'])
    check(len(all_rows)==121 and len({i['id'] for i in all_rows})==121 and {i['asset_id'] for i in all_rows}=={asset['id'],second['id']},'Todas las páginas incluyen ambos activos sin omitir ni repetir incidencias')
    check(query(quality,q=marker,severity='CRITICAL')['meta']['page_info']['total_count']==60,'Filtro de gravedad antes de paginar')
    check(query(quality,q=marker,dimension='COMPLETENESS')['meta']['page_info']['total_count']==61,'Filtro de dimensión antes de paginar')
    check(query(quality,asset_id=asset['id'],evaluation_id=evaluations[0])['meta']['page_info']['total_count']==60,'Filtros por activo y evaluación combinados')
    check(query(quality,asset_id=second['id'],q='correo')['meta']['page_info']['total_count']==1,'Búsqueda por campo')
    check(query(quality,q=marker+'%')['meta']['page_info']['total_count']==0,'Búsqueda literal sin comodines ni interpolación SQL')
    chosen=next(i for i in all_rows if i['severity']=='CRITICAL')
    status,updated,_=quality.request('POST',api.PREFIX+'/quality-incidents/'+chosen['id']+'/events',{'status':'IN_PROGRESS','comment':'Seguimiento ficticio de prueba'},{'If-Match':'"'+str(chosen['revision'])+'"'})
    assert status==201,updated
    filtered=query(quality,q=marker,status='IN_PROGRESS',severity='CRITICAL',dimension='COMPLETENESS')
    check(filtered['meta']['page_info']['total_count']==1 and filtered['data'][0]['id']==chosen['id'],'Estado, gravedad, dimensión y búsqueda combinados')
    for params in ({'status':'INVALID'},{'severity':'INVALID'},{'dimension':'INVALID'},{'page':0},{'q':'x'*161}):
        status,_,_=quality.request('GET',api.PREFIX+'/quality-incidents?'+urlencode(params));assert status==422,(params,status)
    check(True,'Filtros y paginación inválidos rechazados')
    status,_,_=admin.request('GET',api.PREFIX+'/quality-incidents')
    check(status==403,'Administrador sin permiso empresarial no puede listar incidencias')
    user_id=None
    password=uuid.uuid4().hex+'Aa1!'
    email='prueba-filtros-'+uuid.uuid4().hex+'@example.test'
    try:
        status,created,_=admin.request('POST','/api/v1/users?project_id='+api.PROJECT,{'email':email,'password':password,'role':'QUALITY_MANAGER','asset_scope_id':asset['id']})
        assert status==201,created
        user_id=created['data']['id'];scoped=api.Client();scoped.login(email,password)
        scoped_rows=query(scoped,q=marker,page_size=100)
        check(scoped_rows['meta']['page_info']['total_count']==120 and all(i['asset_id']==asset['id'] for i in scoped_rows['data']),'Vista global de usuario acotado solo devuelve su activo')
        status,_,_=scoped.request('GET',api.PREFIX+'/quality-incidents?asset_id='+second['id'])
        check(status==403,'Filtro explícito de activo ajeno denegado')
        status,_,_=admin.request('POST',api.PREFIX+'/roles',{'user_id':user_id,'role':'QUALITY_MANAGER','asset_scope_id':second['id']});assert status==201
        scoped.login(email,password)
        check(query(scoped,q=marker)['meta']['page_info']['total_count']==121,'Roles acotados a dos activos permiten consultar la unión autorizada')
        status,_,_=scoped.request('GET','/api/v1/projects/'+str(uuid.uuid4())+'/quality-incidents')
        check(status==403,'Sin acceso a incidencias de otro proyecto')
    finally:
        if user_id:
            _,users,_=admin.request('GET','/api/v1/users?project_id='+api.PROJECT)
            user=next(u for u in users['data'] if u['id']==user_id)
            status,_,_=admin.request('PATCH','/api/v1/users/'+user_id+'?project_id='+api.PROJECT,{'active':False},{'If-Match':'"'+str(user['revision'])+'"'})
            assert status==200,'No se pudo desactivar la cuenta ficticia de prueba.'
    (api.ROOT/'target/catalog-incidents-verification.json').write_text(json.dumps({'asset_id':asset['id'],'second_asset_id':second['id'],'marker':marker,'evaluation_ids':evaluations,'passed':len(checks)},ensure_ascii=False),encoding='utf-8')
    print(json.dumps({'passed':len(checks)},ensure_ascii=False))

if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8');main()
