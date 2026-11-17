import { useId, useRef, useState } from 'react';
import { ApiError, UUID } from '../shared/errors';
import type { PlatformClient } from './client';
import type { Assignment, FunctionalRole, Member, Project, RoleCode, Scope } from './contracts';
import type { AdminProps } from './AdminLists';
import { UserName, UserPicker } from './AdminLists';
import { date, Dialog, Empty, ErrorNote, Load, MutationDialog, Paginator, Reference, roleNames, Status, Table, useAction, useQuery, value } from './ui';

export function ProjectPicker({api,org,expired,chosen,choose}:{api:PlatformClient;org:string;expired():void;chosen:Project|undefined;choose(p:Project):void}) {
  const [page,setPage]=useState(1);
  const q=useQuery('project-picker:'+org+':'+page,s=>api.projects(org,page,s),expired);
  return <Load query={q.state} retry={q.reload}>{rows=><div className="project-picker"><label>Proyecto administrado<select value={chosen?.id||''} onChange={e=>{const p=rows.data.find(p=>p.id===e.target.value);if(p)choose(p);}}>
    <option value="" disabled>Selecciona un proyecto</option>
    {chosen && !rows.data.some(p=>p.id===chosen.id)?<option value={chosen.id}>{chosen.name}</option>:null}
    {rows.data.map(p=><option key={p.id} value={p.id}>{p.name}{p.status==='archived'?' (archivado)':p.status==='inactive'?' (inactivo)':''}</option>)}
  </select></label><Paginator info={rows.meta.page_info} change={setPage}/></div>}</Load>;
}
type RoleProps = AdminProps & {org:string;project:string;member:Member;close():void;changed():void};
export function RoleDialog(props:RoleProps) {
  const {api,org,project,member,expired}=props;
  const q=useQuery('role-editor:'+org+':'+project+':'+member.id,async s=>{
    const [access,roles,current]=await Promise.all([api.admin(org,s),api.roles(org,s),api.currentRole(org,project,member.id,s)]);
    if(!access.organization_permissions.includes('tc.company.roles.read')) throw new ApiError(403);
    return {roles,current};
  },expired);
  if(q.state.phase!=='ready') return <Dialog title="Configurar rol funcional" close={props.close}><Load query={q.state} retry={q.reload}>{()=>null}</Load><div className="form-actions"><button className="secondary" onClick={props.close}>Cerrar</button></div></Dialog>;
  return <RoleEditor {...props} roles={q.state.data.roles} current={q.state.data.current}/>;
}
function RoleEditor({api,org,project,member,expired,notify,close,changed,roles,current}:{roles:FunctionalRole[];current:Assignment|null}&RoleProps) {
  const roleId=useId();
  const [reference,setReference]=useState(current);
  const [role,setRole]=useState<RoleCode|''>(current?.role_code||'');
  const [mode,setMode]=useState<'PROJECT'|'RESOURCES'|''>(current?current.scopes.some(s=>s.resource_scope==='PROJECT')?'PROJECT':'RESOURCES':'');
  const next=useRef((current?.scopes.length||0)+1);
  const [scopes,setScopes]=useState(()=>current?.scopes.filter(s=>s.resource_scope!=='PROJECT').map((s,i)=>({...s,key:i+1}))||[]);
  const [reviewed,setReviewed]=useState(false),refresh=useAction(expired);
  async function reload(s:AbortSignal) {
    const access=await api.admin(org,s);
    if(!access.organization_permissions.includes('tc.company.roles.assign')) throw new ApiError(403);
    return api.currentRole(org,project,member.id,s);
  }
  function update(key:number,patch:Partial<Scope>) { setScopes(rows=>rows.map(row=>row.key===key?{...row,...patch}:row)); }
  return <MutationDialog title="Configurar rol funcional" label={reference?'Confirmar sustitución':'Asignar rol'} close={close} expired={expired} blocked={refresh.pending}
    description="Se concede un solo rol funcional por proyecto. Un cambio retira la asignación anterior y conserva su historial."
    submit={(d,s)=>{
      if(!role || !mode) throw new ApiError(422);
      const selected:Scope[]=mode==='PROJECT'?[{resource_scope:'PROJECT',resource_id:null}]:scopes.map(({resource_scope,resource_id})=>({resource_scope,resource_id}));
      if(!selected.length || selected.some(a=>a.resource_scope!=='PROJECT'&&!UUID.test(a.resource_id||''))) throw new ApiError(422);
      return api.setRole(org,project,member.id,role,selected,reference?.id||null,value(d,'reason'),s);
    }}
    done={()=>{notify('Rol y alcances guardados.');changed();close();}}>
    <div className="inline-notice"><p>Asignación actual: <strong>{reference?roleNames[reference.role_code]:'Sin rol funcional'}</strong></p>{reference?<Reference value={reference.id}/>:null}
      <button type="button" className="secondary" disabled={refresh.pending} onClick={()=>void refresh.run(reload,a=>{setReference(a);setReviewed(true);})}>Actualizar referencia</button>
      {reviewed?<p role="status">Referencia actualizada. Revisa el cambio propuesto antes de confirmar.</p>:null}
      {refresh.error?<ErrorNote error={refresh.error}/>:null}
    </div>
    <label htmlFor={roleId}>Rol funcional</label><select id={roleId} required value={role} onChange={e=>setRole(e.target.value as RoleCode)}><option value="" disabled>Selecciona un rol</option>{roles.map(r=><option key={r.code} value={r.code}>{r.name}</option>)}</select>
    {role?<p className="form-help">{roles.find(r=>r.code===role)?.permissions.length||0} permisos disponibles en el catálogo vigente. La asignación no modifica su definición.</p>:null}
    <fieldset className="scope-editor"><legend>Alcance del acceso</legend>
      <label className="choice"><input type="radio" name="scope_mode" required checked={mode==='PROJECT'} onChange={()=>setMode('PROJECT')}/>Todo el proyecto</label>
      <label className="choice"><input type="radio" name="scope_mode" required checked={mode==='RESOURCES'} onChange={()=>{setMode('RESOURCES');if(!scopes.length)setScopes([{key:next.current++,resource_scope:'ASSET',resource_id:''}]);}}/>Solo fuentes o activos indicados</label>
      {mode==='PROJECT'?<p className="form-help">Las funciones de este rol podrán aplicarse a todos los recursos autorizables del proyecto.</p>:null}
      {mode==='RESOURCES'?<><p className="form-help">Cada identificador debe existir y pertenecer a este proyecto. Se pueden indicar hasta 50 recursos.</p>
        {scopes.map((a,index)=><div className="scope-row" key={a.key}><label>Tipo de recurso {index+1}<select value={a.resource_scope} onChange={e=>update(a.key,{resource_scope:e.target.value as 'SOURCE'|'ASSET'})}><option value="SOURCE">Fuente</option><option value="ASSET">Activo</option></select></label>
          <label>Identificador del recurso {index+1}<input required spellCheck={false} pattern={UUID.source} value={a.resource_id||''} onChange={e=>update(a.key,{resource_id:e.target.value.trim()})}/></label>
          <button type="button" className="secondary" onClick={()=>setScopes(rows=>rows.filter(r=>r.key!==a.key))}>Quitar recurso {index+1}</button></div>)}
        <button type="button" className="secondary" disabled={scopes.length>=50} onClick={()=>setScopes(rows=>[...rows,{key:++next.current,resource_scope:'ASSET',resource_id:''}])}>Añadir recurso</button>
      </>:null}
    </fieldset>
  </MutationDialog>;
}
function RoleHistory({api,org,project,member,expired,notify,close,changed,canRevoke}:{org:string;project:string;member:Member;close():void;changed():void;canRevoke:boolean}&AdminProps) {
  const [page,setPage]=useState(1),[version,setVersion]=useState(0),[target,setTarget]=useState<Assignment>();
  const q=useQuery('role-history:'+org+':'+project+':'+member.id+':'+page+':'+version,s=>api.history(org,project,member.id,page,s),expired);
  return <Dialog title="Historial de roles y alcances" close={close}><Load query={q.state} retry={q.reload}>{rows=><>{rows.data.length?<Table caption="Asignaciones funcionales" headings={['Rol y alcance','Estado','Asignación','Acciones']}>{rows.data.map(a=><tr key={a.id}><th scope="row">{roleNames[a.role_code]}<Reference value={a.id}/><ul className="scope-list">{a.scopes.map((s,i)=><li key={i}>{s.resource_scope==='PROJECT'?'Todo el proyecto':s.resource_scope==='SOURCE'?'Fuente':'Activo'}{s.resource_id?<Reference value={s.resource_id}/>:null}</li>)}</ul>{!a.scopes.length?<small>Sin alcances registrados</small>:null}</th><td><Status value={a.revoked_at?'inactive':a.status}/></td><td>{date(a.assigned_at)}{a.revoked_at?<small className="cell-detail">Retirado: {date(a.revoked_at)}</small>:null}</td><td>{canRevoke && a.status==='active'&&!a.revoked_at?<button className="secondary danger-text" onClick={()=>setTarget(a)}>Revocar esta asignación</button>:null}</td></tr>)}</Table>:<Empty title="Sin asignaciones anteriores"/>}<Paginator info={rows.meta.page_info} change={setPage}/></>}</Load>
    <div className="form-actions"><button className="secondary" onClick={close}>Cerrar historial</button></div>
    {target?<MutationDialog title="Revocar rol funcional" label="Revocar rol" danger close={()=>setTarget(undefined)} expired={expired} description="Se retirará esta asignación concreta y sus alcances efectivos. El historial se conservará."
      submit={(d,s)=>api.revokeRole(org,project,member.id,target.id,value(d,'reason'),s)} done={()=>{setTarget(undefined);setVersion(n=>n+1);changed();notify('Asignación revocada.');}}><p>{roleNames[target.role_code]}</p><Reference value={target.id}/></MutationDialog>:null}
  </Dialog>;
}
export function Members({api,expired,notify,org,permissions,chosen,choose}:{org:string;permissions:string[];chosen:Project|undefined;choose(p:Project):void}&AdminProps) {
  return <section className="admin-section"><h2>Participación en proyectos</h2><p>El proyecto administrado organiza las asignaciones; no cambia tu proyecto de trabajo.</p><ProjectPicker api={api} org={org} expired={expired} chosen={chosen} choose={choose}/>
    {chosen?<ProjectMembers key={chosen.id} api={api} org={org} project={chosen.id} permissions={permissions} expired={expired} notify={notify}/>:<Empty title="Selecciona un proyecto para gestionar sus participantes"/>}
  </section>;
}
function ProjectMembers({api,expired,notify,org,project,permissions}:{org:string;project:string;permissions:string[]}&AdminProps) {
  const [page,setPage]=useState(1),[version,setVersion]=useState(0),[creating,setCreating]=useState(false),[target,setTarget]=useState<Member>(),[role,setRole]=useState<Member>(),[history,setHistory]=useState<Member>();
  const q=useQuery('members:'+org+':'+project+':'+page+':'+version,async s=>{const [rows,p]=await Promise.all([api.members(org,project,page,s),api.project(org,project,s)]);return {rows,p};},expired);
  const changed=()=>setVersion(n=>n+1);
  return <><Load query={q.state} retry={q.reload}>{({rows,p})=><>
    <div className="section-toolbar"><div><h3>{p.project.name}</h3><Status value={p.project.status}/></div><div className="row-actions"><button className="secondary" onClick={q.reload}>Actualizar participantes</button>{p.project.status==='active'&&permissions.includes('tc.company.memberships.assign')?<button onClick={()=>setCreating(true)}>Incorporar empleado</button>:null}</div></div>
    {p.project.status!=='active'?<p className="warning-note">El proyecto no admite nuevas incorporaciones ni asignaciones. Puedes consultar el historial o retirar accesos.</p>:null}
    {rows.data.length?<Table caption="Participantes del proyecto" headings={['Empleado','Pertenencia y rol','Acciones']}>{rows.data.map(m=><tr key={m.id}><th scope="row"><UserName api={api} org={org} user={m.user_id} expired={expired}/><Reference value={m.user_id}/></th><td><Status value={m.status}/><small className="cell-detail">{m.role_code?roleNames[m.role_code]:'Sin rol funcional'}</small></td><td><div className="row-actions">
      {p.project.status==='active'&&m.status==='active'&&permissions.includes('tc.company.roles.assign')?<button className="secondary" onClick={()=>setRole(m)}>Configurar rol</button>:null}
      {permissions.includes('tc.company.roles.read')?<button className="secondary" onClick={()=>setHistory(m)}>Ver historial de roles</button>:null}
      {m.status==='active'&&permissions.includes('tc.company.memberships.revoke')?<button className="secondary danger-text" onClick={()=>setTarget(m)}>Retirar del proyecto</button>:m.status==='inactive'&&p.project.status==='active'&&permissions.includes('tc.company.memberships.reactivate')?<button className="secondary" onClick={()=>setTarget(m)}>Reactivar en el proyecto</button>:null}
    </div></td></tr>)}</Table>:<Empty title="Todavía no hay participantes">Incorpora empleados de esta empresa y luego asigna su rol y alcance.</Empty>}
    <Paginator info={rows.meta.page_info} change={setPage}/>
  </>}</Load>
  {creating?<MutationDialog title="Incorporar empleado al proyecto" label="Incorporar empleado" close={()=>setCreating(false)} expired={expired}
    description="La incorporación no concede funciones hasta que asignes un rol y su alcance."
    submit={(d,s)=>api.incorporate(org,project,value(d,'user_id'),value(d,'reason'),s)} done={()=>{setCreating(false);setPage(1);changed();notify('Empleado incorporado sin rol funcional.');}}>
    <UserPicker api={api} org={org} expired={expired}/>
  </MutationDialog>:null}
  {target?<MutationDialog title={target.status==='active'?'Retirar del proyecto':'Reactivar pertenencia al proyecto'} label="Confirmar cambio" danger={target.status==='active'} close={()=>setTarget(undefined)} expired={expired}
    description="Se conserva el historial. Reactivar la pertenencia no recupera roles ni alcances anteriores."
    submit={(d,s)=>api.memberState(org,project,target.id,target.status!=='active',value(d,'reason'),s)} done={()=>{setTarget(undefined);changed();notify('Pertenencia al proyecto actualizada.');}}><Reference value={target.user_id}/></MutationDialog>:null}
  {role?<RoleDialog api={api} org={org} project={project} member={role} expired={expired} notify={notify} close={()=>setRole(undefined)} changed={changed}/>:null}
  {history?<RoleHistory api={api} org={org} project={project} member={history} expired={expired} notify={notify} close={()=>setHistory(undefined)} changed={changed} canRevoke={permissions.includes('tc.company.roles.revoke')}/>:null}
  </>;
}
