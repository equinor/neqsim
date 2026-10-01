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


class BenchmarkQualificationTest(unittest.TestCase):
    def setUp(self):
        with CATALOG_PATH.open(encoding="utf-8") as catalog_file:
            self.catalog = json.load(catalog_file)

    def test_public_catalog_is_valid_but_not_quantitatively_qualified(self):
        catalog = load_catalog(CATALOG_PATH)
        readiness = qualification_readiness(catalog)
        self.assertFalse(readiness["eligibleForQuantitativeQualification"])
        self.assertEqual(readiness["quantitativeBenchmarks"], [])
        self.assertEqual(readiness["qualificationMechanisms"], [])
        self.assertEqual(len(readiness["gaps"]), 2)

    def test_conditions_only_record_cannot_smuggle_observations(self):
        catalog = copy.deepcopy(self.catalog)
        catalog["benchmarks"][0]["observations"] = [{"temperatureK": 1000.0}]
        with self.assertRaisesRegex(QualificationInputError, "cannot contain observations"):
            validate_catalog(catalog)

    def test_quantitative_record_requires_verified_data_rights(self):
        catalog = copy.deepcopy(self.catalog)
        benchmark = catalog["benchmarks"][0]
        benchmark["dataAvailability"] = "quantitative"
        benchmark["observations"] = [
            {
                "temperatureK": 1000.0,
                "equivalenceRatio": 1.0,
                "values": {"CO": 1.0e-4},
                "uncertainties": {"CO": 1.0e-5}
            }
        ]
        benchmark["dataSource"] = {
            "url": "https://example.invalid/data.csv",
            "licenseSpdxId": "CC-BY-4.0",
            "redistributionVerified": False,
            "sha256": "0" * 64
        }
        benchmark["uncertainty"]["reported"] = True
        with self.assertRaisesRegex(QualificationInputError, "data rights are unverified"):
            validate_catalog(catalog)

    def test_quantitative_uncertainty_must_cover_every_value(self):
        catalog = copy.deepcopy(self.catalog)
        benchmark = catalog["benchmarks"][0]
        benchmark["dataAvailability"] = "quantitative"
        benchmark["observations"] = [
            {
                "temperatureK": 1000.0,
                "equivalenceRatio": 1.0,
                "values": {"CO": 1.0e-4, "CO2": 2.0e-3},
                "uncertainties": {"CO": 1.0e-5}
            }
        ]
        benchmark["dataSource"] = {
            "url": "https://example.invalid/data.csv",
            "licenseSpdxId": "CC-BY-4.0",
            "redistributionVerified": True,
            "sha256": "0" * 64
        }
        benchmark["uncertainty"]["reported"] = True
        with self.assertRaisesRegex(QualificationInputError, "uncertainty coverage"):
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
