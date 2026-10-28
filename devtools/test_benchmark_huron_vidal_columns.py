"""Hermetic acceptance tests for benchmark identity and receipt guards, not timing claims."""

import copy
import importlib.util
from pathlib import Path
import sys
from types import SimpleNamespace

import pytest


SPEC = importlib.util.spec_from_file_location(
    "hv_columns", Path(__file__).with_name("benchmark_huron_vidal_columns.py")
)
BENCHMARK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BENCHMARK)


def environment():
    """Return a complete bounded resume identity without requiring a Java installation."""
    return {
        "protocol_version": 2, "base_sha": "base", "component_class_sha256": {"baseline": "a", "candidate": "b"},
        "component_class_versions": {"baseline": 52, "candidate": 52}, "harness_class_sha256": "harness",
        "classpath_sha256": "artifacts", "java": "JDK17", "matrix": [["SRK", 4, "flash", 3]],
        "forks": 2, "warmup_batches": 30, "measurement_batches": 2, "cpu_affinity": None,
        "jvm_options": ["-Xbatch"], "phase_iterations": {"4": 2000}, "flash_iterations": {"4": 20},
        "platform": "mock-host", "host_sha256": "host-identity",
    }


@pytest.mark.parametrize("key", list(environment()))
def test_resume_rejects_each_changed_or_missing_input(key):
    """Changing a scientific or runtime identity must fail before reusing any receipt."""
    current = environment()
    changed = copy.deepcopy(current)
    changed[key] = "different"
    with pytest.raises(ValueError, match=key):
        BENCHMARK.validate_resume(changed, current)
    del changed[key]
    with pytest.raises(ValueError, match=key):
        BENCHMARK.validate_resume(changed, current)


def test_resume_accepts_unchanged_identity_with_result_metadata():
    """Finished-result timestamps do not alter the frozen runtime contract."""
    previous = dict(environment(), utc_finished="previous", jvms=8)
    BENCHMARK.validate_resume(previous, environment())


def test_artifacts_detect_core_resources_jars_and_classpath_order(tmp_path):
    """An unchanged harness cannot hide changed EOS classes, database resources or dependencies."""
    classes = tmp_path / "classes"
    classes.mkdir()
    source = classes / "EOS.class"
    source.write_bytes(b"class A")
    database = classes / "database.csv"
    database.write_text("a,1\n")
    jar = tmp_path / "dependency.jar"
    jar.write_bytes(b"jar A")
    baseline = BENCHMARK.artifact_fingerprint([classes, jar])
    assert baseline != BENCHMARK.artifact_fingerprint([jar, classes])
    for path, changed in ((source, b"class B"), (database, b"a,2\n"), (jar, b"jar B")):
        original = path.read_bytes()
        path.write_bytes(changed)
        assert baseline != BENCHMARK.artifact_fingerprint([classes, jar])
        path.write_bytes(original)
    assert baseline == BENCHMARK.artifact_fingerprint([classes, jar])
    jar.unlink()
    with pytest.raises(ValueError, match="Missing classpath"):
        BENCHMARK.artifact_fingerprint([classes, jar])


def receipts():
    """Construct a complete two-fork, four-arm fixture with two rounds per JVM."""
    summaries, rounds = [], []
    for fork in range(2):
        for arm in BENCHMARK.ARMS:
            summaries.append({"fork": fork, "eos": "SRK", "components": 4, "operation": "flash", "init": 3,
                              "arm": arm})
            for number in range(2):
                rounds.append({"sample": f"f{fork}-SRK-4-flash-i3-{arm}", "round": number})
    return summaries, rounds


def test_complete_and_partial_receipts_are_distinct():
    """A partial checkpoint may resume but cannot be reported as a completed matrix."""
    summaries, rounds = receipts()
    cells = [("SRK", 4, "flash", 3)]
    BENCHMARK.validate_receipts(summaries, rounds, cells, 2, 2, complete=True)
    BENCHMARK.validate_receipts(summaries[:-1], rounds, cells, 2, 2)
    with pytest.raises(ValueError, match="Incomplete JVM matrix"):
        BENCHMARK.validate_receipts(summaries[:-1], rounds, cells, 2, 2, complete=True)


@pytest.mark.parametrize("corruption", ["missing_round", "duplicate_round", "duplicate_jvm", "outside_matrix"])
def test_resume_rejects_incomplete_duplicate_or_mislabeled_evidence(corruption):
    """Complete-receipt reuse cannot fill holes by duplicate rows or another scenario."""
    summaries, rounds = receipts()
    if corruption == "missing_round":
        rounds.pop()
    elif corruption == "duplicate_round":
        rounds[-1] = dict(rounds[-2])
    elif corruption == "duplicate_jvm":
        summaries.append(dict(summaries[-1]))
    else:
        summaries[-1]["eos"] = "PR"
    with pytest.raises(ValueError):
        BENCHMARK.validate_receipts(summaries, rounds, [("SRK", 4, "flash", 3)], 2, 2)


