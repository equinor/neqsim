"""Mutation guards for retained CO2/MEG measurements, not an EOS calibration."""

import copy
import csv
import hashlib
import io
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

from audit_huron_vidal_meg_receipts import ROOT, audit, parse_archive, reconcile


class MegReceiptTest(unittest.TestCase):
    """Preserve both source versions without repairing disputed observations."""

    def setUp(self):
        """Load the authentic source representations for independent mutations."""
        self.raw = (ROOT / "source.xml").read_bytes()
        self.csv = (ROOT / "manuscript.csv").read_bytes()

    def mutate_csv(self, change):
        """Serialize a semantic mutation without hiding behind a byte hash."""
        rows = list(csv.DictReader(io.StringIO(self.csv.decode())))
        change(rows)
        stream = io.StringIO(newline="")
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
        return stream.getvalue().encode()

    def test_complete_receipt_and_retained_discrepancies(self):
        """The accepted audit retains all80 entries and the four disagreements."""
        result = audit()
        self.assertEqual((80, 75, 7), tuple(result[k] for k in (
            "rows", "distinct_numeric_rows", "temperature_count")))
        self.assertEqual({"VLE": 52, "LLE": 28}, result["archive_phase_counts"])
        self.assertEqual([53, 54, 55, 56], result["phase_disagreement_rows"])
        self.assertEqual([[29, 33], [30, 34], [31, 35], [32, 36], [50, 51]],
                         result["duplicate_row_groups"])

    def test_identity_rejects_changed_xml_csv_and_self_rehashed_csv(self):
        """Exact provenance cannot be replaced by editing a receipt's own hash."""
        for filename in ("source.xml", "manuscript.csv"):
            with self.subTest(filename=filename), tempfile.TemporaryDirectory() as temp:
                directory = Path(temp)
                for name in ("source.xml", "manuscript.csv", "provenance.json"):
                    shutil.copyfile(ROOT / name, directory / name)
                path = directory / filename
                path.write_bytes(path.read_bytes() + b"\n")
                with self.assertRaises(ValueError):
                    audit(directory)
                if filename == "manuscript.csv":
                    metadata = json.loads((directory / "provenance.json").read_text())
                    metadata["manuscript_csv_sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
                    (directory / "provenance.json").write_text(json.dumps(metadata))
                    with self.assertRaises(ValueError):
                        audit(directory)

    def test_property_direction_units_identity_and_uncertainty(self):
        """Semantic checks reject a plausible but different physical quantity."""
        mutations = [(b"10.1021/acs.jced.7b00537", b"10.1021/other"),
                     (b"CURLTUGMZLYLDI-UHFFFAOYSA-N", b"LYCAIKOWRPUZTN-UHFFFAOYSA-N"),
                     (b"Pressure, kPa", b"Pressure, MPa"),
                     (b"<nOrgNum>2</nOrgNum>\n        </RegNum>",
                      b"<nOrgNum>1</nOrgNum>\n        </RegNum>"),
                     (b"<nCombUncertLevOfConfid>95", b"<nCombUncertLevOfConfid>68"),
                     (b"Data file compiler", b"Original author"),
                     (b"<nPropValue>0.0069", b"<nPropValue>NaN"),
                     (b"<nCombExpandUncertValue>0.0037", b"<nCombExpandUncertValue>-0.0037")]
        for before, after in mutations:
            with self.subTest(before=before):
                changed = self.raw.replace(before, after, 1)
                self.assertNotEqual(changed, self.raw)
                with self.assertRaises(ValueError):
                    parse_archive(changed)

    def test_missing_reordered_changed_and_deduplicated_rows(self):
        """No average, deduplication, row drop or changed value is accepted."""
        for change in (lambda rows: rows.pop(), lambda rows: rows.reverse(),
                       lambda rows: rows[0].update(x_CO2="0.007"),
                       lambda rows: rows.pop(32)):
            with self.subTest(change=change), self.assertRaises(ValueError):
                reconcile(parse_archive(self.raw), self.mutate_csv(change))

    def test_silent_phase_repair_or_reversed_direction_is_rejected(self):
        """Preserving a disagreement is a gate, not permission to guess a phase."""
        for change in (lambda rows: rows[52].update(phase_as_extracted="LLE"),
                       lambda rows: rows[0].update(phase_as_extracted="LLE")):
            with self.assertRaises(ValueError):
                reconcile(parse_archive(self.raw), self.mutate_csv(change))

    def test_metadata_cannot_promote_or_conflate_uncertainty(self):
        """Staging and standard-versus-expanded uncertainty remain explicit."""
        original = json.loads((ROOT / "provenance.json").read_text())
        for field, new in (("status", "qualified"), ("parameter_activation", True),
                           ("archive_uncertainty", "author standard relative uncertainty"),
                           ("author_standard_uncertainties", {"relative_x": "0.052"}),
                           ("archive", dict(original["archive"], git_blob="0" * 40)),
                           ("missing", [])):
            with self.subTest(field=field), tempfile.TemporaryDirectory() as temp:
                directory = Path(temp)
                for name in ("source.xml", "manuscript.csv"):
                    shutil.copyfile(ROOT / name, directory / name)
                metadata = copy.deepcopy(original)
                metadata[field] = new
                (directory / "provenance.json").write_text(json.dumps(metadata))
                with self.assertRaises(ValueError):
                    audit(directory)

    def test_executable_default_is_read_only_and_works_under_optimization(self):
        """Run the documented CLI in a separate interpreter with all gates active."""
        paths = [ROOT / name for name in ("source.xml", "manuscript.csv", "provenance.json")]
        before = [p.read_bytes() for p in paths]
        script = Path(__file__).with_name("audit_huron_vidal_meg_receipts.py")
        result = subprocess.run([sys.executable, "-O", str(script)], check=True,
                                capture_output=True, text=True)
        self.assertEqual(audit(), json.loads(result.stdout))
        self.assertEqual(before, [p.read_bytes() for p in paths])


if __name__ == "__main__":
    unittest.main()
