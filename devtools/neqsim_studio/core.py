"""Core objects for NeqSim Studio.

This module provides the three things a newcomer interacts with:

* :func:`connect` — start (or reuse) the JVM and return a :class:`Studio`.
* :class:`Studio` — the single entry point that exposes template recipes,
  natural-language building, the guided wizard, and the recipe gallery.
* :class:`FlowsheetResult` — a friendly wrapper around a built NeqSim
  ``ProcessSystem`` with ``summary()``, ``show()``, and an attached editor for
  the "modify by talking" workflow.

The design goal is that everything is reachable from Python with sensible
defaults, while still delegating the actual engineering to the existing NeqSim
Java classes (``JsonProcessBuilder``, the ``ProcessTemplate`` family, and
``ProcessAutomation``).
"""

from __future__ import annotations

import json as _json
from typing import Callable, Dict, List, Mapping, Optional, Union

from . import fluids as _fluids


class ProcessBuilderContext:
    """Caches resolved Java classes for a running JVM.

    Parameters
    ----------
    jclass:
        Callable resolving a fully qualified Java class name to a class object
        (``jpype.JClass`` or the ``ns.JClass`` from ``neqsim_dev_setup``).
    """

    def __init__(self, jclass: Callable[[str], object]):
        self._jclass = jclass
        self._cache: Dict[str, object] = {}

    def cls(self, fqn: str):
        """Resolve and cache a Java class by fully qualified name.

        Parameters
        ----------
        fqn:
            Fully qualified Java class name.

        Returns
        -------
        object
            The resolved JPype class.
        """
        if fqn not in self._cache:
            self._cache[fqn] = self._jclass(fqn)
        return self._cache[fqn]


def _to_py_list(java_list) -> List[str]:
    """Convert a Java ``List<String>`` (or any iterable) to a Python list.

    Parameters
    ----------
    java_list:
        A Java collection or ``None``.

    Returns
    -------
    list of str
        Python strings; empty list when ``java_list`` is ``None``.
    """
    if java_list is None:
        return []
    return [str(item) for item in java_list]


