#!/usr/bin/env python3
"""Generate the source-backed process-equipment documentation catalog."""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Mapping, Optional, Sequence, Set, Tuple


ROOT = Path(__file__).resolve().parent.parent
SOURCE_ROOT = ROOT / "src" / "main" / "java" / "neqsim" / "process" / "equipment"
CATALOG = ROOT / "docs" / "process" / "equipment" / "equipment_catalog.md"


@dataclass(frozen=True)
class JavaType:
    """Small source-level view of one public top-level Java type."""

    qualified_name: str
    simple_name: str
    package_name: str
    source_package: str
    kind: str
    is_abstract: bool
    superclass: Optional[str]
    interfaces: Tuple[str, ...]
    imports: Mapping[str, str]


PACKAGE_GUIDES: Mapping[str, Tuple[str, str]] = {
    "absorber": ("[Absorbers and strippers](absorbers)", "Gas absorption, stripping, amine, and TEG contactors"),
    "adsorber": ("[Adsorbers](adsorbers) and [adsorption beds](adsorption_bed)", "Adsorption beds, mercury removal, and PSA equipment"),
    "battery": ("[Battery storage](battery_storage)", "Electrical energy storage and balancing"),
    "blackoil": ("[Black-oil separation](black_oil_separator)", "Black-oil PVT separation in ProcessSystem"),
    "compressor": ("[Compressors](compressors)", "Compressors, trains, drivers, maps, and anti-surge models"),
    "diffpressure": ("[Differential-pressure equipment](differential_pressure)", "Orifice and differential-pressure flow equipment"),
    "distillation": ("[Distillation](distillation)", "Tray, packed, reactive, and shortcut columns"),
    "ejector": ("[Ejectors](ejectors)", "Motive/suction ejector equipment"),
    "electrolyzer": ("[Electrolyzers](electrolyzers)", "Water and carbon-dioxide electrolysis"),
    "energy": ("[Energy conversion equipment](energy_conversion)", "Motors, generators, converters, utility sources, and network solvers"),
    "expander": ("[Expanders](expanders)", "Turboexpanders and coupled expander-compressor units"),
    "filter": ("[Filters](filters)", "Particulate, charcoal, and sulfur filters"),
    "flare": ("[Flares](flares)", "Flare units and stacks"),
    "heatexchanger": ("[Heat exchangers](heat_exchangers)", "Heaters, coolers, exchangers, evaporators, and dryers"),
    "lng": ("[LNG cargo ageing](../lng-ageing)", "LNG storage, ageing, boil-off, and transport scenarios"),
    "manifold": ("[Manifolds](manifolds)", "Multi-inlet production and routing manifolds"),
    "membrane": ("[Membranes](membranes)", "Membrane separators"),
    "mixer": ("[Mixers and splitters](mixers_splitters)", "Equilibrium, static, non-equilibrium, and phase mixers"),
    "network": ("[Networks](networks)", "Pipe, looped, and well-flowline networks"),
    "pipeline": ("[Pipelines](pipelines)", "Steady and transient single-, two-, and multiphase pipelines"),
    "powergeneration": ("[Power generation](power_generation)", "Turbines, fuel cells, renewables, and combined-cycle systems"),
    "pump": ("[Pumps](pumps)", "Centrifugal, ESP, jet, and sucker-rod pumps"),
    "reactor": ("[Reactors](reactors)", "Equilibrium, kinetic, reforming, sulfur, and bioprocess reactors"),
    "reservoir": ("[Reservoirs and wells](reservoirs)", "Reservoir, inflow, surveillance, and well-system equipment"),
    "separator": ("[Separators](separators)", "Phase, solids, cryogenic, and extraction separators"),
    "solidhandling": ("[Solid handling](solid_handling)", "Biological feedstock preparation and solids handling"),
    "splitter": ("[Mixers and splitters](mixers_splitters)", "Flow, component, capture, and upgrading splitters"),
    "stream": ("[Streams](streams)", "Material, equilibrium, virtual, and diagnostic streams"),
    "subsea": ("[Subsea equipment](subsea_equipment)", "Trees, manifolds, boosters, jumpers, flowlines, and umbilicals"),
    "tank": ("[Tanks](tanks)", "Storage, LNG, and vessel-depressurization equipment"),
    "util": ("[Process utilities](util/)", "Adjusters, recycles, calculators, fitters, setters, and utility systems"),
    "valve": ("[Valves](valves)", "Control, shutdown, relief, rupture-disk, and throttling valves"),
    "watertreatment": ("[Water treatment](water_treatment)", "Hydrocyclone, flotation, and produced-water treatment trains"),
}


