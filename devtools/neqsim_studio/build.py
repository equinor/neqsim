"""JSON build bridge for NeqSim Studio.

Wraps ``ProcessSystem.fromJsonAndRun`` so the rest of the package can build a
running flowsheet from either a Python dict or a JSON string and get back a
:class:`~neqsim_studio.core.FlowsheetResult`.
"""

from __future__ import annotations

import json as _json
from typing import List, Union


def _collect_warnings(result) -> List[str]:
    """Extract human-readable warnings from a Java ``SimulationResult``.

    Parameters
    ----------
    result:
        The Java ``SimulationResult`` returned by ``fromJsonAndRun``.

    Returns
    -------
    list of str
        Warning messages (possibly empty).
    """
    warnings: List[str] = []
    try:
        for warn in result.getWarnings():
            code = ""
            try:
                code = str(warn.getCode())
            except Exception:
                pass
            message = ""
            try:
                message = str(warn.getMessage())
            except Exception:
                message = str(warn)
            warnings.append(("%s: %s" % (code, message)).strip(": "))
    except Exception:
        pass
    return warnings


def build_from_json(context, definition: Union[str, dict], fluid=None,
                    source: str = "json"):
    """Build and run a flowsheet from a JSON definition.

    Parameters
    ----------
    context:
        A :class:`~neqsim_studio.core.ProcessBuilderContext`.
    definition:
        JSON string or Python dict in the ``JsonProcessBuilder`` schema.
    fluid:
        Optional pre-built NeqSim fluid passed to the overloaded builder.
    source:
        Label stored on the resulting :class:`FlowsheetResult`.

    Returns
    -------
    FlowsheetResult
        The built (and run) flowsheet wrapper.

    Raises
    ------
    RuntimeError
        If the builder reports a hard error.
    """
    from .core import FlowsheetResult

    if isinstance(definition, dict):
        json_text = _json.dumps(definition)
    else:
        json_text = str(definition)

    process_system = context.cls("neqsim.process.processmodel.ProcessSystem")

    if fluid is not None:
        result = process_system.fromJsonAndRun(json_text, fluid)
    else:
        result = process_system.fromJsonAndRun(json_text)

    warnings = _collect_warnings(result)

    is_error = False
    try:
        is_error = bool(result.isError())
    except Exception:
        try:
            is_error = not bool(result.isSuccess())
        except Exception:
            is_error = False

    if is_error:
        messages = []
        try:
            for err in result.getErrors():
                messages.append("%s: %s" % (str(err.getCode()), str(err.getMessage())))
        except Exception:
            pass
        raise RuntimeError(
            "Flowsheet build failed: " + ("; ".join(messages) or "unknown error")
        )

    process = result.getProcessSystem()
    return FlowsheetResult(context, process, warnings=warnings, source=source)
