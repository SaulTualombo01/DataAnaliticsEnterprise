import {useEffect, useId, useRef, useState, type ComponentProps, type FormEvent, type ReactNode} from 'react';
import {cva} from 'class-variance-authority';
import {clsx, type ClassValue} from 'clsx';
import {twMerge} from 'tailwind-merge';
import {m, useReducedMotion} from 'motion/react';
import {ApiError, label, type PageInfo} from './api';

/** Componentes adaptados de shadcn/ui (MIT); controles nativos y variantes compartidas. */
export function cn(...values: ClassValue[]) { return twMerge(clsx(values)); }

export function Reveal({children, className, delay = 0}: {children: ReactNode; className?: string; delay?: number}) {
    const reduced = useReducedMotion();
    return <m.div className={className} data-reveal="true" initial={reduced ? false : {opacity: 0, y: 12}}
        animate={{opacity: 1, y: 0}} transition={{duration: reduced ? 0 : .32, delay: reduced ? 0 : delay, ease: [.22, 1, .36, 1]}}>{children}</m.div>;
}

export function BrandMark() {
    return <svg className="brand-mark" viewBox="0 0 40 40" fill="none" aria-hidden="true"><rect width="40" height="40" rx="12" fill="currentColor"/><path d="M11 26V19M19 26V11M27 26V15" stroke="white" strokeWidth="3" strokeLinecap="round"/><circle cx="29" cy="10" r="3" fill="#8de0ce"/></svg>;
}

/** Decoración del valor visible; nunca agrega ni promedia reglas. */
export function MetricGauge({value, tone = 'info'}: {value: number | null; tone?: string}) {
    const reduced = useReducedMotion();
    const ratio = value == null ? null : Math.min(100, Math.max(0, value)) / 100;
    return <svg viewBox="0 0 80 80" className={`metric-gauge ${tone}`} aria-hidden="true">
        <circle className="gauge-track" cx="40" cy="40" r="33"/>
        {ratio != null ? <m.circle className="gauge-value" cx="40" cy="40" r="33" pathLength={1}
            initial={reduced ? false : {pathLength: 0}} animate={{pathLength: ratio}} transition={{duration: reduced ? 0 : .8, ease: [.22, 1, .36, 1]}}/> :
            <path className="gauge-pending" d="M29 40h22"/>}
    </svg>;
}

const buttonVariants = cva(
    'ui-button inline-flex min-h-10 shrink-0 items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold transition-colors disabled:pointer-events-none disabled:opacity-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring',
    {variants: {variant: {
        primary: 'border border-primary bg-primary text-primary-foreground hover:bg-primary/90 active:bg-primary/80',
        outline: 'border border-input bg-card text-foreground hover:border-primary/50 hover:bg-accent active:bg-secondary',
        secondary: 'border border-transparent bg-secondary text-secondary-foreground hover:bg-accent active:bg-border',
        destructive: 'border border-destructive/30 bg-card text-destructive hover:bg-destructive hover:text-white',
        inverse: 'border border-white/50 bg-transparent text-white hover:bg-white hover:text-slate-900',
        link: 'border-0 bg-transparent text-primary underline-offset-4 hover:underline',
        navigation: 'nav-item border-0 bg-transparent text-muted-foreground hover:bg-accent hover:text-foreground',
        tool: 'tool-tile border border-border bg-card text-foreground hover:border-primary/50 hover:bg-accent'
    }}, defaultVariants: {variant: 'outline'}}
);

type ButtonVariant = 'primary' | 'outline' | 'secondary' | 'destructive' | 'inverse' | 'link' | 'navigation' | 'tool';
export function Button({className, variant = 'outline', type, ...props}: ComponentProps<'button'> & {variant?: ButtonVariant}) {
    return <button {...props} type={type} data-slot="button" data-variant={variant} className={cn(buttonVariants({variant}),className)}/>;
}

const control = 'ui-control w-full min-w-0 rounded-lg border border-input bg-card px-3 py-2 text-sm text-foreground shadow-xs transition-colors placeholder:text-muted-foreground disabled:cursor-not-allowed disabled:bg-muted disabled:opacity-70 focus-visible:border-ring focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring aria-invalid:border-destructive';
export function Input({type, className, ...props}: ComponentProps<'input'>) {
    const check = type === 'checkbox' || type === 'radio';
    return <input {...props} type={type} data-slot="input" className={cn(type === 'hidden' ? '' : check ? 'ui-check size-4 shrink-0 cursor-pointer accent-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring' : control, className)}/>;
}
export function Select({className, ...props}: ComponentProps<'select'>) {
    return <select {...props} data-slot="native-select" className={cn(control,'min-h-10 cursor-pointer pe-8',className)}/>;
}
export function Textarea({className, ...props}: ComponentProps<'textarea'>) {
    return <textarea {...props} data-slot="textarea" className={cn(control,'min-h-24 resize-y',className)}/>;
}
export function Table({className, ...props}: ComponentProps<'table'>) {
    return <table {...props} data-slot="table" className={cn('ui-table w-full text-left text-sm',className)}/>;
}
export function Card({className, ...props}: ComponentProps<'section'>) {
    return <section {...props} data-slot="card" className={cn('surface-card min-w-0 rounded-xl border border-border bg-card text-card-foreground shadow-xs',className)}/>;
}
export function CardHeader({className, ...props}: ComponentProps<'div'>) {
    return <div {...props} data-slot="card-header" className={cn('panel-header flex flex-wrap items-center justify-between gap-4 border-b border-border p-5',className)}/>;
}
export function CardContent({className, ...props}: ComponentProps<'div'>) {
    return <div {...props} data-slot="card-content" className={cn('p-5',className)}/>;
}
export function CardFooter({className, ...props}: ComponentProps<'div'>) {
    return <div {...props} data-slot="card-footer" className={cn('panel-footer flex flex-wrap items-center justify-between gap-2 border-t border-border px-5 py-3',className)}/>;
}

