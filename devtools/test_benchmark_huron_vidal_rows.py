"""Hermetic corruption guards for independent JVM row-reuse evidence.

The balanced receipts below are synthetic protocol fixtures, not timing or
thermodynamic measurements. Real retained receipts are audited separately.
"""

import copy
import csv
import io
import errno
import os
import stat
import tempfile
import json
from pathlib import Path
import unittest
from unittest.mock import patch
import zipfile

from benchmark_huron_vidal_rows import FLAGS, audit, plan, sha, stem, write_json_atomic


def encode(rows):
    """Serialize a receipt with an explicit complete column roster."""
    stream = io.StringIO(newline="")
    writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
    writer.writeheader()
    writer.writerows(rows)
    return stream.getvalue().encode("utf-8")


def fixture():
    """Construct two forks with both rules and an identical-binary control."""
    matrix = dict(forks=2, profiles=[2], sizes=[5], scenarios=["init3"])
    manifest = dict(matrix=matrix, baseline_head="a" * 40, finish_utc="synthetic-fixture",
                    planned_jvms=12, flags=FLAGS, driver_sha256="d" * 64,
                    classpath_sha256=dict(base="b" * 64, candidate="c" * 64, control="b" * 64),
                    component_class_sha256=dict(
                        base="b" * 64, candidate="c" * 64, control="b" * 64), runs=[])
    files = {}
    names = ["water", "CO2", "methane", "methanol", "MEG"]
    amounts = [.3, .2, .4, .05, .05]
    for key in plan(matrix):
        rows, states = [], []
        for number in range(10):
            rows.append(dict(profile=2, rule=key[4], scenario="init3", components=5, round=number,
                             operations=2000, elapsed_ns=10000, allocated_bytes=100,
                             T_K=353.15, P_bara=50, topology="gas/aqueous", beta0=.5,
                             balance=0, normalization=0, log_fugacity=0,
                             rich_water=.3, aqueous_CO2=.2))
            for phase in range(2):
                for name, amount in zip(names, amounts):
                    states.append(dict(round=number, phase=phase, type=["gas", "aqueous"][phase],
                                       beta=.5, component=name, z=amount, x=amount, logphi=0))
        run = dict(key=key, exit=0)
        for suffix, data, field in ((".csv", encode(rows), "csv_sha256"),
                                    (".csv.states.csv", encode(states), "states_sha256"),
                                    (".log", b"synthetic protocol fixture\n", "log_sha256")):
            files[stem(key) + suffix] = data
            run[field] = sha(data)
        manifest["runs"].append(run)
    return manifest, files


