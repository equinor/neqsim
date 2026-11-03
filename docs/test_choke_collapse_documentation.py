"""Source contracts for the choke-collapse Python documentation example."""

import ast
import re
from pathlib import Path

GUIDE = Path(__file__).resolve().parent / "process" / "choke-collapse.md"
MARKER = "<!-- choke-collapse-python: standalone-example -->"


def python_example() -> str:
    """Return the uniquely marked standalone Python fence."""
    markdown = GUIDE.read_text(encoding="utf-8")
    marker_position = markdown.index(MARKER)
    assert marker_position == markdown.rindex(MARKER)
    blocks = re.findall(
        r"```python\n(.*?)\n```",
        markdown[marker_position:],
        re.DOTALL,
    )
    assert len(blocks) == 1
    return blocks[0]


def test_python_example_has_valid_syntax_and_supported_gateway() -> None:
    """Keep the published example valid Python using the public gateway."""
    source = python_example()
    ast.parse(source)

    assert "from neqsim import jneqsim" in source
    assert "JClass" not in source
    assert "jpype" not in source
    assert "ns." not in source


def test_python_example_is_standalone_and_orders_definitions() -> None:
    """Require the fluid, feed, and valve to be defined before use."""
    source = python_example()

    fluid = 'fluid = jneqsim.thermo.system.SystemSrkEos(298.15, 100.0)'
    feed = 'feed = jneqsim.process.equipment.stream.Stream("feed", fluid)'
    valve = (
        'valve = jneqsim.process.equipment.valve.ThrottlingValve('
        '"CV-100", feed)'
    )
    result = "result = valve.analyseChokeCollapse()"

    assert source.index(fluid) < source.index(feed)
    assert source.index(feed) < source.index(valve)
    assert source.index(valve) < source.index(result)
    assert 'fluid.setMixingRule("classic")' in source
    assert 'feed.setFlowRate(1000.0, "kg/hr")' in source
    assert 'feed.setPressure(100.0, "bara")' in source
    assert 'feed.setTemperature(25.0, "C")' in source
    assert source.index("feed.run()") < source.index(valve)
    assert source.index("valve.run()") < source.index(result)


def test_python_example_freezes_engineering_checks_without_console_output() -> None:
    """Guard the critical-flow checks and silent notebook behavior."""
    source = python_example()

    assert 'result.getFlowRegime().name() == "CRITICAL"' in source
    assert "result.getPressureRatio() < result.getCriticalPressureRatio()" in source
    assert "result.getMachNumber() == 1.0" in source
    assert "print(" not in source
    assert "System.out" not in source
    assert "System.err" not in source


def test_front_matter_title_is_not_duplicated_as_h1() -> None:
    """Keep the Jekyll title from rendering twice."""
    markdown = GUIDE.read_text(encoding="utf-8")

    assert "\n# Choke Collapse Analysis\n" not in markdown
