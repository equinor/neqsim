"""Geometry-contract tests for the runnable JSON-to-mesh example."""

import copy
import importlib.util
import math
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "design_mesh_example", ROOT / "examples/mechanical_design_json_to_mesh.py"
)
EXAMPLE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(EXAMPLE)


class DesignMeshTest(unittest.TestCase):
    def setUp(self):
        self.data = {
            "schemaVersion": "1.0",
            "geometryKind": "cylindrical_shell",
            "geometryConsistency": "consistent",
            "orientation": "horizontal",
            "geometry": {
                name: {"value": value, "unit": "m", "status": "available"}
                for name, value in {
                    "innerDiameter": 1.0,
                    "outerDiameter": 1.04,
                    "wallThickness": 0.02,
                    "tangentLength": 3.0,
                    "moduleLength": 5.0,
                    "moduleWidth": 2.0,
                    "moduleHeight": 2.0,
                }.items()
            },
        }

    def test_shell_bounds_and_volume(self):
        mesh, report = EXAMPLE.build_mesh(self.data)
        for actual, expected in zip(mesh.extents, [3.0, 1.04, 1.04]):
            self.assertAlmostEqual(actual, expected, places=10)
        self.assertTrue(mesh.is_watertight)
        self.assertAlmostEqual(report["analyticVolume_m3"], math.pi * 0.0204 * 3.0)
        self.assertLess(report["relativeVolumeError"], 0.00011)
        self.assertFalse(report["fabricationReady"])

    def test_rejects_units_missing_values_and_inconsistent_geometry(self):
        for changed in [
            {"unit": "mm"},
            {"value": None, "status": "unavailable"},
            {"value": 20.0},
            {"value": float("nan")},
        ]:
            with self.subTest(changed=changed):
                data = copy.deepcopy(self.data)
                data["geometry"]["wallThickness"].update(changed)
                with self.assertRaises(ValueError):
                    EXAMPLE.build_mesh(data)

    def test_envelope_is_separate_from_pressure_shell(self):
        self.data["geometryKind"] = "compressor_envelope"
        with self.assertRaises(ValueError):
            EXAMPLE.build_mesh(self.data)
        mesh, report = EXAMPLE.build_mesh({"designData": self.data}, mode="envelope")
        self.assertAlmostEqual(mesh.volume, 20.0)
        self.assertIn("plot-space", report["representation"])


if __name__ == "__main__":
    unittest.main()
