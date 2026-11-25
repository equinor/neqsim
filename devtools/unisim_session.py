"""Own one UniSim Design process for a script: start it, guard it, end it.

The UniSim COM server attaches to a running UniSim, so a plain
``Dispatch('UnisimDesign.Application')`` can drive a user's open session and a
later ``Quit()`` closes it with any unsaved work. A modal dialog blocks every COM
call, so an unattended run can hang for hours. ``UniSimSession`` is the shared
frame that ``unisim_reader`` and ``unisim_writer`` use and that add-on skills
can import instead of writing their own:

1. **Own process only.** ``DispatchEx`` plus a before/after process-list diff.
   If no new UniSim process appears, COM attached to a running UniSim: wait for
   a closing one, otherwise refuse. On close, quit and end only the own PID.
2. **Pop-up watchdog.** A thread records and dismisses modal dialogs of the own
   process (OK / Close / Cancel / No, never Yes).
3. **Wall-clock limits.** ``limit(seconds)`` and ``solve(case, cap_s)`` end the
   own process at the limit, so the caller gets ``UniSimTimeout``, not a hang.
4. **Work on a copy.** ``open_copy`` opens a writable temporary copy, closes it
   with ``Close(False)`` and checks the SHA-256 of the source before and after.
5. **Solve status.** ``solve_status`` reports ``IsSolving``, variables flagged
   ``IsInconsistent`` and streams whose values are UniSim's empty marker.

Usage::

    from unisim_session import UniSimSession

    with UniSimSession(visible=False) as session:
        with session.limit(1800, "study"):
            case = session.open_copy(r"C:\\models\\plant.usc")
            seconds, finished = session.solve(case, cap_s=600)
            status = session.solve_status(case)
            session.close_case(case)
    session.report   # pid, sources and hashes, popups, solves, timeouts

Windows only at run time (pywin32); importing the module needs nothing extra.
"""
from __future__ import annotations

import contextlib
import hashlib
import logging
import os
import shutil
import stat
import subprocess
import tempfile
import threading
import time
from pathlib import Path
from typing import Any, Callable, Dict, Iterable, List, Optional, Set, Tuple

logger = logging.getLogger(__name__)

PROG_ID = "UnisimDesign.Application"
IMAGE = "UniSimDesign.exe"
#: HRESULT of E_ACCESSDENIED as pywin32 prints it.
ACCESS_DENIED = -2147024891
#: UniSim writes -32767 for an empty value; anything below this is empty.
EMPTY_BELOW = -30000.0
#: Dialog buttons the watchdog may press, in order of preference.
SAFE_BUTTONS = ("ok", "close", "cancel", "no")
#: Stream variables scanned for IsInconsistent and empty values.
STREAM_FLAGS = ("MassFlow", "Pressure", "Temperature")


class UniSimSessionError(RuntimeError):
    """The session cannot be used safely (no own process, source changed)."""


class UniSimTimeout(UniSimSessionError):
    """A wall-clock limit was reached and the own UniSim process was ended."""


def sha256(path) -> str:
    """Upper-case SHA-256 of a file."""
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest().upper()


def is_access_denied(error: BaseException) -> bool:
    """True when a COM error carries E_ACCESSDENIED."""
    return str(ACCESS_DENIED) in str(error)


def pids_of(image: str = IMAGE) -> Set[int]:
    """PIDs of running processes with this image name."""
    out = subprocess.run(["tasklist", "/FI", f"IMAGENAME eq {image}", "/FO", "CSV", "/NH"],
                         capture_output=True, text=True, check=False).stdout
    return {int(line.split('","')[1]) for line in out.splitlines()
            if line.startswith('"') and '","' in line}


def kill_pid(pid: int) -> None:
    """End one process by PID (never by image name)."""
    subprocess.run(["taskkill", "/F", "/PID", str(pid)], capture_output=True, check=False)


def _dispatch_ex():
    """New UniSim server, late bound (``DispatchEx`` would pick up a gencache wrapper)."""
    import pythoncom
    import win32com.client.dynamic
    unknown = pythoncom.CoCreateInstanceEx(PROG_ID, None, pythoncom.CLSCTX_SERVER, None,
                                           (pythoncom.IID_IDispatch,))[0]
    return win32com.client.dynamic.Dispatch(unknown, PROG_ID)


