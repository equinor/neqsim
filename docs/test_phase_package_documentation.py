"""Source-anchored regression contracts for the phase-package guide."""

import re
from pathlib import Path
from urllib.parse import unquote


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs" / "thermo" / "phase" / "README.md"
PHASE_INTERFACE = (
    ROOT / "src" / "main" / "java" / "neqsim" / "thermo" / "phase"
    / "PhaseInterface.java"
)
PHASE_TYPE = (
    ROOT / "src" / "main" / "java" / "neqsim" / "thermo" / "phase"
    / "PhaseType.java"
)
SYSTEM_INTERFACE = (
    ROOT / "src" / "main" / "java" / "neqsim" / "thermo" / "system"
    / "SystemInterface.java"
)
THERMODYNAMIC_OPERATIONS = (
    ROOT / "src" / "main" / "java" / "neqsim"
    / "thermodynamicoperations" / "ThermodynamicOperations.java"
)


def read(path: Path) -> str:
    """Read one repository text file."""

    return path.read_text(encoding="utf-8")


def heading_slugs(content: str) -> set[str]:
    """Return GitHub-style slugs for Markdown headings outside code fences."""

    prose = re.sub(r"```.*?```", "", content, flags=re.DOTALL)
    return {
        re.sub(r"[^a-z0-9 -]", "", heading.lower())
        .strip()
        .replace(" ", "-")
        for heading in re.findall(
            r"^#{1,6}\s+(.+)$",
            prose,
            flags=re.MULTILINE,
        )
    }


def resolve_internal_target(source_path: Path, destination: str) -> tuple[Path, str]:
    """Resolve one explicit Markdown source link."""

    target, _, fragment = unquote(destination).partition("#")
    if not target:
        return source_path, fragment
    target_path = (source_path.parent / target).resolve()
    if not target_path.is_file():
        raise AssertionError(
            "Unresolved link from {}: {}".format(source_path, destination)
        )
    return target_path, fragment


def test_structure_links_and_logging_are_source_safe() -> None:
    """Keep the canonical page renderable, navigable, and repository compliant."""

    guide = read(GUIDE)
    prose = re.sub(r"```.*?```", "", guide, flags=re.DOTALL)
    markdown_links = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")

    assert guide.startswith("---\n")
    assert guide.count("```") % 2 == 0
    assert not re.search(r"^# ", prose, flags=re.MULTILINE)
    assert "System.out" not in guide
    assert "System.err" not in guide
    assert "LogManager.getLogger" in guide
    assert "logger.info(" in guide

    for destination in markdown_links.findall(guide):
        if destination.startswith(("http://", "https://", "mailto:")):
            continue
        target, _, fragment = destination.partition("#")
        if target:
            assert target.endswith(".md")
        target_path, resolved_fragment = resolve_internal_target(
            GUIDE,
            destination,
        )
        if fragment:
            assert resolved_fragment in heading_slugs(read(target_path))


def test_phase_fraction_ownership_matches_current_interfaces() -> None:
    """Mole, volume, and mass fractions must be assigned to their real owners."""

    guide = read(GUIDE)
    phase_source = read(PHASE_INTERFACE)
    system_source = read(SYSTEM_INTERFACE)

    assert "public double getBeta();" in phase_source
    assert "public double getBeta(int phaseNum);" in system_source
    assert "public double getVolumeFraction(int phaseNumber);" in system_source
    assert "public double getWtFraction(int phaseNumber);" in system_source

    for contract in (
        "phase.getBeta()",
        "fluid.getBeta(index)",
        "fluid.getVolumeFraction(index)",
        "fluid.getWtFraction(index)",
    ):
        assert contract in guide

    assert "PhaseInterface.getBetaV()" in guide
    assert "There is no `PhaseInterface.getBetaV()`" in guide


def test_initialization_and_property_boundary_is_source_anchored() -> None:
    """Transport-property guidance must use supported initialization and getters."""

    guide = read(GUIDE)
    phase_source = read(PHASE_INTERFACE)
    system_source = read(SYSTEM_INTERFACE)

    assert "public default void initProperties()" in system_source
    for signature in (
        "public double getDensity(String unit);",
        "public double getViscosity(String unit);",
        "public double getThermalConductivity(String unit);",
        "public double getMolarVolume(String unit);",
        "public double getFugacity(String compName);",
        "public double getActivityCoefficient(int k);",
        "public double getpH();",
    ):
        assert signature in phase_source

    for documented_call in (
        "operations.TPflash();",
        "fluid.initProperties();",
        'phase.getDensity("kg/m3")',
        'phase.getViscosity("cP")',
        'phase.getThermalConductivity("W/mK")',
    ):
        assert documented_call in guide


