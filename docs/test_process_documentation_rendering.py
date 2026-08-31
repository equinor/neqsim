"""Contracts for curated process-equipment documentation rendering."""

from pathlib import Path
import re
import unittest


DOCS = Path(__file__).resolve().parent
SCOPE_PAGES = (
    DOCS / "process/README.md",
    DOCS / "process/equipment/LNGHeatExchanger.md",
    DOCS / "process/equipment/README.md",
    DOCS / "process/equipment/absorbers.md",
    DOCS / "process/equipment/adsorbers.md",
    DOCS / "process/equipment/adsorption_bed.md",
    DOCS / "process/equipment/battery_storage.md",
    DOCS / "process/equipment/black_oil_separator.md",
    DOCS / "process/equipment/compressor_antisurge_control.md",
    DOCS / "process/equipment/compressor_curves.md",
    DOCS / "process/equipment/compressor_shaft.md",
    DOCS / "process/equipment/compressors.md",
    DOCS / "process/equipment/control_valves.md",
    DOCS / "process/equipment/differential_pressure.md",
    DOCS / "process/equipment/distillation.md",
    DOCS / "process/equipment/ejectors.md",
    DOCS / "process/equipment/electrolyzers.md",
    DOCS / "process/equipment/energy_conversion.md",
    DOCS / "process/equipment/equipment_catalog.md",
    DOCS / "process/equipment/expanders.md",
    DOCS / "process/equipment/failure_modes.md",
    DOCS / "process/equipment/filters.md",
    DOCS / "process/equipment/flares.md",
    DOCS / "process/equipment/heat_exchangers.md",
    DOCS / "process/equipment/heat_integration.md",
    DOCS / "process/equipment/iron_sulfide_wall_source.md",
    DOCS / "process/equipment/looped_networks.md",
    DOCS / "process/equipment/manifold_design.md",
    DOCS / "process/equipment/manifolds.md",
    DOCS / "process/equipment/measurement_devices.md",
    DOCS / "process/equipment/membranes.md",
    DOCS / "process/equipment/mixers_splitters.md",
    DOCS / "process/equipment/multiphase_flow_correlations.md",
    DOCS / "process/equipment/multistream_heat_exchanger.md",
    DOCS / "process/equipment/networks.md",
    DOCS / "process/equipment/pipeline_simulation.md",
    DOCS / "process/equipment/pipelines.md",
    DOCS / "process/equipment/plug_flow_reactor.md",
    DOCS / "process/equipment/power_generation.md",
    DOCS / "process/equipment/production_well_networks.md",
    DOCS / "process/equipment/pumps.md",
    DOCS / "process/equipment/reactors.md",
    DOCS / "process/equipment/reservoirs.md",
    DOCS / "process/equipment/separator-entrainment-modeling.md",
    DOCS / "process/equipment/separators.md",
    DOCS / "process/equipment/solid_handling.md",
    DOCS / "process/equipment/streams.md",
    DOCS / "process/equipment/subsea_boosters.md",
    DOCS / "process/equipment/subsea_equipment.md",
    DOCS / "process/equipment/subsea_manifolds.md",
    DOCS / "process/equipment/subsea_systems.md",
    DOCS / "process/equipment/subsea_trees.md",
    DOCS / "process/equipment/tanks.md",
    DOCS / "process/equipment/umbilicals.md",
    DOCS / "process/equipment/util/README.md",
    DOCS / "process/equipment/util/adjusters.md",
    DOCS / "process/equipment/util/calculators.md",
    DOCS / "process/equipment/util/fuel_gas_system.md",
    DOCS / "process/equipment/util/produced_water_degassing.md",
    DOCS / "process/equipment/util/recycles.md",
    DOCS / "process/equipment/util/saturators.md",
    DOCS / "process/equipment/util/stream_fitters.md",
    DOCS / "process/equipment/util/utility_air_system.md",
    DOCS / "process/equipment/valves.md",
    DOCS / "process/equipment/vessel_depressurization.md",
    DOCS / "process/equipment/water_cooler_reboiler.md",
    DOCS / "process/equipment/water_treatment.md",
    DOCS / "process/equipment/well_allocation.md",
    DOCS / "process/equipment/wells.md",
    DOCS / "process/index.md",
    DOCS / "process/processmodel/DIAGRAM_ARCHITECTURE_DEXPI_SYNERGY.md",
    DOCS / "process/processmodel/README.md",
    DOCS / "process/processmodel/diagram_export.md",
    DOCS / "process/processmodel/graph_simulation.md",
    DOCS / "process/processmodel/low_flow_bypass.md",
    DOCS / "process/processmodel/parallel_scenario_sweeps.md",
    DOCS / "process/processmodel/process_model.md",
    DOCS / "process/processmodel/process_module.md",
    DOCS / "process/processmodel/process_system.md",
    DOCS / "process/processmodel/run_status.md",
)