def start_owned_unisim(dispatch: Callable[[], Any], pids: Callable[[], Iterable[int]],
                       wait_closing_s: float = 60.0, poll_closing_s: float = 2.0,
                       sleep: Callable[[float], None] = time.sleep
                       ) -> Tuple[Any, int, Optional[Dict[str, Any]]]:
    """Start a UniSim process that this script owns.

    Returns ``(app, pid, waited)``. ``waited`` is None, or a record of the wait
    for a UniSim that was still closing (a previous script's process). A UniSim
    that stays open is never driven or quit: ``UniSimSessionError`` is raised.
    """
    before = set(pids())
    app = dispatch()
    new = set(pids()) - before
    if len(new) == 1:
        return app, next(iter(new)), None
    del app  # attached to someone else's UniSim: release it, never quit it
    polls = int(wait_closing_s / poll_closing_s) if poll_closing_s > 0 else 0
    left = set(before)
    for i in range(polls):
        sleep(poll_closing_s)
        left = set(pids())
        if left:
            continue
        app = dispatch()
        new = set(pids())
        if len(new) == 1:
            return app, next(iter(new)), {"pids": sorted(before), "polls": i + 1,
                                          "seconds": round((i + 1) * poll_closing_s, 1)}
        del app
        left = new
        break
    raise UniSimSessionError(
        f"no dedicated UniSim process started (already running: {sorted(left or before)}); "
        f"waited {wait_closing_s:.0f} s for it to close. Close the other UniSim or end the "
        "orphan process: a running UniSim is never driven or quit.")


# ---------------------------------------------------------------------------
# Pop-up watchdog (win32gui only, no COM)
# ---------------------------------------------------------------------------

def _dialogs(pids: Set[int]) -> List[int]:
    import win32gui
    import win32process
    found: List[int] = []

    def visit(hwnd, acc):
        if win32gui.IsWindowVisible(hwnd) and win32gui.GetClassName(hwnd) == "#32770":
            if win32process.GetWindowThreadProcessId(hwnd)[1] in pids:
                acc.append(hwnd)
        return True

    win32gui.EnumWindows(visit, found)
    return found


def dismiss(hwnd: int) -> Dict[str, Any]:
    """Record the text of one dialog and press a safe button (never Yes)."""
    import win32con
    import win32gui
    children: List[int] = []
    win32gui.EnumChildWindows(hwnd, lambda h, acc: acc.append(h) or True, children)
    texts, buttons = [], []
    for child in children:
        cls, txt = win32gui.GetClassName(child), win32gui.GetWindowText(child).strip()
        if cls == "Button":
            buttons.append((txt.replace("&", "").lower(), child))
        elif txt:
            texts.append(txt)
    event = {"time": time.strftime("%Y-%m-%d %H:%M:%S"), "title": win32gui.GetWindowText(hwnd),
             "text": " | ".join(texts), "buttons": [b for b, _ in buttons], "action": None}
    for name in SAFE_BUTTONS:
        hit = next((c for b, c in buttons if b == name), None)
        if hit:
            win32gui.PostMessage(hit, win32con.BM_CLICK, 0, 0)
            event["action"] = f"pressed {name}"
            return event
    win32gui.PostMessage(hwnd, win32con.WM_CLOSE, 0, 0)
    event["action"] = "WM_CLOSE"
    return event


class PopupWatchdog:
    """Background thread that records and dismisses dialogs of the given PIDs."""

    def __init__(self, pids: Iterable[int], interval: float = 0.5,
                 on_event: Optional[Callable[[Dict[str, Any]], None]] = None):
        self.pids, self.interval, self.on_event = set(pids), interval, on_event
        self.events: List[Dict[str, Any]] = []
        self._stop, self._seen = threading.Event(), {}
        self._thread = threading.Thread(target=self._run, daemon=True)

    def _run(self):
        while not self._stop.is_set():
            try:
                for hwnd in _dialogs(self.pids):
                    if time.monotonic() - self._seen.get(hwnd, -1e9) > 2.0:
                        self._seen[hwnd] = time.monotonic()
                        self.events.append(dismiss(hwnd))
                        logger.warning("UniSim pop-up dismissed: %s", self.events[-1])
                        if self.on_event:
                            self.on_event(self.events[-1])
            except Exception as exc:  # noqa: BLE001 - a window can vanish mid-scan
                self.events.append({"time": time.strftime("%H:%M:%S"), "error": repr(exc)[:200]})
            self._stop.wait(self.interval)

    def start(self) -> "PopupWatchdog":
        self._thread.start()
        return self

    def stop(self) -> None:
        self._stop.set()
        self._thread.join(timeout=5)

    def mark(self) -> int:
        return len(self.events)

    def since(self, mark: int = 0) -> List[Dict[str, Any]]:
        """Dialog events (not scan errors) recorded after ``mark``."""
        return [e for e in self.events[mark:] if "text" in e]