def test_phase_types_and_lookup_match_current_source() -> None:
    """Document stable enum names/descriptors and avoid deprecated numeric identity."""

    guide = read(GUIDE)
    phase_type_source = read(PHASE_TYPE)
    system_source = read(SYSTEM_INTERFACE)
    expected = {
        "LIQUID": "liquid",
        "GAS": "gas",
        "OIL": "oil",
        "AQUEOUS": "aqueous",
        "HYDRATE": "gas hydrate",
        "WAX": "wax",
        "SOLID": "solid",
        "SOLIDCOMPLEX": "solidComplex",
        "ASPHALTENE": "asphaltene",
        "LIQUID_ASPHALTENE": "asphaltene liquid",
    }

    for name, descriptor in expected.items():
        assert re.search(
            r"\b{}\(\"{}\",\s*\d+\)".format(name, descriptor),
            phase_type_source,
        )
        assert "| `{}` | `{}` |".format(name, descriptor) in guide

    assert "public PhaseInterface getPhase(PhaseType pt);" in system_source
    assert "public boolean hasPhaseType(PhaseType pt);" in system_source
    assert "fluid.getPhase(PhaseType.GAS)" in guide
    assert "fluid.hasPhaseType(PhaseType.GAS)" in guide
    assert "deprecated" in guide
    assert "| Value |" not in guide


def test_special_phase_boundaries_use_current_system_api() -> None:
    """Hydrate, solid, wax, and asphaltene guidance must avoid invented helpers."""

    guide = read(GUIDE)
    system_source = read(SYSTEM_INTERFACE)
    operations_source = read(THERMODYNAMIC_OPERATIONS)

    for method in (
        "hasHydratePhase()",
        "getHydratePhase()",
        "getHydrateFraction()",
        "setSolidPhaseCheck(",
        "setMultiphaseWaxCheck(",
    ):
        assert method in system_source

    assert "public void hydrateTPflash()" in operations_source

    for contract in (
        "fluid.hasHydratePhase()",
        "fluid.getHydratePhase()",
        "fluid.getHydrateFraction()",
        "PhaseType.WAX",
        "PhaseType.SOLID",
        "PhaseType.ASPHALTENE",
        "PhaseType.LIQUID_ASPHALTENE",
    ):
        assert contract in guide


def test_stale_or_model_specific_api_patterns_do_not_return() -> None:
    """Reject the invalid and misleading patterns removed from the guide."""

    guide = read(GUIDE)
    stale_patterns = (
        ".getBetaV()",
        ".getDiffusionCoefficient(\"m2/s\")",
        "getNumberOfLiquidPhases()",
        "getSolidPhase()",
        ".hasHydrate()",
        "getHydrateTemperature()",
        ".setWaxCheck(",
        ".hasWax()",
        ".getActivity()",
        ".getIonicStrength()",
        ".isPhaseStable()",
        ".getdZdT()",
        ".getdZdP()",
        ".getdfugdT()",
        ".getdfugdP()",
        ".getExcessEnthalpy()",
        ".getExcessEntropy()",
        ".getExcessVolume()",
        "(PhaseGasEos)",
        "(PhaseGasCPA)",
        "(PhaseGasPCSAFT)",
        "~1150",
        "~10,000",
    )
    for pattern in stale_patterns:
        assert pattern not in guide


def test_complete_example_calls_are_exercised_by_java_regression() -> None:
    """The guide and executable regression must retain the same public calls."""

    guide = read(GUIDE)
    regression = read(
        ROOT
        / "src"
        / "test"
        / "java"
        / "neqsim"
        / "thermo"
        / "phase"
        / "PhasePackageDocumentationTest.java"
    )

    for call in (
        'new SystemSrkEos(300.0, 50.0)',
        'fluid.setMixingRule("classic")',
        "operations.TPflash()",
        "fluid.initProperties()",
        "fluid.getNumberOfPhases()",
        "fluid.getPhase(phaseIndex)",
        "phase.getBeta()",
        "fluid.getVolumeFraction(phaseIndex)",
        "fluid.getWtFraction(phaseIndex)",
        'phase.getDensity("kg/m3")',
        'phase.getViscosity("cP")',
        'phase.getThermalConductivity("W/mK")',
    ):
        assert call in guide
        assert call in regression
