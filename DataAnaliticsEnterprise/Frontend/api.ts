export type PageInfo = { page: number; page_size: number; returned_count: number; has_next: boolean; total_count?: number | null };
export type Envelope<T> = { data: T; meta: { request_id: string; page_info?: PageInfo } };

export function queryPath(base: string, parameters: Record<string, string | number | boolean | null | undefined>) {
    const query = new URLSearchParams();
    for (const [name, value] of Object.entries(parameters)) {
        if (value !== undefined && value !== null && value !== '') query.set(name, String(value));
    }
    return query.size ? `${base}?${query}` : base;
}

export class ApiError extends Error {
    constructor(public status: number, message: string, public requestId = '') {
        super(message);
    }
}

let csrf: { token: string; header_name: string } | undefined;

export function clearCsrf() {
    csrf = undefined;
}

export async function refreshCsrf() {
    const response = await fetch('/api/v1/session/csrf', {credentials: 'same-origin'});
    if (!response.ok) throw new ApiError(response.status, 'No se pudo preparar la sesión. Vuelve a intentarlo.');
    const body: Envelope<{ token: string; header_name: string }> = await response.json();
    csrf = body.data;
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<Envelope<T>> {
    const method = options.method ?? 'GET';
    const headers = new Headers(options.headers);
    if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
        if (!csrf) await refreshCsrf();
        headers.set(csrf!.header_name, csrf!.token);
    }
    let response: Response;
    try {
        response = await fetch('/api/v1' + path, {...options, method, headers, credentials: 'same-origin'});
    } catch (error) {
        if (error instanceof DOMException && error.name === 'AbortError') throw error;
        throw new ApiError(0, 'No hay conexión con el servidor. Conservamos el formulario para que puedas reintentar.');
    }
    if (!response.ok) {
        const problem = await response.json().catch(() => ({}));
        if (response.status === 401) {
            clearCsrf();
            if (path !== '/sessions') window.dispatchEvent(new Event('m2-session-expired'));
        }
        throw new ApiError(response.status, problem.detail ?? 'No se pudo completar la operación.', problem.request_id ?? '');
    }
    if (response.status === 204) return {
        data: undefined as T,
        meta: {request_id: response.headers.get('X-Request-ID') ?? ''}
    };
    return response.json();
}

