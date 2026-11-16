#!/usr/bin/env python3
"""Mock HTTP de TC04 Contexto (solo biblioteca estándar).

Implementa docs/contratos/tc04/contexto-http.openapi.json con datos sintéticos en memoria:
los cambios duran lo que dura el proceso.

Uso:
    python tools/mock_tc04.py [--port 8095]

Cabecera X-Mock-Scenario: normal (por defecto), unauthenticated, denied, unavailable.
Éxito: envoltura {data, meta}. Errores: application/problem+json (code, request_id).
Este mock NO autentica, NO aplica RBAC/CSRF y solo escucha en 127.0.0.1.
"""
import argparse
import json
import re
import sys
import threading
import uuid
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

HOST = "127.0.0.1"
PROBLEM_JSON = "application/problem+json"
UUID = r"([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"
BASE = "/api/v1/admin"
SCENARIOS = {
    "unauthenticated": (401, "AUTHENTICATION_REQUIRED", "Autenticación requerida", "Inicia sesión para continuar"),
    "denied": (403, "ACCESS_DENIED", "Acceso denegado", "La operación no está autorizada"),
    "unavailable": (503, "CONTEXT_UNAVAILABLE", "Servicio no disponible", "No se pudo completar la operación"),
}
TITLES = {400: "Solicitud inválida", 404: "Recurso no encontrado", 405: "Método no permitido", 409: "Conflicto"}

ADMIN_ID = "8d0f6c2a-41b7-5c1e-9a3d-7e2b5f4c6a10"
ORG_ID = "5b6c1d2e-7f80-5a91-b2c3-d4e5f6a7b8c9"
PROJECT_ID = "2f66e241-dd8b-508f-b5e1-85db372e9f2c"
SEED_TIME = "2026-11-01T09:00:00Z"


class Problem(Exception):
    def __init__(self, status, code, detail):
        super().__init__(detail)
        self.status, self.code, self.detail = status, code, detail


class Store:
    """Estado sintético en memoria, protegido por un candado."""

    def __init__(self):
        self.lock = threading.Lock()
        self.organizations = {ORG_ID: {
            "id": ORG_ID, "name": "Organización demo (sintética)", "status": "active",
            "created_at": SEED_TIME, "updated_at": SEED_TIME}}
        self.projects = {PROJECT_ID: {
            "id": PROJECT_ID, "organization_id": ORG_ID, "name": "Proyecto demo (sintético)", "status": "active",
            "created_at": SEED_TIME, "updated_at": SEED_TIME}}
        self.memberships = {(ADMIN_ID, PROJECT_ID): {
            "id": "c1d2e3f4-a5b6-5c7d-8e9f-0a1b2c3d4e5f", "user_id": ADMIN_ID, "project_id": PROJECT_ID,
            "organization_id": ORG_ID, "status": "active", "assigned_by": ADMIN_ID, "assigned_at": SEED_TIME}}

    def organization(self, org_id):
        return found(self.organizations.get(org_id))

    def project(self, project_id):
        return found(self.projects.get(project_id))

    def create_organization(self, body):
        org = {"id": str(uuid.uuid4()), "name": valid_name(body), "status": "active",
               "created_at": now(), "updated_at": now()}
        self.organizations[org["id"]] = org
        return org

    def create_project(self, org_id, body):
        org = self.organization(org_id)
        name = valid_name(body)
        if org["status"] != "active":
            raise Problem(409, "CONFLICT", "La organización no está activa")
        self.unique_project_name(org_id, name, None)
        project = {"id": str(uuid.uuid4()), "organization_id": org_id, "name": name, "status": "active",
                   "created_at": now(), "updated_at": now()}
        self.projects[project["id"]] = project
        return project

    def rename_project(self, project_id, body):
        project = self.project(project_id)
        name = valid_name(body)
        self.unique_project_name(project["organization_id"], name, project_id)
        return touch(project, name=name)

    def unique_project_name(self, org_id, name, except_id):
        if any(p["organization_id"] == org_id and p["name"] == name and p["id"] != except_id
               for p in self.projects.values()):
            raise Problem(409, "CONFLICT", "Ya existe un proyecto con ese nombre en la organización")

    def activate_project(self, project_id):
        project = self.project(project_id)
        if self.organizations[project["organization_id"]]["status"] != "active":
            raise Problem(409, "CONFLICT", "La organización no está activa")
        return touch(project, status="active")

    def assign(self, project_id, body):
        project = self.project(project_id)
        user_id = body.get("user_id") if isinstance(body, dict) and set(body) == {"user_id"} else None
        if not isinstance(user_id, str) or not re.fullmatch(UUID, user_id):
            raise Problem(400, "INVALID_REQUEST", "user_id debe ser un UUID")
        org = self.organizations[project["organization_id"]]
        if project["status"] != "active" or org["status"] != "active":
            raise Problem(409, "CONFLICT", "El proyecto o su organización no están activos")
        membership = self.memberships.setdefault((user_id, project_id), {
            "id": str(uuid.uuid4()), "user_id": user_id, "project_id": project_id,
            "organization_id": org["id"], "status": "active", "assigned_by": ADMIN_ID, "assigned_at": now()})
        membership["status"] = "active"
        return membership

    def revoke(self, project_id, user_id):
        self.project(project_id)
        membership = found(self.memberships.get((user_id, project_id)))
        membership["status"] = "inactive"
        return membership


def now():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def found(entity):
    if entity is None:
        raise Problem(404, "NOT_FOUND", "El recurso no existe")
    return entity


