import { useEffect, useId, useRef, useState } from 'react';
import type { FormEvent, InputHTMLAttributes, ReactNode } from 'react';
import { aborted, ApiError, message } from '../shared/errors';
import type { PageInfo } from './contracts';

export type Query<T> = { phase: 'loading' } | { phase: 'error'; error: unknown } | { phase: 'ready'; data: T };
/** La clave incluye cuenta/empresa/proyecto/página. Un cambio oculta el resultado anterior antes de pintar. */
export function useQuery<T>(key: string, loader: (signal: AbortSignal)=>Promise<T>, expired: ()=>void) {
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<{ key: string; result: Query<T> }>({ key, result: { phase: 'loading' } });
  const effective = key+':'+attempt;
  useEffect(() => {
    const controller = new AbortController();
    setState({ key: effective, result: { phase: 'loading' } });
    Promise.resolve().then(()=>{ if(controller.signal.aborted) throw new DOMException('Aborted','AbortError'); return loader(controller.signal); }).then(data => {
      if(!controller.signal.aborted) setState({ key: effective, result: { phase: 'ready', data } });
    }).catch(error => {
      if(controller.signal.aborted || aborted(error)) return;
      if(error instanceof ApiError && error.status===401) { expired(); return; }
      setState({ key: effective, result: { phase: 'error', error } });
    });
    return () => controller.abort();
  }, [effective]); // Cada consumidor proporciona una clave completa para su consulta.
  return { state: state.key===effective ? state.result : { phase: 'loading' } as Query<T>, reload: ()=>setAttempt(n=>n+1) };
}
export function useAction(expired?: ()=>void) {
  const [pending,setPending]=useState(false),[error,setError]=useState<unknown>();
  const control=useRef<AbortController|null>(null), alive=useRef(true);
  useEffect(()=>{ alive.current=true; return ()=>{ alive.current=false; control.current?.abort(); }; },[]);
  async function run<T>(operation:(signal:AbortSignal)=>Promise<T>, done:(result:T)=>void) {
    if(control.current) return;
    const controller=new AbortController(); control.current=controller; setPending(true); setError(undefined);
    try { const value=await operation(controller.signal); if(alive.current && !controller.signal.aborted) done(value); }
    catch(failure) {
      if(!alive.current || controller.signal.aborted || aborted(failure)) return;
      if(failure instanceof ApiError && failure.status===401 && expired) expired(); else setError(failure);
    } finally { control.current=null; if(alive.current) setPending(false); }
  }
  return {pending,error,run,clear:()=>setError(undefined)};
}
export function ErrorNote({error}:{error:unknown}) {
  return <div className="error-text" role="alert"><p>{message(error)}</p>{error instanceof ApiError && error.requestId ? <p className="reference">Referencia: {error.requestId}</p>:null}</div>;
}
export function Load<T>({query,retry,children,empty}:{query:Query<T>;retry():void;children(data:T):ReactNode;empty?:string}) {
  if(query.phase==='loading') return <p className="loading-line" role="status">Consultando información…</p>;
  if(query.phase==='error') return <div className="inline-notice"><ErrorNote error={query.error}/><button type="button" className="secondary" onClick={retry}>Volver a consultar</button></div>;
  return <>{children(query.data)}</>;
}
export function Field({label,hint,...props}:InputHTMLAttributes<HTMLInputElement>&{label:string;hint?:string}) {
  const id=useId(); return <div className="form-field"><label htmlFor={id}>{label}</label><input {...props} id={id} aria-describedby={hint?id+'-hint':undefined}/>{hint?<small id={id+'-hint'}>{hint}</small>:null}</div>;
}
export function Reason() { const id=useId(); return <div className="form-field"><label htmlFor={id}>Motivo del cambio</label><textarea id={id} name="reason" required maxLength={500} rows={3} aria-describedby={id+'-help'}/><small id={id+'-help'}>Quedará en el historial. No incluyas contraseñas ni códigos de invitación.</small></div>; }
export function Dialog({title,children,close,locked=false}:{title:string;children:ReactNode;close():void;locked?:boolean}) {
  const ref=useRef<HTMLDialogElement>(null); const id=useId();
  useEffect(()=>{
    const previous=document.activeElement; const dialog=ref.current!;
    dialog.showModal();
    return ()=>{ dialog.close(); if(previous instanceof HTMLElement && previous.isConnected) previous.focus(); };
  },[]);
  return <dialog className="dialog" ref={ref} aria-labelledby={id} onCancel={e=>{e.preventDefault(); if(!locked) close();}}>
    <div className="dialog-heading"><h2 id={id}>{title}</h2></div>{children}
  </dialog>;
}
export function MutationDialog<T>({title,description,children,label='Guardar cambios',submit,done,close,expired,danger=false,blocked=false}:{title:string;description?:string;children:ReactNode;label?:string;submit(data:FormData,signal:AbortSignal):Promise<T>;done(result:T):void;close():void;expired():void;danger?:boolean;blocked?:boolean}) {
  const action=useAction(expired);
  function send(e:FormEvent<HTMLFormElement>) { e.preventDefault(); const data=new FormData(e.currentTarget); void action.run(s=>submit(data,s),done); }
  return <Dialog title={title} close={close} locked={action.pending}><form onSubmit={send} aria-busy={action.pending}>
    {description?<p>{description}</p>:null}<fieldset disabled={action.pending||blocked}>{children}<Reason/></fieldset>
    {action.error?<ErrorNote error={action.error}/>:null}
    {action.pending?<p role="status">Guardando el cambio…</p>:null}
    <div className="form-actions"><button type="button" className="secondary" disabled={action.pending} onClick={close}>Cancelar</button><button type="submit" className={danger?'danger':undefined} disabled={action.pending||blocked}>{action.pending?'Guardando…':label}</button></div>
  </form></Dialog>;
}
export function Paginator({info,change}:{info:PageInfo;change(page:number):void}) {
  return <div className="pagination"><button type="button" className="secondary" disabled={info.page===1} onClick={()=>change(info.page-1)}>Anterior</button><span>Página {info.page}</span><button type="button" className="secondary" disabled={!info.has_next} onClick={()=>change(info.page+1)}>Siguiente</button></div>;
}
export function Table({caption,headings,children}:{caption:string;headings:string[];children:ReactNode}) {
  return <div className="table-scroll" role="region" aria-label={caption} tabIndex={0}><table><caption className="sr-only">{caption}</caption><thead><tr>{headings.map(h=><th scope="col" key={h}>{h}</th>)}</tr></thead><tbody>{children}</tbody></table></div>;
}
export function Empty({title,children}:{title:string;children?:ReactNode}) { return <div className="empty" role="status"><h3>{title}</h3>{children?<p>{children}</p>:null}</div>; }
const states:Record<string,string>={active:'Activo',inactive:'Inactivo',archived:'Archivado',pending:'Pendiente',expired:'Vencida',accepted:'Aceptada',revoked:'Revocada'};
export function Status({value}:{value:string}) { return <span className={'status-pill status-'+value}>{states[value]||'Estado no disponible'}</span>; }
export function Reference({value}:{value:string}) { return <span className="reference identifier">{value}</span>; }
export function date(value:string) { const d=new Date(value); return Number.isNaN(d.valueOf())?'No disponible':new Intl.DateTimeFormat('es-EC',{dateStyle:'medium',timeStyle:'short',timeZone:'America/Guayaquil'}).format(d); }
export const value=(data:FormData,key:string)=>String(data.get(key)??'').trim();
export const roleNames:Record<string,string>={GESTOR_CATALOGO:'Gestor de fuentes y catálogo',ANALISTA_CALIDAD:'Analista de calidad',RESPONSABLE_GOBIERNO:'Responsable de gobierno',ANALISTA_NEGOCIO:'Analista de negocio'};
