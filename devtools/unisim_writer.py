r"""
UniSim Design COM writer — creates .usc files from NeqSim ProcessSystem /
ProcessModel JSON exports.

This is the reverse of ``unisim_reader.py``: it takes the JSON produced by
``ProcessSystem.toJson()`` or ``ProcessModel.toJson()`` and builds a fully
wired UniSim Design simulation via COM automation.

Requirements:
    pip install pywin32  (Windows only — COM automation)
    UniSim Design R490+ installed and licensed

Known Limitations (UniSim COM):
    - New cases created via SimulationCases.Add() have restricted COM write
      access.  The writer prefers to open an existing .usc *template* file
      (any licensed-saved UniSim file works) and build the process inside it.
      Set ``template_path`` in the constructor to enable this mode.
    - Solver pause (solver.CanSolve = False) is unreliable on blank cases.
    - Temperature.SetValue() works on new streams, but Pressure and MassFlow
      require the ``Calculate()`` fallback (sets value in internal units:
      kPa for pressure, kg/s for mass flow, °C for temperature).
    - Separator Feeds.Add() may fail while the solver is active; connections
      are attempted with retries.
    - Before first use, clear the COM type-library cache if you see
      E_ACCESSDENIED errors::

          import shutil, os
          p = os.path.join(os.environ['LOCALAPPDATA'], 'Temp', 'gen_py')
          shutil.rmtree(p, ignore_errors=True)

Architecture:
    NeqSimJsonParser  — parses the NeqSim JSON, resolves topology
    UniSimWriter      — creates UniSim objects via COM from parsed model

Usage (from NeqSim JSON string):
    from devtools.unisim_writer import UniSimWriter

    writer = UniSimWriter(visible=True)
    writer.build_from_json(neqsim_json_str, save_path="output.usc")
    writer.close()

Usage (from a running NeqSim ProcessSystem):
    from neqsim import jneqsim
    # ... build and run process ...
    json_str = str(process.toJson())

    from devtools.unisim_writer import UniSimWriter
    writer = UniSimWriter(visible=True)
    writer.build_from_json(json_str, save_path="output.usc")
    writer.close()

Usage (ProcessModel multi-area):
    json_str = str(processModel.toJson())  # {"areas": {"sep": {...}, "comp": {...}}}

    writer = UniSimWriter(visible=True)
    writer.build_from_json(json_str, save_path="platform.usc")
    writer.close()
"""

import json
import logging
import os
import time
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Tuple

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Reverse mapping dictionaries (NeqSim -> UniSim)
# ---------------------------------------------------------------------------

# NeqSim component name -> UniSim component name
NEQSIM_TO_UNISIM_COMPONENT = {
    'nitrogen': 'Nitrogen',
    'CO2': 'CO2',
    'methane': 'Methane',
    'ethane': 'Ethane',
    'propane': 'Propane',
    'i-butane': 'i-Butane',
    'n-butane': 'n-Butane',
    'i-pentane': 'i-Pentane',
    'n-pentane': 'n-Pentane',
    'n-hexane': 'n-Hexane',
    'n-heptane': 'n-Heptane',
    'n-octane': 'n-Octane',
    'n-nonane': 'n-Nonane',
    'nC10': 'n-Decane',
    'nC11': 'nC11',
    'nC12': 'nC12',
    'nC13': 'nC13',
    'nC14': 'nC14',
    'nC15': 'nC15',
    'nC16': 'nC16',
    'nC17': 'nC17',
    'nC18': 'nC18',
    'nC19': 'nC19',
    'nC20': 'nC20',
    'water': 'H2O',
    'MEG': 'EGlycol',
    'hydrogen': 'Hydrogen',
    'H2S': 'H2S',
    'oxygen': 'Oxygen',
    'argon': 'Argon',
    'helium': 'Helium',
    'TEG': 'TEGlycol',
    'DEG': 'DEGlycol',
    'methanol': 'MeOH',
    'ethanol': 'Ethanol',
    'COS': 'COS',
    'SO2': 'SO2',
    'ammonia': 'NH3',
    'DEA': 'DEAmine',
    'MEA': 'MEAmine',
    'MDEA': 'MDEAmine',
    'benzene': 'Benzene',
    'toluene': 'Toluene',
    'cyclohexane': 'Cyclohexane',
    'CO': 'CO',
    'propene': 'Propylene',
    'ethylene': 'Ethylene',
    '1-butene': '1-Butene',
    'c2-butene': 'cis-2-Butene',
    't2-butene': 'trans-2-Butene',
    'isobutene': 'Isobutene',
    'acetic acid': 'AceticAcid',
    'c-hexane': 'c-Hexane',
}

# Approximate molar mass (g/mol) for common components, used to convert
# mass flow (kg/h) to molar flow (kgmole/s) for the UniSim COM feed spec.
_COMPONENT_MW = {
    'nitrogen': 28.014, 'CO2': 44.010, 'methane': 16.043,
    'ethane': 30.070, 'propane': 44.096, 'i-butane': 58.123,
    'n-butane': 58.123, 'i-pentane': 72.150, 'n-pentane': 72.150,
    'n-hexane': 86.177, 'n-heptane': 100.204, 'n-octane': 114.231,
    'n-nonane': 128.258, 'nC10': 142.285, 'water': 18.015,
    'hydrogen': 2.016, 'H2S': 34.081, 'oxygen': 31.998,
    'argon': 39.948, 'helium': 4.003, 'CO': 28.010,
    'methanol': 32.042, 'ethanol': 46.069, 'benzene': 78.114,
    'toluene': 92.141, 'cyclohexane': 84.161, 'MEG': 62.068,
    'TEG': 150.174, 'DEG': 106.120, 'ammonia': 17.031,
    'SO2': 64.066, 'COS': 60.075, 'ethylene': 28.054,
    'propene': 42.081,
}

# NeqSim EOS model name -> UniSim property package name
NEQSIM_TO_UNISIM_PROPERTY_PACKAGE = {
    'SRK': 'SRK',
    'PR': 'Peng-Robinson',
    'CPA': 'CPA',
    'GERG2008': 'GERG 2008',
    'UMRPRU': 'Peng-Robinson',  # closest UniSim equivalent
    'PCSAFT': 'SRK',  # fallback — no PC-SAFT in UniSim
}

