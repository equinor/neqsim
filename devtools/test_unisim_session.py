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
    UniSimSession, UniSimSessionError, UniSimTimeout, inconsistent_scan, empty_streams)
from unisim_writer import ParsedUnit, UniSimWriter  # noqa: E402

DENIED = Exception("(-2147024891, 'Access is denied.', None, None)")


# ------------------------------------------------------------------ fake COM
class Var:
    def __init__(self, value=1.0, inconsistent=False, refuse=None):
        self.Value, self.IsInconsistent, self.refuse = value, inconsistent, refuse
        self.erased, self.set_to = False, None

    def SetValue(self, value, unit=None):
        if self.refuse:
            raise self.refuse
        self.set_to = (value, unit)

    def Erase(self):
        self.erased = True


class Stream:
    def __init__(self, name, empty=(), inconsistent=(), refuse=None):
        self.name = name
        for attr in ("MassFlow", "Pressure", "Temperature"):
            setattr(self, attr, Var(-32767.0 if attr in empty else 1.0,
                                    attr in inconsistent, refuse))


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
    session, app, killed = make_session([set(), {42}], pid_file=str(tmp_path / "pid"))
    try:
        with session:
            assert (tmp_path / "pid").read_text() == "42"
            case = session.open_copy(source)
            copy = case.path
            assert copy != str(source.resolve())
            assert os.access(copy, os.W_OK)
            session.close_case(case)
            assert case.closed_with is False
    finally:
        os.chmod(source, stat.S_IWRITE | stat.S_IREAD)
    assert app.quit and killed == [42]
    assert not (tmp_path / "pid").exists()
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


def test_limit_ends_own_process_raises_once_and_session_restarts():
    session, _, killed = make_session([set(), {42}])
    session.start()
    with pytest.raises(UniSimTimeout, match="slow step exceeded"):
        with session.limit(0.05, "slow step"):
            time.sleep(0.3)
    assert killed == [42] and not session.usable
    with session.limit(0.05, "slow step"):  # a fresh, fast block is not a timeout
        pass
    session.close()  # the timeout was already raised once


def test_reader_does_not_retry_open_after_its_limit_ended_unisim(tmp_path):
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
    assert len(opens) == 1
    reader.close(error=True)


def test_solve_status_names_inconsistent_and_empty_objects():
    tee = type("Tee", (), {"name": "TEE-1", "TypeName": "teeop",
                           "Splits": Var(inconsistent=(False, True))})()
    other_tee = type("Tee", (), {"name": "TEE-2", "TypeName": "teeop",
                                 "Splits": Var(inconsistent=(False, False))})()
    sub = Flowsheet(streams=[Stream("s3", empty=("Pressure",))])
    sub.name = "TPL1"
    fs = Flowsheet(streams=[Stream("s1", inconsistent=("Temperature",)), Stream("s2")],
                   ops=[tee, other_tee], subs=[sub])
    assert inconsistent_scan(fs) == [{"object": "Main/s1", "variable": "Temperature"},
                                     {"object": "Main/TEE-1", "variable": "Splits"}]
    assert empty_streams(fs) == ["Main/TPL1/s3"]


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


def test_renamed_operation_is_renamed_back_or_reported():
    writer = UniSimWriter()
    op = Compressor("K-100")
    writer._ensure_operation_name(op, "compressor")
    assert op.name == "compressor" and writer.warnings == []

    class Stubborn(Compressor):
        def __setattr__(self, key, value):
            if key == "name" and "name" in self.__dict__:
                return
            super().__setattr__(key, value)
    writer._ensure_operation_name(Stubborn("K-101"), "compressor2")
    assert "as 'K-101'" in writer.warnings[0]


def test_write_probe_raises_on_access_denied_and_keeps_existing_streams():
    writer = UniSimWriter()
    fs = Flowsheet()
    fs.MaterialStreams.make = lambda name: Stream(name, refuse=DENIED)
    with pytest.raises(RuntimeError, match="licence server"):
        writer._check_write_access(fs)
    assert fs.MaterialStreams.Count == 0

    writable = Flowsheet(streams=[Stream("neqsim_write_probe")])
    writer._check_write_access(writable)
    assert [s.name for s in writable.MaterialStreams.items] == ["neqsim_write_probe"]
