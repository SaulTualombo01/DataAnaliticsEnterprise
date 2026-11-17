import { useState } from 'react';
import type { FormEvent } from 'react';
import type { Gateway, Profile } from '../shared/contracts';
import { ApiError } from '../shared/errors';
import { ErrorNote, Field, useAction } from './ui';
import { Brand } from '../shared/Brand';

export function Auth({gateway,logged,expired}:{gateway:Gateway;logged(profile:Profile):void;expired:boolean}) {
  const [mode,setMode]=useState<'login'|'activate'>('login');
  const [activated,setActivated]=useState(false);
  const [invalid,setInvalid]=useState('');
  const action=useAction();
  function submit(e:FormEvent<HTMLFormElement>) {
    e.preventDefault(); const form=e.currentTarget; const data=new FormData(form);
    const password=String(data.get('password')||'');
    const confirmation=String(data.get('confirmation')||'');
    setInvalid('');
    for(const name of ['password','confirmation']) { const input=form.elements.namedItem(name); if(input instanceof HTMLInputElement) input.value=''; }
    if(mode==='activate') {
      if(Array.from(password).length<15 || new TextEncoder().encode(password).length>72) { setInvalid('Usa una contraseña de al menos 15 caracteres y hasta 72 bytes UTF-8.'); return; }
      if(password!==confirmation) { setInvalid('Las contraseñas no coinciden. Escríbelas otra vez.'); return; }
      const token=String(data.get('activation_token')||'').trim();
      if(!/^[A-Za-z0-9_-]{43}$/.test(token)) { setInvalid('Revisa el código de invitación completo.'); return; }
      if(!gateway.platform) { setInvalid('La activación no está disponible.'); return; }
      void action.run(s=>gateway.platform!.activate(token,password,s),()=>{ form.reset(); setActivated(true); setMode('login'); });
    } else void action.run(()=>gateway.login(String(data.get('email')||'').trim(),password),logged);
  }
  return <main id="main" className="login-page"><section className="login-panel"><Brand className="brand-login"/><h1>{mode==='login'?'Tu espacio de datos':'Activa tu cuenta'}</h1><p>{mode==='login'?'Accede a tus proyectos y administra el trabajo de tu empresa.':'Introduce el código que recibiste y elige tu contraseña.'}</p>
    {expired?<p role="status">La sesión terminó. Vuelve a entrar para continuar.</p>:null}
    {activated?<p className="success-note" role="status">Cuenta activada. Ya puedes iniciar sesión con tu correo y contraseña.</p>:null}
    <form key={mode} onSubmit={submit} aria-busy={action.pending}><fieldset disabled={action.pending}>
      {mode==='login'?<Field label="Correo electrónico" name="email" type="email" autoComplete="username" required maxLength={254}/>:<Field label="Código de invitación" name="activation_token" type="password" autoComplete="off" required minLength={43} maxLength={43} spellCheck={false}/>}
      <Field label={mode==='login'?'Contraseña':'Nueva contraseña'} name="password" type="password" autoComplete={mode==='login'?'current-password':'new-password'} required hint={mode==='activate'?'Al menos 15 caracteres; máximo 72 bytes UTF-8.':undefined}/>
      {mode==='activate'?<Field label="Repite la nueva contraseña" name="confirmation" type="password" autoComplete="new-password" required/>:null}
    </fieldset>{invalid?<p className="error-text" role="alert">{invalid}</p>:null}
    {action.error ? action.error instanceof ApiError && action.error.status===401 && mode==='login'?<p className="error-text" role="alert">No se pudo iniciar sesión. Revisa tus credenciales.</p>:<ErrorNote error={action.error}/>:null}
    <button disabled={action.pending} type="submit">{action.pending?'Verificando…':mode==='login'?'Iniciar sesión':'Activar cuenta'}</button>
    </form>
    {gateway.platform?<button className="link-button" disabled={action.pending} onClick={()=>{setMode(mode==='login'?'activate':'login'); setInvalid('');action.clear();}}>{mode==='login'?'Tengo una invitación':'Volver al inicio de sesión'}</button>:null}
  </section></main>;
}
