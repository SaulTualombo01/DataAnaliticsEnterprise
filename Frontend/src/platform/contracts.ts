import type { Envelope } from '../shared/contracts';
export type PageInfo = { page: number; page_size: number; returned_count: number; has_next: boolean };
export type Page<T> = { data: T[]; meta: { request_id: string; page_info: PageInfo } };
export type Company = { id: string; name: string; status: 'active' | 'inactive' };
export type Organization = Company & { created_at: string };
export type Project = { id: string; organization_id: string; name: string; status: 'active' | 'inactive' | 'archived'; created_at: string };
export type ManagedProject = { project: Project; revision: number };
export type Account = { id: string; email: string; display_name: string; status: 'active' | 'inactive'; created_at: string };
export type AccountDetail = { account: Account; revision: number };
export type AdminAccess = { platform_permissions: string[]; companies: Company[]; selected_organization: Company | null; organization_permissions: string[] };
export type WorkContext = { organization_id: string; organization_name: string; project_id: string; project_name: string; project_status: 'active' | 'archived'; membership_id: string; role_code: RoleCode | null };
export type RoleCode = 'GESTOR_CATALOGO' | 'ANALISTA_CALIDAD' | 'RESPONSABLE_GOBIERNO' | 'ANALISTA_NEGOCIO';
export type Scope = { resource_scope: 'PROJECT' | 'SOURCE' | 'ASSET'; resource_id: string | null };
export type Grant = { permission: string; scopes: Scope[] };
export type FunctionalRole = { code: RoleCode; name: string; permissions: string[] };
export type CompanyMember = { id: string; user_id: string; organization_id: string; status: 'active' | 'inactive'; assigned_by: string; assigned_at: string };
export type Member = CompanyMember & { project_id: string; current_assignment_id: string | null; role_code: RoleCode | null };
export type Assignment = { id: string; user_id: string; project_id: string; membership_id: string; role_code: RoleCode; status: 'active' | 'inactive'; assigned_by: string; assigned_at: string; revoked_at: string | null; scopes: Scope[] };
export type CompanyAdmin = { id: string; user_id: string; organization_id: string; assigned_by: string; status: 'active' | 'inactive'; assigned_at: string; revoked_at: string | null };
export type Invitation = { id: string; organization_id: string; email: string; display_name: string; invited_by: string; status: 'pending' | 'expired' | 'accepted' | 'revoked'; created_at: string; expires_at: string; accepted_user_id: string | null };
export type Delivery = { invitation: Invitation; delivery_mode: 'manual'; activation_token: string };
export interface Transport {
  get<T>(path: string, signal?: AbortSignal): Promise<Envelope<T>>;
  write<T>(method: 'POST' | 'PATCH' | 'PUT', path: string, body: unknown, signal?: AbortSignal): Promise<Envelope<T> | undefined>;
}