def touch(entity, **changes):
    entity.update(changes, updated_at=now())
    return entity


def valid_name(body):
    name = body.get("name") if isinstance(body, dict) and set(body) == {"name"} else None
    if not isinstance(name, str) or not 1 <= len(name.strip()) <= 200:
        raise Problem(400, "INVALID_REQUEST", "name debe tener entre 1 y 200 caracteres")
    return name.strip()


def page(items, query):
    try:
        limit = int(query.get("limit", ["50"])[0])
        offset = int(query.get("offset", ["0"])[0])
    except ValueError:
        raise Problem(400, "INVALID_REQUEST", "limit y offset deben ser enteros") from None
    if not 1 <= limit <= 100 or offset < 0:
        raise Problem(400, "INVALID_REQUEST", "limit debe estar entre 1 y 100 y offset no puede ser negativo")
    return items[offset:offset + limit]


def routes(s):
    """(método, patrón) -> función(ids, body, query) que devuelve (status, data)."""
    org, proj = f"{BASE}/organizations/{UUID}", f"{BASE}/projects/{UUID}"
    return [
        ("GET", f"{BASE}/organizations", lambda i, b, q: (200, page(list(s.organizations.values()), q))),
        ("POST", f"{BASE}/organizations", lambda i, b, q: (201, s.create_organization(b))),
        ("GET", org, lambda i, b, q: (200, s.organization(i[0]))),
        ("PATCH", org, lambda i, b, q: (200, touch(s.organization(i[0]), name=valid_name(b)))),
        ("POST", f"{org}/activate", lambda i, b, q: (200, touch(s.organization(i[0]), status="active"))),
        ("POST", f"{org}/deactivate", lambda i, b, q: (200, touch(s.organization(i[0]), status="inactive"))),
        ("GET", f"{org}/projects", lambda i, b, q: (200, page(
            [p for p in s.projects.values() if p["organization_id"] == s.organization(i[0])["id"]], q))),
        ("POST", f"{org}/projects", lambda i, b, q: (201, s.create_project(i[0], b))),
        ("GET", proj, lambda i, b, q: (200, s.project(i[0]))),
        ("PATCH", proj, lambda i, b, q: (200, s.rename_project(i[0], b))),
        ("POST", f"{proj}/activate", lambda i, b, q: (200, s.activate_project(i[0]))),
        ("POST", f"{proj}/deactivate", lambda i, b, q: (200, touch(s.project(i[0]), status="inactive"))),
        ("GET", f"{proj}/memberships", lambda i, b, q: (200, page(
            [m for m in s.memberships.values() if m["project_id"] == s.project(i[0])["id"]], q))),
        ("POST", f"{proj}/memberships", lambda i, b, q: (200, s.assign(i[0], b))),
        ("DELETE", f"{proj}/memberships/{UUID}", lambda i, b, q: (200, s.revoke(i[0], i[1]))),
    ]


class Handler(BaseHTTPRequestHandler):
    server_version = "MockTC04/0.1"
    store = Store()
    table = [(m, re.compile(f"^{p}$"), f) for m, p, f in routes(store)]

    def _send(self, status, body, content_type="application/json"):
        payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", f"{content_type}; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("X-Mock-Response", "true")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(payload)

    def _problem(self, status, code, detail, title=None):
        self._send(status, {
            "type": "about:blank", "title": title or TITLES.get(status, "Error del mock"), "status": status,
            "detail": detail, "instance": urlsplit(self.path).path, "code": code,
            "request_id": str(uuid.uuid4())}, PROBLEM_JSON)

    def _body(self):
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(min(length, 1_000_000)) if length else b""
        if not raw:
            return None
        try:
            return json.loads(raw)
        except ValueError:
            raise Problem(400, "INVALID_REQUEST", "El cuerpo no es JSON válido") from None

    def _handle(self):
        url = urlsplit(self.path)
        try:
            body = self._body()
            scenario = (self.headers.get("X-Mock-Scenario") or "normal").strip().lower()
            if scenario in SCENARIOS:
                status, code, title, detail = SCENARIOS[scenario]
                return self._problem(status, code, detail, title)
            if scenario != "normal":
                raise Problem(400, "INVALID_REQUEST", "X-Mock-Scenario inválido")
            path_known = False
            for method, pattern, action in self.table:
                match = pattern.match(url.path)
                if not match:
                    continue
                path_known = True
                if method == self.command:
                    with self.store.lock:
                        status, data = action([g.lower() for g in match.groups()], body, parse_qs(url.query))
                    return self._send(status, {"data": data, "meta": {"request_id": str(uuid.uuid4())}})
            if path_known:
                raise Problem(405, "INVALID_REQUEST", "Método no permitido para esta ruta")
            raise Problem(404, "NOT_FOUND", "Ruta no definida en el contrato TC04")
        except Problem as problem:
            self._problem(problem.status, problem.code, problem.detail)

    do_GET = do_POST = do_PATCH = do_DELETE = lambda self: self._handle()

    def log_message(self, fmt, *args):
        sys.stderr.write("[mock_tc04] %s - %s\n" % (self.address_string(), fmt % args))


def main():
    parser = argparse.ArgumentParser(description="Mock HTTP de TC04 Contexto")
    parser.add_argument("--port", type=int, default=8095)
    args = parser.parse_args()
    httpd = ThreadingHTTPServer((HOST, args.port), Handler)
    print(f"Mock TC04 en http://{HOST}:{args.port} (Ctrl+C para salir)")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        httpd.server_close()


if __name__ == "__main__":
    main()
