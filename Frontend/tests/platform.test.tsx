import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { createHttpGateway } from '../src/shared/http';
import { ApiError } from '../src/shared/errors';
import { createDemoGateway } from '../src/mocks/gateway';
import { createPlatformClient } from '../src/platform/client';
import type { PlatformClient } from '../src/platform/client';
import type { AdminAccess, Assignment, Delivery, FunctionalRole, Member, Transport } from '../src/platform/contracts';
import { Companies, TokenReceipt } from '../src/platform/AdminLists';
import { Administration } from '../src/platform/Admin';
import { Auth } from '../src/platform/Auth';
import { RoleDialog } from '../src/platform/Members';
import { useQuery } from '../src/platform/ui';
import { useState } from 'react';

const id='00000000-0000-0000-0000-000000000001';
const org='00000000-0000-0000-0000-000000000002';
const project='00000000-0000-0000-0000-000000000003';
const memberId='00000000-0000-0000-0000-000000000004';
const assignmentId='00000000-0000-0000-0000-000000000005';
const envelope=(data:unknown)=>({data,meta:{request_id:id}});
const page=(data:unknown[])=>({data,meta:{request_id:id,page_info:{page:1,page_size:25,returned_count:data.length,has_next:false}}});
const response=(data:unknown,status=200)=>new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json'}});
const access:AdminAccess={platform_permissions:[],companies:[{id:org,name:'Empresa autorizada',status:'active'}],selected_organization:{id:org,name:'Empresa autorizada',status:'active'},organization_permissions:['tc.company.roles.read','tc.company.roles.assign']};
const member:Member={id:memberId,user_id:id,organization_id:org,project_id:project,status:'active',assigned_by:id,assigned_at:'2026-11-14T00:00:00Z',current_assignment_id:null,role_code:null};
const roles:FunctionalRole[]=[{code:'GESTOR_CATALOGO',name:'Gestor de fuentes y catálogo',permissions:['m1.assets.read']},{code:'ANALISTA_CALIDAD',name:'Analista de calidad',permissions:['m1.progress.read']}];
const assigned:Assignment={id:assignmentId,user_id:id,project_id:project,membership_id:memberId,role_code:'ANALISTA_CALIDAD',status:'active',assigned_by:id,assigned_at:'2026-11-14T00:00:00Z',revoked_at:null,scopes:[{resource_scope:'PROJECT',resource_id:null}]};
const apiStub=(fields:Partial<PlatformClient>)=>fields as PlatformClient;

describe('cliente de plataforma',()=>{
  it('escribe PATCH con CSRF nuevo, cookie y revisión explícita',async()=>{
    const fetcher=vi.fn<typeof fetch>().mockResolvedValueOnce(response(envelope({header_name:'X-CSRF-TOKEN',token:'synthetic'}))).mockResolvedValueOnce(response(envelope({account:{id,email:'a@example.test',display_name:'Nombre nuevo',status:'active',created_at:'2026-11-14T00:00:00Z'},revision:1})));
    await createHttpGateway(fetcher).platform!.editUser(undefined,id,'Nombre nuevo',0,'Corrección');
    expect(fetcher.mock.calls[1][0]).toBe('/api/v1/admin/users/'+id);
    expect(fetcher.mock.calls[1][1]).toMatchObject({method:'PATCH',credentials:'same-origin',cache:'no-store',headers:{'X-CSRF-TOKEN':'synthetic'}});
    expect(JSON.parse(String(fetcher.mock.calls[1][1]?.body))).toEqual({display_name:'Nombre nuevo',expected_revision:0,reason:'Corrección'});
  });
  it('no reintenta una escritura cuyo resultado no pudo confirmarse',async()=>{
    const fetcher=vi.fn<typeof fetch>().mockResolvedValueOnce(response(envelope({header_name:'X-CSRF-TOKEN',token:'synthetic'}))).mockRejectedValueOnce(new TypeError('private failure'));
    await expect(createHttpGateway(fetcher).platform!.createOrganization('Empresa','Alta')).rejects.toMatchObject({status:0});
    expect(fetcher).toHaveBeenCalledTimes(2);expect(new ApiError(0).message).not.toContain('private');
  });
  it('rechaza resultados y capacidades de otra empresa',async()=>{
    const get=vi.fn().mockResolvedValueOnce(page([{id:project,organization_id:id,name:'Ajeno',status:'active'}])).mockResolvedValueOnce(envelope({...access,selected_organization:{id,name:'Ajena',status:'active'}}));
    const client=createPlatformClient({get,write:vi.fn()} as Transport);
    await expect(client.projects(org,1)).rejects.toMatchObject({code:'INVALID_RESPONSE'});
    await expect(client.admin(org)).rejects.toMatchObject({code:'INVALID_RESPONSE'});
  });
  it('valida identificadores y página antes de construir rutas',async()=>{
    const get=vi.fn(),client=createPlatformClient({get,write:vi.fn()} as Transport);
    expect(()=>client.members('../ajeno',project,1)).toThrow(ApiError);
    await expect(client.projects(org,0)).rejects.toMatchObject({status:422});expect(get).not.toHaveBeenCalled();
  });
  it('un contexto vacío no se sustituye por un proyecto ficticio',async()=>{
    const client=createPlatformClient({get:vi.fn().mockResolvedValue(page([])),write:vi.fn()} as Transport);
    expect((await client.contexts(1)).data).toEqual([]);
  });
});

