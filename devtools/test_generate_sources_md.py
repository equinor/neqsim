"""Regression coverage for source attribution and the generated evidence bundle."""

import json
import tempfile
import unittest
from pathlib import Path

import generate_sources_md as sources


class SourceCollectionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.task = Path(self.temp.name)
        self.refs = self.task / "step1_scope_and_research" / "references"
        self.refs.mkdir(parents=True)

    def add_file(self, name):
        path = self.refs / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("Synthetic reference", encoding="utf-8")
        return path

    def test_equipment_portal_is_emitted_once_in_all_outputs(self):
        self.add_file("equipment-portal/reference_prices.pdf")
        for _ in range(2):
            self.assertEqual(sources.main([str(self.task)]), 0)
            record = json.loads((self.refs / "collection_manifest.json").read_text())
            self.assertEqual(record["totals"], {"documents": 1, "sources": 1})
            self.assertEqual(len(record["sources"]), 1)
            markdown = (self.refs / "SOURCES.md").read_text()
            self.assertEqual(markdown.count("## Equipment Portal\n"), 1)
            self.assertEqual(markdown.count("`equipment-portal/reference_prices.pdf`"), 1)
            evidence = json.loads(
                (self.refs.parent / "document_evidence_manifest.json").read_text()
            )
            self.assertEqual(evidence["totals"]["files"], 1)
            self.assertEqual(len(evidence["sources"]), 1)

    def test_loose_source_exports_are_organized_before_generic_name_rules(self):
        cases = {
            "Equipment_Portal_U100_datasheet.html": "equipment-portal",
            "equipment-portal_reference_prices.pdf": "equipment-portal",
            "Omnia_Maps_routes.json": "omnia-maps",
            "SEMI_manual.pdf": "semi",
            "semi.equinor.com_catalogue.html": "semi",
            "SMI-123_datasheet.pdf": "semi",
            "pump_SDI-456_datasheet.pdf": "semi",
            "pump_SMI_123_manual.pdf": "semi",
        }
        for name in cases:
            self.add_file(name)
        self.assertEqual(dict(sources.organize(self.task, self.refs, "other")), cases)
        record = sources.scan_references(self.task, self.refs)
        self.assertEqual(record["totals"]["documents"], len(cases))
        for name, source in cases.items():
            self.assertTrue((self.refs / source / name).is_file())

    def test_aliases_and_explicit_provenance_take_precedence(self):
        cases = {
            "equipment_portal/SMI-123.pdf": "equipment-portal",
            "omnia_maps/routes.json": "omnia-maps",
            "manual/SEMI_manual.pdf": "manual",
            "generic.pdf": "equipment-portal",
            "routes.json": "omnia-maps",
            "SEMI_datasheet.pdf": "vendor",
        }
        for name in cases:
            self.add_file(name)
        (self.refs / "manifest.json").write_text(json.dumps({"documents": [
            {"file": "generic.pdf", "source": "equipment_portal"},
            {"file": "routes.json", "source": "omnia_maps"},
            {"file": "SEMI_datasheet.pdf", "source": "vendor"},
        ]}), encoding="utf-8")
        record = sources.scan_references(self.task, self.refs)
        actual = {doc["file"]: src["source"]
                  for src in record["sources"] for doc in src["documents"]}
        self.assertEqual(actual, cases)
        moves = dict(sources.organize(self.task, self.refs, "other"))
        self.assertEqual(moves["generic.pdf"], "equipment-portal")
        self.assertEqual(moves["routes.json"], "omnia-maps")
        self.assertEqual(moves["SEMI_datasheet.pdf"], "vendor")

    def test_existing_sources_and_unrelated_names_keep_their_classification(self):
        for name, expected in {
            "TR2000_pipe_class.pdf": "tr2000",
            "STID_datasheet.pdf": "stid",
            "vendor_manual.pdf": "vendor",
            "semiconductor.pdf": "",
            "seismic_survey.pdf": "",
        }.items():
            with self.subTest(name=name):
                self.assertEqual(sources._infer_source_from_name(name), expected)


if __name__ == "__main__":
    unittest.main()