# NeqSim equipment type -> UniSim TypeName for creation
NEQSIM_TO_UNISIM_OPERATION_TYPE = {
    'Stream': 'materialstream',
    'ThrottlingValve': 'valveop',
    'Separator': 'flashtank',
    'ThreePhaseSeparator': 'sep3op',
    'Mixer': 'mixerop',
    'Splitter': 'teeop',
    'Compressor': 'compressor',
    'Cooler': 'coolerop',
    'Heater': 'heaterop',
    'Pump': 'pumpop',
    'Expander': 'expandop',
    'HeatExchanger': 'heatexop',
    'Recycle': 'recycle',
    'AdiabaticPipe': 'pipeseg',
}


# ---------------------------------------------------------------------------
# Parsed model classes
# ---------------------------------------------------------------------------

@dataclass
class ParsedStream:
    """A feed stream extracted from the JSON."""
    name: str
    temperature_K: Optional[float] = None
    pressure_bara: Optional[float] = None
    flow_rate_kghr: Optional[float] = None


@dataclass
class ParsedUnit:
    """A process equipment unit extracted from the JSON."""
    name: str
    type_name: str  # NeqSim type (e.g. "Compressor", "Separator")
    inlet: Optional[str] = None  # single inlet ref (dot-notation)
    inlets: Optional[List[str]] = None  # multi-inlet refs
    properties: Dict[str, Any] = field(default_factory=dict)
    # Resolved connectivity
    feed_stream_names: List[str] = field(default_factory=list)
    product_stream_names: List[str] = field(default_factory=list)


@dataclass
class ParsedFluid:
    """Fluid definition from the JSON."""
    model: str = 'SRK'
    temperature_K: float = 298.15
    pressure_bara: float = 1.0
    mixing_rule: str = 'classic'
    components: Dict[str, float] = field(default_factory=dict)
    multi_phase_check: bool = False


@dataclass
class ParsedProcessSystem:
    """A complete parsed process system."""
    name: str = 'Main'
    fluid: Optional[ParsedFluid] = None
    feed_streams: List[ParsedStream] = field(default_factory=list)
    units: List[ParsedUnit] = field(default_factory=list)


# ---------------------------------------------------------------------------
# JSON Parser
# ---------------------------------------------------------------------------

class NeqSimJsonParser:
    """Parses NeqSim JSON (from ProcessSystem.toJson / ProcessModel.toJson)
    into structured data suitable for UniSim COM creation.
    """

    def __init__(self):
        self._warnings: List[str] = []

    @property
    def warnings(self) -> List[str]:
        return self._warnings

    def parse(self, json_str: str) -> List[ParsedProcessSystem]:
        """Parse a JSON string into one or more process systems.

        Handles both single-area (ProcessSystem) and multi-area (ProcessModel)
        JSON formats.

        Returns:
            List of ParsedProcessSystem objects.
        """
        data = json.loads(json_str)
        systems = []

        if 'areas' in data:
            # ProcessModel format: {"areas": {"name1": {...}, "name2": {...}}}
            for area_name, area_data in data['areas'].items():
                ps = self._parse_single_system(area_data, area_name)
                systems.append(ps)
        elif 'fluid' in data and 'process' in data:
            # Single ProcessSystem format
            ps = self._parse_single_system(data, 'Main')
            systems.append(ps)
        else:
            self._warnings.append(
                "Unrecognized JSON format — expected 'areas' or 'fluid'+'process' keys")

        return systems

    def _parse_single_system(self, data: Dict, name: str) -> ParsedProcessSystem:
        """Parse a single process system JSON object."""
        ps = ParsedProcessSystem(name=name)

        # Fluid
        fluid_data = data.get('fluid', {})
        ps.fluid = ParsedFluid(
            model=fluid_data.get('model', 'SRK'),
            temperature_K=fluid_data.get('temperature', 298.15),
            pressure_bara=fluid_data.get('pressure', 1.0),
            mixing_rule=fluid_data.get('mixingRule', 'classic'),
            components=fluid_data.get('components', {}),
            multi_phase_check=fluid_data.get('multiPhaseCheck', False),
        )

        # Process units
        process_arr = data.get('process', [])
        for entry in process_arr:
            type_name = entry.get('type', '')
            unit_name = entry.get('name', '')

            if type_name == 'Stream':
                # Feed stream
                stream = ParsedStream(name=unit_name)
                props = entry.get('properties', {})

                # Temperature: either bare number (Kelvin) or [value, unit] array
                temp = props.get('temperature')
                if isinstance(temp, list):
                    val, unit = temp[0], temp[1] if len(temp) > 1 else 'K'
                    if unit == 'C':
                        stream.temperature_K = val + 273.15
                    else:
                        stream.temperature_K = val
                elif isinstance(temp, (int, float)):
                    stream.temperature_K = temp

                # Pressure
                pres = props.get('pressure')
                if isinstance(pres, list):
                    val, unit = pres[0], pres[1] if len(pres) > 1 else 'bara'
                    stream.pressure_bara = val  # assume bara
                elif isinstance(pres, (int, float)):
                    stream.pressure_bara = pres

                # Flow rate
                flow = props.get('flowRate')
                if isinstance(flow, list):
                    stream.flow_rate_kghr = flow[0]
                elif isinstance(flow, (int, float)):
                    stream.flow_rate_kghr = flow

                ps.feed_streams.append(stream)
            else:
                # Equipment
                unit = ParsedUnit(
                    name=unit_name,
                    type_name=type_name,
                    inlet=entry.get('inlet'),
                    inlets=entry.get('inlets'),
                    properties=entry.get('properties', {}),
                )
                ps.units.append(unit)

        # Resolve connectivity: build internal stream names
        self._resolve_connectivity(ps)

        return ps

    def _resolve_connectivity(self, ps: ParsedProcessSystem):
        """Resolve dot-notation inlet references to feed/product stream names.

        For each equipment unit, determine which internal stream names connect
        it to upstream equipment. This builds the feed_stream_names and
        product_stream_names lists used by the UniSim writer to wire
        operations together.
        """
        # Map each equipment name to its parsed unit
        unit_by_name = {u.name: u for u in ps.units}

        # Assign product stream names to each unit based on its type
        for unit in ps.units:
            t = unit.type_name
            if t in ('Separator', 'ThreePhaseSeparator'):
                if t == 'ThreePhaseSeparator':
                    unit.product_stream_names = [
                        f"{unit.name}_gasOut",
                        f"{unit.name}_oilOut",
                        f"{unit.name}_waterOut",
                    ]
                else:
                    unit.product_stream_names = [
                        f"{unit.name}_gasOut",
                        f"{unit.name}_liquidOut",
                    ]
            elif t == 'Splitter':
                n_splits = unit.properties.get('splitNumber', 2)
                if isinstance(unit.properties.get('splitFactors'), list):
                    n_splits = len(unit.properties['splitFactors'])
                unit.product_stream_names = [
                    f"{unit.name}_split{i}" for i in range(n_splits)
                ]
            elif t == 'HeatExchanger':
                unit.product_stream_names = [
                    f"{unit.name}_hx0",
                    f"{unit.name}_hx1",
                ]
            else:
                # Single outlet
                unit.product_stream_names = [f"{unit.name}_outlet"]

        # Also track feed stream names as products
        feed_stream_product_map = {}
        for fs in ps.feed_streams:
            feed_stream_product_map[fs.name] = fs.name

        # Build a resolution map: dot-notation ref -> internal stream name
        ref_to_stream = {}
        for fs in ps.feed_streams:
            ref_to_stream[fs.name] = fs.name

        for unit in ps.units:
            t = unit.type_name
            if t == 'ThreePhaseSeparator':
                ref_to_stream[f"{unit.name}.gasOut"] = unit.product_stream_names[0]
                ref_to_stream[f"{unit.name}.oilOut"] = unit.product_stream_names[1]
                ref_to_stream[f"{unit.name}.waterOut"] = unit.product_stream_names[2]
            elif t == 'Separator':
                ref_to_stream[f"{unit.name}.gasOut"] = unit.product_stream_names[0]
                ref_to_stream[f"{unit.name}.liquidOut"] = unit.product_stream_names[1]
            elif t == 'Splitter':
                for i, sn in enumerate(unit.product_stream_names):
                    ref_to_stream[f"{unit.name}.split{i}"] = sn
            elif t == 'HeatExchanger':
                ref_to_stream[f"{unit.name}.outlet"] = unit.product_stream_names[0]
                ref_to_stream[f"{unit.name}.outlet1"] = unit.product_stream_names[1]
                ref_to_stream[f"{unit.name}.hx0"] = unit.product_stream_names[0]
                ref_to_stream[f"{unit.name}.hx1"] = unit.product_stream_names[1]
            else:
                ref_to_stream[f"{unit.name}.outlet"] = unit.product_stream_names[0]

        # Resolve feed_stream_names for each unit
        for unit in ps.units:
            if unit.inlets:
                for ref in unit.inlets:
                    resolved = ref_to_stream.get(ref, ref)
                    unit.feed_stream_names.append(resolved)
            elif unit.inlet:
                resolved = ref_to_stream.get(unit.inlet, unit.inlet)
                unit.feed_stream_names.append(resolved)


