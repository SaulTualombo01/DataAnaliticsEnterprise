import { useCallback, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import type { DemoScenario, Gateway, Profile } from '../shared/contracts';
import { aborted, ApiError, message, UUID } from '../shared/http';
import { modules } from '../modules/registry';
import type { ModuleId } from '../modules/registry';
import { Catalogo } from '../modules/catalogo/Catalogo';
import { Brand } from '../shared/Brand';

type Locator = { projectId: string; assetId: string };

function Login({ gateway, onLogin, expired }: { gateway: Gateway; onLogin(profile: Profile): void; expired: boolean }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  async function enter(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault();
    if (busy) return;
    const form = event?.currentTarget;
    const data = form ? new FormData(form) : null;
    const email = String(data?.get('email') || '');
    const password = String(data?.get('password') || '');
    const input = form?.elements.namedItem('password');
    if (input instanceof HTMLInputElement) input.value = '';
    setBusy(true); setError(undefined);
    try { onLogin(await gateway.login(email, password)); }
    catch (failure) { setError(failure); }
    finally { setBusy(false); }
  }
  return <main id="main" className="login-page"><section className="login-panel"><Brand className="brand-login"/><h1>Tu espacio de datos</h1><p>Consulta los activos y el diccionario de tu proyecto.</p>
    {expired ? <p role="status">La sesión terminó. Vuelve a entrar para continuar.</p> : null}
    {gateway.demo ? <button disabled={busy} onClick={() => void enter()}>{busy ? 'Entrando…' : 'Entrar a la demostración'}</button> : <form onSubmit={enter}><label htmlFor="email">Correo electrónico</label><input id="email" name="email" type="email" autoComplete="username" required maxLength={254} disabled={busy} /><label htmlFor="password">Contraseña</label><input id="password" name="password" type="password" autoComplete="current-password" required disabled={busy} /><button disabled={busy} type="submit">{busy ? 'Verificando acceso…' : 'Iniciar sesión'}</button></form>}
    {error ? <p className="error-text" role="alert">{error instanceof ApiError && error.status === 401 ? 'No se pudo iniciar sesión. Revisa tus credenciales.' : message(error)}</p> : null}
  </section></main>;
}

function ResourceForm({ onOpen }: { onOpen(locator: Locator): void }) {
  const [invalid, setInvalid] = useState(false);
  return <form className="resource-form" onSubmit={event => {
    event.preventDefault(); const data = new FormData(event.currentTarget);
    const projectId = String(data.get('project') || '').trim(); const assetId = String(data.get('asset') || '').trim();
    if (!UUID.test(projectId) || !UUID.test(assetId)) { setInvalid(true); return; }
    setInvalid(false); onOpen({ projectId, assetId });
  }}><div><label htmlFor="project">Identificador del proyecto</label><input id="project" name="project" required spellCheck={false} placeholder="UUID del proyecto asignado" /></div><div><label htmlFor="asset">Identificador del activo</label><input id="asset" name="asset" required spellCheck={false} placeholder="UUID del activo" /></div><button type="submit">Abrir activo</button>{invalid ? <p className="error-text" role="alert">Escribe identificadores UUID completos en minúsculas.</p> : null}</form>;
}

export function DemoApp({ gateway }: { gateway: Gateway }) {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [boot, setBoot] = useState(true);
  const [bootAttempt, setBootAttempt] = useState(0);
  const [bootError, setBootError] = useState<unknown>();
  const [expired, setExpired] = useState(false);
  const [active, setActive] = useState<ModuleId>('catalogo');
  const [contextIndex, setContextIndex] = useState(0);
  const [scenario, setScenario] = useState<DemoScenario>('normal');
  const [locator, setLocator] = useState<Locator | null>(null);
  const [closing, setClosing] = useState(false);
  const [logoutError, setLogoutError] = useState<unknown>();
  useEffect(() => {
    const controller = new AbortController();
    gateway.profile(controller.signal).then(user => { if (!controller.signal.aborted) { setProfile(user); setBoot(false); } }).catch(error => {
      if (controller.signal.aborted || aborted(error)) return;
      if (!(error instanceof ApiError && error.status === 401)) setBootError(error);
      setBoot(false);
    });
    return () => controller.abort();
  }, [gateway, bootAttempt]);
  const expire = useCallback(() => { setProfile(null); setLocator(null); setExpired(true); }, []);
  const module = modules.find(item => item.id === active)!;
  const context = gateway.demo?.contexts[contextIndex];
  const selected = gateway.demo ? context?.asset_id ? { projectId: context.project_id, assetId: context.asset_id } : null : locator;
  async function logout() {
    setClosing(true); setLogoutError(undefined);
    try { await gateway.logout(); setProfile(null); setLocator(null); setExpired(false); }
    catch (error) { if (error instanceof ApiError && error.status === 401) expire(); else setLogoutError(error); }
    finally { setClosing(false); }
  }
  return <>
    <a className="skip-link" href="#main">Ir al contenido</a>
    {gateway.demo ? <div className="demo-banner"><strong>Demostración</strong><span>Identidad, proyectos y activos sintéticos. Este modo no verifica permisos reales.</span></div> : null}
    {boot ? <main id="main" className="notice" role="status"><h1>Preparando tu espacio</h1><p>Verificando la sesión.</p></main> : bootError ? <main id="main" className="notice error" role="alert"><h1>No se pudo verificar la sesión</h1><p>{message(bootError)}</p><button onClick={() => { setBootError(undefined); setBoot(true); setBootAttempt(n => n + 1); }}>Volver a intentar</button></main> : !profile ? <Login gateway={gateway} expired={expired} onLogin={user => { setProfile(user); setExpired(false); setScenario('normal'); setActive('catalogo'); setContextIndex(0); setLocator(null); }} /> : <div className="app-shell">
      <aside className="sidebar"><Brand variant="inverse"/><nav aria-label="Componentes de la plataforma">{modules.map(item => <button key={item.id} className="nav-item" aria-current={active === item.id ? 'page' : undefined} onClick={() => setActive(item.id)}><span className="module-code">{item.code}</span>{item.label}</button>)}</nav><div className="sidebar-note"><strong>Un contexto, un alcance</strong><p>Las consultas están sujetas a los permisos vigentes de tu cuenta.</p></div></aside>
      <div className="workspace"><header className="topbar"><div><span className="context-label">{context ? context.organization_name : 'Espacio de trabajo'}</span><strong>{context ? context.role_label : profile.display_name}</strong></div><div className="account"><span>{profile.display_name}</span><button className="secondary" disabled={closing} onClick={() => void logout()}>{closing ? 'Cerrando…' : 'Cerrar sesión'}</button></div></header>
        <main id="main" className="content"><div className="page-heading"><div><h1>{module.title}</h1><p>{module.description}</p></div><span className="module-label">{module.code}</span></div>
          {logoutError ? <p className="error-text" role="alert">No se pudo cerrar la sesión. Vuelve a intentarlo.</p> : null}
          {context ? <div className="context-controls"><div><label htmlFor="context">Proyecto de demostración</label><select id="context" value={contextIndex} onChange={event => setContextIndex(Number(event.target.value))}>{gateway.demo!.contexts.map((item, index) => <option key={item.project_id} value={index}>{item.project_name}</option>)}</select></div><div><label htmlFor="scenario">Escenario de demostración</label><select id="scenario" value={scenario} onChange={event => { const next = event.target.value as DemoScenario; gateway.demo!.configure(next); setScenario(next); }}><option value="normal">Consulta disponible</option><option value="empty">Diccionario vacío</option><option value="denied">Acceso denegado</option><option value="unavailable">Servicio no disponible</option><option value="expired">Sesión expirada</option></select></div></div> : null}
          {active !== 'catalogo' ? <section className="notice"><h2>Vista aún no integrada</h2><p>Este espacio está reservado para el componente de {module.label.toLowerCase()}.</p><button className="secondary" onClick={() => setActive('catalogo')}>Volver al catálogo</button></section> : <>
            {!gateway.demo ? <section aria-labelledby="open-heading" className="open-resource"><h2 id="open-heading">Abrir un activo autorizado</h2><p>Introduce el proyecto y el activo que necesitas consultar. El acceso se comprobará en cada solicitud.</p><ResourceForm onOpen={setLocator} /></section> : null}
            {selected ? <Catalogo key={`${profile.id}:${selected.projectId}:${selected.assetId}:${scenario}`} gateway={gateway} projectId={selected.projectId} assetId={selected.assetId} onExpired={expire} /> : <section className="notice" role="status"><h2>{gateway.demo ? 'Este proyecto aún no tiene activos' : 'Selecciona un activo para comenzar'}</h2><p>{gateway.demo ? 'Selecciona Catálogo comercial para explorar el diccionario de demostración.' : 'El diccionario aparecerá aquí cuando abras un activo disponible para tu cuenta.'}</p></section>}
          </>}
        </main><footer className="footer">DataAnalitics Enterprise <span>Catálogo, calidad, gobierno y analítica</span></footer>
      </div>
    </div>}
  </>;
}