def test_atomic_csv_roundtrip(tmp_path):
    """CSV replacement preserves complete values and leaves no partially published file."""
    path = tmp_path / "rounds.csv"
    BENCHMARK.write_csv(path, [{"sample": "original", "round": 0}])
    BENCHMARK.write_csv(path, [{"sample": "replacement", "round": 1}])
    assert BENCHMARK.read_csv(path) == [{"sample": "replacement", "round": "1"}]
    assert not path.with_name(path.name + ".tmp").exists()


@pytest.fixture
def mock_runtime(tmp_path, monkeypatch):
    """Provide mocked JVM receipts to test orchestration; no thermodynamic or timing acceptance is inferred."""
    project = tmp_path / "project"
    for root in (project / "target/classes", project / "target/test-classes"):
        root.mkdir(parents=True)
    harness = project / "target/test-classes" / Path(BENCHMARK.HARNESS.replace(".", "/") + ".class")
    harness.parent.mkdir(parents=True)
    harness.write_bytes(b"harness")
    for variant in ("baseline", "candidate"):
        component = tmp_path / variant / BENCHMARK.COMPONENT
        component.parent.mkdir(parents=True)
        component.write_bytes(b"\xca\xfe\xba\xbe\0\0\0\x34" + variant.encode())
    jar = tmp_path / "dependency.jar"
    jar.write_bytes(b"dependency")
    dependencies = tmp_path / "classpath.txt"
    dependencies.write_text(str(jar))
    output = tmp_path / "evidence"
    argv = ["benchmark", "--project", str(project), "--baseline", str(tmp_path / "baseline"),
            "--candidate", str(tmp_path / "candidate"), "--dependencies", str(dependencies),
            "--output", str(output), "--base-sha", "frozen", "--forks", "2", "--warmups", "1",
            "--rounds", "2", "--eos", "SRK", "--components", "4", "--operations", "phase",
            "--initializations", "1"]
    calls = []

    def run(command, **kwargs):
        if command[-1] == "-version":
            return SimpleNamespace(stderr="Mock JDK")
        label, path, eos, size, operation, init, rule, _, count, _ = command[-10:]
        calls.append(label)
        BENCHMARK.write_csv(Path(path), [
            {"sample": label, "round": number, "ns_per_op": 1, "checksum": 1,
             "max_balance": 0, "max_logfugacity": 0} for number in range(int(count))
        ])
        BENCHMARK.write_csv(Path(path + ".states.csv"), [
            {"sample": label, "eos": eos, "components": size, "operation": operation, "init": init,
             "rule": rule, "phase": "gas", "x": 1}
        ])
        return SimpleNamespace(returncode=0)

    monkeypatch.setattr(BENCHMARK.shutil, "which", lambda _: "mock-java")
    monkeypatch.setattr(BENCHMARK.platform, "platform", lambda: "mock-host")
    monkeypatch.setattr(BENCHMARK.subprocess, "run", run)
    monkeypatch.setattr(sys, "argv", argv)
    return SimpleNamespace(argv=argv, output=output, jar=jar, calls=calls)


def test_orchestrator_completes_and_resumes_without_duplicate_jvms(mock_runtime, monkeypatch):
    """A complete mocked run must resume without repeating work or losing receipts."""
    import json

    BENCHMARK.main()
    assert len(mock_runtime.calls) == 8
    result = json.loads((mock_runtime.output / "environment.json").read_text())
    assert result["jvms"] == 8
    assert result["matrix"] == [["SRK", 4, "phase", 1]]
    assert result["utc_finished"]
    monkeypatch.setattr(sys, "argv", mock_runtime.argv + ["--resume"])
    BENCHMARK.main()
    assert len(mock_runtime.calls) == 8
    assert len(BENCHMARK.read_csv(mock_runtime.output / "rounds.csv")) == 16


@pytest.mark.parametrize("changed", ["matrix", "dependency"])
def test_cli_resume_rejects_changed_identity_before_starting_a_jvm(mock_runtime, monkeypatch, changed):
    """Reproduce the previously accepted matrix/dependency switch through the actual CLI path."""
    BENCHMARK.main()
    argv = mock_runtime.argv + ["--resume"]
    if changed == "matrix":
        argv[argv.index("--eos") + 1] = "PR"
    else:
        mock_runtime.jar.write_bytes(b"changed dependency")
    monkeypatch.setattr(sys, "argv", argv)
    with pytest.raises(ValueError, match="matrix|classpath_sha256"):
        BENCHMARK.main()
    assert len(mock_runtime.calls) == 8


def test_concurrent_artifact_change_prevents_completion(mock_runtime, monkeypatch):
    """A dependency changed during the last JVM must leave the experiment incomplete."""
    import json

    original_run = BENCHMARK.subprocess.run

    def change_after_last_jvm(command, **kwargs):
        result = original_run(command, **kwargs)
        if len(mock_runtime.calls) == 8:
            mock_runtime.jar.write_bytes(b"concurrent rebuild")
        return result

    monkeypatch.setattr(BENCHMARK.subprocess, "run", change_after_last_jvm)
    with pytest.raises(RuntimeError, match="Classpath artifacts changed"):
        BENCHMARK.main()
    result = json.loads((mock_runtime.output / "environment.json").read_text())
    assert "utc_finished" not in result
