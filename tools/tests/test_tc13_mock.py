from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from pathlib import Path
from threading import Thread
from urllib.error import HTTPError
from urllib.request import Request, urlopen
import copy
import json
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from mock_tc13 import CONTRACTS, FIXTURE, MockState, create_server, encode, sha256
from jsonschema import Draft202012Validator, FormatChecker
from validar_contratos import validate_read

SCHEMA=json.loads((CONTRACTS/'tc13.schema.json').read_text(encoding='utf-8'))
VALIDATORS={name:Draft202012Validator({**SCHEMA,'$ref':'#/$defs/'+name},format_checker=FormatChecker()) for name in ('ReadResponse','ReceiptResponse','CaptureResponse','Problem')}

def request_body(mode='live'):
    return {'asset_id':FIXTURE['id'],'metadata_version_id':FIXTURE['metadata_version_id'],
        'field_ids':FIXTURE['field_ids'],'mode':mode,'max_rows':10000,'max_bytes':10485760,'timeout_ms':60000}

class Tc13MockTests(unittest.TestCase):
    def setUp(self):
        self.now = datetime(2026,11,11,tzinfo=timezone.utc)
        self.state = MockState(lambda:self.now)
        self.server = create_server(0,self.state)
        self.thread = Thread(target=self.server.serve_forever,daemon=True); self.thread.start()
        self.base = f'http://127.0.0.1:{self.server.server_port}/api/v1/projects/{FIXTURE["project_id"]}'
    def tearDown(self):
        self.server.shutdown(); self.server.server_close(); self.thread.join(timeout=5)
        self.assertFalse(self.thread.is_alive())
    def call(self,path='/reads',body=None,scenario='normal',raw=None,method=None):
        data = raw if raw is not None else encode(body) if body is not None else None
        request = Request(self.base+path,data=data,method=method,headers={'Content-Type':'application/json','X-Mock-Scenario':scenario})
        try: response=urlopen(request,timeout=5)
        except HTTPError as error: response=error
        with response:
            result=json.loads(response.read())
            self.assertEqual(response.headers['X-Mock-Response'],'true')
            self.assertEqual(response.headers['Cache-Control'],'no-store')
            self.assertEqual(response.headers['X-Request-Id'],result.get('request_id') or result['meta']['request_id'])
            schema='Problem' if response.status>=400 else 'ReadResponse' if response.status==201 else 'CaptureResponse' if path.startswith('/captures/') else 'ReceiptResponse'
            VALIDATORS[schema].validate(result)
            if schema=='ReadResponse': validate_read(result['data'])
            return response.status,result
    def test_live_preserves_null_missing_duplicates_and_business_anomalies_without_capture(self):
        status,result=self.call(body=request_body())
        self.assertEqual(status,201); read=result['data']['read']; rows=result['data']['records']
        self.assertEqual(read['execution_state'],'COMPLETED'); self.assertIsNone(read['capture'])
        self.assertNotEqual(read['read_id'],read['execution_id'])
        self.assertNotEqual(read['read_id'],read['metadata_version_id'])
        self.assertEqual(rows[0]['values'][0]['raw_value'],rows[1]['values'][0]['raw_value'])
        self.assertEqual(rows[1]['values'][1]['state'],'NULL'); self.assertEqual(rows[2]['values'][1]['state'],'MISSING')
        self.assertEqual(rows[1]['values'][2]['raw_value'],'invalid_boolean')
        self.assertEqual(read['counts']['serialized_bytes'],len(encode(rows)))
        self.assertFalse(self.state.captures)
        status,receipt=self.call('/reads/'+read['read_id'])
        self.assertEqual(status,200); self.assertNotIn('records',receipt['data'])
    def test_empty_completes_without_quality_percentage_or_zero_division(self):
        status,result=self.call(body=request_body(),scenario='empty')
        self.assertEqual(status,201); self.assertEqual(result['data']['records'],[])
        self.assertEqual(result['data']['read']['execution_state'],'COMPLETED')
        self.assertIsNone(result['data']['read']['counts']['coverage_ratio'])
    def test_partial_fails_technical_scope_without_inventing_a_capture(self):
        status,result=self.call(body=request_body('capture'),scenario='partial')
        read=result['data']['read']; self.assertEqual(status,201)
        self.assertEqual(read['execution_state'],'FAILED'); self.assertFalse(read['scope']['complete'])
        self.assertEqual(read['scope']['partial_reason'],'SOURCE_INCOMPLETE'); self.assertIsNone(read['capture'])
    def test_row_and_byte_limits_never_present_partial_as_complete(self):
        for key,value,reason in [('max_rows',1,'ROW_LIMIT'),('max_bytes',2,'BYTE_LIMIT')]:
            with self.subTest(key=key):
                body=request_body(); body[key]=value
                _,result=self.call(body=body); read=result['data']['read']
                self.assertEqual(read['execution_state'],'FAILED'); self.assertEqual(read['scope']['partial_reason'],reason)
                self.assertLessEqual(read['counts']['serialized_bytes'],body['max_bytes'])
    def test_capture_replays_exact_records_then_expires_without_rereading(self):
        _,result=self.call(body=request_body('capture')); read=result['data']['read']; capture=read['capture']
        self.assertEqual(capture['content_hash'],sha256(encode(result['data']['records'])).hexdigest())
        status,replay=self.call('/captures/'+capture['capture_id'])
        self.assertEqual(status,200); self.assertEqual(replay['data']['records'],result['data']['records'])
        self.assertEqual(self.call('/captures/'+capture['capture_id'],scenario='source_disabled')[0],200)
        self.now+=timedelta(days=8)
        self.assertEqual(self.call('/captures/'+capture['capture_id'])[0],410)
        status,receipt=self.call('/reads/'+read['read_id'])
        self.assertEqual(status,200); self.assertEqual(receipt['data']['capture']['capture_id'],capture['capture_id'])
    def test_permission_error_scenarios_are_explicit_and_never_create_receipts(self):
        for scenario,status in [('denied',403),('unauthenticated',401),('unavailable',503),('source_disabled',409)]:
            with self.subTest(scenario=scenario): self.assertEqual(self.call(body=request_body(),scenario=scenario)[0],status)
        self.assertFalse(self.state.receipts)
    def test_unknown_context_asset_version_or_field_is_hidden(self):
        for key in ('asset_id','metadata_version_id','field_ids'):
            body=request_body(); body[key]=['00000000-0000-0000-0000-000000000999'] if key=='field_ids' else '00000000-0000-0000-0000-000000000999'
            with self.subTest(key=key): self.assertEqual(self.call(body=body)[0],404)
        self.base=self.base.replace(FIXTURE['project_id'],'00000000-0000-0000-0000-000000000999')
        self.assertEqual(self.call(body=request_body())[0],404)
    def test_rejects_actor_sql_duplicates_unknown_fields_and_invalid_limits(self):
        bodies=[{**request_body(),'actor_id':FIXTURE['id']},{**request_body(),'sql':'select * from sensitive'},
            {**request_body(),'max_rows':True},{**request_body(),'max_rows':10001},
            {**request_body(),'field_ids':[FIXTURE['field_ids'][0]]*2},{**request_body(),'mode':[]}]
        for body in bodies:
            with self.subTest(body=body): self.assertEqual(self.call(body=body)[0],422)
        self.assertEqual(self.call(raw=b'{"mode":"live","mode":"capture"}')[0],422)
        self.assertEqual(self.call(raw=b'{"max_rows":NaN}')[0],422)
        self.assertEqual(self.call(raw=b'\xff')[0],422)
        self.assertEqual(self.call(raw=b'x'*65537)[0],413)
    def test_projects_cannot_retrieve_another_projects_receipt_or_capture(self):
        _,result=self.call(body=request_body('capture')); read=result['data']['read']
        self.base=self.base.replace(FIXTURE['project_id'],'00000000-0000-0000-0000-000000000999')
        self.assertEqual(self.call('/reads/'+read['read_id'])[0],404)
        self.assertEqual(self.call('/captures/'+read['capture']['capture_id'])[0],404)
    def test_simultaneous_reads_have_distinct_ids_and_immutable_results(self):
        with ThreadPoolExecutor(max_workers=4) as pool:
            results=list(pool.map(lambda _:self.call(body=request_body('capture')),range(4)))
        self.assertEqual(len({r[1]['data']['read']['read_id'] for r in results}),4)
        self.assertEqual(len({r[1]['data']['read']['capture']['capture_id'] for r in results}),4)
        read=results[0][1]['data']['read']; copy_read=copy.deepcopy(read)
        read['counts']['returned_rows']=999
        self.assertEqual(self.call('/reads/'+read['read_id'])[1]['data'],copy_read)

if __name__=='__main__': unittest.main()
