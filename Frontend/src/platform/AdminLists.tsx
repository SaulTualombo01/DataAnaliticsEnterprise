import { useId, useState } from 'react';
import type { PlatformClient } from './client';
import type { Account, AccountDetail, CompanyAdmin, CompanyMember, Delivery, Invitation, ManagedProject, Organization, Project } from './contracts';
import { date, Dialog, Empty, ErrorNote, Field, Load, MutationDialog, Paginator, Reference, Status, Table, useAction, useQuery, value } from './ui';

export type AdminProps = { api:PlatformClient; expired():void; notify(message:string):void };
export function UserName({api,org,user,expired}:{api:PlatformClient;org:string;user:string;expired():void}) {
  const q=useQuery('name:'+org+':'+user,s=>api.detail(org,user,s),expired);
  return q.state.phase==='ready'?<><strong>{q.state.data.account.display_name}</strong><small className="cell-detail">{q.state.data.account.email}</small></>:<Reference value={user}/>;
}
export function UserPicker({api,org,expired,label='Empleado',name='user_id'}:{api:PlatformClient;org?:string;expired():void;label?:string;name?:string}) {
  const id=useId();
  const [page,setPage]=useState(1),[chosen,setChosen]=useState<Account>();
  const q=useQuery('picker:'+(org||'global')+':'+page,s=>api.users(org,page,s),expired);
  if(q.state.phase!=='ready'&&!chosen) return <div className="user-picker"><label htmlFor={id}>{label}</label><select id={id} name={name} required value="" onChange={()=>{}}><option value="">Selecciona una cuenta cuando termine la consulta</option></select><Load query={q.state} retry={q.reload}>{()=>null}</Load></div>;
  return <div className="user-picker"><Load query={q.state} retry={q.reload}>{rows=><>
    <label htmlFor={id}>{label}</label><select id={id} name={name} required value={chosen?.id||''} onChange={e=>setChosen(rows.data.find(a=>a.id===e.target.value)||chosen)}>
      <option value="" disabled>Selecciona una cuenta activa</option>
      {chosen && !rows.data.some(a=>a.id===chosen.id)?<option value={chosen.id}>{chosen.display_name} ({chosen.email})</option>:null}
      {rows.data.map(a=><option key={a.id} value={a.id} disabled={a.status!=='active'}>{a.display_name} ({a.email}){a.status!=='active'?' — inactivo':''}</option>)}
    </select><Paginator info={rows.meta.page_info} change={setPage}/>
    {rows.data.length===0?<p>No hay cuentas en esta página. Primero invita y activa la cuenta.</p>:null}
  </>}</Load></div>;
}
export function Companies({api,expired,notify,permissions,select}:{permissions:string[];select(id:string):void}&AdminProps) {
  const [page,setPage]=useState(1),[creating,setCreating]=useState(false),[version,setVersion]=useState(0);
  const q=useQuery('companies:'+page+':'+version,s=>api.organizations(page,s),expired);
  return <section className="admin-section"><div className="section-toolbar"><h2>Empresas</h2>{permissions.includes('tc.organizations.create')?<button onClick={()=>setCreating(true)}>Crear empresa</button>:null}</div>
    <Load query={q.state} retry={q.reload}>{rows=><>{rows.data.length?<Table caption="Empresas registradas" headings={['Empresa','Estado','Acciones']}>{rows.data.map(o=><tr key={o.id}><th scope="row">{o.name}<Reference value={o.id}/></th><td><Status value={o.status}/></td><td><button className="secondary" onClick={()=>select(o.id)}>Administrar {o.name}</button></td></tr>)}</Table>:<Empty title="Todavía no hay empresas">Crea una empresa para organizar sus empleados y proyectos.</Empty>}<Paginator info={rows.meta.page_info} change={setPage}/></>}</Load>
    {creating?<MutationDialog title="Crear empresa" label="Crear empresa" close={()=>setCreating(false)} expired={expired}
      submit={(d,s)=>api.createOrganization(value(d,'name'),value(d,'reason'),s)}
      done={o=>{setCreating(false);setPage(1);setVersion(n=>n+1);notify('Empresa creada.');select(o.id);}}>
      <Field label="Nombre de la empresa" name="name" required maxLength={200}/>
    </MutationDialog>:null}
  </section>;
}
export function Users({api,expired,notify,org,permissions}:{org?:string;permissions:string[]}&AdminProps) {
  const [page,setPage]=useState(1),[version,setVersion]=useState(0),[editing,setEditing]=useState<AccountDetail>(),[target,setTarget]=useState<Account>();
  const read=useAction(expired);
  const q=useQuery('users:'+(org||'global')+':'+page+':'+version,s=>api.users(org,page,s),expired);
  const canEdit=permissions.includes(org?'tc.company.users.update':'tc.users.update');
  const canDisable=permissions.includes(org?'tc.company.users.deactivate':'tc.users.deactivate');
  const changed=()=>{setVersion(n=>n+1);notify('Cambio de cuenta guardado.');};
  return <section className="admin-section"><div className="section-toolbar"><h2>{org?'Empleados de la empresa':'Usuarios de la plataforma'}</h2><button className="secondary" onClick={q.reload}>Actualizar lista</button></div>
    {read.error?<ErrorNote error={read.error}/>:null}
    <Load query={q.state} retry={q.reload}>{rows=><>{rows.data.length?<Table caption={org?'Empleados':'Usuarios'} headings={['Persona','Estado','Acciones']}>{rows.data.map(a=><tr key={a.id}><th scope="row">{a.display_name}<small className="cell-detail">{a.email}</small><Reference value={a.id}/></th><td><Status value={a.status}/></td><td><div className="row-actions">{canEdit?<button className="secondary" disabled={read.pending} onClick={()=>void read.run(s=>api.detail(org,a.id,s),setEditing)}>Editar {a.display_name}</button>:null}{canDisable && a.status==='active'?<button className="secondary danger-text" onClick={()=>setTarget(a)}>Desactivar {a.display_name}</button>:null}</div></td></tr>)}</Table>:<Empty title="No hay cuentas en esta página">Las invitaciones aceptadas aparecerán en la lista de empleados.</Empty>}<Paginator info={rows.meta.page_info} change={setPage}/></>}</Load>
    {editing?<MutationDialog title="Editar nombre de la cuenta" description={'Cuenta: '+editing.account.email} close={()=>setEditing(undefined)} expired={expired}
      submit={(d,s)=>api.editUser(org,editing.account.id,value(d,'display_name'),editing.revision,value(d,'reason'),s)}
      done={()=>{setEditing(undefined);changed();}}>
      <Field label="Nombre visible" name="display_name" defaultValue={editing.account.display_name} required maxLength={200}/>
      <p className="form-help">El correo de acceso, la contraseña y los roles se conservan. Si hay un cambio concurrente, vuelve a consultar la cuenta antes de confirmar.</p>
    </MutationDialog>:null}
    {target?<MutationDialog title={'Desactivar a '+target.display_name} description={org?'Solo se permite dar de baja a empleados ordinarios de esta empresa. Se retirarán sus sesiones, membresías y asignaciones.':'Se retirarán las sesiones, membresías y asignaciones de esta cuenta. La plataforma impide desactivar al último administrador disponible.'} label="Confirmar desactivación" danger close={()=>setTarget(undefined)} expired={expired}
      submit={(d,s)=>api.deactivate(org,target.id,value(d,'reason'),s)} done={()=>{setTarget(undefined);changed();}}>
      <p>{target.email}</p>
    </MutationDialog>:null}
  </section>;
}
export function TokenReceipt({delivery,close}:{delivery:Delivery;close():void}) {
  const [visible,setVisible]=useState(false),[copied,setCopied]=useState(false),[copyError,setCopyError]=useState(false);
  return <Dialog title="Invitación creada" close={close}><p>Para {delivery.invitation.display_name} ({delivery.invitation.email}). Vence el {date(delivery.invitation.expires_at)}.</p>
    <p className="warning-note">Entrega manual de desarrollo. Comparte este código por un canal privado con la persona invitada. No se envió correo.</p>
    <Field label="Código de activación" type={visible?'text':'password'} value={delivery.activation_token} readOnly autoComplete="off" spellCheck={false}/>
    <div className="row-actions"><button className="secondary" onClick={()=>setVisible(v=>!v)}>{visible?'Ocultar código':'Mostrar código'}</button><button className="secondary" onClick={()=>{void navigator.clipboard.writeText(delivery.activation_token).then(()=>setCopied(true)).catch(()=>setCopyError(true));}}>Copiar código</button></div>
    {copied?<p role="status">Código copiado. Compártelo únicamente con la persona invitada.</p>:null}
    {copyError?<p role="alert">No se pudo copiar. Puedes mostrar y seleccionar el código.</p>:null}
    <p className="form-help">Este recibo se elimina al cerrar o cambiar de empresa. Si pierdes el código, una nueva invitación para el mismo correo invalidará la anterior.</p>
    <div className="form-actions"><button onClick={close}>Cerrar recibo</button></div>
  </Dialog>;
}
export function Invitations({api,expired,notify,org,permissions}:{org:string;permissions:string[]}&AdminProps) {
  const [page,setPage]=useState(1),[version,setVersion]=useState(0),[creating,setCreating]=useState(false),[receipt,setReceipt]=useState<Delivery>(),[target,setTarget]=useState<Invitation>();
  const canRead=permissions.includes('tc.company.invitations.read');
  const q=useQuery('invitations:'+org+':'+page+':'+version,s=>canRead?api.invitations(org,page,s):Promise.resolve(null),expired);
  return <section className="admin-section"><div className="section-toolbar"><h2>Invitaciones</h2>{permissions.includes('tc.company.users.invite')?<button onClick={()=>setCreating(true)}>Invitar empleado</button>:null}</div>
    <p>Una invitación activa la cuenta dentro de esta empresa. La participación y el rol de proyecto se asignan después.</p>
    <Load query={q.state} retry={q.reload}>{rows=>rows?<>{rows.data.length?<Table caption="Invitaciones de la empresa" headings={['Persona invitada','Estado','Vencimiento','Acciones']}>{rows.data.map(i=><tr key={i.id}><th scope="row">{i.display_name}<small className="cell-detail">{i.email}</small></th><td><Status value={i.status}/></td><td>{date(i.expires_at)}</td><td>{permissions.includes('tc.company.invitations.revoke') && (i.status==='pending'||i.status==='expired')?<button className="secondary" onClick={()=>setTarget(i)}>Revocar invitación de {i.display_name}</button>:null}</td></tr>)}</Table>:<Empty title="Todavía no hay invitaciones">Invita a los empleados que participarán en la empresa.</Empty>}<Paginator info={rows.meta.page_info} change={setPage}/></>:<Empty title="La consulta de invitaciones no está autorizada"/>}</Load>
    {creating?<MutationDialog title="Invitar empleado" label="Crear invitación" close={()=>setCreating(false)} expired={expired}
      submit={(d,s)=>api.invite(org,value(d,'email'),value(d,'display_name'),value(d,'reason'),s)}
      done={delivery=>{setCreating(false);setReceipt(delivery);setPage(1);setVersion(n=>n+1);notify('Invitación creada.');}}>
      <Field label="Nombre del empleado" name="display_name" required maxLength={200}/>
      <Field label="Correo del empleado" name="email" type="email" required maxLength={254}/>
    </MutationDialog>:null}
    {target?<MutationDialog title="Revocar invitación" description={'El código de '+target.email+' dejará de funcionar.'} label="Revocar invitación" danger close={()=>setTarget(undefined)} expired={expired}
      submit={(d,s)=>api.revokeInvitation(org,target.id,value(d,'reason'),s)} done={()=>{setTarget(undefined);setVersion(n=>n+1);notify('Invitación revocada.');}}><p>{target.display_name}</p></MutationDialog>:null}
    {receipt?<TokenReceipt delivery={receipt} close={()=>setReceipt(undefined)}/>:null}
  </section>;
}
export function Projects({api,expired,notify,org,permissions,manage}:{org:string;permissions:string[];manage(project:Project):void}&AdminProps) {
  const [page,setPage]=useState(1),[version,setVersion]=useState(0),[creating,setCreating]=useState(false),[editing,setEditing]=useState<ManagedProject>(),[target,setTarget]=useState<{p:ManagedProject;archive:boolean}>();
  const action=useAction(expired),q=useQuery('projects:'+org+':'+page+':'+version,s=>api.projects(org,page,s),expired);
  const changed=()=>{setVersion(n=>n+1);notify('Cambio de proyecto guardado.');};
  return <section className="admin-section"><div className="section-toolbar"><h2>Proyectos de la empresa</h2>{permissions.includes('tc.company.projects.create')?<button onClick={()=>setCreating(true)}>Crear proyecto</button>:null}</div>
    {action.error?<ErrorNote error={action.error}/>:null}
    <Load query={q.state} retry={q.reload}>{rows=><>{rows.data.length?<Table caption="Proyectos de la empresa" headings={['Proyecto','Estado','Acciones']}>{rows.data.map(p=><tr key={p.id}><th scope="row">{p.name}<Reference value={p.id}/></th><td><Status value={p.status}/></td><td><div className="row-actions">{permissions.includes('tc.company.memberships.read')?<button className="secondary" onClick={()=>manage(p)}>Gestionar miembros de {p.name}</button>:null}
      {permissions.includes('tc.company.projects.update')?<button className="secondary" disabled={action.pending} onClick={()=>void action.run(s=>api.project(org,p.id,s),setEditing)}>Editar {p.name}</button>:null}
      {p.status==='active' && permissions.includes('tc.company.projects.archive')?<button className="secondary danger-text" disabled={action.pending} onClick={()=>void action.run(s=>api.project(org,p.id,s),loaded=>setTarget({p:loaded,archive:true}))}>Archivar {p.name}</button>:null}
      {p.status==='archived' && permissions.includes('tc.company.projects.reactivate')?<button className="secondary" disabled={action.pending} onClick={()=>void action.run(s=>api.project(org,p.id,s),loaded=>setTarget({p:loaded,archive:false}))}>Reabrir {p.name}</button>:null}
    </div></td></tr>)}</Table>:<Empty title="Todavía no hay proyectos">Crea un proyecto y asigna a sus participantes explícitamente.</Empty>}<Paginator info={rows.meta.page_info} change={setPage}/></>}</Load>
    {creating?<MutationDialog title="Crear proyecto" label="Crear proyecto" close={()=>setCreating(false)} expired={expired}
      submit={(d,s)=>api.createProject(org,value(d,'name'),value(d,'reason'),s)} done={()=>{setCreating(false);setPage(1);changed();}}>
      <Field label="Nombre del proyecto" name="name" required maxLength={200}/>
    </MutationDialog>:null}
    {editing?<MutationDialog title="Editar nombre del proyecto" close={()=>setEditing(undefined)} expired={expired}
      submit={(d,s)=>api.editProject(org,editing.project.id,value(d,'name'),editing.revision,value(d,'reason'),s)} done={()=>{setEditing(undefined);changed();}}>
      <Field label="Nombre del proyecto" name="name" defaultValue={editing.project.name} required maxLength={200}/>
    </MutationDialog>:null}
    {target?<MutationDialog title={target.archive?'Archivar proyecto':'Reabrir proyecto'} label={target.archive?'Confirmar archivo':'Confirmar reapertura'} danger={target.archive} description={target.archive?'Se conservará la información y se bloquearán las nuevas escrituras y ejecuciones. El historial seguirá sujeto a los permisos vigentes.':'Se habilitarán las operaciones permitidas por las asignaciones que sigan vigentes. No se recuperarán roles revocados.'}
      close={()=>setTarget(undefined)} expired={expired} submit={(d,s)=>api.projectState(org,target.p.project.id,target.archive,target.p.revision,value(d,'reason'),s)} done={()=>{setTarget(undefined);changed();}}>
      <p>{target.p.project.name}</p>
    </MutationDialog>:null}
  </section>;
}
export function CompanyMembers({api,expired,notify,org,permissions}:{org:string;permissions:string[]}&AdminProps) {
  const [page,setPage]=useState(1),[version,setVersion]=useState(0),[target,setTarget]=useState<CompanyMember>();
  const q=useQuery('company-members:'+org+':'+page+':'+version,s=>api.companyMembers(org,page,s),expired);
  return <section className="admin-section"><h2>Pertenencia a la empresa</h2><p>Retirar esta pertenencia retira también los proyectos y las facultades delegadas. Reactivarla exige asignar de nuevo los accesos.</p>
    <Load query={q.state} retry={q.reload}>{rows=><>{rows.data.length?<Table caption="Membresías de empresa" headings={['Empleado','Estado','Acciones']}>{rows.data.map(m=><tr key={m.id}><th scope="row"><UserName api={api} org={org} user={m.user_id} expired={expired}/></th><td><Status value={m.status}/></td><td>{permissions.includes(m.status==='active'?'tc.company.memberships.revoke':'tc.company.memberships.reactivate')?<button className="secondary" onClick={()=>setTarget(m)}>{m.status==='active'?'Retirar pertenencia':'Reactivar pertenencia'} de {m.user_id}</button>:null}</td></tr>)}</Table>:<Empty title="Sin membresías empresariales"/>}<Paginator info={rows.meta.page_info} change={setPage}/></>}</Load>
    {target?<MutationDialog title={target.status==='active'?'Retirar pertenencia empresarial':'Reactivar pertenencia empresarial'} description="No se restauran automáticamente proyectos, roles ni alcances anteriores." label="Confirmar cambio" danger={target.status==='active'} close={()=>setTarget(undefined)} expired={expired}
      submit={(d,s)=>api.companyMemberState(org,target.id,target.status!=='active',value(d,'reason'),s)} done={()=>{setTarget(undefined);setVersion(n=>n+1);notify('Pertenencia empresarial actualizada.');}}><Reference value={target.user_id}/></MutationDialog>:null}
  </section>;
}
export function Administrators({api,expired,notify,org,permissions}:{org:string;permissions:string[]}&AdminProps) {
  const [page,setPage]=useState(1),[version,setVersion]=useState(0),[creating,setCreating]=useState(false),[target,setTarget]=useState<CompanyAdmin>();
  const canRead=permissions.includes('tc.company_administrators.read');
  const q=useQuery('company-admins:'+org+':'+page+':'+version,s=>canRead?api.admins(org,page,s):Promise.resolve(null),expired);
  return <section className="admin-section"><div className="section-toolbar"><h2>Administradores de empresa</h2>{permissions.includes('tc.company_administrators.assign')?<button onClick={()=>setCreating(true)}>Designar administrador</button>:null}</div>
    <Load query={q.state} retry={q.reload}>{rows=>rows?<>{rows.data.length?<Table caption="Designaciones empresariales" headings={['Cuenta','Estado','Fecha','Acciones']}>{rows.data.map(a=><tr key={a.id}><th scope="row"><UserName api={api} org={org} user={a.user_id} expired={expired}/></th><td><Status value={a.status}/></td><td>{date(a.assigned_at)}</td><td>{a.status==='active' && permissions.includes('tc.company_administrators.revoke')?<button className="secondary danger-text" onClick={()=>setTarget(a)}>Revocar designación de {a.user_id}</button>:null}</td></tr>)}</Table>:<Empty title="Sin administradores designados">Activa primero la cuenta invitada y luego designa al responsable de la empresa.</Empty>}<Paginator info={rows.meta.page_info} change={setPage}/></>:<Empty title="Consulta no autorizada"/>}</Load>
    {creating?<MutationDialog title="Designar administrador de empresa" description="La cuenta debe estar activa y pertenecer a esta empresa. Esta designación no concede funciones de catálogo ni de otros componentes." label="Designar administrador" close={()=>setCreating(false)} expired={expired}
      submit={(d,s)=>api.assignAdmin(org,value(d,'user_id'),value(d,'reason'),s)} done={()=>{setCreating(false);setVersion(n=>n+1);notify('Administrador designado.');}}>
      <UserPicker api={api} org={org} expired={expired} label="Cuenta administradora"/>
    </MutationDialog>:null}
    {target?<MutationDialog title="Revocar administración empresarial" label="Revocar designación" danger close={()=>setTarget(undefined)} expired={expired}
      description="Se retirarán las facultades de administración empresarial y sus invitaciones pendientes. Se conservará el historial."
      submit={(d,s)=>api.revokeAdmin(org,target.id,value(d,'reason'),s)} done={()=>{setTarget(undefined);setVersion(n=>n+1);notify('Designación revocada.');}}><Reference value={target.user_id}/></MutationDialog>:null}
  </section>;
}
