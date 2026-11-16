#!/usr/bin/env python3
"""Mock HTTP de M4 Analítica (solo biblioteca estándar).

Sirve los endpoints de docs/contratos/m4/analitica-http.openapi.json con los
datos sintéticos de docs/contratos/m4/analitica.examples.json.

Uso:
    python tools/mock_m4.py [--port 8094]

Cabecera X-Mock-Scenario: normal (por defecto), empty, error, partial, denied.
Si el endpoint no define el escenario pedido, responde 400.
Éxito: envoltura {data, meta}. Errores: application/problem+json (code, request_id).
Este mock NO autentica, NO aplica RBAC/CSRF y NO consulta fuentes reales.
Solo escucha en 127.0.0.1.
"""
import argparse
import json
import re
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HOST = "127.0.0.1"
EXAMPLES = Path(__file__).resolve().parent.parent / "docs" / "contratos" / "m4" / "analitica.examples.json"
SCENARIOS = ("normal", "empty", "error", "partial", "denied")
PROBLEM_JSON = "application/problem+json"
UUID = r"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"

# (método, regex de ruta) -> clave en el archivo de ejemplos
ROUTES = [
    ("GET", re.compile(r"^/api/v1/analitica/ping$"), "ping"),
    ("POST", re.compile(rf"^/api/v1/projects/{UUID}/analytics/kpis/evaluate$"), "kpis_evaluate"),
    ("GET", re.compile(rf"^/api/v1/projects/{UUID}/analytics/dashboards/summary$"), "dashboards_summary"),
    ("POST", re.compile(rf"^/api/v1/projects/{UUID}/analytics/reports/generate$"), "reports_generate"),
]


def load_examples():
    with EXAMPLES.open(encoding="utf-8") as fh:
        return json.load(fh)


class Handler(BaseHTTPRequestHandler):
    server_version = "MockM4/0.1"
    examples = {}

    def _send(self, status, body, content_type="application/json"):
        if content_type == PROBLEM_JSON:
            body = {**body, "instance": self._path()}
        payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", f"{content_type}; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("X-Mock-Response", "true")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(payload)

    def _error(self, status, code, detail):
        """Errores propios del mock, en el mismo formato problem+json del backend."""
        self._send(status, {"type": "about:blank", "title": "Error del mock", "status": status,
                            "detail": detail, "instance": "", "code": code, "request_id": "mock"},
                   PROBLEM_JSON)

    def _path(self):
        return self.path.split("?", 1)[0]

    def _handle(self, method):
        path = self._path()
        # Consumir el cuerpo para no dejar el socket a medias
        length = int(self.headers.get("Content-Length") or 0)
        if length:
            self.rfile.read(min(length, 1_000_000))

        matched_path = False
        for route_method, pattern, key in ROUTES:
            if pattern.match(path):
                matched_path = True
                if route_method == method:
                    return self._respond(key)
        if matched_path:
            return self._error(405, "INVALID_REQUEST", "Método no permitido para esta ruta.")
        return self._error(404, "NOT_FOUND", "Ruta no definida en el contrato M4.")

    def _respond(self, key):
        scenario = (self.headers.get("X-Mock-Scenario") or "normal").strip().lower()
        if scenario not in SCENARIOS:
            return self._error(400, "INVALID_REQUEST",
                               f"X-Mock-Scenario inválido. Valores: {', '.join(SCENARIOS)}.")
        by_scenario = self.examples.get(key, {})
        example = by_scenario.get(scenario)
        if example is None:
            return self._error(400, "INVALID_REQUEST",
                               f"El escenario '{scenario}' no está definido para '{key}'.")
        self._send(example["status"], example["body"], example.get("content_type", "application/json"))

    def do_GET(self):
        self._handle("GET")

    def do_POST(self):
        self._handle("POST")

    def log_message(self, fmt, *args):
        sys.stderr.write("[mock_m4] %s - %s\n" % (self.address_string(), fmt % args))


def main():
    parser = argparse.ArgumentParser(description="Mock HTTP de M4 Analítica")
    parser.add_argument("--port", type=int, default=8094)
    args = parser.parse_args()
    Handler.examples = load_examples()
    httpd = ThreadingHTTPServer((HOST, args.port), Handler)
    print(f"Mock M4 en http://{HOST}:{args.port} (Ctrl+C para salir)")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        httpd.server_close()


if __name__ == "__main__":
    main()