function Race({identity,load}:{identity:string;load:(key:string,signal:AbortSignal)=>Promise<string>}) {
  const q=useQuery(identity,s=>load(identity,s),()=>{});
  return <p>{q.state.phase==='ready'?q.state.data:q.state.phase}</p>;
}
describe('formularios y aislamiento',()=>{
  it('oculta el contexto anterior de inmediato y descarta su respuesta tardía',async()=>{
    let oldResolve:(value:string)=>void=()=>{};const load=vi.fn((key:string,_s:AbortSignal)=>key==='A'?new Promise<string>(r=>{oldResolve=r;}):Promise.resolve('Empresa B'));
    const {rerender}=render(<Race identity="A" load={load}/>);
    await waitFor(()=>expect(load).toHaveBeenCalledWith('A',expect.anything()));
    rerender(<Race identity="B" load={load}/>);
    await screen.findByText('Empresa B');await act(async()=>oldResolve('Empresa A'));
    expect(screen.queryByText('Empresa A')).toBeNull();expect(screen.getByText('Empresa B')).toBeTruthy();
  });
  it('conserva nombre y motivo cuando el servidor rechaza una creación',async()=>{
    const user=userEvent.setup(),create=vi.fn().mockRejectedValue(new ApiError(409));
    const api=apiStub({organizations:vi.fn().mockResolvedValue(page([])),createOrganization:create});
    render(<Companies api={api} permissions={['tc.organizations.create']} expired={vi.fn()} notify={vi.fn()} select={vi.fn()}/>);
    await user.click(screen.getByRole('button',{name:'Crear empresa'}));
    await user.type(screen.getByLabelText('Nombre de la empresa'),'Empresa propuesta');await user.type(screen.getByLabelText('Motivo del cambio'),'Alta acordada');
    await user.click(within(screen.getByRole('dialog')).getByRole('button',{name:'Crear empresa'}));
    expect((await screen.findByRole('alert')).textContent).toContain('Los datos cambiaron');
    expect((screen.getByLabelText('Nombre de la empresa') as HTMLInputElement).value).toBe('Empresa propuesta');
    expect((screen.getByLabelText('Motivo del cambio') as HTMLTextAreaElement).value).toBe('Alta acordada');expect(create).toHaveBeenCalledTimes(1);
  });
  it('una cuenta sin capacidades no recibe botones administrativos',async()=>{
    const empty={platform_permissions:[],companies:[],selected_organization:null,organization_permissions:[]};
    render(<Administration api={apiStub({admin:vi.fn().mockResolvedValue(empty)})} initial={empty} expired={vi.fn()} notify={vi.fn()}/>);
    await screen.findByText('No hay administración disponible');expect(screen.queryByRole('button',{name:'Crear empresa'})).toBeNull();expect(screen.queryByRole('button',{name:'Usuarios globales'})).toBeNull();
  });
  it('una referencia de rol obsoleta conserva la propuesta hasta revisar el estado actual',async()=>{
    const user=userEvent.setup(),setRole=vi.fn().mockRejectedValueOnce(new ApiError(409)).mockResolvedValueOnce(assigned);
    const currentRole=vi.fn().mockResolvedValueOnce(null).mockResolvedValueOnce(assigned);
    const api=apiStub({admin:vi.fn().mockResolvedValue(access),roles:vi.fn().mockResolvedValue(roles),currentRole,setRole});
    render(<RoleDialog api={api} org={org} project={project} member={member} expired={vi.fn()} notify={vi.fn()} close={vi.fn()} changed={vi.fn()}/>);
    await screen.findByLabelText('Rol funcional');await user.selectOptions(screen.getByLabelText('Rol funcional'),'GESTOR_CATALOGO');
    await user.click(screen.getByLabelText('Todo el proyecto'));await user.type(screen.getByLabelText('Motivo del cambio'),'Función acordada');
    await user.click(screen.getByRole('button',{name:'Asignar rol'}));await screen.findByRole('alert');
    expect((screen.getByLabelText('Rol funcional') as HTMLSelectElement).value).toBe('GESTOR_CATALOGO');expect(setRole.mock.calls[0][5]).toBeNull();
    await user.click(screen.getByRole('button',{name:'Actualizar referencia'}));await screen.findByText(/Referencia actualizada/);
    expect((screen.getByLabelText('Motivo del cambio') as HTMLTextAreaElement).value).toBe('Función acordada');
    await user.click(screen.getByRole('button',{name:'Confirmar sustitución'}));await waitFor(()=>expect(setRole).toHaveBeenCalledTimes(2));expect(setRole.mock.calls[1][5]).toBe(assignmentId);
  });
});

