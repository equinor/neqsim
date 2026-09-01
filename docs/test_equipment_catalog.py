"""Regression tests for complete process-equipment documentation coverage."""

import unittest

from devtools import generate_equipment_documentation_catalog as catalog


class EquipmentDocumentationCatalogTest(unittest.TestCase):
    def test_catalog_matches_current_equipment_source(self) -> None:
        self.assertEqual(
            catalog.CATALOG.read_text(encoding="utf-8-sig"),
            catalog.expected_catalog(),
        )

    def test_inventory_covers_representative_equipment_families(self) -> None:
        equipment = catalog.concrete_equipment(catalog.read_java_types())
        qualified_names = {java_type.qualified_name for java_type in equipment}

        self.assertGreater(len(equipment), 0)
        self.assertIn(
            "neqsim.process.equipment.blackoil.BlackOilSeparator",
            qualified_names,
        )
        self.assertIn(
            "neqsim.process.equipment.energy.EnergyNetworkSolver",
            qualified_names,
        )
        self.assertIn(
            "neqsim.process.equipment.solidhandling.BioFeedstockPreparation",
            qualified_names,
        )
        self.assertNotIn(
            "neqsim.process.equipment.capacity.CapacityConstraint",
            qualified_names,
        )


if __name__ == "__main__":
    unittest.main()
