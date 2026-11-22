"""Source contracts for the dynamic compressor Python documentation example."""

import ast
import re
from pathlib import Path

GUIDE = (
    Path(__file__).resolve().parent
    / "process"
    / "equipment"
    / "compressor_curves.md"
)
HEADING = "### Python Example - Dynamic Simulation"


def dynamic_python_example() -> str:
    """Return the single Python fence in the bounded dynamic-example section."""
    markdown = GUIDE.read_text(encoding="utf-8")
    section_start = markdown.index(HEADING)
    section_end = markdown.index("\n---\n", section_start)
    section = markdown[section_start:section_end]
    blocks = re.findall(r"```python\n(.*?)\n```", section, re.DOTALL)
    assert len(blocks) == 1
    return blocks[0]


def test_dynamic_python_example_has_valid_syntax_and_supported_gateway() -> None:
    """Keep the published example valid Python using the public NeqSim gateway."""
    source = dynamic_python_example()
    ast.parse(source)

    assert "from neqsim import jneqsim" in source
    assert "jNeqSim" not in source
    assert "JClass" not in source
    assert "from jpype" not in source


def test_dynamic_python_example_is_standalone_and_orders_definitions() -> None:
    """Require an explicit fluid and inlet before the compressor is constructed."""
    source = dynamic_python_example()

    assert source.index("fluid = SystemSrkEos(") < source.index(
        'inlet_stream = Stream("dynamic inlet", fluid)'
    )
    assert source.index('inlet_stream = Stream("dynamic inlet", fluid)') < source.index(
        'comp = Compressor("K-100 dynamic", inlet_stream)'
    )
    assert 'fluid.setMixingRule("classic")' in source
    assert 'inlet_stream.setFlowRate(5000.0, "kg/hr")' in source
    assert 'inlet_stream.setTemperature(25.0, "C")' in source
    assert 'inlet_stream.setPressure(40.0, "bara")' in source
    assert source.index("inlet_stream.run()") < source.index(
        'comp = Compressor("K-100 dynamic", inlet_stream)'
    )


def test_dynamic_python_example_uses_deterministic_steps_and_reports_results() -> None:
    """Guard the dynamic API sequence and integer-step reporting contract."""
    source = dynamic_python_example()

    assert "for step in range(number_of_steps):" in source
    assert "while sim_time" not in source
    assert "sim_time = step * dt" in source
    assert "if step % 10 == 0:" in source
    assert "comp.startCompressor(10000.0)" in source
    assert "comp.updateDynamicState(dt)" in source
    assert "anti_surge.updateController(surge_margin, dt)" in source
    assert "assert history.getPointCount() > 0" in source
    assert "history.generateSummary()" in source