# ---------------------------------------------------------------------------
# Solve-status helpers (plain COM reads, usable on any case object)
# ---------------------------------------------------------------------------

def _safe(read: Callable[[], Any]) -> Any:
    try:
        return read()
    except Exception:  # noqa: BLE001 - COM raises many types
        return None


def _flagged(value: Any) -> bool:
    """IsInconsistent of an array variable is a tuple with one flag per element."""
    if isinstance(value, (tuple, list)):
        return any(_flagged(v) for v in value)
    return bool(value)


def _items(collection) -> List[Any]:
    count = _safe(lambda: int(collection.Count)) or 0
    return [item for item in (_safe(lambda i=i: collection.Item(i)) for i in range(count))
            if item is not None]


def _name(obj) -> str:
    return str(_safe(lambda: obj.name) or "").strip()


def _value(variable_owner, attr: str) -> Optional[float]:
    """Number of a stream variable, or None when empty or unreadable."""
    for read in (lambda: getattr(variable_owner, attr + "Value"),
                 lambda: getattr(variable_owner, attr).Value):
        number = _safe(lambda: float(read()))
        if number is not None:
            return None if number < EMPTY_BELOW else number
    return None


def is_solving(case) -> bool:
    """``case.Solver.IsSolving``; False when the property is missing."""
    return bool(_safe(lambda: case.Solver.IsSolving))


def inconsistent_scan(flowsheet, path: str = "Main") -> List[Dict[str, str]]:
    """Stream variables and tee splits that UniSim flags as inconsistent."""
    out: List[Dict[str, str]] = []
    for stream in _items(_safe(lambda: flowsheet.MaterialStreams)):
        for attr in STREAM_FLAGS:
            if _safe(lambda: _flagged(getattr(stream, attr).IsInconsistent)):
                out.append({"object": f"{path}/{_name(stream)}", "variable": attr})
    for op in _items(_safe(lambda: flowsheet.Operations)):
        if str(_safe(lambda: op.TypeName)) == "teeop" and _safe(
                lambda: _flagged(op.Splits.IsInconsistent)):
            out.append({"object": f"{path}/{_name(op)}", "variable": "Splits"})
    for sub in _items(_safe(lambda: flowsheet.Flowsheets)):
        out += inconsistent_scan(sub, f"{path}/{_name(sub)}")
    return out


def empty_streams(flowsheet, path: str = "Main") -> List[str]:
    """Streams where MassFlow, Pressure or Temperature holds no value."""
    out = [f"{path}/{_name(s)}" for s in _items(_safe(lambda: flowsheet.MaterialStreams))
           if any(_value(s, attr) is None for attr in STREAM_FLAGS)]
    for sub in _items(_safe(lambda: flowsheet.Flowsheets)):
        out += empty_streams(sub, f"{path}/{_name(sub)}")
    return out


# ---------------------------------------------------------------------------
# The session
# ---------------------------------------------------------------------------

