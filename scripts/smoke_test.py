#!/usr/bin/env python3
"""Verify readiness retries against a real local HTTP server."""
import os
from pathlib import Path
import socket
import struct
import subprocess
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Thread
import unittest


class SmokeTest(unittest.TestCase):
    def run_smoke(self, replies):
        calls = {}

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                index = calls.get(self.path, 0)
                calls[self.path] = index + 1
                sequence = replies[self.path]
                reply = sequence[min(index, len(sequence) - 1)]
                if reply is None:
                    self.connection.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER, struct.pack("ii", 1, 0))
                    self.close_connection = True
                    self.connection.close()
                    return
                status, body = reply
                self.send_response(status)
                self.end_headers()
                self.wfile.write(body.encode())

            def log_message(self, *_):
                pass

        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        thread = Thread(target=server.serve_forever, daemon=True)
        thread.start()
        base = f"http://127.0.0.1:{server.server_port}"
        env = {**os.environ, "API_URL": base, "WEB_URL": base,
               "SMOKE_ATTEMPTS": "4", "SMOKE_RETRY_DELAY": "0", "SMOKE_REQUEST_TIMEOUT": "1"}
        try:
            result = subprocess.run(["sh", str(Path(__file__).with_name("smoke.sh"))],
                                    env=env, capture_output=True, text=True, timeout=10)
            return result, calls
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def test_reset_http_failure_and_pending_seed_recover(self):
        result, calls = self.run_smoke({
            "/actuator/health/readiness": [None, (503, '{"status":"DOWN"}'), (200, '{"status":"UP"}')],
            "/": [(503, "starting"), (200, "ready")],
            "/api/programs": [(200, "[]"), (200, '[{"name":"Yoga Everyday"}]')],
        })
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Smoke checks passed", result.stdout)
        self.assertEqual(calls["/actuator/health/readiness"], 3)
        self.assertEqual(calls["/api/programs"], 2)

    def test_http_success_with_down_readiness_still_fails(self):
        result, calls = self.run_smoke({"/actuator/health/readiness": [(200, '{"status":"DOWN"}')]})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("API did not become ready after 4 attempts", result.stderr)
        self.assertEqual(calls["/actuator/health/readiness"], 4)

    def test_missing_seed_data_still_fails(self):
        result, calls = self.run_smoke({
            "/actuator/health/readiness": [(200, '{"status":"UP"}')],
            "/": [(200, "ready")],
            "/api/programs": [(200, "[]")],
        })
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Seeded programs did not become ready", result.stderr)
        self.assertEqual(calls["/api/programs"], 4)


if __name__ == "__main__":
    unittest.main()
