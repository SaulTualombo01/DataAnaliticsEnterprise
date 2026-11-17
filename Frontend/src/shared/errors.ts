export const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
export class ApiError extends Error {
  constructor(public status: number, public code = '', public requestId?: string) {
    super(status === 401 ? 'La sesión terminó. Inicia sesión para continuar.'
      : code === 'CSRF_INVALID' ? 'La verificación de seguridad venció. Revisa el estado y vuelve a confirmar.'
      : status === 403 ? 'Tu cuenta no tiene permiso para esta operación.'
      : status === 404 ? 'El elemento no está disponible o perdiste acceso a este contexto.'
      : status === 409 ? 'Los datos cambiaron o la operación no es válida en su estado actual. Actualiza la información antes de volver a confirmar.'
      : status === 422 && code !== 'INVALID_INVITATION' ? 'Revisa los campos y los identificadores indicados.'
      : code === 'DELIVERY_UNAVAILABLE' ? 'La entrega de invitaciones no está habilitada. Solicita su configuración al administrador.'
      : code === 'INVALID_INVITATION' ? 'La invitación venció, fue revocada o ya se utilizó.'
      : status === 0 ? 'No se pudo confirmar la operación. Comprueba el estado antes de volver a enviarla.'
      : status === 429 ? 'Hay demasiados intentos. Espera antes de volver a intentarlo.'
      : 'No se pudo completar la solicitud. Vuelve a intentarlo.');
  }
}
export function message(error: unknown): string { return error instanceof ApiError ? error.message : 'No se pudo conectar. Comprueba la conexión y vuelve a intentarlo.'; }
export const aborted = (error: unknown) => error instanceof DOMException && error.name === 'AbortError';