export function FilterPanel({title, children}: {title: string; children: ReactNode}) {
    return <section className="filter-panel surface-card p-5 mb-4" aria-label={title}><div className="filter-heading"><PanelIcon name="search"/><h2>{title}</h2></div>{children}</section>;
}

export function PanelIcon({name, className = ''}: {name: string; className?: string}) {
    const paths: Record<string, string> = {
        panel: 'M5 5h9v9H5z M18 5h9v9h-9z M5 18h9v9H5z M18 18h9v9h-9z',
        evaluations: 'M8 4h12l5 5v19H8z M20 4v6h5 M12 16h9 M12 21h6',
        rules: 'M11 6h16 M11 16h16 M11 26h16 M4 5l2 2 3-4 M4 15l2 2 3-4 M4 25l2 2 3-4',
        incidents: 'M16 4l13 23H3z M16 12v7 M16 23v1',
        accounts: 'M20 10a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M6 28v-4a10 10 0 0 1 20 0v4',
        database: 'M5 8c0-5 22-5 22 0s-22 5-22 0 M5 8v8c0 5 22 5 22 0V8 M5 16v8c0 5 22 5 22 0v-8',
        chart: 'M5 4v23h24 M10 21v-6 M17 21V9 M24 21V5',
        shield: 'M16 3l11 4v8c0 7-11 14-11 14S5 22 5 15V7z M11 15l4 4 7-8',
        clock: 'M28 16a12 12 0 1 1-24 0 12 12 0 0 1 24 0 M16 8v9l6 3',
        upload: 'M16 22V4 M10 10l6-6 6 6 M5 21v7h22v-7',
        arrow: 'M6 16h20 M18 8l8 8-8 8',
        search: 'M24 14a10 10 0 1 1-20 0 10 10 0 0 1 20 0 M22 22l7 7',
        check: 'M6 16l7 7L27 8',
        logout: 'M14 5H5v22h9 M16 16h13 M23 10l6 6-6 6'
    };
    return <svg className={`panel-icon ${className}`} viewBox="0 0 32 32" fill="none" stroke="currentColor" strokeWidth="1.65" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><path d={paths[name] ?? paths.database}/></svg>;
}

export function ErrorMessage({error, id}: { error: unknown; id?: string }) {
    if (!error) return null;
    return <div id={id} className="notice notice-danger my-4"
                role="alert">{error instanceof Error ? error.message : 'No se pudo completar la operación.'}{error instanceof ApiError && error.requestId ?
        <small>Referencia: {error.requestId}</small> : null}</div>;
}

export function Status({value, context}: { value: string; context?: 'execution' | 'quality' | 'input' }) {
    const tone = ['PASS', 'RESOLVED', 'ACTIVE'].includes(value) ? 'good' : ['FAIL', 'FAILED', 'CRITICAL', 'HIGH'].includes(value) ? 'bad' : ['EXPIRED', 'DELETED', 'UNAVAILABLE', 'PARTIAL_COVERAGE', 'NOT_EVALUABLE', 'QUEUED', 'OPEN', 'MEDIUM'].includes(value) ? 'warning' : ['RUNNING', 'COMPLETED', 'IN_PROGRESS'].includes(value) ? 'info' : 'neutral';
    const text = context === 'quality' && value === 'PASS' ? 'Cumple reglas seleccionadas' : context === 'quality' && value === 'FAIL' ? 'No cumple reglas seleccionadas' : label(value);
    return <span className={`status ${tone}${value === 'RUNNING' ? ' status-running' : ''}`} aria-label={text + (context === 'execution' ? ` (${value})` : '')}>{value === 'RUNNING' ? <span className="status-dot" aria-hidden="true"/> : null}{text}{context === 'execution' ? <small className="status-code">{value}</small> : null}</span>;
}

export function InputStatus({value}: {value?: string}) {
    return <Status value={value ?? 'UNKNOWN'} context="input"/>;
}

