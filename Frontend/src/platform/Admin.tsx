import { useEffect, useRef, useState } from 'react';
import type { AdminAccess, Project } from './contracts';
import { Administrators, Companies, CompanyMembers, Invitations, Projects, Users } from './AdminLists';
import type { AdminProps } from './AdminLists';
import { Members } from './Members';
import { Empty, Load, Status, useQuery } from './ui';

export function Administration({initial,refreshKey=0,...props}:{initial:AdminAccess;refreshKey?:number}&AdminProps) {
  const {api,expired}=props;
  const [org,setOrg]=useState<string|undefined>(()=>!initial.platform_permissions.includes('tc.organizations.read')&&initial.companies.length===1?initial.companies[0].id:undefined);
  const [view,setView]=useState<'companies'|'users'|'company'>(org?'company':initial.platform_permissions.includes('tc.organizations.read')?'companies':'company');
  const q=useQuery('admin-access:'+(org||'')+':'+refreshKey,s=>api.admin(org,s),expired);
  const cache=useRef<{org:string|undefined;access:AdminAccess}|undefined>(undefined);
  const fresh=q.state.phase==='ready'?q.state.data:undefined;
  useEffect(()=>{if(fresh)cache.current={org,access:fresh};},[fresh,org]);
  const access=fresh||(q.state.phase==='loading'&&cache.current?.org===org?cache.current?.access:undefined);
  return <div className="administration">
    {q.state.phase!=='ready'?<Load query={q.state} retry={q.reload}>{()=>null}</Load>:null}
    {access?<div className="retained-view" hidden={!fresh}>{(()=>{

    const root=access.platform_permissions;
    return <><nav className="subnav" aria-label="Administración de plataforma">
      {root.includes('tc.organizations.read')?<button className="secondary" aria-current={view==='companies'?'page':undefined} onClick={()=>setView('companies')}>Empresas</button>:null}
      {root.includes('tc.users.read')?<button className="secondary" aria-current={view==='users'?'page':undefined} onClick={()=>setView('users')}>Usuarios globales</button>:null}
      {org?<button className="secondary" aria-current={view==='company'?'page':undefined} onClick={()=>setView('company')}>Empresa seleccionada</button>:null}
    </nav>
    {view==='companies'&&root.includes('tc.organizations.read')?<Companies {...props} permissions={root} select={id=>{setOrg(id);setView('company');}}/>:
     view==='users'&&root.includes('tc.users.read')?<Users {...props} permissions={root}/>:
     org&&access.selected_organization?<CompanyArea key={org} {...props} access={access}/>:
     access.companies.length?<section className="admin-section"><h2>Empresas que administras</h2>{access.companies.map(c=><button className="secondary" key={c.id} onClick={()=>{setOrg(c.id);setView('company');}}>Administrar {c.name}</button>)}</section>:
     <Empty title="No hay administración disponible">Tu cuenta no tiene facultades administrativas vigentes.</Empty>}
    </>;
  })()}</div>:null}</div>;
}
function CompanyArea({access,...props}:{access:AdminAccess}&AdminProps) {
  const c=access.selected_organization!,p=access.organization_permissions,global=access.platform_permissions;
  const [tab,setTab]=useState('projects'),[chosen,setChosen]=useState<Project>();
  const tabs=[
    {id:'projects',label:'Proyectos',visible:p.includes('tc.company.projects.read')},
    {id:'users',label:'Empleados',visible:p.includes('tc.company.users.read')},
    {id:'invites',label:'Invitaciones',visible:p.includes('tc.company.invitations.read')||p.includes('tc.company.users.invite')},
    {id:'members',label:'Participantes y roles',visible:p.includes('tc.company.memberships.read')&&p.includes('tc.company.projects.read')},
    {id:'companyMembers',label:'Pertenencia empresarial',visible:p.includes('tc.company.memberships.read')},
    {id:'admins',label:'Administradores',visible:global.some(x=>x.startsWith('tc.company_administrators.'))},
  ].filter(t=>t.visible);
  const current=tabs.some(t=>t.id===tab)?tab:tabs[0]?.id;
  return <section aria-label="Empresa administrada"><div className="company-heading"><div><p className="context-label">Empresa administrada</p><h2>{c.name}</h2></div><Status value={c.status}/></div>
    {c.status!=='active'?<p className="warning-note">La empresa está inactiva. Las operaciones ordinarias no están disponibles.</p>:null}
    <nav className="subnav" aria-label="Gestión de empresa">{tabs.map(t=><button className="secondary" key={t.id} aria-current={current===t.id?'page':undefined} onClick={()=>setTab(t.id)}>{t.label}</button>)}</nav>
    {current==='projects'?<Projects {...props} org={c.id} permissions={p} manage={project=>{setChosen(project);setTab('members');}}/>:
     current==='users'?<Users {...props} org={c.id} permissions={p}/>:
     current==='invites'?<Invitations {...props} org={c.id} permissions={p}/>:
     current==='members'?<Members {...props} org={c.id} permissions={p} chosen={chosen} choose={setChosen}/>:
     current==='companyMembers'?<CompanyMembers {...props} org={c.id} permissions={p}/>:
     current==='admins'?<Administrators {...props} org={c.id} permissions={global.filter(x=>c.status==='active'||x!=='tc.company_administrators.assign')}/>:<Empty title="Sin acciones administrativas disponibles"/>}
  </section>;
}