DECLARATION = re.compile(
    r"public\s+(?P<abstract>abstract\s+)?(?P<kind>class|interface)\s+"
    r"(?P<name>[A-Za-z_$][\w$]*)(?:\s+extends\s+(?P<extends>[\w.$]+))?"
    r"(?:\s+implements\s+(?P<implements>[^\{]+))?\s*\{",
    re.MULTILINE,
)
PACKAGE = re.compile(r"^package\s+([\w.]+);", re.MULTILINE)
IMPORT = re.compile(r"^import\s+([\w.]+);", re.MULTILINE)


def read_java_types() -> Dict[str, JavaType]:
    """Read public top-level classes and interfaces under the equipment package."""

    types: Dict[str, JavaType] = {}
    for path in sorted(SOURCE_ROOT.rglob("*.java")):
        text = path.read_text(encoding="utf-8-sig")
        package_match = PACKAGE.search(text)
        declaration = DECLARATION.search(text)
        if package_match is None or declaration is None:
            continue
        package_name = package_match.group(1)
        simple_name = declaration.group("name")
        imports = {
            qualified.rsplit(".", 1)[-1]: qualified for qualified in IMPORT.findall(text)
        }
        interfaces = tuple(
            value.strip().split("<", 1)[0]
            for value in (declaration.group("implements") or "").split(",")
            if value.strip()
        )
        relative = path.relative_to(SOURCE_ROOT)
        source_package = relative.parts[0] if len(relative.parts) > 1 else "(root)"
        qualified_name = package_name + "." + simple_name
        types[qualified_name] = JavaType(
            qualified_name=qualified_name,
            simple_name=simple_name,
            package_name=package_name,
            source_package=source_package,
            kind=declaration.group("kind"),
            is_abstract=bool(declaration.group("abstract")),
            superclass=declaration.group("extends"),
            interfaces=interfaces,
            imports=imports,
        )
    return types


def resolve_reference(reference: str, owner: JavaType, types: Mapping[str, JavaType]) -> str:
    """Resolve a source-level superclass or interface reference when possible."""

    plain = reference.strip().split("<", 1)[0]
    if "." in plain:
        return plain
    if plain in owner.imports:
        return owner.imports[plain]
    same_package = owner.package_name + "." + plain
    if same_package in types:
        return same_package
    matches = [name for name, java_type in types.items() if java_type.simple_name == plain]
    return matches[0] if len(matches) == 1 else plain


def concrete_equipment(types: Mapping[str, JavaType]) -> List[JavaType]:
    """Return every non-abstract class that implements the equipment contract."""

    equipment: Set[str] = {
        "neqsim.process.equipment.ProcessEquipmentInterface",
        "neqsim.process.equipment.ProcessEquipmentBaseClass",
    }
    changed = True
    while changed:
        changed = False
        for qualified_name, java_type in types.items():
            parents: Iterable[str] = ([java_type.superclass] if java_type.superclass else [])
            parents = list(parents) + list(java_type.interfaces)
            if qualified_name not in equipment and any(
                resolve_reference(parent, java_type, types) in equipment for parent in parents
            ):
                equipment.add(qualified_name)
                changed = True
    return sorted(
        (
            java_type
            for qualified_name, java_type in types.items()
            if qualified_name in equipment
            and java_type.kind == "class"
            and not java_type.is_abstract
            and java_type.source_package != "(root)"
        ),
        key=lambda java_type: (java_type.source_package, java_type.simple_name),
    )


