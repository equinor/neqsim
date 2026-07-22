"""NeqSim Studio — a Python-first, newcomer-friendly way to build process models.

NeqSim Studio wraps the existing NeqSim Java process-simulation engine with a
small Python layer that lowers the barrier to building a flowsheet.  There are
five complementary ways to create a process, all reachable from a single
:class:`Studio` object returned by :func:`connect`:

1. **Natural language** — ``studio.from_text("cool to 30 C then compress to
   120 bara")`` (rule-based offline, or LLM-backed when a callable is supplied).
2. **Template recipes** — ``studio.gas_compression(...)``,
   ``studio.three_stage_separation(...)``, etc.
3. **Guided wizard** — ``studio.wizard(answers={...})`` or ``interactive=True``.
4. **Edit by chat / automation** — ``result.editor().apply_command("set Stage 1
   Compressor outlet pressure to 130 bara")`` and ``editor.evaluate(...)``.
5. **Recipe gallery** — ``studio.gallery()`` then ``studio.build_recipe("key")``.

Everything ultimately produces the JSON understood by NeqSim's
``JsonProcessBuilder`` and runs through ``ProcessSystem.fromJsonAndRun``, so the
engineering stays in the trusted Java core.

Quick start
-----------
.. code-block:: python

    import sys
    sys.path.insert(0, "devtools")
    from neqsim_studio import connect

    studio = connect()                       # starts/reuses the NeqSim JVM
    result = studio.gas_compression(         # template recipe
        suction_pressure_bara=20, discharge_pressure_bara=120, stages=2)
    result.summary()                          # text overview + key results
    result.editor().apply_command(            # tweak by talking
        "set Stage 1 Compressor outlet pressure to 55 bara")

The pure-Python building blocks (:class:`FlowsheetSpec`,
:func:`parse_edit_command`, :func:`text_to_json`, the ``*_spec`` template
functions, the gallery catalog, and the wizard router) carry no JVM dependency
and are unit-tested without starting Java.
"""

from __future__ import annotations

from .core import FlowsheetResult, ProcessBuilderContext, Studio, connect
from .edit import FlowsheetEditor, parse_edit_command
from .gallery import (
    RECIPES,
    find_recipes,
    get_recipe,
    list_recipes,
    print_gallery,
)
from .jsonspec import FlowsheetSpec, quantity
from .templates import (
    co2_capture_spec,
    dehydration_spec,
    from_template,
    gas_compression_spec,
    list_templates,
    three_stage_separation_spec,
)
from .text import build_from_text, build_prompt, text_to_json, text_to_spec
from .wizard import plan_from_answers, run_wizard

__all__ = [
    # entry points
    "connect",
    "Studio",
    "FlowsheetResult",
    "ProcessBuilderContext",
    # spec
    "FlowsheetSpec",
    "quantity",
    # templates
    "list_templates",
    "from_template",
    "gas_compression_spec",
    "three_stage_separation_spec",
    "dehydration_spec",
    "co2_capture_spec",
    # text
    "text_to_spec",
    "text_to_json",
    "build_from_text",
    "build_prompt",
    # editing
    "FlowsheetEditor",
    "parse_edit_command",
    # wizard
    "run_wizard",
    "plan_from_answers",
    # gallery
    "RECIPES",
    "list_recipes",
    "get_recipe",
    "find_recipes",
    "print_gallery",
]

__version__ = "0.1.0"
