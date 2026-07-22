"""Render a NeqSim process as a P&ID-style graph using pyDEXPI.

This example shows the full **NeqSim -> DEXPI/Proteus -> pyDEXPI -> image**
pipeline:

1. Build and run a small NeqSim gas-processing flowsheet.
2. Export pyDEXPI-ready DEXPI (Proteus 4.1) XML with
   ``DexpiXmlWriter.writeForPyDexpi``.
3. Load it with pyDEXPI, convert to a NetworkX process graph, and render
   it to PNG/SVG with a clean left-to-right (Graphviz ``dot``) layout,
   falling back to pyDEXPI's built-in renderer if Graphviz is unavailable.

Requirements
------------
* ``pip install pydexpi matplotlib pydot`` (pyDEXPI is **AGPL-3.0** licensed).
* Graphviz ``dot`` on PATH for the nicest layout (optional). On Windows the
  binary is typically at ``C:\\Program Files\\Graphviz\\bin``.
* A NeqSim build recent enough to include
  ``neqsim.process.processmodel.dexpi.DexpiXmlWriter`` with the layout engine.
  When run from inside the NeqSim repository this script uses the freshly
  compiled classes in ``target/classes`` automatically; otherwise it falls
  back to the installed ``neqsim`` Python package.

Run
---
    python examples/neqsim/render_neqsim_dexpi_with_pydexpi.py
"""

import os
import sys
from pathlib import Path


# --------------------------------------------------------------------------- #
# 1. NeqSim Java access (dev workspace if available, else installed package)
# --------------------------------------------------------------------------- #
def _bootstrap_neqsim():
    """Return an object exposing ``JClass(name)`` for NeqSim Java classes."""
    here = Path(__file__).resolve()
    for cand in [here] + list(here.parents):
        setup = cand / "devtools" / "neqsim_dev_setup.py"
        if (cand / "pom.xml").exists() and setup.exists():
            sys.path.insert(0, str(cand / "devtools"))
            from neqsim_dev_setup import neqsim_init, neqsim_classes

            ns = neqsim_init(project_root=cand, recompile=False, verbose=False)
            return neqsim_classes(ns), cand

    # Fallback: installed neqsim Python package
    import jpype
    from neqsim import jneqsim  # noqa: F401  (starts the JVM)

    class _Wrap:
        @staticmethod
        def JClass(name):
            return jpype.JClass(name)

    return _Wrap(), Path.cwd()


def build_process(ns):
    """Build and run a small three-phase separation + compression flowsheet."""
    SystemSrkEos = ns.JClass("neqsim.thermo.system.SystemSrkEos")
    Stream = ns.JClass("neqsim.process.equipment.stream.Stream")
    ThreePhaseSeparator = ns.JClass(
        "neqsim.process.equipment.separator.ThreePhaseSeparator")
    Cooler = ns.JClass("neqsim.process.equipment.heatexchanger.Cooler")
    Compressor = ns.JClass("neqsim.process.equipment.compressor.Compressor")
    ThrottlingValve = ns.JClass("neqsim.process.equipment.valve.ThrottlingValve")
    ProcessSystem = ns.JClass("neqsim.process.processmodel.ProcessSystem")

    fluid = SystemSrkEos(273.15 + 60.0, 60.0)
    for comp, frac in [("methane", 0.80), ("ethane", 0.07), ("propane", 0.05),
                       ("n-butane", 0.03), ("water", 0.03), ("nC10", 0.02)]:
        fluid.addComponent(comp, frac)
    fluid.setMixingRule("classic")
    fluid.setMultiPhaseCheck(True)

    feed = Stream("Well Stream", fluid)
    feed.setFlowRate(50.0, "MSm3/day")
    feed.setTemperature(60.0, "C")
    feed.setPressure(60.0, "bara")

    separator = ThreePhaseSeparator("Inlet Separator", feed)
    cooler = Cooler("Gas Cooler", separator.getGasOutStream())
    cooler.setOutTemperature(30.0, "C")
    compressor = Compressor("Export Compressor", cooler.getOutStream())
    compressor.setOutletPressure(120.0, "bara")
    oil_valve = ThrottlingValve("Oil LCV", separator.getOilOutStream())
    oil_valve.setOutletPressure(20.0, "bara")

    process = ProcessSystem()
    for unit in [feed, separator, cooler, compressor, oil_valve]:
        process.add(unit)
    process.run()
    return process


