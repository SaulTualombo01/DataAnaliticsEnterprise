import type { Envelope } from '../shared/contracts';
import { ApiError, UUID } from '../shared/errors';
import type { Account, AccountDetail, AdminAccess, Assignment, CompanyAdmin, CompanyMember, Delivery, FunctionalRole, Grant, Invitation, ManagedProject, Member, Organization, Page, Project, RoleCode, Scope, Transport, WorkContext } from './contracts';

const valid = (condition: boolean): void => { if (!condition) throw new ApiError(502, 'INVALID_RESPONSE'); };
const identifier = (id: string) => { if (!UUID.test(id)) throw new ApiError(422, 'INVALID_IDENTIFIER'); return id; };
const orgPath = (org: string) => '/api/v1/organizations/' + identifier(org);
const projectPath = (org: string, project: string) => orgPath(org) + '/projects/' + identifier(project);
const roleCodes = new Set(['GESTOR_CATALOGO','ANALISTA_CALIDAD','RESPONSABLE_GOBIERNO','ANALISTA_NEGOCIO']);
function company(x: Organization | { id: string; name: string; status: string }) { valid(UUID.test(x?.id ?? '') && typeof x.name === 'string' && ['active','inactive'].includes(x.status)); }
function account(x: Account) { valid(UUID.test(x?.id ?? '') && typeof x.email === 'string' && typeof x.display_name === 'string' && ['active','inactive'].includes(x.status)); }
function context(x: WorkContext) { valid(UUID.test(x?.project_id ?? '') && UUID.test(x?.organization_id ?? '') && UUID.test(x?.membership_id ?? '') && typeof x.project_name === 'string' && typeof x.organization_name === 'string' && ['active','archived'].includes(x.project_status) && (x.role_code === null || roleCodes.has(x.role_code))); }
function scope(x: Scope) { valid(x?.resource_scope === 'PROJECT' ? x.resource_id === null : ['SOURCE','ASSET'].includes(x?.resource_scope) && UUID.test(x.resource_id ?? '')); }

