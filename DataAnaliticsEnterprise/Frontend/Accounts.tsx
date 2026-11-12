import {useEffect, useState} from 'react';
import {api, label} from './api';
import {Dialog, ErrorMessage, Form, Loading, Button, Input, Select, Table} from './ui';

type Account = { id: string; active: boolean; revision: number };
type Assignment = { id: string; user_id: string; role_name: string; asset_scope_id: string | null };
const roleNames = ['SYSTEM_ADMIN', 'CATALOG_MANAGER', 'QUALITY_MANAGER', 'GOVERNANCE_MANAGER', 'BUSINESS_ANALYST'];

export function Accounts({project}: { project: string }) {
    const [users, setUsers] = useState<Account[]>([]);
    const [roles, setRoles] = useState<Assignment[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<unknown>();
    const [refresh, setRefresh] = useState(0);
    const [create, setCreate] = useState(false);
    const [assign, setAssign] = useState<Account>();
    const [confirm, setConfirm] = useState<{ account?: Account; role?: Assignment }>();
    useEffect(() => {
        const abort = new AbortController();
        setLoading(true);
        Promise.all([api<Account[]>(`/users?project_id=${project}`, {signal: abort.signal}), api<Assignment[]>(`/projects/${project}/roles`, {signal: abort.signal})]).then(([u, r]) => {
            setUsers(u.data);
            setRoles(r.data);
            setError(undefined);
        }).catch(e => {
            if (!abort.signal.aborted) setError(e);
        }).finally(() => {
            if (!abort.signal.aborted) setLoading(false);
        });
        return () => abort.abort();
    }, [project, refresh]);
    return <>
        <div className="toolbar surface-card p-5 flex flex-row flex-wrap items-center justify-between gap-4 mb-4"><p>Administrar cuentas no concede acceso automático a los datos.</p>
            <Button variant="primary" onClick={() => setCreate(true)}>Crear cuenta</Button>
        </div>
        <ErrorMessage error={error}/>{loading ? <Loading/> : <div className="table-scroll max-w-full overflow-x-auto">
        <Table>
            <thead>
            <tr>
                <th>Cuenta</th>
                <th>Estado</th>
                <th>Roles</th>
                <th>Acciones</th>
            </tr>
            </thead>
            <tbody>{users.map(u => <tr key={u.id}>
                <td className="identifier">{u.id}</td>
                <td>{u.active ? 'Activa' : 'Inactiva'}</td>
                <td>{roles.filter(r => r.user_id === u.id).map(r => <div key={r.id}
                                                                         className="role-row flex flex-wrap items-center gap-2 py-2">{label(r.role_name)} {r.asset_scope_id ?
                    <small>Activo: {r.asset_scope_id}</small> : null}
                    <Button onClick={() => setConfirm({role: r})} aria-label={`Revocar ${label(r.role_name)}`}>Revocar
                    </Button>
                </div>)}</td>
                <td><div className="actions flex flex-wrap gap-2">
                    <Button onClick={() => setAssign(u)}>Asignar rol</Button>
                    <Button onClick={() => setConfirm({account: u})}>{u.active ? 'Desactivar' : 'Activar'}</Button>
                </div></td>
            </tr>)}</tbody>
        </Table>
    </div>}
        {create || assign ? <Dialog title={create ? 'Crear cuenta' : 'Asignar rol'} close={() => {
            setCreate(false);
            setAssign(undefined);
        }}><Form noValidate cancel={() => {
            setCreate(false);
            setAssign(undefined);
        }} submit={async data => {
            const body = {role: data.get('role'), asset_scope_id: data.get('scope') || null};
            if (create) await api(`/users?project_id=${project}`, {
                method: 'POST',
                body: JSON.stringify({...body, email: data.get('email'), password: data.get('password')})
            }); else await api(`/projects/${project}/roles`, {
                method: 'POST',
                body: JSON.stringify({...body, user_id: assign!.id})
            });
            setCreate(false);
            setAssign(undefined);
            setRefresh(r => r + 1);
        }}>
            {create ? <><label className="field-label flex flex-col gap-2 mb-4">Correo<Input name="email" type="email" required maxLength={254}/></label><label className="field-label flex flex-col gap-2 mb-4">Contraseña
                inicial<Input name="password" type="password" minLength={12} maxLength={128} autoComplete="new-password"
                              required/></label></> : null}<label className="field-label flex flex-col gap-2 mb-4">Rol<Select name="role">{roleNames.map(r => <option
            key={r} value={r}>{label(r)}</option>)}</Select></label><label className="field-label flex flex-col gap-2 mb-4">Ámbito de activo (UUID, opcional)<Input
            name="scope" placeholder="Vacío: proyecto completo"
            pattern="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"/></label>
        </Form></Dialog> : null}
        {confirm ? <Dialog title={confirm.role ? 'Revocar permiso' : 'Cambiar estado de cuenta'}
                           close={() => setConfirm(undefined)}>
            <p>{confirm.role ? `Se revocará el rol ${label(confirm.role.role_name)} de la cuenta ${confirm.role.user_id}.` : `Se ${confirm.account!.active ? 'desactivará' : 'activará'} la cuenta ${confirm.account!.id}.`} Sus
                sesiones anteriores dejarán de ser válidas.</p><Form noValidate cancel={() => setConfirm(undefined)}
                                                                     label="Confirmar cambio" submit={async () => {
            if (confirm.role) await api(`/projects/${project}/roles/${confirm.role.id}`, {method: 'DELETE'}); else await api(`/users/${confirm.account!.id}?project_id=${project}`, {
                method: 'PATCH',
                headers: {'If-Match': `"${confirm.account!.revision}"`},
                body: JSON.stringify({active: !confirm.account!.active})
            });
            setConfirm(undefined);
            setRefresh(r => r + 1);
        }}/></Dialog> : null}
    </>;
}

