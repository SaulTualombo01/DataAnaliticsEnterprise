import type { PlatformClient } from '../platform/client';
export type Profile = { id: string; email: string; display_name: string };
export type Context = { organization_id: string; organization_name: string; project_id: string; project_name: string; role_label: string; asset_id?: string };
export type Asset = {
  id: string; organization_id: string; project_id: string; source_id: string;
  name: string; kind: string; status: string; metadata_version_id: string;
  origin: { namespace: string | null; object_path: string | null };
  business_description: string | null; tags: string[]; updated_at: string;
};
export type Field = {
  id: string; asset_id: string; project_id: string; organization_id: string;
  metadata_version_id: string; name: string; path: string; position: number;
  native_type: string | null; logical_type: string; nullable: boolean | null;
  business_description: string | null; status: string;
};
export type FieldPage = { data: Field[]; meta: { request_id: string; page_info: { page: number; page_size: number; returned_count: number; has_next: boolean } } };
export type Envelope<T> = { data: T; meta: { request_id: string } };
export type DemoScenario = 'normal' | 'empty' | 'denied' | 'unavailable' | 'expired';
export interface Gateway {
  platform?: PlatformClient;
  profile(signal?: AbortSignal): Promise<Profile>;
  login(email: string, password: string): Promise<Profile>;
  logout(): Promise<void>;
  asset(project: string, asset: string, signal?: AbortSignal): Promise<Asset>;
  fields(project: string, asset: string, version: string, page: number, signal?: AbortSignal): Promise<FieldPage>;
  demo?: { contexts: readonly Context[]; configure(scenario: DemoScenario): void };
}
