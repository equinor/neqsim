"""Hermetic roster, numerical and fork-order guards for the selected-pair benchmark."""

import copy
import csv
import hashlib
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

from audit_huron_vidal_selected_pair import audit, stem, profile_counts
from run_huron_vidal_selected_pair_benchmark import selections, validate_evidence


def fixture(size=5):
    """Build a balanced synthetic receipt; this does not simulate thermodynamics."""
    names = ["water", "CO2", "methane", "methanol", "MEG", "ethane", "propane", "i-butane",
             "n-butane", "i-pentane", "n-pentane", "n-hexane", "n-heptane", "n-octane", "nitrogen"]
    amounts = [.3, .2, .4, .05, .05] if size == 5 else [.3, .1, .3, .03, .02] + [.025] * 10
    rows, states = [], []
    for round_number in range(10):
        rows.append(dict(profile=2, rule=4, scenario="init3", components=size, round=round_number,
                         operations=2000, elapsed_ns=10000, allocated_bytes=100,
                         T_K=353.15, P_bara=50, topology="gas/aqueous", beta0=.5,
                         balance=0, normalization=0, log_fugacity=0, rich_water=.3, aqueous_CO2=.2))
        for phase in range(2):
            for name, amount in zip(names, amounts):
                states.append(dict(round=round_number, phase=phase, type=["gas", "aqueous"][phase],
                                   beta=.5, component=name, z=amount, x=amount, logphi=0))
    return rows, states


class SelectedPairReceiptTest(unittest.TestCase):
    """Reject missing or corrupt evidence before accepting a timing process."""

    def test_complete_five_and_fifteen_component_receipts(self):
        """Accept both exact component rosters at the frozen state."""
        for size in (5, 15):
            validate_evidence(*fixture(size), 2, 4, "init3", size)

    def test_fork_orders_are_balanced_and_unique(self):
        """Four forks cover each cell once and alternate rule order."""
        plan = list(selections(4, [0, 1, 2], ["init1", "init3", "flash"], [5, 15]))
        self.assertEqual(144, len(plan))
        self.assertEqual(len(plan), len(set(plan)))
        for fork in range(4):
            members = [item for item in plan if item[0] == fork]
            for index in range(0, len(members), 2):
                self.assertEqual(members[index][1:4], members[index + 1][1:4])
                self.assertEqual([2, 4] if fork % 2 == 0 else [4, 2],
                                 [members[index][-1], members[index + 1][-1]])

    def test_missing_and_duplicate_rosters_fail(self):
        """Dropping an entire round, phase or component cannot shrink the denominator."""
        for mutation in (lambda rows, states: rows.pop(),
                         lambda rows, states: states.pop(),
                         lambda rows, states: states.append(copy.copy(states[-1])),
                         lambda rows, states: states.__setitem__(slice(None),
                             [item for item in states if item["phase"] != 1]),
                         lambda rows, states: states.__setitem__(slice(None),
                             [item for item in states if item["component"] != "MEG"]),
                         lambda rows, states: rows.append(copy.copy(rows[-1]))):
            rows, states = fixture()
            mutation(rows, states)
            with self.assertRaises(ValueError):
                validate_evidence(rows, states, 2, 4, "init3", 5)


def receipt_fixture():
    """Build two exact raw receipts with hashes, independent of any Java benchmark."""
    files = {}
    manifest = dict(matrix=dict(forks=1, profiles=[2], sizes=[5], scenarios=["init3"]),
                    planned_jvms=2, finish_utc="hermetic-test", declared_source_head="a" * 40, runs=[])
    for rule in (2, 4):
        rows, states = fixture()
        for row in rows:
            row["rule"] = rule
        run = dict(fork=0, profile=2, scenario="init3", components=5, rule=rule, exit=0)
        files[stem(run) + ".log"] = b""
        for suffix, values, hash_key in ((".csv", rows, "csv_sha256"),
                                         (".csv.states.csv", states, "state_sha256")):
            stream = io.StringIO(newline="")
            writer = csv.DictWriter(stream, fieldnames=list(values[0]))
            writer.writeheader()
            writer.writerows(values)
            data = stream.getvalue().encode("utf-8")
            files[stem(run) + suffix] = data
            run[hash_key] = hashlib.sha256(data).hexdigest()
        manifest["runs"].append(run)
    return manifest, files


