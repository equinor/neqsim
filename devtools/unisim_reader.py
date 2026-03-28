r"""
UniSim Design COM reader — extracts process models from .usc files and converts
them to NeqSim ProcessSystem / ProcessModule structures.

Requirements:
    pip install pywin32  (Windows only — COM automation)

Architecture:
    UniSimReader  — opens .usc files, extracts all data via COM
    UniSimToNeqSim — converts extracted data to NeqSim JSON builder format
    UniSimComparator — runs both UniSim and NeqSim side-by-side for verification

Usage:
    from devtools.unisim_reader import UniSimReader, UniSimToNeqSim

    reader = UniSimReader()
    model = reader.read("path/to/file.usc")
    print(model.summary())

    converter = UniSimToNeqSim(model)
    neqsim_json = converter.to_json()
    process = converter.build_and_run()
"""

import json
import os
import time
import logging
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple, Any

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Data classes for the extracted UniSim model
# ---------------------------------------------------------------------------

@dataclass
class UniSimComponent:
    """A component in a fluid package."""
    name: str
    index: int
    is_hypothetical: bool = False  # * suffix means hypo in UniSim


@dataclass
class UniSimFluidPackage:
    """A fluid package (thermodynamic basis) in UniSim."""
    name: str
    property_package: str  # e.g. "Peng-Robinson", "SRK", "ASME Steam"
    components: List[UniSimComponent] = field(default_factory=list)

    @property
    def component_names(self) -> List[str]:
        return [c.name for c in self.components]


@dataclass
class UniSimStreamData:
    """Extracted data from a material stream."""
    name: str
    temperature_C: Optional[float] = None
    pressure_bara: Optional[float] = None
    mass_flow_kgh: Optional[float] = None
    molar_flow_kgmolh: Optional[float] = None
    vapour_fraction: Optional[float] = None
    mass_density_kgm3: Optional[float] = None
    molecular_weight: Optional[float] = None
    enthalpy_kJkg: Optional[float] = None
    composition: Optional[Dict[str, float]] = None
    n_phases: Optional[int] = None
    # Phase-specific if available
    viscosity_cP: Optional[float] = None
    thermal_conductivity: Optional[float] = None
    specific_heat_kJkgC: Optional[float] = None


@dataclass
class UniSimEnergyStream:
    """Extracted data from an energy stream."""
    name: str
    heat_flow_kW: Optional[float] = None


@dataclass
class UniSimOperation:
    """Extracted data from a unit operation."""
    name: str
    type_name: str  # UniSim internal type (valveop, compressor, etc.)
    feeds: List[str] = field(default_factory=list)
    products: List[str] = field(default_factory=list)
    energy_feeds: List[str] = field(default_factory=list)
    energy_products: List[str] = field(default_factory=list)
    # Type-specific properties
    properties: Dict[str, Any] = field(default_factory=dict)


@dataclass
class UniSimFlowsheet:
    """A complete flowsheet (main or sub-flowsheet)."""
    name: str
    material_streams: List[UniSimStreamData] = field(default_factory=list)
    energy_streams: List[UniSimEnergyStream] = field(default_factory=list)
    operations: List[UniSimOperation] = field(default_factory=list)
    sub_flowsheets: List['UniSimFlowsheet'] = field(default_factory=list)


@dataclass
class UniSimModel:
    """Complete extracted UniSim model."""
    file_path: str
    file_name: str
    fluid_packages: List[UniSimFluidPackage] = field(default_factory=list)
    flowsheet: Optional[UniSimFlowsheet] = None

    def summary(self) -> str:
        """Human-readable summary of the model."""
        lines = [
            f"UniSim Model: {self.file_name}",
            f"  Fluid Packages: {len(self.fluid_packages)}",
        ]
        for fp in self.fluid_packages:
            lines.append(f"    '{fp.name}': {fp.property_package} ({len(fp.components)} components)")
        if self.flowsheet:
            lines.append(f"  Main Flowsheet: '{self.flowsheet.name}'")
            lines.append(f"    Streams: {len(self.flowsheet.material_streams)}")
            lines.append(f"    Operations: {len(self.flowsheet.operations)}")
            lines.append(f"    Sub-Flowsheets: {len(self.flowsheet.sub_flowsheets)}")
            for sf in self.flowsheet.sub_flowsheets:
                lines.append(f"      '{sf.name}': {len(sf.operations)} ops, "
                             f"{len(sf.material_streams)} streams")
            # Operation type breakdown
            type_counts = {}
            for op in self.flowsheet.operations:
                t = op.type_name
                type_counts[t] = type_counts.get(t, 0) + 1
            lines.append(f"    Operation types:")
            for t, c in sorted(type_counts.items(), key=lambda x: -x[1]):
                lines.append(f"      {t}: {c}")
        return "\n".join(lines)

    def all_operations(self) -> List[UniSimOperation]:
        """Get all operations including sub-flowsheets."""
        ops = list(self.flowsheet.operations) if self.flowsheet else []
        if self.flowsheet:
            for sf in self.flowsheet.sub_flowsheets:
                ops.extend(sf.operations)
        return ops

    def all_streams(self) -> List[UniSimStreamData]:
        """Get all material streams including sub-flowsheets."""
        streams = list(self.flowsheet.material_streams) if self.flowsheet else []
        if self.flowsheet:
            for sf in self.flowsheet.sub_flowsheets:
                streams.extend(sf.material_streams)
        return streams

    def to_dict(self) -> Dict:
        """Convert to JSON-serializable dict."""
        import dataclasses
        return dataclasses.asdict(self)


# ---------------------------------------------------------------------------
# UniSim COM Reader
# ---------------------------------------------------------------------------