class RowReuseEvidenceTest(unittest.TestCase):
    """Reject incomplete, changed and scientifically different receipts."""

    def test_complete_roster_and_balanced_order(self):
        """Independent forks reverse binary/case order without losing any cell."""
        manifest, files = fixture()
        result = audit(manifest, files.__getitem__)
        self.assertEqual((12, 120, 240000, 1200), tuple(
            result[key] for key in ("jvms", "rounds", "operations", "phase_component_rows")))
        self.assertEqual(0, result["max_same_case_difference"])
        keys = list(plan(manifest["matrix"]))
        self.assertEqual(["base", "candidate", "control"], [key[-1] for key in keys[:3]])
        self.assertEqual(["control", "candidate", "base"], [key[-1] for key in keys[6:9]])
        self.assertEqual(4, keys[6][4])

    def test_missing_duplicate_reordered_or_failed_process(self):
        """Every scheduled process remains in the acceptance denominator."""
        manifest, files = fixture()
        for change in (lambda item: item["runs"].pop(),
                       lambda item: item["runs"].append(copy.deepcopy(item["runs"][-1])),
                       lambda item: item["runs"].reverse(),
                       lambda item: item.pop("finish_utc"),
                       lambda item: item["runs"][0].update(exit=1)):
            changed = copy.deepcopy(manifest)
            change(changed)
            with self.assertRaises(ValueError):
                audit(changed, files.__getitem__)

    def test_invalid_control_replication_or_matrix(self):
        """A changed control binary or shrunken/duplicated matrix is not replication."""
        manifest, files = fixture()
        for change in (lambda item: item["component_class_sha256"].update(control="d" * 64),
                       lambda item: item["component_class_sha256"].update(candidate="not-a-hash"),
                       lambda item: item.update(baseline_head="unknown"),
                       lambda item: item["classpath_sha256"].update(control="e" * 64),
                       lambda item: item["classpath_sha256"].update(candidate="invalid"),
                       lambda item: item.update(flags=[]),
                       lambda item: item.update(driver_sha256="invalid"),
                       lambda item: item["matrix"].update(forks=1),
                       lambda item: item["matrix"].update(profiles=[2, 2]),
                       lambda item: item["matrix"].update(scenarios=["unknown"])):
            changed = copy.deepcopy(manifest)
            change(changed)
            with self.assertRaises(ValueError):
                audit(changed, files.__getitem__)

    def test_any_changed_raw_receipt_is_rejected(self):
        """CSV, complete states and logs retain their independently recorded hashes."""
        manifest, files = fixture()
        for suffix in (".csv", ".csv.states.csv", ".log"):
            changed = dict(files)
            changed[stem(manifest["runs"][0]["key"]) + suffix] += b"changed"
            with self.assertRaisesRegex(ValueError, "Changed process evidence"):
                audit(manifest, changed.__getitem__)

    def test_balanced_but_different_endpoint_is_rejected(self):
        """Equal fugacities and balances cannot hide a changed full phase vector."""
        manifest, files = fixture()
        run = manifest["runs"][1]
        name = stem(run["key"]) + ".csv.states.csv"
        states = list(csv.DictReader(io.StringIO(files[name].decode("utf-8"))))
        for row in states:
            row["logphi"] = .1
        files[name] = encode(states)
        run["states_sha256"] = sha(files[name])
        with self.assertRaisesRegex(ValueError, "Changed baseline/candidate/control equilibrium"):
            audit(manifest, files.__getitem__)

    def test_held_sources_and_diagnostic_parts_retain_identity(self):
        """Archive hashes connect the assessed binaries to held source snapshots."""
        root = Path(__file__).resolve().parents[1] / (
            "src/test/resources/neqsim/thermo/mixingrule/hv-row-performance")
        index = json.loads((root / "index.json").read_text())
        for key in ("held", "compiler_diagnostics"):
            entry = index[key]
            blocks = []
            for part in entry["parts"]:
                raw = (root / part["path"]).read_bytes()
                self.assertEqual(part["bytes"], len(raw))
                self.assertEqual(part["sha256"], sha(raw))
                blocks.append(raw)
            combined = b"".join(blocks)
            self.assertEqual(entry["bytes"], len(combined))
            self.assertEqual(entry["sha256"], sha(combined))
            if key == "held":
                with zipfile.ZipFile(io.BytesIO(combined)) as archive:
                    for number, files in index["prototypes"].items():
                        for name, identity in files.items():
                            self.assertEqual(identity["sha256"], sha(archive.read(
                                "prototype" + number + "/" + name)))
                    with zipfile.ZipFile(root / "raw.zip") as matrix:
                        manifest = json.loads(matrix.read("execution.json"))
                    self.assertEqual(index["prototypes"]["2"][
                        "ComponentGENRTLmodifiedHV.class"]["sha256"],
                        manifest["component_class_sha256"]["candidate"])
        raw = (root / index["matrix"]["path"]).read_bytes()
        self.assertEqual(index["matrix"]["bytes"], len(raw))
        self.assertEqual(index["matrix"]["sha256"], sha(raw))

        validation = json.loads((root / "validation.json").read_text())
        for receipt in (validation, validation["reconciliation"]):
            raw = (root / receipt["log_archive"]["path"]).read_bytes()
            self.assertEqual(receipt["log_archive"]["sha256"], sha(raw))
            self.assertEqual(receipt["log_archive"]["bytes"], len(raw))
            with zipfile.ZipFile(io.BytesIO(raw)) as archive:
                self.assertEqual(set(receipt["logs"]), set(archive.namelist()))
                for name, expected in receipt["logs"].items():
                    self.assertEqual(expected, sha(archive.read(name)))

    def test_retained_independent_jvm_matrix(self):
        """Reconstruct every real process hash, round, phase and binary comparison."""
        root = Path(__file__).resolve().parents[1] / (
            "src/test/resources/neqsim/thermo/mixingrule/hv-row-performance")
        with zipfile.ZipFile(root / "raw.zip") as archive:
            manifest = json.loads(archive.read("execution.json"))
            self.assertEqual(dict(forks=2, profiles=[0, 1, 2], sizes=[5, 15],
                                  scenarios=["init1", "init3", "flash"]), manifest["matrix"])
            result = audit(manifest, archive.read)
        self.assertEqual((216, 2160, 2916000, 59400), tuple(
            result[key] for key in ("jvms", "rounds", "operations", "phase_component_rows")))
        self.assertEqual(json.loads((root / "summary.json").read_text(encoding="utf-8")), result)


class ScratchTrialEvidenceTest(unittest.TestCase):
    """Audit rejected trials as frozen observations, never as live timing budgets."""

    def test_retained_negative_trials(self):
        root = Path(__file__).resolve().parents[1] / (
            "src/test/resources/neqsim/thermo/mixingrule/hv-phase-scratch")
        report = json.loads((root / "validation.json").read_text(encoding="utf-8"))
        raw = (root / "raw.zip").read_bytes()
        self.assertEqual(sha(raw), report["archive"]["sha256"])
        expected = {"tuple-full": 216, "tuple-repeat": 24, "tuple-java21": 24,
                    "tuple-boundary": 24, "descriptor-pilot": 24}
        self.assertEqual(set(report["datasets"]), set(expected))
        with zipfile.ZipFile(io.BytesIO(raw)) as archive:
            for name, count in expected.items():
                manifest = json.loads(archive.read(name + "/execution.json"))
                result = audit(manifest, lambda path: archive.read(name + "/" + path))
                self.assertEqual(result, report["datasets"][name]["summary"])
                self.assertEqual(result["jvms"], count)
                self.assertEqual(result["max_same_case_difference"], 0.0)
        # These assertions protect the retained adverse observations, not current CPU timings.
        for name in ("tuple-full", "tuple-repeat"):
            cell = next(row for row in report["datasets"][name]["summary"]["cells"]
                        if (row["profile"], row["size"], row["scenario"], row["rule"])
                        == (0, 5, "init3", 4))
            self.assertTrue(all(value > 1.0 for key in ("candidate_over_base", "candidate_over_control")
                                for value in cell["ratios"][key]))
        self.assertEqual(sum(expected.values()), 312)