class SelectedPairArchiveTest(unittest.TestCase):
    """Retained raw data must preserve the complete frozen JVM denominator and bytes."""

    def test_retained_full_matrix(self):
        """Audit the real published archive rather than relying only on synthetic corruption tests."""
        import zipfile
        root = Path(__file__).resolve().parents[1] / "src/test/resources/neqsim/thermo/mixingrule/hv-selected-pair-performance"
        with zipfile.ZipFile(root / "raw.zip") as archive:
            manifest = json.loads(archive.read("execution.json"))
            self.assertEqual(dict(forks=4, profiles=[0, 1, 2], scenarios=["init1", "init3", "flash"], sizes=[5, 15]),
                             manifest["matrix"])
            result = audit(manifest, archive.read)
        self.assertEqual(144, result["jvms"])
        self.assertEqual(json.loads((root / "summary.json").read_text(encoding="utf-8")), result)

    def test_retained_profiles_and_native_endpoints(self):
        """Recompute CPU counts from complete exports and verify every recording/log/native receipt hash."""
        root = Path(__file__).resolve().parents[1] / "src/test/resources/neqsim/thermo/mixingrule/hv-selected-pair-performance"
        meta = json.loads((root / "profiles.json").read_text(encoding="utf-8"))
        self.assertTrue(meta["timing_excluded_from_matrix"])
        self.assertEqual({"init3", "flash"}, set(meta["cases"]))
        with zipfile.ZipFile(root / "profiles.zip") as archive:
            for scenario, case in meta["cases"].items():
                for name, expected_hash in case["files"].items():
                    self.assertEqual(expected_hash, hashlib.sha256(archive.read(name)).hexdigest())
                name = "pr2017-" + scenario
                self.assertEqual(case["cpu_samples"], profile_counts(json.loads(archive.read(name + ".json"))))
                rows = list(csv.DictReader(io.StringIO(archive.read(name + ".csv").decode("utf-8"))))
                states = list(csv.DictReader(io.StringIO(archive.read(name + ".csv.states.csv").decode("utf-8"))))
                validate_evidence(rows, states, 2, 4, scenario, 15)

    def test_cpu_stack_counting_does_not_double_count_inlined_frames(self):
        """One recursive/inlined method contributes at most once to each inclusive sample."""
        frame = dict(method=dict(type=dict(name="example/Gamma"), name="evaluate"))
        data = dict(recording=dict(events=[dict(type="jdk.ExecutionSample", values=dict(
            sampledThread=dict(javaName="main"), stackTrace=dict(frames=[frame, frame], truncated=False)))]))
        counts = profile_counts(data)["jdk.ExecutionSample"]
        self.assertEqual(1, counts["events"])
        self.assertEqual([["example.Gamma.evaluate", 1]], json.loads(json.dumps(counts["inclusive"])))
        with self.assertRaises(ValueError):
            profile_counts(dict(recording=dict(events=[])))
        data["recording"]["events"][0]["type"] = "unsupported"
        with self.assertRaises(ValueError):
            profile_counts(data)

    def test_complete_raw_receipts(self):
        """Summarize independent JVM centers without treating rounds as replications."""
        manifest, files = receipt_fixture()
        result = audit(manifest, files.__getitem__)
        self.assertEqual(2, result["jvms"])
        self.assertEqual(20, result["rounds"])
        self.assertEqual(40000, result["measured_operations"])
        self.assertEqual(1, result["cells"][0]["ratio_median"])

    def test_same_case_changed_endpoint_is_rejected(self):
        """A balanced but changed endpoint in another JVM cannot masquerade as identical work."""
        manifest, files = receipt_fixture()
        manifest["matrix"]["forks"] = 2
        manifest["planned_jvms"] = 4
        for previous in list(reversed(manifest["runs"])):
            run = dict(previous, fork=1)
            for suffix in (".csv", ".csv.states.csv", ".log"):
                files[stem(run) + suffix] = files[stem(previous) + suffix]
            manifest["runs"].append(run)
        self.assertEqual(0.0, audit(manifest, files.__getitem__)["max_same_case_beta_z_x_logphi_difference"])
        run = manifest["runs"][-1]
        name = stem(run) + ".csv.states.csv"
        states = list(csv.DictReader(io.StringIO(files[name].decode("utf-8"))))
        for state in states:
            state["logphi"] = .001
        stream = io.StringIO(newline="")
        writer = csv.DictWriter(stream, fieldnames=list(states[0]))
        writer.writeheader()
        writer.writerows(states)
        files[name] = stream.getvalue().encode("utf-8")
        run["state_sha256"] = hashlib.sha256(files[name]).hexdigest()
        with self.assertRaisesRegex(ValueError, "Changed same-case endpoint"):
            audit(manifest, files.__getitem__)

    def test_incomplete_duplicate_reordered_and_changed_hash_fail(self):
        """No missing JVM, failed process or changed byte can quietly enter a summary."""
        for mutation in (lambda m: m["runs"].pop(),
                         lambda m: m["runs"].reverse(),
                         lambda m: m["runs"].append(copy.copy(m["runs"][0])),
                         lambda m: m["runs"][1].update(exit=1),
                         lambda m: m["runs"][1].update(csv_sha256="0" * 64),
                         lambda m: m.pop("finish_utc")):
            manifest, files = receipt_fixture()
            mutation(manifest)
            with self.assertRaises(ValueError):
                audit(manifest, files.__getitem__)

    def test_raw_and_archive_cli_round_trip(self):
        """Execute both documented audit paths and require identical summaries."""
        manifest, files = receipt_fixture()
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            data = root / "raw"
            data.mkdir()
            (data / "execution.json").write_text(json.dumps(manifest), encoding="utf-8")
            for name, content in files.items():
                (data / name).write_bytes(content)
            script = str(Path(__file__).with_name("audit_huron_vidal_selected_pair.py"))
            archive = root / "raw.zip"
            first, second = root / "first.json", root / "second.json"
            subprocess.run([sys.executable, script, str(data), "--archive", str(archive), "--summary", str(first)],
                           check=True, capture_output=True)
            subprocess.run([sys.executable, script, str(archive), "--summary", str(second)],
                           check=True, capture_output=True)
            self.assertEqual(first.read_bytes(), second.read_bytes())

    def test_late_nonfinite_and_physical_corruption_fail(self):
        """NaN in a later row must not disappear inside a maximum reduction."""
        for target, fields in (("row", ["balance", "normalization", "log_fugacity", "beta0", "T_K"]),
                               ("state", ["beta", "z", "x", "logphi"])):
            for field in fields:
                for value in (float("nan"), float("inf"), -1):
                    rows, states = fixture()
                    (rows if target == "row" else states)[-1][field] = value
                    with self.assertRaises(ValueError, msg=(target, field, value)):
                        validate_evidence(rows, states, 2, 4, "init3", 5)

    def test_changed_work_and_identity_fail(self):
        """Keep source case and measured work aligned with each JVM receipt."""
        for field, value in (("profile", 0), ("rule", 2), ("scenario", "flash"), ("components", 15),
                             ("operations", 1000), ("elapsed_ns", 0), ("allocated_bytes", -2),
                             ("topology", "gas"), ("P_bara", 51)):
            rows, states = fixture()
            rows[-1][field] = value
            with self.assertRaises(ValueError):
                validate_evidence(rows, states, 2, 4, "init3", 5)

    def test_finite_balance_and_fugacity_failures(self):
        """The independent reconstruction catches finite but inconsistent endpoints."""
        for field, value in (("beta", .4), ("x", .1), ("z", .051), ("logphi", .01),
                             ("component", "unknown"), ("type", "oil"), ("round", 20)):
            rows, states = fixture()
            states[-1][field] = value
            with self.assertRaises(ValueError):
                validate_evidence(rows, states, 2, 4, "init3", 5)


if __name__ == "__main__":
    unittest.main()
