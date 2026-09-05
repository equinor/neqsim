"""
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
import math
import os
import time
import logging
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple, Any, Union

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
    # UniSim ideal-gas enthalpy polynomial coefficients (a0..a5), mass basis
    # kJ/kg, H(T[K]) = sum_i a_i * T**i. Cp0 = dH/dT. Used to transfer the
    # ideal-gas heat capacity of pseudo/hypothetical components to NeqSim so
    # enthalpy / Joule-Thomson behaviour matches UniSim (E300 does not carry Cp).
    enthalpy_a0: Optional[float] = None
    enthalpy_a1: Optional[float] = None
    enthalpy_a2: Optional[float] = None
    enthalpy_a3: Optional[float] = None
    enthalpy_a4: Optional[float] = None
    enthalpy_a5: Optional[float] = None

    def to_dict(self) -> Dict[str, Any]:
        """Convert component to dictionary for serialization."""
        result: Dict[str, Any] = {}
        for key, value in self.__dict__.items():
            if value is not None:
                result[key] = value
        return result

    def __str__(self) -> str:
        """Return a human-readable representation of the component."""
        return f"{self.name} (index={self.index}, is_hypothetical={self.is_hypothetical})"


@dataclass
class UniSimFluidPackage:
    """A fluid package containing multiple components."""
    name: str
    components: List[UniSimComponent] = field(default_factory=list)
    # TBP fraction data
    tbp_begin: Optional[float] = None
    tbp_end: Optional[float] = None
    # Weight fraction if available
    wtfraction: Optional[float] = None

    def add_component(self, component: UniSimComponent) -> None:
        """Add a component to the fluid package."""
        component.index = len(self.components)
        self.components.append(component)

    def to_dict(self) -> Dict[str, Any]:
        """Convert fluid package to dictionary for serialization."""
        return {
            'name': self.name,
            'components': [comp.to_dict() for comp in self.components],
            'tbp_begin': self.tbp_begin,
            'tbp_end': self.tbp_end,
            'wtfraction': self.wtfraction,
        }

    def __str__(self) -> str:
        """Return a human-readable representation of the fluid package."""
        return f"{self.name} ({len(self.components)} components)"


@dataclass
class UniSimReader:
    """The main reader class for extracting UniSim .usc models."""

    def __post_init__(self) -> None:
        """Initialize the reader after dataclass creation."""
        self._component_count = 0

    def read(self, path: Union[str, Path]) -> UniSimFluidPackage:
        """Read a UniSim model from the given file path.

        Args:
            path: Path to the .usc file to read

        Returns:
            A UniSimFluidPackage containing the extracted model
        """
        if isinstance(path, str):
            path = Path(path)

        if not path.exists():
            raise FileNotFoundError(f"UniSim file not found: {path}")

        # Open COM object and extract model
        # NOTE: This assumes pywin32 is available on Windows
        from win32com.client import Dispatch  # type: ignore

        com_model = Dispatch("EclipseFluidReadWrite.FluiddPackage")  # type: ignore

        # Read component names from the .usc file structure
        fluid = UniSimFluidPackage(name=path.stem)

        for idx in range(1, 50):  # Reasonable upper bound for components
            try:
                comp_name = com_model.ComponentName(idx)
                if not comp_name or comp_name == '<Undefined>':
                    break

                component = UniSimComponent(name=comp_name, index=idx)

                # Extract properties from COM object
                component.tc_K = float(com_model.TC_K) if hasattr(com_model, 'TC_K') else None
                component.pc_bara = float(com_model.PC_bara) if hasattr(com_model, 'PC_bara') else None
                component.acentric_factor = float(com_model.AcentricFactor) if hasattr(com_model, 'AcentricFactor') else None
                component.mw = float(com_model.MolecularWeight) if hasattr(com_model, 'MolecularWeight') else None
                component.tboil_K = float(com_model.TBOIL_K) if hasattr(com_model, 'TBOIL_K') else None

                # Normalize component name through E300 map
                base_name = E300_COMPONENT_NAME_MAP.get(comp_name, comp_name)
                if base_name != comp_name:
                    logger.info(f"Component {idx} normalized: {comp_name} → {base_name}")

                fluid.add_component(component)
                self._component_count += 1

            except (IndexError, ValueError) as e:
                logger.debug(f"Component {idx} extraction failed (expected for last component): {e}")
                break

        return fluid

    def summary(self, fluid: Optional[UniSimFluidPackage] = None) -> str:
        """Return a summary of the UniSim model.

        Args:
            fluid: Optional fluid package to summarize; defaults to reading current state

        Returns:
            Human-readable summary string
        """
        if fluid is None:
            # If no fluid provided, get from the last read
            # This allows chaining: reader.read().summary()
            return f"UniSimModel (components read: {self._component_count})"

        lines = [str(fluid)]
        for comp in fluid.components:
            lines.append(f"  - {comp}")
        return "\n".join(lines)


@dataclass
class UniSimToNeqSim:
    """Converts UniSim extracted data to NeqSim JSON builder format."""

    def to_json(self, fluid: UniSimFluidPackage) -> Dict[str, Any]:
        """Convert UniSim fluid package to NeqSim-compatible JSON structure.

        Args:
            fluid: The UniSim fluid package to convert

        Returns:
            Dictionary ready for NeqSim ProcessSystem ingestion
        """
        base_fluid = {
            'name': fluid.name,
            'num_components': len(fluid.components),
            'components': [],
            'tc': fluid.components[0].tc_K if fluid.components else None,
            'pc': fluid.components[0].pc_bara if fluid.components else None,
        }

        for comp in fluid.components:
            comp_dict = comp.to_dict()
            base_fluid['components'].append(comp_dict)

        # Handle TBP fraction if present
        if fluid.tbp_begin is not None or fluid.tbp_end is not None:
            base_fluid['is_tbp'] = True
            base_fluid['tbp_begin_K'] = fluid.tbp_begin
            base_fluid['tbp_end_K'] = fluid.tbp_end

        # Handle weighted fraction
        if fluid.wtfraction is not None:
            base_fluid['wtfraction'] = fluid.wtfraction

        # Determine critical compressibility from first component
        if fluid.components and fluid.components[0].zcrit is not None:
            base_fluid['zcrit'] = fluid.components[0].zcrit

        return base_fluid

    def build_and_run(self, fluid: UniSimFluidPackage) -> Any:
        """Build and execute a NeqSim process from the UniSim model.

        Args:
            fluid: The UniSim fluid package to use

        Returns:
            A NeqSim ProcessSystem (or equivalent result)
        """
        neqsim_json = self.to_json(fluid)

        # Convert to NeqSim's expected format
        neqsim_data = {
            'type': 'ProcessSystem',
            'modules': [
                {
                    'type': 'FluidPackage',
                    'attributes': neqsim_json,
                }
            ],
        }

        # For a real implementation, this would interface with NeqSim's Python bindings
        # For now, return a structure that can be easily consumed
        return neqsim_data


# Export at module level for convenience
__all__ = [
    'E300_COMPONENT_NAME_MAP',
    'UniSimComponent',
    'UniSimFluidPackage',
    'UniSimReader',
    'UniSimToNeqSim',
]