class UniSimReader:
    """Reads UniSim Design .usc files via COM automation.

    Usage:
        reader = UniSimReader()
        model = reader.read("path/to/file.usc")
        reader.close()  # or use as context manager
    """

    # UniSim operation type → NeqSim type mapping
    OPERATION_TYPE_MAP = {
        'valveop': 'ThrottlingValve',
        'sep3op': 'ThreePhaseSeparator',
        'flashtank': 'Separator',
        'mixerop': 'Mixer',
        'teeop': 'Splitter',
        'compressor': 'Compressor',
        'coolerop': 'Cooler',
        'heaterop': 'Heater',
        'pumpop': 'Pump',
        'expandop': 'Expander',
        'heatexop': 'HeatExchanger',
        'recycle': 'Recycle',
        'adjust': 'Adjuster',
        'setop': 'Set',
        'pipeseg': 'AdiabaticPipe',
        'fractop': 'DistillationColumn',
        'saturateop': 'StreamSaturatorUtil',
        'spreadsheetop': 'Spreadsheet',
        'templateop': 'SubFlowsheet',
        'absorberop': 'Absorber',
        'reactorop': 'Reactor',
        'pfreactorop': 'Reactor',
        'cstrop': 'Reactor',
        'convreactorop': 'Reactor',
        'eqreactorop': 'Reactor',
        'gibbsreactorop': 'Reactor',
        'pidfbcontrolop': 'PIDController',
        'surgecontroller': 'SurgeController',
    }

    # UniSim component name → NeqSim component name mapping
    COMPONENT_NAME_MAP = {
        'Nitrogen': 'nitrogen',
        'N2': 'nitrogen',
        'CO2': 'CO2',
        'CarbonDioxide': 'CO2',
        'Methane': 'methane',
        'C1': 'methane',
        'Ethane': 'ethane',
        'C2': 'ethane',
        'Propane': 'propane',
        'C3': 'propane',
        'i-Butane': 'i-butane',
        'iC4': 'i-butane',
        'n-Butane': 'n-butane',
        'nC4': 'n-butane',
        'i-Pentane': 'i-pentane',
        'iC5': 'i-pentane',
        'n-Pentane': 'n-pentane',
        'nC5': 'n-pentane',
        'n-Hexane': 'n-hexane',
        'nC6': 'n-hexane',
        'n-Heptane': 'n-heptane',
        'nC7': 'n-heptane',
        'n-Octane': 'n-octane',
        'nC8': 'n-octane',
        'n-Nonane': 'n-nonane',
        'nC9': 'n-nonane',
        'n-Decane': 'nC10',
        'nC10': 'nC10',
        'nC11': 'nC11',
        'nC12': 'nC12',
        'H2O': 'water',
        'Water': 'water',
        'EGlycol': 'MEG',
        'Hydrogen': 'hydrogen',
        'H2': 'hydrogen',
        'H2S': 'H2S',
        'Oxygen': 'oxygen',
        'O2': 'oxygen',
        'Argon': 'argon',
        'Ar': 'argon',
        'Helium': 'helium',
        'He': 'helium',
        'TEGlycol': 'TEG',
        'DEGlycol': 'DEG',
        'MeOH': 'methanol',
        'Methanol': 'methanol',
        'Ethanol': 'ethanol',
        'COS': 'COS',
        'SO2': 'SO2',
        'NH3': 'ammonia',
        'Ammonia': 'ammonia',
        'Benzene': 'benzene',
        'Toluene': 'toluene',
        'Cyclohexane': 'cyclohexane',
        'CO': 'CO',
        'CarbonMonoxide': 'CO',
        'nC13': 'nC13',
        'nC14': 'nC14',
        'nC15': 'nC15',
        'nC16': 'nC16',
        'nC17': 'nC17',
        'nC18': 'nC18',
        'nC19': 'nC19',
        'nC20': 'nC20',
    }

    # UniSim property package → NeqSim EOS model mapping
    PROPERTY_PACKAGE_MAP = {
        'Peng-Robinson': 'PR',
        'Peng Robinson': 'PR',
        'PengRobinson': 'PR',
        'Peng-Robinson - LK': 'PR',
        'Peng Robinson - LK': 'PR',
        'SRK': 'SRK',
        'Soave-Redlich-Kwong': 'SRK',
        'CPA': 'CPA',
        'CPA-SRK': 'CPA',
        'ASME Steam': 'SRK',  # Will only have water
        'GERG 2008': 'GERG2008',
        'MBWR': 'SRK',  # fallback
        'Lee-Kesler-Plocker': 'SRK',  # fallback
        'NRTL': 'SRK',  # approx
    }

    def __init__(self, visible: bool = False):
        """Initialize but don't start UniSim yet.

        Args:
            visible: If True, show the UniSim GUI window.
        """
        self._app = None
        self._visible = visible

    def _ensure_app(self):
        """Start UniSim if not already running."""
        if self._app is None:
            import win32com.client
            logger.info("Starting UniSim Design via COM...")
            self._app = win32com.client.dynamic.Dispatch('UnisimDesign.Application')
            self._app.Visible = self._visible
            time.sleep(2)
            logger.info("UniSim started.")

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

    def read(self, usc_path: str, extract_streams: bool = True) -> UniSimModel:
        """Read a .usc file and extract the complete model.

        Args:
            usc_path: Path to the .usc file.
            extract_streams: If True, extract full stream property data.
                             Set False for faster extraction (topology only).

        Returns:
            UniSimModel with all extracted data.
        """
        usc_path = os.path.abspath(usc_path)
        if not os.path.exists(usc_path):
            raise FileNotFoundError(f"UniSim file not found: {usc_path}")

        self._ensure_app()
        logger.info(f"Opening: {usc_path}")

        case = self._app.SimulationCases.Open(usc_path)
        time.sleep(3)

        # Pause solver
        solver = case.Solver
        solver.CanSolve = False

        model = UniSimModel(
            file_path=usc_path,
            file_name=os.path.basename(usc_path),
        )

        # Extract fluid packages
        model.fluid_packages = self._extract_fluid_packages(case.BasisManager)
        comp_names = (model.fluid_packages[0].component_names
                      if model.fluid_packages else [])

        # Extract flowsheet
        model.flowsheet = self._extract_flowsheet(
            case.Flowsheet, comp_names, extract_streams)

        # Close the case (not the app — may read more files)
        case.Close()
        logger.info(f"Extracted: {len(model.all_operations())} operations, "
                     f"{len(model.all_streams())} streams")
        return model

    def read_multiple(self, usc_paths: List[str],
                      extract_streams: bool = True) -> List[UniSimModel]:
        """Read multiple .usc files efficiently (one UniSim session)."""
        models = []
        for path in usc_paths:
            try:
                model = self.read(path, extract_streams)
                models.append(model)
            except Exception as e:
                logger.error(f"Failed to read {path}: {e}")
        return models

    def _extract_fluid_packages(self, basis) -> List[UniSimFluidPackage]:
        """Extract all fluid packages from BasisManager."""
        packages = []
        try:
            for i in range(basis.FluidPackages.Count):
                fp = basis.FluidPackages.Item(i)
                pkg = UniSimFluidPackage(
                    name=self._safe_get(fp, 'name', f'FP_{i}'),
                    property_package=self._safe_get(fp, 'PropertyPackageName', 'Unknown'),
                )
                for j in range(fp.Components.Count):
                    comp = fp.Components.Item(j)
                    comp_name = self._safe_get(comp, 'name', f'Comp_{j}')
                    pkg.components.append(UniSimComponent(
                        name=comp_name,
                        index=j,
                        is_hypothetical=comp_name.endswith('*'),
                    ))
                packages.append(pkg)
        except Exception as e:
            logger.error(f"Error extracting fluid packages: {e}")
        return packages

    def _extract_flowsheet(self, fs, comp_names: List[str],
                           extract_streams: bool) -> UniSimFlowsheet:
        """Recursively extract a flowsheet."""
        flowsheet = UniSimFlowsheet(
            name=self._safe_get(fs, 'name', 'Main'),
        )

        # Material streams
        if extract_streams:
            try:
                for i in range(fs.MaterialStreams.Count):
                    ms = fs.MaterialStreams.Item(i)
                    sd = self._extract_stream(ms, comp_names)
                    flowsheet.material_streams.append(sd)
            except Exception as e:
                logger.warning(f"Error reading streams in '{flowsheet.name}': {e}")

        # Energy streams
        try:
            for i in range(fs.EnergyStreams.Count):
                es = fs.EnergyStreams.Item(i)
                esd = UniSimEnergyStream(
                    name=self._safe_get(es, 'name', f'E_{i}'),
                    heat_flow_kW=self._safe_getval(es.HeatFlow, 'kW'),
                )
                flowsheet.energy_streams.append(esd)
        except Exception as e:
            logger.warning(f"Error reading energy streams: {e}")

        # Operations
        try:
            for i in range(fs.Operations.Count):
                try:
                    op = fs.Operations.Item(i)
                    op_data = self._extract_operation(op)
                    flowsheet.operations.append(op_data)
                except Exception as e:
                    logger.warning(f"Error reading operation [{i}]: {e}")
        except Exception as e:
            logger.warning(f"Error iterating operations: {e}")

        # Sub-flowsheets
        try:
            if hasattr(fs, 'Flowsheets') and fs.Flowsheets.Count > 0:
                for i in range(fs.Flowsheets.Count):
                    sub = fs.Flowsheets.Item(i)
                    sub_fs = self._extract_flowsheet(sub, comp_names, extract_streams)
                    flowsheet.sub_flowsheets.append(sub_fs)
        except Exception:
            pass

        return flowsheet

    def _extract_stream(self, ms, comp_names: List[str]) -> UniSimStreamData:
        """Extract all available data from a material stream."""
        sd = UniSimStreamData(
            name=self._safe_get(ms, 'name', '<unnamed>'),
        )

        sd.temperature_C = self._safe_getval(ms.Temperature, 'C')
        sd.pressure_bara = self._safe_getval(ms.Pressure, 'bar')
        sd.mass_flow_kgh = self._safe_getval(ms.MassFlow, 'kg/h')
        sd.molar_flow_kgmolh = self._safe_getval(ms.MolarFlow, 'kgmole/h')
        sd.mass_density_kgm3 = self._safe_getval(ms.MassDensity, 'kg/m3')

        try:
            sd.molecular_weight = ms.MolecularWeight.GetValue()
        except Exception:
            pass

        try:
            sd.vapour_fraction = ms.VapourFraction.GetValue()
        except Exception:
            pass

        try:
            sd.enthalpy_kJkg = self._safe_getval(ms.MassEnthalpy, 'kJ/kg')
        except Exception:
            pass

        # Composition
        try:
            fracs = ms.ComponentMolarFraction.GetValues()
            if fracs and comp_names:
                sd.composition = {}
                for k, name in enumerate(comp_names):
                    if k < len(fracs) and fracs[k] is not None:
                        val = float(fracs[k])
                        if val > 1e-10:
                            sd.composition[name] = val
        except Exception:
            pass

        return sd

    def _extract_operation(self, op) -> UniSimOperation:
        """Extract type, connections, and properties from a unit operation.

        UniSim COM uses different connectivity patterns by operation type:
          - Single-stream ops (compressor, valve, cooler, heater, pump,
            expander, recycle): FeedStream / ProductStream
          - Mixer: Feeds[] / Product (singular)
          - Tee/Splitter: FeedStream / Products[]
          - Separator/FlashTank: Feeds[] / VapourProduct, LiquidProduct
          - ThreePhaseSep: Feeds[] / VapourProduct, LiquidProduct, WaterProduct
          - HeatExchanger: ShellSide/TubeSide sub-objects
        """
        op_data = UniSimOperation(
            name=self._safe_get(op, 'name', '<unnamed>'),
            type_name=self._safe_get(op, 'TypeName', '<unknown>'),
        )

        type_lower = op_data.type_name.lower()

        # ---- Special handling for HeatExchanger ----
        if 'heatex' in type_lower:
            self._extract_heatexchanger(op, op_data)
            return op_data

        # ---- FEEDS ----
        # 1) Try Feeds[] array (mixers, separators, multi-feed ops)
        feeds_found = False
        try:
            n = op.Feeds.Count
            if n > 0:
                for i in range(n):
                    name = self._safe_get(op.Feeds.Item(i), 'name', f'feed_{i}')
                    op_data.feeds.append(name)
                feeds_found = True
        except Exception:
            pass

        # 2) Try FeedStream (single-stream ops: compressor, valve, cooler,
        #    heater, pump, expander, tee, recycle)
        if not feeds_found:
            try:
                fs = op.FeedStream
                if fs is not None:
                    name = self._safe_get(fs, 'name', None)
                    if name:
                        op_data.feeds.append(name)
            except Exception:
                pass

        # ---- PRODUCTS ----
        # 1) Try Products[] array (tee/splitter, multi-product ops)
        prods_found = False
        try:
            n = op.Products.Count
            if n > 0:
                for i in range(n):
                    name = self._safe_get(op.Products.Item(i), 'name', f'prod_{i}')
                    op_data.products.append(name)
                prods_found = True
        except Exception:
            pass

        # 2) Try VapourProduct / LiquidProduct / WaterProduct (separators)
        if not prods_found and 'flash' in type_lower or 'sep' in type_lower:
            for attr in ('VapourProduct', 'LiquidProduct', 'WaterProduct'):
                try:
                    stream = getattr(op, attr)
                    if stream is not None:
                        name = self._safe_get(stream, 'name', None)
                        if name:
                            op_data.products.append(name)
                            prods_found = True
                except Exception:
                    pass

        # 3) Try Product (singular, for mixers)
        if not prods_found:
            try:
                prod = op.Product
                if prod is not None:
                    name = self._safe_get(prod, 'name', None)
                    if name:
                        op_data.products.append(name)
                        prods_found = True
            except Exception:
                pass

        # 4) Try ProductStream (single-stream ops)
        if not prods_found:
            try:
                ps = op.ProductStream
                if ps is not None:
                    name = self._safe_get(ps, 'name', None)
                    if name:
                        op_data.products.append(name)
            except Exception:
                pass

        # ---- ENERGY STREAMS ----
        try:
            if hasattr(op, 'EnergyFeeds'):
                for i in range(op.EnergyFeeds.Count):
                    name = self._safe_get(op.EnergyFeeds.Item(i), 'name', f'efeed_{i}')
                    op_data.energy_feeds.append(name)
        except Exception:
            pass

        try:
            if hasattr(op, 'EnergyProducts'):
                for i in range(op.EnergyProducts.Count):
                    name = self._safe_get(op.EnergyProducts.Item(i), 'name', f'eprod_{i}')
                    op_data.energy_products.append(name)
        except Exception:
            pass

        # Type-specific properties
        type_name = op_data.type_name.lower()
        props = op_data.properties

        if 'compressor' in type_name:
            props['duty_kW'] = self._safe_getval(op.DutyValue, 'kW') if hasattr(op, 'DutyValue') else None
            props['adiabatic_efficiency'] = self._safe_getval(op.AdiabaticEfficiency) if hasattr(op, 'AdiabaticEfficiency') else None
            props['polytropic_efficiency'] = self._safe_getval(op.PolytropicEfficiency) if hasattr(op, 'PolytropicEfficiency') else None
            # Outlet pressure from product stream (use ProductStream for single-port ops)
            try:
                prod_stream = op.ProductStream
                props['outlet_pressure_bara'] = self._safe_getval(prod_stream.Pressure, 'bar')
            except Exception:
                pass

        elif 'valve' in type_name:
            props['pressure_drop_kPa'] = self._safe_getval(op.PressureDrop, 'kPa') if hasattr(op, 'PressureDrop') else None
            # Outlet pressure from product stream
            try:
                prod_stream = op.ProductStream
                props['outlet_pressure_bara'] = self._safe_getval(prod_stream.Pressure, 'bar')
            except Exception:
                pass

        elif 'cooler' in type_name or 'heater' in type_name:
            props['duty_kW'] = self._safe_getval(op.DutyValue, 'kW') if hasattr(op, 'DutyValue') else None
            # Outlet temperature from product stream
            try:
                prod_stream = op.ProductStream
                props['outlet_temperature_C'] = self._safe_getval(prod_stream.Temperature, 'C')
                props['outlet_pressure_bara'] = self._safe_getval(prod_stream.Pressure, 'bar')
            except Exception:
                pass

        elif 'pump' in type_name:
            props['duty_kW'] = self._safe_getval(op.DutyValue, 'kW') if hasattr(op, 'DutyValue') else None
            props['adiabatic_efficiency'] = self._safe_getval(op.AdiabaticEfficiency) if hasattr(op, 'AdiabaticEfficiency') else None
            try:
                prod_stream = op.ProductStream
                props['outlet_pressure_bara'] = self._safe_getval(prod_stream.Pressure, 'bar')
            except Exception:
                pass

        elif 'expand' in type_name:
            props['duty_kW'] = self._safe_getval(op.DutyValue, 'kW') if hasattr(op, 'DutyValue') else None
            props['adiabatic_efficiency'] = self._safe_getval(op.AdiabaticEfficiency) if hasattr(op, 'AdiabaticEfficiency') else None
            try:
                prod_stream = op.ProductStream
                props['outlet_pressure_bara'] = self._safe_getval(prod_stream.Pressure, 'bar')
            except Exception:
                pass

        elif 'pipeseg' in type_name:
            # Try to get pipe length, diameter
            try:
                props['length_m'] = self._safe_getval(op.Length, 'm') if hasattr(op, 'Length') else None
                props['diameter_m'] = self._safe_getval(op.Diameter, 'm') if hasattr(op, 'Diameter') else None
            except Exception:
                pass

        elif 'heatex' in type_name:
            props['duty_kW'] = self._safe_getval(op.DutyValue, 'kW') if hasattr(op, 'DutyValue') else None

        return op_data

    def _extract_heatexchanger(self, op, op_data: UniSimOperation):
        """Extract HeatExchanger-specific connections and properties.

        UniSim heat exchangers use ShellSide/TubeSide sub-objects with
        Feed/Product streams, not the standard Feeds[]/Products[] pattern.
        """
        # Try multiple COM property patterns for HX feed/product extraction
        feed_attrs = [
            ('ShellSideFeed', 'ShellSideProduct'),
            ('TubeSideFeed', 'TubeSideProduct'),
        ]

        # Pattern 1: ShellSideFeed/TubeSideFeed (some UniSim versions)
        for feed_attr, prod_attr in feed_attrs:
            try:
                feed_stream = getattr(op, feed_attr)
                if feed_stream is not None:
                    name = self._safe_get(feed_stream, 'name', None)
                    if name:
                        op_data.feeds.append(name)
            except Exception:
                pass
            try:
                prod_stream = getattr(op, prod_attr)
                if prod_stream is not None:
                    name = self._safe_get(prod_stream, 'name', None)
                    if name:
                        op_data.products.append(name)
            except Exception:
                pass

        # Pattern 2: Feeds[]/Products[] generic arrays
        if not op_data.feeds:
            try:
                n = op.Feeds.Count
                for i in range(n):
                    name = self._safe_get(op.Feeds.Item(i), 'name', f'feed_{i}')
                    op_data.feeds.append(name)
            except Exception:
                pass

        if not op_data.products:
            try:
                n = op.Products.Count
                for i in range(n):
                    name = self._safe_get(op.Products.Item(i), 'name', f'prod_{i}')
                    op_data.products.append(name)
            except Exception:
                pass

        # Properties
        props = op_data.properties
        try:
            props['duty_kW'] = self._safe_getval(op.DutyValue, 'kW')
        except Exception:
            pass

    @staticmethod
    def _safe_get(obj, attr, default="<unavailable>"):
        """Safely get an attribute from a COM object."""
        try:
            val = getattr(obj, attr)
            if callable(val):
                val = val()
            return val
        except Exception:
            return default

    @staticmethod
    def _safe_getval(prop, unit=None, default=None):
        """Safely get value from a UniSim property object."""
        try:
            if unit:
                val = prop.GetValue(unit)
            else:
                val = prop.GetValue()
            if val is not None and val > -30000:  # UniSim uses -32767 for empty
                return float(val)
            return default
        except Exception:
            return default