# ---------------------------------------------------------------------------
# UniSim COM Writer
# ---------------------------------------------------------------------------

class UniSimWriter:
    """Creates a UniSim Design simulation from NeqSim JSON via COM.

    The writer:
    1. Starts (or connects to) UniSim Design
    2. Creates a new simulation case
    3. Sets up a fluid package with the correct property package and components
    4. Creates material streams with T, P, flow, and composition
    5. Creates operations (equipment) and wires feeds/products
    6. Optionally saves the .usc file

    For multi-area ProcessModel JSON, each area becomes a sub-flowsheet
    in UniSim.
    """

    # COM sleep durations (seconds) for stability
    COM_DELAY_SHORT = 0.3
    COM_DELAY_MEDIUM = 1.0
    COM_DELAY_LONG = 2.0
    # Retry limits for solver-contended writes
    MAX_RETRIES = 5
    RETRY_DELAY = 1.0

    def __init__(self, visible: bool = True,
                 template_path: Optional[str] = None):
        """Initialize the writer.

        Args:
            visible: If True, show the UniSim GUI window (recommended for
                     debugging). If False, run headless.
            template_path: Optional path to a licensed .usc file to use as
                           a starting template.  Using a template provides
                           reliable COM write access (solver pause, property
                           setting).  The template's fluid package and
                           equipment will be replaced.  If None, a new
                           blank case is created via ``SimulationCases.Add()``
                           (write access may be limited).
        """
        self._app = None
        self._case = None
        self._visible = visible
        self._template_path = template_path
        self._warnings: List[str] = []
        # Track created COM objects by name for wiring
        self._stream_objects: Dict[str, Any] = {}
        self._operation_objects: Dict[str, Any] = {}
        # Fluid package component names (set during _setup_fluid_package)
        self._fp_component_names: List[str] = []

    @property
    def warnings(self) -> List[str]:
        return self._warnings

    def _ensure_app(self):
        """Start UniSim if not already running.

        Also clears the gencache type-library cache for UniSim to
        prevent stale early-bound wrappers from causing E_ACCESSDENIED.
        """
        if self._app is None:
            # Clear gencache to avoid stale type-library wrappers
            self._clear_gencache()
            import win32com.client
            logger.info("Starting UniSim Design via COM...")
            self._app = win32com.client.dynamic.Dispatch(
                'UnisimDesign.Application')
            self._app.Visible = self._visible
            time.sleep(self.COM_DELAY_LONG)
            logger.info("UniSim started.")

    @staticmethod
    def _clear_gencache():
        """Remove UniSim type-library cache (prevents E_ACCESSDENIED)."""
        import shutil
        gen_py = os.path.join(
            os.environ.get('LOCALAPPDATA', ''), 'Temp', 'gen_py')
        if os.path.isdir(gen_py):
            for item in os.listdir(gen_py):
                # UniSim type-library GUID prefix
                if '707BF17F' in item:
                    shutil.rmtree(
                        os.path.join(gen_py, item), ignore_errors=True)
                    logger.debug("Cleared UniSim gencache: %s", item)

    def _open_or_create_case(self):
        """Open a template .usc file or create a new blank case.

        Returns:
            COM reference to the simulation case.
        """
        if self._template_path and os.path.isfile(self._template_path):
            abs_tp = os.path.abspath(self._template_path)
            logger.info("Opening template: %s", abs_tp)
            case = self._app.SimulationCases.Open(abs_tp)
            time.sleep(self.COM_DELAY_LONG + 2)
            return case

        # Fallback: create a new blank case
        logger.info("Creating new blank case via SimulationCases.Add()…")
        try:
            case = self._app.SimulationCases.Add()
        except Exception:
            case = self._app.SimulationCases.Add("")
        time.sleep(self.COM_DELAY_LONG + 2)
        return case

    def _pause_solver(self):
        """Attempt to pause the UniSim solver.

        Returns:
            The solver COM object, or None if it could not be paused.
        """
        try:
            solver = self._case.Solver
            solver.CanSolve = False
            time.sleep(self.COM_DELAY_MEDIUM)
            logger.info("Solver paused.")
            return solver
        except Exception as e:
            self._warnings.append(
                "Could not pause solver (building with solver active): %s" % e)
            return None

    @staticmethod
    def _set_variable(variable, value, unit_str, name_hint=""):
        """Set a UniSim process variable, with Calculate() fallback.

        The COM variable model has two paths:
        1. ``SetValue(value, unit)`` — works when ``CanModify`` is True.
        2. ``Calculate(value)`` — always works but uses internal units
           (kPa for pressure, °C for temperature, kg/s for mass flow).

        Args:
            variable: COM variable object (e.g. ``ms.Temperature``).
            value:    The engineering value to set.
            unit_str: Unit string for **SetValue** (e.g. 'C', 'bar', 'kg/h').
            name_hint: For logging only.

        Returns:
            True if the value was set, False otherwise.
        """
        # Attempt SetValue first
        try:
            variable.SetValue(value, unit_str)
            return True
        except Exception:
            pass

        # Fallback: use Calculate() with internal-unit conversion
        try:
            internal = UniSimWriter._to_internal_unit(value, unit_str)
            if internal is not None:
                variable.Calculate(internal)
                return True
        except Exception:
            pass

        return False

    @staticmethod
    def _to_internal_unit(value, unit_str):
        """Convert *value* in *unit_str* to UniSim internal units.

        Internal units: pressure = kPa, temperature = °C, mass flow = kg/s,
        molar flow = kgmole/h, energy = kJ/h.
        """
        u = unit_str.lower().strip()
        if u in ('c', '°c', 'degc'):
            return value  # already °C
        if u in ('k', 'kelvin'):
            return value - 273.15
        if u in ('bar', 'bara'):
            return value * 100.0  # bar → kPa
        if u in ('kpa',):
            return value
        if u in ('pa',):
            return value / 1000.0
        if u in ('psi', 'psia'):
            return value * 6.89476
        if u in ('kg/h', 'kg/hr'):
            return value / 3600.0  # kg/h → kg/s
        if u in ('kg/s',):
            return value
        return None  # unknown unit — skip Calculate fallback

    @staticmethod
    def _estimate_molar_mass(fluid: 'ParsedFluid') -> Optional[float]:
        """Estimate mixture molar mass (g/mol) from composition.

        Returns None if any component MW is unknown.
        """
        if not fluid or not fluid.components:
            return None
        mw_mix = 0.0
        for comp, frac in fluid.components.items():
            mw = _COMPONENT_MW.get(comp)
            if mw is None:
                return None  # unknown component — can't compute
            mw_mix += frac * mw
        return mw_mix if mw_mix > 0 else None

    def close(self):
        """Close UniSim application."""
        if self._app is not None:
            try:
                self._app.Quit()
            except Exception:
                pass
            self._app = None

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()

    def build_from_json(self, json_str: str,
                        save_path: Optional[str] = None) -> Any:
        """Build a UniSim simulation from NeqSim JSON.

        Args:
            json_str: JSON string from ProcessSystem.toJson() or
                      ProcessModel.toJson().
            save_path: Optional path to save the .usc file. If None,
                       the case remains open in UniSim without saving.

        Returns:
            The UniSim case COM object.
        """
        parser = NeqSimJsonParser()
        systems = parser.parse(json_str)
        self._warnings.extend(parser.warnings)

        if not systems:
            raise ValueError("No process systems found in JSON")

        self._ensure_app()

        # Create or open a simulation case
        self._case = self._open_or_create_case()

        # Pause the solver while we build the flowsheet
        solver = self._pause_solver()

        if len(systems) == 1:
            # Single system — build in main flowsheet
            self._build_system_in_flowsheet(
                systems[0], self._case.Flowsheet, self._case.BasisManager)
        else:
            # Multi-area: build first system in main flowsheet for fluid
            # package, then add sub-flowsheets for each area
            # UniSim requires a fluid package on the main flowsheet first
            self._setup_fluid_package(systems[0].fluid, self._case.BasisManager)
            for ps in systems:
                self._build_area_as_subflowsheet(ps)

        # Enable solver and re-apply flow rates on feed streams.
        # Flow rates set with Calculate() while the solver is paused may
        # not persist because UniSim needs a converged composition first.
        # After enabling the solver, re-setting the flow allows UniSim to
        # propagate it through the flowsheet.
        if solver is not None:
            try:
                solver.CanSolve = True
                time.sleep(self.COM_DELAY_LONG)
            except Exception as e:
                self._warnings.append("Could not re-enable solver: %s" % e)

        # Re-set feed stream flow rates via MolarFlow.Calculate.
        # MolarFlow (internal unit: kgmole/s) is the most reliable way
        # to set flow in UniSim COM — MassFlow.Calculate persists on the
        # feed but doesn't propagate to downstream equipment.
        for ps in systems:
            mw = self._estimate_molar_mass(ps.fluid)
            for feed in ps.feed_streams:
                if feed.flow_rate_kghr is not None:
                    ms = self._stream_objects.get(feed.name)
                    if ms is None:
                        continue
                    # Try MolarFlow first (converts kg/h -> kgmole/s)
                    flow_set = False
                    if mw and mw > 0:
                        kgmol_s = feed.flow_rate_kghr / 3600.0 / mw
                        try:
                            ms.MolarFlow.Calculate(kgmol_s)
                            flow_set = True
                            time.sleep(self.COM_DELAY_MEDIUM)
                            logger.info(
                                "Set flow on %s: %.0f kg/h "
                                "(%.3f kgmol/s, MW=%.1f)",
                                feed.name, feed.flow_rate_kghr,
                                kgmol_s, mw)
                        except Exception:
                            pass
                    # Fallback: MassFlow
                    if not flow_set:
                        if not self._set_variable(
                                ms.MassFlow, feed.flow_rate_kghr,
                                'kg/h', f"{feed.name}.F"):
                            self._warnings.append(
                                f"Could not set flow on '{feed.name}'")

        # Save if requested
        if save_path:
            abs_path = os.path.abspath(save_path)
            try:
                self._case.SaveAs(abs_path)
                logger.info("Saved UniSim case: %s", abs_path)
            except Exception as e:
                self._warnings.append(f"Could not save: {e}")

        return self._case

    def build_from_process_system(self, process_system,
                                  save_path: Optional[str] = None) -> Any:
        """Build a UniSim simulation from a live NeqSim ProcessSystem object.

        Args:
            process_system: A NeqSim ProcessSystem (Java object via jpype/jneqsim).
            save_path: Optional path to save the .usc file.

        Returns:
            The UniSim case COM object.
        """
        json_str = str(process_system.toJson())
        return self.build_from_json(json_str, save_path)

    def build_from_process_model(self, process_model,
                                 save_path: Optional[str] = None) -> Any:
        """Build a UniSim simulation from a live NeqSim ProcessModel object.

        Args:
            process_model: A NeqSim ProcessModel (Java object via jpype/jneqsim).
            save_path: Optional path to save the .usc file.

        Returns:
            The UniSim case COM object.
        """
        json_str = str(process_model.toJson())
        return self.build_from_json(json_str, save_path)

    # -----------------------------------------------------------------
    # Internal: build a system in a flowsheet
    # -----------------------------------------------------------------

    def _build_system_in_flowsheet(self, ps: ParsedProcessSystem,
                                   flowsheet, basis_manager):
        """Build an entire process system into a UniSim flowsheet."""
        # 1. Fluid package
        self._setup_fluid_package(ps.fluid, basis_manager)

        # 2. Feed streams
        for feed in ps.feed_streams:
            self._create_material_stream(flowsheet, feed, ps.fluid)

        # 3. Equipment (in order — JSON is already topologically sorted)
        for unit in ps.units:
            self._create_operation(flowsheet, unit, ps)

    def _build_area_as_subflowsheet(self, ps: ParsedProcessSystem):
        """Build a process area as a UniSim sub-flowsheet.

        Note: UniSim sub-flowsheets share the parent's fluid package,
        so we only create streams and operations here.
        """
        main_fs = self._case.Flowsheet

        # For multi-area, we build everything in the main flowsheet
        # with name prefixes to avoid collisions, since creating
        # sub-flowsheets via COM is non-trivial.
        prefix = f"{ps.name}_" if ps.name != 'Main' else ''

        for feed in ps.feed_streams:
            prefixed_feed = ParsedStream(
                name=prefix + feed.name,
                temperature_K=feed.temperature_K,
                pressure_bara=feed.pressure_bara,
                flow_rate_kghr=feed.flow_rate_kghr,
            )
            self._create_material_stream(main_fs, prefixed_feed, ps.fluid)

        for unit in ps.units:
            # Create a copy with prefixed names for multi-area
            prefixed_unit = ParsedUnit(
                name=prefix + unit.name,
                type_name=unit.type_name,
                inlet=unit.inlet,
                inlets=unit.inlets,
                properties=dict(unit.properties),
                feed_stream_names=[prefix + s for s in unit.feed_stream_names],
                product_stream_names=[prefix + s for s in unit.product_stream_names],
            )
            self._create_operation(main_fs, prefixed_unit, ps)

    # -----------------------------------------------------------------
    # Fluid package
    # -----------------------------------------------------------------

    def _setup_fluid_package(self, fluid: ParsedFluid, basis_manager):
        """Create and configure a UniSim fluid package.

        If the case already has a fluid package (e.g. from a template),
        the existing package is reused and only missing components are added.
        """
        pp_name = NEQSIM_TO_UNISIM_PROPERTY_PACKAGE.get(
            fluid.model, 'SRK')

        # Get or create a fluid package.
        fp = None
        try:
            count = basis_manager.FluidPackages.Count
        except Exception:
            count = 0

        if count > 0:
            fp = basis_manager.FluidPackages.Item(0)
            logger.info("Using existing fluid package (count=%d)", count)
        else:
            try:
                fp = basis_manager.FluidPackages.Add("Basis-1")
                time.sleep(self.COM_DELAY_SHORT)
                logger.info("Created new fluid package 'Basis-1'")
            except Exception as e:
                self._warnings.append(
                    f"Could not create fluid package: {e}")
                return

        # Set property package (skip if already correct or fails)
        try:
            fp.PropertyPackageName = pp_name
            time.sleep(self.COM_DELAY_SHORT)
            logger.info(f"Set property package: {pp_name}")
        except Exception as e:
            # May fail if already set or solver is active — not critical
            logger.debug(
                f"Property package set skipped (may already be '{pp_name}'): {e}")

        # Catalogue existing components so we don't re-add them
        existing_components = set()
        try:
            for i in range(fp.Components.Count):
                existing_components.add(fp.Components.Item(i).name)
        except Exception:
            pass
        logger.debug("Existing components in fluid package: %d",
                      len(existing_components))

        # Add components (skip those already present)
        for neqsim_name in fluid.components:
            unisim_name = NEQSIM_TO_UNISIM_COMPONENT.get(neqsim_name)
            if unisim_name is None:
                self._warnings.append(
                    f"Component '{neqsim_name}' has no UniSim mapping — skipped")
                continue
            if unisim_name in existing_components:
                logger.debug(f"Component already present: {unisim_name}")
                continue
            try:
                fp.Components.Add(unisim_name)
                time.sleep(self.COM_DELAY_SHORT)
                existing_components.add(unisim_name)
                logger.debug(f"Added component: {unisim_name}")
            except Exception as e:
                # May fail if already present under a variant name
                logger.debug(
                    f"Could not add component '{unisim_name}': {e}")

        # Store component name list for composition mapping
        time.sleep(self.COM_DELAY_MEDIUM)  # let solver settle
        self._fp_component_names = []
        try:
            n = fp.Components.Count
            for i in range(n):
                self._fp_component_names.append(fp.Components.Item(i).name)
        except Exception as e:
            logger.warning("Could not enumerate components: %s", e)
        logger.info("Fluid package components: %d",
                     len(self._fp_component_names))

    # -----------------------------------------------------------------
    # Material streams
    # -----------------------------------------------------------------

    def _create_material_stream(self, flowsheet, feed: ParsedStream,
                                fluid: ParsedFluid):
        """Create a material stream in the flowsheet."""
        try:
            ms = flowsheet.MaterialStreams.Add(feed.name)
            time.sleep(self.COM_DELAY_SHORT)
        except Exception as e:
            self._warnings.append(
                f"Could not create stream '{feed.name}': {e}")
            return

        # Set temperature (UniSim expects °C)
        if feed.temperature_K is not None:
            t_c = feed.temperature_K - 273.15
            if not self._set_variable(ms.Temperature, t_c, 'C',
                                      f"{feed.name}.T"):
                self._warnings.append(
                    f"Could not set temperature on '{feed.name}'")

        # Set pressure (bar → kPa via Calculate fallback)
        if feed.pressure_bara is not None:
            if not self._set_variable(ms.Pressure, feed.pressure_bara,
                                      'bar', f"{feed.name}.P"):
                self._warnings.append(
                    f"Could not set pressure on '{feed.name}'")

        # Set flow rate (kg/h → kg/s via Calculate fallback)
        if feed.flow_rate_kghr is not None:
            if not self._set_variable(ms.MassFlow, feed.flow_rate_kghr,
                                      'kg/h', f"{feed.name}.F"):
                self._warnings.append(
                    f"Could not set flow rate on '{feed.name}'")

        # Set composition from fluid definition
        if fluid and fluid.components:
            self._set_stream_composition(ms, fluid)

        self._stream_objects[feed.name] = ms
        logger.info(f"Created stream: {feed.name}")

    def _set_stream_composition(self, ms, fluid: ParsedFluid):
        """Set molar composition on a material stream.

        Uses the fluid package component order (cached in
        ``self._fp_component_names``) to build the composition array.
        Components not present in the NeqSim model get fraction = 0.
        """
        try:
            # Build reverse map: UniSim name → NeqSim name
            u2n = {}
            for nname, uname in NEQSIM_TO_UNISIM_COMPONENT.items():
                u2n[uname] = nname

            # Use stored fluid-package component order if available
            comp_order = self._fp_component_names if self._fp_component_names else None
            if not comp_order:
                # Fallback: read from stream itself
                comp_order = []
                try:
                    for i in range(ms.ComponentMolarFraction.Count):
                        comp_order.append(
                            ms.ComponentMolarFraction.Names[i])
                except Exception:
                    pass

            if comp_order:
                fracs = []
                for unisim_name in comp_order:
                    neqsim_name = u2n.get(unisim_name)
                    if neqsim_name and neqsim_name in fluid.components:
                        fracs.append(fluid.components[neqsim_name])
                    else:
                        fracs.append(0.0)
            else:
                # Last resort: assume order matches NeqSim
                fracs = list(fluid.components.values())

            if fracs:
                ms.ComponentMolarFraction.Values = fracs
                logger.debug("Set composition: %d components", len(fracs))

        except Exception as e:
            self._warnings.append(
                f"Could not set composition on stream: {e}")

    # -----------------------------------------------------------------
    # Internal stream creation (for wiring between operations)
    # -----------------------------------------------------------------

    def _ensure_internal_stream(self, flowsheet, stream_name: str):
        """Ensure an internal stream exists (create if needed).

        Internal streams are the intermediate streams between equipment
        units. UniSim auto-creates them when wiring operations, but we
        may need to pre-create them for certain wiring patterns.
        """
        if stream_name in self._stream_objects:
            return self._stream_objects[stream_name]

        try:
            ms = flowsheet.MaterialStreams.Add(stream_name)
            time.sleep(self.COM_DELAY_SHORT)
            self._stream_objects[stream_name] = ms
            return ms
        except Exception as e:
            self._warnings.append(
                f"Could not create internal stream '{stream_name}': {e}")
            return None

    # -----------------------------------------------------------------
    # Operations (equipment)
    # -----------------------------------------------------------------

    def _create_operation(self, flowsheet, unit: ParsedUnit,
                          ps: ParsedProcessSystem):
        """Create a UniSim operation and wire its streams."""
        unisim_type = NEQSIM_TO_UNISIM_OPERATION_TYPE.get(unit.type_name)
        if unisim_type is None:
            self._warnings.append(
                f"Equipment type '{unit.type_name}' ({unit.name}) "
                f"has no UniSim mapping — skipped")
            return

        # Create the operation
        try:
            op = flowsheet.Operations.Add(unit.name, unisim_type)
            time.sleep(self.COM_DELAY_SHORT)
        except Exception as e:
            self._warnings.append(
                f"Could not create operation '{unit.name}' "
                f"(type={unisim_type}): {e}")
            return

        self._operation_objects[unit.name] = op

        # Wire feeds
        self._wire_feeds(flowsheet, op, unit)

        # Wire products (create output streams)
        self._wire_products(flowsheet, op, unit)

        # Set equipment-specific properties
        self._set_operation_properties(op, unit)

        logger.info(
            f"Created operation: {unit.name} ({unit.type_name} -> {unisim_type})")

    def _wire_feeds(self, flowsheet, op, unit: ParsedUnit):
        """Connect feed streams to an operation."""
        for i, feed_name in enumerate(unit.feed_stream_names):
            feed_stream = self._stream_objects.get(feed_name)
            if feed_stream is None:
                feed_stream = self._ensure_internal_stream(flowsheet, feed_name)
            if feed_stream is None:
                self._warnings.append(
                    f"Feed stream '{feed_name}' not found for '{unit.name}'")
                continue

            connected = False
            type_name = unit.type_name

            # Strategy 1: FeedStream = stream_object (single-inlet equipment)
            if type_name not in ('Mixer', 'HeatExchanger'):
                try:
                    op.FeedStream = feed_stream
                    time.sleep(self.COM_DELAY_SHORT)
                    connected = True
                except Exception:
                    pass

            # Strategy 2: Feeds.Add(name) (multi-inlet / separator)
            if not connected:
                try:
                    op.Feeds.Add(feed_stream.name)
                    time.sleep(self.COM_DELAY_SHORT)
                    connected = True
                except Exception:
                    pass

            # Strategy 3: Feeds.Add(object)
            if not connected:
                try:
                    op.Feeds.Add(feed_stream)
                    time.sleep(self.COM_DELAY_SHORT)
                    connected = True
                except Exception:
                    pass

            # Strategy 4: Feed property (some separator variants)
            if not connected:
                try:
                    op.Feed = feed_stream
                    time.sleep(self.COM_DELAY_SHORT)
                    connected = True
                except Exception:
                    pass

            if not connected:
                self._warnings.append(
                    f"Could not wire feed '{feed_name}' to '{unit.name}' "
                    f"(tried FeedStream, Feeds.Add, Feed)")

    def _wire_products(self, flowsheet, op, unit: ParsedUnit):
        """Create and connect product streams to an operation."""
        type_name = unit.type_name

        for i, prod_name in enumerate(unit.product_stream_names):
            prod_stream = self._ensure_internal_stream(flowsheet, prod_name)
            if prod_stream is None:
                continue

            try:
                if type_name in ('Separator',):
                    if i == 0:
                        op.VapourProduct = prod_stream
                    elif i == 1:
                        op.LiquidProduct = prod_stream
                elif type_name == 'ThreePhaseSeparator':
                    if i == 0:
                        op.VapourProduct = prod_stream
                    elif i == 1:
                        op.LiquidProduct = prod_stream
                    elif i == 2:
                        op.WaterProduct = prod_stream
                elif type_name == 'Splitter':
                    # Splitter uses Products[] array
                    op.Products.Add(prod_stream.name)
                elif type_name == 'Mixer':
                    # Mixer has single Product
                    op.Product = prod_stream
                elif type_name == 'HeatExchanger':
                    # HX products wired by index
                    if i == 0:
                        try:
                            op.ShellSideProduct = prod_stream
                        except Exception:
                            op.Products.Add(prod_stream.name)
                    elif i == 1:
                        try:
                            op.TubeSideProduct = prod_stream
                        except Exception:
                            op.Products.Add(prod_stream.name)
                else:
                    # Single-outlet: use ProductStream
                    op.ProductStream = prod_stream
                time.sleep(self.COM_DELAY_SHORT)
            except Exception as e:
                self._warnings.append(
                    f"Could not wire product '{prod_name}' from "
                    f"'{unit.name}': {e}")

    def _set_operation_properties(self, op, unit: ParsedUnit):
        """Set equipment-specific properties on a UniSim operation."""
        props = unit.properties
        type_name = unit.type_name

        try:
            if type_name == 'Compressor':
                # Outlet pressure via product stream
                if 'outletPressure' in props:
                    try:
                        ps = op.ProductStream
                        if ps is not None:
                            self._set_variable(
                                ps.Pressure, props['outletPressure'],
                                'bar', f"{unit.name}.Pout")
                    except Exception:
                        pass
                # Efficiency
                if 'polytropicEfficiency' in props:
                    try:
                        op.PolytropicEfficiency.SetValue(
                            props['polytropicEfficiency'])
                    except Exception:
                        pass
                elif 'isentropicEfficiency' in props:
                    try:
                        op.AdiabaticEfficiency.SetValue(
                            props['isentropicEfficiency'])
                    except Exception:
                        pass

            elif type_name == 'ThrottlingValve':
                if 'outletPressure' in props:
                    try:
                        ps = op.ProductStream
                        if ps is not None:
                            self._set_variable(
                                ps.Pressure, props['outletPressure'],
                                'bar', f"{unit.name}.Pout")
                    except Exception:
                        pass

            elif type_name in ('Cooler', 'Heater'):
                # Outlet temperature
                out_temp = props.get('outletTemperature') or props.get('outTemperature')
                if out_temp is not None:
                    try:
                        ps = op.ProductStream
                        if ps is not None:
                            if isinstance(out_temp, list):
                                val = out_temp[0]
                                unit_str = out_temp[1] if len(out_temp) > 1 else 'C'
                            else:
                                # Assume Kelvin from JSON
                                val = out_temp - 273.15
                                unit_str = 'C'
                            self._set_variable(
                                ps.Temperature, val, unit_str,
                                f"{unit.name}.Tout")
                    except Exception:
                        pass
                # Pressure drop
                if 'pressureDrop' in props:
                    try:
                        op.PressureDrop.SetValue(
                            props['pressureDrop'], 'bar')
                    except Exception:
                        pass

            elif type_name == 'Pump':
                if 'outletPressure' in props:
                    try:
                        ps = op.ProductStream
                        if ps is not None:
                            self._set_variable(
                                ps.Pressure, props['outletPressure'],
                                'bar', f"{unit.name}.Pout")
                    except Exception:
                        pass
                if 'isentropicEfficiency' in props:
                    try:
                        op.AdiabaticEfficiency.SetValue(
                            props['isentropicEfficiency'])
                    except Exception:
                        pass

            elif type_name == 'Expander':
                if 'outletPressure' in props:
                    try:
                        ps = op.ProductStream
                        if ps is not None:
                            self._set_variable(
                                ps.Pressure, props['outletPressure'],
                                'bar', f"{unit.name}.Pout")
                    except Exception:
                        pass
                if 'isentropicEfficiency' in props:
                    try:
                        op.AdiabaticEfficiency.SetValue(
                            props['isentropicEfficiency'])
                    except Exception:
                        pass

            elif type_name == 'Splitter':
                # Split factors
                if 'splitFactors' in props:
                    factors = props['splitFactors']
                    # UniSim tee uses flow fractions — set on product streams
                    # This is typically handled by UniSim's tee specification
                    self._warnings.append(
                        f"Split factors for '{unit.name}' exported but "
                        f"may need manual verification in UniSim")

            elif type_name == 'AdiabaticPipe':
                if 'length' in props:
                    try:
                        op.Length.SetValue(props['length'], 'm')
                    except Exception:
                        pass
                if 'diameter' in props:
                    try:
                        op.Diameter.SetValue(props['diameter'], 'm')
                    except Exception:
                        pass

        except Exception as e:
            self._warnings.append(
                f"Error setting properties on '{unit.name}': {e}")

    # -----------------------------------------------------------------
    # Convenience: build from file
    # -----------------------------------------------------------------

    def build_from_json_file(self, json_path: str,
                             save_path: Optional[str] = None) -> Any:
        """Build a UniSim simulation from a JSON file.

        Args:
            json_path: Path to a .json file containing NeqSim JSON.
            save_path: Optional path to save the .usc file.

        Returns:
            The UniSim case COM object.
        """
        with open(json_path, 'r', encoding='utf-8') as f:
            json_str = f.read()
        return self.build_from_json(json_str, save_path)

    # -----------------------------------------------------------------
    # Reporting
    # -----------------------------------------------------------------

    def get_build_report(self) -> str:
        """Get a summary of the build including any warnings."""
        lines = [
            "UniSim Writer Build Report",
            "=" * 40,
            f"Streams created: {len(self._stream_objects)}",
            f"Operations created: {len(self._operation_objects)}",
            f"Warnings: {len(self._warnings)}",
        ]
        if self._warnings:
            lines.append("")
            lines.append("Warnings:")
            for w in self._warnings:
                lines.append(f"  - {w}")
        return "\n".join(lines)