class UniSimSession:
    """One UniSim process owned by this script.

    Keyword arguments starting with ``_`` replace COM and process calls in tests.
    """

    def __init__(self, visible: bool = False, work_dir: Optional[str] = None,
                 watch_popups: bool = True, wait_closing_s: float = 60.0,
                 poll_closing_s: float = 2.0,
                 _dispatch: Optional[Callable[[], Any]] = None,
                 _pids: Optional[Callable[[], Iterable[int]]] = None,
                 _watchdog: Optional[Callable[[Set[int]], Any]] = None,
                 _kill: Optional[Callable[[int], None]] = None,
                 _sleep: Optional[Callable[[float], None]] = None):
        self.visible, self.watch_popups = visible, watch_popups
        self.wait_closing_s, self.poll_closing_s = wait_closing_s, poll_closing_s
        self._dispatch = _dispatch or _dispatch_ex
        self._pids = _pids or pids_of
        self._watchdog = _watchdog or (lambda pids: PopupWatchdog(pids).start())
        self._kill = _kill or kill_pid
        self._sleep = _sleep or time.sleep
        self._given_work_dir = Path(work_dir) if work_dir else None
        self._work_dir: Optional[Path] = None
        self._open: List[Dict[str, Any]] = []  # {"case", "source", "sha256"}
        self._lock = threading.Lock()
        self.app = None
        self.pid: Optional[int] = None
        self.watchdog = None
        #: Label of a limit that fired and has not been raised yet.
        self.timed_out: Optional[str] = None
        #: True after a limit ended the process; ``restart()`` clears it.
        self.ended_by_limit = False
        self.report: Dict[str, Any] = {"pids": [], "sources": [], "popups": [], "solves": [],
                                       "timeouts": []}

    @property
    def usable(self) -> bool:
        """True while the own process runs and no limit has ended it."""
        return self.app is not None and not self.ended_by_limit

    # -- lifetime -------------------------------------------------------------

    def __enter__(self) -> "UniSimSession":
        return self.start()

    def __exit__(self, exc_type, exc, tb) -> bool:
        self.close(error=exc_type is not None)
        return False

    def start(self) -> "UniSimSession":
        """Start the own UniSim process (no-op when already running)."""
        if self.app is not None:
            return self
        self.app, self.pid, waited = start_owned_unisim(
            self._dispatch, self._pids, self.wait_closing_s, self.poll_closing_s, self._sleep)
        self.ended_by_limit = False
        self.report["pids"].append(self.pid)
        if waited:
            self.report["waited_for_closing_unisim"] = waited
        try:
            if self.watch_popups:
                self.watchdog = self._watchdog({self.pid})
            self.app.Visible = bool(self.visible)
        except Exception:
            self._end_process()
            raise
        logger.info("Started own UniSim process %s", self.pid)
        return self

    def restart(self) -> "UniSimSession":
        """End the own process (closing its cases) and start a fresh one."""
        self._end_process()
        return self.start()

    def close(self, error: bool = False) -> None:
        """Close every open case, quit and end the own process, check sources.

        Unless ``error`` says an exception is already on its way, raises
        ``UniSimTimeout`` if a limit ended the process and the timeout was not
        raised yet, and ``UniSimSessionError`` if a source file changed.
        """
        self._end_process()
        if self._work_dir is not None and self._given_work_dir is None:
            shutil.rmtree(self._work_dir, ignore_errors=True)
            self._work_dir = None
        for entry in self.report["sources"]:
            if "unchanged" not in entry:  # the copy never opened
                entry["unchanged"] = _safe(lambda e=entry: sha256(e["source"])) == entry["sha256"]
        changed = [s["source"] for s in self.report["sources"] if s["unchanged"] is False]
        self.report["sources_unchanged"] = not changed
        pending, self.timed_out = self.timed_out, None
        if pending and not error:
            raise UniSimTimeout(f"{pending}: UniSim process ended")
        unreported = [s["source"] for s in self.report["sources"]
                      if s["unchanged"] is False and not s.get("reported")]
        if unreported and not error:
            raise UniSimSessionError(f"source model changed while its copy was open: {unreported}")

    def _end_process(self) -> None:
        for record in list(self._open):
            self._close_record(record)
        if self.app is not None:
            _safe(self.app.Quit)
        self.app = None
        if self.watchdog is not None:
            _safe(self.watchdog.stop)
            self.report["popups"] = self.report["popups"] + list(_safe(lambda: self.watchdog.since(0)) or [])
            self.watchdog = None
        if self.pid is not None:
            if self.pid in set(_safe(self._pids) or ()):
                self._kill(self.pid)  # no orphan when Quit did not end it; only ever the own PID
            self.pid = None

    # -- limits ---------------------------------------------------------------

    def _expire(self, what: str) -> None:
        with self._lock:
            if self.pid is None:
                return
            self.timed_out = what
            self.ended_by_limit = True
            self.report["timeouts"].append({"what": what, "pid": self.pid})
            logger.error("%s: ending own UniSim process %s", what, self.pid)
            self._kill(self.pid)

    def _take_timeout(self, label: str) -> Optional["UniSimTimeout"]:
        """The timeout for ``label`` if it fired (cleared, so it is raised once)."""
        if self.timed_out != label:
            return None
        self.timed_out = None
        return UniSimTimeout(f"{label}: UniSim process ended")

    @contextlib.contextmanager
    def limit(self, seconds: Optional[float], what: str = "UniSim work"):
        """End the own process when the block takes longer than ``seconds``.

        ``None`` or 0 disables the limit. Raises ``UniSimTimeout`` once when it
        fires; ``usable`` is then False until ``restart()``.
        """
        if not seconds:
            yield
            return
        label = f"{what} exceeded {seconds:g} s"
        timer = threading.Timer(seconds, self._expire, args=(label,))
        timer.daemon = True
        timer.start()
        try:
            yield
        except Exception as exc:
            timeout = self._take_timeout(label)
            if timeout is not None:
                raise timeout from exc
            raise
        finally:
            timer.cancel()
        timeout = self._take_timeout(label)
        if timeout is not None:
            raise timeout

    # -- cases ----------------------------------------------------------------

    def _work(self) -> Path:
        if self._work_dir is None:
            self._work_dir = self._given_work_dir or Path(tempfile.mkdtemp(prefix="unisim_copy_"))
            self._work_dir.mkdir(parents=True, exist_ok=True)
        return self._work_dir

    def open_copy(self, path) -> Any:
        """Open a writable copy of ``path``; the source file is never opened."""
        if self.app is None:
            self.start()
        source = Path(path).resolve()
        if not source.is_file():
            raise FileNotFoundError(f"UniSim file not found: {source}")
        digest = sha256(source)
        copy = self._work() / f"{len(self.report['sources']) + 1:02d}_{source.name}"
        shutil.copy2(source, copy)
        os.chmod(copy, stat.S_IWRITE | stat.S_IREAD)  # copy2 keeps a read-only flag
        entry = {"source": str(source), "copy": str(copy), "sha256": digest}
        self.report["sources"].append(entry)
        case = self.app.SimulationCases.Open(str(copy))
        self._open.append({"case": case, "entry": entry})
        return case

    def new_case(self) -> Any:
        """Create a blank case in the own process."""
        if self.app is None:
            self.start()
        try:
            case = self.app.SimulationCases.Add()
        except Exception:  # noqa: BLE001 - some versions need an explicit name
            case = self.app.SimulationCases.Add("")
        self._open.append({"case": case, "entry": None})
        return case

    def close_case(self, case) -> None:
        """Close a case without saving and check that its source is unchanged."""
        record = next((r for r in self._open if r["case"] is case), None)
        if record is None:
            _safe(lambda: case.Close(False))
            return
        self._close_record(record)
        entry = record["entry"]
        if entry is not None and entry["unchanged"] is False:
            entry["reported"] = True
            raise UniSimSessionError(f"source model changed while its copy was open: {entry['source']}")

    def _close_record(self, record: Dict[str, Any]) -> None:
        case = record["case"]
        try:
            case.Close(False)
        except Exception:  # noqa: BLE001
            _safe(case.Close)
        self._open.remove(record)
        entry = record["entry"]
        if entry is not None:
            entry["unchanged"] = _safe(lambda: sha256(entry["source"])) == entry["sha256"]

    # -- solving --------------------------------------------------------------

    def solve(self, case, cap_s: float = 600.0, poll_s: float = 0.5,
              grace_s: float = 30.0) -> Tuple[float, bool]:
        """Switch the solver on and wait until it is idle or ``cap_s`` passes.

        ``CanSolve = True`` can block until the solve ends, so the own process is
        ended ``grace_s`` after the cap and ``UniSimTimeout`` is raised. Returns
        ``(seconds, finished)``.
        """
        start = time.monotonic()
        with self.limit(cap_s + grace_s, f"solve (cap {cap_s:g} s)"):
            case.Solver.CanSolve = True
            while is_solving(case) and time.monotonic() - start < cap_s:
                self._sleep(poll_s)
            finished = not is_solving(case)
        seconds = round(time.monotonic() - start, 1)
        self.report["solves"].append({"seconds": seconds, "finished": finished, "cap_s": cap_s})
        return seconds, finished

    def solve_status(self, case) -> Dict[str, Any]:
        """Solver state, inconsistent variables and streams without values."""
        flowsheet = case.Flowsheet
        return {"is_solving": is_solving(case),
                "inconsistent": inconsistent_scan(flowsheet),
                "empty_streams": empty_streams(flowsheet)}

    @property
    def popups(self) -> List[Dict[str, Any]]:
        """Dialogs dismissed so far (closed sessions included)."""
        live = list(_safe(lambda: self.watchdog.since(0)) or []) if self.watchdog is not None else []
        return self.report["popups"] + live