# ---------------------------------------------------------------------------
# UniSim → NeqSim Converter
# ---------------------------------------------------------------------------

class UniSimToNeqSim:
    """Converts a UniSimModel to NeqSim ProcessSystem JSON format.

    Handles:
    - Component name mapping (UniSim → NeqSim)
    - EOS model mapping
    - Operation type mapping
    - Stream wiring (topology reconstruction)
    - Sub-flowsheet → ProcessModule mapping
    - Hypothetical component handling (pseudo-components)
    """

    def __init__(self, model: UniSimModel):
        self.model = model
        self._warnings = []
        self._assumptions = []

    @property
    def warnings(self) -> List[str]:
        return self._warnings

    @property
    def assumptions(self) -> List[str]:
        return self._assumptions

    def to_json(self, include_subflowsheets: bool = True) -> Dict:
        """Convert the full model to NeqSim JSON builder format.

        For complex models with sub-flowsheets, produces a structure
        that maps to ProcessModule containing multiple ProcessSystems.

        Returns:
            Dict suitable for json.dumps() and ProcessSystem.fromJson()
        """
        result = {
            '_source': f'UniSim: {self.model.file_name}',
            '_unisim_property_package': (self.model.fluid_packages[0].property_package
                                        if self.model.fluid_packages else 'Unknown'),
        }

        # Map fluid/EOS
        result['fluid'] = self._build_fluid_section()

        # Build process topology for main flowsheet
        result['process'] = self._build_process_section(self.model.flowsheet)

        # Sub-flowsheets as separate process systems
        if include_subflowsheets and self.model.flowsheet:
            sub_systems = {}
            for sf in self.model.flowsheet.sub_flowsheets:
                sf_json = self._build_process_section(sf)
                if sf_json:
                    sub_systems[sf.name] = sf_json
            if sub_systems:
                result['sub_flowsheets'] = sub_systems

        result['autoRun'] = True
        return result

    def to_neqsim_json_str(self) -> str:
        """Get the JSON as a string ready for ProcessSystem.fromJson()."""
        return json.dumps(self.to_json(), indent=2)

    def build_and_run(self):
        """Build and run the NeqSim process using jneqsim.

        Returns:
            The ProcessSystem result object.
        """
        from neqsim import jneqsim
        ProcessSystem = jneqsim.process.processmodel.ProcessSystem
        json_str = self.to_neqsim_json_str()
        return ProcessSystem.fromJsonAndRun(json_str)

    def _build_fluid_section(self) -> Dict:
        """Build the fluid section from the first fluid package."""
        if not self.model.fluid_packages:
            self._warnings.append("No fluid packages found — using SRK defaults")
            return {'model': 'SRK', 'temperature': 298.15, 'pressure': 1.0,
                    'mixingRule': 'classic', 'components': {'methane': 1.0}}

        fp = self.model.fluid_packages[0]
        eos = UniSimReader.PROPERTY_PACKAGE_MAP.get(fp.property_package, 'SRK')
        if eos == 'SRK' and fp.property_package not in ('SRK', 'Soave-Redlich-Kwong'):
            self._assumptions.append(
                f"Mapped '{fp.property_package}' → SRK (closest NeqSim equivalent)")

        # Find a feed stream to get initial composition
        composition = {}
        ref_temp_K = 298.15
        ref_P_bara = 1.0

        # Look for the first stream with a valid composition
        feed_stream = self._find_feed_stream()
        if feed_stream and feed_stream.composition:
            for unisim_name, frac in feed_stream.composition.items():
                neqsim_name = self._map_component_name(unisim_name)
                if neqsim_name:
                    composition[neqsim_name] = frac
            if feed_stream.temperature_C is not None:
                ref_temp_K = feed_stream.temperature_C + 273.15
            if feed_stream.pressure_bara is not None:
                ref_P_bara = feed_stream.pressure_bara

        # If no stream data, use equal molar for known components
        if not composition:
            self._warnings.append("No stream composition found — using equal molar")
            for comp in fp.components:
                neqsim_name = self._map_component_name(comp.name)
                if neqsim_name:
                    composition[neqsim_name] = 1.0 / len(fp.components)

        # Normalize
        total = sum(composition.values())
        if total > 0 and abs(total - 1.0) > 0.001:
            composition = {k: v / total for k, v in composition.items()}

        mixing_rule = '10' if eos == 'CPA' else 'classic'
        has_water = 'water' in composition

        return {
            'model': eos,
            'temperature': ref_temp_K,
            'pressure': ref_P_bara,
            'mixingRule': mixing_rule,
            'multiPhaseCheck': has_water,
            'components': composition,
        }

    def _build_process_section(self, flowsheet: UniSimFlowsheet) -> List[Dict]:
        """Build the process equipment array from a flowsheet.

        Performs topological sort to ensure equipment is defined
        in dependency order (feeds before consumers).
        Flattens sub-flowsheets into the main process.
        """
        if not flowsheet:
            return []

        # Collect ALL operations and streams (including sub-flowsheets)
        all_operations = list(flowsheet.operations)
        all_streams = list(flowsheet.material_streams)
        for sf in flowsheet.sub_flowsheets:
            all_operations.extend(sf.operations)
            all_streams.extend(sf.material_streams)

        # Build stream→operation connectivity map across all flowsheets
        stream_producer = {}  # stream_name → (operation_name, port)
        stream_consumer = {}  # stream_name → [(operation_name, port)]

        for op in all_operations:
            for s in op.products:
                stream_producer[s] = (op.name, 'outlet')
            for s in op.feeds:
                if s not in stream_consumer:
                    stream_consumer[s] = []
                stream_consumer[s].append((op.name, 'inlet'))

        # Find feed streams (produced by no operation → external feeds)
        external_feeds = set()
        for op in all_operations:
            for s in op.feeds:
                if s not in stream_producer:
                    external_feeds.add(s)

        # Build the process array
        process = []

        # Helper: find stream data by name across all flowsheets
        stream_by_name = {s.name: s for s in all_streams}

        # Add external feed streams first
        for feed_name in sorted(external_feeds):
            stream_data = stream_by_name.get(feed_name)
            entry = {'type': 'Stream', 'name': feed_name}
            if stream_data:
                props = {}
                if stream_data.mass_flow_kgh is not None and stream_data.mass_flow_kgh > 0:
                    props['flowRate'] = [stream_data.mass_flow_kgh, 'kg/hr']
                if stream_data.temperature_C is not None:
                    props['temperature'] = stream_data.temperature_C + 273.15
                if stream_data.pressure_bara is not None:
                    props['pressure'] = stream_data.pressure_bara
                if props:
                    entry['properties'] = props
            process.append(entry)

        # Topological sort of ALL operations (main + sub-flowsheets)
        sorted_ops = self._topological_sort(all_operations, stream_producer)

        # Convert each operation (sub-flowsheet template ops are skipped in _convert_operation)
        for op in sorted_ops:
            entry = self._convert_operation(op, stream_producer, flowsheet)
            if entry:
                process.append(entry)

        return process

    def _convert_operation(self, op: UniSimOperation,
                           stream_producer: Dict,
                           flowsheet: UniSimFlowsheet) -> Optional[Dict]:
        """Convert a single UniSim operation to NeqSim JSON format."""
        neqsim_type = UniSimReader.OPERATION_TYPE_MAP.get(
            op.type_name, None)

        # Skip unsupported types
        if neqsim_type is None:
            self._warnings.append(
                f"Unsupported operation type '{op.type_name}': '{op.name}' — skipped")
            return None

        # Skip utility/control operations that don't map to physical equipment
        if neqsim_type in ('Adjuster', 'Set', 'Spreadsheet', 'SubFlowsheet',
                           'PIDController', 'SurgeController', 'Reactor',
                           'Absorber'):
            return None

        entry = {
            'type': neqsim_type,
            'name': op.name,
        }

        # Wire inlet(s)
        if op.feeds:
            # For multi-inlet equipment (Mixer, HeatExchanger), use inlets array
            if neqsim_type in ('Mixer', 'HeatExchanger') and len(op.feeds) >= 2:
                entry['inlets'] = []
                for feed_name in op.feeds:
                    inlet_ref = self._resolve_inlet_ref(feed_name, stream_producer)
                    entry['inlets'].append(inlet_ref)
            else:
                # Single inlet
                inlet_ref = self._resolve_inlet_ref(op.feeds[0], stream_producer)
                entry['inlet'] = inlet_ref

        # Set properties based on operation type
        props = {}

        # Helper: get outlet stream data from already-extracted UniSim streams
        def _get_outlet_stream_data():
            """Get the first product stream data from extracted UniSim data."""
            if op.products:
                return self._find_stream_by_name(flowsheet, op.products[0])
            return None

        if neqsim_type == 'Compressor':
            if op.properties.get('outlet_pressure_bara'):
                props['outletPressure'] = op.properties['outlet_pressure_bara']
            else:
                # Fallback: get outlet pressure from extracted stream data
                out_s = _get_outlet_stream_data()
                if out_s and out_s.pressure_bara:
                    props['outletPressure'] = out_s.pressure_bara
            eff = op.properties.get('adiabatic_efficiency')
            if eff and 0 < eff <= 1:
                props['isentropicEfficiency'] = eff
            poly_eff = op.properties.get('polytropic_efficiency')
            if poly_eff and 0 < poly_eff <= 1:
                props['polytropicEfficiency'] = poly_eff
                props['usePolytropicCalc'] = True

        elif neqsim_type == 'ThrottlingValve':
            if op.properties.get('outlet_pressure_bara'):
                props['outletPressure'] = op.properties['outlet_pressure_bara']
            else:
                out_s = _get_outlet_stream_data()
                if out_s and out_s.pressure_bara:
                    props['outletPressure'] = out_s.pressure_bara

        elif neqsim_type in ('Cooler', 'Heater'):
            if op.properties.get('outlet_temperature_C') is not None:
                props['outTemperature'] = op.properties['outlet_temperature_C'] + 273.15
            else:
                out_s = _get_outlet_stream_data()
                if out_s and out_s.temperature_C is not None:
                    props['outTemperature'] = out_s.temperature_C + 273.15

        elif neqsim_type == 'Pump':
            if op.properties.get('outlet_pressure_bara'):
                props['outletPressure'] = op.properties['outlet_pressure_bara']
            else:
                out_s = _get_outlet_stream_data()
                if out_s and out_s.pressure_bara:
                    props['outletPressure'] = out_s.pressure_bara
            eff = op.properties.get('adiabatic_efficiency')
            if eff and 0 < eff <= 1:
                props['isentropicEfficiency'] = eff

        elif neqsim_type == 'Expander':
            if op.properties.get('outlet_pressure_bara'):
                props['outletPressure'] = op.properties['outlet_pressure_bara']
            else:
                out_s = _get_outlet_stream_data()
                if out_s and out_s.pressure_bara:
                    props['outletPressure'] = out_s.pressure_bara
            eff = op.properties.get('adiabatic_efficiency')
            if eff and 0 < eff <= 1:
                props['isentropicEfficiency'] = eff

        elif neqsim_type == 'AdiabaticPipe':
            if op.properties.get('length_m'):
                props['length'] = op.properties['length_m']
            if op.properties.get('diameter_m'):
                props['diameter'] = op.properties['diameter_m']

        elif neqsim_type == 'Splitter':
            if op.products:
                props['splitNumber'] = len(op.products)

        if props:
            entry['properties'] = props

        return entry

    def _resolve_inlet_ref(self, stream_name: str,
                           stream_producer: Dict) -> str:
        """Resolve a stream name to a dot-notation inlet reference."""
        if stream_name in stream_producer:
            producer_name, port = stream_producer[stream_name]
            # Find the producer operation to determine port type
            for op in self.model.all_operations():
                if op.name == producer_name:
                    neqsim_type = UniSimReader.OPERATION_TYPE_MAP.get(op.type_name)
                    if neqsim_type in ('Separator', 'ThreePhaseSeparator'):
                        # Determine which port this stream comes from
                        if len(op.products) > 0:
                            idx = op.products.index(stream_name) if stream_name in op.products else 0
                            if neqsim_type == 'ThreePhaseSeparator':
                                ports = ['gasOut', 'oilOut', 'waterOut']
                            else:
                                ports = ['gasOut', 'liquidOut']
                            port_name = ports[idx] if idx < len(ports) else 'outlet'
                            return f"{producer_name}.{port_name}"
                    elif neqsim_type == 'Splitter':
                        # Splitter uses indexed split ports: split0, split1, ...
                        if len(op.products) > 0 and stream_name in op.products:
                            idx = op.products.index(stream_name)
                            return f"{producer_name}.split{idx}"
                        return f"{producer_name}.split0"
                    return f"{producer_name}.outlet"
            return stream_name  # External feed
        else:
            return stream_name  # External feed — directly reference the Stream name

    def _topological_sort(self, operations: List[UniSimOperation],
                          stream_producer: Dict) -> List[UniSimOperation]:
        """Sort operations in dependency order (feeds before consumers)."""
        # Build adjacency: op A → op B if A produces a stream that B consumes
        op_by_name = {op.name: op for op in operations}
        graph = {op.name: set() for op in operations}  # deps for each op

        for op in operations:
            for feed_stream in op.feeds:
                if feed_stream in stream_producer:
                    producer_name, _ = stream_producer[feed_stream]
                    if producer_name in op_by_name and producer_name != op.name:
                        graph[op.name].add(producer_name)

        # Kahn's algorithm
        in_degree = {name: len(deps) for name, deps in graph.items()}
        queue = [name for name, deg in in_degree.items() if deg == 0]
        sorted_names = []

        while queue:
            node = queue.pop(0)
            sorted_names.append(node)
            for name, deps in graph.items():
                if node in deps:
                    in_degree[name] -= 1
                    if in_degree[name] == 0:
                        queue.append(name)

        # Add any remaining (cycles — recycles)
        for op in operations:
            if op.name not in sorted_names:
                sorted_names.append(op.name)

        return [op_by_name[n] for n in sorted_names if n in op_by_name]

    def _find_feed_stream(self) -> Optional[UniSimStreamData]:
        """Find the main feed stream (first stream with composition data)."""
        if not self.model.flowsheet:
            return None
        for s in self.model.flowsheet.material_streams:
            if s.composition and len(s.composition) > 1:
                return s
        return None

    def _find_stream_by_name(self, flowsheet: UniSimFlowsheet,
                             name: str) -> Optional[UniSimStreamData]:
        """Find a stream by name in a flowsheet."""
        for s in flowsheet.material_streams:
            if s.name == name:
                return s
        return None

    def _map_component_name(self, unisim_name: str) -> Optional[str]:
        """Map a UniSim component name to NeqSim name."""
        # Direct mapping
        if unisim_name in UniSimReader.COMPONENT_NAME_MAP:
            return UniSimReader.COMPONENT_NAME_MAP[unisim_name]

        # Hypothetical components (ending with *)
        if unisim_name.endswith('*'):
            self._warnings.append(
                f"Hypothetical component '{unisim_name}' — not mapped to NeqSim "
                f"(would need C7+ characterization)")
            return None

        # Try case-insensitive
        for k, v in UniSimReader.COMPONENT_NAME_MAP.items():
            if k.lower() == unisim_name.lower():
                return v

        self._warnings.append(f"Unknown component '{unisim_name}' — skipped")
        return None

    def get_comparison_points(self) -> List[Dict]:
        """Extract key comparison points for verification against NeqSim.

        Returns list of dicts with stream name, property, UniSim value, and units.
        Useful for validating that the NeqSim model produces similar results.
        """
        points = []
        for s in self.model.all_streams():
            if s.temperature_C is not None and s.pressure_bara is not None:
                point = {
                    'stream': s.name,
                    'temperature_C': s.temperature_C,
                    'pressure_bara': s.pressure_bara,
                }
                if s.mass_flow_kgh is not None:
                    point['mass_flow_kgh'] = s.mass_flow_kgh
                if s.mass_density_kgm3 is not None:
                    point['density_kgm3'] = s.mass_density_kgm3
                if s.vapour_fraction is not None:
                    point['vapour_fraction'] = s.vapour_fraction
                if s.molecular_weight is not None:
                    point['molecular_weight'] = s.molecular_weight
                points.append(point)
        return points

    # -----------------------------------------------------------------
    # Shared topology analysis for code generation
    # -----------------------------------------------------------------

    def _prepare_topology(self, include_subflowsheets: bool = True):
        """Analyse the model topology and return shared data structures.

        Returns a dict with:
          fluid        – fluid spec dict from _build_fluid_section()
          eos_class    – 'SystemPrEos' or 'SystemSrkEos'
          flowsheet    – the main UniSimFlowsheet (or None)
          all_ops      – flat list of all operations
          all_streams  – flat list of all material streams
          stream_producer – {stream_name: (op_name, port)}
          external_feeds  – set of feed stream names
          stream_by_name  – {name: UniSimStreamData}
          sorted_ops      – topologically sorted operations
          var_names    – mutable dict for tracking python var names
          used_vars    – mutable set for tracking used var names
        """
        fluid = self._build_fluid_section()
        eos_class = 'SystemPrEos' if fluid['model'] == 'PR' else 'SystemSrkEos'

        flowsheet = self.model.flowsheet
        if not flowsheet:
            return dict(fluid=fluid, eos_class=eos_class, flowsheet=None,
                        all_ops=[], all_streams=[], stream_producer={},
                        external_feeds=set(), stream_by_name={},
                        sorted_ops=[], var_names={}, used_vars=set())

        all_operations = list(flowsheet.operations)
        all_streams = list(flowsheet.material_streams)
        if include_subflowsheets:
            for sf in flowsheet.sub_flowsheets:
                all_operations.extend(sf.operations)
                all_streams.extend(sf.material_streams)

        stream_producer: dict = {}
        for op in all_operations:
            for s in op.products:
                stream_producer[s] = (op.name, 'outlet')

        external_feeds: set = set()
        for op in all_operations:
            for s in op.feeds:
                if s not in stream_producer:
                    external_feeds.add(s)

        stream_by_name = {s.name: s for s in all_streams}
        sorted_ops = self._topological_sort(all_operations, stream_producer)

        return dict(
            fluid=fluid, eos_class=eos_class, flowsheet=flowsheet,
            all_ops=all_operations, all_streams=all_streams,
            stream_producer=stream_producer, external_feeds=external_feeds,
            stream_by_name=stream_by_name, sorted_ops=sorted_ops,
            var_names={}, used_vars=set(),
        )

    # ---- variable-name helpers (static, used by all code-gen paths) ----

    @staticmethod
    def _to_pyvar(name: str) -> str:
        """Convert an arbitrary name to a valid Python identifier."""
        v = name.replace(' ', '_').replace('-', '_').replace('/', '_')
        v = v.replace('(', '').replace(')', '').replace('.', '_')
        v = v.replace('+', '_plus_').replace('&', '_and_')
        if v and v[0].isdigit():
            v = '_' + v
        v = ''.join(c if (c.isalnum() or c == '_') else '_' for c in v)
        return v

    @staticmethod
    def _unique_var(name: str, var_names: dict, used_vars: set) -> str:
        """Return a unique Python variable name, updating *var_names* and *used_vars*."""
        base = UniSimToNeqSim._to_pyvar(name)
        v = base
        i = 2
        while v in used_vars:
            v = f'{base}_{i}'
            i += 1
        used_vars.add(v)
        var_names[name] = v
        return v

    @staticmethod
    def _outlet_ref(ref: str, var_names: dict) -> str:
        """Resolve a dot-notation ref like ``"Sep.gasOut"`` to a Python expression."""
        if '.' not in ref:
            return var_names.get(ref, UniSimToNeqSim._to_pyvar(ref))
        parent, port = ref.rsplit('.', 1)
        pvar = var_names.get(parent, UniSimToNeqSim._to_pyvar(parent))
        port_methods = {
            'gasOut': 'getGasOutStream()',
            'oilOut': 'getOilOutStream()',
            'waterOut': 'getWaterOutStream()',
            'liquidOut': 'getLiquidOutStream()',
            'outlet': 'getOutletStream()',
        }
        if port.startswith('split'):
            idx = port.replace('split', '') or '0'
            return f'{pvar}.getSplitStream(int({idx}))'
        method = port_methods.get(port, 'getOutletStream()')
        return f'{pvar}.{method}'

    # ---- shared code-line generators ----

    SKIPPED_NEQSIM_TYPES = frozenset((
        'Adjuster', 'Set', 'Spreadsheet', 'SubFlowsheet',
        'PIDController', 'SurgeController', 'Reactor', 'Absorber',
    ))

    NEQSIM_IMPORTS = (
        'SystemSrkEos = jneqsim.thermo.system.SystemSrkEos',
        'SystemPrEos  = jneqsim.thermo.system.SystemPrEos',
        'ProcessSystem = jneqsim.process.processmodel.ProcessSystem',
        'Stream = jneqsim.process.equipment.stream.Stream',
        'Separator = jneqsim.process.equipment.separator.Separator',
        'ThreePhaseSeparator = jneqsim.process.equipment.separator.ThreePhaseSeparator',
        'Mixer = jneqsim.process.equipment.mixer.Mixer',
        'Splitter = jneqsim.process.equipment.splitter.Splitter',
        'Compressor = jneqsim.process.equipment.compressor.Compressor',
        'ThrottlingValve = jneqsim.process.equipment.valve.ThrottlingValve',
        'Cooler = jneqsim.process.equipment.heatexchanger.Cooler',
        'Heater = jneqsim.process.equipment.heatexchanger.Heater',
        'HeatExchanger = jneqsim.process.equipment.heatexchanger.HeatExchanger',
        'Pump = jneqsim.process.equipment.pump.Pump',
        'Recycle = jneqsim.process.equipment.util.Recycle',
        'AdiabaticPipe = jneqsim.process.equipment.pipeline.AdiabaticPipe',
    )

    def _gen_fluid_lines(self, fluid: dict, eos_class: str) -> List[str]:
        """Return code lines that create the thermodynamic fluid."""
        lines = []
        lines.append(f'fluid = {eos_class}({fluid["temperature"]}, {fluid["pressure"]})')
        for comp, frac in fluid.get('components', {}).items():
            lines.append(f'fluid.addComponent("{comp}", {frac})')
        lines.append(f'fluid.setMixingRule("{fluid.get("mixingRule", "classic")}")')
        if fluid.get('multiPhaseCheck'):
            lines.append('fluid.setMultiPhaseCheck(True)')
        return lines

    def _gen_feed_lines(self, topo: dict) -> List[str]:
        """Return code lines that create external feed streams."""
        lines = []
        var_names = topo['var_names']
        used_vars = topo['used_vars']
        stream_by_name = topo['stream_by_name']
        for feed_name in sorted(topo['external_feeds']):
            sd = stream_by_name.get(feed_name)
            v = self._unique_var(feed_name, var_names, used_vars)
            lines.append(f'{v} = Stream("{feed_name}", fluid.clone())')
            if sd:
                if sd.mass_flow_kgh is not None and sd.mass_flow_kgh > 0:
                    lines.append(f'{v}.setFlowRate({sd.mass_flow_kgh}, "kg/hr")')
                if sd.temperature_C is not None:
                    lines.append(f'{v}.setTemperature({sd.temperature_C}, "C")')
                if sd.pressure_bara is not None:
                    lines.append(f'{v}.setPressure({sd.pressure_bara}, "bara")')
            lines.append(f'process.add({v})')
            lines.append('')
        return lines

    def _gen_equipment_lines(self, op: 'UniSimOperation', topo: dict) -> Optional[List[str]]:
        """Return code lines for one equipment unit, or None if skipped."""
        neqsim_type = UniSimReader.OPERATION_TYPE_MAP.get(op.type_name)
        if neqsim_type is None:
            return [f'# SKIPPED: unsupported type "{op.type_name}": "{op.name}"']
        if neqsim_type in self.SKIPPED_NEQSIM_TYPES:
            return [f'# SKIPPED ({neqsim_type}): "{op.name}"']

        var_names = topo['var_names']
        used_vars = topo['used_vars']
        stream_producer = topo['stream_producer']
        flowsheet = topo['flowsheet']

        lines: List[str] = []
        v = self._unique_var(op.name, var_names, used_vars)

        inlet_refs = []
        if op.feeds:
            for f in op.feeds:
                inlet_refs.append(self._resolve_inlet_ref(f, stream_producer))

        _ref = lambda ref: self._outlet_ref(ref, var_names)

        if neqsim_type == 'Mixer':
            lines.append(f'{v} = Mixer("{op.name}")')
            for ref in inlet_refs:
                lines.append(f'{v}.addStream({_ref(ref)})')
        elif neqsim_type == 'HeatExchanger' and len(inlet_refs) >= 2:
            lines.append(f'{v} = HeatExchanger("{op.name}")')
            lines.append(f'{v}.setFeedStream(0, {_ref(inlet_refs[0])})')
            lines.append(f'{v}.setFeedStream(1, {_ref(inlet_refs[1])})')
        elif neqsim_type == 'Splitter':
            ref_expr = _ref(inlet_refs[0]) if inlet_refs else 'None'
            n_splits = len(op.products) if op.products else 2
            lines.append(f'{v} = Splitter("{op.name}", {ref_expr}, {n_splits})')
        elif neqsim_type == 'DistillationColumn':
            n_trays = op.properties.get('numberOfTrays', 5)
            lines.append(f'# DistillationColumn: {op.name}')
            lines.append('from neqsim.process.equipment.distillation import DistillationColumn as DC')
            lines.append(f'{v} = DC("{op.name}", {n_trays}, True, True)')
            if inlet_refs:
                lines.append(f'{v}.addFeedStream({_ref(inlet_refs[0])})')
        elif neqsim_type == 'Recycle':
            lines.append(f'{v} = Recycle("{op.name}")')
            if inlet_refs:
                lines.append(f'{v}.setInletStream({_ref(inlet_refs[0])})')
        else:
            ref_expr = _ref(inlet_refs[0]) if inlet_refs else 'None'
            lines.append(f'{v} = {neqsim_type}("{op.name}", {ref_expr})')

        self._gen_properties(lines, v, neqsim_type, op, flowsheet)
        lines.append(f'process.add({v})')
        return lines

    # -----------------------------------------------------------------
    # Python code generation
    # -----------------------------------------------------------------

    def to_python(self, include_subflowsheets: bool = True) -> str:
        """Generate a self-contained Python script that builds the NeqSim process.

        The generated code uses the ``jneqsim`` gateway and creates every
        stream, equipment unit, and connection as explicit Python statements
        so the process is fully transparent, readable, and editable.

        Args:
            include_subflowsheets: whether to include sub-flowsheet operations.

        Returns:
            A string containing a complete, runnable Python script.
        """
        topo = self._prepare_topology(include_subflowsheets)
        fluid = topo['fluid']
        eos_class = topo['eos_class']
        flowsheet = topo['flowsheet']

        lines: list = []
        _a = lines.append

        # --- header / imports ---
        _a('"""')
        _a(f'NeqSim process model — generated from UniSim file')
        _a(f'Source: {self.model.file_name}')
        if self.model.fluid_packages:
            _a(f'Property package: {self.model.fluid_packages[0].property_package}')
        _a(f'Generated by: UniSimToNeqSim.to_python()')
        _a('"""')
        _a('')
        _a('from neqsim import jneqsim')
        _a('')
        _a('# --- NeqSim class imports ---')
        for imp in self.NEQSIM_IMPORTS:
            _a(imp)
        _a('')

        # --- fluid creation ---
        _a('# ============================================================')
        _a('# Fluid definition')
        _a('# ============================================================')
        lines.extend(self._gen_fluid_lines(fluid, eos_class))
        _a('')

        if not flowsheet:
            _a('# WARNING: no flowsheet found')
            return '\n'.join(lines)

        # --- process definition ---
        _a('# ============================================================')
        _a('# Process definition')
        _a('# ============================================================')
        _a("process = ProcessSystem('Main')")
        _a('')

        # --- feed streams ---
        _a('# --- Feed streams ---')
        lines.extend(self._gen_feed_lines(topo))

        # --- equipment in topological order ---
        _a('# --- Equipment ---')
        for op in topo['sorted_ops']:
            eq_lines = self._gen_equipment_lines(op, topo)
            if eq_lines:
                lines.extend(eq_lines)
                _a('')

        # --- run ---
        _a('# ============================================================')
        _a('# Run simulation')
        _a('# ============================================================')
        _a('process.run()')
        _a('')
        _a('# Print key stream results')
        _a('for i in range(int(process.getUnitOperations().size())):')
        _a('    u = process.getUnitOperations().get(i)')
        _a('    try:')
        _a('        T = float(u.getTemperature()) - 273.15')
        _a('        P = float(u.getPressure())')
        _a('        print(f"  {str(u.getName()):40s}  T={T:8.1f} °C   P={P:8.2f} bara")')
        _a('    except Exception:')
        _a('        pass')

        return '\n'.join(lines)

    # -----------------------------------------------------------------
    # Jupyter notebook generation
    # -----------------------------------------------------------------

    # Equipment type → plain-language description for notebook markdown
    EQUIPMENT_DESCRIPTIONS = {
        'ThreePhaseSeparator': 'Three-phase separator — separates the inlet '
            'stream into gas, oil and water by gravity.',
        'Separator': 'Two-phase separator — flashes the inlet into gas and '
            'liquid phases.',
        'Compressor': 'Gas compressor — raises gas pressure using mechanical '
            'work.  Outlet conditions depend on efficiency and pressure ratio.',
        'Cooler': 'Cooler — removes heat (e.g. air or sea-water cooling) to '
            'reach a target outlet temperature.',
        'Heater': 'Heater — adds heat to raise the stream temperature, '
            'typically with hot oil, steam or electric heating.',
        'ThrottlingValve': 'Throttling valve — reduces pressure by '
            'isenthalpic (Joule-Thomson) expansion.',
        'Mixer': 'Stream mixer — combines two or more inlet streams into a '
            'single outlet at the lowest inlet pressure.',
        'Splitter': 'Stream splitter/tee — divides one stream into multiple '
            'outlets, each with the same composition and conditions.',
        'Pump': 'Liquid pump — increases liquid pressure.',
        'Expander': 'Turbo-expander — extracts work by expanding gas across '
            'a pressure drop, used for dew-point control or power recovery.',
        'HeatExchanger': 'Heat exchanger — transfers heat between two '
            'process streams (shell & tube or plate type).',
        'Recycle': 'Recycle block — converges a tear stream so that '
            'downstream conditions match the assumed upstream values.',
        'AdiabaticPipe': 'Pipeline segment — models pressure drop and '
            'heat transfer along the pipe length.',
        'DistillationColumn': 'Distillation column — separates components '
            'by boiling-point differences across multiple stages.',
    }

    def _nb_cell(self, cell_type: str, source: str) -> Dict:
        """Create a single nbformat v4 notebook cell (code or markdown)."""
        return {
            'cell_type': cell_type,
            'metadata': {},
            'source': source if isinstance(source, list) else source.split('\n'),
            'outputs': [] if cell_type == 'code' else [],
            **({"execution_count": None} if cell_type == 'code' else {}),
        }

    def to_notebook(self, include_subflowsheets: bool = True) -> Dict:
        """Generate a Jupyter notebook (nbformat v4 dict) for the NeqSim process.

        The notebook mirrors ``to_python()`` exactly — the same shared
        code-generation helpers produce the equipment lines — but wraps
        them in separate cells with explanatory markdown so the process
        is self-documenting.

        Save with::

            import json
            with open('process.ipynb', 'w') as f:
                json.dump(converter.to_notebook(), f, indent=1)

        Or with nbformat::

            import nbformat
            nb = nbformat.from_dict(converter.to_notebook())
            nbformat.write(nb, 'process.ipynb')

        Args:
            include_subflowsheets: include sub-flowsheet operations.

        Returns:
            A dict in Jupyter nbformat v4 structure.
        """
        topo = self._prepare_topology(include_subflowsheets)
        fluid = topo['fluid']
        eos_class = topo['eos_class']
        flowsheet = topo['flowsheet']

        cells: List[Dict] = []

        # --- helper to format a code cell from a list of lines ---
        def _code(lines_or_str):
            if isinstance(lines_or_str, list):
                src = '\n'.join(lines_or_str)
            else:
                src = lines_or_str
            cells.append(self._nb_cell('code', src))

        def _md(text):
            cells.append(self._nb_cell('markdown', text))

        # ---- Cell 1: Title & Overview ----
        n_ops = len(topo['all_ops'])
        n_streams = len(topo['all_streams'])
        n_feeds = len(topo['external_feeds'])
        n_comps = len(fluid.get('components', {}))
        pp_name = (self.model.fluid_packages[0].property_package
                   if self.model.fluid_packages else 'Unknown')

        overview = (
            f'# UniSim Import: {self.model.file_name}\n\n'
            f'| Property | Value |\n'
            f'|----------|-------|\n'
            f'| Property package | {pp_name} |\n'
            f'| NeqSim EOS | {fluid["model"]} |\n'
            f'| Components | {n_comps} |\n'
            f'| Operations | {n_ops} |\n'
            f'| Material streams | {n_streams} |\n'
            f'| Feed streams | {n_feeds} |\n'
        )
        if flowsheet and flowsheet.sub_flowsheets:
            overview += (f'| Sub-flowsheets | '
                         f'{len(flowsheet.sub_flowsheets)} |\n')
        _md(overview)

        # ---- Cell 2: Setup imports ----
        _md('## Setup\n\n'
            'Import NeqSim via the `jneqsim` gateway and define the '
            'class aliases used throughout the notebook.')
        import_lines = ['from neqsim import jneqsim', '']
        import_lines.append('# --- NeqSim class imports ---')
        for imp in self.NEQSIM_IMPORTS:
            import_lines.append(imp)
        _code(import_lines)

        # ---- Cell 3: Fluid definition (markdown) ----
        comp_table = '| Component | Mole fraction |\n|-----------|---------------|\n'
        for comp, frac in fluid.get('components', {}).items():
            comp_table += f'| {comp} | {frac:.6f} |\n'

        _md(f'## Fluid Definition\n\n'
            f'Equation of state: **{fluid["model"]}** '
            f'(mapped from UniSim *{pp_name}*)\n\n'
            f'Mixing rule: `{fluid.get("mixingRule", "classic")}`\n\n'
            f'{comp_table}')

        # ---- Cell 4: Fluid creation (code) ----
        _code(self._gen_fluid_lines(fluid, eos_class))

        if not flowsheet:
            _md('> **Warning:** No flowsheet found in the UniSim model.')
            return self._wrap_notebook(cells)

        # ---- Cell 5: Process system init ----
        _md('## Process Definition\n\n'
            'Build the `ProcessSystem` and add equipment in topological '
            'order (upstream before downstream).')
        _code(["process = ProcessSystem('Main')"])

        # ---- Cell 6: Feed streams (markdown) ----
        feed_table = ('| Feed stream | T (°C) | P (bara) | '
                      'Flow (kg/hr) |\n|------------|--------|----------|'
                      '-------------|\n')
        for fn in sorted(topo['external_feeds']):
            sd = topo['stream_by_name'].get(fn)
            t = f'{sd.temperature_C:.1f}' if sd and sd.temperature_C is not None else '—'
            p = f'{sd.pressure_bara:.1f}' if sd and sd.pressure_bara is not None else '—'
            fl = f'{sd.mass_flow_kgh:.0f}' if sd and sd.mass_flow_kgh else '—'
            feed_table += f'| {fn} | {t} | {p} | {fl} |\n'
        _md(f'### Feed Streams\n\n{feed_table}')

        # ---- Cell 7: Feed stream code ----
        _code(self._gen_feed_lines(topo))

        # ---- Cells 8..N: Equipment ----
        _md('### Equipment')
        for op in topo['sorted_ops']:
            neqsim_type = UniSimReader.OPERATION_TYPE_MAP.get(op.type_name)
            if neqsim_type is None or neqsim_type in self.SKIPPED_NEQSIM_TYPES:
                continue  # skip silently in notebook
            desc = self.EQUIPMENT_DESCRIPTIONS.get(neqsim_type, neqsim_type)
            _md(f'**{op.name}** ({neqsim_type})\n\n{desc}')
            eq_lines = self._gen_equipment_lines(op, topo)
            if eq_lines:
                _code(eq_lines)

        # ---- Run cell ----
        _md('## Run Simulation')
        _code(['process.run()'])

        # ---- Results cell ----
        _md('## Results\n\n'
            'Print temperature, pressure and flow for every unit operation.')
        results_code = [
            'print(f"{\'Unit\':40s}  {\'T (°C)\':>10s}  {\'P (bara)\':>10s}")',
            'print("-" * 65)',
            'for i in range(int(process.getUnitOperations().size())):',
            '    u = process.getUnitOperations().get(i)',
            '    try:',
            '        T = float(u.getTemperature()) - 273.15',
            '        P = float(u.getPressure())',
            '        print(f"  {str(u.getName()):40s}  {T:8.1f}    {P:8.2f}")',
            '    except Exception:',
            '        pass',
        ]
        _code(results_code)

        # ---- Warnings cell (if any) ----
        if self._warnings:
            warn_md = '## Warnings & Assumptions\n\n'
            for w in self._warnings:
                warn_md += f'- {w}\n'
            for a in self._assumptions:
                warn_md += f'- *Assumption:* {a}\n'
            _md(warn_md)

        return self._wrap_notebook(cells)

    def _wrap_notebook(self, cells: List[Dict]) -> Dict:
        """Wrap a list of cells into a complete nbformat v4 notebook dict."""
        return {
            'nbformat': 4,
            'nbformat_minor': 5,
            'metadata': {
                'kernelspec': {
                    'display_name': 'Python 3',
                    'language': 'python',
                    'name': 'python3',
                },
                'language_info': {
                    'name': 'python',
                    'version': '3.12.0',
                },
            },
            'cells': cells,
        }

    def save_notebook(self, path: str,
                      include_subflowsheets: bool = True) -> None:
        """Generate and save a Jupyter notebook to *path*.

        Args:
            path: file path ending in ``.ipynb``.
            include_subflowsheets: include sub-flowsheet operations.
        """
        nb = self.to_notebook(include_subflowsheets)
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(nb, f, indent=1, ensure_ascii=False)

    # -----------------------------------------------------------------
    # EOT / ProcessPilot simulator generation
    # -----------------------------------------------------------------

    # Maps NeqSim type → eot component factory function name
    EOT_COMPONENT_MAP = {
        'ThreePhaseSeparator': 'get_three_phase_separator',
        'Separator': 'get_separator',
        'Compressor': 'get_compressor',
        'Cooler': 'get_cooler',
        'Heater': 'get_heater',
        'ThrottlingValve': 'get_valve',
        'Mixer': 'get_mixer',
        'Splitter': 'get_splitter',
        'Pump': 'get_pump',
        'Recycle': 'get_recycle',
        'HeatExchanger': 'get_heat_exchanger',
    }

    # Maps NeqSim type → EquipmentType enum member name in eot
    EOT_EQUIPMENT_TYPE_MAP = {
        'ThreePhaseSeparator': 'ThreePhaseSeparator',
        'Separator': 'Separator',
        'Compressor': 'Compressor',
        'Cooler': 'Cooler',
        'Heater': 'Heater',
        'ThrottlingValve': 'ThrottlingValve',
        'Mixer': 'Mixer',
        'Splitter': 'Splitter',
        'Pump': 'Pump',
        'Recycle': 'Recycle',
        'HeatExchanger': 'MultiStreamHeatExchanger',
        'Expander': 'Expander',
        'AdiabaticPipe': 'PipeBeggsAndBrills',
    }

    def to_eot_simulator(self, class_name: str = 'UniSimSimulator',
                         include_subflowsheets: bool = True) -> str:
        """Generate a Python module containing an ``eot.BaseSimulator`` subclass.

        The generated code follows the ProcessPilot-NeqSimInterface pattern:

        * Uses ``eot.components`` factory functions (``get_stream``,
          ``get_compressor``, ``get_valve``, …)
        * Subclasses ``BaseSimulator`` with a ``build_process()`` method
        * Wires equipment with ``getOutStream()`` / ``getGasOutStream()``
          / ``getOilOutStream()`` etc.
        * Registers all units via ``self.process.add(...)``

        Equipment types not covered by an ``eot.components`` helper fall
        back to raw ``jneqsim`` calls (same code as ``to_python()``).

        Args:
            class_name: name for the generated simulator class.
            include_subflowsheets: include sub-flowsheet operations.

        Returns:
            A string containing a complete, runnable Python module.
        """
        topo = self._prepare_topology(include_subflowsheets)
        fluid = topo['fluid']
        eos_class = topo['eos_class']
        flowsheet = topo['flowsheet']

        lines: list = []
        _a = lines.append

        # ---- header ----
        _a('"""')
        _a(f'EOT Simulator — generated from UniSim file')
        _a(f'Source: {self.model.file_name}')
        if self.model.fluid_packages:
            _a(f'Property package: {self.model.fluid_packages[0].property_package}')
        _a(f'Generated by: UniSimToNeqSim.to_eot_simulator()')
        _a('"""')
        _a('')

        # ---- imports ----
        _a('from jneqsim import neqsim')
        _a('from eot.simulators.base_simulator import BaseSimulator')
        # Collect which eot component functions we need
        needed_factories = set()
        needed_factories.add('get_fluid')
        needed_factories.add('get_stream')
        if topo['sorted_ops']:
            for op in topo['sorted_ops']:
                nt = UniSimReader.OPERATION_TYPE_MAP.get(op.type_name)
                factory = self.EOT_COMPONENT_MAP.get(nt)
                if factory:
                    needed_factories.add(factory)
        for factory in sorted(needed_factories):
            _a(f'from eot.components import {factory}')
        _a('')
        _a('')

        # ---- fluid helper ----
        _a('def _get_fluid():')
        _a(f'    """Create the thermodynamic fluid (mapped from UniSim)."""')
        eos_neqsim = f'neqsim.thermo.system.{"SystemPrEos" if fluid["model"] == "PR" else "SystemSrkEos"}'
        _a(f'    fluid = {eos_neqsim}()')
        for comp, frac in fluid.get('components', {}).items():
            _a(f'    fluid.addComponent("{comp}", {frac})')
        _a(f'    fluid.setMixingRule("{fluid.get("mixingRule", "classic")}")')
        if fluid.get('multiPhaseCheck'):
            _a('    fluid.setMultiPhaseCheck(True)')
        _a('    return fluid')
        _a('')
        _a('')

        # ---- simulator class ----
        _a(f'class {class_name}(BaseSimulator):')
        _a(f'    """Simulator generated from UniSim model: {self.model.file_name}"""')
        _a('')
        _a('    @property')
        _a('    def name(self) -> str:')
        sim_name = self.model.file_name.replace('.usc', '')
        _a(f'        return "{sim_name}"')
        _a('')
        _a('    def __init__(self):')
        _a('        super().__init__()')
        _a('')
        _a('    def build_process(self):')
        _a('        fluid = _get_fluid()')
        _a('')

        if not flowsheet:
            _a('        pass  # WARNING: no flowsheet found')
            return '\n'.join(lines)

        indent = '        '  # 8 spaces for method body

        # ---- feed streams ----
        var_names = topo['var_names']
        used_vars = topo['used_vars']
        stream_by_name = topo['stream_by_name']

        for feed_name in sorted(topo['external_feeds']):
            sd = stream_by_name.get(feed_name)
            v = self._unique_var(feed_name, var_names, used_vars)
            t_val = f'{sd.temperature_C}' if sd and sd.temperature_C is not None else '15.0'
            p_val = f'{sd.pressure_bara}' if sd and sd.pressure_bara is not None else '1.0'
            fl_val = f'{sd.mass_flow_kgh}' if sd and sd.mass_flow_kgh and sd.mass_flow_kgh > 0 else '1000.0'
            _a(f'{indent}{v} = get_stream("{feed_name}", fluid, '
               f'flow_rate={fl_val}, pressure={p_val}, temperature={t_val})')
            _a('')

        # ---- equipment ----
        for op in topo['sorted_ops']:
            neqsim_type = UniSimReader.OPERATION_TYPE_MAP.get(op.type_name)
            if neqsim_type is None or neqsim_type in self.SKIPPED_NEQSIM_TYPES:
                _a(f'{indent}# SKIPPED: {op.name} ({op.type_name})')
                continue

            v = self._unique_var(op.name, var_names, used_vars)
            factory = self.EOT_COMPONENT_MAP.get(neqsim_type)

            # Resolve inlet reference expression
            inlet_refs = []
            if op.feeds:
                for f_name in op.feeds:
                    inlet_refs.append(
                        self._resolve_inlet_ref(f_name, topo['stream_producer']))

            _ref = lambda r: self._outlet_ref(r, var_names)

            if factory and neqsim_type == 'Compressor':
                ref_expr = _ref(inlet_refs[0]) if inlet_refs else 'None'
                p_out = op.properties.get('outlet_pressure_bara')
                if not p_out:
                    out_s = self._find_stream_by_name(flowsheet, op.products[0]) if op.products else None
                    if out_s and out_s.pressure_bara:
                        p_out = out_s.pressure_bara
                p_out = p_out or 100.0
                eff = op.properties.get('adiabatic_efficiency', 1.0)
                if not eff or eff <= 0 or eff > 1:
                    eff = 1.0
                _a(f'{indent}{v} = get_compressor("{op.name}", '
                   f'{ref_expr}, outlet_pressure={p_out}, '
                   f'insentropic_efficiency={eff})')

            elif factory and neqsim_type == 'ThrottlingValve':
                ref_expr = _ref(inlet_refs[0]) if inlet_refs else 'None'
                p_out = op.properties.get('outlet_pressure_bara')
                if not p_out:
                    out_s = self._find_stream_by_name(flowsheet, op.products[0]) if op.products else None
                    if out_s and out_s.pressure_bara:
                        p_out = out_s.pressure_bara
                p_out = p_out or 50.0
                _a(f'{indent}{v} = get_valve("{op.name}", '
                   f'{ref_expr}, initial_valve_opening=100, '
                   f'outlet_pressure={p_out})')

            elif factory and neqsim_type in ('Cooler', 'Heater'):
                ref_expr = _ref(inlet_refs[0]) if inlet_refs else 'None'
                t_out = op.properties.get('outlet_temperature_C')
                if t_out is None:
                    out_s = self._find_stream_by_name(flowsheet, op.products[0]) if op.products else None
                    if out_s and out_s.temperature_C is not None:
                        t_out = out_s.temperature_C
                t_out = t_out if t_out is not None else 20.0
                fn = 'get_cooler' if neqsim_type == 'Cooler' else 'get_heater'
                _a(f'{indent}{v} = {fn}("{op.name}", '
                   f'{ref_expr}, outlet_temperature={t_out})')

            elif factory and neqsim_type == 'Pump':
                ref_expr = _ref(inlet_refs[0]) if inlet_refs else 'None'
                p_out = op.properties.get('outlet_pressure_bara')
                if not p_out:
                    out_s = self._find_stream_by_name(flowsheet, op.products[0]) if op.products else None
                    if out_s and out_s.pressure_bara:
                        p_out = out_s.pressure_bara
                p_out = p_out or 50.0
                _a(f'{indent}{v} = get_pump("{op.name}", '
                   f'{ref_expr}, outlet_pressure={p_out})')

            elif factory and neqsim_type == 'Mixer':
                stream_args = ', '.join(_ref(r) for r in inlet_refs)
                _a(f'{indent}{v} = get_mixer("{op.name}", '
                   f'inlet_streams=[{stream_args}])')

            elif factory and neqsim_type in ('ThreePhaseSeparator', 'Separator'):
                ref_expr = _ref(inlet_refs[0]) if inlet_refs else 'None'
                fn = self.EOT_COMPONENT_MAP[neqsim_type]
                _a(f'{indent}{v} = {fn}("{op.name}", '
                   f'inlet_stream={ref_expr})')

            elif factory and neqsim_type == 'Splitter':
                ref_expr = _ref(inlet_refs[0]) if inlet_refs else 'None'
                _a(f'{indent}{v} = get_splitter("{op.name}", '
                   f'inlet_stream={ref_expr})')

            elif factory and neqsim_type == 'Recycle':
                ref_expr = _ref(inlet_refs[0]) if inlet_refs else 'None'
                _a(f'{indent}# Recycle requires outlet_stream to be wired manually')
                _a(f'{indent}{v} = get_recycle("{op.name}", '
                   f'inlet_stream={ref_expr}, '
                   f'outlet_stream={ref_expr}, tolerance=1)')

            else:
                # Fallback: raw jneqsim call (same as to_python)
                eq_lines = self._gen_equipment_lines(op, topo)
                if eq_lines:
                    for line in eq_lines:
                        _a(f'{indent}{line}')
                continue  # process.add already included in eq_lines

            _a('')

        # ---- process.add() section ----
        _a(f'{indent}# --- Add equipment to process ---')
        for fn in sorted(topo['external_feeds']):
            v = var_names.get(fn)
            if v:
                _a(f'{indent}self.process.add({v})')
        for op in topo['sorted_ops']:
            neqsim_type = UniSimReader.OPERATION_TYPE_MAP.get(op.type_name)
            if neqsim_type is None or neqsim_type in self.SKIPPED_NEQSIM_TYPES:
                continue
            v = var_names.get(op.name)
            if v:
                _a(f'{indent}self.process.add({v})')

        return '\n'.join(lines)

    def save_eot_simulator(self, path: str,
                           class_name: str = 'UniSimSimulator',
                           include_subflowsheets: bool = True) -> None:
        """Generate and save an EOT simulator module to *path*.

        Args:
            path: file path ending in ``.py``.
            class_name: name for the generated simulator class.
            include_subflowsheets: include sub-flowsheet operations.
        """
        code = self.to_eot_simulator(class_name, include_subflowsheets)
        with open(path, 'w', encoding='utf-8') as f:
            f.write(code)

    def to_eot_notebook(self, class_name: str = 'UniSimSimulator',
                        include_subflowsheets: bool = True) -> Dict:
        """Generate a Jupyter notebook demonstrating the EOT simulator.

        The notebook creates the simulator, runs it, and prints state —
        letting users see the ProcessPilot interface in action.

        Args:
            class_name: name for the generated simulator class.
            include_subflowsheets: include sub-flowsheet operations.

        Returns:
            A dict in Jupyter nbformat v4 structure.
        """
        cells: List[Dict] = []

        def _code(src):
            cells.append(self._nb_cell('code',
                         src if isinstance(src, str) else '\n'.join(src)))

        def _md(text):
            cells.append(self._nb_cell('markdown', text))

        fluid = self._build_fluid_section()
        pp_name = (self.model.fluid_packages[0].property_package
                   if self.model.fluid_packages else 'Unknown')

        _md(f'# EOT Simulator: {self.model.file_name}\n\n'
            f'Auto-generated ProcessPilot / EOT simulator from UniSim model.\n\n'
            f'| Property | Value |\n|----------|-------|\n'
            f'| UniSim Property Package | {pp_name} |\n'
            f'| NeqSim EOS | {fluid["model"]} |\n'
            f'| Components | {len(fluid.get("components", {}))} |\n')

        _md('## Simulator Class\n\n'
            'The cell below contains the complete simulator. It subclasses '
            '`BaseSimulator` from the EOT framework and uses `eot.components` '
            'factory functions for each equipment item.\n\n'
            'You can modify equipment parameters, add controllers, or change '
            'the fluid definition in the code below.')

        sim_code = self.to_eot_simulator(class_name, include_subflowsheets)
        _code(sim_code)

        _md('## Run the simulator\n\n'
            'Instantiate and run. The constructor calls `build_process()` '
            'and `process.run()` automatically.')
        _code([
            f'sim = {class_name}()',
            'print(f"Process units: {len(sim.equipment)}")',
            'print(f"Equipment: {sim.equipment}")',
        ])

        _md('## State\n\n'
            'The `get_state()` method returns temperatures, pressures, '
            'flows, and valve positions for all registered equipment.')
        _code([
            'state = sim.get_state()',
            'sim.print_state(decimals=2)',
        ])

        _md('## Step (RL Interface)\n\n'
            'Apply an action and observe the new state. This is the '
            'interface used by reinforcement learning agents.')
        _code([
            'from eot.utils.types import ActionType',
            '',
            '# Example: no-op step (empty action)',
            'new_state = sim.step({})',
            'sim.print_state()',
        ])

        return self._wrap_notebook(cells)

    def _gen_properties(self, lines: list, var: str, neqsim_type: str,
                        op, flowsheet) -> None:
        """Append property-setting lines for an equipment unit."""

        def _get_outlet_stream_data():
            if op.products:
                return self._find_stream_by_name(flowsheet, op.products[0])
            return None

        if neqsim_type == 'Compressor':
            p_out = op.properties.get('outlet_pressure_bara')
            if not p_out:
                out_s = _get_outlet_stream_data()
                if out_s and out_s.pressure_bara:
                    p_out = out_s.pressure_bara
            if p_out:
                lines.append(f'{var}.setOutletPressure({p_out})')
            eff = op.properties.get('adiabatic_efficiency')
            if eff and 0 < eff <= 1:
                lines.append(f'{var}.setIsentropicEfficiency({eff})')
            poly = op.properties.get('polytropic_efficiency')
            if poly and 0 < poly <= 1:
                lines.append(f'{var}.setPolytropicEfficiency({poly})')
                lines.append(f'{var}.setUsePolytropicCalc(True)')

        elif neqsim_type == 'ThrottlingValve':
            p_out = op.properties.get('outlet_pressure_bara')
            if not p_out:
                out_s = _get_outlet_stream_data()
                if out_s and out_s.pressure_bara:
                    p_out = out_s.pressure_bara
            if p_out:
                lines.append(f'{var}.setOutletPressure({p_out})')

        elif neqsim_type in ('Cooler', 'Heater'):
            t_out = op.properties.get('outlet_temperature_C')
            if t_out is None:
                out_s = _get_outlet_stream_data()
                if out_s and out_s.temperature_C is not None:
                    t_out = out_s.temperature_C
            if t_out is not None:
                lines.append(f'{var}.setOutTemperature({t_out + 273.15})')

        elif neqsim_type == 'Pump':
            p_out = op.properties.get('outlet_pressure_bara')
            if not p_out:
                out_s = _get_outlet_stream_data()
                if out_s and out_s.pressure_bara:
                    p_out = out_s.pressure_bara
            if p_out:
                lines.append(f'{var}.setOutletPressure({p_out})')
            eff = op.properties.get('adiabatic_efficiency')
            if eff and 0 < eff <= 1:
                lines.append(f'{var}.setIsentropicEfficiency({eff})')

        elif neqsim_type == 'Expander':
            p_out = op.properties.get('outlet_pressure_bara')
            if not p_out:
                out_s = _get_outlet_stream_data()
                if out_s and out_s.pressure_bara:
                    p_out = out_s.pressure_bara
            if p_out:
                lines.append(f'{var}.setOutletPressure({p_out})')

        elif neqsim_type == 'AdiabaticPipe':
            if op.properties.get('length_m'):
                lines.append(f'{var}.setLength({op.properties["length_m"]})')
            if op.properties.get('diameter_m'):
                lines.append(f'{var}.setDiameter({op.properties["diameter_m"]})')


