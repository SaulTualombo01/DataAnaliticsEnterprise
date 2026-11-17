import { lazy, Suspense } from 'react';
import type { Gateway } from '../shared/contracts';
import { RealApp } from '../platform/Workspace';
const DemoApp=lazy(()=>import('./DemoApp').then(m=>({default:m.DemoApp})));
export function App({gateway}:{gateway:Gateway}) {
  return gateway.demo?<Suspense fallback={<p role="status">Preparando demostración…</p>}><DemoApp gateway={gateway}/></Suspense>:<RealApp gateway={gateway}/>;
}
