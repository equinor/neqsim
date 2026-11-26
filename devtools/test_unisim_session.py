"""UniSim session guard and writer specification logic, on fake COM objects.

No UniSim, pywin32 or Windows needed: process listing, dispatch, the pop-up
watchdog and process termination are injected.
"""
import os
import stat
import sys
import time

import pytest

sys.path.insert(0, os.path.dirname(__file__))

from unisim_session import (  # noqa: E402
    UniSimSession, UniSimSessionError, UniSimTimeout, inconsistent_scan)
from unisim_writer import ParsedUnit, UniSimWriter  # noqa: E402

DENIED = Exception("(-2147024891, 'Access is denied.', None, None)")


# ------------------------------------------------------------------ fake COM
class Var:
    def __init__(self, inconsistent=False):
        self.IsInconsistent = inconsistent
        self.erased, self.set_to = False, None

    def SetValue(self, value, unit=None):
        self.set_to = (value, unit)

    def Erase(self):
        self.erased = True


class Stream:
    def __init__(self, name, inconsistent=()):
        self.name = name
        for attr in ("MassFlow", "Pressure", "Temperature"):
            setattr(self, attr, Var(attr in inconsistent))


class Coll:
    def __init__(self, items=(), make=None):
        self.items, self.make = list(items), make

    @property
    def Count(self):
        return len(self.items)

    def Item(self, i):
        return self.items[i]

    def Add(self, *args):
        item = self.make(*args)
        self.items.append(item)
        return item

    def Remove(self, i):
        del self.items[i]


class Solver:
    def __init__(self):
        self.CanSolve, self.IsSolving = False, False


class Flowsheet:
    def __init__(self, streams=(), ops=(), subs=()):
        self.MaterialStreams = Coll(streams, make=lambda name: Stream(name))
        self.Operations, self.Flowsheets = Coll(ops), Coll(subs)
        self.name = "Main"


class Case:
    def __init__(self, path=None):
        self.path, self.Solver, self.Flowsheet, self.closed_with = path, Solver(), Flowsheet(), None

    def Close(self, save):
        self.closed_with = save


class App:
    def __init__(self):
        self.Visible, self.quit, self.opened = None, False, []
        app = self

        class Cases:
            def Open(self, path):
                app.opened.append(Case(path))
                return app.opened[-1]

            def Add(self):
                app.opened.append(Case())
                return app.opened[-1]
        self.SimulationCases = Cases()

    def Quit(self):
        self.quit = True


class Watchdog:
    def __init__(self, pids):
        self.pids, self.events = pids, [{"title": "UniSim", "text": "Efficiency above 100 %",
                                          "action": "pressed ok"}]

    def since(self, mark):
        return self.events[mark:]

    def stop(self):
        pass


def make_session(pid_sets, **kw):
    app, killed, seq = App(), [], iter(pid_sets)
    last = [pid_sets[-1]]

    def pids():
        last[0] = next(seq, last[0])
        return last[0]
    session = UniSimSession(_dispatch=lambda: app, _pids=pids, _watchdog=Watchdog,
                            _kill=killed.append, _sleep=lambda s: None, **kw)
    return session, app, killed


# ------------------------------------------------------------------ session
def test_running_unisim_is_refused_without_being_dispatched():
    session, app, killed = make_session([{11}], wait_closing_s=0.1, poll_closing_s=0.05)
    with pytest.raises(UniSimSessionError, match="already running"):
        session.start()
    assert app.Visible is None and not app.quit and killed == []


def test_unclear_ownership_after_dispatch_is_refused():
    session, app, killed = make_session([set(), {42, 43}])
    with pytest.raises(UniSimSessionError, match="expected one UniSim"):
        session.start()
    assert not app.quit and killed == []


def test_copy_is_opened_closed_without_saving_and_only_own_pid_ended(tmp_path):
    source = tmp_path / "plant.usc"
    source.write_bytes(b"case")
    os.chmod(source, stat.S_IREAD)
    session, app, killed = make_session([set(), {42}])
    try:
        with session:
            case = session.open_copy(source)
            copy = case.path
            assert copy != str(source.resolve())
            assert os.access(copy, os.W_OK)
            session.close_case(case)
            assert case.closed_with is False
    finally:
        os.chmod(source, stat.S_IWRITE | stat.S_IREAD)
    assert app.quit and killed == [42]
    assert session.report["sources_unchanged"] is True
    assert session.report["popups"][0]["text"] == "Efficiency above 100 %"
    assert not os.path.exists(copy)


def test_changed_source_is_reported(tmp_path):
    source = tmp_path / "plant.usc"
    source.write_bytes(b"case")
    session, _, _ = make_session([set(), {42}])
    with session:
        case = session.open_copy(source)
        source.write_bytes(b"changed")
        with pytest.raises(UniSimSessionError, match="source model changed"):
            session.close_case(case)


def test_limit_ends_own_process_and_raises_once():
    session, _, killed = make_session([set(), {42}])
    session.start()
    with pytest.raises(UniSimTimeout, match="slow step exceeded"):
        with session.limit(0.05, "slow step"):
            time.sleep(0.3)
    assert killed[0] == 42
    session.close()  # the timeout was already raised once


def test_close_survives_a_dead_com_proxy():
    session, app, killed = make_session([set(), {42}])
    session.start()

    class DeadApp(App):
        @property
        def Quit(self):  # on a dead proxy the lookup itself fails
            raise Exception("The RPC server is unavailable.")
    app.__class__ = DeadApp
    session.close()
    assert killed == [42] and session.app is None and session.watchdog is None