export type Role = { project_id: string; project_name?: string; role: string; asset_scope_id: string | null };
export type Me = { id: string; roles: Role[] };
export type Asset = { id: string; name: string; display_name?: string; metadata_version_id: string; fields: string[]; source_kind?: string; row_count?: number; import_reused?: boolean };
export const assetLabel = (asset: Asset) => asset.display_name ?? asset.name;
export type Rule = { id: string; name: string; enabled: boolean; revision: number; versions?: Version[]; current_version?: Version | null };
export type Version = {
    id: string;
    dimension: string;
    definition: Record<string, unknown>;
    threshold: number | null;
    minimum_coverage: number;
    critical: boolean;
    formula_version?: string;
    created_at?: string;
    version_number: number
};
export type InputMetadata = {
    asset_id?: string;
    metadata_version_id: string;
    snapshot_id?: string | null;
    read_id?: string;
    input_availability?: string;
    input_row_count?: number;
    input_byte_count?: number;
    input_acquired_at?: string;
    input_expires_at?: string | null;
    input_complete?: boolean;
    scope?: {kind?: string; filename?: string; headers?: string[]; separator?: string; column_types?: Record<string, string>};
};
export type Evaluation = Partial<InputMetadata> & {
    id: string;
    asset_id: string;
    state: string;
    quality_outcome: string;
    submitted_at: string;
    failure_code?: string;
    input_availability?: string;
    snapshot_id?: string;
    read_id?: string
};
export type EvaluationReport = {evaluation: Evaluation; results: Result[]; rule_versions: Version[]};
export type QualityOverviewData = {
    latest_evaluation: Evaluation | null;
    results: Result[];
    rule_versions: Version[];
    dimension_evaluations: Record<string, Evaluation>;
};
export const isPendingEvaluation = (evaluation: Evaluation) => ['QUEUED', 'RUNNING'].includes(evaluation.state);
export const hasPendingEvaluations = (evaluations: Evaluation[]) => evaluations.some(isPendingEvaluation);
export const hasPendingReport = (report: EvaluationReport) => isPendingEvaluation(report.evaluation);
export type Evidence = {available: boolean; reason?: string; values?: Record<string, unknown>; record_reference?: string; captured_at?: string; snapshot_id?: string; read_id?: string};
export type Result = {
    id: string;
    rule_version_id: string;
    numerator: number;
    denominator: number;
    metric_value: number | null;
    coverage_value: number | null;
    compliance_state: string;
    threshold: number | null;
    measurement_state: string
};
export type Incident = {
    asset_id: string;
    evaluation_id: string;
    created_at: string;
    rule_name?: string;
    dimension?: string;
    rule_version_id?: string;
    id: string;
    field_name: string;
    record_reference: string;
    problem_code: string;
    status: string;
    revision: number;
    severity: string
};
export const labels: Record<string, string> = {
    QUEUED: 'En cola',
    RUNNING: 'En ejecución',
    COMPLETED: 'Completada',
    FAILED: 'Fallida',
    PASS: 'Cumple',
    FAIL: 'No cumple',
    NO_DATA: 'Sin datos',
    NOT_ASSESSED: 'Sin evaluación',
    INCOMPLETE: 'Incompleta',
    NOT_EVALUABLE: 'No evaluable',
    NOT_APPLICABLE: 'No aplica',
    PARTIAL_COVERAGE: 'Cobertura parcial', INSUFFICIENT_COVERAGE: 'Cobertura insuficiente', NO_THRESHOLD: 'Sin umbral',
    MEASURED: 'Medida',
    AVAILABLE: 'Origen conservado', EXPIRED: 'Origen caducado', DELETED: 'Origen retirado', UNAVAILABLE: 'Origen no disponible', UNKNOWN: 'Origen no informado',
    COMPLETENESS: 'Completitud',
    ACCURACY: 'Exactitud',
    CONSISTENCY: 'Consistencia',
    UNIQUENESS: 'Unicidad',
    VALIDITY: 'Validez',
    TIMELINESS: 'Actualidad',
    OPEN: 'Abierta',
    IN_PROGRESS: 'En seguimiento',
    RESOLVED: 'Resuelta',
    DISMISSED: 'Descartada',
    LOW: 'Baja', MEDIUM: 'Media', HIGH: 'Alta', CRITICAL: 'Crítica',
    SYSTEM_ADMIN: 'Administrador del sistema',
    ACTIVE: 'Activa', INACTIVE: 'Inactiva',
    QUALITY_MANAGER: 'Responsable de calidad',
    CATALOG_MANAGER: 'Gestor de catálogo',
    GOVERNANCE_MANAGER: 'Responsable de gobierno',
    BUSINESS_ANALYST: 'Analista de negocio'
};
export const label = (value: string) => labels[value] ?? value;
export const percentage = (value: number | null) => value == null ? 'No evaluable' : new Intl.NumberFormat('es-EC', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
}).format(value) + ' %';
export const date = (value: string) => new Intl.DateTimeFormat('es-EC', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'America/Guayaquil'
}).format(new Date(value));

export const dimensions = ['COMPLETENESS', 'ACCURACY', 'CONSISTENCY', 'UNIQUENESS', 'VALIDITY', 'TIMELINESS'] as const;
export function ruleCondition(version?: Version | null) {
    if (!version) return 'Configuración no disponible';
    const d = version.definition;
    const fields = Array.isArray(d.fields) ? d.fields.join(', ') : '';
    const names: Record<string, string> = {REQUIRED: 'Valores obligatorios', UNIQUE_KEY: 'Clave única', EMAIL: 'Formato de correo', NUMBER_RANGE: 'Rango numérico', ALLOWED_VALUES: 'Valores permitidos', FIELD_EQUALS: 'Igualdad entre campos', FOREIGN_KEY: 'Referencia entre activos', REFERENCE_EQUALS: 'Comparación con referencia', WITHIN_AGE: 'Vigencia temporal'};
    const kind = String(d.kind);
    const parameter = kind === 'NUMBER_RANGE' ? ` [${d.minimum}, ${d.maximum}]` : kind === 'WITHIN_AGE' ? ` (${d.max_age_seconds} s)` : kind === 'ALLOWED_VALUES' && Array.isArray(d.allowed_values) ? ` (${d.allowed_values.join(', ')})` : '';
    return `${names[kind] ?? kind}${parameter} · ${fields}`;
}
export function metricFormula(dimension: string) {
    const formulas: Record<string, string> = {
        COMPLETENESS: '100 × valores presentes / valores comprobados', ACCURACY: '100 × coincidencias / comparaciones evaluables',
        CONSISTENCY: '100 × comprobaciones consistentes / comprobaciones evaluables', UNIQUENESS: '100 × claves distintas / registros evaluables',
        VALIDITY: '100 × comprobaciones válidas / comprobaciones evaluables', TIMELINESS: '100 × registros vigentes / registros temporales evaluables'
    };
    return formulas[dimension] ?? 'Fórmula no informada';
}
export const metricValue = (result: Result) => result.denominator === 0 || result.measurement_state === 'NOT_EVALUABLE' ? 'No evaluable' : percentage(result.metric_value);