def render_catalog(equipment: Sequence[JavaType]) -> str:
    """Render the complete Markdown catalog."""

    grouped: Dict[str, List[JavaType]] = {}
    for java_type in equipment:
        grouped.setdefault(java_type.source_package, []).append(java_type)
    missing_guides = sorted(set(grouped) - set(PACKAGE_GUIDES))
    if missing_guides:
        raise ValueError("Equipment source packages need documentation mappings: " + ", ".join(missing_guides))

    lines = [
        "---",
        'title: "Complete Process Equipment Catalog"',
        'description: "Source-backed catalog of every concrete NeqSim ProcessEquipmentInterface implementation, grouped by Java package and linked to its maintained guide."',
        "---",
        "",
        "This catalog is generated from `src/main/java/neqsim/process/equipment`. It lists every public,",
        "non-abstract class that implements `ProcessEquipmentInterface`, directly or through an equipment",
        "base class. Helper classes, result records, strategies, enums, and interfaces are intentionally",
        "excluded from the equipment count.",
        "",
        f"**Current source inventory:** {len(equipment)} concrete equipment classes in {len(grouped)} packages.",
        "",
        "Regenerate this page after adding or removing equipment:",
        "",
        "```text",
        "python devtools/generate_equipment_documentation_catalog.py",
        "```",
        "",
        "## Equipment by source package",
        "",
        "| Source package | Maintained guide | Concrete equipment classes |",
        "| --- | --- | --- |",
    ]
    for source_package in sorted(grouped):
        guide, purpose = PACKAGE_GUIDES[source_package]
        classes = ", ".join(f"`{java_type.simple_name}`" for java_type in grouped[source_package])
        lines.append(
            f"| `{source_package}` | {guide}<br>{purpose} | {classes} |"
        )

    lines.extend(
        [
            "",
            "## Equipment-adjacent framework packages",
            "",
            "These packages live below `neqsim.process.equipment` but provide shared contracts, metadata,",
            "or services rather than concrete process units, so they are not included in the equipment count.",
            "",
            "| Source package | Documentation | Role |",
            "| --- | --- | --- |",
            "| `capacity` | [Capacity constraint framework](../CAPACITY_CONSTRAINT_FRAMEWORK) | Capacity strategies, constraints, bottleneck results, and design data |",
            "| `failure` | [Failure modes](failure_modes) | Reliability and failure-mode metadata attached to equipment |",
            "| `iec81346` | [Engineering diagram and identification guide](../../integration/engineering-diagram-document-model) | IEC 81346 reference designations and automatic assignment |",
            "| `well` | [Well allocation](well_allocation) | Allocation results and production-allocation services |",
            "",
            "## Catalog boundary",
            "",
            "Controllers and measurement devices implement the broader `ProcessElementInterface` contract and",
            "are documented separately in [controllers](../controllers) and [measurement devices](measurement_devices).",
            "Mechanical-design calculators, thermodynamic systems, and process modules are likewise outside this",
            "equipment-class inventory even when they configure or consume equipment results.",
            "",
            "Return to the [equipment guide](./).",
            "",
        ]
    )
    return "\n".join(lines)


def expected_catalog() -> str:
    """Return the catalog content implied by the current Java source."""

    return render_catalog(concrete_equipment(read_java_types()))


def main(argv: Sequence[str] = ()) -> int:
    """Generate or check the catalog."""

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="fail when the committed catalog is stale")
    args = parser.parse_args(argv)
    expected = expected_catalog()
    if args.check:
        actual = CATALOG.read_text(encoding="utf-8-sig") if CATALOG.is_file() else ""
        if actual != expected:
            print(f"{CATALOG.relative_to(ROOT)} is stale; regenerate it", file=sys.stderr)
            return 1
        print(f"Equipment catalog is current: {len(concrete_equipment(read_java_types()))} concrete classes.")
        return 0
    CATALOG.write_text(expected, encoding="utf-8")
    print(f"Generated {CATALOG.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
