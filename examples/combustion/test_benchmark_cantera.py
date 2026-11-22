"""Cantera-backed fingerprint check for the combustion benchmark catalog."""

import json
from pathlib import Path
import unittest

from benchmark_qualification import validate_catalog
from cantera_backend import CanteraBackend


class BenchmarkCanteraTest(unittest.TestCase):
    def test_gri_demonstration_fingerprint_matches_resolved_mechanism(self):
        catalog_path = Path(__file__).with_name("benchmark_catalog.json")
        with catalog_path.open(encoding="utf-8") as catalog_file:
            catalog = validate_catalog(json.load(catalog_file))
        mechanism = next(
            item
            for item in catalog["mechanisms"]
            if item["id"] == "gri-mech-3.0-cantera-3.2"
        )
        backend = CanteraBackend("gri30.yaml")
        self.assertEqual(
            mechanism["reportedCanonicalSha256"],
            backend.mechanism_fingerprint,
        )


if __name__ == "__main__":
    unittest.main()
