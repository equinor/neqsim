"""Engine-too-old guard of the neqsim CLI for pip and plugin installs."""
import io
import sys
import zipfile
from contextlib import redirect_stdout
from unittest import mock

import neqsim_cli


def _fake_package(tmp_path, entries):
    lib = tmp_path / "neqsim" / "lib"
    lib.mkdir(parents=True)
    with zipfile.ZipFile(str(lib / "neqsim-9.9.9.jar"), "w") as jar:
        for entry in entries:
            jar.writestr(entry, b"")
    return mock.Mock(submodule_search_locations=[str(tmp_path / "neqsim")])


def test_packaged_jar_lookup(tmp_path):
    spec = _fake_package(tmp_path, ["neqsim/thermo/Present.class"])
    with mock.patch("importlib.util.find_spec", return_value=spec):
        assert neqsim_cli._packaged_jar_has("neqsim/thermo/Present.class")
        assert not neqsim_cli._packaged_jar_has("neqsim/pvtsimulation/util/PVTFluidStudy.class")


def test_unknown_jar_layout_never_blocks(tmp_path):
    with mock.patch("importlib.util.find_spec", return_value=None):
        assert neqsim_cli._packaged_jar_has("neqsim/anything.class")


def test_missing_engine_method_gives_hint_outside_checkout():
    def crash(_argv):
        raise AttributeError("'neqsim.process.equipment.reservoir.WellFlow' object has no "
                             "attribute 'addLiquidInjectionZone'")

    out = io.StringIO()
    with mock.patch.object(neqsim_cli, "_is_checkout", return_value=False), redirect_stdout(out):
        assert neqsim_cli._run_needing_newer_engine(crash, []) == 3
    assert "newer NeqSim engine" in out.getvalue()
    assert "pip install -U neqsim" in out.getvalue()


def test_other_errors_and_checkouts_are_not_masked():
    def other(_argv):
        raise AttributeError("'NoneType' object has no attribute 'x'")

    def engine(_argv):
        raise AttributeError("'neqsim.Foo' object has no attribute 'x'")

    with mock.patch.object(neqsim_cli, "_is_checkout", return_value=False):
        try:
            neqsim_cli._run_needing_newer_engine(other, [])
        except AttributeError:
            pass
        else:
            raise AssertionError("unrelated AttributeError must propagate")
    with mock.patch.object(neqsim_cli, "_is_checkout", return_value=True):
        try:
            neqsim_cli._run_needing_newer_engine(engine, [])
        except AttributeError:
            pass
        else:
            raise AssertionError("a source checkout must see the real error")


def test_successful_command_returns_its_exit_code():
    assert neqsim_cli._run_needing_newer_engine(lambda argv: 0, []) == 0
    assert sys.modules["neqsim_cli"] is neqsim_cli
