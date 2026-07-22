"""Pure-Python flowsheet JSON spec builder for NeqSim Studio.

This module assembles the JSON definition consumed by
``ProcessSystem.fromJsonAndRun`` (see :class:`JsonProcessBuilder`).  It contains
**no JVM dependency**, so it can be unit-tested in plain Python and reused by
the template recipes, the natural-language parser, and the gallery.

Schema reminder (the part this builder emits)::

    {
      "fluid": {"model": "SRK", "temperature": 288.15, "pressure": 1.01325,
                 "mixingRule": "classic", "components": {"methane": 0.9, ...}},
      "process": [
        {"type": "Stream", "name": "feed",
         "properties": {"flowRate": [100.0, "MSm3/day"],
                        "temperature": [40.0, "C"],
                        "pressure": [60.0, "bara"]}},
        {"type": "Cooler", "name": "Cooler 1", "inlet": "feed",
         "properties": {"outletTemperature": [30.0, "C"]}}
      ],
      "autoRun": true
    }

Property values use the ``[value, "unit"]`` pair form understood by the Java
builder's reflection-based setter logic.
"""

from __future__ import annotations

from typing import Dict, List, Mapping, Optional


def quantity(value: float, unit: str):
    """Return a ``[value, unit]`` property pair.

    Parameters
    ----------
    value:
        Numeric magnitude.
    unit:
        Unit of measure string understood by NeqSim setters.

    Returns
    -------
    list
        A two-element ``[float, str]`` list.
    """
    return [float(value), str(unit)]