const delivery:Delivery={invitation:{id,organization_id:org,email:'invitado@example.test',display_name:'Persona invitada',invited_by:id,status:'pending',created_at:'2026-11-14T00:00:00Z',expires_at:'2026-11-15T00:00:00Z',accepted_user_id:null},delivery_mode:'manual',activation_token:'A'.repeat(43)};
function Receipt(){const[open,setOpen]=useState(true);return open?<TokenReceipt delivery={delivery} close={()=>setOpen(false)}/>:<p>Recibo cerrado</p>;}
describe('credenciales y activación',()=>{
  it('el código se oculta y desaparece al cerrar, sin usar almacenamiento web',async()=>{
    const user=userEvent.setup();render(<Receipt/>);
    expect((screen.getByLabelText('Código de activación') as HTMLInputElement).type).toBe('password');
    await user.click(screen.getByRole('button',{name:'Cerrar recibo'}));expect(screen.queryByLabelText('Código de activación')).toBeNull();
    expect(localStorage.length).toBe(0);expect(sessionStorage.length).toBe(0);
  });
  it('un login401 muestra error y elimina la contraseña del formulario',async()=>{
    const user=userEvent.setup(),gateway={...createDemoGateway(0),demo:undefined,login:vi.fn().mockRejectedValue(new ApiError(401))};
    render(<Auth gateway={gateway} logged={vi.fn()} expired={false}/>);
    await user.type(screen.getByLabelText('Correo electrónico'),'invitado@example.test');await user.type(screen.getByLabelText('Contraseña',{exact:true}),'contraseña sintética');
    await user.click(screen.getByRole('button',{name:'Iniciar sesión'}));expect((await screen.findByRole('alert')).textContent).toContain('Revisa tus credenciales');expect((screen.getByLabelText('Contraseña',{exact:true}) as HTMLInputElement).value).toBe('');
  });
  it('activar no inicia sesión automáticamente ni conserva contraseña o código',async()=>{
    const user=userEvent.setup(),activate=vi.fn().mockResolvedValue({id,email:'invitado@example.test',display_name:'Invitado',status:'active',created_at:'2026-11-14T00:00:00Z'}),logged=vi.fn();
    const gateway={...createDemoGateway(0),demo:undefined,platform:apiStub({activate})};
    render(<Auth gateway={gateway} logged={logged} expired={false}/>);
    await user.click(screen.getByRole('button',{name:'Tengo una invitación'}));
    await user.type(screen.getByLabelText('Código de invitación'),'A'.repeat(43));await user.type(screen.getByLabelText('Nueva contraseña',{exact:true}),'Frase sintetica de pruebas 2026');await user.type(screen.getByLabelText('Repite la nueva contraseña'),'Frase sintetica de pruebas 2026');
    await user.click(screen.getByRole('button',{name:'Activar cuenta'}));await screen.findByText(/Cuenta activada/);expect(logged).not.toHaveBeenCalled();expect(screen.queryByLabelText('Código de invitación')).toBeNull();expect((screen.getByLabelText('Contraseña',{exact:true}) as HTMLInputElement).value).toBe('');
  });
});
