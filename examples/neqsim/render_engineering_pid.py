#!/usr/bin/env python3
"""Render native DEXPI 2.0 or Proteus XML as a deterministic P&ID overview.

The renderer deliberately uses only the Python standard library and matplotlib. It is
therefore suitable for documentation, CI and notebook previews where pyDEXPI or
Graphviz are unavailable. It does not replace a CAE product or the richer pyDEXPI
renderer; it visualizes the identities and references that NeqSim qualifies during
the internal DEXPI export/reimport check.
"""

from __future__ import annotations

import argparse
import math
import os
import re
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, Iterable, List, Mapping, MutableMapping, Sequence, Set, Tuple


@dataclass
class PidNode:
    """One renderable DEXPI object."""

    identity: str
    label: str
    category: str
    dexpi_type: str


@dataclass
class PidGraph:
    """Minimal exchange-format-neutral P&ID graph used by the renderer."""

    nodes: MutableMapping[str, PidNode] = field(default_factory=dict)
    edges: Set[Tuple[str, str, str]] = field(default_factory=set)
    source_profile: str = "UNKNOWN"

    def add_edge(self, source: str, target: str, role: str) -> None:
        if source in self.nodes and target in self.nodes and source != target:
            self.edges.add((source, target, role))


def _local(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def _clean_label(value: str) -> str:
    value = re.sub(r"^(Equipment|PipingNode|PipingComponent|ProcessInstrumentationFunction)[_-]?", "", value)
    value = value.replace("_N_", "-").replace("_", "-")
    return value[:32]


def _direct_data(element: ET.Element, property_name: str) -> str:
    for child in list(element):
        if _local(child.tag) == "Data" and child.get("property") == property_name:
            return "".join(child.itertext()).strip()
    return ""


def _category(dexpi_type: str) -> str:
    lowered = dexpi_type.lower()
    if "instrument" in lowered or "signal" in lowered:
        return "instrument"
    if "nozzle" in lowered or "pipingnode" in lowered or "port" in lowered:
        return "connection"
    if "valve" in lowered or "piping" in lowered or "pipe" in lowered:
        return "piping"
    if "equipment" in lowered or "separator" in lowered or "compressor" in lowered:
        return "equipment"
    return "other"


def read_native_dexpi(path: Path) -> PidGraph:
    """Read the native DEXPI 2.0 object/reference representation."""

    root = ET.parse(path).getroot()
    result = PidGraph(source_profile="DEXPI_2_0_NATIVE")
    objects: Dict[str, ET.Element] = {}
    for element in root.iter():
        if _local(element.tag) != "Object" or not element.get("id"):
            continue
        identity = element.get("id", "").strip()
        dexpi_type = element.get("type", "Object")
        category = _category(dexpi_type)
        if category == "other":
            continue
        label = (
            _direct_data(element, "TagName")
            or _direct_data(element, "ProcessInstrumentationFunctionNumber")
            or _direct_data(element, "SubTagName")
            or _clean_label(identity)
        )
        result.nodes[identity] = PidNode(identity, label, category, dexpi_type)
        objects[identity] = element

    for source, element in objects.items():
        for reference in element.iter():
            if _local(reference.tag) != "References":
                continue
            role = reference.get("property", "reference")
            for target in reference.get("objects", "").split():
                result.add_edge(source, target.lstrip("#"), role)
    return result


def _proteus_attribute(element: ET.Element, name: str) -> str:
    for child in element.iter():
        if _local(child.tag) == "GenericAttribute" and child.get("Name") == name:
            return child.get("Value", "").strip()
    return ""


def read_proteus(path: Path) -> PidGraph:
    """Read the graphical Proteus representation used by NeqSim and pyDEXPI."""

    root = ET.parse(path).getroot()
    result = PidGraph(source_profile="PROTEUS_4_1_1")
    renderable = {
        "Equipment",
        "PipingComponent",
        "ProcessInstrumentationFunction",
        "ProcessSignalGeneratingFunction",
        "Nozzle",
    }
    elements: Dict[str, ET.Element] = {}
    for element in root.iter():
        kind = _local(element.tag)
        identity = element.get("ID", "").strip()
        if kind not in renderable or not identity or identity.startswith("TaggedPlantItemShape"):
            continue
        dexpi_type = element.get("ComponentClass", kind)
        label = _proteus_attribute(element, "TagName") or _clean_label(identity.removeprefix("ID-"))
        result.nodes[identity] = PidNode(identity, label, _category(dexpi_type + " " + kind), dexpi_type)
        elements[identity] = element

    for connection in root.iter():
        if _local(connection.tag) == "Connection":
            result.add_edge(connection.get("FromID", ""), connection.get("ToID", ""), "connection")
    for source, element in elements.items():
        for association in element.iter():
            if _local(association.tag) == "Association":
                result.add_edge(source, association.get("ItemID", ""), association.get("Type", "association"))
    return result


def read_pid_graph(path: Path) -> PidGraph:
    """Detect and read native DEXPI or Proteus XML."""

    root_tag = _local(ET.parse(path).getroot().tag)
    if root_tag == "Model":
        return read_native_dexpi(path)
    if root_tag == "PlantModel":
        return read_proteus(path)
    raise ValueError("Unsupported DEXPI XML root element: %s" % root_tag)


def _selected_nodes(graph: PidGraph, max_nodes: int) -> List[str]:
    priorities = {"equipment": 0, "piping": 1, "instrument": 2, "connection": 3, "other": 4}
    connected = {source for source, _, _ in graph.edges} | {target for _, target, _ in graph.edges}
    primary = [identity for identity, node in graph.nodes.items() if node.category != "connection"]
    candidates = primary if primary else list(graph.nodes)
    values = sorted(
        candidates,
        key=lambda identity: (
            priorities.get(graph.nodes[identity].category, 9),
            identity not in connected,
            graph.nodes[identity].label,
        ),
    )
    return values[:max_nodes]


def _display_edges(graph: PidGraph, selected: Set[str]) -> List[Tuple[str, str, str]]:
    """Keep direct references and contract hidden nozzle/connection nodes."""

    result = {edge for edge in graph.edges if edge[0] in selected and edge[1] in selected}
    hidden_neighbors: Dict[str, Set[str]] = {}
    for source, target, _ in graph.edges:
        if source in selected and target not in selected:
            hidden_neighbors.setdefault(target, set()).add(source)
        if target in selected and source not in selected:
            hidden_neighbors.setdefault(source, set()).add(target)
    for neighbors in hidden_neighbors.values():
        ordered = sorted(neighbors)
        for index, source in enumerate(ordered):
            for target in ordered[index + 1 :]:
                result.add((source, target, "via connection node"))
    return sorted(result)


def _layout(graph: PidGraph, selected: Sequence[str]) -> Mapping[str, Tuple[float, float]]:
    categories: Dict[str, List[str]] = {"instrument": [], "equipment": [], "piping": [], "connection": [], "other": []}
    for identity in selected:
        categories.setdefault(graph.nodes[identity].category, []).append(identity)
    result: Dict[str, Tuple[float, float]] = {}
    rows = {"instrument": 3.0, "equipment": 1.8, "piping": 0.6, "connection": -0.5, "other": -1.4}
    for category, identities in categories.items():
        count = len(identities)
        for index, identity in enumerate(identities):
            x = 0.0 if count == 1 else 11.0 * index / max(1, count - 1)
            result[identity] = (x, rows.get(category, -1.4))
    return result


def render_pid(
    graph: PidGraph,
    output_png: Path,
    output_svg: Path | None = None,
    title: str = "NeqSim DEXPI engineering P&ID overview",
    max_nodes: int = 38,
) -> Mapping[str, int | str]:
    """Render a deterministic overview and return figure statistics."""

    os.environ.setdefault("MPLCONFIGDIR", "/tmp/neqsim-matplotlib")
    import matplotlib

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    from matplotlib.patches import Circle, FancyArrowPatch, FancyBboxPatch, Polygon

    selected = _selected_nodes(graph, max_nodes)
    selected_set = set(selected)
    positions = _layout(graph, selected)
    edges = _display_edges(graph, selected_set)
    colors = {
        "equipment": ("#DCEEFF", "#1565A7"),
        "piping": ("#FFE5E1", "#B33A2B"),
        "instrument": ("#FFF2B8", "#9B7000"),
        "connection": ("#E8ECEF", "#59636B"),
        "other": ("#EEE8F7", "#72529B"),
    }

    fig, ax = plt.subplots(figsize=(16, 8.5))
    ax.set_facecolor("#FAFBFC")
    for source, target, role in edges:
        x1, y1 = positions[source]
        x2, y2 = positions[target]
        rad = 0.08 if abs(y2 - y1) < 0.1 else 0.02
        arrow = FancyArrowPatch(
            (x1, y1),
            (x2, y2),
            arrowstyle="-|>",
            mutation_scale=10,
            linewidth=1.05,
            color="#71808D",
            alpha=0.70,
            connectionstyle="arc3,rad=%s" % rad,
            zorder=1,
        )
        ax.add_patch(arrow)

    for identity in selected:
        node = graph.nodes[identity]
        x, y = positions[identity]
        fill, edge = colors.get(node.category, colors["other"])
        if node.category == "instrument":
            patch = Circle((x, y), 0.29, facecolor=fill, edgecolor=edge, linewidth=1.6, zorder=3)
        elif node.category == "piping":
            patch = Polygon(
                [(x - 0.34, y), (x - 0.16, y + 0.25), (x + 0.16, y - 0.25), (x + 0.34, y)],
                closed=True,
                facecolor=fill,
                edgecolor=edge,
                linewidth=1.5,
                zorder=3,
            )
        else:
            patch = FancyBboxPatch(
                (x - 0.43, y - 0.25),
                0.86,
                0.50,
                boxstyle="round,pad=0.04,rounding_size=0.08",
                facecolor=fill,
                edgecolor=edge,
                linewidth=1.5,
                zorder=3,
            )
        ax.add_patch(patch)
        ax.text(x, y - 0.42, node.label, ha="center", va="top", fontsize=7.5, color="#1D2A33", zorder=4)

    legend = [
        ("Process equipment", "equipment"),
        ("Piping / valves", "piping"),
        ("Instrumentation / signals", "instrument"),
        ("Nozzles / connection nodes", "connection"),
    ]
    for index, (label, category) in enumerate(legend):
        fill, edge = colors[category]
        ax.scatter([0.2 + index * 2.8], [4.25], s=95, c=[fill], edgecolors=[edge], linewidths=1.4)
        ax.text(0.45 + index * 2.8, 4.25, label, va="center", fontsize=9)

    ax.text(0.0, 4.75, title, fontsize=17, fontweight="bold", color="#142B3B")
    ax.text(
        0.0,
        4.48,
        "%s · %d displayed objects · %d displayed references"
        % (graph.source_profile, len(selected), len(edges)),
        fontsize=10,
        color="#526471",
    )
    ax.text(
        0.0,
        -2.05,
        "Documentation preview — verify symbols, layout and round-trip fidelity in the target CAE tool before controlled issue.",
        fontsize=8.5,
        color="#7A3E2B",
    )
    ax.set_xlim(-0.8, 11.8)
    ax.set_ylim(-2.3, 5.0)
    ax.axis("off")
    fig.tight_layout()
    output_png.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_png, dpi=170, bbox_inches="tight", facecolor=fig.get_facecolor())
    if output_svg is not None:
        output_svg.parent.mkdir(parents=True, exist_ok=True)
        fig.savefig(output_svg, bbox_inches="tight", facecolor=fig.get_facecolor())
    plt.close(fig)
    return {
        "sourceProfile": graph.source_profile,
        "parsedNodeCount": len(graph.nodes),
        "parsedEdgeCount": len(graph.edges),
        "displayedNodeCount": len(selected),
        "displayedEdgeCount": len(edges),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="Native DEXPI 2.0 or Proteus XML")
    parser.add_argument("--png", type=Path, required=True, help="Output PNG")
    parser.add_argument("--svg", type=Path, default=None, help="Optional output SVG")
    parser.add_argument("--title", default="NeqSim DEXPI engineering P&ID overview")
    parser.add_argument("--max-nodes", type=int, default=38)
    args = parser.parse_args()
    graph = read_pid_graph(args.input)
    stats = render_pid(graph, args.png, args.svg, args.title, max_nodes=args.max_nodes)
    print(stats)


if __name__ == "__main__":
    main()
