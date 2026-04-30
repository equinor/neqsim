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

# Names recognized by EclipseFluidReadWrite as database components or common
# aliases. Components not listed here are intentionally kept as named TBP
# pseudo-fractions so UniSim oil/hypothetical characterization parameters are
# preserved from the E300 property tables.
E300_COMPONENT_NAME_MAP = {
    'Nitrogen': 'N2', 'N2': 'N2',
    'CO2': 'CO2', 'CarbonDioxide': 'CO2', 'Carbon Dioxide': 'CO2',
    'Methane': 'C1', 'C1': 'C1',
    'Ethane': 'C2', 'C2': 'C2',
    'Propane': 'C3', 'C3': 'C3',
    'i-Butane': 'iC4', 'iC4': 'iC4', 'Isobutane': 'iC4',
    'n-Butane': 'C4', 'nC4': 'C4', 'C4': 'C4',
    'i-Pentane': 'iC5', 'iC5': 'iC5', 'Isopentane': 'iC5',
    'n-Pentane': 'C5', 'nC5': 'C5', 'C5': 'C5',
    'n-Hexane': 'C6', 'nC6': 'C6', 'C6': 'C6',
    'n-Heptane': 'nC7', 'nC7': 'nC7',
    'n-Octane': 'nC8', 'nC8': 'nC8',
    'n-Nonane': 'nC9', 'nC9': 'nC9',
    'n-Decane': 'nC10', 'nC10': 'nC10',
    'nC11': 'nC11', 'nC12': 'nC12', 'nC13': 'nC13', 'nC14': 'nC14',
    'nC15': 'nC15', 'nC16': 'nC16', 'nC17': 'nC17', 'nC18': 'nC18',
    'nC19': 'nC19', 'nC20': 'nC20',
    'H2O': 'H2O', 'Water': 'H2O',
    'H2S': 'H2S', 'Hydrogen Sulfide': 'H2S', 'Hydrogen Sulphide': 'H2S',
    'Hydrogen': 'H2', 'H2': 'H2',
    'Oxygen': 'O2', 'O2': 'O2',
    'Argon': 'Ar', 'Ar': 'Ar',
    'Helium': 'He', 'He': 'He',
    'CO': 'CO', 'CarbonMonoxide': 'CO', 'Carbon Monoxide': 'CO',
    'Methanol': 'MeOH', 'MeOH': 'MeOH',
    'EGlycol': 'MEG', 'MEG': 'MEG',
    'DEGlycol': 'DEG', 'DEG': 'DEG',
    'TEGlycol': 'TEG', 'TEG': 'TEG',
    'Benzene': 'benzene', 'Toluene': 'toluene',
    'E-Benzene': 'ethylbenzene', 'Ethylbenzene': 'ethylbenzene',
    'm-Xylene': 'm-Xylene', 'o-Xylene': 'o-Xylene', 'p-Xylene': 'p-Xylene',
}

# ---------------------------------------------------------------------------
# Data classes for the extracted UniSim model
# ---------------------------------------------------------------------------

@dataclass
class UniSimComponent:
    """A component in a fluid package."""
    name: str
    index: int
    is_hypothetical: bool = False  # * suffix means hypo in UniSim
    # Critical properties (populated when extracted via COM)
    tc_K: Optional[float] = None
    pc_bara: Optional[float] = None
    acentric_factor: Optional[float] = None
    mw: Optional[float] = None
    tboil_K: Optional[float] = None
    vcrit_m3_kgmol: Optional[float] = None
    volume_shift: Optional[float] = None
    parachor: Optional[float] = None
    # EOS-specific parameters (OMEGAA, OMEGAB)
    omegaa: Optional[float] = None
    omegab: Optional[float] = None
    # Critical compressibility factor
    zcrit: Optional[float] = None
    # Volume shift at surface conditions (SSHIFTS)
    sshifts: Optional[float] = None