class FlowsheetResult:
    """Friendly wrapper around a built NeqSim ``ProcessSystem``.

    Parameters
    ----------
    context:
        The :class:`ProcessBuilderContext` used to resolve helper classes.
    process:
        The Java ``ProcessSystem`` object (already built, optionally run).
    warnings:
        Human-readable warnings collected while building.
    source:
        Short label describing how the flowsheet was created (e.g.
        ``"template:gas_compression"`` or ``"text"``).
    """

    def __init__(self, context: ProcessBuilderContext, process, warnings=None,
                 source: str = "unknown"):
        self.context = context
        self.process = process
        self.warnings: List[str] = list(warnings or [])
        self.source = source
        self._editor = None

    # ── status ──
    @property
    def ok(self) -> bool:
        """Whether the flowsheet built without hard errors.

        Returns
        -------
        bool
            True when a process object is present.
        """
        return self.process is not None

    def units(self) -> List[str]:
        """List the equipment unit names in the flowsheet.

        Returns
        -------
        list of str
            Unit names in process order.
        """
        if self.process is None:
            return []
        try:
            return _to_py_list(self.process.getAutomation().getUnitList())
        except Exception:
            names = []
            try:
                for unit in self.process.getUnitOperations():
                    names.append(str(unit.getName()))
            except Exception:
                pass
            return names

    # ── automation / editing ──
    def automation(self):
        """Return the cached Java ``ProcessAutomation`` facade.

        Returns
        -------
        object
            The ``ProcessAutomation`` instance for this process.
        """
        return self.process.getAutomation()

    def editor(self):
        """Return a :class:`~neqsim_studio.edit.FlowsheetEditor` for this process.

        Returns
        -------
        FlowsheetEditor
            A Python-friendly wrapper over ``ProcessAutomation``.
        """
        if self._editor is None:
            from .edit import FlowsheetEditor

            self._editor = FlowsheetEditor(self.context, self.process)
        return self._editor

    def get(self, address: str, unit: str = "") -> float:
        """Read a variable by dot-notation address.

        Parameters
        ----------
        address:
            Variable address, e.g. ``"Stage 1 Compressor.power"``.
        unit:
            Unit of measure for the returned value (may be empty).

        Returns
        -------
        float
            The variable value in the requested unit.
        """
        return float(self.process.getAutomation().getVariableValue(address, unit))

    def set(self, address: str, value: float, unit: str = "", run: bool = True):
        """Write an input variable and optionally re-run.

        Parameters
        ----------
        address:
            Variable address to write.
        value:
            New numeric value.
        unit:
            Unit of measure of ``value`` (may be empty for default).
        run:
            When True, re-run the process after writing.

        Returns
        -------
        FlowsheetResult
            ``self`` for chaining.
        """
        auto = self.process.getAutomation()
        if run:
            auto.setVariableValueAndRun(address, float(value), unit)
        else:
            auto.setVariableValue(address, float(value), unit)
        return self

    def run(self):
        """Re-run the process simulation.

        Returns
        -------
        FlowsheetResult
            ``self`` for chaining.
        """
        self.process.run()
        return self

    def describe(self) -> dict:
        """Return the machine-readable automation manifest as a dict.

        Returns
        -------
        dict
            Parsed ``ProcessAutomation.describe()`` output (units, variables).
        """
        return _json.loads(str(self.process.getAutomation().describe()))

    def topology(self) -> dict:
        """Return the equipment-and-connection topology as a dict.

        Returns
        -------
        dict
            Parsed ``ProcessAutomation.getTopology()`` output.
        """
        return _json.loads(str(self.process.getAutomation().getTopology()))

    # ── results ──
    def key_results(self) -> Dict[str, float]:
        """Collect headline results (total compression power, cooling duty).

        Returns
        -------
        dict
            Mapping of result name to value; units are encoded in the key.
        """
        results: Dict[str, float] = {}
        if self.process is None:
            return results
        # ``getPower(unit)`` treats its argument as a UoM and sums compressors and
        # pumps together, so accumulate per equipment type explicitly instead.
        compressor_w = 0.0
        pump_w = 0.0
        cooling = 0.0
        heating = 0.0
        try:
            for unit in self.process.getUnitOperations():
                simple = str(unit.getClass().getSimpleName())
                if simple == "Compressor":
                    try:
                        compressor_w += float(unit.getPower())
                    except Exception:
                        pass
                elif simple == "Pump":
                    try:
                        pump_w += float(unit.getPower())
                    except Exception:
                        pass
                elif simple in ("Cooler", "Heater", "HeatExchanger"):
                    try:
                        duty = float(unit.getDuty())
                    except Exception:
                        continue
                    if duty < 0:
                        cooling += -duty
                    else:
                        heating += duty
        except Exception:
            pass
        if compressor_w:
            results["total_compressor_power_MW"] = compressor_w / 1.0e6
        if pump_w:
            results["total_pump_power_MW"] = pump_w / 1.0e6
        if cooling:
            results["total_cooling_duty_MW"] = cooling / 1.0e6
        if heating:
            results["total_heating_duty_MW"] = heating / 1.0e6
        return results

    # ── presentation ──
    def summary(self, stream=None) -> str:
        """Print and return a compact text summary of the flowsheet.

        Parameters
        ----------
        stream:
            Optional writable stream (defaults to ``sys.stdout``).

        Returns
        -------
        str
            The summary text (also printed).
        """
        lines: List[str] = []
        name = "process"
        try:
            name = str(self.process.getName())
        except Exception:
            pass
        lines.append("NeqSim flowsheet: %s  (source: %s)" % (name, self.source))
        lines.append("-" * 60)
        units = self.units()
        lines.append("Equipment (%d):" % len(units))
        for uname in units:
            simple = ""
            try:
                simple = str(self.process.getUnit(uname).getClass().getSimpleName())
            except Exception:
                pass
            lines.append("  - %-28s %s" % (uname, simple))
        results = self.key_results()
        if results:
            lines.append("-" * 60)
            lines.append("Key results:")
            for key, value in results.items():
                lines.append("  %-32s %12.4f" % (key, value))
        if self.warnings:
            lines.append("-" * 60)
            lines.append("Warnings (%d):" % len(self.warnings))
            for warn in self.warnings:
                lines.append("  ! %s" % warn)
        text = "\n".join(lines)
        print(text, file=stream)
        return text

    def show(self, ax=None):
        """Draw a simple left-to-right block diagram of the flowsheet.

        Requires matplotlib.  Each unit is a labelled box; material
        connections from the automation topology are drawn as arrows.

        Parameters
        ----------
        ax:
            Optional Matplotlib axes to draw on.

        Returns
        -------
        object
            The Matplotlib axes used.
        """
        import matplotlib.pyplot as plt

        if ax is None:
            _fig, ax = plt.subplots(figsize=(max(6, len(self.units()) * 1.8), 3))

        try:
            topo = self.topology()
        except Exception:
            topo = {"equipment": [{"name": u} for u in self.units()],
                    "connections": []}
        equipment = [e.get("name") for e in topo.get("equipment", [])]
        positions = {}
        for idx, name in enumerate(equipment):
            x = idx * 2.0
            positions[name] = (x, 0.0)
            ax.add_patch(
                plt.Rectangle((x - 0.7, -0.4), 1.4, 0.8, fill=True,
                              facecolor="#cfe8fc", edgecolor="#1f77b4")
            )
            ax.text(x, 0.0, name, ha="center", va="center", fontsize=8, wrap=True)
        for conn in topo.get("connections", []):
            src = conn.get("from") or conn.get("source")
            dst = conn.get("to") or conn.get("target")
            if src in positions and dst in positions:
                x0, y0 = positions[src]
                x1, y1 = positions[dst]
                ax.annotate("", xy=(x1 - 0.7, y1), xytext=(x0 + 0.7, y0),
                            arrowprops=dict(arrowstyle="->", color="#555"))
        ax.set_xlim(-1.2, max(1.0, (len(equipment) - 1) * 2.0 + 1.2))
        ax.set_ylim(-1.0, 1.0)
        ax.axis("off")
        ax.set_title("Flowsheet: %s" % self.source)
        return ax

    def to_json(self) -> str:
        """Export the flowsheet to the round-trippable JSON schema.

        Returns
        -------
        str
            JSON consumable by ``ProcessSystem.fromJson``.
        """
        return str(self.process.toJson())

    def __repr__(self):
        return "<FlowsheetResult source=%r units=%d ok=%s>" % (
            self.source, len(self.units()), self.ok)


