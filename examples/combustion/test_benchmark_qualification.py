"""Regression tests for fail-closed combustion qualification provenance."""

import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from benchmark_qualification import (
    QualificationInputError,
    load_catalog,
    qualification_readiness,
    validate_catalog,
    verify_mechanism,
)


CATALOG_PATH = Path(__file__).with_name("benchmark_catalog.json")
QUANTITATIVE_ID = "cong-bedjanian-dagaut-2010-ethylene-jsr-phi-0.5"


class BenchmarkQualificationTest(unittest.TestCase):
    def setUp(self):
        with CATALOG_PATH.open(encoding="utf-8") as catalog_file:
            self.catalog = json.load(catalog_file)

    def test_public_catalog_has_executable_c2_input_evidence(self):
        catalog = load_catalog(CATALOG_PATH)
        readiness = qualification_readiness(catalog)
        self.assertTrue(readiness["eligibleForQuantitativeQualification"])
        self.assertEqual(readiness["quantitativeBenchmarks"], [QUANTITATIVE_ID])
        self.assertEqual(
            readiness["qualificationMechanisms"],
            ["creck-s-2.0.0-zenodo-22982859"],
        )
        self.assertEqual(readiness["gaps"], [])

    def test_respecth_record_preserves_provenance_basis_and_uncertainty_origin(self):
        catalog = load_catalog(CATALOG_PATH)
        benchmark = next(
            item for item in catalog["benchmarks"] if item["id"] == QUANTITATIVE_ID
        )
        self.assertEqual(len(benchmark["observations"]), 7)
        self.assertEqual(benchmark["dataSource"]["fileDoi"], "10.24388/x00014002")
        self.assertEqual(
            benchmark["dataSource"]["sha256"],
            "e4546690562c936314416762cee143ccb03d0b3cd832d477d962e53d962d0f6e",
        )
        self.assertEqual(benchmark["dataSource"]["licenseSpdxId"], "CC-BY-4.0")
        self.assertFalse(benchmark["uncertainty"]["reported"])
        self.assertEqual(benchmark["uncertainty"]["sourceType"], "estimated")
        self.assertIn("no dry or reference-O2 correction", benchmark["measurementBasis"])

    def test_creck_s_candidate_has_source_locked_provenance(self):
        catalog = load_catalog(CATALOG_PATH)
        mechanism = next(
            item
            for item in catalog["mechanisms"]
            if item["id"] == "creck-s-2.0.0-zenodo-22982859"
        )
        self.assertEqual(mechanism["releaseDoi"], "10.5281/zenodo.22982859")
        self.assertEqual(mechanism["releaseDate"], "2026-09-26")
        self.assertEqual(mechanism["expectedSizeBytes"], 495804)
        self.assertEqual(mechanism["speciesCount"], 209)
        self.assertEqual(mechanism["reactionCount"], 2816)
        self.assertEqual(mechanism["license"]["spdxId"], "CC-BY-4.0")

    def test_conditions_only_record_cannot_smuggle_observations(self):
        catalog = copy.deepcopy(self.catalog)
        catalog["benchmarks"][0]["observations"] = [{"temperatureK": 1000.0}]
        with self.assertRaisesRegex(QualificationInputError, "cannot contain observations"):
            validate_catalog(catalog)

    def test_quantitative_record_requires_verified_data_rights(self):
        catalog = copy.deepcopy(self.catalog)
        benchmark = next(item for item in catalog["benchmarks"] if item["id"] == QUANTITATIVE_ID)
        benchmark["dataSource"]["redistributionVerified"] = False
        with self.assertRaisesRegex(QualificationInputError, "data rights are unverified"):
            validate_catalog(catalog)

    def test_estimated_uncertainty_is_accepted_but_not_mislabeled_as_reported(self):
        catalog = copy.deepcopy(self.catalog)
        benchmark = next(item for item in catalog["benchmarks"] if item["id"] == QUANTITATIVE_ID)
        benchmark["uncertainty"]["sourceType"] = "unknown"
        with self.assertRaisesRegex(QualificationInputError, "source type is invalid"):
            validate_catalog(catalog)

    def test_quantitative_uncertainty_must_cover_every_value(self):
        catalog = copy.deepcopy(self.catalog)
        benchmark = next(item for item in catalog["benchmarks"] if item["id"] == QUANTITATIVE_ID)
        del benchmark["observations"][0]["uncertainties"]["CO2"]
        with self.assertRaisesRegex(QualificationInputError, "uncertainty coverage"):
            validate_catalog(catalog)

    def test_fingerprints_must_be_lowercase_hex_not_only_correct_length(self):
        catalog = copy.deepcopy(self.catalog)
        benchmark = next(item for item in catalog["benchmarks"] if item["id"] == QUANTITATIVE_ID)
        benchmark["dataSource"]["sha256"] = "z" * 64
        with self.assertRaisesRegex(QualificationInputError, "data SHA-256 is invalid"):
            validate_catalog(catalog)

    def test_fuel_composition_must_sum_to_one(self):
        catalog = copy.deepcopy(self.catalog)
        catalog["benchmarks"][0]["conditions"]["fuelMoleFractions"]["C3H8"] = 0.3
        with self.assertRaisesRegex(QualificationInputError, "must sum to one"):
            validate_catalog(catalog)

    def test_exact_mechanism_bytes_are_verified(self):
        payload = b"exact mechanism bytes\n"
        mechanism = {
            "id": "licensed-test-mechanism",
            "role": "qualification-candidate",
            "sourceUrl": "https://example.invalid/mechanism.yaml",
            "expectedSha256": hashlib.sha256(payload).hexdigest(),
            "license": {"redistributionVerified": True, "spdxId": "CC-BY-4.0"},
        }
        with tempfile.TemporaryDirectory() as temporary_directory:
            mechanism_path = Path(temporary_directory) / "mechanism.yaml"
            mechanism_path.write_bytes(payload)
            self.assertEqual(
                verify_mechanism(mechanism, mechanism_path, for_qualification=True),
                mechanism["expectedSha256"],
            )
            mechanism_path.write_bytes(payload + b"changed")
            with self.assertRaisesRegex(QualificationInputError, "fingerprint mismatch"):
                verify_mechanism(mechanism, mechanism_path, for_qualification=True)

    def test_demonstration_mechanism_cannot_qualify(self):
        payload = b"demonstration mechanism\n"
        mechanism = copy.deepcopy(self.catalog["mechanisms"][0])
        mechanism["expectedSha256"] = hashlib.sha256(payload).hexdigest()
        with tempfile.TemporaryDirectory() as temporary_directory:
            mechanism_path = Path(temporary_directory) / "mechanism.yaml"
            mechanism_path.write_bytes(payload)
            with self.assertRaisesRegex(QualificationInputError, "only a software demonstration"):
                verify_mechanism(mechanism, mechanism_path, for_qualification=True)


if __name__ == "__main__":
    unittest.main()
