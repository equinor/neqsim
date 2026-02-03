---
title: Python Extension Patterns for NeqSim
description: Guide to extending NeqSim from Python using JPype, implementing Java interfaces, and creating custom models.
---

# Python Extension Patterns for NeqSim

This guide covers how to extend NeqSim functionality from Python, including implementing Java interfaces, creating custom calculation wrappers, and integrating with Python scientific libraries.

## Table of Contents

1. [Overview](#overview)
2. [Setting Up JPype with NeqSim](#setting-up-jpype-with-neqsim)
3. [Calling Java from Python](#calling-java-from-python)
4. [Implementing Java Interfaces in Python](#implementing-java-interfaces-in-python)
5. [Custom Process Equipment Wrappers](#custom-process-equipment-wrappers)
6. [Custom Thermodynamic Calculations](#custom-thermodynamic-calculations)
7. [Batch Processing and Optimization](#batch-processing-and-optimization)
8. [Integration with Scientific Python](#integration-with-scientific-python)
9. [Performance Considerations](#performance-considerations)
10. [Best Practices](#best-practices)

---

## Overview

NeqSim Python integration works through three approaches:

| Approach | Use Case | Complexity |
|----------|----------|------------|
| Direct Java Access | Use existing NeqSim classes | Low |
| Python Wrappers | Simplify API, add features | Medium |
| Interface Implementation | Custom calculations in Python | High |

### When to Use Each Approach

- **Direct Java Access**: Standard simulations using existing models
- **Python Wrappers**: When you need Pythonic interfaces or want to combine with NumPy/pandas
- **Interface Implementation**: When you need custom behavior that integrates into NeqSim's calculation flow

---

## Setting Up JPype with NeqSim

### Basic Import Pattern (Recommended)

```python
# Standard neqsim-python import
from neqsim import jneqsim

# This auto-starts the JVM and provides access to all NeqSim classes
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ThermodynamicOperations = jneqsim.thermodynamicoperations.ThermodynamicOperations
Stream = jneqsim.process.equipment.stream.Stream
```

### Direct JPype Access (Advanced)

```python
import jpype
import jpype.imports
from jpype.types import JDouble, JArray, JString

# Only needed if not using neqsim package
if not jpype.isJVMStarted():
    # Path to neqsim JAR
    jar_path = "/path/to/neqsim.jar"
    jpype.startJVM(classpath=[jar_path])

# Import Java packages
from neqsim.thermo.system import SystemSrkEos
from neqsim.process.equipment.stream import Stream
```

### Type Conversion Reference

| Python Type | Java Type | Notes |
|-------------|-----------|-------|
| `float` | `double` | Automatic |
| `int` | `int`/`long` | Automatic |
| `str` | `String` | Automatic |
| `bool` | `boolean` | Automatic |
| `list` | `ArrayList` or array | Use `JArray` for typed arrays |
| `dict` | `HashMap` | Manual conversion needed |
| `numpy.ndarray` | `double[][]` | Use `JArray(JDouble, 2)(arr)` |

---

## Calling Java from Python

### Basic Fluid Creation

```python
from neqsim import jneqsim

# Create thermodynamic system (Temperature in Kelvin, Pressure in bara)
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
fluid = SystemSrkEos(273.15 + 25.0, 60.0)

# Add components
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)

# REQUIRED: Set mixing rule
fluid.setMixingRule("classic")

# Run flash calculation
ThermodynamicOperations = jneqsim.thermodynamicoperations.ThermodynamicOperations
ops = ThermodynamicOperations(fluid)
ops.TPflash()

# Access results
print(f"Density: {fluid.getDensity('kg/m3'):.2f} kg/m³")
print(f"Z-factor: {fluid.getPhase('gas').getZ():.4f}")
```

### Working with Process Equipment

```python
from neqsim import jneqsim

# Import classes
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
Separator = jneqsim.process.equipment.separator.Separator
Compressor = jneqsim.process.equipment.compressor.Compressor

# Create fluid
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
feed_fluid = SystemSrkEos(273.15 + 30.0, 60.0)
feed_fluid.addComponent("methane", 0.85)
feed_fluid.addComponent("n-pentane", 0.15)
feed_fluid.setMixingRule("classic")

# Build process
process = ProcessSystem("Gas Processing")

feed = Stream("Feed", feed_fluid)
feed.setFlowRate(50000.0, "kg/hr")
process.add(feed)

separator = Separator("HP Sep", feed)
process.add(separator)

# Get outlet and compress
gas = separator.getGasOutStream()
compressor = Compressor("Comp", gas)
compressor.setOutletPressure(100.0)
process.add(compressor)

# Run simulation
process.run()

# Results
print(f"Compressor power: {compressor.getPower('kW'):.1f} kW")
print(f"Outlet T: {compressor.getOutletStream().getTemperature() - 273.15:.1f}°C")
```

### Handling Java Arrays

```python
import jpype
from jpype.types import JDouble, JArray

# Create Java double array
java_array = JArray(JDouble)([1.0, 2.0, 3.0, 4.0, 5.0])

# Convert NumPy array to Java
import numpy as np
numpy_array = np.array([1.0, 2.0, 3.0])
java_from_numpy = JArray(JDouble)(numpy_array.tolist())

# 2D arrays
numpy_2d = np.array([[1.0, 2.0], [3.0, 4.0]])
java_2d = JArray(JDouble, 2)(numpy_2d.tolist())

# Convert Java array back to Python
python_list = list(java_array)
numpy_back = np.array(list(java_array))
```

---

## Implementing Java Interfaces in Python

### Using @JImplements Decorator

JPype allows you to implement Java interfaces directly in Python:

```python
import jpype
from jpype import JImplements, JOverride
from neqsim import jneqsim

@JImplements('neqsim.thermo.component.ComponentInterface')
class CustomComponent:
    """Custom component implementation in Python."""
    
    def __init__(self, name, moles):
        self.name = name
        self.moles = moles
        self.mole_fraction = 0.0
        self.fugacity_coeff = 1.0
    
    @JOverride
    def getName(self):
        return self.name
    
    @JOverride
    def getNumberOfMolesInPhase(self):
        return self.moles
    
    @JOverride
    def getx(self):
        """Get mole fraction."""
        return self.mole_fraction
    
    @JOverride
    def setx(self, x):
        """Set mole fraction."""
        self.mole_fraction = float(x)
    
    @JOverride
    def getFugacityCoefficient(self):
        return self.fugacity_coeff
    
    @JOverride
    def getTC(self):
        """Critical temperature in K."""
        # Return from database or calculate
        return 190.56  # Example: methane
    
    @JOverride
    def getPC(self):
        """Critical pressure in bar."""
        return 45.99  # Example: methane
```

### Implementing Custom Calculation Interface

```python
@JImplements('neqsim.util.CalculationInterface')
class CustomCalculation:
    """Custom calculation that can be called from NeqSim."""
    
    def __init__(self, param1=1.0, param2=2.0):
        self.param1 = param1
        self.param2 = param2
        self.result = None
    
    @JOverride
    def run(self):
        """Execute the calculation."""
        # Your custom logic here
        self.result = self.param1 * self.param2
        return True
    
    @JOverride
    def getResult(self):
        return self.result
```

### Implementing Property Models

```python
@JImplements('neqsim.physicalproperties.ViscosityInterface')
class CustomViscosityModel:
    """Custom viscosity model in Python."""
    
    def __init__(self):
        self.viscosity = 0.0
        self._phase = None
    
    @JOverride
    def setPhase(self, phase):
        self._phase = phase
    
    @JOverride
    def calcViscosity(self):
        """Calculate viscosity using custom correlation."""
        if self._phase is None:
            return 0.0
        
        T = self._phase.getTemperature()  # K
        P = self._phase.getPressure()     # bar
        rho = self._phase.getDensity()    # mol/m³
        
        # Custom correlation (example: simple gas viscosity)
        # mu = A * T^0.5 / (1 + B/T)
        A = 2.6693e-6
        B = 127.0
        
        self.viscosity = A * (T ** 0.5) / (1.0 + B / T)
        return self.viscosity
    
    @JOverride
    def getViscosity(self):
        return self.viscosity
```

---

## Custom Process Equipment Wrappers

### Creating Pythonic Wrappers

```python
from neqsim import jneqsim
from dataclasses import dataclass
from typing import Optional, Dict, Any
import json

@dataclass
class SeparatorResult:
    """Results from separator calculation."""
    gas_flow_rate: float  # kg/hr
    liquid_flow_rate: float  # kg/hr
    gas_density: float  # kg/m³
    liquid_density: float  # kg/m³
    temperature: float  # °C
    pressure: float  # bara

class PythonSeparator:
    """Pythonic wrapper for NeqSim Separator."""
    
    def __init__(self, name: str, inlet_stream=None):
        """
        Create separator.
        
        Args:
            name: Equipment name
            inlet_stream: NeqSim Stream object or dict with fluid spec
        """
        self.name = name
        self._java_sep = None
        self._inlet = inlet_stream
        
        Separator = jneqsim.process.equipment.separator.Separator
        
        if inlet_stream is not None:
            if isinstance(inlet_stream, dict):
                # Create stream from dict specification
                self._inlet = self._create_stream_from_dict(inlet_stream)
            self._java_sep = Separator(name, self._inlet)
    
    def _create_stream_from_dict(self, spec: Dict[str, Any]):
        """Create stream from dictionary specification."""
        SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
        Stream = jneqsim.process.equipment.stream.Stream
        
        T = spec.get('temperature_C', 25.0) + 273.15
        P = spec.get('pressure_bara', 1.0)
        
        fluid = SystemSrkEos(T, P)
        for comp, frac in spec.get('composition', {}).items():
            fluid.addComponent(comp, frac)
        fluid.setMixingRule(spec.get('mixing_rule', 'classic'))
        
        stream = Stream(f"{self.name}_feed", fluid)
        stream.setFlowRate(spec.get('flow_rate', 1000.0), 
                         spec.get('flow_unit', 'kg/hr'))
        
        return stream
    
    def run(self) -> SeparatorResult:
        """Run separator and return results."""
        if self._java_sep is None:
            raise ValueError("No inlet stream connected")
        
        self._java_sep.run()
        
        gas = self._java_sep.getGasOutStream()
        liquid = self._java_sep.getLiquidOutStream()
        
        return SeparatorResult(
            gas_flow_rate=gas.getFlowRate("kg/hr"),
            liquid_flow_rate=liquid.getFlowRate("kg/hr"),
            gas_density=gas.getFluid().getDensity("kg/m3"),
            liquid_density=liquid.getFluid().getDensity("kg/m3"),
            temperature=self._java_sep.getTemperature() - 273.15,
            pressure=self._java_sep.getPressure()
        )
    
    def get_gas_stream(self):
        """Get gas outlet stream."""
        return self._java_sep.getGasOutStream()
    
    def get_liquid_stream(self):
        """Get liquid outlet stream."""
        return self._java_sep.getLiquidOutStream()
    
    def to_json(self) -> str:
        """Export results to JSON."""
        result = self.run()
        return json.dumps({
            'name': self.name,
            'gas_flow_rate_kg_hr': result.gas_flow_rate,
            'liquid_flow_rate_kg_hr': result.liquid_flow_rate,
            'gas_density_kg_m3': result.gas_density,
            'liquid_density_kg_m3': result.liquid_density,
            'temperature_C': result.temperature,
            'pressure_bara': result.pressure
        }, indent=2)

# Usage
sep = PythonSeparator("HP-SEP", {
    'temperature_C': 30.0,
    'pressure_bara': 60.0,
    'composition': {'methane': 0.85, 'n-pentane': 0.15},
    'flow_rate': 50000.0,
    'flow_unit': 'kg/hr'
})

result = sep.run()
print(f"Gas flow: {result.gas_flow_rate:.1f} kg/hr")
print(f"Liquid flow: {result.liquid_flow_rate:.1f} kg/hr")
```

### Process Builder Pattern

```python
class ProcessBuilder:
    """Fluent API for building NeqSim processes."""
    
    def __init__(self, name: str):
        self.name = name
        self._process = jneqsim.process.processmodel.ProcessSystem(name)
        self._current_stream = None
        self._equipment = {}
    
    def feed(self, name: str, fluid_spec: dict, flow_rate: float, 
             flow_unit: str = "kg/hr"):
        """Add feed stream."""
        SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
        Stream = jneqsim.process.equipment.stream.Stream
        
        T = fluid_spec.get('T_C', 25.0) + 273.15
        P = fluid_spec.get('P_bara', 1.0)
        
        fluid = SystemSrkEos(T, P)
        for comp, frac in fluid_spec.get('composition', {}).items():
            fluid.addComponent(comp, frac)
        fluid.setMixingRule('classic')
        
        stream = Stream(name, fluid)
        stream.setFlowRate(flow_rate, flow_unit)
        
        self._process.add(stream)
        self._current_stream = stream
        self._equipment[name] = stream
        
        return self
    
    def separator(self, name: str):
        """Add separator using current stream."""
        Separator = jneqsim.process.equipment.separator.Separator
        sep = Separator(name, self._current_stream)
        self._process.add(sep)
        self._current_stream = sep.getGasOutStream()
        self._equipment[name] = sep
        return self
    
    def compressor(self, name: str, outlet_pressure: float):
        """Add compressor."""
        Compressor = jneqsim.process.equipment.compressor.Compressor
        comp = Compressor(name, self._current_stream)
        comp.setOutletPressure(outlet_pressure)
        self._process.add(comp)
        self._current_stream = comp.getOutletStream()
        self._equipment[name] = comp
        return self
    
    def cooler(self, name: str, outlet_temp_C: float):
        """Add cooler."""
        Cooler = jneqsim.process.equipment.heatexchanger.Cooler
        cooler = Cooler(name, self._current_stream)
        cooler.setOutTemperature(outlet_temp_C + 273.15)
        self._process.add(cooler)
        self._current_stream = cooler.getOutletStream()
        self._equipment[name] = cooler
        return self
    
    def use_liquid_from(self, separator_name: str):
        """Switch to liquid outlet of a separator."""
        sep = self._equipment.get(separator_name)
        if sep:
            self._current_stream = sep.getLiquidOutStream()
        return self
    
    def build(self):
        """Build and return the process."""
        return self._process
    
    def run(self):
        """Build and run the process."""
        self._process.run()
        return self

# Usage with fluent API
process = (ProcessBuilder("Gas Plant")
    .feed("Feed", {
        'T_C': 30, 'P_bara': 60,
        'composition': {'methane': 0.8, 'ethane': 0.1, 'propane': 0.1}
    }, flow_rate=100000)
    .separator("HP-Sep")
    .compressor("Comp-1", outlet_pressure=100.0)
    .cooler("After-Cooler", outlet_temp_C=40.0)
    .run()
)

# Access results
comp = process._equipment["Comp-1"]
print(f"Compressor power: {comp.getPower('MW'):.2f} MW")
```

---

## Custom Thermodynamic Calculations

### Property Calculator

```python
import numpy as np
from typing import List, Dict, Tuple

class PropertyCalculator:
    """Calculate thermodynamic properties over ranges."""
    
    def __init__(self, composition: Dict[str, float], 
                 eos: str = "SRK"):
        """
        Initialize calculator.
        
        Args:
            composition: Component name to mole fraction mapping
            eos: Equation of state ("SRK", "PR", "CPA")
        """
        self.composition = composition
        self.eos = eos
        self._fluid = self._create_fluid()
    
    def _create_fluid(self):
        """Create base fluid."""
        eos_classes = {
            "SRK": jneqsim.thermo.system.SystemSrkEos,
            "PR": jneqsim.thermo.system.SystemPrEos,
            "CPA": jneqsim.thermo.system.SystemSrkCPAstatoil
        }
        
        FluidClass = eos_classes.get(self.eos, 
                                    jneqsim.thermo.system.SystemSrkEos)
        fluid = FluidClass(300.0, 50.0)
        
        for comp, frac in self.composition.items():
            fluid.addComponent(comp, frac)
        
        mixing_rule = "classic" if self.eos != "CPA" else 2
        fluid.setMixingRule(mixing_rule)
        
        return fluid
    
    def calculate_properties(self, T_C: float, P_bara: float) -> Dict:
        """Calculate properties at single point."""
        fluid = self._fluid.clone()
        fluid.setTemperature(T_C + 273.15)
        fluid.setPressure(P_bara)
        
        ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
        ops.TPflash()
        
        results = {
            'temperature_C': T_C,
            'pressure_bara': P_bara,
            'number_of_phases': fluid.getNumberOfPhases(),
        }
        
        # Gas phase properties (if exists)
        if fluid.hasPhaseType("gas"):
            gas = fluid.getPhase("gas")
            results['gas'] = {
                'Z': gas.getZ(),
                'density_kg_m3': gas.getDensity("kg/m3"),
                'viscosity_cP': gas.getViscosity("cP"),
                'molar_mass_kg_mol': gas.getMolarMass("kg/mol"),
                'phase_fraction': fluid.getBeta(fluid.getPhaseIndex("gas"))
            }
        
        # Liquid phase properties (if exists)
        if fluid.hasPhaseType("oil") or fluid.hasPhaseType("aqueous"):
            phase_type = "oil" if fluid.hasPhaseType("oil") else "aqueous"
            liq = fluid.getPhase(phase_type)
            results['liquid'] = {
                'density_kg_m3': liq.getDensity("kg/m3"),
                'viscosity_cP': liq.getViscosity("cP"),
                'phase_fraction': fluid.getBeta(fluid.getPhaseIndex(phase_type))
            }
        
        return results
    
    def property_table(self, T_range: Tuple[float, float], 
                       P_range: Tuple[float, float],
                       n_T: int = 10, n_P: int = 10) -> 'pd.DataFrame':
        """Generate property table over T-P range."""
        import pandas as pd
        
        T_vals = np.linspace(T_range[0], T_range[1], n_T)
        P_vals = np.linspace(P_range[0], P_range[1], n_P)
        
        data = []
        for T in T_vals:
            for P in P_vals:
                try:
                    props = self.calculate_properties(T, P)
                    row = {
                        'T_C': T, 'P_bara': P,
                        'n_phases': props['number_of_phases']
                    }
                    if 'gas' in props:
                        row['gas_Z'] = props['gas']['Z']
                        row['gas_rho'] = props['gas']['density_kg_m3']
                    if 'liquid' in props:
                        row['liq_rho'] = props['liquid']['density_kg_m3']
                    data.append(row)
                except Exception as e:
                    print(f"Failed at T={T}, P={P}: {e}")
        
        return pd.DataFrame(data)
    
    def phase_envelope(self) -> Dict[str, List[float]]:
        """Calculate phase envelope."""
        fluid = self._fluid.clone()
        fluid.setTemperature(250.0)
        fluid.setPressure(1.0)
        
        ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
        ops.calcPTphaseEnvelope()
        
        envelope = fluid.getPhaseEnvelope()
        
        return {
            'T_dew': list(envelope.getDewPointLine().get("temperature")),
            'P_dew': list(envelope.getDewPointLine().get("pressure")),
            'T_bubble': list(envelope.getBubblePointLine().get("temperature")),
            'P_bubble': list(envelope.getBubblePointLine().get("pressure")),
            'cricondentherm_T': envelope.getCricondenthermTemperature(),
            'cricondenbar_P': envelope.getCricondenbarPressure()
        }

# Usage
calc = PropertyCalculator({
    'methane': 0.8,
    'ethane': 0.1, 
    'propane': 0.1
}, eos="SRK")

# Single point
props = calc.calculate_properties(T_C=25, P_bara=50)
print(f"Gas Z-factor: {props['gas']['Z']:.4f}")

# Generate table
df = calc.property_table(
    T_range=(-20, 100),
    P_range=(10, 200),
    n_T=5, n_P=5
)
print(df)

# Phase envelope
envelope = calc.phase_envelope()
print(f"Cricondentherm: {envelope['cricondentherm_T']:.1f} K")
```

---

## Batch Processing and Optimization

### Parallel Property Calculations

```python
from concurrent.futures import ThreadPoolExecutor, as_completed
import numpy as np

def calculate_point(args):
    """Calculate properties at single T,P point."""
    T, P, composition = args
    
    # Create fresh fluid (thread-safe)
    SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
    fluid = SystemSrkEos(T + 273.15, P)
    
    for comp, frac in composition.items():
        fluid.addComponent(comp, frac)
    fluid.setMixingRule("classic")
    
    ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
    
    try:
        ops.TPflash()
        return {
            'T': T, 'P': P,
            'Z': fluid.getPhase(0).getZ(),
            'rho': fluid.getDensity("kg/m3"),
            'success': True
        }
    except Exception as e:
        return {'T': T, 'P': P, 'success': False, 'error': str(e)}

def batch_calculate(composition, T_range, P_range, n_workers=4):
    """Calculate properties over grid in parallel."""
    T_vals = np.linspace(*T_range, 20)
    P_vals = np.linspace(*P_range, 20)
    
    # Create work items
    work = [(T, P, composition) for T in T_vals for P in P_vals]
    
    results = []
    with ThreadPoolExecutor(max_workers=n_workers) as executor:
        futures = [executor.submit(calculate_point, w) for w in work]
        
        for future in as_completed(futures):
            results.append(future.result())
    
    return results

# Usage
composition = {'methane': 0.9, 'ethane': 0.1}
results = batch_calculate(composition, T_range=(-50, 100), P_range=(1, 200))

# Filter successful results
successful = [r for r in results if r['success']]
print(f"Completed {len(successful)}/{len(results)} calculations")
```

### Optimization with SciPy

```python
from scipy.optimize import minimize, differential_evolution
import numpy as np

def optimize_compressor_stages(feed_spec, target_pressure, 
                               max_stages=5, max_temp_C=150):
    """
    Optimize compressor staging for minimum power.
    
    Args:
        feed_spec: Feed stream specification dict
        target_pressure: Target discharge pressure (bara)
        max_stages: Maximum number of stages
        max_temp_C: Maximum discharge temperature per stage
    
    Returns:
        Optimal pressure ratios and total power
    """
    
    def calculate_power(pressure_ratios):
        """Calculate total power for given pressure ratios."""
        # Create process
        ProcessSystem = jneqsim.process.processmodel.ProcessSystem
        Stream = jneqsim.process.equipment.stream.Stream
        Compressor = jneqsim.process.equipment.compressor.Compressor
        Cooler = jneqsim.process.equipment.heatexchanger.Cooler
        SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
        
        process = ProcessSystem("Compressor Optimization")
        
        # Create feed
        fluid = SystemSrkEos(feed_spec['T_C'] + 273.15, feed_spec['P_bara'])
        for comp, frac in feed_spec['composition'].items():
            fluid.addComponent(comp, frac)
        fluid.setMixingRule("classic")
        
        feed = Stream("Feed", fluid)
        feed.setFlowRate(feed_spec['flow_rate'], "kg/hr")
        process.add(feed)
        
        current_stream = feed
        current_pressure = feed_spec['P_bara']
        total_power = 0.0
        
        # Add compressor stages
        for i, ratio in enumerate(pressure_ratios):
            if ratio <= 1.0:
                continue
            
            outlet_P = current_pressure * ratio
            
            comp = Compressor(f"Comp-{i+1}", current_stream)
            comp.setOutletPressure(outlet_P)
            process.add(comp)
            
            # Check if cooling needed
            cooler = Cooler(f"Cooler-{i+1}", comp.getOutletStream())
            cooler.setOutTemperature(feed_spec['T_C'] + 273.15)  # Cool to feed T
            process.add(cooler)
            
            current_stream = cooler.getOutletStream()
            current_pressure = outlet_P
        
        try:
            process.run()
            
            # Sum compressor power
            for i in range(len(pressure_ratios)):
                comp_name = f"Comp-{i+1}"
                try:
                    comp = process.getUnit(comp_name)
                    total_power += comp.getPower("kW")
                except:
                    pass
            
            # Penalty if target not reached
            if current_pressure < target_pressure * 0.99:
                total_power += 1e6 * (target_pressure - current_pressure)
            
            return total_power
            
        except Exception as e:
            return 1e9  # Large penalty for failed calculation
    
    # Optimize using differential evolution
    n_stages = 3  # Start with 3 stages
    
    def objective(x):
        ratios = x.tolist()
        return calculate_power(ratios)
    
    # Calculate total ratio needed
    total_ratio = target_pressure / feed_spec['P_bara']
    avg_ratio = total_ratio ** (1/n_stages)
    
    # Bounds: each ratio between 1 and 4
    bounds = [(1.0, 4.0)] * n_stages
    
    result = differential_evolution(
        objective, bounds,
        maxiter=50, seed=42,
        workers=1  # Single worker due to JVM
    )
    
    return {
        'optimal_ratios': result.x.tolist(),
        'total_power_kW': result.fun,
        'success': result.success
    }

# Usage
feed = {
    'T_C': 30,
    'P_bara': 5,
    'composition': {'methane': 0.95, 'ethane': 0.05},
    'flow_rate': 100000
}

result = optimize_compressor_stages(feed, target_pressure=100)
print(f"Optimal ratios: {result['optimal_ratios']}")
print(f"Total power: {result['total_power_kW']:.1f} kW")
```

---

## Integration with Scientific Python

### Visualization with Matplotlib

```python
import matplotlib.pyplot as plt
import numpy as np
from neqsim import jneqsim

def plot_phase_diagram(composition, T_range, P_range, n_points=50):
    """Plot phase diagram showing number of phases."""
    SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
    ThermodynamicOperations = jneqsim.thermodynamicoperations.ThermodynamicOperations
    
    T_vals = np.linspace(*T_range, n_points)
    P_vals = np.linspace(*P_range, n_points)
    T_grid, P_grid = np.meshgrid(T_vals, P_vals)
    
    n_phases = np.zeros_like(T_grid)
    
    for i, P in enumerate(P_vals):
        for j, T in enumerate(T_vals):
            fluid = SystemSrkEos(T + 273.15, P)
            for comp, frac in composition.items():
                fluid.addComponent(comp, frac)
            fluid.setMixingRule("classic")
            
            ops = ThermodynamicOperations(fluid)
            try:
                ops.TPflash()
                n_phases[i, j] = fluid.getNumberOfPhases()
            except:
                n_phases[i, j] = np.nan
    
    fig, ax = plt.subplots(figsize=(10, 8))
    
    # Plot phase regions
    contour = ax.contourf(T_grid, P_grid, n_phases, levels=[0.5, 1.5, 2.5, 3.5],
                          colors=['lightblue', 'lightgreen', 'lightyellow'])
    
    # Add phase envelope
    fluid = SystemSrkEos(250.0, 1.0)
    for comp, frac in composition.items():
        fluid.addComponent(comp, frac)
    fluid.setMixingRule("classic")
    
    ops = ThermodynamicOperations(fluid)
    ops.calcPTphaseEnvelope()
    
    envelope = fluid.getPhaseEnvelope()
    T_dew = np.array(list(envelope.getDewPointLine().get("temperature"))) - 273.15
    P_dew = np.array(list(envelope.getDewPointLine().get("pressure")))
    T_bub = np.array(list(envelope.getBubblePointLine().get("temperature"))) - 273.15
    P_bub = np.array(list(envelope.getBubblePointLine().get("pressure")))
    
    ax.plot(T_dew, P_dew, 'b-', linewidth=2, label='Dew line')
    ax.plot(T_bub, P_bub, 'r-', linewidth=2, label='Bubble line')
    
    ax.set_xlabel('Temperature (°C)')
    ax.set_ylabel('Pressure (bar)')
    ax.set_title('Phase Diagram')
    ax.legend()
    ax.grid(True, alpha=0.3)
    
    plt.colorbar(contour, label='Number of phases')
    plt.tight_layout()
    return fig

# Usage
fig = plot_phase_diagram(
    {'methane': 0.8, 'n-pentane': 0.2},
    T_range=(-50, 200),
    P_range=(1, 100)
)
plt.show()
```

### Integration with Pandas

```python
import pandas as pd
from neqsim import jneqsim

def fluid_to_dataframe(fluid) -> pd.DataFrame:
    """Convert fluid composition to DataFrame."""
    data = []
    
    for i in range(fluid.getNumberOfComponents()):
        comp = fluid.getComponent(i)
        row = {
            'Component': comp.getName(),
            'Mole Fraction': comp.getz(),
            'Molar Mass (g/mol)': comp.getMolarMass() * 1000,
            'Tc (K)': comp.getTC(),
            'Pc (bar)': comp.getPC(),
            'Acentric Factor': comp.getAcentricFactor()
        }
        
        # Add phase compositions if available
        for phase_idx in range(fluid.getNumberOfPhases()):
            phase = fluid.getPhase(phase_idx)
            phase_name = str(phase.getType())
            row[f'x_{phase_name}'] = phase.getComponent(i).getx()
        
        data.append(row)
    
    return pd.DataFrame(data)

def process_results_to_dataframe(process) -> pd.DataFrame:
    """Extract process results to DataFrame."""
    data = []
    
    # Iterate through units
    for i in range(process.getNumberOfUnits()):
        unit = process.getUnit(i)
        
        row = {
            'Unit': unit.getName(),
            'Type': type(unit).__name__,
        }
        
        # Get outlet conditions if stream-like
        try:
            outlet = unit.getOutletStream() if hasattr(unit, 'getOutletStream') else unit
            row['T_out (°C)'] = outlet.getTemperature() - 273.15
            row['P_out (bar)'] = outlet.getPressure()
            row['Flow (kg/hr)'] = outlet.getFlowRate("kg/hr")
        except:
            pass
        
        # Get power if applicable
        try:
            row['Power (kW)'] = unit.getPower("kW")
        except:
            pass
        
        # Get duty if applicable
        try:
            row['Duty (kW)'] = unit.getDuty() / 1000
        except:
            pass
        
        data.append(row)
    
    return pd.DataFrame(data)

# Usage
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
fluid = SystemSrkEos(300.0, 50.0)
fluid.addComponent("methane", 0.8)
fluid.addComponent("ethane", 0.15)
fluid.addComponent("propane", 0.05)
fluid.setMixingRule("classic")

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()

df = fluid_to_dataframe(fluid)
print(df.to_string())
```

---

## Performance Considerations

### Minimize Java-Python Boundary Crossings

```python
# BAD: Many small calls
for i in range(1000):
    T = fluid.getTemperature()  # Crosses JNI boundary each time
    P = fluid.getPressure()
    
# GOOD: Batch operations
# Get all data at once using toJson() or similar bulk method
json_str = fluid.toJson()
data = json.loads(json_str)
```

### Reuse Fluid Objects

```python
# BAD: Creating new fluid each time
def bad_calculate(T, P, composition):
    fluid = SystemSrkEos(T, P)  # New object each call
    # ... add components
    ops = ThermodynamicOperations(fluid)
    ops.TPflash()
    return fluid.getDensity()

# GOOD: Clone from template
template_fluid = None

def good_calculate(T, P, composition):
    global template_fluid
    
    if template_fluid is None:
        template_fluid = SystemSrkEos(T, P)
        for comp, frac in composition.items():
            template_fluid.addComponent(comp, frac)
        template_fluid.setMixingRule("classic")
    
    fluid = template_fluid.clone()  # Reuse structure
    fluid.setTemperature(T)
    fluid.setPressure(P)
    
    ops = ThermodynamicOperations(fluid)
    ops.TPflash()
    return fluid.getDensity()
```

### Use Appropriate Data Types

```python
# Explicit type conversion for better performance
from jpype.types import JDouble

# When passing many values, pre-convert
temperature = JDouble(300.0)  # Explicit conversion
fluid.setTemperature(temperature)
```

---

## Best Practices

### 1. Error Handling

```python
from neqsim import jneqsim

def safe_flash(fluid, T=None, P=None):
    """Safe flash calculation with error handling."""
    try:
        if T is not None:
            fluid.setTemperature(T)
        if P is not None:
            fluid.setPressure(P)
        
        ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
        ops.TPflash()
        
        if fluid.getNumberOfPhases() == 0:
            raise ValueError("Flash calculation returned no phases")
        
        return True
        
    except Exception as e:
        # Java exceptions wrapped by JPype
        error_msg = str(e)
        if "convergence" in error_msg.lower():
            print(f"Convergence failed at T={T}, P={P}")
        else:
            print(f"Flash failed: {error_msg}")
        return False
```

### 2. Resource Management

```python
# Clean up large objects when done
import gc

def intensive_calculation():
    results = []
    
    for i in range(1000):
        fluid = SystemSrkEos(300.0, 50.0)
        # ... calculations
        results.append(some_result)
        
        # Periodically trigger garbage collection
        if i % 100 == 0:
            gc.collect()
    
    return results
```

### 3. Logging and Debugging

```python
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def debug_flash(fluid):
    """Flash calculation with detailed logging."""
    logger.info(f"Starting flash: T={fluid.getTemperature():.1f}K, "
                f"P={fluid.getPressure():.1f}bar")
    
    # Log composition
    for i in range(fluid.getNumberOfComponents()):
        comp = fluid.getComponent(i)
        logger.debug(f"  {comp.getName()}: z={comp.getz():.4f}")
    
    ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
    ops.TPflash()
    
    logger.info(f"Flash complete: {fluid.getNumberOfPhases()} phases")
    
    return fluid
```

### 4. Unit Conversion Helper

```python
class UnitConverter:
    """Helper for unit conversions."""
    
    @staticmethod
    def C_to_K(T_C: float) -> float:
        return T_C + 273.15
    
    @staticmethod
    def K_to_C(T_K: float) -> float:
        return T_K - 273.15
    
    @staticmethod
    def bara_to_psia(P_bara: float) -> float:
        return P_bara * 14.5038
    
    @staticmethod
    def psia_to_bara(P_psia: float) -> float:
        return P_psia / 14.5038
    
    @staticmethod
    def kg_m3_to_lb_ft3(rho: float) -> float:
        return rho * 0.062428
    
    @staticmethod
    def cP_to_Pa_s(mu: float) -> float:
        return mu * 0.001

# Usage
uc = UnitConverter()
T_K = uc.C_to_K(25)  # 298.15 K
```

---

## See Also

- [Extending Process Equipment](extending_process_equipment)
- [Extending Physical Properties](extending_physical_properties)
- [Extending Thermodynamic Models](extending_thermodynamic_models)
- [NeqSim Python Documentation](https://equinor.github.io/neqsim-python/)
- [JPype Documentation](https://jpype.readthedocs.io/)

---

*Document last updated: February 2026*