class AtomicReceiptTest(unittest.TestCase):
    """A failed next checkpoint must preserve every byte of its accepted prefix."""

    def test_success_publishes_complete_json(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "execution.json"
            value = {"runs": [{"exit": 0}], "reason": "water–gas"}
            write_json_atomic(path, value)
            self.assertEqual(json.loads(path.read_bytes()), value)
            self.assertEqual(path.read_bytes(), (json.dumps(value, indent=2) + "\n").encode())
            self.assertEqual(list(Path(directory).iterdir()), [path])

    def test_failed_fsync_preserves_accepted_receipt(self):
        self.check_failed_replacement("os.fsync")

    def test_failed_replace_preserves_accepted_receipt(self):
        self.check_failed_replacement("os.replace")

    @unittest.skipUnless(os.name == "posix", "POSIX permission semantics")
    def test_replacement_preserves_existing_mode(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "execution.json"
            path.write_text("{}")
            path.chmod(0o640)
            write_json_atomic(path, {"runs": []})
            self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o640)

    @unittest.skipUnless(os.name == "posix", "POSIX permission semantics")
    def test_new_receipt_preserves_umask_creation_policy(self):
        with tempfile.TemporaryDirectory() as directory:
            control = Path(directory) / "ordinary.json"
            control.write_text("{}")
            path = Path(directory) / "execution.json"
            write_json_atomic(path, {"runs": []})
            self.assertEqual(stat.S_IMODE(path.stat().st_mode), stat.S_IMODE(control.stat().st_mode))

    def test_cleanup_failure_preserves_primary_error_and_reports_orphan(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "execution.json"
            original = b'{"runs":[]}\n'
            path.write_bytes(original)
            with patch("benchmark_huron_vidal_rows.os.fsync",
                       side_effect=OSError(errno.ENOSPC, "injected full device")):
                with patch("benchmark_huron_vidal_rows.Path.unlink",
                           side_effect=OSError(errno.EPERM, "injected cleanup refusal")):
                    with self.assertRaises(OSError) as caught:
                        write_json_atomic(path, {"runs": [1]})
            self.assertEqual(caught.exception.errno, errno.ENOSPC)
            self.assertTrue(any("Temporary receipt cleanup failed:" in note
                                for note in caught.exception.__notes__))
            self.assertEqual(path.read_bytes(), original)
            self.assertEqual(len(list(Path(directory).glob("*.tmp"))), 1)

    def test_partial_write_failure_preserves_accepted_receipt(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "execution.json"
            original = b'{"runs":[{"exit":0}]}\n'
            path.write_bytes(original)
            factory = Path.open

            class FailingWriter:
                """Write a real partial payload before raising a full-device error."""

                def __init__(self, stream):
                    self.stream = stream
                    self.name = stream.name

                def __enter__(self):
                    return self

                def __exit__(self, *args):
                    return self.stream.__exit__(*args)

                def write(self, data):
                    self.stream.write(data[:7])
                    self.stream.flush()
                    raise OSError(errno.ENOSPC, "injected partial write")

            def failed_file(candidate, *args, **kwargs):
                stream = factory(candidate, *args, **kwargs)
                return FailingWriter(stream) if args and args[0] == "xb" else stream

            with patch("benchmark_huron_vidal_rows.Path.open", failed_file):
                with self.assertRaises(OSError):
                    write_json_atomic(path, {"runs": [{"exit": 0}, {"exit": 0}]})
            self.assertEqual(path.read_bytes(), original)
            self.assertEqual(list(Path(directory).iterdir()), [path])

    def check_failed_replacement(self, operation):
        """Inject a deterministic disk failure after the new payload was written."""
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "execution.json"
            original = b'{"runs":[{"exit":0,"csv_sha256":"retained"}]}\n'
            path.write_bytes(original)
            with patch("benchmark_huron_vidal_rows." + operation,
                       side_effect=OSError(errno.ENOSPC, "injected full device")):
                with self.assertRaises(OSError):
                    write_json_atomic(path, {"runs": [{"exit": 0}, {"exit": 0}]})
            self.assertEqual(path.read_bytes(), original)
            self.assertEqual(list(Path(directory).iterdir()), [path])

    def test_serialization_failure_preserves_accepted_receipt(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "execution.json"
            original = b'{"runs":[]}\n'
            path.write_bytes(original)
            with self.assertRaises(TypeError):
                write_json_atomic(path, {"invalid": object()})
            self.assertEqual(path.read_bytes(), original)
            self.assertEqual(list(Path(directory).iterdir()), [path])


if __name__ == "__main__":
    unittest.main()