class Studio:
    """Single entry point for building NeqSim processes the easy way.

    Parameters
    ----------
    context:
        A :class:`ProcessBuilderContext` bound to a running JVM.
    """

    def __init__(self, context: ProcessBuilderContext):
        self.context = context

    # ── fluids ──
    def fluid(self, components: Mapping[str, float], temperature_c: float = 15.0,
              pressure_bara: float = 1.01325, model: str = "srk",
              mixing_rule: str = "classic"):
        """Build a NeqSim fluid from a component dictionary.

        Parameters
        ----------
        components:
            Mapping of component name to mole fraction.
        temperature_c:
            Initial temperature in Celsius.
        pressure_bara:
            Initial pressure in bara.
        model:
            Friendly EOS name (see :data:`neqsim_studio.fluids.EOS_MODELS`).
        mixing_rule:
            Mixing rule name.

        Returns
        -------
        object
            A NeqSim ``SystemInterface``.
        """
        return _fluids.make_fluid(self.context.cls, components,
                                  temperature_c=temperature_c,
                                  pressure_bara=pressure_bara, model=model,
                                  mixing_rule=mixing_rule)

    def preset_fluid(self, name: str, **kwargs):
        """Build one of the built-in preset fluids by name.

        Parameters
        ----------
        name:
            Preset key (e.g. ``"natural_gas"``).
        **kwargs:
            Forwarded to :func:`neqsim_studio.fluids.make_preset`.

        Returns
        -------
        object
            A NeqSim ``SystemInterface``.
        """
        return _fluids.make_preset(self.context.cls, name, **kwargs)

    # ── JSON path ──
    def from_json(self, definition: Union[str, dict], fluid=None) -> FlowsheetResult:
        """Build and run a flowsheet from a JSON definition.

        Parameters
        ----------
        definition:
            JSON string or Python dict in the ``JsonProcessBuilder`` schema.
        fluid:
            Optional pre-built NeqSim fluid; when given, the JSON ``fluid``
            section is ignored.

        Returns
        -------
        FlowsheetResult
            The built (and run) flowsheet wrapper.

        Raises
        ------
        RuntimeError
            If the build returns a hard error.
        """
        from .build import build_from_json

        return build_from_json(self.context, definition, fluid=fluid)

    # ── template recipes (proposal #2) ──
    def gas_compression(self, fluid=None, **params) -> FlowsheetResult:
        """Build a multi-stage gas compression train.

        Parameters
        ----------
        fluid:
            Feed fluid; a ``natural_gas`` preset is used when omitted.
        **params:
            Recipe parameters (see :mod:`neqsim_studio.templates`).

        Returns
        -------
        FlowsheetResult
            The built compression flowsheet.
        """
        from . import templates

        return templates.gas_compression(self, fluid=fluid, **params)

    def three_stage_separation(self, fluid=None, **params) -> FlowsheetResult:
        """Build a three-stage separation train.

        Parameters
        ----------
        fluid:
            Feed fluid; a ``rich_gas`` preset is used when omitted.
        **params:
            Recipe parameters (see :mod:`neqsim_studio.templates`).

        Returns
        -------
        FlowsheetResult
            The built separation flowsheet.
        """
        from . import templates

        return templates.three_stage_separation(self, fluid=fluid, **params)

    def dehydration(self, fluid=None, **params) -> FlowsheetResult:
        """Build a TEG dehydration unit.

        Parameters
        ----------
        fluid:
            Feed fluid; a ``wet_gas`` preset is used when omitted.
        **params:
            Recipe parameters (see :mod:`neqsim_studio.templates`).

        Returns
        -------
        FlowsheetResult
            The built dehydration flowsheet.
        """
        from . import templates

        return templates.dehydration(self, fluid=fluid, **params)

    def co2_capture(self, fluid=None, **params) -> FlowsheetResult:
        """Build an amine CO2 capture unit.

        Parameters
        ----------
        fluid:
            Feed fluid; a ``rich_gas`` preset is used when omitted.
        **params:
            Recipe parameters (see :mod:`neqsim_studio.templates`).

        Returns
        -------
        FlowsheetResult
            The built capture flowsheet.
        """
        from . import templates

        return templates.co2_capture(self, fluid=fluid, **params)

    def from_template(self, name: str, fluid=None, **params) -> FlowsheetResult:
        """Build any registered template recipe by name.

        Parameters
        ----------
        name:
            Recipe name (see :func:`neqsim_studio.templates.list_templates`).
        fluid:
            Optional feed fluid.
        **params:
            Recipe parameters.

        Returns
        -------
        FlowsheetResult
            The built flowsheet.
        """
        from . import templates

        return templates.from_template(self, name, fluid=fluid, **params)

    def list_templates(self) -> Dict[str, str]:
        """List the available template recipes.

        Returns
        -------
        dict
            Mapping of recipe name to one-line description.
        """
        from . import templates

        return templates.list_templates()

    # ── natural language (proposal #1) ──
    def from_text(self, text: str, llm: Optional[Callable[[str], str]] = None,
                  fluid=None) -> FlowsheetResult:
        """Build a flowsheet from a natural-language description.

        Parameters
        ----------
        text:
            Plain-language description of the process.
        llm:
            Optional callable ``str -> str`` that maps a grounding prompt to a
            JSON string.  When omitted a deterministic rule-based parser is
            used so the feature works offline.
        fluid:
            Optional pre-built feed fluid.

        Returns
        -------
        FlowsheetResult
            The built flowsheet.
        """
        from .text import build_from_text

        return build_from_text(self, text, llm=llm, fluid=fluid)

    # ── guided wizard (proposal #3) ──
    def wizard(self, answers: Optional[dict] = None,
               interactive: bool = False) -> FlowsheetResult:
        """Run the guided fill-in-the-blanks builder.

        Parameters
        ----------
        answers:
            Pre-filled answers for non-interactive use (testable).
        interactive:
            When True, prompt the user via ``input()``.

        Returns
        -------
        FlowsheetResult
            The built flowsheet.
        """
        from .wizard import run_wizard

        return run_wizard(self, answers=answers, interactive=interactive)

    # ── gallery (proposal #5) ──
    def gallery(self):
        """Print the cookbook gallery of ready-to-run recipes.

        Returns
        -------
        None
        """
        from . import gallery

        gallery.print_gallery()

    def recipes(self) -> Dict[str, str]:
        """List the gallery recipes.

        Returns
        -------
        dict
            Mapping of recipe key to title.
        """
        from . import gallery

        return {k: v["title"] for k, v in gallery.RECIPES.items()}

    def build_recipe(self, name: str) -> FlowsheetResult:
        """Build and run a named gallery recipe.

        Parameters
        ----------
        name:
            Recipe key from :data:`neqsim_studio.gallery.RECIPES`.

        Returns
        -------
        FlowsheetResult
            The built flowsheet.
        """
        from . import gallery

        recipe = gallery.get_recipe(name)
        return self.from_json(recipe["json"])


