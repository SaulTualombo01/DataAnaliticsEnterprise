"""Mock contractual TC13: solo loopback, datos sintéticos; no implementa seguridad ni conectores."""
from collections import OrderedDict
from copy import deepcopy
from datetime import datetime, timedelta, timezone
from hashlib import sha256
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Lock
from urllib.parse import urlsplit
from uuid import uuid4
import argparse
import json
import re
from tc13_codec import record_bytes

CONTRACTS = Path(__file__).resolve().parents[1] / 'docs' / 'contratos' / 'tc13'
FIXTURE = json.loads((CONTRACTS / 'tc13.fixture.json').read_text(encoding='utf-8'))
UUID = re.compile(r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')
SCENARIOS = {'normal','empty','partial','denied','unauthenticated','unavailable','source_disabled'}
def encode(value): return json.dumps(value, ensure_ascii=False, separators=(',', ':'), allow_nan=False).encode('utf-8')
def instant(value): return value.isoformat().replace('+00:00','Z')
def problem(status, code, request_id):
    title = {401:'Inicia sesión para continuar',403:'La solicitud no está autorizada',404:'El recurso no está disponible',
        405:'Método no permitido',409:'La fuente no admite nuevas lecturas',410:'La captura ya no está disponible',
        411:'Se requiere Content-Length',413:'La solicitud excede el tamaño permitido',415:'Se requiere application/json',
        422:'Revisa la solicitud',503:'No se pudo completar la lectura'}.get(status,'No se pudo completar la solicitud')
    return {'type':'about:blank','title':title,'status':status,'detail':title,
        'instance':'urn:request:'+request_id,'code':code,'request_id':request_id}
def strict_object(pairs):
    result = {}
    for key,value in pairs:
        if key in result: raise ValueError('Duplicate key')
        result[key] = value
    return result
def reject_constant(_): raise ValueError('Non-finite value')
def valid_request(body):
    if not isinstance(body,dict) or set(body) != {'asset_id','metadata_version_id','field_ids','mode','max_rows','max_bytes','timeout_ms'}: return False
    if not all(isinstance(body[k],str) and UUID.fullmatch(body[k]) for k in ('asset_id','metadata_version_id')): return False
    fields = body['field_ids']
    if not isinstance(fields,list) or not 1 <= len(fields) <= 100 or not all(isinstance(item,str) and UUID.fullmatch(item) for item in fields): return False
    if len(set(fields)) != len(fields) or body['mode'] not in ('live','capture'): return False
    return all(type(body[key]) is int and low <= body[key] <= high for key,low,high in (
        ('max_rows',1,10000),('max_bytes',2,10485760),('timeout_ms',1,60000)))

class MockState:
    def __init__(self, clock=None):
        self.clock = clock or (lambda: datetime.now(timezone.utc))
        self.receipts = OrderedDict()
        self.captures = OrderedDict()
        self.lock = Lock()

    def create(self, project, body, scenario, request_id):
        if not valid_request(body): return 422, problem(422,'VALIDATION_ERROR',request_id)
        if project != FIXTURE['project_id'] or body['asset_id'] != FIXTURE['id'] or body['metadata_version_id'] != FIXTURE['metadata_version_id'] or not set(body['field_ids']).issubset(FIXTURE['field_ids']):
            return 404, problem(404,'RESOURCE_NOT_FOUND',request_id)
        with self.lock:
            source = [] if scenario == 'empty' else deepcopy(FIXTURE['records'])
            for row in source:
                by_id = {cell['field_id']:cell for cell in row['values']}
                row['values'] = [by_id[field] for field in body['field_ids']]
            rows = []; reason = None
            for row in source:
                if len(rows) >= body['max_rows']: reason = 'ROW_LIMIT'; break
                if len(record_bytes([*rows,row])) > body['max_bytes']: reason = 'BYTE_LIMIT'; break
                rows.append(row)
            if scenario == 'partial': rows = rows[:1]; reason = 'SOURCE_INCOMPLETE'
            now = self.clock(); capture = None
            if body['mode'] == 'capture' and reason is None:
                capture = {'capture_id':str(uuid4()),'created_at':instant(now),'expires_at':instant(now+timedelta(days=7)),
                    **{key:FIXTURE[key] for key in ('organization_id','source_id','metadata_version_id')},
                    'project_id':project,'asset_id':body['asset_id'],'field_ids':deepcopy(body['field_ids']),
                    'content_hash':sha256(record_bytes(rows)).hexdigest(),'content_hash_encoding':'json_utf8_compact_v1','representation':'raw_cells_v1','row_count':len(rows)}
                self.captures[capture['capture_id']] = (project,deepcopy(capture),deepcopy(rows))
                if len(self.captures)>1000: self.captures.popitem(last=False)
            receipt = {
                'read_id':str(uuid4()),'execution_id':str(uuid4()),'origin_request_id':request_id,
                **{key:FIXTURE[key] for key in ('organization_id','source_id','metadata_version_id')},
                'project_id':project,'asset_id':body['asset_id'],'field_ids':deepcopy(body['field_ids']),
                'observed_at':instant(now),'mode':body['mode'],'execution_state':'FAILED' if reason else 'COMPLETED',
                'scope':{'kind':'asset','complete':reason is None,'partial_reason':reason},
                'counts':{'returned_rows':len(rows),'expected_rows':len(source),'serialized_bytes':len(record_bytes(rows)),
                    'coverage_ratio':len(rows)/len(source) if source else None},
                'capture':capture,'representation':'raw_cells_v1',
            }
            self.receipts[receipt['read_id']] = deepcopy(receipt)
            if len(self.receipts)>1000: self.receipts.popitem(last=False)
            return 201, {'data':{'read':receipt,'records':rows},'meta':{'request_id':request_id}}

    def receipt(self, project, read_id, request_id):
        with self.lock:
            receipt = self.receipts.get(read_id)
            if not receipt or receipt['project_id'] != project: return 404,problem(404,'RESOURCE_NOT_FOUND',request_id)
            return 200, {'data':deepcopy(receipt),'meta':{'request_id':request_id}}

    def capture(self, project, capture_id, request_id):
        with self.lock:
            stored = self.captures.get(capture_id)
            if not stored or stored[0] != project: return 404,problem(404,'RESOURCE_NOT_FOUND',request_id)
            _,capture,rows = stored
            if self.clock() >= datetime.fromisoformat(capture['expires_at'].replace('Z','+00:00')):
                self.captures[capture_id] = (project,capture,None)
                return 410,problem(410,'CAPTURE_EXPIRED',request_id)
            return 200,{'data':{'capture':deepcopy(capture),'records':deepcopy(rows)},'meta':{'request_id':request_id}}

def create_server(port=8093, state=None):
    state = state or MockState()
    class Handler(BaseHTTPRequestHandler):
        def setup(self):
            super().setup()
            self.connection.settimeout(5)
        def log_message(self, *_): pass
        def reply(self, status, body, location=None):
            raw = encode(body)
            self.send_response(status)
            self.send_header('Content-Type','application/json' if status<400 else 'application/problem+json')
            self.send_header('Cache-Control','no-store')
            self.send_header('X-Mock-Response','true')
            self.send_header('X-Request-Id',body.get('request_id') or body['meta']['request_id'])
            self.send_header('Content-Length',str(len(raw)))
            if location: self.send_header('Location',location)
            self.end_headers(); self.wfile.write(raw)
        def handle_request(self, post=False):
            request_id = str(uuid4())
            raw_body = None
            if post:
                # Consume bounded request bodies before replying: closing with unread
                # bytes can reset TCP on Windows and discard a valid error response.
                try:
                    if self.headers.get('Transfer-Encoding') or len(self.headers.get_all('Content-Length',[]))!=1: return self.reply(411,problem(411,'LENGTH_REQUIRED',request_id))
                    length = int(self.headers['Content-Length'])
                    if not 0<length<=65536:
                        if 0<length<=1048576: self.rfile.read(length)
                        return self.reply(413,problem(413,'PAYLOAD_TOO_LARGE',request_id))
                    raw_body = self.rfile.read(length)
                except (ValueError,OSError): return self.reply(422,problem(422,'VALIDATION_ERROR',request_id))
            scenario = self.headers.get('X-Mock-Scenario','normal')
            if scenario not in SCENARIOS: return self.reply(422,problem(422,'VALIDATION_ERROR',request_id))
            if scenario in ('denied','unauthenticated','unavailable') or (scenario == 'source_disabled' and post):
                status,code = {'denied':(403,'CONTENT_ACCESS_DENIED'),'unauthenticated':(401,'AUTHENTICATION_REQUIRED'),
                    'unavailable':(503,'READ_UNAVAILABLE'),'source_disabled':(409,'SOURCE_DISABLED')}[scenario]
                return self.reply(status,problem(status,code,request_id))
            parsed = urlsplit(self.path)
            match = re.fullmatch(r'/api/v1/projects/([^/]+)/(reads|captures)(?:/([^/]+))?',parsed.path)
            if not match or not UUID.fullmatch(match[1]) or (match[3] and not UUID.fullmatch(match[3])): return self.reply(404,problem(404,'RESOURCE_NOT_FOUND',request_id))
            project,kind,identifier = match.groups()
            if parsed.query: return self.reply(422,problem(422,'VALIDATION_ERROR',request_id))
            if post:
                if kind!='reads' or identifier: return self.reply(405,problem(405,'METHOD_NOT_ALLOWED',request_id))
                if self.headers.get('Content-Type','').split(';')[0].strip().lower()!='application/json': return self.reply(415,problem(415,'UNSUPPORTED_MEDIA_TYPE',request_id))
                try:
                    body = json.loads(raw_body.decode('utf-8'),object_pairs_hook=strict_object,parse_constant=reject_constant)
                except (ValueError,UnicodeError): return self.reply(422,problem(422,'VALIDATION_ERROR',request_id))
                status,result = state.create(project,body,scenario,request_id)
                location = f"/api/v1/projects/{project}/reads/{result['data']['read']['read_id']}" if status==201 else None
                return self.reply(status,result,location)
            if not identifier: return self.reply(405,problem(405,'METHOD_NOT_ALLOWED',request_id))
            status,result = (state.receipt if kind=='reads' else state.capture)(project,identifier,request_id)
            return self.reply(status,result)
        def do_GET(self): self.handle_request()
        def do_POST(self): self.handle_request(True)
        def method_denied(self):
            request_id=str(uuid4()); self.reply(405,problem(405,'METHOD_NOT_ALLOWED',request_id))
        do_PUT = do_PATCH = do_DELETE = do_OPTIONS = method_denied
    server = ThreadingHTTPServer(('127.0.0.1',port),Handler)
    server.daemon_threads = True
    return server

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port',type=int,default=8093)
    args=parser.parse_args()
    with create_server(args.port) as server:
        print('MOCK_TC13_PORT='+str(server.server_port),flush=True)
        print('Demostración local: sin autenticación real, sin fuentes ni persistencia duradera.',flush=True)
        try: server.serve_forever()
        except KeyboardInterrupt: pass