# ---------------------------------------------------------------------------
# Comparator: Verify NeqSim vs UniSim results
# ---------------------------------------------------------------------------

class UniSimComparator:
    """Compares UniSim stream results with NeqSim simulation results.

    Usage:
        reader = UniSimReader()
        model = reader.read("file.usc")
        converter = UniSimToNeqSim(model)
        process = converter.build_and_run()

        comparator = UniSimComparator(model, process)
        report = comparator.compare()
        comparator.print_report(report)
    """

    def __init__(self, unisim_model: UniSimModel, neqsim_process=None):
        self.unisim_model = unisim_model
        self.neqsim_process = neqsim_process

    def compare_streams(self) -> List[Dict]:
        """Compare temperature, pressure, flow, density between UniSim and NeqSim.

        Returns list of comparison dicts with deviations.
        """
        if self.neqsim_process is None:
            return []

        results = []
        unisim_streams = {s.name: s for s in self.unisim_model.all_streams()}

        # Get NeqSim equipment and their outlet streams
        try:
            n_units = self.neqsim_process.size()
            for i in range(n_units):
                unit = self.neqsim_process.getUnit(i)
                unit_name = str(unit.getName())

                # Check if this unit name matches a UniSim stream
                # Try outlet stream
                try:
                    outlet = unit.getOutletStream()
                    if outlet:
                        neqsim_T = float(outlet.getTemperature()) - 273.15  # K → C
                        neqsim_P = float(outlet.getPressure())  # bara
                        neqsim_flow = float(outlet.getFlowRate('kg/hr'))

                        # Find matching UniSim stream
                        for prod_name in self._get_product_stream_names(unit_name):
                            if prod_name in unisim_streams:
                                us = unisim_streams[prod_name]
                                comp = {
                                    'stream': prod_name,
                                    'equipment': unit_name,
                                    'neqsim_T_C': neqsim_T,
                                    'unisim_T_C': us.temperature_C,
                                    'neqsim_P_bara': neqsim_P,
                                    'unisim_P_bara': us.pressure_bara,
                                    'neqsim_flow_kgh': neqsim_flow,
                                    'unisim_flow_kgh': us.mass_flow_kgh,
                                }
                                # Calculate deviations
                                if us.temperature_C is not None and neqsim_T != 0:
                                    comp['T_deviation_C'] = neqsim_T - us.temperature_C
                                if us.pressure_bara is not None and us.pressure_bara > 0:
                                    comp['P_deviation_pct'] = (
                                        (neqsim_P - us.pressure_bara) / us.pressure_bara * 100)
                                if us.mass_flow_kgh is not None and us.mass_flow_kgh > 0:
                                    comp['flow_deviation_pct'] = (
                                        (neqsim_flow - us.mass_flow_kgh) / us.mass_flow_kgh * 100)
                                results.append(comp)
                except Exception:
                    pass
        except Exception as e:
            logger.warning(f"Error during comparison: {e}")

        return results

    def _get_product_stream_names(self, unit_name: str) -> List[str]:
        """Get the names of product streams for a given unit from the UniSim model."""
        for op in self.unisim_model.all_operations():
            if op.name == unit_name:
                return op.products
        return []

    def print_report(self, comparisons: List[Dict]):
        """Print a formatted comparison report."""
        if not comparisons:
            print("No comparison data available.")
            return

        print(f"\n{'=' * 90}")
        print(f"UNISIM vs NEQSIM COMPARISON REPORT")
        print(f"Model: {self.unisim_model.file_name}")
        print(f"{'=' * 90}")
        print(f"{'Stream':<25s} {'T dev (°C)':<12s} {'P dev (%)':<12s} {'Flow dev (%)':<12s}")
        print(f"{'-' * 60}")

        for c in comparisons:
            t_dev = f"{c.get('T_deviation_C', ''):>8.2f}" if 'T_deviation_C' in c else '   N/A  '
            p_dev = f"{c.get('P_deviation_pct', ''):>8.2f}" if 'P_deviation_pct' in c else '   N/A  '
            f_dev = f"{c.get('flow_deviation_pct', ''):>8.2f}" if 'flow_deviation_pct' in c else '   N/A  '
            print(f"{c['stream']:<25s} {t_dev:<12s} {p_dev:<12s} {f_dev:<12s}")

        # Summary statistics
        t_devs = [abs(c['T_deviation_C']) for c in comparisons if 'T_deviation_C' in c]
        p_devs = [abs(c['P_deviation_pct']) for c in comparisons if 'P_deviation_pct' in c]
        f_devs = [abs(c['flow_deviation_pct']) for c in comparisons if 'flow_deviation_pct' in c]

        print(f"\n{'SUMMARY'}")
        print(f"  Temperature: avg |dev| = {sum(t_devs)/len(t_devs):.2f} °C  "
              f"(max {max(t_devs):.2f} °C)" if t_devs else "  Temperature: no data")
        print(f"  Pressure:    avg |dev| = {sum(p_devs)/len(p_devs):.2f} %  "
              f"(max {max(p_devs):.2f} %)" if p_devs else "  Pressure: no data")
        print(f"  Flow:        avg |dev| = {sum(f_devs)/len(f_devs):.2f} %  "
              f"(max {max(f_devs):.2f} %)" if f_devs else "  Flow: no data")