export function Pagination({info, onPage}: { info?: PageInfo; onPage: (page: number) => void }) {
    return <div className="pagination-controls flex flex-wrap justify-end items-center gap-4 my-4">
        <Button disabled={!info || info.page <= 1} onClick={() => onPage(info!.page - 1)}>Anterior</Button>
        <span role="status" aria-live="polite" aria-atomic="true">Página {info?.page ?? 1} · {info?.returned_count ?? 0} registros{info?.total_count != null ? ` de ${info.total_count.toLocaleString('es-EC')}` : ''}</span>
        <Button disabled={!info?.has_next} onClick={() => onPage(info!.page + 1)}>Siguiente</Button>
    </div>;
}

export function Dialog({title, children, close, variant, busy = false}: { title: string; children: ReactNode; close: () => void; variant?: 'evaluation' | 'evidence'; busy?: boolean }) {
    const ref = useRef<HTMLDialogElement>(null);
    const titleId = useId();
    useEffect(() => {
        const previous = document.activeElement as HTMLElement | null;
        ref.current?.showModal();
        return () => {
            ref.current?.close();
            previous?.focus();
        };
    }, []);
    return <dialog ref={ref} className={variant ? `dialog-${variant}` : undefined} aria-labelledby={titleId} onCancel={e => {
        e.preventDefault();
        if (!busy) close();
    }}>
        <div className="dialog-heading flex items-center justify-between gap-4"><div className="dialog-title"><span><PanelIcon name={variant === 'evaluation' ? 'chart' : variant === 'evidence' ? 'shield' : 'evaluations'}/></span><h2 id={titleId}>{title}</h2></div>
            <Button variant="outline" aria-label="Cerrar formulario" disabled={busy} onClick={close}>×</Button>
        </div>
        {children}</dialog>;
}

export function Form({children, submit, label: action = 'Guardar', cancel, variant, cancelLabel = 'Cancelar'}: {
    noValidate?: true;
    children?: ReactNode;
    submit: (data: FormData) => Promise<void>;
    label?: string;
    cancel?: () => void;
    cancelLabel?: string;
    variant?: 'filters'
}) {
    const [pending, setPending] = useState(false);
    const [error, setError] = useState<unknown>();
    const errorId = useId();

    async function save(e: FormEvent<HTMLFormElement>) {
        e.preventDefault();
        const form = e.currentTarget;
        if (pending) return;
        if (!form.checkValidity()) {
            const invalid = form.querySelector<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>('input:invalid, select:invalid, textarea:invalid');
            form.querySelectorAll<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>('input, select, textarea').forEach(field => {
                if (!field.validity.valid) {field.setAttribute('aria-invalid', 'true'); field.setAttribute('aria-describedby', [...new Set([...(field.getAttribute('aria-describedby') ?? '').split(' '), errorId])].filter(Boolean).join(' '));}
            });
            invalid?.focus();
            setError(new Error('Completa los campos obligatorios y revisa su formato.'));
            return;
        }
        setPending(true);
        setError(undefined);
        try {
            await submit(new FormData(form));
        } catch (err) {
            setError(err);
        } finally {
            setPending(false);
        }
    }

    return <form noValidate aria-busy={pending} className={variant === 'filters' ? 'filter-form grid items-end gap-4 lg:grid-cols-[minmax(0,1fr)_auto]' : undefined} onSubmit={save} onInput={e => {
        const field = e.target as HTMLInputElement;
        if (field.validity?.valid) {field.removeAttribute('aria-invalid'); field.setAttribute('aria-describedby', (field.getAttribute('aria-describedby') ?? '').split(' ').filter(id => id !== errorId).join(' '));}
    }}>
        <fieldset className={variant === 'filters' ? 'min-w-0' : undefined} disabled={pending}>{children}</fieldset>
        <ErrorMessage id={errorId} error={error}/>
        <div className={`form-actions flex flex-wrap justify-end gap-2 ${variant === 'filters' ? 'lg:col-start-2' : 'mt-4 pt-4'}`}>{cancel ?
            <Button type="button" disabled={pending} onClick={cancel}>{cancelLabel}</Button> : null}
            <Button variant="primary" disabled={pending}
                    aria-busy={pending}>{pending ? <span className="button-spinner" aria-hidden="true"/> : null}{pending ? variant === 'filters' ? 'Aplicando…' : 'Guardando…' : action}</Button>
        </div>
    </form>;
}

export function Loading() {
    return <div className="loading-state" role="status" aria-live="polite"><div className="loading-caption"><span className="loading-spinner" aria-hidden="true"/>Cargando información…</div>
        <div className="skeleton-grid" aria-hidden="true">{[0, 1, 2].map(key => <div className="skeleton-card" key={key}><div className="skeleton skeleton-icon"/><div className="skeleton skeleton-line"/><div className="skeleton skeleton-number"/><div className="skeleton skeleton-line short"/></div>)}</div>
    </div>;
}