def connect(ns=None, recompile: bool = False, verbose: bool = False) -> Studio:
    """Start (or reuse) the NeqSim JVM and return a :class:`Studio`.

    Parameters
    ----------
    ns:
        An existing namespace from ``neqsim_dev_setup.neqsim_init`` (recommended
        inside notebooks so the JVM is not restarted).  When omitted the dev
        setup is bootstrapped automatically.
    recompile:
        Forwarded to ``neqsim_init`` when bootstrapping (recompile Java first).
    verbose:
        Forwarded to ``neqsim_init`` for classpath/JVM logging.

    Returns
    -------
    Studio
        A ready-to-use studio bound to the running JVM.

    Raises
    ------
    RuntimeError
        If no JVM accessor can be obtained.
    """
    jclass = None
    if ns is not None and hasattr(ns, "JClass"):
        jclass = ns.JClass
    else:
        try:
            from neqsim_dev_setup import neqsim_init

            booted = neqsim_init(recompile=recompile, verbose=verbose)
            if booted is not None and hasattr(booted, "JClass"):
                jclass = booted.JClass
        except Exception:
            jclass = None
    if jclass is None:
        try:
            import jpype

            jclass = jpype.JClass
        except Exception as exc:  # pragma: no cover - environment dependent
            raise RuntimeError(
                "Could not obtain a JVM class resolver. Pass ns= from "
                "neqsim_dev_setup.neqsim_init(), or ensure jpype is installed "
                "and the JVM is started."
            ) from exc
    return Studio(ProcessBuilderContext(jclass))
