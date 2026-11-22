"""Worked example of a phase-band PFD (gas top, oil middle, water bottom) built with :class:`pfd_diagram.PfdDiagram`.

Equinor TR2329 SR-15794 / ISO 10628:1997 5.3.3: horizontal bands, gas flow in the top band, liquid flow below, process flow left to right.
Run ``python pfd_band_example.py out_dir`` -> ``out_dir/band_example.png|svg|layout.json|review.json``; the report status is PASS.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from pfd_diagram import PfdDiagram  # noqa: E402


def build():
    """Return the diagram: inlet separator in the oil band, gas train above it, produced water below it."""
    d = PfdDiagram("Example - gas compression, oil export and produced water in phase bands", width_in=16.54, height_in=11.69,
                   font_pt=8.0, label_pt=7.0, intended_width_mm=420.0, line_pt=1.5, top_band=5.0, bottom_band=9.5,
                   footer="Example only. Bands: gas on top, oil / liquid hydrocarbon in the middle, water at the bottom (TR2329 SR-15794, ISO 10628:1997 5.3.3).")
    d.bub_r = 1.05
    b = d.phase_bands(weights=(33.0, 13.0, 7.0))
    gas_y, oil_y, water_y = b["gas"][2] - 6.0, b["oil"][2] - 1.5, b["water"][2] + 0.6
    d.node("feed", "Feed\n473 t/h", 7.5, oil_y, kind="boundary", w=9.5)
    d.node("sep", "VA-20-0001\nseparator", 30, oil_y, kind="vessel", tag="VA-20-0001", band="oil", w=12, h=9)
    d.node("hx", "HA-23-0001\ncooler", 27, gas_y, kind="hx", tag="HA-23-0001", band="gas", w=7.5)
    d.node("scr", "VG-23-0001\nscrubber", 40, gas_y, kind="vessel", tag="VG-23-0001", band="gas", w=7.5)
    d.node("comp", "KA-23-0001\ncompressor", 56, gas_y, kind="compressor", tag="KA-23-0001", band="gas", w=11.5)
    d.node("gas", "Gas to export", 85, gas_y, kind="boundary", w=11)
    d.node("pump", "PA-21-0001A", 62, oil_y + 2.0, kind="pump", tag="PA-21-0001A", band="oil")
    d.node("oil", "Export oil", 88, oil_y + 2.0, kind="boundary", w=11)
    d.node("water", "Produced water", 30, water_y, kind="boundary", w=16)
    d.edge("feed", "r", "sep", "l", fluid="mixed", stream="1")
    d.edge("sep", "t", "hx", "b", fluid="gas", stream="2", offa=-3.0)
    d.edge("hx", "r", "scr", "l", fluid="gas", stream="3")
    d.edge("scr", "r", "comp", "l", fluid="gas", stream="4")
    d.edge("comp", "r", "gas", "l", fluid="gas", stream="5")
    d.edge("sep", "r", "pump", "l", fluid="oil", stream="6", offa=2.0)
    d.edge("pump", "r", "oil", "l", fluid="oil", stream="7")
    d.edge("sep", "b", "water", "t", fluid="water", stream="8")
    d.waive("PORT_AMBIGUOUS", "separator top carries only the gas riser in this example")
    d.waive("WHITESPACE_POCKET", "minimal example: one gas row only")
    return d


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "pfd_layout_examples")
    rep = build().render(os.path.join(out, "band_example.png"))
    print(rep.status, rep.to_dict()["counts"])
