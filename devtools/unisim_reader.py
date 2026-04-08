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
        'setop': 'SetPoint',
        'pipeseg': 'AdiabaticPipe',
        'fractop': 'DistillationColumn',
        'saturateop': 'StreamSaturatorUtil',
        'spreadsheetop': 'Spreadsheet',
        'templateop': 'SubFlowsheet',
        'absorberop': 'Absorber',
        # Reactor subtypes — map to specific NeqSim reactor classes
        'reactorop': 'GibbsReactor',
        'pfreactorop': 'PlugFlowReactor',
        'cstrop': 'StirredTankReactor',
        'convreactorop': 'GibbsReactor',
        'conversionreactorop': 'GibbsReactor',
        'eqreactorop': 'GibbsReactor',
        'gibbsreactorop': 'GibbsReactor',
        'equilibriumreactorop': 'GibbsReactor',
        'kineticreactorop': 'PlugFlowReactor',
        # Controllers / utilities
        'pidfbcontrolop': 'PIDController',
        'surgecontroller': 'SurgeController',
        # Column types
        'distillation': 'DistillationColumn',
        'absorber': 'Absorber',
        'columnop': 'DistillationColumn',
        'reboiledabsorber': 'DistillationColumn',
        # Column internals (sub-parts, not standalone)
        'partialcondenser': 'ColumnInternals',
        'totalcondenser': 'ColumnInternals',
        'condenser3op': 'ColumnInternals',
        'traysection': 'ColumnInternals',
        'bpreboiler': 'ColumnInternals',
        # Utility / logic ops
        'balanceop': 'BalanceOp',
        'logicalop': 'LogicalOp',
        'selectop': 'LogicalOp',
    }

    # Set of all reactor NeqSim types (for generic handling)
    REACTOR_TYPES = frozenset(('GibbsReactor', 'PlugFlowReactor',
                               'StirredTankReactor'))

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
        'DEAmine': 'DEA',
        'MEAmine': 'MEA',
        'MDEAmine': 'MDEA',
        'Benzene': 'benzene',
        'Toluene': 'toluene',
        'Cyclohexane': 'cyclohexane',
        'CO': 'CO',
        'CarbonMonoxide': 'CO',
        'Propene': 'propene',
        'Propylene': 'propene',
        'Ethylene': 'ethylene',
        'Ethene': 'ethylene',
        '1-Butene': '1-butene',
        'cis-2-Butene': 'c2-butene',
        'trans-2-Butene': 't2-butene',
        'Isobutene': 'isobutene',
        'AceticAcid': 'acetic acid',
        'Acetic Acid': 'acetic acid',
        '12C3Oxide': None,  # propylene oxide — not in NeqSim database
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
        'Glycol Package': 'CPA',
        'COMPropertyPkg': 'SRK',  # COM extension; fallback
        'DBR Amine Package': 'SRK',  # DBR amine; fallback
        'OLI': 'SRK',  # electrolyte; fallback
        'Sour PR': 'PR',
        'SourPR': 'PR',
        'Sour SRK': 'SRK',
        'Zudkevitch Joffee': 'SRK',  # fallback
        'Kabadi Danner': 'SRK',  # fallback
        'UNIQUAC': 'SRK',  # activity model; fallback
        'UNIQUAC - Ideal': 'SRK',  # activity model; fallback
        'Wilson': 'SRK',  # activity model; fallback
        'Antoine': 'SRK',  # fallback
        'Chao Seader': 'SRK',  # fallback
        'Grayson Streed': 'SRK',  # fallback
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
            fracs = ms.ComponentMolarFraction.Values
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
        if not prods_found and ('flash' in type_lower or 'sep' in type_lower):
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

        elif 'flash' in type_name or 'sep' in type_name:
            # ---- Separator / Three-Phase Separator properties ----
            # Detect actual phase count from connected products
            has_water_product = False
            try:
                wp = op.WaterProduct
                if wp is not None:
                    wn = self._safe_get(wp, 'name', None)
                    if wn:
                        has_water_product = True
            except Exception:
                pass
            props['has_water_product'] = has_water_product

            # If a flashtank has a WaterProduct, it is effectively 3-phase
            if has_water_product and type_name == 'flashtank':
                props['detected_three_phase'] = True
                op_data.type_name = 'sep3op'  # override to 3-phase
                logger.info(
                    "Separator '%s' is flashtank with WaterProduct — "
                    "re-classified as three-phase (sep3op)", op_data.name)

            # Entrainment / carryover fractions
            # UniSim COM exposes these via various attribute names;
            # try the most common ones.
            entrainment_specs = []
            # --- liquid in vapour (oil carryover in gas) ---
            for attr_name in ('LiqCarryOverMolFrac', 'LiqCarryOverFrac',
                              'LiquidInVapourFraction', 'LiqInVap',
                              'LiquidCarryover'):
                try:
                    val = self._safe_getval(getattr(op, attr_name))
                    if val is not None and val > 0:
                        entrainment_specs.append({
                            'value': val, 'specType': 'volume',
                            'specifiedStream': 'product',
                            'phaseFrom': 'oil', 'phaseTo': 'gas',
                            'source_attr': attr_name})
                        break
                except Exception:
                    pass
            # --- vapour in liquid (gas carry-under in oil) ---
            for attr_name in ('VapCarryUnderMolFrac', 'VapCarryUnderFrac',
                              'VapourInLiquidFraction', 'VapInLiq',
                              'VapourCarryunder'):
                try:
                    val = self._safe_getval(getattr(op, attr_name))
                    if val is not None and val > 0:
                        entrainment_specs.append({
                            'value': val, 'specType': 'volume',
                            'specifiedStream': 'product',
                            'phaseFrom': 'gas', 'phaseTo': 'liquid',
                            'source_attr': attr_name})
                        break
                except Exception:
                    pass
            # --- water in oil (for 3-phase) ---
            for attr_name in ('WaterInOilFraction', 'WaterInOil',
                              'AqInOil', 'AqueousInOilFraction'):
                try:
                    val = self._safe_getval(getattr(op, attr_name))
                    if val is not None and val > 0:
                        entrainment_specs.append({
                            'value': val, 'specType': 'volume',
                            'specifiedStream': 'product',
                            'phaseFrom': 'aqueous', 'phaseTo': 'oil',
                            'source_attr': attr_name})
                        break
                except Exception:
                    pass
            # --- oil in water (for 3-phase) ---
            for attr_name in ('OilInWaterFraction', 'OilInWater',
                              'OilInAq', 'OilInAqueousFraction'):
                try:
                    val = self._safe_getval(getattr(op, attr_name))
                    if val is not None and val > 0:
                        entrainment_specs.append({
                            'value': val, 'specType': 'volume',
                            'specifiedStream': 'product',
                            'phaseFrom': 'oil', 'phaseTo': 'aqueous',
                            'source_attr': attr_name})
                        break
                except Exception:
                    pass
            if entrainment_specs:
                props['entrainment'] = entrainment_specs
                logger.info("Separator '%s': extracted %d entrainment specs",
                            op_data.name, len(entrainment_specs))

            # Dimensions (if available)
            try:
                props['diameter_m'] = self._safe_getval(op.Diameter, 'm')
            except Exception:
                pass
            try:
                props['length_m'] = self._safe_getval(op.Length, 'm')
            except Exception:
                pass

            # Orientation detection (vertical → GasScrubber, horizontal → Separator)
            orientation = None
            for attr_name in ('Orientation', 'VesselOrientation',
                              'SeparatorOrientation'):
                try:
                    raw = getattr(op, attr_name)
                    if raw is not None:
                        val_str = str(raw).strip().lower()
                        if 'vert' in val_str:
                            orientation = 'vertical'
                        elif 'horiz' in val_str:
                            orientation = 'horizontal'
                        elif val_str in ('0', '1'):
                            # Some UniSim versions: 0=horizontal, 1=vertical
                            orientation = 'vertical' if val_str == '1' else 'horizontal'
                        if orientation:
                            break
                except Exception:
                    pass
            # Fallback: try numeric enum (UniSim R510+)
            if orientation is None:
                try:
                    raw = op.Orientation
                    ival = int(raw)
                    orientation = 'vertical' if ival == 1 else 'horizontal'
                except Exception:
                    pass
            if orientation:
                props['orientation'] = orientation
                logger.info("Separator '%s': orientation = %s",
                            op_data.name, orientation)
                # For 2-phase vertical separators, reclassify to GasScrubber
                if orientation == 'vertical' and op_data.type_name == 'flashtank':
                    props['detected_vertical'] = True
                    logger.info(
                        "Separator '%s' is vertical flashtank — "
                        "will map to GasScrubber", op_data.name)

        elif 'heatex' in type_name:
            props['duty_kW'] = self._safe_getval(op.DutyValue, 'kW') if hasattr(op, 'DutyValue') else None

        elif type_name in ('adjust', 'balanceop'):
            # Adjuster / Balance op: extract target and adjusted variable info
            try:
                props['target_variable'] = str(op.TargetVariable.GetValue())
            except Exception:
                pass
            try:
                props['adjusted_variable'] = str(op.AdjustVariable.GetValue()) if hasattr(op, 'AdjustVariable') else None
            except Exception:
                pass
            try:
                props['target_value'] = float(op.TargetValue.GetValue())
            except Exception:
                pass
            try:
                props['tolerance'] = float(op.Tolerance.GetValue())
            except Exception:
                pass
            try:
                props['step_size'] = float(op.StepSize.GetValue())
            except Exception:
                pass
            # Try to get target/adjusted object names
            try:
                props['target_object_name'] = str(op.TargetObject.name)
            except Exception:
                pass
            try:
                props['adjusted_object_name'] = str(op.AdjustObject.name) if hasattr(op, 'AdjustObject') else None
            except Exception:
                pass

        elif type_name == 'setop':
            # Set operation: extract source and target references
            try:
                props['source_object_name'] = str(op.SourceObject.name) if hasattr(op, 'SourceObject') else None
            except Exception:
                pass
            try:
                props['source_variable'] = str(op.SourceVariable.GetValue()) if hasattr(op, 'SourceVariable') else None
            except Exception:
                pass
            try:
                props['target_object_name'] = str(op.TargetObject.name) if hasattr(op, 'TargetObject') else None
            except Exception:
                pass
            try:
                props['target_variable'] = str(op.TargetVariable.GetValue()) if hasattr(op, 'TargetVariable') else None
            except Exception:
                pass

        elif type_name == 'pidfbcontrolop':
            # PID controller: extract tuning parameters
            try:
                props['kp'] = float(op.Gain.GetValue()) if hasattr(op, 'Gain') else None
            except Exception:
                pass
            try:
                props['ti'] = float(op.IntegralTime.GetValue()) if hasattr(op, 'IntegralTime') else None
            except Exception:
                pass
            try:
                props['td'] = float(op.DerivativeTime.GetValue()) if hasattr(op, 'DerivativeTime') else None
            except Exception:
                pass
            try:
                props['setpoint'] = float(op.SPValue.GetValue()) if hasattr(op, 'SPValue') else None
            except Exception:
                pass
            try:
                props['pv_object_name'] = str(op.PVSource.name) if hasattr(op, 'PVSource') else None
            except Exception:
                pass
            try:
                props['op_object_name'] = str(op.OPTarget.name) if hasattr(op, 'OPTarget') else None
            except Exception:
                pass
            try:
                props['is_reverse'] = bool(op.ReverseActing) if hasattr(op, 'ReverseActing') else None
            except Exception:
                pass
            try:
                props['op_min'] = float(op.OPLow.GetValue()) if hasattr(op, 'OPLow') else None
                props['op_max'] = float(op.OPHigh.GetValue()) if hasattr(op, 'OPHigh') else None
            except Exception:
                pass

        elif type_name in ('reactorop', 'gibbsreactorop', 'equilibriumreactorop',
                           'eqreactorop', 'convreactorop', 'conversionreactorop'):
            # Reactor: try to extract volume/dimensions
            try:
                props['volume_m3'] = self._safe_getval(op.Volume, 'm3') if hasattr(op, 'Volume') else None
            except Exception:
                pass
            try:
                props['duty_kW'] = self._safe_getval(op.DutyValue, 'kW') if hasattr(op, 'DutyValue') else None
            except Exception:
                pass

        elif type_name in ('pfreactorop', 'kineticreactorop'):
            # Plug-flow reactor: extract length and diameter
            try:
                props['length_m'] = self._safe_getval(op.Length, 'm') if hasattr(op, 'Length') else None
            except Exception:
                pass
            try:
                props['diameter_m'] = self._safe_getval(op.Diameter, 'm') if hasattr(op, 'Diameter') else None
            except Exception:
                pass
            try:
                props['volume_m3'] = self._safe_getval(op.Volume, 'm3') if hasattr(op, 'Volume') else None
            except Exception:
                pass

        elif type_name == 'cstrop':
            # CSTR: extract volume
            try:
                props['volume_m3'] = self._safe_getval(op.Volume, 'm3') if hasattr(op, 'Volume') else None
            except Exception:
                pass

        elif type_name == 'logicalop' or type_name == 'selectop':
            # Logical operation: try to extract operator type
            try:
                props['logic_type'] = str(op.LogicType) if hasattr(op, 'LogicType') else None
            except Exception:
                pass

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

    @staticmethod
    def resolve_neqsim_type(op: 'UniSimOperation') -> Optional[str]:
        """Resolve the NeqSim equipment type for a UniSim operation.

        Applies orientation-based overrides:
        - Vertical 2-phase flashtank → GasScrubber
        - Horizontal 2-phase flashtank → Separator (default)
        - sep3op always → ThreePhaseSeparator (regardless of orientation)
        """
        base_type = UniSimReader.OPERATION_TYPE_MAP.get(op.type_name)
        if base_type is None:
            return None
        # Vertical 2-phase separator → GasScrubber
        if base_type == 'Separator' and op.properties.get('detected_vertical'):
            return 'GasScrubber'
        return base_type

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
        eos = UniSimReader.PROPERTY_PACKAGE_MAP.get(fp.property_package)
        if eos is None:
            # Try partial/prefix match (e.g. "DBR Amine Package (v2011.1)")
            pp_lower = fp.property_package.lower()
            for key, val in UniSimReader.PROPERTY_PACKAGE_MAP.items():
                if key.lower() in pp_lower or pp_lower.startswith(key.lower()):
                    eos = val
                    break
            if eos is None:
                eos = 'SRK'
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

        mixing_rule = 'classic'
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
        neqsim_type = self.resolve_neqsim_type(op)

        # Skip unsupported types
        if neqsim_type is None:
            self._warnings.append(
                f"Unsupported operation type '{op.type_name}': '{op.name}' — skipped")
            return None

        # Skip utility/control operations that don't have NeqSim equivalents
        if neqsim_type in ('SurgeController', 'ColumnInternals'):
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

        elif neqsim_type in ('Separator', 'GasScrubber',
                             'ThreePhaseSeparator'):
            entrainment_specs = op.properties.get('entrainment', [])
            if entrainment_specs:
                props['entrainment'] = entrainment_specs
            if op.properties.get('diameter_m'):
                props['diameter'] = op.properties['diameter_m']

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
                    neqsim_type = self.resolve_neqsim_type(op)
                    if neqsim_type in ('Separator', 'GasScrubber',
                                       'ThreePhaseSeparator'):
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
                    elif neqsim_type == 'DistillationColumn':
                        # Column products: first = overhead/gas, last = bottoms
                        if len(op.products) > 0 and stream_name in op.products:
                            idx = op.products.index(stream_name)
                            if idx == 0:
                                return f"{producer_name}.gasOut"
                            else:
                                return f"{producer_name}.liquidOut"
                        return f"{producer_name}.gasOut"
                    elif neqsim_type == 'Absorber':
                        # Detect glycol/TEG contactor → ComponentSplitter
                        _name_lower = op.name.lower()
                        _is_glycol = any(kw in _name_lower for kw in
                                         ('glyc', 'teg', 'dehydrat'))
                        if _is_glycol:
                            # ComponentSplitter: split0 = dry gas, split1 = water
                            if len(op.products) > 0 and stream_name in op.products:
                                idx = op.products.index(stream_name)
                                return f"{producer_name}.split{idx}"
                            return f"{producer_name}.split0"
                        # Regular absorber: first product = gas out, second = liquid out
                        if len(op.products) > 0 and stream_name in op.products:
                            idx = op.products.index(stream_name)
                            if idx == 0:
                                return f"{producer_name}.gasOut"
                            else:
                                return f"{producer_name}.liquidOut"
                        return f"{producer_name}.gasOut"
                    elif neqsim_type == 'HeatExchanger':
                        if len(op.products) > 0 and stream_name in op.products:
                            idx = op.products.index(stream_name)
                            return f"{producer_name}.hx{idx}"
                        return f"{producer_name}.hx0"
                    elif neqsim_type in UniSimReader.REACTOR_TYPES:
                        return f"{producer_name}.outlet"
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
            mapped = UniSimReader.COMPONENT_NAME_MAP[unisim_name]
            if mapped is None:
                self._warnings.append(
                    f"Component '{unisim_name}' has no NeqSim equivalent — skipped")
            return mapped

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

        # --- detect forward references (cycles in recycle loops) ---
        defined_ops: set = set(external_feeds)  # feeds are "defined" before equipment
        fwd_ref_placeholders: set = set()
        for op in sorted_ops:
            n_type = self.resolve_neqsim_type(op)
            if n_type is None or n_type in self.SKIPPED_NEQSIM_TYPES:
                defined_ops.add(op.name)
                continue
            for feed_stream in (op.feeds or []):
                if feed_stream in stream_producer:
                    producer_name, _ = stream_producer[feed_stream]
                    if producer_name not in defined_ops:
                        fwd_ref_placeholders.add(producer_name)
            defined_ops.add(op.name)

        return dict(
            fluid=fluid, eos_class=eos_class, flowsheet=flowsheet,
            all_ops=all_operations, all_streams=all_streams,
            stream_producer=stream_producer, external_feeds=external_feeds,
            stream_by_name=stream_by_name, sorted_ops=sorted_ops,
            var_names={}, used_vars=set(),
            fwd_ref_placeholders=fwd_ref_placeholders,
            fwd_ref_vars={},
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
    def _register_fwd_placeholders(topo: dict) -> None:
        """Populate ``topo['fwd_ref_vars']`` from ``topo['fwd_ref_placeholders']``.

        For each producer name in fwd_ref_placeholders, create a ``_fwd_XXX``
        variable name and register it in var_names / used_vars / fwd_ref_vars.
        For multi-outlet equipment (Separator, ThreePhaseSeparator), also
        create port-specific placeholder vars so that downstream equipment
        can reference the correct outlet (gasOut vs liquidOut).
        This must be called before any code generation that uses _outlet_ref.
        """
        used_vars = topo['used_vars']
        fwd_ref_vars = topo['fwd_ref_vars']
        sorted_ops = topo.get('sorted_ops', [])
        op_by_name = {op.name: op for op in sorted_ops}
        for prod_name in sorted(topo['fwd_ref_placeholders']):
            if prod_name in fwd_ref_vars:
                continue  # already registered
            pv = prod_name.replace(' ', '_').replace('-', '_')
            pv = ''.join(c if (c.isalnum() or c == '_') else '_' for c in pv)
            if pv and pv[0].isdigit():
                pv = '_' + pv
            pv = f'_fwd_{pv}'
            used_vars.add(pv)
            fwd_ref_vars[prod_name] = pv
            # Create port-specific placeholders for multi-outlet equipment
            op = op_by_name.get(prod_name)
            if op:
                n_type = UniSimToNeqSim.resolve_neqsim_type(op)
                if n_type in ('Separator', 'GasScrubber') and len(op.products or []) >= 2:
                    ports = ['gasOut', 'liquidOut']
                elif n_type == 'ThreePhaseSeparator' and len(op.products or []) >= 3:
                    ports = ['gasOut', 'oilOut', 'waterOut']
                elif n_type == 'HeatExchanger' and len(op.products or []) >= 2:
                    ports = ['hx0', 'hx1']
                else:
                    ports = []
                for port in ports:
                    port_var = f'{pv}_{port}'
                    used_vars.add(port_var)
                    fwd_ref_vars[f'{prod_name}.{port}'] = port_var

    @staticmethod
    def _outlet_ref(ref: str, var_names: dict, fwd_ref_vars: dict = None) -> str:
        """Resolve a dot-notation ref like ``"Sep.gasOut"`` to a Python expression.

        If *fwd_ref_vars* maps a parent name to a placeholder variable,
        the placeholder stream is returned directly (no method call needed).
        """
        if '.' not in ref:
            return var_names.get(ref, UniSimToNeqSim._to_pyvar(ref))
        parent, port = ref.rsplit('.', 1)
        # If this parent is a forward-reference placeholder, check for
        # a port-specific placeholder first (e.g. separator gasOut/liquidOut)
        if fwd_ref_vars and parent in fwd_ref_vars:
            port_key = f'{parent}.{port}'
            if port_key in fwd_ref_vars:
                return fwd_ref_vars[port_key]
            return fwd_ref_vars[parent]
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
        if port.startswith('hx'):
            idx = port.replace('hx', '') or '0'
            return f'{pvar}.getOutStream(int({idx}))'
        method = port_methods.get(port, 'getOutletStream()')
        return f'{pvar}.{method}'

    # ---- shared code-line generators ----

    SKIPPED_NEQSIM_TYPES = frozenset((
        'SurgeController', 'ColumnInternals',
    ))

    NEQSIM_IMPORTS = (
        'SystemSrkEos = jneqsim.thermo.system.SystemSrkEos',
        'SystemPrEos  = jneqsim.thermo.system.SystemPrEos',
        'ProcessSystem = jneqsim.process.processmodel.ProcessSystem',
        'ProcessModel = jneqsim.process.processmodel.ProcessModel',
        'Stream = jneqsim.process.equipment.stream.Stream',
        'Separator = jneqsim.process.equipment.separator.Separator',
        'GasScrubber = jneqsim.process.equipment.separator.GasScrubber',
        'ThreePhaseSeparator = jneqsim.process.equipment.separator.ThreePhaseSeparator',
        'Mixer = jneqsim.process.equipment.mixer.Mixer',
        'Splitter = jneqsim.process.equipment.splitter.Splitter',
        'Compressor = jneqsim.process.equipment.compressor.Compressor',
        'ThrottlingValve = jneqsim.process.equipment.valve.ThrottlingValve',
        'Cooler = jneqsim.process.equipment.heatexchanger.Cooler',
        'Heater = jneqsim.process.equipment.heatexchanger.Heater',
        'HeatExchanger = jneqsim.process.equipment.heatexchanger.HeatExchanger',
        'Pump = jneqsim.process.equipment.pump.Pump',
        'Expander = jneqsim.process.equipment.expander.Expander',
        'Recycle = jneqsim.process.equipment.util.Recycle',
        'Adjuster = jneqsim.process.equipment.util.Adjuster',
        'SetPoint = jneqsim.process.equipment.util.SetPoint',
        'StreamSaturatorUtil = jneqsim.process.equipment.util.StreamSaturatorUtil',
        'SpreadsheetBlock = jneqsim.process.equipment.util.SpreadsheetBlock',
        'AdiabaticPipe = jneqsim.process.equipment.pipeline.AdiabaticPipe',
        'DistillationColumn = jneqsim.process.equipment.distillation.DistillationColumn',
        'SimpleAbsorber = jneqsim.process.equipment.absorber.SimpleAbsorber',
        'ComponentSplitter = jneqsim.process.equipment.splitter.ComponentSplitter',
        'GibbsReactor = jneqsim.process.equipment.reactor.GibbsReactor',
        'PlugFlowReactor = jneqsim.process.equipment.reactor.PlugFlowReactor',
        'StirredTankReactor = jneqsim.process.equipment.reactor.StirredTankReactor',
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
        neqsim_type = self.resolve_neqsim_type(op)
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

        fwd_ref_vars = topo.get('fwd_ref_vars', {})
        _ref = lambda ref: self._outlet_ref(ref, var_names, fwd_ref_vars)

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
            # Detect column internals from sub-flowsheet to determine
            # whether it has a condenser and/or reboiler
            has_condenser, has_reboiler, n_trays = self._detect_column_config(
                op, topo)
            n_trays = op.properties.get('numberOfTrays', n_trays)
            lines.append(
                f'{v} = DistillationColumn("{op.name}", {n_trays}, '
                f'{has_reboiler}, {has_condenser})')
            if inlet_refs:
                feed_tray = max(1, n_trays // 2)
                lines.append(
                    f'{v}.addFeedStream({_ref(inlet_refs[0])}, {feed_tray})')
                for extra in inlet_refs[1:]:
                    lines.append(f'{v}.addFeedStream({_ref(extra)}, 1)')
            else:
                lines.append(
                    f'# TODO: connect feed stream(s) to {op.name} — '
                    f'feeds: {op.feeds}')

        elif neqsim_type == 'Absorber':
            # Detect glycol/TEG contactor by name — use ComponentSplitter
            _name_lower = op.name.lower()
            _is_glycol = any(kw in _name_lower for kw in
                             ('glyc', 'teg', 'dehydrat'))
            if _is_glycol:
                # TEG contactor: model as ComponentSplitter removing water
                # TEG dehydration pattern: water is always last component
                ref_expr = _ref(inlet_refs[0]) if inlet_refs else 'None'
                lines.append(
                    f'{v} = ComponentSplitter("{op.name}", {ref_expr})')
                lines.append(
                    f'_complen = {ref_expr}.getFluid()'
                    f'.getNumberOfComponents()')
                lines.append(
                    f'{v}.setSplitFactors([1.0] * (_complen - 1) + [0.0])')
            elif len(inlet_refs) >= 2:
                n_stages = op.properties.get('numberOfStages',
                                             op.properties.get('numberOfTrays', 5))
                # Two-feed absorber: gas enters bottom, liquid enters top
                lines.append(
                    f'{v} = DistillationColumn("{op.name}", {n_stages}, '
                    f'False, False)')
                lines.append(
                    f'{v}.addFeedStream({_ref(inlet_refs[0])}, {n_stages})')
                lines.append(
                    f'{v}.addFeedStream({_ref(inlet_refs[1])}, 1)')
            elif len(inlet_refs) == 1:
                n_stages = op.properties.get('numberOfStages',
                                             op.properties.get('numberOfTrays', 5))
                lines.append(
                    f'{v} = DistillationColumn("{op.name}", {n_stages}, '
                    f'False, False)')
                lines.append(
                    f'{v}.addFeedStream({_ref(inlet_refs[0])}, 1)')
                lines.append(
                    f'# TODO: absorber needs a second feed stream '
                    f'(solvent) — feeds: {op.feeds}')
            else:
                n_stages = op.properties.get('numberOfStages',
                                             op.properties.get('numberOfTrays', 5))
                lines.append(
                    f'{v} = DistillationColumn("{op.name}", {n_stages}, '
                    f'False, False)')
                lines.append(
                    f'# TODO: connect feed streams to absorber — '
                    f'feeds: {op.feeds}')

        elif neqsim_type == 'GibbsReactor':
            ref_expr = _ref(inlet_refs[0]) if inlet_refs else 'None'
            lines.append(f'{v} = GibbsReactor("{op.name}", {ref_expr})')

        elif neqsim_type == 'PlugFlowReactor':
            ref_expr = _ref(inlet_refs[0]) if inlet_refs else 'None'
            lines.append(f'{v} = PlugFlowReactor("{op.name}", {ref_expr})')
            # Set reactor dimensions if available
            if op.properties.get('length_m'):
                lines.append(
                    f'{v}.setLength({op.properties["length_m"]}, "m")')
            if op.properties.get('diameter_m'):
                lines.append(
                    f'{v}.setDiameter({op.properties["diameter_m"]}, "m")')

        elif neqsim_type == 'StirredTankReactor':
            ref_expr = _ref(inlet_refs[0]) if inlet_refs else 'None'
            lines.append(
                f'{v} = StirredTankReactor("{op.name}", {ref_expr})')
            if op.properties.get('volume_m3'):
                lines.append(
                    f'{v}.setVesselVolume({op.properties["volume_m3"]})')

        elif neqsim_type == 'Adjuster':
            lines.append(f'{v} = Adjuster("{op.name}")')
            # Configure adjusted variable (the variable being changed)
            adj_obj = op.properties.get('adjusted_object_name')
            adj_var = op.properties.get('adjusted_variable')
            if adj_obj and adj_var:
                adj_obj_var = var_names.get(adj_obj, self._to_pyvar(adj_obj))
                lines.append(
                    f'{v}.setAdjustedVariable({adj_obj_var}, "{adj_var}")')
            elif adj_obj:
                adj_obj_var = var_names.get(adj_obj, self._to_pyvar(adj_obj))
                lines.append(f'{v}.setAdjustedVariable({adj_obj_var})')
            else:
                lines.append(
                    f'# TODO: set adjusted variable — '
                    f'e.g. {v}.setAdjustedVariable(equipment, "pressure")')
            # Configure target variable (the specification to meet)
            tgt_obj = op.properties.get('target_object_name')
            tgt_var = op.properties.get('target_variable')
            tgt_val = op.properties.get('target_value')
            if tgt_obj and tgt_var and tgt_val is not None:
                tgt_obj_var = var_names.get(tgt_obj, self._to_pyvar(tgt_obj))
                lines.append(
                    f'{v}.setTargetVariable({tgt_obj_var}, "{tgt_var}", '
                    f'{tgt_val}, "")')
            elif tgt_obj and tgt_var:
                tgt_obj_var = var_names.get(tgt_obj, self._to_pyvar(tgt_obj))
                lines.append(
                    f'{v}.setTargetVariable({tgt_obj_var}, "{tgt_var}")')
            elif tgt_obj:
                tgt_obj_var = var_names.get(tgt_obj, self._to_pyvar(tgt_obj))
                lines.append(f'{v}.setTargetVariable({tgt_obj_var})')
            else:
                lines.append(
                    f'# TODO: set target variable — '
                    f'e.g. {v}.setTargetVariable(equipment, "pressure", '
                    f'50.0, "bara")')
            tol = op.properties.get('tolerance')
            if tol is not None:
                lines.append(f'{v}.setTolerance({tol})')

        elif neqsim_type == 'SetPoint':
            lines.append(f'{v} = SetPoint("{op.name}")')
            src_obj = op.properties.get('source_object_name')
            src_var = op.properties.get('source_variable')
            tgt_obj = op.properties.get('target_object_name')
            tgt_var = op.properties.get('target_variable')
            if src_obj:
                src_obj_var = var_names.get(src_obj, self._to_pyvar(src_obj))
                if src_var:
                    lines.append(
                        f'{v}.setSourceVariable({src_obj_var}, "{src_var}")')
                else:
                    lines.append(f'{v}.setSourceVariable({src_obj_var})')
            else:
                lines.append(
                    f'# TODO: set source — '
                    f'e.g. {v}.setSourceVariable(sourceEquipment)')
            if tgt_obj:
                tgt_obj_var = var_names.get(tgt_obj, self._to_pyvar(tgt_obj))
                if tgt_var:
                    lines.append(
                        f'{v}.setTargetVariable({tgt_obj_var}, "{tgt_var}")')
                else:
                    lines.append(f'{v}.setTargetVariable({tgt_obj_var})')
            else:
                lines.append(
                    f'# TODO: set target — '
                    f'e.g. {v}.setTargetVariable(targetEquipment, '
                    f'"pressure")')

        elif neqsim_type == 'Recycle':
            lines.append(f'{v} = Recycle("{op.name}")')
            if inlet_refs:
                lines.append(f'{v}.addStream({_ref(inlet_refs[0])})')
            # Wire outlet to the forward-reference placeholder if one exists
            fwd_ref_vars_map = topo.get('fwd_ref_vars', {})
            if op.name in fwd_ref_vars_map:
                pv = fwd_ref_vars_map[op.name]
                lines.append(f'{v}.setOutletStream({pv})')

        elif neqsim_type == 'StreamSaturatorUtil':
            ref_expr = _ref(inlet_refs[0]) if inlet_refs else 'None'
            lines.append(
                f'{v} = StreamSaturatorUtil("{op.name}", {ref_expr})')

        elif neqsim_type == 'Spreadsheet':
            lines.append(f'{v} = SpreadsheetBlock("{op.name}")')
            # Wire inlet stream imports if available
            if inlet_refs:
                ref_expr = _ref(inlet_refs[0])
                lines.append(
                    f'# Spreadsheet inlet: {ref_expr} — '
                    f'add import cells for stream properties:')
                lines.append(
                    f'# {v}.addStreamImportCell("T_in", {ref_expr}, '
                    f'lambda s: float(s.getTemperature("C")))')
                lines.append(
                    f'# {v}.addStreamImportCell("P_in", {ref_expr}, '
                    f'lambda s: float(s.getPressure("bara")))')
            else:
                lines.append(
                    f'# Configure spreadsheet cells:')
                lines.append(
                    f'# {v}.addStreamImportCell("T", stream, '
                    f'lambda s: float(s.getTemperature("C")))')
            lines.append(
                f'# {v}.addFormulaCell("result", '
                f'lambda cells: cells["T_in"] * 1.0)')

        elif neqsim_type == 'SubFlowsheet':
            # Generate a separate ProcessSystem for the sub-flowsheet.
            # These are later composed into a ProcessModel in to_notebook().
            sf_data = self._find_sub_flowsheet_for_op(op, topo)
            if sf_data and sf_data.operations:
                lines.append(f'{v} = ProcessSystem("{op.name}")')
                # Generate equipment lines for the sub-flowsheet
                sf_topo = self._build_sub_topo(sf_data, topo)
                for sf_op in sf_topo['sorted_ops']:
                    sf_lines = self._gen_equipment_lines(sf_op, sf_topo)
                    if sf_lines:
                        # Redirect process.add() to sub-process
                        for sl in sf_lines:
                            if sl.startswith('process.add('):
                                lines.append(
                                    sl.replace('process.add(',
                                               f'{v}.add('))
                            else:
                                lines.append(sl)
            else:
                lines.append(f'{v} = ProcessSystem("{op.name}")')
                lines.append(
                    f'# Sub-flowsheet "{op.name}" has no extractable '
                    f'operations — add equipment manually')

        elif neqsim_type == 'BalanceOp':
            lines.append(f'{v} = Adjuster("{op.name}")')
            # Use same logic as Adjuster with extracted properties
            adj_obj = op.properties.get('adjusted_object_name')
            tgt_obj = op.properties.get('target_object_name')
            tgt_var = op.properties.get('target_variable')
            tgt_val = op.properties.get('target_value')
            if adj_obj:
                adj_obj_var = var_names.get(adj_obj, self._to_pyvar(adj_obj))
                lines.append(f'{v}.setAdjustedVariable({adj_obj_var})')
            if tgt_obj and tgt_var and tgt_val is not None:
                tgt_obj_var = var_names.get(tgt_obj, self._to_pyvar(tgt_obj))
                lines.append(
                    f'{v}.setTargetVariable({tgt_obj_var}, "{tgt_var}", '
                    f'{tgt_val}, "")')
            elif tgt_obj:
                tgt_obj_var = var_names.get(tgt_obj, self._to_pyvar(tgt_obj))
                lines.append(f'{v}.setTargetVariable({tgt_obj_var})')
            if not adj_obj and not tgt_obj:
                lines.append(
                    f'# Balance op — configure adjusted and target '
                    f'variables for mass/energy balance')

        elif neqsim_type == 'PIDController':
            # Generate PID controller creation and equipment attachment
            kp = op.properties.get('kp')
            ti = op.properties.get('ti')
            td = op.properties.get('td')
            sp = op.properties.get('setpoint')
            pv_obj = op.properties.get('pv_object_name')
            op_obj = op.properties.get('op_object_name')
            is_rev = op.properties.get('is_reverse')
            op_min = op.properties.get('op_min')
            op_max = op.properties.get('op_max')

            ctrl_var = self._to_pyvar(f'ctrl_{op.name}')
            lines.append(
                f'{ctrl_var} = jneqsim.process.controllerdevice'
                f'.ControllerDeviceBaseClass("{op.name}")')
            if kp is not None and ti is not None and td is not None:
                lines.append(
                    f'{ctrl_var}.setControllerParameters({kp}, {ti}, {td})')
            if sp is not None:
                lines.append(
                    f'{ctrl_var}.setControllerSetPoint({sp})')
            if is_rev:
                lines.append(f'{ctrl_var}.setReverseActing(True)')
            if op_min is not None and op_max is not None:
                lines.append(
                    f'{ctrl_var}.setOutputLimits({op_min}, {op_max})')
            if pv_obj:
                pv_var = var_names.get(pv_obj, self._to_pyvar(pv_obj))
                lines.append(
                    f'{pv_var}.addController("{op.name}", {ctrl_var})')
            elif op_obj:
                op_var = var_names.get(op_obj, self._to_pyvar(op_obj))
                lines.append(
                    f'{op_var}.addController("{op.name}", {ctrl_var})')
            else:
                lines.append(
                    f'# TODO: attach {ctrl_var} to the controlled '
                    f'equipment via .addController()')

        elif neqsim_type == 'LogicalOp':
            lines.append(
                f'# LogicalOp "{op.name}" — logic operations are not '
                f'directly modeled in NeqSim; implement via Python logic')

        else:
            ref_expr = _ref(inlet_refs[0]) if inlet_refs else 'None'
            lines.append(f'{v} = {neqsim_type}("{op.name}", {ref_expr})')

        self._gen_properties(lines, v, neqsim_type, op, flowsheet)

        # PIDController and LogicalOp are controller devices, not standalone
        # equipment — they produce only TODO comments, no process.add().
        # SubFlowsheet generates its own ProcessSystem — do NOT add it to
        # the parent ProcessSystem (ProcessSystem cannot contain another
        # ProcessSystem). Sub-flowsheet ProcessSystems are combined via
        # ProcessModel in to_notebook().
        if neqsim_type not in ('PIDController', 'LogicalOp', 'SubFlowsheet'):
            lines.append(f'process.add({v})')

        # Wire actual separator/3-phase/HX outlets back to forward ref placeholders
        if neqsim_type in ('Separator', 'GasScrubber',
                           'ThreePhaseSeparator', 'HeatExchanger'):
            fwd_ref_vars_map = topo.get('fwd_ref_vars', {})
            if neqsim_type in ('Separator', 'GasScrubber'):
                port_info = [('gasOut', 'getGasOutStream()'),
                             ('liquidOut', 'getLiquidOutStream()')]
            elif neqsim_type == 'ThreePhaseSeparator':
                port_info = [('gasOut', 'getGasOutStream()'),
                             ('oilOut', 'getOilOutStream()'),
                             ('waterOut', 'getWaterOutStream()')]
            else:  # HeatExchanger
                port_info = [('hx0', 'getOutStream(int(0))'),
                             ('hx1', 'getOutStream(int(1))')]
            for port_name, port_method in port_info:
                port_key = f'{op.name}.{port_name}'
                if port_key in fwd_ref_vars_map:
                    port_pv = fwd_ref_vars_map[port_key]
                    rcy_var = f'_rcy_{v}_{port_name}'
                    lines.append(
                        f'# Auto-recycle: wire {op.name} {port_name} '
                        f'back to forward ref placeholder')
                    lines.append(
                        f'{rcy_var} = Recycle("{op.name}_{port_name}_loop")')
                    lines.append(
                        f'{rcy_var}.addStream({v}.{port_method})')
                    lines.append(
                        f'{rcy_var}.setOutletStream({port_pv})')
                    lines.append(f'process.add({rcy_var})')

        return lines

    def _find_sub_flowsheet_for_op(self, op: 'UniSimOperation',
                                    topo: dict) -> 'Optional[UniSimFlowsheet]':
        """Find the sub-flowsheet belonging to a template/sub-flowsheet op.

        Matches by checking if the operation's product streams appear in
        any sub-flowsheet, or by name match.
        """
        if not topo.get('flowsheet') or not topo['flowsheet'].sub_flowsheets:
            return None

        for sf in topo['flowsheet'].sub_flowsheets:
            # Match by name
            if sf.name and sf.name == op.name:
                return sf
            # Match by stream overlap — op's products appear in sub-flowsheet
            if op.products:
                sf_stream_names = {s.name for s in sf.material_streams}
                if any(p in sf_stream_names for p in op.products):
                    return sf
            # Match by feed overlap
            if op.feeds:
                sf_stream_names = {s.name for s in sf.material_streams}
                if any(f in sf_stream_names for f in op.feeds):
                    return sf
        return None

    def _build_sub_topo(self, sf: 'UniSimFlowsheet',
                        parent_topo: dict) -> dict:
        """Build a mini-topology dict for a sub-flowsheet.

        Reuses the parent topology's fluid, var_names, and used_vars so
        that variable names don't collide between parent and child.
        """
        stream_producer: dict = {}
        for op in sf.operations:
            for s in op.products:
                stream_producer[s] = (op.name, 'outlet')
        # Also include parent stream_producer for cross-flowsheet refs
        for k, v in parent_topo['stream_producer'].items():
            if k not in stream_producer:
                stream_producer[k] = v

        external_feeds: set = set()
        for op in sf.operations:
            for s in op.feeds:
                if s not in stream_producer:
                    external_feeds.add(s)

        stream_by_name = dict(parent_topo['stream_by_name'])
        for s in sf.material_streams:
            stream_by_name[s.name] = s

        sorted_ops = self._topological_sort(sf.operations, stream_producer)

        return dict(
            fluid=parent_topo['fluid'],
            eos_class=parent_topo['eos_class'],
            flowsheet=sf,
            all_ops=sf.operations,
            all_streams=sf.material_streams,
            stream_producer=stream_producer,
            external_feeds=external_feeds,
            stream_by_name=stream_by_name,
            sorted_ops=sorted_ops,
            var_names=parent_topo['var_names'],
            used_vars=parent_topo['used_vars'],
        )

    def _detect_column_config(self, op: 'UniSimOperation',
                              topo: dict) -> tuple:
        """Detect condenser/reboiler presence and tray count from sub-flowsheet.

        Examines the column's sub-flowsheet operations (traysection,
        partialcondenser, totalcondenser, bpreboiler, condenser3op) to
        determine the column configuration.

        Returns:
            (has_condenser, has_reboiler, n_trays)
        """
        has_condenser = True  # defaults
        has_reboiler = True
        n_trays = 5

        # Find the sub-flowsheet that belongs to this column
        if not topo.get('flowsheet'):
            return has_condenser, has_reboiler, n_trays

        for sf in topo['flowsheet'].sub_flowsheets:
            # Match sub-flowsheet by checking if column's products appear in it
            sf_stream_names = {s.name for s in sf.material_streams}
            if not op.products:
                continue
            if any(p in sf_stream_names for p in op.products):
                # Found the column's sub-flowsheet
                sf_types = {sop.type_name.lower() for sop in sf.operations}
                has_condenser = any(
                    t in sf_types for t in (
                        'partialcondenser', 'totalcondenser', 'condenser3op'))
                has_reboiler = 'bpreboiler' in sf_types
                # Count tray sections
                for sop in sf.operations:
                    if sop.type_name.lower() == 'traysection':
                        # Try to get number of trays from tray section
                        n = sop.properties.get('numberOfTrays', 5)
                        if isinstance(n, (int, float)) and n > 1:
                            n_trays = int(n)
                        break
                break

        return has_condenser, has_reboiler, n_trays

    # -----------------------------------------------------------------
    # Process-aware documentation generators
    # -----------------------------------------------------------------

    def _gen_process_narrative(self, topo: dict) -> str:
        """Generate a plain-language narrative describing the complete process.

        Returns a multi-paragraph text explaining what the plant does,
        the fluid composition, key operating conditions, and the process
        sequence from feed to product.
        """
        fluid = topo['fluid']
        flowsheet = topo['flowsheet']
        pp_name = (self.model.fluid_packages[0].property_package
                   if self.model.fluid_packages else 'unknown')

        # Classify the fluid
        comps = fluid.get('components', {})
        comp_list = list(comps.keys())
        comp_fracs = list(comps.values())
        dominant = comp_list[comp_fracs.index(max(comp_fracs))] if comp_fracs else 'unknown'
        has_water = any(c in ('water', 'H2O') for c in comp_list)
        has_co2 = 'CO2' in comp_list
        has_h2s = any(c in ('H2S', 'h2s') for c in comp_list)
        n_heavy = sum(1 for c in comp_list
                      if c.startswith(('n-', 'i-')) or c in ('propane', 'n-butane',
                      'i-butane', 'n-pentane', 'i-pentane', 'n-hexane', 'n-heptane',
                      'n-octane', 'n-nonane', 'n-decane'))
        fluid_type = 'gas'
        if n_heavy >= 4 or dominant in ('n-heptane', 'n-octane', 'n-decane'):
            fluid_type = 'oil/condensate'
        elif n_heavy >= 2:
            fluid_type = 'rich gas'

        # Classify equipment sequence
        eq_types = []
        for op in topo['sorted_ops']:
            nt = self.resolve_neqsim_type(op)
            if nt and nt not in self.SKIPPED_NEQSIM_TYPES:
                eq_types.append(nt)

        has_compression = 'Compressor' in eq_types
        has_separation = any(t in ('Separator', 'GasScrubber',
                                   'ThreePhaseSeparator') for t in eq_types)
        has_cooling = 'Cooler' in eq_types
        has_heating = 'Heater' in eq_types
        has_expansion = any(t in ('Expander', 'ThrottlingValve') for t in eq_types)
        has_distillation = 'DistillationColumn' in eq_types

        # Describe feeds
        feed_descs = []
        for fn in sorted(topo['external_feeds']):
            sd = topo['stream_by_name'].get(fn)
            parts = [f'"{fn}"']
            if sd and sd.temperature_C is not None:
                parts.append(f'{sd.temperature_C:.0f} °C')
            if sd and sd.pressure_bara is not None:
                parts.append(f'{sd.pressure_bara:.0f} bara')
            if sd and sd.mass_flow_kgh and sd.mass_flow_kgh > 0:
                parts.append(f'{sd.mass_flow_kgh:.0f} kg/hr')
            feed_descs.append(', '.join(parts))

        # Build the narrative
        paras = []

        # Paragraph 1: What this model is
        paras.append(
            f'This process model was converted from a UniSim Design simulation '
            f'("{self.model.file_name}") using the {pp_name} property package, '
            f'mapped to the NeqSim **{fluid["model"]}** equation of state.  '
            f'The fluid is a **{fluid_type}** mixture with {len(comp_list)} '
            f'components (dominant: {dominant}'
            + (f', contains CO2' if has_co2 else '')
            + (f', contains H2S' if has_h2s else '')
            + (f', contains water' if has_water else '')
            + ').'
        )

        # Paragraph 2: Feed conditions
        if feed_descs:
            if len(feed_descs) == 1:
                paras.append(f'The single feed stream ({feed_descs[0]}) enters '
                             f'the process and passes through {len(eq_types)} '
                             f'unit operations.')
            else:
                paras.append(
                    f'The process has {len(feed_descs)} feed streams: '
                    + '; '.join(feed_descs)
                    + f'.  These pass through {len(eq_types)} unit operations.'
                )

        # Paragraph 3: Process description based on equipment sequence
        process_steps = []
        if has_cooling:
            n_cool = eq_types.count('Cooler')
            process_steps.append(f'cooling ({n_cool} cooler{"s" if n_cool > 1 else ""})')
        if has_heating:
            n_heat = eq_types.count('Heater')
            process_steps.append(f'heating ({n_heat} heater{"s" if n_heat > 1 else ""})')
        if has_separation:
            sep_types = [t for t in eq_types
                         if t in ('Separator', 'GasScrubber',
                                  'ThreePhaseSeparator')]
            process_steps.append(f'phase separation ({len(sep_types)} stage'
                                 f'{"s" if len(sep_types) > 1 else ""})')
        if has_compression:
            n_comp = eq_types.count('Compressor')
            process_steps.append(f'gas compression ({n_comp} stage'
                                 f'{"s" if n_comp > 1 else ""})')
        if has_expansion:
            process_steps.append('pressure letdown / expansion')
        if has_distillation:
            process_steps.append('distillation')
        has_absorption = 'Absorber' in eq_types
        if has_absorption:
            process_steps.append('absorption')
        n_rx = sum(1 for t in eq_types if t in UniSimReader.REACTOR_TYPES)
        if n_rx > 0:
            process_steps.append(f'chemical reaction ({n_rx} reactor'
                                 f'{"s" if n_rx > 1 else ""})')
        if process_steps:
            paras.append(
                'The process involves: ' + ', '.join(process_steps) + '.'
            )

        # Paragraph 4: Step-by-step sequence
        step_sentences = []
        for op in topo['sorted_ops']:
            nt = self.resolve_neqsim_type(op)
            if nt is None or nt in self.SKIPPED_NEQSIM_TYPES:
                continue
            step_sentences.append(self._describe_equipment_in_context(op, topo))
        if step_sentences:
            paras.append(
                '**Step-by-step:** '
                + '  \n'.join(f'{i+1}. {s}' for i, s in enumerate(step_sentences))
            )

        return '\n\n'.join(paras)

    def _describe_equipment_in_context(self, op: 'UniSimOperation',
                                       topo: dict) -> str:
        """Generate a context-aware description for a single equipment item.

        Unlike the static EQUIPMENT_DESCRIPTIONS, this uses actual operating
        conditions (inlet/outlet T, P, efficiency) to produce a description
        specific to this equipment in this process.
        """
        neqsim_type = self.resolve_neqsim_type(op) or op.type_name
        props = op.properties
        stream_by_name = topo['stream_by_name']
        flowsheet = topo['flowsheet']

        # Gather inlet/outlet stream data
        inlet_sd = stream_by_name.get(op.feeds[0]) if op.feeds else None
        outlet_sd = stream_by_name.get(op.products[0]) if op.products else None

        t_in = f'{inlet_sd.temperature_C:.0f} °C' if inlet_sd and inlet_sd.temperature_C is not None else None
        p_in = f'{inlet_sd.pressure_bara:.0f} bara' if inlet_sd and inlet_sd.pressure_bara is not None else None
        t_out = f'{outlet_sd.temperature_C:.0f} °C' if outlet_sd and outlet_sd.temperature_C is not None else None
        p_out = f'{outlet_sd.pressure_bara:.0f} bara' if outlet_sd and outlet_sd.pressure_bara is not None else None

        # Temperature from properties
        prop_t_out = props.get('outlet_temperature_C')
        if prop_t_out is not None and t_out is None:
            t_out = f'{prop_t_out:.0f} °C'
        prop_p_out = props.get('outlet_pressure_bara')
        if prop_p_out is not None and p_out is None:
            p_out = f'{prop_p_out:.0f} bara'

        if neqsim_type == 'Cooler':
            if t_in and t_out:
                return (f'**{op.name}** cools the gas from {t_in} down to '
                        f'{t_out} to condense heavier components and '
                        f'reduce the downstream temperature.')
            elif t_out:
                return f'**{op.name}** cools the stream to {t_out}.'
            return f'**{op.name}** removes heat from the process stream.'

        elif neqsim_type == 'Heater':
            if t_in and t_out:
                return (f'**{op.name}** heats the stream from {t_in} to '
                        f'{t_out}.')
            elif t_out:
                return f'**{op.name}** heats the stream to {t_out}.'
            return f'**{op.name}** adds heat to the process stream.'

        elif neqsim_type == 'Separator':
            if p_in:
                return (f'**{op.name}** (two-phase separator at {p_in}) '
                        f'flashes the cooled stream into gas and liquid '
                        f'fractions by gravity settling.')
            return (f'**{op.name}** separates the inlet into gas and liquid '
                    f'phases.')

        elif neqsim_type == 'ThreePhaseSeparator':
            if p_in:
                return (f'**{op.name}** (three-phase separator at {p_in}) '
                        f'separates the inlet into gas, oil, and produced '
                        f'water by gravity and residence time.')
            return (f'**{op.name}** separates the inlet into gas, oil, '
                    f'and water phases.')

        elif neqsim_type == 'Compressor':
            eff = props.get('adiabatic_efficiency')
            eff_str = f' at {eff*100:.0f}% isentropic efficiency' if eff and 0 < eff < 1 else ''
            if p_in and p_out:
                return (f'**{op.name}** compresses gas from {p_in} to '
                        f'{p_out}{eff_str}.  The discharge temperature '
                        f'rises due to the work of compression.')
            elif p_out:
                return f'**{op.name}** raises gas pressure to {p_out}{eff_str}.'
            return f'**{op.name}** compresses the gas stream{eff_str}.'

        elif neqsim_type == 'ThrottlingValve':
            if p_in and p_out:
                return (f'**{op.name}** reduces pressure from {p_in} to '
                        f'{p_out} by isenthalpic (Joule-Thomson) expansion.  '
                        f'The temperature drops due to the JT effect.')
            elif p_out:
                return f'**{op.name}** letdown valve to {p_out}.'
            return f'**{op.name}** reduces pressure by throttling.'

        elif neqsim_type == 'Mixer':
            n_in = len(op.feeds)
            return (f'**{op.name}** combines {n_in} inlet streams into a '
                    f'single mixed outlet at the lowest inlet pressure.')

        elif neqsim_type == 'Splitter':
            n_out = len(op.products)
            return (f'**{op.name}** splits one stream into {n_out} outlets '
                    f'with the same composition and conditions.')

        elif neqsim_type == 'Pump':
            if p_out:
                return f'**{op.name}** pumps liquid to {p_out}.'
            return f'**{op.name}** increases liquid pressure.'

        elif neqsim_type == 'HeatExchanger':
            return (f'**{op.name}** transfers heat between two process streams '
                    f'(cross-exchange for energy recovery).')

        elif neqsim_type == 'Expander':
            if p_in and p_out:
                return (f'**{op.name}** expands gas from {p_in} to {p_out}, '
                        f'extracting work and cooling the stream significantly.')
            return f'**{op.name}** expands gas to recover work.'

        elif neqsim_type == 'Recycle':
            return (f'**{op.name}** converges a recycle tear stream so that '
                    f'upstream and downstream conditions are consistent.')

        elif neqsim_type == 'AdiabaticPipe':
            return (f'**{op.name}** models pressure drop and heat loss along '
                    f'the pipeline segment.')

        elif neqsim_type == 'DistillationColumn':
            return (f'**{op.name}** separates components by boiling-point '
                    f'differences across multiple stages.')

        elif neqsim_type == 'Absorber':
            n_in = len(op.feeds)
            if n_in >= 2:
                return (f'**{op.name}** contacts gas with a liquid solvent '
                        f'to remove target components (e.g. CO2, H2S, water).')
            return (f'**{op.name}** absorbs selected components from the '
                    f'gas stream using a packed or trayed column.')

        elif neqsim_type == 'GibbsReactor':
            return (f'**{op.name}** models chemical equilibrium by '
                    f'minimizing Gibbs free energy at the given T and P.')

        elif neqsim_type == 'StirredTankReactor':
            return (f'**{op.name}** models a continuously-stirred tank '
                    f'reactor at steady state.')

        elif neqsim_type == 'Adjuster':
            return (f'**{op.name}** iterates to adjust one process variable '
                    f'until a target specification is met.')

        elif neqsim_type == 'StreamSaturatorUtil':
            return (f'**{op.name}** saturates the stream with water vapour '
                    f'(or another phase) at its current conditions.')

        elif neqsim_type == 'Spreadsheet':
            return (f'**{op.name}** performs user-defined calculations via '
                    f'imported stream variables and formula cells.')

        elif neqsim_type == 'SubFlowsheet':
            return (f'**{op.name}** encapsulates a nested sub-process that '
                    f'runs as an independent module within the main flowsheet.')

        elif neqsim_type == 'BalanceOp':
            return (f'**{op.name}** adjusts a process variable to satisfy '
                    f'a mass or energy balance constraint.')

        elif neqsim_type == 'PIDController':
            return (f'**{op.name}** is a PID controller that regulates '
                    f'a process variable by adjusting a manipulated variable.')

        elif neqsim_type == 'LogicalOp':
            return (f'**{op.name}** evaluates a logical condition '
                    f'(AND/OR/NOT) on process signals.')

        elif neqsim_type == 'SetPoint':
            return (f'**{op.name}** sets a process variable on one unit '
                    f'equal to a variable from another unit.')

        return f'**{op.name}** ({neqsim_type})'

    def _gen_mermaid_flowchart(self, topo: dict) -> str:
        """Generate a Mermaid flowchart of the process topology.

        Returns a string like::

            ```mermaid
            graph LR
                Feed_Gas["Feed Gas"] --> Inlet_Cooler["Inlet Cooler<br/>Cooler"]
                ...
            ```
        """
        lines = ['```mermaid', 'graph LR']
        var_names = {}
        used_vars = set()

        # Style definitions
        lines.append('    classDef feed fill:#4fc3f7,stroke:#0288d1,color:#000')
        lines.append('    classDef sep fill:#81c784,stroke:#388e3c,color:#000')
        lines.append('    classDef comp fill:#ffb74d,stroke:#f57c00,color:#000')
        lines.append('    classDef cool fill:#90caf9,stroke:#1565c0,color:#000')
        lines.append('    classDef heat fill:#ef9a9a,stroke:#c62828,color:#000')
        lines.append('    classDef valve fill:#ce93d8,stroke:#7b1fa2,color:#000')
        lines.append('    classDef default fill:#e0e0e0,stroke:#616161,color:#000')
        lines.append('    classDef reactor fill:#fff176,stroke:#f9a825,color:#000')
        lines.append('    classDef utility fill:#b0bec5,stroke:#455a64,color:#000')
        lines.append('    classDef controller fill:#ffcc80,stroke:#ef6c00,color:#000')
        lines.append('    classDef absorber fill:#a5d6a7,stroke:#2e7d32,color:#000')
        lines.append('    classDef column fill:#b39ddb,stroke:#512da8,color:#000')

        # Build node IDs
        for fn in sorted(topo['external_feeds']):
            v = self._unique_var(fn, var_names, used_vars)

        for op in topo['sorted_ops']:
            nt = self.resolve_neqsim_type(op)
            if nt and nt not in self.SKIPPED_NEQSIM_TYPES:
                self._unique_var(op.name, var_names, used_vars)

        # Feed nodes
        for fn in sorted(topo['external_feeds']):
            v = var_names[fn]
            sd = topo['stream_by_name'].get(fn)
            label = fn
            if sd and sd.temperature_C is not None and sd.pressure_bara is not None:
                label += f'<br/>{sd.temperature_C:.0f}°C, {sd.pressure_bara:.0f} bara'
            lines.append(f'    {v}["{label}"]:::feed')

        # Equipment nodes + edges
        style_map = {
            'Separator': 'sep', 'ThreePhaseSeparator': 'sep',
            'Compressor': 'comp', 'Cooler': 'cool', 'Heater': 'heat',
            'ThrottlingValve': 'valve', 'Expander': 'valve',
            'GibbsReactor': 'reactor', 'PlugFlowReactor': 'reactor',
            'StirredTankReactor': 'reactor',
            'Absorber': 'absorber',
            'DistillationColumn': 'column',
            'StreamSaturatorUtil': 'utility', 'Spreadsheet': 'utility',
            'SubFlowsheet': 'utility', 'BalanceOp': 'utility',
            'PIDController': 'controller', 'LogicalOp': 'controller',
            'Adjuster': 'utility', 'SetPoint': 'utility',
        }
        for op in topo['sorted_ops']:
            nt = self.resolve_neqsim_type(op)
            if nt is None or nt in self.SKIPPED_NEQSIM_TYPES:
                continue
            v = var_names.get(op.name, self._to_pyvar(op.name))
            style = style_map.get(nt, 'default')

            # Node label with key parameter
            label = op.name
            props = op.properties
            if nt == 'Cooler' and props.get('outlet_temperature_C') is not None:
                label += f'<br/>→ {props["outlet_temperature_C"]:.0f}°C'
            elif nt == 'Heater' and props.get('outlet_temperature_C') is not None:
                label += f'<br/>→ {props["outlet_temperature_C"]:.0f}°C'
            elif nt == 'Compressor' and props.get('outlet_pressure_bara') is not None:
                label += f'<br/>→ {props["outlet_pressure_bara"]:.0f} bara'
            elif nt == 'ThrottlingValve' and props.get('outlet_pressure_bara') is not None:
                label += f'<br/>→ {props["outlet_pressure_bara"]:.0f} bara'
            lines.append(f'    {v}["{label}"]:::{style}')

            # Edges from feeds
            for f_name in op.feeds:
                ref = self._resolve_inlet_ref(f_name, topo['stream_producer'])
                if '.' in ref:
                    parent = ref.rsplit('.', 1)[0]
                    src_v = var_names.get(parent, self._to_pyvar(parent))
                else:
                    src_v = var_names.get(ref, self._to_pyvar(ref))
                if src_v:
                    lines.append(f'    {src_v} --> {v}')

        lines.append('```')
        return '\n'.join(lines)

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
        _a('')
        # Embed process narrative in the docstring
        narrative = self._gen_process_narrative(topo)
        for para in narrative.split('\n\n'):
            # Strip markdown bold for plain-text comments
            clean = para.replace('**', '')
            for nline in clean.split('\n'):
                _a(nline)
            _a('')
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

        # --- create placeholder streams for forward references (recycle loops) ---
        var_names = topo['var_names']
        used_vars = topo['used_vars']
        fwd_ref_placeholders = topo['fwd_ref_placeholders']

        if fwd_ref_placeholders:
            _a('# --- Placeholder streams for forward references (recycle loops) ---')
            self._register_fwd_placeholders(topo)
            stream_by_name = topo.get('stream_by_name', {})
            fwd_ref_vars = topo['fwd_ref_vars']
            for prod_name in sorted(fwd_ref_placeholders):
                placeholder_var = fwd_ref_vars[prod_name]
                op_ = None
                for o_ in topo['sorted_ops']:
                    if o_.name == prod_name:
                        op_ = o_
                        break
                n_type = self.resolve_neqsim_type(op_) if op_ else None
                # Determine port-specific placeholders for multi-outlet equipment
                if (n_type in ('Separator', 'GasScrubber')
                        and op_ and len(op_.products or []) >= 2):
                    ports = ['gasOut', 'liquidOut']
                elif (n_type == 'ThreePhaseSeparator'
                      and op_ and len(op_.products or []) >= 3):
                    ports = ['gasOut', 'oilOut', 'waterOut']
                elif (n_type == 'HeatExchanger'
                      and op_ and len(op_.products or []) >= 2):
                    ports = ['hx0', 'hx1']
                else:
                    ports = []
                if ports:
                    for idx, port in enumerate(ports):
                        port_key = f'{prod_name}.{port}'
                        port_pv = fwd_ref_vars.get(port_key)
                        if not port_pv:
                            continue
                        sd = (stream_by_name.get(op_.products[idx])
                              if op_ and idx < len(op_.products) else None)
                        _a(f'# Forward reference placeholder for '
                           f'"{prod_name}" {port}')
                        _a(f'{port_pv} = Stream("{prod_name} {port} '
                           f'(placeholder)", fluid.clone())')
                        if sd and sd.temperature_C is not None:
                            _a(f'{port_pv}.setTemperature('
                               f'{sd.temperature_C}, "C")')
                        if sd and sd.pressure_bara is not None:
                            _a(f'{port_pv}.setPressure('
                               f'{sd.pressure_bara}, "bara")')
                        if (sd and sd.mass_flow_kgh is not None
                                and sd.mass_flow_kgh > 0):
                            _a(f'{port_pv}.setFlowRate('
                               f'{sd.mass_flow_kgh}, "kg/hr")')
                        _a(f'process.add({port_pv})')
                    # Also create default placeholder (first product)
                    sd = (stream_by_name.get(op_.products[0])
                          if op_ and op_.products else None)
                else:
                    sd = None
                    if op_ and op_.products:
                        sd = stream_by_name.get(op_.products[0])
                _a(f'# Forward reference placeholder for "{prod_name}" '
                   f'(in a recycle loop)')
                _a(f'{placeholder_var} = Stream("{prod_name} (placeholder)",'
                   f' fluid.clone())')
                if sd and sd.temperature_C is not None:
                    _a(f'{placeholder_var}.setTemperature('
                       f'{sd.temperature_C}, "C")')
                if sd and sd.pressure_bara is not None:
                    _a(f'{placeholder_var}.setPressure('
                       f'{sd.pressure_bara}, "bara")')
                if (sd and sd.mass_flow_kgh is not None
                        and sd.mass_flow_kgh > 0):
                    _a(f'{placeholder_var}.setFlowRate('
                       f'{sd.mass_flow_kgh}, "kg/hr")')
                _a(f'process.add({placeholder_var})')
                _a('')

        # --- equipment in topological order ---
        _a('# --- Equipment (topological order: upstream before downstream) ---')
        sub_flowsheet_vars = []  # Track sub-flowsheet ProcessSystem vars
        for op in topo['sorted_ops']:
            # Add context-aware comment before each equipment block
            neqsim_type = self.resolve_neqsim_type(op)
            if neqsim_type and neqsim_type not in self.SKIPPED_NEQSIM_TYPES:
                desc = self._describe_equipment_in_context(op, topo)
                # Strip markdown bold for plain-text comment
                desc_clean = desc.replace('**', '')
                _a(f'# {desc_clean}')
            eq_lines = self._gen_equipment_lines(op, topo)
            if eq_lines:
                lines.extend(eq_lines)
                _a('')
            # Collect sub-flowsheet variable names for ProcessModel
            if neqsim_type == 'SubFlowsheet':
                v = topo['var_names'].get(op.name, self._to_pyvar(op.name))
                sub_flowsheet_vars.append((op.name, v))

        # --- compose with ProcessModel if sub-flowsheets exist ---
        has_sub_flowsheets = bool(sub_flowsheet_vars)
        if has_sub_flowsheets:
            _a('# ============================================================')
            _a('# Compose process areas into ProcessModel')
            _a('# ============================================================')
            _a('plant = ProcessModel()')
            _a('plant.add("Main", process)')
            for sf_name, sf_var in sub_flowsheet_vars:
                _a(f'plant.add("{sf_name}", {sf_var})')
            _a('')

        # --- run ---
        _a('# ============================================================')
        _a('# Run simulation')
        _a('# ============================================================')
        if has_sub_flowsheets:
            _a('plant.run()')
            _a('print(plant.getConvergenceSummary())')
            _a('')
            _a('# Print key stream results per process area')
            _a('for area_name in ["Main"'
               + ''.join(f', "{sf_name}"'
                         for sf_name, _ in sub_flowsheet_vars)
               + ']:')
            _a('    area = plant.get(area_name)')
            _a('    print(f"\\n=== {area_name} ===")')
            _a('    for i in range(int(area.getUnitOperations().size())):')
            _a('        u = area.getUnitOperations().get(i)')
            _a('        try:')
            _a('            T = float(u.getTemperature()) - 273.15')
            _a('            P = float(u.getPressure())')
            _a('            print(f"  {str(u.getName()):40s}  '
               'T={T:8.1f} °C   P={P:8.2f} bara")')
            _a('        except Exception:')
            _a('            pass')
        else:
            _a('process.run()')
            _a('')
            _a('# Print key stream results')
            _a('for i in range(int(process.getUnitOperations().size())):')
            _a('    u = process.getUnitOperations().get(i)')
            _a('    try:')
            _a('        T = float(u.getTemperature()) - 273.15')
            _a('        P = float(u.getPressure())')
            _a('        print(f"  {str(u.getName()):40s}  T={T:8.1f} °C   '
               'P={P:8.2f} bara")')
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
        'GasScrubber': 'Vertical gas scrubber — a vertical two-phase '
            'separator optimised for removing liquid droplets from gas.',
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

        # ---- Cell: Process Overview (narrative + flow diagram) ----
        narrative = self._gen_process_narrative(topo)
        _md(f'## Process Overview\n\n{narrative}')

        mermaid = self._gen_mermaid_flowchart(topo)
        _md(f'### Process Flow Diagram\n\n{mermaid}')

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

        # ---- Forward reference placeholders for recycle loops ----
        fwd_ref_placeholders = topo['fwd_ref_placeholders']

        if fwd_ref_placeholders:
            self._register_fwd_placeholders(topo)
            placeholder_lines = [
                '# Placeholder streams for forward references (recycle loops)']
            stream_by_name = topo.get('stream_by_name', {})
            fwd_ref_vars = topo['fwd_ref_vars']
            for prod_name in sorted(fwd_ref_placeholders):
                pv = fwd_ref_vars[prod_name]
                op_ = None
                for o_ in topo['sorted_ops']:
                    if o_.name == prod_name:
                        op_ = o_
                        break
                n_type = self.resolve_neqsim_type(op_) if op_ else None
                if (n_type in ('Separator', 'GasScrubber')
                        and op_ and len(op_.products or []) >= 2):
                    ports = ['gasOut', 'liquidOut']
                elif (n_type == 'ThreePhaseSeparator'
                      and op_ and len(op_.products or []) >= 3):
                    ports = ['gasOut', 'oilOut', 'waterOut']
                elif (n_type == 'HeatExchanger'
                      and op_ and len(op_.products or []) >= 2):
                    ports = ['hx0', 'hx1']
                else:
                    ports = []
                if ports:
                    for idx, port in enumerate(ports):
                        port_key = f'{prod_name}.{port}'
                        port_pv = fwd_ref_vars.get(port_key)
                        if not port_pv:
                            continue
                        sd = (stream_by_name.get(op_.products[idx])
                              if op_ and idx < len(op_.products) else None)
                        placeholder_lines.append(
                            f'# Forward ref: "{prod_name}" {port}')
                        placeholder_lines.append(
                            f'{port_pv} = Stream("{prod_name} {port} '
                            f'(placeholder)", fluid.clone())')
                        if sd and sd.temperature_C is not None:
                            placeholder_lines.append(
                                f'{port_pv}.setTemperature('
                                f'{sd.temperature_C}, "C")')
                        if sd and sd.pressure_bara is not None:
                            placeholder_lines.append(
                                f'{port_pv}.setPressure('
                                f'{sd.pressure_bara}, "bara")')
                        if (sd and sd.mass_flow_kgh is not None
                                and sd.mass_flow_kgh > 0):
                            placeholder_lines.append(
                                f'{port_pv}.setFlowRate('
                                f'{sd.mass_flow_kgh}, "kg/hr")')
                        placeholder_lines.append(
                            f'process.add({port_pv})')
                    sd = (stream_by_name.get(op_.products[0])
                          if op_ and op_.products else None)
                else:
                    sd = None
                    if op_ and op_.products:
                        sd = stream_by_name.get(op_.products[0])
                placeholder_lines.append(
                    f'{pv} = Stream("{prod_name} (placeholder)", '
                    f'fluid.clone())')
                if sd and sd.temperature_C is not None:
                    placeholder_lines.append(
                        f'{pv}.setTemperature({sd.temperature_C}, "C")')
                if sd and sd.pressure_bara is not None:
                    placeholder_lines.append(
                        f'{pv}.setPressure({sd.pressure_bara}, "bara")')
                if sd and sd.mass_flow_kgh is not None and sd.mass_flow_kgh > 0:
                    placeholder_lines.append(
                        f'{pv}.setFlowRate({sd.mass_flow_kgh}, "kg/hr")')
                placeholder_lines.append(f'process.add({pv})')
                placeholder_lines.append('')
            _md('### Recycle Placeholders\n\n'
                'These placeholder streams provide initial values for '
                'forward references in recycle loops.')
            _code(placeholder_lines)

        # ---- Cells 8..N: Equipment ----
        _md('### Equipment')
        sub_flowsheet_vars = []  # Track sub-flowsheet ProcessSystem vars
        for op in topo['sorted_ops']:
            neqsim_type = self.resolve_neqsim_type(op)
            if neqsim_type is None or neqsim_type in self.SKIPPED_NEQSIM_TYPES:
                continue  # skip silently in notebook
            # Use context-aware description instead of static generic text
            desc = self._describe_equipment_in_context(op, topo)
            _md(f'{desc}')
            eq_lines = self._gen_equipment_lines(op, topo)
            if eq_lines:
                _code(eq_lines)
            # Collect sub-flowsheet variable names for ProcessModel
            if neqsim_type == 'SubFlowsheet':
                var_names = topo['var_names']
                v = var_names.get(op.name, self._to_pyvar(op.name))
                sub_flowsheet_vars.append((op.name, v))

        # ---- Compose with ProcessModel if sub-flowsheets exist ----
        has_sub_flowsheets = bool(sub_flowsheet_vars)
        if has_sub_flowsheets:
            _md('## Compose Process Areas\n\n'
                'Combine the main process and sub-flowsheet ProcessSystems '
                'into a `ProcessModel` for coordinated execution.')
            compose_lines = [
                'plant = ProcessModel()',
                'plant.add("Main", process)',
            ]
            for sf_name, sf_var in sub_flowsheet_vars:
                compose_lines.append(
                    f'plant.add("{sf_name}", {sf_var})')
            _code(compose_lines)

        # ---- Run cell ----
        _md('## Run Simulation')
        if has_sub_flowsheets:
            _code(['plant.run()',
                    'print(plant.getConvergenceSummary())'])
        else:
            _code(['process.run()'])

        # ---- Results cell ----
        _md('## Results\n\n'
            'Print temperature, pressure and flow for every unit operation.')
        if has_sub_flowsheets:
            results_code = [
                'for area_name in ["Main"'
                + ''.join(f', "{sf_name}"'
                          for sf_name, _ in sub_flowsheet_vars)
                + ']:',
                '    area = plant.get(area_name)',
                '    print(f"\\n=== {area_name} ===")',
                '    print(f"{\'Unit\':40s}  {\'T (°C)\':>10s}  '
                '{\'P (bara)\':>10s}")',
                '    print("-" * 65)',
                '    for i in range(int(area.getUnitOperations().size())):',
                '        u = area.getUnitOperations().get(i)',
                '        try:',
                '            T = float(u.getTemperature()) - 273.15',
                '            P = float(u.getPressure())',
                '            print(f"  {str(u.getName()):40s}  '
                '{T:8.1f}    {P:8.2f}")',
                '        except Exception:',
                '            pass',
            ]
        else:
            results_code = [
                'print(f"{\'Unit\':40s}  {\'T (°C)\':>10s}  '
                '{\'P (bara)\':>10s}")',
                'print("-" * 65)',
                'for i in range(int(process.getUnitOperations().size())):',
                '    u = process.getUnitOperations().get(i)',
                '    try:',
                '        T = float(u.getTemperature()) - 273.15',
                '        P = float(u.getPressure())',
                '        print(f"  {str(u.getName()):40s}  '
                '{T:8.1f}    {P:8.2f}")',
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
                nt = self.resolve_neqsim_type(op)
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

        # ---- forward reference placeholders for recycle loops ----
        fwd_ref_placeholders = topo['fwd_ref_placeholders']
        if fwd_ref_placeholders:
            self._register_fwd_placeholders(topo)
            _a(f'{indent}# --- Placeholder streams for recycle forward references ---')
            fwd_ref_vars = topo['fwd_ref_vars']
            for prod_name in sorted(fwd_ref_placeholders):
                pv = fwd_ref_vars[prod_name]
                op_ = None
                for o_ in topo['sorted_ops']:
                    if o_.name == prod_name:
                        op_ = o_
                        break
                n_type = self.resolve_neqsim_type(op_) if op_ else None
                if (n_type in ('Separator', 'GasScrubber')
                        and op_ and len(op_.products or []) >= 2):
                    ports = ['gasOut', 'liquidOut']
                elif (n_type == 'ThreePhaseSeparator'
                      and op_ and len(op_.products or []) >= 3):
                    ports = ['gasOut', 'oilOut', 'waterOut']
                elif (n_type == 'HeatExchanger'
                      and op_ and len(op_.products or []) >= 2):
                    ports = ['hx0', 'hx1']
                else:
                    ports = []
                if ports:
                    for idx, port in enumerate(ports):
                        port_key = f'{prod_name}.{port}'
                        port_pv = fwd_ref_vars.get(port_key)
                        if not port_pv:
                            continue
                        sd = (stream_by_name.get(op_.products[idx])
                              if op_ and idx < len(op_.products) else None)
                        t_val = (f'{sd.temperature_C}'
                                 if sd and sd.temperature_C is not None
                                 else '15.0')
                        p_val = (f'{sd.pressure_bara}'
                                 if sd and sd.pressure_bara is not None
                                 else '1.0')
                        fl_val = (f'{sd.mass_flow_kgh}'
                                  if sd and sd.mass_flow_kgh
                                  and sd.mass_flow_kgh > 0 else '1000.0')
                        _a(f'{indent}{port_pv} = get_stream('
                           f'"{prod_name} {port} (placeholder)", fluid, '
                           f'flow_rate={fl_val}, pressure={p_val}, '
                           f'temperature={t_val})')
                    sd = (stream_by_name.get(op_.products[0])
                          if op_ and op_.products else None)
                else:
                    sd = None
                    if op_ and op_.products:
                        sd = stream_by_name.get(op_.products[0])
                t_val = (f'{sd.temperature_C}'
                         if sd and sd.temperature_C is not None else '15.0')
                p_val = (f'{sd.pressure_bara}'
                         if sd and sd.pressure_bara is not None else '1.0')
                fl_val = (f'{sd.mass_flow_kgh}'
                          if sd and sd.mass_flow_kgh
                          and sd.mass_flow_kgh > 0 else '1000.0')
                _a(f'{indent}{pv} = get_stream("{prod_name} (placeholder)",'
                   f' fluid, flow_rate={fl_val}, pressure={p_val}, '
                   f'temperature={t_val})')
            _a('')

        # ---- equipment ----
        for op in topo['sorted_ops']:
            neqsim_type = self.resolve_neqsim_type(op)
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

            fwd_ref_vars = topo.get('fwd_ref_vars', {})
            _ref = lambda r: self._outlet_ref(r, var_names, fwd_ref_vars)

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
            neqsim_type = self.resolve_neqsim_type(op)
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
            if eff is not None and eff > 1:
                eff = eff / 100.0  # convert percentage to fraction
            poly = op.properties.get('polytropic_efficiency')
            if poly is not None and poly > 1:
                poly = poly / 100.0  # convert percentage to fraction
            if eff and 0 < eff <= 1:
                lines.append(f'{var}.setIsentropicEfficiency({eff})')
            elif poly and 0 < poly <= 1:
                lines.append(f'{var}.setPolytropicEfficiency({poly})')
                lines.append(f'{var}.setUsePolytropicCalc(True)')
            else:
                # No efficiency extracted — use engineering default
                lines.append(f'{var}.setIsentropicEfficiency(0.75)')
                lines.append(f'# WARNING: compressor efficiency not available '
                             f'from source model — using 75% default')

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

        elif neqsim_type in ('Separator', 'GasScrubber',
                             'ThreePhaseSeparator'):
            # Entrainment specs extracted from UniSim
            entrainment_specs = op.properties.get('entrainment', [])
            for ent in entrainment_specs:
                val = ent['value']
                spec_type = ent.get('specType', 'volume')
                spec_stream = ent.get('specifiedStream', 'product')
                phase_from = ent['phaseFrom']
                phase_to = ent['phaseTo']
                lines.append(
                    f'{var}.setEntrainment({val}, "{spec_type}", '
                    f'"{spec_stream}", "{phase_from}", "{phase_to}")  '
                    f'# UniSim: {phase_from} in {phase_to}')
            # Dimensions
            if op.properties.get('diameter_m'):
                lines.append(
                    f'{var}.setInternalDiameter({op.properties["diameter_m"]})')


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
            ops = self.neqsim_process.getUnitOperations()
            n_units = int(ops.size())
            for i in range(n_units):
                unit = ops.get(i)
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
