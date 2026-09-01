"""Tests for the dependency-light DEXPI documentation renderer."""

import importlib.util
import os
import sys
import tempfile
import unittest
from pathlib import Path
from textwrap import dedent


ROOT = Path(__file__).resolve().parent.parent
MODULE_PATH = ROOT / "examples" / "neqsim" / "render_engineering_pid.py"
SPEC = importlib.util.spec_from_file_location("render_engineering_pid", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class EngineeringPidRendererTest(unittest.TestCase):
    def test_reads_and_renders_native_dexpi_fixture(self):
        fixture = ROOT / "src" / "test" / "resources" / "dexpi" / "2.0" / "golden" / "branching-process.dexpi.xml"
        graph = MODULE.read_pid_graph(fixture)

        self.assertEqual("DEXPI_2_0_NATIVE", graph.source_profile)
        self.assertGreater(len(graph.nodes), 3)
        self.assertGreater(len(graph.edges), 1)
        self.assertTrue(any(node.category == "equipment" for node in graph.nodes.values()))

        with tempfile.TemporaryDirectory() as directory:
            png = Path(directory) / "pid.png"
            svg = Path(directory) / "pid.svg"
            stats = MODULE.render_pid(graph, png, svg, max_nodes=24)
            self.assertTrue(png.is_file())
            self.assertTrue(svg.is_file())
            self.assertGreater(png.stat().st_size, 1000)
            self.assertGreater(stats["displayedNodeCount"], 3)

    def test_reads_proteus_equipment_and_connections(self):
        proteus = dedent(
            """\
            <?xml version="1.0" encoding="UTF-8"?>
            <PlantModel xmlns="http://www.dexpi.org/ProteusSchema" SchemaVersion="4.1.1">
              <Equipment ID="ID-20-VG-001" ComponentClass="Separator">
                <GenericAttributes>
                  <GenericAttribute Name="TagName" Value="20-VG-001" />
                </GenericAttributes>
                <Association Type="connected to" ItemID="ID-20-XV-001" />
              </Equipment>
              <PipingComponent ID="ID-20-XV-001" ComponentClass="Valve">
                <GenericAttributes>
                  <GenericAttribute Name="TagName" Value="20-XV-001" />
                </GenericAttributes>
              </PipingComponent>
              <Connection FromID="ID-20-VG-001" ToID="ID-20-XV-001" />
            </PlantModel>
            """
        )
        with tempfile.TemporaryDirectory() as directory:
            fixture = Path(directory) / "plant-proteus.xml"
            fixture.write_text(proteus, encoding="utf-8")
            graph = MODULE.read_pid_graph(fixture)

        self.assertEqual("PROTEUS_4_1_1", graph.source_profile)
        self.assertEqual("20-VG-001", graph.nodes["ID-20-VG-001"].label)
        self.assertEqual("equipment", graph.nodes["ID-20-VG-001"].category)
        self.assertEqual("piping", graph.nodes["ID-20-XV-001"].category)
        self.assertTrue(any(edge[:2] == ("ID-20-VG-001", "ID-20-XV-001") for edge in graph.edges))


if __name__ == "__main__":
    os.environ.setdefault("MPLCONFIGDIR", "/tmp/neqsim-matplotlib")
    unittest.main()
