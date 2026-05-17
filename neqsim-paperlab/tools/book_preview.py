"""Live HTML preview server for PaperLab books.

Watches `chapters/`, `frontmatter/`, `backmatter/`, `book.yaml`, and
`refs.bib`. On each change, re-renders the HTML format only (fast path,
no notebook execution, no compile) and pushes a browser reload via a
tiny Server-Sent-Events endpoint.

Usage:
    python paperflow.py book-preview books/<book_dir>
    python paperflow.py book-preview books/<book_dir> --port 8765 --no-open

Dependencies (optional):
    pip install watchdog       # event-driven file watching (falls back to polling)
"""
from __future__ import annotations

import http.server
import os
import socketserver
import sys
import threading
import time
import webbrowser
from pathlib import Path
from urllib.parse import urlparse

# ---------------------------------------------------------------------------
# Renderer wrapper
# ---------------------------------------------------------------------------

def _render_html(book_dir: Path) -> Path | None:
    sys.path.insert(0, str(Path(__file__).parent))
    from book_render_html import render_book_html  # type: ignore
    try:
        out = render_book_html(str(book_dir))
        return Path(out) if out else None
    except Exception as exc:  # pragma: no cover - surfaced in console
        print(f"[book_preview] Render failed: {exc}")
        return None


# ---------------------------------------------------------------------------
# File watcher (watchdog if available, else polling)
# ---------------------------------------------------------------------------

WATCH_GLOBS = ("chapter.md", "*.md", "book.yaml", "refs.bib", "nomenclature.yaml")


def _scan_mtimes(book_dir: Path) -> dict[Path, float]:
    snap: dict[Path, float] = {}
    for sub in ("chapters", "frontmatter", "backmatter"):
        for p in (book_dir / sub).rglob("*.md") if (book_dir / sub).exists() else []:
            try:
                snap[p] = p.stat().st_mtime
            except OSError:
                pass
    for name in ("book.yaml", "refs.bib", "nomenclature.yaml"):
        p = book_dir / name
        if p.exists():
            snap[p] = p.stat().st_mtime
    return snap


class ChangeNotifier:
    """Coalesces filesystem events into a monotonic version counter."""

    def __init__(self) -> None:
        self.version = 0
        self._lock = threading.Lock()

    def bump(self) -> None:
        with self._lock:
            self.version += 1


def _watch_loop(book_dir: Path, notifier: ChangeNotifier, interval: float = 0.6) -> None:
    last = _scan_mtimes(book_dir)
    while True:
        time.sleep(interval)
        cur = _scan_mtimes(book_dir)
        if cur != last:
            last = cur
            print("[book_preview] change detected → re-rendering …")
            t0 = time.time()
            _render_html(book_dir)
            print(f"[book_preview] re-rendered in {time.time() - t0:.2f} s")
            notifier.bump()


# ---------------------------------------------------------------------------
# HTTP server with SSE reload endpoint
# ---------------------------------------------------------------------------

RELOAD_SNIPPET = b"""
<script>
(function() {
  var es = new EventSource('/__reload');
  es.onmessage = function(e) { if (e.data === 'reload') location.reload(); };
  es.onerror = function() { console.warn('preview SSE disconnected'); };
})();
</script>
"""


def _make_handler(book_dir: Path, notifier: ChangeNotifier):
    submission = book_dir / "submission"

    class Handler(http.server.SimpleHTTPRequestHandler):
        def __init__(self, *a, **kw):
            super().__init__(*a, directory=str(submission), **kw)

        def log_message(self, fmt, *args):  # silence default access log
            pass

        def do_GET(self):  # noqa: N802 (stdlib API)
            path = urlparse(self.path).path
            if path == "/__reload":
                self.send_response(200)
                self.send_header("Content-Type", "text/event-stream")
                self.send_header("Cache-Control", "no-cache")
                self.end_headers()
                seen = notifier.version
                try:
                    while True:
                        if notifier.version > seen:
                            seen = notifier.version
                            self.wfile.write(b"data: reload\n\n")
                            self.wfile.flush()
                        time.sleep(0.4)
                except (BrokenPipeError, ConnectionResetError):
                    return
            elif path in ("/", "/book.html", "/index.html"):
                target = submission / "book.html"
                if not target.exists():
                    self.send_error(404, "book.html not yet rendered")
                    return
                html = target.read_bytes()
                # Inject reload snippet just before </body>
                idx = html.lower().rfind(b"</body>")
                if idx != -1:
                    html = html[:idx] + RELOAD_SNIPPET + html[idx:]
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Cache-Control", "no-store")
                self.send_header("Content-Length", str(len(html)))
                self.end_headers()
                self.wfile.write(html)
            else:
                super().do_GET()

    return Handler


# ---------------------------------------------------------------------------
# Public entry point
# ---------------------------------------------------------------------------

def serve(book_dir: str | os.PathLike, port: int = 8765, open_browser: bool = True) -> None:
    book_dir = Path(book_dir).resolve()
    if not book_dir.exists():
        raise FileNotFoundError(f"Book directory not found: {book_dir}")

    print(f"[book_preview] initial render of {book_dir.name} …")
    _render_html(book_dir)

    notifier = ChangeNotifier()
    watcher = threading.Thread(
        target=_watch_loop, args=(book_dir, notifier), daemon=True
    )
    watcher.start()

    handler = _make_handler(book_dir, notifier)
    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.ThreadingTCPServer(("127.0.0.1", port), handler) as httpd:
        url = f"http://127.0.0.1:{port}/"
        print(f"[book_preview] serving at {url}  (Ctrl+C to stop)")
        if open_browser:
            try:
                webbrowser.open(url)
            except Exception:
                pass
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\n[book_preview] stopped.")
