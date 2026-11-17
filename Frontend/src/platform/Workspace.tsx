import { useCallback, useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import type { Gateway, Profile } from '../shared/contracts';
import { ApiError, UUID } from '../shared/errors';
import { modules } from '../modules/registry';
import type { ModuleId } from '../modules/registry';
import { Catalogo } from '../modules/catalogo/Catalogo';
import type { PlatformClient } from './client';
import type { WorkContext } from './contracts';
import { Auth } from './Auth';
import { Administration } from './Admin';
import { Empty, ErrorNote, Field, Load, Paginator, roleNames, Status, Table, useAction, useQuery } from './ui';

type View='contexts'|'administration'|ModuleId;
export function RealApp({gateway}:{gateway:Gateway}) {
  const [profile,setProfile]=useState<Profile|null>(null),[boot,setBoot]=useState(true),[bootError,setBootError]=useState<unknown>(),[attempt,setAttempt]=useState(0),[expired,setExpired]=useState(false);
  useEffect(()=>{
    const controller=new AbortController(); setBoot(true);
    gateway.profile(controller.signal).then(p=>{if(!controller.signal.aborted){setProfile(p);setBoot(false);}}).catch(error=>{
      if(controller.signal.aborted)return;
      if(!(error instanceof ApiError&&error.status===401))setBootError(error);
      setBoot(false);
    }); return ()=>controller.abort();
  },[gateway,attempt]);
  const expire=useCallback(()=>{setProfile(null);setExpired(true);},[]);
  return <><a className="skip-link" href="#main">Ir al contenido</a>
    {boot?<main id="main" className="notice" role="status"><h1>Preparando tu espacio</h1><p>Verificando la sesión.</p></main>:
     bootError?<main id="main" className="notice error" role="alert"><h1>No se pudo verificar la sesión</h1><ErrorNote error={bootError}/><button onClick={()=>{setBootError(undefined);setAttempt(n=>n+1);}}>Volver a intentar</button></main>:
     !profile?<Auth gateway={gateway} expired={expired} logged={p=>{setProfile(p);setExpired(false);}}/>:
     !gateway.platform?<main id="main" className="notice error"><h1>No se pudo preparar la plataforma</h1><p>La conexión de la interfaz no está configurada.</p></main>:
     <Workspace key={profile.id} api={gateway.platform} gateway={gateway} profile={profile} expired={expire} loggedOut={()=>{setProfile(null);setExpired(false);}}/>}
  </>;
}
function Workspace({api,gateway,profile,expired,loggedOut}:{api:PlatformClient;gateway:Gateway;profile:Profile;expired():void;loggedOut():void}) {
  const [view,setView]=useState<View>('contexts'),[epoch,setEpoch]=useState(0),[notice,setNotice]=useState('');
  const logout=useAction(expired);
  const q=useQuery('workspace:'+profile.id+':'+epoch,async s=>{
    const [access,selected]=await Promise.all([api.admin(undefined,s),api.selected(s).catch(e=>{if(e instanceof ApiError&&e.status===404)return null;throw e;})]);
    const grants=selected?await api.permissions(selected.project_id,s):[];
    return {access,selected,grants};
  },expired);
  useEffect(()=>{document.getElementById('view-heading')?.focus();},[view]);
  useEffect(()=>{
    const focus=()=>{if(document.visibilityState==='visible'&&!document.querySelector('dialog[open]'))setEpoch(n=>n+1);};
    window.addEventListener('focus',focus);
    return ()=>window.removeEventListener('focus',focus);
  },[]);
  const ready=q.state.phase==='ready'?q.state.data:undefined;
  const cache=useRef<typeof ready>(undefined);
  useEffect(()=>{if(ready)cache.current=ready;},[ready]);
  const adminData=ready||(q.state.phase==='loading'?cache.current:undefined);
  const canAdmin=ready&&(ready.access.platform_permissions.length>0||ready.access.companies.length>0);
  const module=modules.find(m=>m.id===view);
  const title=view==='contexts'?'Tus proyectos':view==='administration'?'Administración':module?.title||'Espacio de trabajo';
  const description=view==='contexts'?'Selecciona el proyecto en el que vas a trabajar.':view==='administration'?'Gestiona empresas, personas y accesos dentro de tus facultades.':module?.description;
  return <div className="app-shell real-shell"><aside className="sidebar"><div className="brand"><span className="brand-mark" aria-hidden="true">D</span><div><strong>DataAnalitics</strong><span>Enterprise</span></div></div>
    <nav aria-label="Componentes de la plataforma"><button className="nav-item" aria-current={view==='contexts'?'page':undefined} onClick={()=>{setView('contexts');setNotice('');}}>Mis proyectos</button>
      {canAdmin?<button className="nav-item" aria-current={view==='administration'?'page':undefined} onClick={()=>{setView('administration');setNotice('');}}>Administración</button>:null}
      {modules.map(m=><button className="nav-item" key={m.id} aria-current={view===m.id?'page':undefined} onClick={()=>{setView(m.id);setNotice('');}}><span className="module-code">{m.code}</span>{m.label}</button>)}
    </nav><div className="sidebar-note"><strong>Accesos por proyecto</strong><p>La administración de una empresa y el acceso a sus datos se conceden por separado.</p></div>
  </aside><div className="workspace"><header className="topbar"><div><span className="context-label">{ready?.selected?.organization_name||'Espacio de trabajo'}</span><strong>{ready?.selected?.project_name||'Sin proyecto de trabajo seleccionado'}</strong></div>
    <div className="account"><span>{profile.display_name}</span><button type="button" className="secondary" onClick={()=>{setNotice('');setEpoch(n=>n+1);}}>Actualizar acceso</button><button className="secondary" disabled={logout.pending} onClick={()=>void logout.run(()=>gateway.logout(),loggedOut)}>{logout.pending?'Cerrando…':'Cerrar sesión'}</button></div>
  </header><main id="main" className="content"><div className="page-heading"><div><h1 id="view-heading" tabIndex={-1}>{title}</h1><p>{description}</p></div>{module?<span className="module-label">{module.code}</span>:null}</div>
    {notice?<p className="success-note" role="status">{notice}</p>:null}
    {logout.error?<div><p className="error-text">No se pudo cerrar la sesión. Vuelve a intentarlo.</p><ErrorNote error={logout.error}/></div>:null}
    <Load query={q.state} retry={q.reload}>{data=><>
      {data.selected?.project_status==='archived'?<p className="warning-note">Proyecto de trabajo archivado: solo lecturas autorizadas de información conservada.</p>:null}
      {view==='contexts'?<Contexts api={api} selected={data.selected} expired={expired} changed={()=>{setEpoch(n=>n+1);setView('catalogo');setNotice('Proyecto de trabajo seleccionado.');}}/>:
       view==='administration'?null:
       view==='catalogo'?<CatalogWorkspace key={data.selected?.project_id||'none'} api={api} gateway={gateway} selected={data.selected} allowed={data.grants.some(g=>g.permission==='m1.assets.read')&&data.grants.some(g=>g.permission==='m1.dictionary.read')} expired={expired} choose={()=>setView('contexts')}/>:
       <section className="notice"><h2>Vista aún no integrada</h2><p>Este espacio está reservado para el componente de {module?.label.toLowerCase()}.</p><button className="secondary" onClick={()=>setView('contexts')}>Volver a mis proyectos</button></section>}
    </>}</Load>
    {view==='administration'&&adminData?<div className="retained-view" hidden={!ready}><Administration initial={adminData.access} refreshKey={epoch} api={api} expired={expired} notify={setNotice}/></div>:null}
  </main><footer className="footer">DataAnalitics Enterprise<span>Catálogo, calidad, gobierno y analítica</span></footer></div></div>;
}
function Contexts({api,selected,expired,changed}:{api:PlatformClient;selected:WorkContext|null;expired():void;changed():void}) {
  const [page,setPage]=useState(1);
  const q=useQuery('my-contexts:'+page,s=>api.contexts(page,s),expired),action=useAction(expired);
  return <section className="admin-section"><div className="section-toolbar"><h2>Proyectos disponibles</h2><button className="secondary" onClick={q.reload}>Actualizar proyectos</button></div>{action.error?<ErrorNote error={action.error}/>:null}
    <Load query={q.state} retry={q.reload}>{rows=><>{rows.data.length?<Table caption="Mis proyectos" headings={['Proyecto y empresa','Estado','Función asignada','Acciones']}>{rows.data.map(c=><tr key={c.membership_id}><th scope="row">{c.project_name}<small className="cell-detail">{c.organization_name}</small></th><td><Status value={c.project_status}/></td><td>{c.role_code?roleNames[c.role_code]:'Sin rol funcional'}</td><td><button className="secondary" disabled={action.pending} onClick={()=>void action.run(s=>api.select(c.project_id,s),changed)}>{selected?.project_id===c.project_id?'Continuar en '+c.project_name:'Trabajar en '+c.project_name}</button></td></tr>)}</Table>:<Empty title="Todavía no participas en proyectos">Tu administrador debe incorporarte a un proyecto de tu empresa. Administrar la empresa no concede esa participación automáticamente.</Empty>}<Paginator info={rows.meta.page_info} change={setPage}/></>}</Load>
  </section>;
}
function CatalogWorkspace({gateway,selected,allowed,expired,choose}:{api:PlatformClient;gateway:Gateway;selected:WorkContext|null;allowed:boolean;expired():void;choose():void}) {
  const [asset,setAsset]=useState(''),[invalid,setInvalid]=useState(false);
  if(!selected)return <section className="notice"><h2>Selecciona un proyecto de trabajo</h2><p>El catálogo se consulta dentro de una pertenencia autorizada.</p><button onClick={choose}>Elegir proyecto</button></section>;
  if(!allowed)return <section className="notice"><h2>Sin acceso al diccionario en este proyecto</h2><p>La participación o función actual no concede las lecturas necesarias. Solicita una asignación explícita al administrador de tu empresa.</p><button className="secondary" onClick={choose}>Volver a mis proyectos</button></section>;
  function open(e:FormEvent<HTMLFormElement>) {e.preventDefault();const id=String(new FormData(e.currentTarget).get('asset')||'').trim();if(!UUID.test(id)){setInvalid(true);return;}setInvalid(false);setAsset(id);}
  return <><section className="open-resource"><h2>Abrir un activo autorizado</h2><p>Proyecto: <strong>{selected.project_name}</strong>. Introduce el identificador del activo que necesitas consultar.</p><form className="resource-form" onSubmit={open}><Field label="Identificador del activo" name="asset" required spellCheck={false} pattern={UUID.source}/><button type="submit">Abrir activo</button>{invalid?<p className="error-text" role="alert">Escribe un identificador UUID completo en minúsculas.</p>:null}</form></section>
    {asset?<Catalogo key={selected.project_id+':'+asset} gateway={gateway} projectId={selected.project_id} assetId={asset} onExpired={expired}/>:<Empty title="Selecciona un activo para comenzar">El diccionario aparecerá aquí cuando abras un activo disponible para tu cuenta.</Empty>}
  </>;
}