def test_watchdog_never_presses_yes_and_only_sees_own_dialogs(monkeypatch):
    import types
    import unisim_session
    # hwnd: (class, text, pid, children)
    windows = {1: ("#32770", "Save?", 42, [11, 12]), 11: ("Button", "&Yes", 42, []),
               12: ("Button", "&No", 42, []), 2: ("#32770", "Other", 99, [21]),
               21: ("Button", "OK", 99, []), 3: ("#32770", "Overwrite?", 42, [31]),
               31: ("Button", "Yes", 42, [])}
    posted = []
    gui = types.SimpleNamespace(
        EnumWindows=lambda cb, acc: [cb(h, acc) for h in (1, 2, 3)],
        EnumChildWindows=lambda h, cb, acc: [cb(c, acc) for c in windows[h][3]],
        IsWindowVisible=lambda h: True, GetClassName=lambda h: windows[h][0],
        GetWindowText=lambda h: windows[h][1], PostMessage=lambda h, msg, *a: posted.append((h, msg)))
    monkeypatch.setitem(sys.modules, "win32gui", gui)
    monkeypatch.setitem(sys.modules, "win32con", types.SimpleNamespace(BM_CLICK="click", WM_CLOSE="close"))
    monkeypatch.setitem(sys.modules, "win32process", types.SimpleNamespace(
        GetWindowThreadProcessId=lambda h: (0, windows[h][2])))
    assert unisim_session._dialogs({42}) == [1, 3]
    assert unisim_session.dismiss(1)["action"] == "pressed no"
    assert unisim_session.dismiss(3)["action"] == "WM_CLOSE"
    assert posted == [(12, "click"), (3, "close")]


def test_reader_closes_its_session_after_a_timeout(tmp_path):
    from unisim_reader import UniSimReader
    source = tmp_path / "plant.usc"
    source.write_bytes(b"case")
    session, app, _ = make_session([set(), {42}])
    opens = []

    def slow_open(path):
        opens.append(path)
        time.sleep(0.3)
        raise Exception("The RPC server is unavailable.")
    app.SimulationCases.Open = slow_open
    reader = UniSimReader(time_limit_s=0.05)
    reader._session = session.start()
    with pytest.raises(UniSimTimeout, match="reading plant.usc exceeded"):
        reader.read(str(source), export_e300=False)
    assert len(opens) == 1 and reader._session is None


def test_inconsistent_scan_names_streams_and_tee_splits():
    tee = type("Tee", (), {"name": "TEE-1", "TypeName": "teeop",
                           "Splits": Var(inconsistent=(False, True))})()
    other_tee = type("Tee", (), {"name": "TEE-2", "TypeName": "teeop",
                                 "Splits": Var(inconsistent=(False, False))})()
    sub = Flowsheet(streams=[Stream("s3", inconsistent=("Pressure",))])
    sub.name = "TPL1"
    fs = Flowsheet(streams=[Stream("s1", inconsistent=("Temperature",)), Stream("s2")],
                   ops=[tee, other_tee], subs=[sub])
    assert inconsistent_scan(fs) == [{"object": "Main/s1", "variable": "Temperature"},
                                     {"object": "Main/TEE-1", "variable": "Splits"},
                                     {"object": "Main/TPL1/s3", "variable": "Pressure"}]


# ------------------------------------------------------------------ writer
class Compressor:
    def __init__(self, name="K-100"):
        self.name = name
        self.CompAdiabaticEff, self.CompPolytropicEff = Var(), Var()
        self.ProductStream = Stream("out")

    def __setattr__(self, key, value):
        if key == "CompPolytropicEffValue":
            self.CompPolytropicEff.set_to = (value, None)
            return
        object.__setattr__(self, key, value)


def test_compressor_outlet_temperature_erases_default_efficiencies():
    writer, op = UniSimWriter(), Compressor()
    writer._set_operation_properties(op, ParsedUnit(
        "K1", "Compressor", properties={"outletPressure": 117.0, "outTemperature": 373.15,
                                         "polytropicEfficiency": 0.75}))
    assert op.CompAdiabaticEff.erased and op.CompPolytropicEff.erased
    assert op.ProductStream.Pressure.set_to == (117.0, "bar")
    assert op.ProductStream.Temperature.set_to == (pytest.approx(100.0), "C")
    assert op.CompPolytropicEff.set_to is None
    assert any("efficiency was not written" in w for w in writer.warnings)


def test_compressor_without_efficiency_warns_about_unisim_default():
    writer, op = UniSimWriter(), Compressor()
    writer._set_operation_properties(op, ParsedUnit("K1", "Compressor",
                                                    properties={"outletPressure": 117.0}))
    assert not op.CompAdiabaticEff.erased
    assert any("default 75 %" in w for w in writer.warnings)


def test_renamed_operation_is_reported_and_denied_add_raises():
    writer = UniSimWriter()
    fs = Flowsheet()
    fs.Operations.make = lambda name, kind: type("Op", (), {"name": "VLV-100"})()
    writer._create_operation(fs, ParsedUnit("valve1", "ThrottlingValve"), None)
    assert "as 'VLV-100'" in writer.warnings[0]

    def refuse(name, kind):
        raise DENIED
    fs.Operations.make = refuse
    with pytest.raises(RuntimeError, match="licence server"):
        writer._create_operation(fs, ParsedUnit("valve2", "ThrottlingValve"), None)