export function createPlatformClient(http: Transport) {
  async function get<T>(path: string, signal?: AbortSignal) { return (await http.get<T>(path, signal)).data; }
  async function write<T>(method: 'POST'|'PUT'|'PATCH', path: string, body: unknown, signal?: AbortSignal) { return (await http.write<T>(method,path,body,signal))?.data as T; }
  async function list<T>(path: string, page: number, validate: (item: T)=>void, signal?: AbortSignal): Promise<Page<T>> {
    if (!Number.isSafeInteger(page) || page < 1 || page > 2147483647) throw new ApiError(422);
    const result = await http.get<T[]>(path+'?page='+page+'&page_size=25',signal) as Page<T>;
    const info = result.meta.page_info;
    valid(Array.isArray(result.data) && result.data.length<=25 && info?.page===page && info.page_size===25 && info.returned_count===result.data.length && typeof info.has_next==='boolean');
    result.data.forEach(validate); return result;
  }
  function project(x: Project, org: string) { valid(UUID.test(x?.id ?? '') && x.organization_id===org && typeof x.name==='string' && ['active','inactive','archived'].includes(x.status)); }
  function companyMember(x: CompanyMember, org: string) { valid(UUID.test(x?.id ?? '') && UUID.test(x?.user_id ?? '') && x.organization_id===org && ['active','inactive'].includes(x.status)); }
  function member(x: Member, org: string, p: string) { companyMember(x,org); valid(x.project_id===p && (x.current_assignment_id===null || UUID.test(x.current_assignment_id)) && (x.role_code===null || roleCodes.has(x.role_code))); }
  function assignment(x: Assignment, p: string, m: string) { valid(UUID.test(x?.id ?? '') && x.project_id===p && x.membership_id===m && roleCodes.has(x.role_code) && Array.isArray(x.scopes)); x.scopes.forEach(scope); }
  function invitation(x: Invitation, org: string) { valid(UUID.test(x?.id ?? '') && x.organization_id===org && typeof x.email==='string' && typeof x.display_name==='string' && ['pending','expired','accepted','revoked'].includes(x.status)); }
  const api = {
    async admin(org?: string, signal?: AbortSignal): Promise<AdminAccess> {
      const a=await get<AdminAccess>('/api/v1/auth/administration'+(org?'?organization_id='+identifier(org):''),signal);
      valid(Array.isArray(a?.platform_permissions) && a.platform_permissions.every(p=>typeof p==='string') && Array.isArray(a.companies) && Array.isArray(a.organization_permissions) && a.organization_permissions.every(p=>typeof p==='string'));
      a.companies.forEach(company);
      valid(org ? a.selected_organization?.id===org : a.selected_organization===null);
      if(a.selected_organization) company(a.selected_organization);
      return a;
    },
    organizations: (page: number, signal?: AbortSignal) => list<Organization>('/api/v1/admin/organizations',page,company,signal),
    createOrganization: (name: string, reason: string, signal?: AbortSignal) => write<Organization>('POST','/api/v1/admin/organizations',{name,reason},signal),
    users: (org: string|undefined, page: number, signal?: AbortSignal) => list<Account>(org?orgPath(org)+'/users':'/api/v1/admin/users',page,account,signal),
    async detail(org: string|undefined, user: string, signal?: AbortSignal) {
      const a=await get<AccountDetail>((org?orgPath(org)+'/users':'/api/v1/admin/users')+'/'+identifier(user),signal);
      account(a?.account); valid(a.account.id===user && Number.isSafeInteger(a.revision) && a.revision>=0); return a;
    },
    editUser: (org: string|undefined, user: string, display_name: string, expected_revision: number, reason: string, signal?: AbortSignal) => write<AccountDetail>('PATCH',(org?orgPath(org)+'/users':'/api/v1/admin/users')+'/'+identifier(user),{display_name,expected_revision,reason},signal),
    deactivate: (org: string|undefined, user: string, reason: string, signal?: AbortSignal) => write<void>('POST',(org?orgPath(org)+'/users':'/api/v1/admin/users')+'/'+identifier(user)+'/deactivations',{reason},signal),
    projects: (org: string, page: number, signal?: AbortSignal) => list<Project>(orgPath(org)+'/projects',page,x=>project(x,org),signal),
    createProject: (org: string,name: string,reason: string,signal?: AbortSignal) => write<Project>('POST',orgPath(org)+'/projects',{name,reason},signal),
    async project(org: string,id: string,signal?: AbortSignal) { const x=await get<ManagedProject>(projectPath(org,id),signal); project(x?.project,org); valid(x.project.id===id && Number.isSafeInteger(x.revision) && x.revision>=0); return x; },
    editProject: (org: string,id: string,name: string,expected_revision: number,reason: string,signal?: AbortSignal) => write<ManagedProject>('PATCH',projectPath(org,id),{name,expected_revision,reason},signal),
    projectState: (org: string,id: string,archived: boolean,expected_revision: number,reason: string,signal?: AbortSignal) => write<ManagedProject>('POST',projectPath(org,id)+(archived?'/archivals':'/reactivations'),{expected_revision,reason},signal),
    invitations: (org: string,page: number,signal?: AbortSignal) => list<Invitation>(orgPath(org)+'/invitations',page,x=>invitation(x,org),signal),
    async invite(org: string,email: string,display_name: string,reason: string,signal?: AbortSignal) {
      const x=await write<Delivery>('POST',orgPath(org)+'/invitations',{email,display_name,reason},signal);
      invitation(x?.invitation,org); valid(x.delivery_mode==='manual' && /^[A-Za-z0-9_-]{43}$/.test(x.activation_token)); return x;
    },
    revokeInvitation: (org: string,id: string,reason: string,signal?: AbortSignal) => write<void>('POST',orgPath(org)+'/invitations/'+identifier(id)+'/revocations',{reason},signal),
    async activate(activation_token: string,new_password: string,signal?: AbortSignal) { const x=await write<Account>('POST','/api/v1/auth/invitations/accept',{activation_token,new_password},signal); account(x); return x; },
    admins: (org: string,page: number,signal?: AbortSignal) => list<CompanyAdmin>('/api/v1/admin/organizations/'+identifier(org)+'/administrators',page,x=>{ valid(x.organization_id===org && UUID.test(x.id) && UUID.test(x.user_id)); },signal),
    assignAdmin: (org: string,user: string,reason: string,signal?: AbortSignal) => write<CompanyAdmin>('POST','/api/v1/admin/organizations/'+identifier(org)+'/administrators',{user_id:identifier(user),reason},signal),
    revokeAdmin: (org: string,id: string,reason: string,signal?: AbortSignal) => write<void>('POST','/api/v1/admin/organizations/'+identifier(org)+'/administrators/'+identifier(id)+'/revocations',{reason},signal),
    companyMembers: (org: string,page: number,signal?: AbortSignal) => list<CompanyMember>(orgPath(org)+'/memberships',page,x=>companyMember(x,org),signal),
    companyMemberState: (org: string,id: string,active: boolean,reason: string,signal?: AbortSignal) => write<CompanyMember|undefined>('POST',orgPath(org)+'/memberships/'+identifier(id)+(active?'/reactivations':'/revocations'),{reason},signal),
    members: (org: string,p: string,page: number,signal?: AbortSignal) => list<Member>(projectPath(org,p)+'/memberships',page,x=>member(x,org,p),signal),
    incorporate: (org: string,p: string,user: string,reason: string,signal?: AbortSignal) => write<Member>('POST',projectPath(org,p)+'/memberships',{user_id:identifier(user),reason},signal),
    memberState: (org: string,p: string,id: string,active: boolean,reason: string,signal?: AbortSignal) => write<Member|undefined>('POST',projectPath(org,p)+'/memberships/'+identifier(id)+(active?'/reactivations':'/revocations'),{reason},signal),
    async roles(org: string,signal?: AbortSignal) { const rows=await get<FunctionalRole[]>(orgPath(org)+'/functional-roles',signal); valid(Array.isArray(rows) && rows.length<=4 && rows.every(r=>roleCodes.has(r.code) && typeof r.name==='string' && Array.isArray(r.permissions))); return rows; },
    async setRole(org: string,p: string,m: string,role_code: RoleCode,scopes: Scope[],expected_assignment_id: string|null,reason: string,signal?: AbortSignal) {
      if(expected_assignment_id!==null) identifier(expected_assignment_id);
      const x=await write<Assignment>('PUT',projectPath(org,p)+'/memberships/'+identifier(m)+'/role',{role_code,scopes,expected_assignment_id,reason},signal); assignment(x,p,m); return x;
    },
    async currentRole(org: string,p: string,m: string,signal?: AbortSignal) {
      try { const x=await get<Assignment>(projectPath(org,p)+'/memberships/'+identifier(m)+'/role',signal); assignment(x,p,m); return x; }
      catch(error) { if(error instanceof ApiError && error.status===404) return null; throw error; }
    },
    history: (org: string,p: string,m: string,page: number,signal?: AbortSignal) => list<Assignment>(projectPath(org,p)+'/memberships/'+identifier(m)+'/roles',page,x=>assignment(x,p,m),signal),
    revokeRole: (org: string,p: string,m: string,id: string,reason: string,signal?: AbortSignal) => write<void>('POST',projectPath(org,p)+'/memberships/'+identifier(m)+'/roles/'+identifier(id)+'/revocations',{reason},signal),
    contexts: (page: number,signal?: AbortSignal) => list<WorkContext>('/api/v1/auth/contexts',page,context,signal),
    async selected(signal?: AbortSignal) { const x=await get<WorkContext>('/api/v1/auth/context',signal); context(x); return x; },
    async select(project: string,signal?: AbortSignal) { const x=await write<WorkContext>('POST','/api/v1/auth/context',{project_id:identifier(project)},signal); context(x); valid(x.project_id===project); return x; },
    async permissions(project: string,signal?: AbortSignal) { const rows=await get<Grant[]>('/api/v1/auth/contexts/'+identifier(project)+'/permissions',signal); valid(Array.isArray(rows) && rows.every(r=>typeof r.permission==='string' && Array.isArray(r.scopes))); rows.forEach(r=>r.scopes.forEach(scope)); return rows; },
  };
  return api;
}
export type PlatformClient = ReturnType<typeof createPlatformClient>;
