"""Codec contractual json_utf8_compact_v1 para raw_cells_v1, sin dependencias externas."""
import json

def record_bytes(rows):
    # Ordered property names are normative. No trimming, casting, sorting of rows or deduplication.
    ordered=[{'row_number':row['row_number'],'values':[
        {'field_id':cell['field_id'],'state':cell['state'],'raw_value':cell['raw_value']}
        for cell in row['values']]} for row in rows]
    return json.dumps(ordered,ensure_ascii=False,separators=(',',':'),allow_nan=False).encode('utf-8')