@dataclass
class UniSimFluidPackage:
    """A fluid package (thermodynamic basis) in UniSim."""
    name: str
    property_package: str  # e.g. "Peng-Robinson", "SRK", "ASME Steam"
    components: List[UniSimComponent] = field(default_factory=list)
    # Binary interaction parameters: bips[i][j] for i > j (lower triangle)
    bips: Optional[List[List[float]]] = None
    # Surface-condition BIPs (BICS section): bips_surface[i][j] for i > j
    bips_surface: Optional[List[List[float]]] = None
    # Use Pedersen (PFCT) viscosity model
    use_pedersen: bool = False
    # Path to exported E300 file (set after write_e300() is called)
    e300_file_path: Optional[str] = None
    # Composition from a reference stream (mole fractions, same order as components)
    reference_composition: Optional[List[float]] = None

    @property
    def component_names(self) -> List[str]:
        return [c.name for c in self.components]

    @property
    def has_critical_properties(self) -> bool:
        """True if every component has the core E300 critical properties."""
        return bool(self.components) and all(
            c.tc_K is not None and c.pc_bara is not None and c.mw is not None
            for c in self.components)

    @staticmethod
    def _e300_component_name(component_name: str) -> str:
        """Return the E300/NeqSim component name for a UniSim component name."""
        if component_name in E300_COMPONENT_NAME_MAP:
            return E300_COMPONENT_NAME_MAP[component_name]
        normalized = component_name.replace(' ', '').replace('_', '').replace('-', '').lower()
        for candidate_name, e300_name in E300_COMPONENT_NAME_MAP.items():
            candidate_normalized = candidate_name.replace(' ', '').replace('_', '')
            candidate_normalized = candidate_normalized.replace('-', '').lower()
            if normalized == candidate_normalized:
                return e300_name
        return component_name.replace('/', '_')

    @staticmethod
    def _estimate_parachor(component: UniSimComponent) -> float:
        """Estimate parachor when UniSim does not expose one explicitly."""
        if component.parachor is not None:
            return component.parachor
        if component.mw is None or component.mw <= 0.0:
            return 0.0
        return 4.0 * component.mw ** 0.77

    def write_e300(self, output_path: str,
                   composition: Optional[List[float]] = None,
                   reservoir_temp_C: float = 100.0) -> str:
        """Write this fluid package to Eclipse E300 format (PVTsim reference format).

        Uses trailing ``/`` on the last value line (not standalone ``/``).
        Includes all available sections: CNAMES, TCRIT, PCRIT, ACF, OMEGAA,
        OMEGAB, MW, TBOIL, VCRIT, ZCRIT, SSHIFT, PARACHOR, ZI, BIC, BICS,
        PEDERSEN, SSHIFTS.

        Args:
            output_path: File path to write (e.g. 'fluid_pkg.e300').
            composition: Mole fractions per component. If None, uses
                         reference_composition or equal molar.
            reservoir_temp_C: Reservoir temperature for RTEMP keyword.

        Returns:
            The output_path written to.
        """
        if not self.has_critical_properties:
            missing = [c.name for c in self.components
                       if c.tc_K is None or c.pc_bara is None or c.mw is None]
            raise ValueError(
                f"Fluid package '{self.name}' is missing E300 critical "
                f"properties for: {', '.join(missing) or 'all components'}. "
                f"Extract via COM first (UniSimReader with extract_properties=True).")

        n = len(self.components)
        zi = composition or self.reference_composition
        if zi is None:
            zi = [1.0 / n] * n

        package_lower = self.property_package.lower()
        eos_keyword = 'PR' if 'peng' in package_lower else 'SRK'
        use_pr_lk = eos_keyword == 'PR' and (
            'lk' in package_lower or 'lee' in package_lower or 'kesler' in package_lower)

        def _write_section(lines, keyword, values, fmt, comment=None):
            """Write a section with trailing / on the last value."""
            if comment:
                lines.append(f'-- {comment}')
            lines.append(keyword)
            for i, v in enumerate(values):
                val_str = fmt.format(v)
                if i == len(values) - 1:
                    lines.append(f'{val_str}  /')
                else:
                    lines.append(val_str)

        def _write_bic_section(lines, keyword, bips_matrix, n_comps, comment=None):
            """Write a BIC/BICS lower-triangular matrix section."""
            if comment:
                lines.append(f'-- {comment}')
            lines.append(keyword)
            for i in range(1, n_comps):
                row_vals = []
                for j in range(i):
                    row_vals.append(f'{bips_matrix[i][j]:.4f}')
                row_str = '  '.join(row_vals)
                if i == n_comps - 1:
                    lines.append(f'  {row_str}  /')
                else:
                    lines.append(f'  {row_str}')

        lines = []
        lines.append(f'-- E300 fluid exported from UniSim package: {self.name}')
        lines.append(f'-- Property package: {self.property_package}')
        lines.append('METRIC')
        lines.append('')
        lines.append('NCOMPS')
        lines.append(f'{n}  /')
        lines.append('')
        lines.append('EOS')
        lines.append(f'{eos_keyword}  /')
        lines.append('')
        if eos_keyword == 'PR':
            lines.append('PRLKCORR' if use_pr_lk else 'PRCORR')
            lines.append('')
        lines.append('-- Reservoir temperature (C)')
        lines.append('RTEMP')
        lines.append(f'   {reservoir_temp_C:.5f}  /')
        lines.append('')
        lines.append('-- Standard Conditions (C and bara)')
        lines.append('STCOND')
        lines.append('   15.00000    1.01325  /')
        lines.append('')

        # Component names — last name gets trailing /
        lines.append('-- Component names')
        lines.append('CNAMES')
        for i, c in enumerate(self.components):
            e300_name = self._e300_component_name(c.name)
            if i == n - 1:
                lines.append(f'{e300_name}   /')
            else:
                lines.append(e300_name)
        lines.append('')

        # TCRIT (K)
        tc_vals = [c.tc_K for c in self.components]
        _write_section(lines, 'TCRIT', tc_vals, '   {:.3f}', 'Tc (K)')
        lines.append('')

        # PCRIT (bara)
        pc_vals = [c.pc_bara for c in self.components]
        _write_section(lines, 'PCRIT', pc_vals, '   {:.4f}', 'Pc (Bar)')
        lines.append('')

        # ACF (acentric factor)
        acf_vals = [c.acentric_factor if c.acentric_factor is not None else 0.0
                    for c in self.components]
        _write_section(lines, 'ACF', acf_vals, '   {:.6f}', 'Omega')
        lines.append('')

        # OMEGAA (EOS-specific)
        if any(c.omegaa is not None for c in self.components):
            omegaa_vals = [c.omegaa if c.omegaa is not None else 0.4572355
                          for c in self.components]
            _write_section(lines, 'OMEGAA', omegaa_vals, '   {:.7f}',
                          'EOS Constant Omega A')
            lines.append('')

        # OMEGAB (EOS-specific)
        if any(c.omegab is not None for c in self.components):
            omegab_vals = [c.omegab if c.omegab is not None else 0.0777961
                          for c in self.components]
            _write_section(lines, 'OMEGAB', omegab_vals, '   {:.7f}',
                          'EOS Constant Omega B')
            lines.append('')

        # MW (g/mol)
        mw_vals = [c.mw if c.mw is not None else 0.0 for c in self.components]
        _write_section(lines, 'MW', mw_vals, '   {:.3f}', 'Molecular Weight (g/mol)')
        lines.append('')

        # TBOIL (K)
        if any(c.tboil_K is not None for c in self.components):
            tboil_vals = [c.tboil_K if c.tboil_K is not None else 0.0
                         for c in self.components]
            _write_section(lines, 'TBOIL', tboil_vals, '   {:.3f}',
                          'Normal Boiling Point (K)')
            lines.append('')

        # VCRIT (m3/kgmol)
        if any(c.vcrit_m3_kgmol is not None for c in self.components):
            vcrit_vals = [c.vcrit_m3_kgmol if c.vcrit_m3_kgmol is not None else 0.0
                         for c in self.components]
            _write_section(lines, 'VCRIT', vcrit_vals, '   {:.5f}',
                          'Critical Volume (m3/kmol)')
            lines.append('')

        # ZCRIT
        if any(c.zcrit is not None for c in self.components):
            zcrit_vals = [c.zcrit if c.zcrit is not None else 0.0
                         for c in self.components]
            _write_section(lines, 'ZCRIT', zcrit_vals, '   {:.6f}',
                          'Critical Z-factor')
            lines.append('')

        # SSHIFT (volume translation at reservoir conditions). Always emit so
        # zero shifts remain explicit and the component ordering is reproducible.
        vs_vals = [c.volume_shift if c.volume_shift is not None else 0.0
                   for c in self.components]
        _write_section(lines, 'SSHIFT', vs_vals, '   {:.6f}',
                       'Volume Translation')
        lines.append('')

        # PARACHOR. UniSim does not expose this consistently for all fluid
        # packages, so use the extracted value when available and a molecular-
        # weight based estimate only as an explicit fallback.
        par_vals = [self._estimate_parachor(c) for c in self.components]
        _write_section(lines, 'PARACHOR', par_vals, '    {:.3f}', 'Parachor')
        lines.append('')

        # ZI (mole fractions)
        _write_section(lines, 'ZI', zi, '   {:.10f}', 'Mole Fractions')
        lines.append('')

        # BIC (lower triangular matrix)
        bips = self.bips if self.bips and len(self.bips) == n else [[0.0] * n for _ in range(n)]
        _write_bic_section(lines, 'BIC', bips, n,
                           'Binary Interaction Coefficients (lower triangular)')
        lines.append('')

        # BICS (surface-condition BIPs)
        if self.bips_surface and len(self.bips_surface) == n:
            _write_bic_section(lines, 'BICS', self.bips_surface, n,
                              'Binary Interaction Coefficients at surface conditions')
            lines.append('')

        # PEDERSEN viscosity model
        if self.use_pedersen:
            lines.append('-- Viscosity correlation')
            lines.append('PEDERSEN')
            lines.append('')

        # SSHIFTS (volume translation at surface conditions)
        if any(c.sshifts is not None for c in self.components):
            sshifts_vals = [c.sshifts if c.sshifts is not None else 0.0
                           for c in self.components]
            _write_section(lines, 'SSHIFTS', sshifts_vals, '   {:.6f}',
                          'Volume translation/co-volume at surface conditions')
            lines.append('')

        lines.append('')  # trailing newline

        with open(output_path, 'w') as f:
            f.write('\n'.join(lines))

        self.e300_file_path = output_path
        logger.info(f"Wrote E300 fluid file: {output_path} "
                    f"({n} components, EOS={eos_keyword}"
                    f"{'-LK' if use_pr_lk else ''})")
        return output_path


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
        'spreadsheetop': 'SpreadsheetBlock',
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
        'balanceop': 'Adjuster',
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
        'Peng-Robinson - LK': 'PR_LK',
        'Peng Robinson - LK': 'PR_LK',
        'SRK': 'SRK',
        'Soave-Redlich-Kwong': 'SRK',
        'CPA': 'CPA',
        'CPA-SRK': 'CPA',
        'ASME Steam': 'SRK',  # Will only have water
        'GERG 2008': 'GERG2008',
        'MBWR': 'SRK',  # fallback
        'Lee-Kesler-Plocker': 'PR_LK',
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

    def read(self, usc_path: str, extract_streams: bool = True,
             export_e300: bool = True, e300_output_dir: Optional[str] = None
             ) -> UniSimModel:
        """Read a .usc file and extract the complete model.

        Args:
            usc_path: Path to the .usc file.
            extract_streams: If True, extract full stream property data.
                             Set False for faster extraction (topology only).
            export_e300: If True (default), export E300 fluid files for all
                         fluid packages. This is the recommended route for
                         importing fluids into NeqSim, as it preserves all
                         critical properties (Tc, Pc, omega, MW, BIPs) for
                         both standard and hypothetical components.
            e300_output_dir: Directory for E300 files. Defaults to same
                             directory as the .usc file.

        Returns:
            UniSimModel with all extracted data. Each fluid package will have
            its e300_file_path set if export_e300 is True.
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

        # Extract fluid packages (with critical properties for E300 export)
        model.fluid_packages = self._extract_fluid_packages(
            case.BasisManager, extract_properties=export_e300, case=case)
        comp_names = (model.fluid_packages[0].component_names
                      if model.fluid_packages else [])

        # Extract flowsheet
        model.flowsheet = self._extract_flowsheet(
            case.Flowsheet, comp_names, extract_streams)

        # Export E300 files for all fluid packages
        if export_e300:
            if e300_output_dir is None:
                e300_output_dir = os.path.dirname(usc_path)
            os.makedirs(e300_output_dir, exist_ok=True)
            base_name = os.path.splitext(os.path.basename(usc_path))[0]

            # Get composition from first feed stream for reference
            ref_comp = self._get_reference_composition(model)

            for idx, fp in enumerate(model.fluid_packages):
                if fp.has_critical_properties:
                    # Set reference composition on the fluid package
                    if ref_comp and len(ref_comp) == len(fp.components):
                        fp.reference_composition = ref_comp
                    # Generate E300 filename
                    safe_name = fp.name.replace(' ', '_').replace('/', '_')
                    e300_path = os.path.join(
                        e300_output_dir,
                        f'{base_name}_{safe_name}.e300')
                    try:
                        fp.write_e300(e300_path)
                        logger.info(f"Exported E300: {e300_path}")
                    except Exception as e:
                        logger.warning(
                            f"Failed to export E300 for '{fp.name}': {e}")
                else:
                    logger.warning(
                        f"Fluid package '{fp.name}' has no critical "
                        f"properties — skipping E300 export")

        # Close the case (not the app — may read more files)
        case.Close()
        logger.info(f"Extracted: {len(model.all_operations())} operations, "
                     f"{len(model.all_streams())} streams")
        return model

    @staticmethod
    def _get_reference_composition(model: UniSimModel) -> Optional[List[float]]:
        """Get mole fraction composition from the first feed stream.

        Returns a list of mole fractions in the same order as the fluid
        package components, or None if not available.
        """
        if not model.flowsheet or not model.fluid_packages:
            return None
        fp = model.fluid_packages[0]
        comp_names = fp.component_names

        # Find a stream with composition data
        for stream in model.flowsheet.material_streams:
            if stream.composition and len(stream.composition) >= len(comp_names):
                zi = []
                for name in comp_names:
                    zi.append(stream.composition.get(name, 0.0))
                if sum(zi) > 0.5:  # sanity check
                    return zi
        return None

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

    def _extract_fluid_packages(self, basis,
                                extract_properties: bool = True,
                                case=None
                                ) -> List[UniSimFluidPackage]:
        """Extract all fluid packages from BasisManager.

        Args:
            basis: UniSim BasisManager COM object.
            extract_properties: If True, extract critical properties (Tc, Pc,
                acentric factor, MW, BIPs) for each component. These are
                needed for E300 export. Set False for faster extraction.
            case: Optional SimulationCase COM object used for flowsheet-level
                fluid package fallbacks when BasisManager does not enumerate
                the active package.
        """
        packages = []
        for package_index, fluid_package in enumerate(
                self._iter_fluid_package_objects(basis, case)):
            try:
                packages.append(self._extract_single_fluid_package(
                    fluid_package, package_index, extract_properties))
            except Exception as exc:
                logger.warning(
                    f"Error extracting fluid package [{package_index}]: {exc}")
        if not packages:
            logger.warning("No UniSim fluid packages were extracted")
        return packages

    def _iter_fluid_package_objects(self, basis, case=None) -> List[Any]:
        """Return unique UniSim fluid package COM objects from known paths."""
        packages = []
        seen = set()

        def add_package(fluid_package):
            if fluid_package is None:
                return
            name = str(self._safe_get(fluid_package, 'name',
                                      self._safe_get(fluid_package, 'Name', '<unnamed>')))
            package_name = str(self._safe_get(fluid_package, 'PropertyPackageName', ''))
            key = f'{name}|{package_name}'
            if key not in seen:
                seen.add(key)
                packages.append(fluid_package)

        for source in [basis, self._safe_get(case, 'BasisManager', None) if case else None]:
            try:
                collection = source.FluidPackages
                for package_index in range(collection.Count):
                    try:
                        add_package(collection.Item(package_index))
                    except Exception:
                        try:
                            add_package(collection.Item(package_index + 1))
                        except Exception:
                            pass
            except Exception:
                pass
            try:
                add_package(source.FluidPackage)
            except Exception:
                pass

        if case is not None:
            try:
                add_package(case.Flowsheet.FluidPackage)
            except Exception:
                pass
            try:
                subflowsheets = case.Flowsheet.Flowsheets
                for flowsheet_index in range(subflowsheets.Count):
                    try:
                        add_package(subflowsheets.Item(flowsheet_index).FluidPackage)
                    except Exception:
                        pass
            except Exception:
                pass
        return packages

    def _extract_single_fluid_package(self, fp, package_index: int,
                                      extract_properties: bool) -> UniSimFluidPackage:
        """Extract one UniSim fluid package COM object."""
        package_name = self._safe_get(fp, 'name', None)
        if package_name is None:
            package_name = self._safe_get(fp, 'Name', f'FP_{package_index}')
        property_package_name = self._safe_get(fp, 'PropertyPackageName', None)
        if property_package_name is None:
            property_package = self._safe_get(fp, 'PropertyPackage', None)
            property_package_name = self._safe_get(property_package, 'Name', 'Unknown')
        pkg = UniSimFluidPackage(
            name=str(package_name),
            property_package=str(property_package_name),
        )
        component_collection = fp.Components
        number_of_components = component_collection.Count
        for component_index in range(number_of_components):
            comp = component_collection.Item(component_index)
            component_name = self._safe_get(comp, 'name', None)
            if component_name is None:
                component_name = self._safe_get(comp, 'Name', f'Comp_{component_index}')
            extracted_component = UniSimComponent(
                name=str(component_name),
                index=component_index,
                is_hypothetical=str(component_name).endswith('*'),
            )
            if extract_properties:
                self._populate_component_properties(comp, extracted_component)
            pkg.components.append(extracted_component)

        if extract_properties:
            pkg.bips = self._extract_bips(fp, number_of_components)
            self._populate_property_package_vectors(fp, pkg)
        return pkg

    def _populate_component_properties(self, comp, extracted_component: UniSimComponent):
        """Populate a UniSimComponent with scalar properties from COM."""
        extracted_component.tc_K = self._extract_quantity(
            comp, ['CriticalTemperature'], [('K', 1.0, 0.0), ('C', 1.0, 273.15),
                                           (None, 1.0, 0.0)])
        extracted_component.pc_bara = self._extract_quantity(
            comp, ['CriticalPressure'], [('bar', 1.0, 0.0), ('bara', 1.0, 0.0),
                                        ('kPa', 0.01, 0.0), (None, 0.01, 0.0)])
        extracted_component.acentric_factor = self._extract_quantity(
            comp, ['AcentricFactor', 'AcentricityFactor', 'Omega'], [(None, 1.0, 0.0)])
        extracted_component.mw = self._extract_quantity(
            comp, ['MolecularWeight'], [(None, 1.0, 0.0), ('kg/kmol', 1.0, 0.0)])
        extracted_component.tboil_K = self._extract_quantity(
            comp, ['NormalBoilingPoint', 'NormalBoilingPt', 'BoilingPoint'],
            [('K', 1.0, 0.0), ('C', 1.0, 273.15), (None, 1.0, 0.0)])
        extracted_component.vcrit_m3_kgmol = self._extract_quantity(
            comp, ['CriticalVolume'], [(None, 1.0, 0.0), ('m3/kgmole', 1.0, 0.0)])
        extracted_component.parachor = self._extract_quantity(
            comp, ['Parachor'], [(None, 1.0, 0.0)])
        extracted_component.zcrit = self._extract_quantity(
            comp, ['CriticalZFactor', 'CriticalCompressibility'], [(None, 1.0, 0.0)])

    def _populate_property_package_vectors(self, fp, pkg: UniSimFluidPackage):
        """Populate component vectors exposed by the UniSim property package."""
        try:
            property_package = fp.PropertyPackage
        except Exception:
            return
        vector_specs = [
            ('volume_shift', ['VolumShift', 'VolumeShift', 'VolumeShifts', 'SSHIFT']),
            ('parachor', ['Parachor', 'Parachors']),
            ('omegaa', ['OmegaA', 'OMEGAA']),
            ('omegab', ['OmegaB', 'OMEGAB']),
            ('sshifts', ['VolumShiftSurface', 'VolumeShiftSurface', 'SSHIFTS']),
        ]
        for attribute_name, candidate_names in vector_specs:
            values = self._extract_package_vector(property_package, candidate_names,
                                                  len(pkg.components))
            if values is None:
                continue
            for component_index, value in enumerate(values):
                if value is not None:
                    setattr(pkg.components[component_index], attribute_name, value)
        for component_index, extracted_component in enumerate(pkg.components):
            if extracted_component.volume_shift is not None:
                continue
            try:
                volume_shift = property_package.GetVolumeShift(component_index)
                if volume_shift is not None:
                    extracted_component.volume_shift = float(volume_shift)
            except Exception:
                pass

    def _extract_quantity(self, obj, attribute_names: List[str], unit_specs) -> Optional[float]:
        """Extract a numeric UniSim property using a list of attributes and units."""
        for attribute_name in attribute_names:
            prop = self._safe_get(obj, attribute_name, None)
            if prop is None:
                continue
            for unit, scale, offset in unit_specs:
                value = self._safe_getval(prop, unit, None)
                if value is not None:
                    return value * scale + offset
        return None

    def _extract_package_vector(self, property_package, candidate_names: List[str],
                                expected_length: int) -> Optional[List[Optional[float]]]:
        """Extract a one-dimensional vector from UniSim property package aliases."""
        for candidate_name in candidate_names:
            holder = self._safe_get(property_package, candidate_name, None)
            if holder is None:
                continue
            raw_values = self._safe_get(holder, 'Values', holder)
            try:
                values = list(raw_values)
            except Exception:
                continue
            if len(values) < expected_length:
                continue
            cleaned = []
            for component_index in range(expected_length):
                cleaned.append(self._clean_matrix_value(values[component_index]))
            return cleaned
        return None

    def _extract_bips(self, fp_com, n_comps: int) -> Optional[List[List[float]]]:
        """Extract binary interaction parameters from a fluid package.

        Returns an n×n matrix where bips[i][j] is the BIP between
        component i and j. Returns None if extraction fails.
        """
        try:
            property_package = fp_com.PropertyPackage
        except Exception as exc:
            logger.warning(f"Could not access property package for BIPs: {exc}")
            return [[0.0] * n_comps for _ in range(n_comps)]

        for holder_name in ['Kij', 'Kijs', 'BinaryInteractionParameters']:
            holder = self._safe_get(property_package, holder_name, None)
            if holder is None:
                continue
            raw_values = self._safe_get(holder, 'Values', holder)
            bips = self._matrix_from_raw_values(raw_values, n_comps)
            if bips is not None:
                return bips

        bips = [[0.0] * n_comps for _ in range(n_comps)]
        found_value = False
        for row_index in range(n_comps):
            for column_index in range(row_index):
                try:
                    value = self._clean_matrix_value(
                        property_package.GetBIP(row_index, column_index))
                    if value is not None:
                        bips[row_index][column_index] = value
                        bips[column_index][row_index] = value
                        found_value = True
                except Exception:
                    pass
        return bips if found_value else [[0.0] * n_comps for _ in range(n_comps)]

    @staticmethod
    def _clean_matrix_value(value) -> Optional[float]:
        """Return a finite COM numeric value, treating UniSim sentinels as zero."""
        try:
            cleaned = float(value)
        except Exception:
            return None
        if cleaned < -30000.0:
            return 0.0
        return cleaned

    def _matrix_from_raw_values(self, raw_values, size: int) -> Optional[List[List[float]]]:
        """Convert common UniSim matrix representations to a square matrix."""
        try:
            values = list(raw_values)
        except Exception:
            return None
        if not values:
            return None
        matrix = [[0.0] * size for _ in range(size)]
        first_value = values[0]
        if isinstance(first_value, (list, tuple)):
            if len(values) < size:
                return None
            for row_index in range(size):
                row_values = list(values[row_index])
                for column_index in range(min(size, len(row_values))):
                    cleaned = self._clean_matrix_value(row_values[column_index])
                    if cleaned is not None and row_index != column_index:
                        matrix[row_index][column_index] = cleaned
            self._symmetrize_lower_triangle(matrix, size)
            return matrix
        if len(values) >= size * size:
            for row_index in range(size):
                for column_index in range(size):
                    cleaned = self._clean_matrix_value(values[row_index * size + column_index])
                    if cleaned is not None and row_index != column_index:
                        matrix[row_index][column_index] = cleaned
            self._symmetrize_lower_triangle(matrix, size)
            return matrix
        expected_lower_count = size * (size - 1) // 2
        if len(values) >= expected_lower_count:
            value_index = 0
            for row_index in range(1, size):
                for column_index in range(row_index):
                    cleaned = self._clean_matrix_value(values[value_index])
                    if cleaned is not None:
                        matrix[row_index][column_index] = cleaned
                        matrix[column_index][row_index] = cleaned
                    value_index += 1
            return matrix
        return None

    @staticmethod
    def _symmetrize_lower_triangle(matrix: List[List[float]], size: int):
        """Mirror the populated half of a square matrix to the other half."""
        for row_index in range(size):
            for column_index in range(row_index):
                value = matrix[row_index][column_index]
                if value == 0.0:
                    value = matrix[column_index][row_index]
                matrix[row_index][column_index] = value
                matrix[column_index][row_index] = value

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

    def to_json(self, include_subflowsheets: bool = True,
                full_mode: bool = True) -> Dict:
        """Convert the full model to NeqSim JSON builder format.

        For complex models with sub-flowsheets, produces a structure
        that maps to ProcessModule containing multiple ProcessSystems.

        Args:
            include_subflowsheets: include sub-flowsheet operations.
                When *full_mode* is True this is ignored.
            full_mode: when True, auto-classify sub-flowsheets and include
                only process sub-flowsheets.

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

        # Determine which sub-flowsheets to include
        if full_mode:
            process_sfs = set(self.get_process_subflowsheets())
        else:
            process_sfs = None  # include all when flag is set

        # Build process topology for main flowsheet
        result['process'] = self._build_process_section(self.model.flowsheet)

        # Sub-flowsheets as separate process systems
        if include_subflowsheets and self.model.flowsheet:
            sub_systems = {}
            for sf in self.model.flowsheet.sub_flowsheets:
                if full_mode and sf.name not in process_sfs:
                    continue
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

    def build_and_run(self, verbose: bool = True, full_mode: bool = True):
        """Build and run the NeqSim process from JSON automatically.

        This is the main automation entry point: UniSim model → JSON → NeqSim
        ProcessSystem (built, wired, run) in one call.

        When the fluid section contains an E300 file path (the default when
        exported via UniSimReader.read(export_e300=True)), the fluid is loaded
        from the E300 file using EclipseFluidReadWrite.read(). This preserves
        all critical properties (Tc, Pc, omega, MW, BIPs) for both standard
        and hypothetical/pseudo components.

        When no E300 file is available, falls back to JSON-based fluid
        definition with component name mapping.

        Args:
            verbose: If True, prints a summary of the build and run results.
            full_mode: when True, auto-classify sub-flowsheets and include
                only process sub-flowsheets.

        Returns:
            SimulationResult from ProcessSystem.fromJsonAndRun(). Use
            result.getProcessSystem() to access the running process, and
            result.isSuccess() / result.getWarnings() for diagnostics.

        Raises:
            ImportError: If neqsim package is not installed.
        """
        from neqsim import jneqsim
        ProcessSystem = jneqsim.process.processmodel.ProcessSystem

        neqsim_json = self.to_json(full_mode=full_mode)
        fluid_section = neqsim_json.get('fluid', {})
        e300_path = fluid_section.get('e300FilePath')

        if e300_path and os.path.exists(e300_path):
            # --- E300 route (preferred) ---
            # Load fluid directly from E300 file — preserves all
            # component critical properties and BIPs exactly.
            EclipseFluidReadWrite = (
                jneqsim.thermo.util.readwrite.EclipseFluidReadWrite)
            try:
                fluid = EclipseFluidReadWrite.read(e300_path)
                if fluid_section.get('temperature') is not None:
                    fluid.setTemperature(float(fluid_section['temperature']), "K")
                if fluid_section.get('pressure') is not None:
                    fluid.setPressure(float(fluid_section['pressure']), "bara")
                if verbose:
                    n_comp = fluid.getNumberOfComponents()
                    print(f"  Fluid loaded from E300: {e300_path}")
                    print(f"  Components: {n_comp}")

                # Strip e300FilePath from JSON (not needed by JSON builder)
                if 'e300FilePath' in neqsim_json.get('fluid', {}):
                    del neqsim_json['fluid']['e300FilePath']
                if 'fluidPackageName' in neqsim_json.get('fluid', {}):
                    del neqsim_json['fluid']['fluidPackageName']
                if 'componentCount' in neqsim_json.get('fluid', {}):
                    del neqsim_json['fluid']['componentCount']
                if 'componentNames' in neqsim_json.get('fluid', {}):
                    del neqsim_json['fluid']['componentNames']

                json_str = json.dumps(neqsim_json, indent=2)
                result = ProcessSystem.fromJsonAndRun(json_str, fluid)
            except Exception as e:
                logger.warning(
                    f"E300 load failed ({e}), falling back to JSON route")
                json_str = json.dumps(neqsim_json, indent=2)
                result = ProcessSystem.fromJsonAndRun(json_str)
        else:
            # --- Fallback: JSON route with component name mapping ---
            json_str = json.dumps(neqsim_json, indent=2)
            result = ProcessSystem.fromJsonAndRun(json_str)

        if verbose:
            self._print_build_summary(result)

        return result

    def run_and_compare(self, verbose: bool = True):
        """Build, run, and compare NeqSim results with UniSim reference data.

        Automates the full workflow:
        1. Converts UniSim model to NeqSim JSON
        2. Builds and runs the NeqSim ProcessSystem
        3. Compares stream temperatures, pressures, and flows with UniSim data
        4. Reports match quality for each comparison point

        Args:
            verbose: If True, prints detailed comparison results.

        Returns:
            Dict with keys:
                'result': SimulationResult from the build
                'comparison': List of dicts with stream-level comparisons
                'match_summary': Overall match statistics
                'warnings': List of converter + build warnings
        """
        # Step 1: Build and run
        result = self.build_and_run(verbose=verbose)

        # Step 2: Compare with UniSim reference data
        comparison_points = self.get_comparison_points()
        comparisons = []
        n_matched = 0
        n_total = 0

        if result.isSuccess() or not result.isError():
            process = result.getProcessSystem()
            try:
                auto = process.getAutomation()
            except Exception:
                auto = None

            for point in comparison_points:
                stream_name = point['stream']
                comp = {'stream': stream_name, 'matched': False, 'details': {}}

                if auto is not None:
                    try:
                        # Try to read temperature and pressure from NeqSim
                        neqsim_T = None
                        neqsim_P = None
                        neqsim_flow = None
                        try:
                            neqsim_T = float(auto.getVariableValue(
                                f"{stream_name}.temperature", "C"))
                        except Exception:
                            pass
                        try:
                            neqsim_P = float(auto.getVariableValue(
                                f"{stream_name}.pressure", "bara"))
                        except Exception:
                            pass
                        try:
                            neqsim_flow = float(auto.getVariableValue(
                                f"{stream_name}.flowRate", "kg/hr"))
                        except Exception:
                            pass

                        unisim_T = point.get('temperature_C')
                        unisim_P = point.get('pressure_bara')
                        unisim_flow = point.get('mass_flow_kgh')

                        if neqsim_T is not None and unisim_T is not None:
                            comp['details']['temperature_C'] = {
                                'unisim': unisim_T,
                                'neqsim': neqsim_T,
                                'delta': abs(neqsim_T - unisim_T)
                            }
                        if neqsim_P is not None and unisim_P is not None:
                            comp['details']['pressure_bara'] = {
                                'unisim': unisim_P,
                                'neqsim': neqsim_P,
                                'delta': abs(neqsim_P - unisim_P)
                            }
                        if neqsim_flow is not None and unisim_flow is not None:
                            comp['details']['mass_flow_kgh'] = {
                                'unisim': unisim_flow,
                                'neqsim': neqsim_flow,
                                'delta': abs(neqsim_flow - unisim_flow)
                            }

                        # Count as matched if any value was compared
                        if comp['details']:
                            comp['matched'] = True
                            n_matched += 1
                        n_total += 1
                    except Exception:
                        n_total += 1

                comparisons.append(comp)

        if verbose and comparisons:
            self._print_comparison_table(comparisons)

        all_warnings = list(self._warnings) + list(self._assumptions)
        if result.getWarnings():
            all_warnings.extend(str(w) for w in result.getWarnings())

        return {
            'result': result,
            'comparison': comparisons,
            'match_summary': {
                'total_streams': n_total,
                'matched_streams': n_matched,
                'match_rate': n_matched / max(n_total, 1),
            },
            'warnings': all_warnings,
        }

    def _print_build_summary(self, result) -> None:
        """Print a summary of the build/run results."""
        is_success = bool(result.isSuccess())
        is_error = bool(result.isError())
        n_warnings = len(list(result.getWarnings())) if result.getWarnings() else 0

        status = 'SUCCESS' if is_success else ('ERROR' if is_error else 'PARTIAL')
        print(f"\n{'='*60}")
        print(f"  UniSim -> NeqSim Automatic Build: {status}")
        print(f"{'='*60}")

        if is_success or not is_error:
            process = result.getProcessSystem()
            n_units = len(list(process.getUnitOperations()))
            print(f"  Equipment units: {n_units}")

        if n_warnings > 0:
            print(f"  Warnings: {n_warnings}")
            for w in result.getWarnings():
                print(f"    - {w}")

        if self._warnings:
            print(f"  Converter warnings: {len(self._warnings)}")
            for w in self._warnings:
                print(f"    - {w}")

        if self._assumptions:
            print(f"  Assumptions: {len(self._assumptions)}")
            for a in self._assumptions:
                print(f"    - {a}")

        print(f"{'='*60}\n")

    def _print_comparison_table(self, comparisons: List[Dict]) -> None:
        """Print a formatted comparison table."""
        print(f"\n{'='*80}")
        print("  Stream Comparison: UniSim vs NeqSim")
        print(f"{'='*80}")
        print(f"  {'Stream':<25} {'Property':<15} {'UniSim':>12} {'NeqSim':>12} {'Delta':>10}")
        print(f"  {'-'*74}")
        for comp in comparisons:
            if not comp.get('details'):
                continue
            first = True
            for prop_name, vals in comp['details'].items():
                label = comp['stream'] if first else ''
                unit = ''
                if 'temperature' in prop_name:
                    unit = ' C'
                elif 'pressure' in prop_name:
                    unit = ' bara'
                elif 'flow' in prop_name:
                    unit = ' kg/h'
                print(f"  {label:<25} {prop_name:<15} "
                      f"{vals['unisim']:>10.2f}{unit} "
                      f"{vals['neqsim']:>10.2f}{unit} "
                      f"{vals['delta']:>8.2f}{unit}")
                first = False
        print(f"{'='*80}\n")

    def _build_fluid_section(self) -> Dict:
        """Build the fluid section from the first fluid package.

        If the fluid package has an E300 file (exported during read()),
        the E300 path is included in the fluid section. This is the
        recommended route for NeqSim fluid import as it preserves all
        critical properties (Tc, Pc, omega, MW, BIPs) for both standard
        and hypothetical/pseudo components.
        """
        if not self.model.fluid_packages:
            self._warnings.append("No fluid packages found — using SRK defaults")
            return {'model': 'SRK', 'temperature': 298.15, 'pressure': 1.0,
                    'mixingRule': 'classic', 'components': {'methane': 1.0}}

        fp = self.model.fluid_packages[0]

        # --- E300 route (preferred) ---
        if fp.e300_file_path and os.path.exists(fp.e300_file_path):
            eos = UniSimReader.PROPERTY_PACKAGE_MAP.get(fp.property_package)
            if eos is None:
                pp_lower = fp.property_package.lower()
                for key, val in UniSimReader.PROPERTY_PACKAGE_MAP.items():
                    if key.lower() in pp_lower or pp_lower.startswith(
                            key.lower()):
                        eos = val
                        break
                if eos is None:
                    eos = 'PR'
            ref_temp_K, ref_P_bara = 298.15, 1.0
            feed_stream = self._find_feed_stream()
            if feed_stream:
                if feed_stream.temperature_C is not None:
                    ref_temp_K = feed_stream.temperature_C + 273.15
                if feed_stream.pressure_bara is not None:
                    ref_P_bara = feed_stream.pressure_bara
            return {
                'model': eos,
                'temperature': ref_temp_K,
                'pressure': ref_P_bara,
                'e300FilePath': fp.e300_file_path,
                'fluidPackageName': fp.name,
                'componentCount': len(fp.components),
                'componentNames': [fp._e300_component_name(c.name) for c in fp.components],
            }

        # --- Fallback: manual component mapping ---
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
        # Operation types that do not actually produce/consume streams
        _NON_STREAM_OPS = {'spreadsheetop', 'virtualstreamop',
                           'blowdowngenesimop'}

        for op in all_operations:
            op_type = getattr(op, 'type_name', '') or ''
            if op_type in _NON_STREAM_OPS:
                continue  # skip non-physical operations
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

        # Skip utility/control operations that do not have JSON-builder
        # equivalents. Generated Python still emits comments for some of these.
        if neqsim_type in self.SKIPPED_NEQSIM_TYPES or neqsim_type in ('PIDController', 'LogicalOp'):
            self._warnings.append(
                f"Skipped non-physical operation '{op.name}' ({neqsim_type})")
            return None

        entry = {
            'type': neqsim_type,
            'name': op.name,
        }

        # Wire inlet(s)
        reference_only_types = ('Adjuster', 'SetPoint', 'SpreadsheetBlock')
        if op.feeds and neqsim_type not in reference_only_types:
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
            split_factors = op.properties.get('split_factors')
            if split_factors:
                props['splitFactors'] = split_factors

        elif neqsim_type in ('Separator', 'GasScrubber',
                             'ThreePhaseSeparator'):
            entrainment_specs = op.properties.get('entrainment', [])
            if entrainment_specs:
                props['entrainment'] = entrainment_specs
            if op.properties.get('diameter_m'):
                props['diameter'] = op.properties['diameter_m']

        elif neqsim_type == 'Recycle':
            tol = op.properties.get('tolerance')
            if tol is not None:
                props['tolerance'] = tol

        elif neqsim_type == 'Absorber':
            # Detect glycol/TEG contactor → emit as ComponentSplitter
            _name_lower = op.name.lower()
            _is_glycol = any(kw in _name_lower for kw in
                             ('glyc', 'teg', 'dehydrat'))
            if _is_glycol:
                entry['type'] = 'ComponentSplitter'
                # Water removal: all components pass except water (last).
                # Build splitFactors from fluid package component list.
                n_comp = 0
                water_idx = -1
                if self.model.fluid_packages:
                    fp = self.model.fluid_packages[0]
                    mapped_names = []
                    for c in fp.components:
                        mn = self._map_component_name(c.name)
                        if mn:
                            mapped_names.append(mn)
                    n_comp = len(mapped_names)
                    for i, mn in enumerate(mapped_names):
                        if mn == 'water':
                            water_idx = i
                            break
                if n_comp > 0 and water_idx >= 0:
                    sf = [1.0] * n_comp
                    sf[water_idx] = 0.0
                    props['splitFactors'] = sf

        elif neqsim_type == 'Adjuster':
            # Adjuster needs: adjustedVariable (equipment+property to change),
            # targetVariable (equipment+property+value to achieve)
            adj_obj = op.properties.get('adjusted_object_name')
            adj_var = op.properties.get('adjusted_variable')
            if adj_obj and adj_var:
                props['adjustedEquipment'] = adj_obj
                props['adjustedVariable'] = adj_var
            tgt_obj = op.properties.get('target_object_name')
            tgt_var = op.properties.get('target_variable')
            tgt_val = op.properties.get('target_value')
            if tgt_obj and tgt_var:
                props['targetEquipment'] = tgt_obj
                props['targetVariable'] = tgt_var
            if tgt_val is not None:
                props['targetValue'] = tgt_val
            tol = op.properties.get('tolerance')
            if tol is not None:
                props['tolerance'] = tol
            step_size = op.properties.get('step_size')
            if step_size is not None:
                props['stepSize'] = step_size

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
                          stream_producer: Dict,
                          extra_deps: Dict[str, set] = None,
                          ) -> List[UniSimOperation]:
        """Sort operations in dependency order (feeds before consumers).

        Args:
            operations: list of operations to sort.
            stream_producer: {stream_name: (op_name, port)}.
            extra_deps: optional dict {op_name: set of op_names it depends on}.
                Used for cross-flowsheet dependencies where a templateop must
                come after main-flowsheet operations that produce streams
                consumed by the sub-flowsheet's internal operations.
        """
        # Build adjacency: op A → op B if A produces a stream that B consumes
        op_by_name = {op.name: op for op in operations}
        graph = {op.name: set() for op in operations}  # deps for each op

        for op in operations:
            for feed_stream in op.feeds:
                if feed_stream in stream_producer:
                    producer_name, _ = stream_producer[feed_stream]
                    if producer_name in op_by_name and producer_name != op.name:
                        graph[op.name].add(producer_name)

        # Inject cross-flowsheet dependencies (templateop → main ops)
        if extra_deps:
            for op_name, deps in extra_deps.items():
                if op_name in graph:
                    for dep_name in deps:
                        if dep_name in op_by_name and dep_name != op_name:
                            graph[op_name].add(dep_name)

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

    def _compute_cross_flowsheet_deps(
            self,
            flowsheet: 'UniSimFlowsheet',
            include_names: set,
            stream_producer: dict,
    ) -> Dict[str, set]:
        """Compute extra dependencies for templateops from sub-flowsheet refs.

        When a sub-flowsheet operation consumes a stream produced by a
        main-flowsheet operation, the templateop that owns that
        sub-flowsheet must come after (depend on) the producer operation.
        Without these edges the topological sort may place the templateop
        before the producer, causing undefined-variable errors at runtime.

        Uses a lightweight templateop → sub-flowsheet mapping based on:
        (1) exact name match, (2) stream overlap, (3) positional pairing.

        Returns:
            Dict[templateop_name, {set of main-op names it depends on}].
        """
        if not flowsheet or not flowsheet.sub_flowsheets:
            return {}

        main_op_names = {op.name for op in flowsheet.operations}
        templateops = [op for op in flowsheet.operations
                       if getattr(op, 'type_name', '') == 'templateop']
        if not templateops:
            return {}

        all_sfs = list(flowsheet.sub_flowsheets)
        main_stream_names = {s.name for s in flowsheet.material_streams}

        # --- Quick templateop → sub-flowsheet mapping ---
        mapping: Dict[str, 'UniSimFlowsheet'] = {}
        matched_sf_names: set = set()

        # Pass 1: exact name match
        for op in templateops:
            for sf in all_sfs:
                if sf.name == op.name and sf.name not in matched_sf_names:
                    mapping[op.name] = sf
                    matched_sf_names.add(sf.name)
                    break

        # Pass 2: stream overlap (templateop feeds/products ∩ SF streams)
        for op in templateops:
            if op.name in mapping:
                continue
            op_streams = set(op.feeds or []) | set(op.products or [])
            if not op_streams:
                continue
            for sf in all_sfs:
                if sf.name in matched_sf_names:
                    continue
                sf_snames = {s.name for s in sf.material_streams}
                if op_streams & sf_snames:
                    mapping[op.name] = sf
                    matched_sf_names.add(sf.name)
                    break

        # Pass 3: positional order for remaining (UniSim stores
        # sub-flowsheets in the same order as their parent templateops)
        unmatched_ops = [op for op in templateops
                         if op.name not in mapping]
        unmatched_sfs = [sf for sf in all_sfs
                         if sf.name not in matched_sf_names
                         and len(sf.operations) > 1]
        for op, sf in zip(unmatched_ops, unmatched_sfs):
            mapping[op.name] = sf
            matched_sf_names.add(sf.name)

        # --- Compute extra dependencies ---
        extra_deps: Dict[str, set] = {}
        for op_name, sf in mapping.items():
            if sf.name not in include_names:
                continue  # skip utility sub-flowsheets
            deps: set = set()
            for sf_op in sf.operations:
                for feed in (sf_op.feeds or []):
                    if feed in stream_producer:
                        prod_name, _ = stream_producer[feed]
                        if prod_name in main_op_names and prod_name != op_name:
                            deps.add(prod_name)
            if deps:
                extra_deps[op_name] = deps

        return extra_deps

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
    # Sub-flowsheet classification
    # -----------------------------------------------------------------

    def classify_subflowsheets(self, min_shared_streams: int = 2):
        """Classify sub-flowsheets as 'process' or 'utility'.

        A sub-flowsheet is 'process' if it shares at least
        *min_shared_streams* material streams with the main flowsheet
        or with another process sub-flowsheet.  Otherwise it is 'utility'
        (e.g. heat-medium, cooling-water, steam loops) and will be
        excluded from full-mode code generation.

        Returns:
            dict mapping sub-flowsheet name → 'process' | 'utility'
        """
        if not self.model.flowsheet:
            return {}

        main_streams = set()
        fs = self.model.flowsheet
        for s in fs.material_streams:
            main_streams.add(s.name)
        for op in fs.operations:
            for f in op.feeds:
                main_streams.add(f)
            for p in op.products:
                main_streams.add(p)

        sf_streams = {}
        for sf in fs.sub_flowsheets:
            names = set()
            for s in sf.material_streams:
                names.add(s.name)
            for op in sf.operations:
                for f in op.feeds:
                    names.add(f)
                for p in op.products:
                    names.add(p)
            sf_streams[sf.name] = names

        result = {}
        # Pass 1: mark sub-flowsheets that share enough streams with main
        for sf_name, streams in sf_streams.items():
            n_shared = len(streams & main_streams)
            if n_shared >= min_shared_streams:
                result[sf_name] = 'process'
            else:
                result[sf_name] = 'utility'

        # Pass 2: promote sub-flowsheets connected to other process subs
        changed = True
        while changed:
            changed = False
            for sf_name in list(result):
                if result[sf_name] == 'utility':
                    for other_name, other_cls in list(result.items()):
                        if other_cls == 'process' and other_name != sf_name:
                            n_shared = len(sf_streams[sf_name]
                                           & sf_streams[other_name])
                            if n_shared >= min_shared_streams:
                                result[sf_name] = 'process'
                                changed = True
                                break

        return result

    def get_process_subflowsheets(self):
        """Return list of sub-flowsheet names classified as 'process'."""
        cls = self.classify_subflowsheets()
        return [name for name, kind in cls.items() if kind == 'process']

    # -----------------------------------------------------------------
    # Shared topology analysis for code generation
    # -----------------------------------------------------------------

    def _prepare_topology(self, include_subflowsheets=True,
                          subflowsheet_filter=None):
        """Analyse the model topology and return shared data structures.

        Args:
            include_subflowsheets: If True, include sub-flowsheet operations.
                Ignored when *subflowsheet_filter* is not None.
            subflowsheet_filter: Controls which sub-flowsheets to include.
                - None  → honour *include_subflowsheets* bool (legacy)
                - 'all' → include every sub-flowsheet
                - 'process' → auto-classify and include only process subs
                - 'none'    → exclude all sub-flowsheets
                - list of str → include only the named sub-flowsheets

        Returns a dict with:
          fluid        – fluid spec dict from _build_fluid_section()
          eos_class    – NeqSim thermodynamic system class name
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
        if fluid['model'] == 'PR_LK':
            eos_class = 'SystemPrLeeKeslerEos'
        elif fluid['model'] == 'PR':
            eos_class = 'SystemPrEos'
        else:
            eos_class = 'SystemSrkEos'

        flowsheet = self.model.flowsheet
        if not flowsheet:
            return dict(fluid=fluid, eos_class=eos_class, flowsheet=None,
                        all_ops=[], all_streams=[], stream_producer={},
                        external_feeds=set(), stream_by_name={},
                        sorted_ops=[], var_names={}, used_vars=set())

        # --- determine which sub-flowsheets to include ---
        if subflowsheet_filter is not None:
            if subflowsheet_filter == 'all':
                include_names = {sf.name for sf in flowsheet.sub_flowsheets}
            elif subflowsheet_filter == 'process':
                include_names = set(self.get_process_subflowsheets())
            elif subflowsheet_filter == 'none':
                include_names = set()
            elif isinstance(subflowsheet_filter, (list, tuple, set)):
                include_names = set(subflowsheet_filter)
            else:
                include_names = set()
        elif include_subflowsheets:
            include_names = {sf.name for sf in flowsheet.sub_flowsheets}
        else:
            include_names = set()

        all_operations = list(flowsheet.operations)
        all_streams = list(flowsheet.material_streams)
        for sf in flowsheet.sub_flowsheets:
            if sf.name in include_names:
                all_streams.extend(sf.material_streams)

        # Build stream_producer from ALL ops (main + sub) so we know
        # which streams are produced and which are true external feeds.
        # Sub-flowsheet consumer/producer info is needed to avoid
        # creating spurious feed-stream declarations.
        _NON_STREAM_OPS = {'spreadsheetop', 'virtualstreamop',
                           'blowdowngenesimop'}
        stream_producer: dict = {}
        _all_ops_flat = list(flowsheet.operations)
        for sf in flowsheet.sub_flowsheets:
            _all_ops_flat.extend(sf.operations)
        for op in _all_ops_flat:
            op_type = getattr(op, 'type_name', '') or ''
            if op_type in _NON_STREAM_OPS:
                continue
            for s in op.products:
                stream_producer[s] = (op.name, 'outlet')

        external_feeds: set = set()
        for op in _all_ops_flat:
            op_type = getattr(op, 'type_name', '') or ''
            if op_type in _NON_STREAM_OPS:
                continue
            for s in op.feeds:
                if s not in stream_producer:
                    external_feeds.add(s)

        stream_by_name = {s.name: s for s in all_streams}

        # --- Compute cross-flowsheet dependencies for templateops ---
        # When a sub-flowsheet operation consumes a stream produced by a
        # main-flowsheet operation, the corresponding templateop must be
        # placed after that main-flowsheet producer in topological order.
        # Otherwise the sub-flowsheet code will reference variables not
        # yet defined.
        extra_deps = self._compute_cross_flowsheet_deps(
            flowsheet, include_names, stream_producer)

        # Topological sort only the MAIN flowsheet operations.
        # Sub-flowsheet operations are sorted separately inside
        # _build_sub_topo() when their templateop is processed.
        sorted_ops = self._topological_sort(
            all_operations, stream_producer, extra_deps)

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
            sf_mapping=None,  # populated lazily by _build_sf_mapping
            included_sf_names=include_names,  # which sub-flowsheets pass filter
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
        'BlowdownGeneSim',     # non-physical: EO utility
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
        'EclipseFluidReadWrite = jneqsim.thermo.util.readwrite.EclipseFluidReadWrite',
    )

    def _gen_fluid_lines(self, fluid: dict, eos_class: str) -> List[str]:
        """Return code lines that create the thermodynamic fluid."""
        lines = []

        # E300 route (preferred when available)
        e300_path = fluid.get('e300FilePath')
        if e300_path:
            escaped_path = e300_path.replace('\\', '/')
            lines.append(f'E300_FILE = r"{escaped_path}"')
            lines.append(f'fluid = EclipseFluidReadWrite.read(E300_FILE)')
            lines.append(f'fluid.setTemperature({fluid.get("temperature", 298.15)}, "K")')
            lines.append(f'fluid.setPressure({fluid.get("pressure", 1.0)}, "bara")')
            pkg_name = fluid.get('fluidPackageName', '')
            n_comp = fluid.get('componentCount', '?')
            lines.append(f'# Loaded from E300: {pkg_name} ({n_comp} components)')
            return lines

        # Fallback: manual fluid creation
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
                adj_obj_var = var_names.get(adj_obj)
                if adj_obj_var is None:
                    lines.append(
                        f'# TODO: wire adjusted object "{adj_obj}" — '
                        f'not yet resolved as a Python variable')
                else:
                    lines.append(
                        f'{v}.setAdjustedVariable({adj_obj_var}, "{adj_var}")')
            elif adj_obj:
                adj_obj_var = var_names.get(adj_obj)
                if adj_obj_var is None:
                    lines.append(
                        f'# TODO: wire adjusted object "{adj_obj}" — '
                        f'not yet resolved as a Python variable')
                else:
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
                tgt_obj_var = var_names.get(tgt_obj)
                if tgt_obj_var is None:
                    lines.append(
                        f'# TODO: wire target object "{tgt_obj}" — '
                        f'not yet resolved as a Python variable')
                else:
                    lines.append(
                        f'{v}.setTargetVariable({tgt_obj_var}, "{tgt_var}", '
                        f'{tgt_val}, "")')
            elif tgt_obj and tgt_var:
                tgt_obj_var = var_names.get(tgt_obj)
                if tgt_obj_var is None:
                    lines.append(
                        f'# TODO: wire target object "{tgt_obj}" — '
                        f'not yet resolved as a Python variable')
                else:
                    lines.append(
                        f'{v}.setTargetVariable({tgt_obj_var}, "{tgt_var}")')
            elif tgt_obj:
                tgt_obj_var = var_names.get(tgt_obj)
                if tgt_obj_var is None:
                    lines.append(
                        f'# TODO: wire target object "{tgt_obj}" — '
                        f'not yet resolved as a Python variable')
                else:
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
                src_obj_var = var_names.get(src_obj)
                if src_obj_var is None:
                    # Source references a stream name not in var_names —
                    # comment out to avoid NameError at runtime
                    src_obj_py = self._to_pyvar(src_obj)
                    lines.append(
                        f'# TODO: wire source stream "{src_obj}" — '
                        f'not yet resolved as a Python variable')
                elif src_var:
                    lines.append(
                        f'{v}.setSourceVariable({src_obj_var}, "{src_var}")')
                else:
                    lines.append(f'{v}.setSourceVariable({src_obj_var})')
            else:
                lines.append(
                    f'# TODO: set source — '
                    f'e.g. {v}.setSourceVariable(sourceEquipment)')
            if tgt_obj:
                tgt_obj_var = var_names.get(tgt_obj)
                if tgt_obj_var is None:
                    lines.append(
                        f'# TODO: wire target "{tgt_obj}" — '
                        f'not yet resolved as a Python variable')
                elif tgt_var:
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
                inlet_ref = _ref(inlet_refs[0])
                lines.append(f'{v}.addStream({inlet_ref})')
                # Initialize outlet stream so downstream getOutletStream()
                # is not null before process.run()
                lines.append(
                    f'{v}.setOutletStream('
                    f'{inlet_ref}.clone("{op.name} outlet"))')
            # Set very large tolerance so recycle "converges" in 2 iterations
            # (essentially a single-pass with UniSim-initialised tear streams)
            lines.append(f'{v}.setTolerance(1e6)')
            # Wire outlet to the forward-reference placeholder if one exists
            fwd_ref_vars_map = topo.get('fwd_ref_vars', {})
            if op.name in fwd_ref_vars_map:
                pv = fwd_ref_vars_map[op.name]
                lines.append(f'{v}.setOutletStream({pv})')

        elif neqsim_type == 'StreamSaturatorUtil':
            ref_expr = _ref(inlet_refs[0]) if inlet_refs else 'None'
            lines.append(
                f'{v} = StreamSaturatorUtil("{op.name}", {ref_expr})')

        elif neqsim_type == 'SpreadsheetBlock':
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

            # Check whether this sub-flowsheet is included (process) or
            # excluded (utility).  Excluded SFs get an empty ProcessSystem.
            included_sf = topo.get('included_sf_names')
            sf_included = (included_sf is None
                           or (sf_data and sf_data.name in included_sf))

            if sf_data and sf_data.operations and sf_included:
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
                if sf_data and not sf_included:
                    lines.append(
                        f'# Utility sub-flowsheet "{op.name}" excluded '
                        f'from process model (heat/cooling/steam medium)')
                else:
                    lines.append(
                        f'# Sub-flowsheet "{op.name}" has no extractable '
                        f'operations — add equipment manually')

        elif neqsim_type == 'Adjuster' and op.type_name == 'balanceop':
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
                    lines.append(f'{rcy_var}.setTolerance(1e6)')
                    lines.append(f'process.add({rcy_var})')

        return lines

    def _build_sf_mapping(self, topo: dict) -> Dict[str, 'UniSimFlowsheet']:
        """Build a mapping from templateop operation names to sub-flowsheets.

        Maps ALL sub-flowsheets (including utility) to establish correct
        pairings, then marks which are included for code generation via
        ``included_sf_names`` in the topo dict.

        Uses three matching strategies in priority order:

        1. Direct name match (templateop name == sub-flowsheet name)
        2. Stream overlap (templateop feeds/products appear in sub-flowsheet)
        3. Positional order for remaining unmatched pairs (UniSim stores
           sub-flowsheets in the same order as their parent templateops)

        Returns:
            Dict mapping templateop operation name → UniSimFlowsheet object.
        """
        if not topo.get('flowsheet') or not topo['flowsheet'].sub_flowsheets:
            return {}

        # Map against ALL sub-flowsheets so utility templateops correctly
        # consume utility SFs and don't steal process SFs.
        all_sfs = list(topo['flowsheet'].sub_flowsheets)

        # templateop operations in ORIGINAL flowsheet order (not sorted
        # order) so that positional matching aligns with the native
        # UniSim sub-flowsheet order.
        templateops = [op for op in topo['flowsheet'].operations
                       if op.type_name == 'templateop']

        mapping: Dict[str, 'UniSimFlowsheet'] = {}
        matched_sf_names: set = set()

        # --- Pass 1: Direct name match ---
        for op in templateops:
            for sf in all_sfs:
                if sf.name == op.name and sf.name not in matched_sf_names:
                    mapping[op.name] = sf
                    matched_sf_names.add(sf.name)
                    break

        # --- Pass 2: Stream overlap (templateop feeds/products ∩ SF) ---
        for op in templateops:
            if op.name in mapping:
                continue
            feeds_set = set(op.feeds) if op.feeds else set()
            prods_set = set(op.products) if op.products else set()
            all_tp_streams = feeds_set | prods_set
            if not all_tp_streams:
                continue
            for sf in all_sfs:
                if sf.name in matched_sf_names:
                    continue
                sf_snames = {s.name for s in sf.material_streams}
                if all_tp_streams & sf_snames:
                    mapping[op.name] = sf
                    matched_sf_names.add(sf.name)
                    break

        # --- Pass 3: Positional order for remaining ---
        # UniSim stores sub-flowsheets in the same order as their
        # parent templateops.  After Passes 1-2 remove matched items,
        # the remaining unmatched templateops and SFs can be paired
        # by their preserved relative order (skipping tiny/column SFs).
        unmatched_ops = [op for op in templateops
                         if op.name not in mapping]
        unmatched_sfs_ = [sf for sf in all_sfs
                          if sf.name not in matched_sf_names]

        # Exclude tiny column-internals sub-flowsheets
        candidate_sfs = [
            sf for sf in unmatched_sfs_
            if len(sf.operations) > 1 or not any(
                o.type_name == 'traysection' for o in sf.operations)
        ]
        for op, sf in zip(unmatched_ops, candidate_sfs):
            mapping[op.name] = sf
            matched_sf_names.add(sf.name)

        return mapping

    def _find_sub_flowsheet_for_op(self, op: 'UniSimOperation',
                                    topo: dict) -> 'Optional[UniSimFlowsheet]':
        """Find the sub-flowsheet belonging to a template/sub-flowsheet op.

        Uses a pre-built mapping from ``_build_sf_mapping()`` if available
        in ``topo['sf_mapping']``.  Falls back to stream-overlap heuristics,
        but only considers sub-flowsheets in ``included_sf_names``.
        """
        # Use cached mapping if available
        sf_map = topo.get('sf_mapping')
        if sf_map is not None:
            # When a mapping exists, ONLY return mapped sub-flowsheets.
            # If the templateop isn't in the map it was intentionally
            # excluded (e.g. utility sub-flowsheet).
            return sf_map.get(op.name)

        if not topo.get('flowsheet') or not topo['flowsheet'].sub_flowsheets:
            return None

        # Respect the include filter when falling back
        included = topo.get('included_sf_names')
        candidates = topo['flowsheet'].sub_flowsheets
        if included is not None:
            candidates = [sf for sf in candidates if sf.name in included]

        for sf in candidates:
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

        # When using E300 route, get component count from fluid package
        n_components = len(comp_list)
        if fluid.get('e300FilePath') and self.model.fluid_packages:
            fp = self.model.fluid_packages[0]
            n_components = fluid.get('componentCount', len(fp.components))
            if fp.components and not comp_list:
                # Derive dominant from fluid package component names / feed composition
                feed_stream = self._find_feed_stream()
                if (feed_stream and feed_stream.composition):
                    max_comp = max(feed_stream.composition,
                                   key=feed_stream.composition.get)
                    dominant = max_comp
                elif fp.components:
                    dominant = fp.components[0].name
                # Build comp_list from fluid package for classification
                comp_list = [c.name for c in fp.components]
        has_water = any(c.lower() in ('water', 'h2o') for c in comp_list)
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
            f'The fluid is a **{fluid_type}** mixture with {n_components} '
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

        elif neqsim_type == 'SpreadsheetBlock':
            return (f'**{op.name}** performs user-defined calculations via '
                    f'imported stream variables and formula cells.')

        elif neqsim_type == 'SubFlowsheet':
            return (f'**{op.name}** encapsulates a nested sub-process that '
                    f'runs as an independent module within the main flowsheet.')

        elif neqsim_type == 'Adjuster' and op.type_name == 'balanceop':
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
            'StreamSaturatorUtil': 'utility', 'SpreadsheetBlock': 'utility',
            'SubFlowsheet': 'utility',
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

    def to_python(self, include_subflowsheets: bool = True,
                  full_mode: bool = True) -> str:
        """Generate a self-contained Python script that builds the NeqSim process.

        The generated code uses the ``jneqsim`` gateway and creates every
        stream, equipment unit, and connection as explicit Python statements
        so the process is fully transparent, readable, and editable.

        Args:
            include_subflowsheets: whether to include sub-flowsheet operations.
                When *full_mode* is True this is ignored.
            full_mode: when True (recommended), auto-classify sub-flowsheets
                and include only process sub-flowsheets (excluding utility
                loops like heat medium, cooling water, steam).

        Returns:
            A string containing a complete, runnable Python script.
        """
        sf_filter = 'process' if full_mode else None
        topo = self._prepare_topology(include_subflowsheets,
                                       subflowsheet_filter=sf_filter)
        # Build sub-flowsheet mapping for templateop → sub-flowsheet
        topo['sf_mapping'] = self._build_sf_mapping(topo)
        fluid = topo['fluid']
        eos_class = topo['eos_class']
        flowsheet = topo['flowsheet']

        lines: list = []
        _a = lines.append

        # --- header / imports ---
        _a('# -*- coding: utf-8 -*-')
        _a('"""')
        _a(f'NeqSim process model -- generated from UniSim file')
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

    def to_notebook(self, include_subflowsheets: bool = True,
                    full_mode: bool = True) -> Dict:
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
                When *full_mode* is True this is ignored.
            full_mode: when True, auto-classify sub-flowsheets and include
                only process sub-flowsheets.

        Returns:
            A dict in Jupyter nbformat v4 structure.
        """
        sf_filter = 'process' if full_mode else None
        topo = self._prepare_topology(include_subflowsheets,
                                       subflowsheet_filter=sf_filter)
        # Build sub-flowsheet mapping for templateop → sub-flowsheet
        topo['sf_mapping'] = self._build_sf_mapping(topo)
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
        e300_path = fluid.get('e300FilePath')
        if e300_path:
            n_comp = fluid.get('componentCount', '?')
            pkg_name = fluid.get('fluidPackageName', pp_name)
            _md(f'## Fluid Definition\n\n'
                f'Equation of state: **{fluid["model"]}** '
                f'(mapped from UniSim *{pp_name}*)\n\n'
                f'Fluid loaded from E300 file: `{e300_path}` '
                f'({n_comp} components with Tc, Pc, ω, MW, BIPs)\n\n'
                f'Fluid package: **{pkg_name}**')
        else:
            comp_table = ('| Component | Mole fraction |\n'
                          '|-----------|---------------|\n')
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
        if fluid.get('e300FilePath'):
            escaped_path = fluid['e300FilePath'].replace('\\', '\\\\')
            _a('    EclipseFluidReadWrite = '
               'neqsim.thermo.util.readwrite.EclipseFluidReadWrite')
            _a(f'    fluid = EclipseFluidReadWrite.read(r"{escaped_path}")')
            _a(f'    fluid.setTemperature({fluid.get("temperature", 298.15)}, "K")')
            _a(f'    fluid.setPressure({fluid.get("pressure", 1.0)}, "bara")')
        else:
            model = fluid.get('model')
            if model == 'PR_LK':
                eos_class_name = 'SystemPrLeeKeslerEos'
            elif model == 'PR':
                eos_class_name = 'SystemPrEos'
            else:
                eos_class_name = 'SystemSrkEos'
            eos_neqsim = f'neqsim.thermo.system.{eos_class_name}'
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
        component_count = fluid.get('componentCount', len(fluid.get('components', {})))

        _md(f'# EOT Simulator: {self.model.file_name}\n\n'
            f'Auto-generated ProcessPilot / EOT simulator from UniSim model.\n\n'
            f'| Property | Value |\n|----------|-------|\n'
            f'| UniSim Property Package | {pp_name} |\n'
            f'| NeqSim EOS | {fluid["model"]} |\n'
            f'| Components | {component_count} |\n')

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
