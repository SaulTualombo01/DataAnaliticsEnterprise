"""Valida contratos y ejemplos públicos sin acceder a fuentes ni a la base de plataforma."""
from pathlib import Path
from hashlib import sha256
import json
from jsonschema import Draft202012Validator, FormatChecker
from openapi_spec_validator import validate
from tc13_codec import record_bytes

ROOT = Path(__file__).resolve().parents[1]
FOLDER = ROOT / 'docs/contratos/tc13'
def validate_read(result):
    read=result['read']; rows=result['records']; counts=read['counts']
    assert counts['returned_rows']==len(rows)
    raw=record_bytes(rows)
    assert counts['serialized_bytes']==len(raw)
    assert [row['row_number'] for row in rows]==list(range(1,len(rows)+1))
    for row in rows: assert [cell['field_id'] for cell in row['values']]==read['field_ids']
    expected=counts['expected_rows']
    if expected is None or expected==0: assert counts['coverage_ratio'] is None
    else: assert abs(counts['coverage_ratio']-len(rows)/expected)<1e-12
    capture=read['capture']
    if capture:
        assert capture['content_hash_encoding']=='json_utf8_compact_v1'
        assert capture['content_hash']==sha256(raw).hexdigest() and capture['row_count']==len(rows)
        assert all(capture[key]==read[key] for key in ('organization_id','project_id','source_id','asset_id','metadata_version_id','field_ids'))
    if read['mode']=='live' or read['execution_state']=='FAILED': assert capture is None

def main():
    schema=json.loads((FOLDER/'tc13.schema.json').read_text(encoding='utf-8'))
    Draft202012Validator.check_schema(schema)
    validate(json.loads((FOLDER/'tc13.openapi.json').read_text(encoding='utf-8')))
    examples=json.loads((FOLDER/'tc13.examples.json').read_text(encoding='utf-8'))
    for example in examples.values():
        Draft202012Validator({**schema,'$ref':'#/$defs/'+example['schema']},format_checker=FormatChecker()).validate(example['body'])
        if example['schema']=='ReadResponse': validate_read(example['body']['data'])
    identity_schema=json.loads((ROOT/'docs/contratos/mocks-identidad-contexto.schema.json').read_text(encoding='utf-8'))
    fixture=json.loads((ROOT/'Frontend/src/mocks/identidad-contexto.json').read_text(encoding='utf-8'))
    Draft202012Validator.check_schema(identity_schema)
    Draft202012Validator(identity_schema,format_checker=FormatChecker()).validate(fixture)
    assert fixture['kind']=='mock' and len({context['organization_id'] for context in fixture['contexts']})==1
    assert len({context['project_id'] for context in fixture['contexts']})==len(fixture['contexts'])
    m1=ROOT/'docs/contratos/m1'
    m1_schema=json.loads((m1/'catalogo-http.schema.json').read_text(encoding='utf-8'))
    m1_spec=json.loads((m1/'catalogo-http.openapi.json').read_text(encoding='utf-8'))
    assert m1_spec['info']['version']=='1.0.0'
    validate(m1_spec); Draft202012Validator.check_schema(m1_schema)
    m1_examples=json.loads((m1/'catalogo.examples.json').read_text(encoding='utf-8'))
    for example in m1_examples.values():
        Draft202012Validator({**m1_schema,'$ref':'#/$defs/'+example['schema']},format_checker=FormatChecker()).validate(example['body'])
    # M1 keeps its public files at their existing paths for its tools; detect drift after integration.
    for filename in ('catalogo-http.openapi.json','catalogo-http.schema.json'):
        source=ROOT/'docs/contratos'/filename
        if source.exists(): assert source.read_bytes()==(m1/filename).read_bytes(),filename
    print(f'OpenAPI TC13 válido; {len(examples)} ejemplos y fixture de identidad/contexto válidos.')
    print(f'OpenAPI M1 válido; {len(m1_examples)} ejemplos válidos. Base técnica v1.0.0.')

if __name__=='__main__': main()
