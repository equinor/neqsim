"""PfdDiagram language option: Norwegian legend and phase-band texts without patching module globals."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import pfd_diagram as pd  # noqa: E402


def _diagram(language):
    d = pd.PfdDiagram("t", width_in=7.0, height_in=4.0, language=language)
    d.node("a", "A", 20.0, 20.0, kind="boundary")
    d.node("b", "B", 60.0, 20.0, kind="boundary")
    d.edge("a", "r", "b", "l", fluid="mixed", stream="1")
    d.edge("a", "b", "b", "b", fluid="water", stream="2")
    return d


def test_norwegian_legend_and_bands():
    d = _diagram("no")
    d._setup_measure()
    labels = [item[3] for item in d._legend_items()["items"]]
    assert labels == ["flerfase", "vann"]
    assert list(d.phase_bands().values())
    assert [b["label"] for b in d.bands] == ["GASS", "OLJE / VÆSKE HC", "VANN"]


def test_default_language_is_english():
    d = _diagram("en")
    d._setup_measure()
    labels = [item[3] for item in d._legend_items()["items"]]
    assert labels == ["multiphase", "water"]
    d.phase_bands()
    assert d.bands[0]["label"] == "GAS"
    assert pd.PfdDiagram("t").language == "en"


def test_fluid_label_override():
    d = pd.PfdDiagram("t", width_in=7.0, height_in=4.0, language="no", fluid_labels={"water": "produsert vann"})
    d.node("a", "A", 20.0, 20.0, kind="boundary")
    d.node("b", "B", 60.0, 20.0, kind="boundary")
    d.edge("a", "r", "b", "l", fluid="water", stream="1")
    d._setup_measure()
    assert [item[3] for item in d._legend_items()["items"]] == ["produsert vann"]