# ---------------------------------------------------------------------------
# Standalone validation (no COM needed)
# ---------------------------------------------------------------------------

def validate_json_for_unisim(json_str: str) -> Dict[str, Any]:
    """Validate that a NeqSim JSON can be converted to UniSim.

    Checks component mappings, EOS mappings, and operation type mappings
    without requiring UniSim to be installed.

    Args:
        json_str: JSON string from ProcessSystem.toJson() or
                  ProcessModel.toJson().

    Returns:
        Dict with 'valid' (bool), 'warnings' (list), 'summary' (dict).
    """
    parser = NeqSimJsonParser()
    systems = parser.parse(json_str)

    warnings = list(parser.warnings)
    summary = {
        'n_systems': len(systems),
        'systems': [],
    }

    for ps in systems:
        sys_info = {
            'name': ps.name,
            'n_feeds': len(ps.feed_streams),
            'n_units': len(ps.units),
            'eos': ps.fluid.model if ps.fluid else 'Unknown',
        }

        # Check EOS mapping
        if ps.fluid:
            unisim_pp = NEQSIM_TO_UNISIM_PROPERTY_PACKAGE.get(ps.fluid.model)
            if unisim_pp is None:
                warnings.append(
                    f"EOS '{ps.fluid.model}' has no UniSim mapping")
            sys_info['unisim_property_package'] = unisim_pp or 'Unknown'

            # Check component mappings
            unmapped = []
            for comp in ps.fluid.components:
                if comp not in NEQSIM_TO_UNISIM_COMPONENT:
                    unmapped.append(comp)
            if unmapped:
                warnings.append(
                    f"Unmapped components: {', '.join(unmapped)}")
            sys_info['unmapped_components'] = unmapped

        # Check operation type mappings
        unmapped_ops = []
        for unit in ps.units:
            if unit.type_name not in NEQSIM_TO_UNISIM_OPERATION_TYPE:
                unmapped_ops.append(f"{unit.name} ({unit.type_name})")
        if unmapped_ops:
            warnings.append(
                f"Unmapped operation types: {', '.join(unmapped_ops)}")
        sys_info['unmapped_operations'] = unmapped_ops

        summary['systems'].append(sys_info)

    return {
        'valid': len(warnings) == 0,
        'warnings': warnings,
        'summary': summary,
    }


