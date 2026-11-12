import {useEffect, useRef, useState} from 'react';
import {api, type Asset} from './api';
import {Dialog, Form, Input, Select, Table, PanelIcon} from './ui';

type Preview = {headers: string[]; row_count: number; byte_count: number; preview_rows: string[][]};
export function CsvUpload({project, close, imported}: {project: string; close: () => void; imported: (asset: Asset) => Promise<void>}) {
    const [file, setFile] = useState<File>();
    const [name, setName] = useState('');
    const [separator, setSeparator] = useState('AUTO');
    const [preview, setPreview] = useState<Preview>();
    const [types, setTypes] = useState<Record<string, string>>({});
    const request = useRef<AbortController | undefined>(undefined);
    useEffect(() => () => request.current?.abort(), []);
    function changed() { request.current?.abort(); setPreview(undefined); setTypes({}); }
    return <Dialog title="Subir archivo CSV" close={close}>
        <p>Sube un CSV de clientes, pedidos o una tabla exportada de tu base de datos. El archivo debe estar en UTF-8 y tener encabezados.</p>
        <Form noValidate label={preview ? 'Importar CSV' : 'Revisar archivo'} cancel={close} submit={async () => {
            if (!file) throw new Error('Selecciona un archivo CSV.');
            if (!file.name.toLowerCase().endsWith('.csv')) throw new Error('Selecciona un archivo con extensión .csv.');
            if (file.size > 10000000) throw new Error('El CSV supera el máximo de 10 MB.');
            const data = new FormData(); data.set('file', file); data.set('separator', separator);
            request.current = new AbortController();
            if (!preview) {
                const response = await api<Preview>(`/projects/${project}/csv-inputs/preview`, {method: 'POST', body: data, signal: request.current.signal});
                setPreview(response.data); setTypes(Object.fromEntries(response.data.headers.map(h => [h, 'TEXT'])));
            } else {
                data.set('name', name || file.name); data.set('column_types', JSON.stringify(types));
                const response = await api<Asset>(`/projects/${project}/csv-inputs`, {method: 'POST', body: data, signal: request.current.signal});
                await imported(response.data);
            }
        }}>
            <label className="csv-file-picker field-label"><span className="upload-symbol" aria-hidden="true"><PanelIcon name="upload"/></span><strong>Selecciona tu archivo CSV</strong><small>Hasta 10 MB · UTF-8 · Encabezados en la primera fila</small><Input aria-label="Archivo CSV" name="file" type="file" accept=".csv,text/csv" required onChange={e => {changed(); const selected = e.target.files?.[0]; setFile(selected); setName(selected?.name ?? '');}}/></label>
            <div className="form-grid grid grid-cols-1 sm:grid-cols-2 gap-4"><label className="field-label flex flex-col gap-2 mb-4">Nombre del conjunto<Input name="dataset_name" value={name} maxLength={160} required onChange={e => setName(e.target.value)}/></label>
                <label className="field-label flex flex-col gap-2 mb-4">Separador<Select value={separator} onChange={e => {changed(); setSeparator(e.target.value);}}><option value="AUTO">Detectar automáticamente</option><option value="COMMA">Coma (,)</option><option value="SEMICOLON">Punto y coma (;)</option><option value="TAB">Tabulación</option></Select></label></div>
            <p className="help">Máximo: 10 MB y 10.000 registros. Se conservan los vacíos y duplicados para evaluarlos; no se corrige el archivo.</p>
            <p className="help">Si ya cargaste el mismo contenido con el mismo separador y tipos de columna, se abrirá su activo existente con sus reglas. Cambiar solo el nombre no crea otra copia.</p>
            {preview ? <section className="csv-preview"><h3>Revisión de columnas</h3><p>{preview.row_count.toLocaleString('es-EC')} registros · {preview.headers.length} columnas · {(preview.byte_count / 1000).toLocaleString('es-EC')} kB</p>
                <p className="help">Mantén cédulas, códigos y fechas como texto. Selecciona Número para columnas con cantidades que quieras validar por rango. Los decimales numéricos usan punto.</p>
                <div className="table-scroll max-w-full overflow-x-auto"><Table><caption>Primeros {preview.preview_rows.length} registros del archivo</caption><thead><tr>{preview.headers.map(header => <th key={header}><label className="field-label flex flex-col gap-2 mb-4">{header}<Select aria-label={`Tipo de ${header}`} value={types[header]} onChange={e => setTypes(previous => ({...previous, [header]: e.target.value}))}><option value="TEXT">Texto</option><option value="NUMBER">Número</option></Select></label></th>)}</tr></thead><tbody>{preview.preview_rows.map((row, index) => <tr key={index}>{row.map((cell, i) => <td key={i}>{cell === '' ? <span className="cell-empty">(vacío)</span> : cell}</td>)}</tr>)}</tbody></Table></div>
                {!preview.row_count ? <p className="notice notice-warning my-4">El CSV solo contiene encabezados. Podrás importarlo; una evaluación mostrará que no hay datos evaluables.</p> : null}
                <p className="help">Al importar podrás crear las reglas para estas columnas y después iniciar el análisis.</p>
            </section> : null}
        </Form>
    </Dialog>;
}