class FlowsheetSpec:
    """Fluent, JVM-free builder for a NeqSim process JSON definition.

    Parameters
    ----------
    name:
        Human-readable flowsheet name stored in metadata.
    """

    def __init__(self, name: str = "studio-process"):
        self.name = name
        self._fluid: Optional[dict] = None
        self._units: List[dict] = []
        self._last: Optional[str] = None

    # ── fluid ──
    def fluid(self, components: Mapping[str, float], temperature_c: float = 15.0,
              pressure_bara: float = 1.01325, model: str = "SRK",
              mixing_rule: str = "classic") -> "FlowsheetSpec":
        """Define the default fluid for streams.

        Parameters
        ----------
        components:
            Mapping of component name to mole fraction.
        temperature_c:
            Fluid temperature in Celsius.
        pressure_bara:
            Fluid pressure in bara.
        model:
            EOS model token (``"SRK"``, ``"PR"``, ``"CPA"`` ...).
        mixing_rule:
            Mixing rule name.

        Returns
        -------
        FlowsheetSpec
            ``self`` for chaining.
        """
        self._fluid = {
            "model": str(model).upper(),
            "temperature": float(temperature_c) + 273.15,
            "pressure": float(pressure_bara),
            "mixingRule": mixing_rule,
            "components": {str(k): float(v) for k, v in components.items()},
        }
        return self

    # ── generic add ──
    def add(self, unit_type: str, name: str, inlet=None,
            properties: Optional[dict] = None, **extra) -> "FlowsheetSpec":
        """Append a generic unit definition.

        Parameters
        ----------
        unit_type:
            NeqSim equipment type (``"Stream"``, ``"Separator"`` ...).
        name:
            Unique unit name.
        inlet:
            Inlet stream reference string, or a list of references for units
            that take multiple inlets (e.g. a Mixer).
        properties:
            Optional property dictionary (``{name: [value, unit]}``).
        **extra:
            Additional top-level keys merged into the unit definition.

        Returns
        -------
        FlowsheetSpec
            ``self`` for chaining.
        """
        unit: Dict[str, object] = {"type": unit_type, "name": name}
        if isinstance(inlet, (list, tuple)):
            unit["inlets"] = list(inlet)
        elif inlet is not None:
            unit["inlet"] = inlet
        if properties:
            unit["properties"] = properties
        unit.update(extra)
        self._units.append(unit)
        self._last = name
        return self

    # ── typed convenience builders ──
    def stream(self, name: str, flow: float, flow_unit: str = "MSm3/day",
               temperature_c: float = 40.0, pressure_bara: float = 60.0) -> "FlowsheetSpec":
        """Add a feed stream.

        Parameters
        ----------
        name:
            Stream name.
        flow:
            Flow rate magnitude.
        flow_unit:
            Flow rate unit (e.g. ``"MSm3/day"``, ``"kg/hr"``).
        temperature_c:
            Stream temperature in Celsius.
        pressure_bara:
            Stream pressure in bara.

        Returns
        -------
        FlowsheetSpec
            ``self`` for chaining.
        """
        return self.add("Stream", name, properties={
            "flowRate": quantity(flow, flow_unit),
            "temperature": quantity(temperature_c, "C"),
            "pressure": quantity(pressure_bara, "bara"),
        })

    def cooler(self, name: str, outlet_temperature_c: float, inlet=None) -> "FlowsheetSpec":
        """Add a cooler with a target outlet temperature.

        Parameters
        ----------
        name:
            Unit name.
        outlet_temperature_c:
            Target outlet temperature in Celsius.
        inlet:
            Inlet reference (defaults to the previous unit).

        Returns
        -------
        FlowsheetSpec
            ``self`` for chaining.
        """
        return self.add("Cooler", name, inlet=self._resolve(inlet), properties={
            "outletTemperature": quantity(outlet_temperature_c, "C")})

    def heater(self, name: str, outlet_temperature_c: float, inlet=None) -> "FlowsheetSpec":
        """Add a heater with a target outlet temperature.

        Parameters
        ----------
        name:
            Unit name.
        outlet_temperature_c:
            Target outlet temperature in Celsius.
        inlet:
            Inlet reference (defaults to the previous unit).

        Returns
        -------
        FlowsheetSpec
            ``self`` for chaining.
        """
        return self.add("Heater", name, inlet=self._resolve(inlet), properties={
            "outletTemperature": quantity(outlet_temperature_c, "C")})

    def compressor(self, name: str, outlet_pressure_bara: float,
                   inlet=None, polytropic_efficiency: Optional[float] = None) -> "FlowsheetSpec":
        """Add a compressor with a discharge pressure.

        Parameters
        ----------
        name:
            Unit name.
        outlet_pressure_bara:
            Discharge pressure in bara.
        inlet:
            Inlet reference (defaults to the previous unit).
        polytropic_efficiency:
            Optional polytropic efficiency fraction (0-1).

        Returns
        -------
        FlowsheetSpec
            ``self`` for chaining.
        """
        props: Dict[str, object] = {"outletPressure": quantity(outlet_pressure_bara, "bara")}
        if polytropic_efficiency is not None:
            props["polytropicEfficiency"] = float(polytropic_efficiency)
        return self.add("Compressor", name, inlet=self._resolve(inlet), properties=props)

    def pump(self, name: str, outlet_pressure_bara: float, inlet=None) -> "FlowsheetSpec":
        """Add a pump with a discharge pressure.

        Parameters
        ----------
        name:
            Unit name.
        outlet_pressure_bara:
            Discharge pressure in bara.
        inlet:
            Inlet reference (defaults to the previous unit).

        Returns
        -------
        FlowsheetSpec
            ``self`` for chaining.
        """
        return self.add("Pump", name, inlet=self._resolve(inlet), properties={
            "outletPressure": quantity(outlet_pressure_bara, "bara")})

    def valve(self, name: str, outlet_pressure_bara: float, inlet=None) -> "FlowsheetSpec":
        """Add a throttling valve with an outlet pressure.

        Parameters
        ----------
        name:
            Unit name.
        outlet_pressure_bara:
            Downstream pressure in bara.
        inlet:
            Inlet reference (defaults to the previous unit).

        Returns
        -------
        FlowsheetSpec
            ``self`` for chaining.
        """
        return self.add("ThrottlingValve", name, inlet=self._resolve(inlet), properties={
            "outletPressure": quantity(outlet_pressure_bara, "bara")})

    def separator(self, name: str, inlet=None, kind: str = "Separator") -> "FlowsheetSpec":
        """Add a separator (two- or three-phase).

        Parameters
        ----------
        name:
            Unit name.
        inlet:
            Inlet reference (defaults to the previous unit).
        kind:
            Separator type token (``"Separator"`` or ``"ThreePhaseSeparator"``).

        Returns
        -------
        FlowsheetSpec
            ``self`` for chaining.
        """
        return self.add(kind, name, inlet=self._resolve(inlet))

    def mixer(self, name: str, inlets: List[str]) -> "FlowsheetSpec":
        """Add a mixer fed by several streams.

        Parameters
        ----------
        name:
            Unit name.
        inlets:
            List of inlet stream references.

        Returns
        -------
        FlowsheetSpec
            ``self`` for chaining.
        """
        return self.add("Mixer", name, inlet=list(inlets))

    def recycle(self, name: str, inlet: str, tolerance: float = 1.0e-3) -> "FlowsheetSpec":
        """Add a recycle unit.

        Parameters
        ----------
        name:
            Unit name.
        inlet:
            Inlet stream reference (the tear stream).
        tolerance:
            Convergence tolerance.

        Returns
        -------
        FlowsheetSpec
            ``self`` for chaining.
        """
        return self.add("Recycle", name, inlet=inlet, properties={
            "tolerance": float(tolerance)})

    # ── helpers ──
    def _resolve(self, inlet):
        """Resolve an inlet reference, defaulting to the previous unit.

        Parameters
        ----------
        inlet:
            Explicit inlet reference, or ``None`` to use the last-added unit.

        Returns
        -------
        str or None
            The resolved reference.
        """
        if inlet is not None:
            return inlet
        return self._last

    @property
    def last(self) -> Optional[str]:
        """Name of the most recently added unit.

        Returns
        -------
        str or None
            The last unit name, or ``None`` when empty.
        """
        return self._last

    def to_dict(self, auto_run: bool = True, include_fluid: bool = True) -> dict:
        """Render the accumulated spec to a JSON-ready dict.

        Parameters
        ----------
        auto_run:
            Whether to set ``autoRun`` so the builder runs the process.
        include_fluid:
            Whether to embed the ``fluid`` section.  Set False when a pre-built
            fluid object will be supplied to ``fromJsonAndRun``.

        Returns
        -------
        dict
            The flowsheet definition.

        Raises
        ------
        ValueError
            If no units were added, or fluid is required but missing.
        """
        if not self._units:
            raise ValueError("flowsheet has no units")
        definition: Dict[str, object] = {
            "metadata": {"name": self.name},
            "process": list(self._units),
            "autoRun": bool(auto_run),
        }
        if include_fluid:
            if self._fluid is None:
                raise ValueError(
                    "no fluid defined; call .fluid(...) or build with include_fluid=False "
                    "and pass a pre-built fluid object"
                )
            definition["fluid"] = self._fluid
        return definition