# ---------------------------------------------------------------------------
# CLI entry point
# ---------------------------------------------------------------------------

def main():
    """Command-line interface for the UniSim writer."""
    import argparse

    arg_parser = argparse.ArgumentParser(
        description='Convert NeqSim JSON to UniSim Design .usc file')
    arg_parser.add_argument(
        'input', help='Path to NeqSim JSON file')
    arg_parser.add_argument(
        '-o', '--output', help='Path for output .usc file',
        default=None)
    arg_parser.add_argument(
        '--validate-only', action='store_true',
        help='Only validate JSON — do not create UniSim file')
    arg_parser.add_argument(
        '--template', default=None,
        help='Path to a licensed .usc template file (recommended for '
             'reliable COM access)')
    args = arg_parser.parse_args()

    logging.basicConfig(level=logging.INFO,
                        format='%(levelname)s: %(message)s')

    with open(args.input, 'r', encoding='utf-8') as f:
        json_str = f.read()

    if args.validate_only:
        result = validate_json_for_unisim(json_str)
        print(json.dumps(result, indent=2))
        return

    output_path = args.output
    if output_path is None:
        base = os.path.splitext(args.input)[0]
        output_path = base + '.usc'

    visible = not args.hidden

    writer = UniSimWriter(visible=visible, template_path=args.template)
    try:
        writer.build_from_json(json_str, save_path=output_path)
        print(writer.get_build_report())
    finally:
        if not visible:
            writer.close()
        else:
            print("\nUniSim is still open. Close manually when done.")


if __name__ == '__main__':
    main()