def metadata_value(metadata, name):
    """Return a plain scalar, including folded YAML front-matter values."""
    prefix = name + ":"
    lines = metadata.splitlines()
    for index, line in enumerate(lines):
        if not line.startswith(prefix):
            continue
        value = line[len(prefix) :].strip()
        if value in {">", ">-", "|", "|-"}:
            parts = []
            for continuation in lines[index + 1 :]:
                if continuation and not continuation[0].isspace():
                    break
                if continuation.strip():
                    parts.append(continuation.strip())
            return " ".join(parts)
        return value.strip().strip("'\"")
    return ""


def parse_front_matter(source):
    """Return title, description, and body from one Markdown page."""
    match = re.match(r"\A---\n(?P<metadata>.*?)\n---\n(?P<body>.*)\Z", source, re.DOTALL)
    if match is None:
        raise AssertionError("Missing Jekyll front matter")
    metadata = match.group("metadata")
    return (
        metadata_value(metadata, "title"),
        metadata_value(metadata, "description"),
        match.group("body"),
    )


def remove_fenced_code(source):
    """Remove backtick and tilde code fences before inspecting rendered Markdown."""
    rendered = []
    marker = None
    for line in source.splitlines():
        stripped = line.lstrip()
        if marker is None and (stripped.startswith("~~~") or stripped.startswith("```")):
            marker = stripped[:3]
            continue
        if marker is not None:
            if stripped.startswith(marker):
                marker = None
            continue
        rendered.append(line)
    return "\n".join(rendered)


class ProcessDocumentationRenderingContractTest(unittest.TestCase):
    """Protect the curated process-equipment and flowsheet documentation surface."""

    def test_frozen_scope_exists(self):
        self.assertEqual(len(SCOPE_PAGES), 80)
        for page in SCOPE_PAGES:
            with self.subTest(page=page):
                self.assertTrue(page.is_file())

    def test_pages_have_searchable_front_matter(self):
        for page in SCOPE_PAGES:
            with self.subTest(page=page):
                title, description, _body = parse_front_matter(page.read_text(encoding="utf-8"))
                self.assertTrue(title)
                self.assertGreaterEqual(len(description.split()), 5)

    def test_front_matter_title_is_not_repeated_as_h1(self):
        for page in SCOPE_PAGES:
            with self.subTest(page=page):
                title, _description, body = parse_front_matter(page.read_text(encoding="utf-8"))
                rendered_markdown = remove_fenced_code(body)
                headings = re.findall(r"^#\s+(.+?)\s*$", rendered_markdown, re.MULTILINE)
                self.assertNotIn(title, headings)

    def test_pages_use_supported_math_delimiters(self):
        unsupported = re.compile(r"\\\[|\\\]|\\\(|\\\)")
        for page in SCOPE_PAGES:
            with self.subTest(page=page):
                _title, _description, body = parse_front_matter(
                    page.read_text(encoding="utf-8")
                )
                rendered_markdown = remove_fenced_code(body)
                self.assertIsNone(unsupported.search(rendered_markdown))


if __name__ == "__main__":
    unittest.main()