# ---------------------------------------------------------------------------
# CLI entry point
# ---------------------------------------------------------------------------

def main():
    """Command-line interface for reading UniSim files."""
    import argparse
    parser = argparse.ArgumentParser(description='Read UniSim .usc files and convert to NeqSim')
    parser.add_argument('usc_file', help='Path to .usc file')
    parser.add_argument('--json', '-j', action='store_true',
                        help='Output NeqSim JSON')
    parser.add_argument('--python', '-p', metavar='FILE',
                        help='Generate standalone NeqSim Python script')
    parser.add_argument('--notebook', '-n', metavar='FILE',
                        help='Generate Jupyter notebook (.ipynb)')
    parser.add_argument('--eot', metavar='FILE',
                        help='Generate EOT/ProcessPilot simulator (.py)')
    parser.add_argument('--eot-notebook', metavar='FILE',
                        help='Generate EOT demo notebook (.ipynb)')
    parser.add_argument('--eot-class', default='UniSimSimulator',
                        help='Class name for the EOT simulator '
                             '(default: UniSimSimulator)')
    parser.add_argument('--summary', '-s', action='store_true',
                        help='Print model summary')
    parser.add_argument('--save', help='Save extracted model to JSON file')
    parser.add_argument('--no-streams', action='store_true',
                        help='Skip stream property extraction (faster)')
    parser.add_argument('--visible', action='store_true',
                        help='Show UniSim GUI window')
    args = parser.parse_args()

    logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')

    with UniSimReader(visible=args.visible) as reader:
        model = reader.read(args.usc_file, extract_streams=not args.no_streams)

        if args.summary or not (args.json or args.python or args.notebook
                                or args.eot or args.eot_notebook):
            print(model.summary())

        converter = UniSimToNeqSim(model)

        def _print_diag():
            if converter.warnings:
                print("\n--- WARNINGS ---")
                for w in converter.warnings:
                    print(f"  ! {w}")
            if converter.assumptions:
                print("\n--- ASSUMPTIONS ---")
                for a in converter.assumptions:
                    print(f"  * {a}")

        if args.json:
            neqsim_json = converter.to_json()
            print(json.dumps(neqsim_json, indent=2))
            _print_diag()

        if args.python:
            code = converter.to_python()
            with open(args.python, 'w', encoding='utf-8') as f:
                f.write(code)
            print(f"\nSaved Python script to: {args.python}")
            _print_diag()

        if args.notebook:
            converter.save_notebook(args.notebook)
            print(f"\nSaved Jupyter notebook to: {args.notebook}")
            _print_diag()

        if args.eot:
            converter.save_eot_simulator(
                args.eot, class_name=args.eot_class)
            print(f"\nSaved EOT simulator to: {args.eot}")
            _print_diag()

        if args.eot_notebook:
            nb = converter.to_eot_notebook(class_name=args.eot_class)
            with open(args.eot_notebook, 'w', encoding='utf-8') as f:
                json.dump(nb, f, indent=1, ensure_ascii=False)
            print(f"\nSaved EOT demo notebook to: {args.eot_notebook}")
            _print_diag()

        if args.save:
            with open(args.save, 'w') as f:
                json.dump(model.to_dict(), f, indent=2, default=str)
            print(f"\nSaved to: {args.save}")


if __name__ == '__main__':
    main()