def export_dexpi(ns, process, xml_path):
    """Export a pyDEXPI-ready NeqSim ``ProcessSystem`` to DEXPI/Proteus XML."""
    DexpiXmlWriter = ns.JClass("neqsim.process.processmodel.dexpi.DexpiXmlWriter")
    JFile = ns.JClass("java.io.File")
    xml_path.parent.mkdir(parents=True, exist_ok=True)
    DexpiXmlWriter.writeForPyDexpi(process, JFile(str(xml_path)))
    return xml_path


# --------------------------------------------------------------------------- #
# 3. Load with pyDEXPI and render
# --------------------------------------------------------------------------- #
def _ensure_graphviz_on_path():
    """Add the default Windows Graphviz bin folder to PATH if present."""
    for candidate in (r"C:\Program Files\Graphviz\bin",
                      r"C:\Program Files (x86)\Graphviz\bin"):
        if os.path.isdir(candidate) and candidate not in os.environ.get("PATH", ""):
            os.environ["PATH"] = candidate + os.pathsep + os.environ.get("PATH", "")


_NODE_COLORS = {
    "equipment": "#2E86C1",   # blue  - process equipment
    "piping": "#CB4335",      # red   - piping components / valves
    "instrument": "#F1C40F",  # amber - instrumentation
}


def _node_category(dexpi_class):
    name = (dexpi_class or "").lower()
    if "valve" in name or "piping" in name or "pipe" in name:
        return "piping"
    if "instrument" in name or "transmitter" in name or "indicator" in name:
        return "instrument"
    return "equipment"


def render(xml_dir, xml_name, png_path, svg_path):
    """Load the Proteus XML with pyDEXPI and render the process graph."""
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    import networkx as nx
    from pydexpi.loaders import ProteusSerializer, MLGraphLoader

    model = ProteusSerializer().load(str(xml_dir), xml_name)
    loader = MLGraphLoader(plant_model=model)
    graph = loader.dexpi_to_graph(model)
    print("Process graph: %d nodes, %d edges"
          % (graph.number_of_nodes(), graph.number_of_edges()))

    # Clean left-to-right layout via Graphviz 'dot' when available.
    _ensure_graphviz_on_path()
    pos = None
    try:
        from networkx.drawing.nx_pydot import graphviz_layout
        pos = graphviz_layout(graph, prog="dot")
    except Exception as exc:  # pragma: no cover - layout fallback
        print("Graphviz 'dot' layout unavailable (%s); using spring layout." % exc)
        pos = nx.spring_layout(graph, seed=7, k=1.5)

    labels, colors = {}, []
    for node, data in graph.nodes(data=True):
        dexpi_class = data.get("dexpi_class", "Item")
        labels[node] = dexpi_class
        colors.append(_NODE_COLORS[_node_category(dexpi_class)])

    fig, ax = plt.subplots(figsize=(16, 9))
    nx.draw_networkx_edges(graph, pos, ax=ax, edge_color="#566573",
                           width=1.6, arrows=True, arrowsize=14)
    nx.draw_networkx_nodes(graph, pos, ax=ax, node_color=colors,
                           node_size=1400, edgecolors="white", linewidths=1.5)
    nx.draw_networkx_labels(graph, pos, labels=labels, ax=ax, font_size=9,
                            font_color="#1B2631", verticalalignment="bottom")
    ax.set_title("NeqSim process rendered via DEXPI/Proteus + pyDEXPI",
                 fontsize=14, fontweight="bold")
    ax.axis("off")
    fig.tight_layout()

    fig.savefig(str(png_path), dpi=150, bbox_inches="tight")
    fig.savefig(str(svg_path), bbox_inches="tight")
    plt.close(fig)
    print("Saved:", png_path)
    print("Saved:", svg_path)


def main():
    ns, root = _bootstrap_neqsim()
    out_dir = root / "output"

    print("Building and running NeqSim flowsheet...")
    process = build_process(ns)

    compat_xml = export_dexpi(ns, process, out_dir / "neqsim_process_pydexpi.xml")
    print("Exported pyDEXPI-ready DEXPI XML:", compat_xml)

    render(out_dir, compat_xml.name,
           out_dir / "neqsim_pydexpi_pid.png",
           out_dir / "neqsim_pydexpi_pid.svg")
    print("Done.")


if __name__ == "__main__":
    main()
