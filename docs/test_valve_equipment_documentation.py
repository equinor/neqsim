"""Regression checks for the throttling-valve equipment documentation."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs/process/equipment/valves.md"
SOURCE = (
    ROOT
    / "src/main/java/neqsim/process/equipment/valve/ThrottlingValve.java"
)
JAVA_TEST = (
    ROOT
    / "src/test/java/neqsim/process/equipment/valve/ThrottlingValveTest.java"
)


def test_valve_guide_metadata_and_navigation() -> None:
    """The guide remains discoverable as the valve equipment reference."""
    text = GUIDE.read_text(encoding="utf-8")

    assert text.startswith("---\ntitle: Valve Equipment\n")
    assert "negative differential pressure" in text
    assert "[Process equipment overview](README.md)" in text


def test_valve_guide_defines_the_pressure_state_truth_table() -> None:
    """Document all requested-pressure and acceptance-flag combinations."""
    text = GUIDE.read_text(encoding="utf-8")

    assert "## Requested outlet pressure above the inlet" in text
    assert "| `Pout <= Pin` | either value |" in text
    assert "| `Pout > Pin` | `false` |" in text
    assert "| `Pout > Pin` | `true` (default) |" in text
    assert "setAcceptNegativeDP(false)" in text


def test_valve_guide_does_not_promise_pressure_adding_or_reverse_flow() -> None:
    """Keep the accepted pressure state distinct from unsupported hydraulics."""
    text = GUIDE.read_text(encoding="utf-8")

    assert "does not add shaft work" in text
    assert "does not enable a reverse-flow calculation" in text
    assert "does **not** calculate compressor" in text
    assert "bidirectional network solution" in text


def test_source_contract_matches_the_documented_default_and_clamp() -> None:
    """Pin the source default, Javadocs, and pressure-clamping branch."""
    text = SOURCE.read_text(encoding="utf-8")

    assert "private boolean acceptNegativeDP = true;" in text
    assert "the valve hydraulic driving differential is limited to zero" in text
    assert "if (!acceptNegativeDP)" in text
    assert "outThermoSystem.setPressure(inThermoSystem.getPressure())" in text


def test_focused_java_regression_covers_default_clamp_and_acceptance() -> None:
    """Ensure executable coverage exists for all higher-outlet-pressure modes."""
    text = JAVA_TEST.read_text(encoding="utf-8")

    assert "assertTrue(defaultValve.isAcceptNegativeDP()" in text
    assert "defaultValve.setOutletPressure(15.0, \"bara\")" in text
    assert "clampingValve.setAcceptNegativeDP(false)" in text
    assert "acceptingValve.setAcceptNegativeDP(true)" in text
