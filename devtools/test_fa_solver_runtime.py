"""Check the shared flow-assurance/SURF runtime adapter without starting Java."""
import sys
from pathlib import Path
from types import SimpleNamespace

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent))

import fa_solver  # noqa: E402


@pytest.mark.parametrize("already_started", [False, True])
def test_runtime_uses_workspace_bootstrap_and_reuses_a_running_jvm(monkeypatch, already_started):
    calls = []
    package = object()

    def initialize(**kwargs):
        calls.append(kwargs)

    def java_package(name):
        assert name == "neqsim"
        return package

    monkeypatch.setitem(sys.modules, "jpype", SimpleNamespace(
        isJVMStarted=lambda: already_started, JPackage=java_package))
    monkeypatch.setitem(sys.modules, "neqsim_dev_setup", SimpleNamespace(neqsim_init=initialize))
    # The supported Python distribution does not provide this top-level module.
    monkeypatch.setitem(sys.modules, "jneqsim", None)

    assert fa_solver.neqsim_handle() is package
    assert calls == ([] if already_started else [{"verbose": False}])


def test_runtime_preserves_the_bootstrap_failure_reason(monkeypatch):
    def initialize(**kwargs):
        raise FileNotFoundError("No Java runtime found")

    monkeypatch.setitem(sys.modules, "jpype", SimpleNamespace(isJVMStarted=lambda: False))
    monkeypatch.setitem(sys.modules, "neqsim_dev_setup", SimpleNamespace(neqsim_init=initialize))
    monkeypatch.setitem(sys.modules, "jneqsim", None)

    with pytest.raises(fa_solver.FlowAssuranceError, match="No Java runtime found") as error:
        fa_solver.neqsim_handle()
    assert isinstance(error.value.__cause__, FileNotFoundError)
