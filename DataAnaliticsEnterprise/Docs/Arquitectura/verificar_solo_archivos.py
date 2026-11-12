"""Verifica el modo de archivos subidos sin crear activos, reglas ni evaluaciones de prueba."""
import uuid
import verificar_csv as csv

api=csv.api

def main():
    quality=api.Client();quality.login('calidad@example.test',api.CONFIG['M2_QUALITY_PASSWORD'])
    passed=[]
    def check(condition,name):
        if not condition: raise AssertionError(name)
        passed.append(name);print('OK '+name,flush=True)
    status,catalog,_=quality.request('GET',api.PREFIX+'/assets')
    check(status==200 and all(a['source_kind']=='CSV' for a in catalog['data']),'Catálogo exclusivo de archivos CSV subidos')
    ids={a['id'] for a in catalog['data']}
    check(len(ids)==len(catalog['data']),'Identificadores de activos sin duplicación')
    status,history,_=quality.request('GET',api.PREFIX+'/quality-evaluations?page_size=100')
    check(status==200 and all(e['asset_id'] in ids for e in history['data']),'Historial vinculado a archivos conservados')
    status,incidents,_=quality.request('GET',api.PREFIX+'/quality-incidents?page_size=100')
    check(status==200 and all(i['asset_id'] in ids for i in incidents['data']),'Incidencias vinculadas a archivos conservados')
    old='f7c83e25-073f-4ddb-b72b-e4669d242d37'
    metadata='8660c613-f093-4a39-a5ed-a84f64fef022'
    status,_,_=quality.request('POST',api.PREFIX+'/quality-evaluations',{'asset_id':old,'metadata_version_id':metadata,'rule_ids':[str(uuid.uuid4())]},{'Idempotency-Key':str(uuid.uuid4())})
    check(status==404,'No permite evaluar activos sintéticos antiguos por identificador')
    status,_,_=quality.request('POST',api.PREFIX+'/quality-rules',{'asset_id':old,'name':'Regla rechazada','version':{'dimension':'COMPLETENESS','definition':{'kind':'REQUIRED','fields':['correo'],'blank_is_missing':True},'threshold':100,'minimum_coverage':100,'critical':False}})
    check(status==404,'No permite recrear reglas sobre datos ficticios retirados')
    status,preview=csv.multipart(quality,api.PREFIX+'/csv-inputs/preview',b'id,correo\n1,\n')
    check(status==200 and preview['data']['row_count']==1,'Vista previa CSV disponible sin importar datos de prueba')
    _,after,_=quality.request('GET',api.PREFIX+'/assets')
    _,history_after,_=quality.request('GET',api.PREFIX+'/quality-evaluations?page_size=100')
    check(catalog['data']==after['data'] and history['data']==history_after['data'],'Verificación no agrega activos ni evaluaciones')
    print('Comprobaciones aprobadas: '+str(len(passed)))

if __name__=='__main__':main()
