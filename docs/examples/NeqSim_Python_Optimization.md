---
layout: default
title: "NeqSim Python Optimization"
description: "Jupyter notebook tutorial for NeqSim"
parent: Examples
nav_order: 1
---

# NeqSim Python Optimization

> **Note:** This is an auto-generated Markdown version of the Jupyter notebook 
> [`NeqSim_Python_Optimization.ipynb`](https://github.com/equinor/neqsim/blob/master/docs/examples/NeqSim_Python_Optimization.ipynb).
> You can also [view it on nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/NeqSim_Python_Optimization.ipynb) 
> or [open in Google Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/NeqSim_Python_Optimization.ipynb).

---

# NeqSim Process Optimization with Python

This notebook demonstrates how to use **Python optimization libraries** (SciPy, etc.) with **NeqSim process simulations**. This approach gives you the flexibility of Python's optimization ecosystem while leveraging NeqSim's rigorous thermodynamics and equipment models.

## Table of Contents

1. [Introduction](#1-introduction)
2. [Setup and Imports](#2-setup-and-imports)
3. [Creating a Process Model](#3-creating-a-process-model)
4. [Defining the Optimization Problem](#4-defining-the-optimization-problem)
5. [Using SciPy Optimizers](#5-using-scipy-optimizers)
6. [Equipment Constraints](#6-equipment-constraints)
   - 6.2 [Compressor Curves and Surge/Choke Constraints](#62-compressor-curves-and-surgechoke-constraints)
   - 6.2.1 [Optimization with Compressor Curve Constraints](#621-optimization-with-compressor-curve-constraints)
   - 6.2.2 [Using CompressorChartGenerator](#622-using-compressorchart-generator-automatic-curves)
   - 6.2.3 [Multi-Map MW Interpolation](#623-multi-map-mw-interpolation-for-varying-gas-composition)
7. [Multi-Objective with Pareto](#7-multi-objective-with-pareto)
8. [Global Optimization](#8-global-optimization)
9. [Gradient-Based Optimization](#9-gradient-based-optimization)
10. [Best Practices](#10-best-practices)

## 1. Introduction

### Why Use Python Optimizers with NeqSim?

| Approach | Advantages | Best For |
|----------|------------|----------|
| **NeqSim Built-in** (ProductionOptimizer) | Integrated, equipment-aware | Standard throughput optimization |
| **Python + NeqSim** | Flexible, any algorithm, custom objectives | Research, complex constraints, ML integration |

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Python Optimization Layer                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │
│  │ scipy.opt   │  │ differential│  │ pymoo/NSGA │              │
│  │ minimize()  │  │ _evolution()│  │ (Pareto)   │              │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘              │
└─────────┼────────────────┼────────────────┼─────────────────────┘
          │                │                │
          ▼                ▼                ▼
┌─────────────────────────────────────────────────────────────────┐
│              Objective Function (Python callable)                │
│  def objective(x):                                               │
│      set_variables(process, x)                                   │
│      process.run()                                               │
│      return evaluate(process)                                    │
└─────────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    NeqSim Process Model                          │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐             │
│  │  Feed   │─▶│ Compres │─▶│ Cooler  │─▶│Separator│             │
│  │ Stream  │  │  sor    │  │         │  │         │             │
│  └─────────┘  └─────────┘  └─────────┘  └─────────┘             │
└─────────────────────────────────────────────────────────────────┘
```

## 2. Setup and Imports

```python
# Install required packages if needed
# !pip install neqsim scipy numpy matplotlib
```

```python
# Python imports
import numpy as np
from scipy import optimize
import matplotlib.pyplot as plt

# NeqSim imports via JPype
from neqsim.neqsimpython import jneqsim

# Process equipment
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
Compressor = jneqsim.process.equipment.compressor.Compressor
Cooler = jneqsim.process.equipment.heatexchanger.Cooler
Heater = jneqsim.process.equipment.heatexchanger.Heater
Separator = jneqsim.process.equipment.separator.Separator
ThrottlingValve = jneqsim.process.equipment.valve.ThrottlingValve
Mixer = jneqsim.process.equipment.mixer.Mixer
Splitter = jneqsim.process.equipment.splitter.Splitter

# Thermodynamic systems
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
SystemPrEos = jneqsim.thermo.system.SystemPrEos

print("NeqSim and SciPy loaded successfully!")
print(f"SciPy version: {optimize.__name__}")
```

## 3. Creating a Process Model

Let's create a gas compression and cooling process that we'll optimize.

```python
def create_gas_process(inlet_pressure=30.0, outlet_pressure=100.0):
    """
    Create a two-stage gas compression process with intercooling.
    
    Parameters:
    - inlet_pressure: Feed pressure (bara)
    - outlet_pressure: Target outlet pressure (bara)
    
    Returns:
    - ProcessSystem object
    """
    # Create natural gas fluid
    gas = SystemSrkEos(288.15, inlet_pressure)  # 15°C
    gas.addComponent("nitrogen", 0.02)
    gas.addComponent("CO2", 0.01)
    gas.addComponent("methane", 0.85)
    gas.addComponent("ethane", 0.07)
    gas.addComponent("propane", 0.03)
    gas.addComponent("i-butane", 0.01)
    gas.addComponent("n-butane", 0.01)
    gas.setMixingRule("classic")
    
    # Create process system
    process = ProcessSystem()
    
    # Feed stream
    feed = Stream("feed", gas)
    feed.setFlowRate(50000.0, "kg/hr")
    feed.setPressure(inlet_pressure, "bara")
    feed.setTemperature(288.15, "K")
    process.add(feed)
    
    # Calculate intermediate pressure (geometric mean for equal compression ratios)
    intermediate_pressure = np.sqrt(inlet_pressure * outlet_pressure)
    
    # Stage 1 compressor
    stage1 = Compressor("stage1", feed)
    stage1.setOutletPressure(intermediate_pressure)
    stage1.setPolytropicEfficiency(0.78)
    process.add(stage1)
    
    # Intercooler (cool back to near inlet temperature)
    intercooler = Cooler("intercooler", stage1.getOutletStream())
    intercooler.setOutTemperature(303.15)  # 30°C
    process.add(intercooler)
    
    # Stage 2 compressor
    stage2 = Compressor("stage2", intercooler.getOutletStream())
    stage2.setOutletPressure(outlet_pressure)
    stage2.setPolytropicEfficiency(0.76)
    process.add(stage2)
    
    # Aftercooler
    aftercooler = Cooler("aftercooler", stage2.getOutletStream())
    aftercooler.setOutTemperature(313.15)  # 40°C
    process.add(aftercooler)
    
    # Run initial simulation
    process.run()
    
    return process

# Create and test the process
process = create_gas_process()

print("Process created successfully!")
print(f"\nInitial conditions:")
print(f"  Feed flow rate: {process.getUnit('feed').getFlowRate('kg/hr'):.0f} kg/hr")
print(f"  Stage 1 power: {process.getUnit('stage1').getPower('kW'):.1f} kW")
print(f"  Stage 2 power: {process.getUnit('stage2').getPower('kW'):.1f} kW")
print(f"  Total power: {process.getUnit('stage1').getPower('kW') + process.getUnit('stage2').getPower('kW'):.1f} kW")
```

## 4. Defining the Optimization Problem

The key is creating a **Python callable** that:
1. Takes decision variables as input
2. Sets them on the NeqSim process
3. Runs the simulation
4. Returns the objective value

```python
class NeqSimOptimizationProblem:
    """
    Wrapper class to use NeqSim process with Python optimizers.
    
    This class handles:
    - Setting decision variables on the process
    - Running the simulation
    - Evaluating objectives and constraints
    - Counting function evaluations
    """
    
    def __init__(self, process_factory, variable_specs, objective_func):
        """
        Initialize the optimization problem.
        
        Parameters:
        - process_factory: Callable that creates a fresh ProcessSystem
        - variable_specs: List of dicts with 'name', 'min', 'max', 'setter'
        - objective_func: Callable(process) -> float
        """
        self.process_factory = process_factory
        self.variable_specs = variable_specs
        self.objective_func = objective_func
        self.process = None
        self.eval_count = 0
        self.history = []
        
    def get_bounds(self):
        """Return bounds as [(min1, max1), (min2, max2), ...]."""
        return [(v['min'], v['max']) for v in self.variable_specs]
    
    def get_x0(self):
        """Return initial point (midpoint of bounds)."""
        return np.array([(v['min'] + v['max']) / 2 for v in self.variable_specs])
    
    def evaluate(self, x):
        """
        Evaluate the objective function.
        
        Parameters:
        - x: Array of decision variable values
        
        Returns:
        - Objective value (for minimization)
        """
        self.eval_count += 1
        
        # Create fresh process (avoids state issues)
        self.process = self.process_factory()
        
        # Set decision variables
        for i, var_spec in enumerate(self.variable_specs):
            var_spec['setter'](self.process, x[i])
        
        # Run simulation
        try:
            self.process.run()
            obj_value = self.objective_func(self.process)
        except Exception as e:
            print(f"Simulation failed at x={x}: {e}")
            obj_value = 1e10  # Large penalty for failed simulations
        
        # Record history
        self.history.append({'x': x.copy(), 'obj': obj_value})
        
        return obj_value
    
    def __call__(self, x):
        """Make the object callable for scipy.optimize."""
        return self.evaluate(x)

print("NeqSimOptimizationProblem class defined")
```

```python
# Define the optimization problem: Minimize total compressor power

# Decision variables
variable_specs = [
    {
        'name': 'intermediate_pressure',
        'min': 40.0,
        'max': 80.0,
        'setter': lambda proc, val: proc.getUnit('stage1').setOutletPressure(val)
    },
    {
        'name': 'intercooler_temp',
        'min': 293.15,  # 20°C
        'max': 323.15,  # 50°C
        'setter': lambda proc, val: proc.getUnit('intercooler').setOutTemperature(val)
    }
]

# Objective: Minimize total power
def total_power_objective(process):
    """Return total compressor power in kW (to minimize)."""
    power1 = process.getUnit('stage1').getPower('kW')
    power2 = process.getUnit('stage2').getPower('kW')
    return power1 + power2

# Create optimization problem
problem = NeqSimOptimizationProblem(
    process_factory=create_gas_process,
    variable_specs=variable_specs,
    objective_func=total_power_objective
)

print("Optimization problem defined:")
print(f"  Variables: {[v['name'] for v in variable_specs]}")
print(f"  Bounds: {problem.get_bounds()}")
print(f"  Initial point: {problem.get_x0()}")
```

## 5. Using SciPy Optimizers

### 5.1 Nelder-Mead (Derivative-Free)

```python
# Optimize using Nelder-Mead (simplex method)
problem.eval_count = 0
problem.history = []

result_nm = optimize.minimize(
    problem,
    x0=problem.get_x0(),
    method='Nelder-Mead',
    options={
        'maxiter': 100,
        'xatol': 0.1,
        'fatol': 1.0,
        'disp': True
    }
)

print("\n=== Nelder-Mead Results ===")
print(f"Success: {result_nm.success}")
print(f"Message: {result_nm.message}")
print(f"Function evaluations: {problem.eval_count}")
print(f"\nOptimal values:")
for i, var in enumerate(variable_specs):
    print(f"  {var['name']}: {result_nm.x[i]:.2f}")
print(f"\nMinimum total power: {result_nm.fun:.1f} kW")
```

### 5.2 Powell Method

```python
# Optimize using Powell method
problem.eval_count = 0

result_powell = optimize.minimize(
    problem,
    x0=problem.get_x0(),
    method='Powell',
    bounds=problem.get_bounds(),
    options={
        'maxiter': 100,
        'ftol': 1.0,
        'disp': True
    }
)

print("\n=== Powell Results ===")
print(f"Function evaluations: {problem.eval_count}")
print(f"Optimal intermediate pressure: {result_powell.x[0]:.2f} bara")
print(f"Optimal intercooler temp: {result_powell.x[1] - 273.15:.1f} °C")
print(f"Minimum total power: {result_powell.fun:.1f} kW")
```

### 5.3 Compare Algorithms

```python
# Compare multiple optimization algorithms

algorithms = ['Nelder-Mead', 'Powell', 'COBYLA']
results = {}

for alg in algorithms:
    problem.eval_count = 0
    
    try:
        if alg == 'COBYLA':
            # COBYLA needs constraints, not bounds
            cons = []
            for i, (lb, ub) in enumerate(problem.get_bounds()):
                cons.append({'type': 'ineq', 'fun': lambda x, i=i, lb=lb: x[i] - lb})
                cons.append({'type': 'ineq', 'fun': lambda x, i=i, ub=ub: ub - x[i]})
            
            result = optimize.minimize(
                problem, x0=problem.get_x0(),
                method=alg, constraints=cons,
                options={'maxiter': 100, 'disp': False}
            )
        else:
            result = optimize.minimize(
                problem, x0=problem.get_x0(),
                method=alg, bounds=problem.get_bounds(),
                options={'maxiter': 100, 'disp': False}
            )
        
        results[alg] = {
            'x': result.x,
            'fun': result.fun,
            'nfev': problem.eval_count,
            'success': result.success
        }
    except Exception as e:
        print(f"{alg} failed: {e}")

# Display comparison
print("\n=== Algorithm Comparison ===")
print("-" * 70)
print(f"{'Algorithm':<15} {'P_inter (bara)':<15} {'T_inter (°C)':<15} {'Power (kW)':<12} {'Evals'}")
print("-" * 70)
for alg, res in results.items():
    print(f"{alg:<15} {res['x'][0]:<15.1f} {res['x'][1]-273.15:<15.1f} {res['fun']:<12.1f} {res['nfev']}")
```

## 6. Equipment Constraints

Real processes have equipment limitations. Let's add constraints for:
- Maximum compressor discharge temperature
- Maximum compressor power
- Minimum/maximum pressure ratios

```python
class ConstrainedOptimizationProblem(NeqSimOptimizationProblem):
    """
    Extended optimization problem with equipment constraints.
    """
    
    def __init__(self, process_factory, variable_specs, objective_func, constraint_specs):
        super().__init__(process_factory, variable_specs, objective_func)
        self.constraint_specs = constraint_specs
        
    def evaluate_constraints(self, x):
        """
        Evaluate all constraints.
        
        Returns:
        - List of constraint values (positive = feasible for inequality)
        """
        # Make sure process is up to date
        if self.process is None:
            self.evaluate(x)
        
        constraint_values = []
        for spec in self.constraint_specs:
            value = spec['evaluator'](self.process)
            if spec['type'] == 'max':
                # g(x) >= 0 means value <= limit
                constraint_values.append(spec['limit'] - value)
            else:  # 'min'
                # g(x) >= 0 means value >= limit
                constraint_values.append(value - spec['limit'])
        
        return constraint_values
    
    def get_scipy_constraints(self):
        """
        Return constraints in SciPy format for constrained optimizers.
        """
        constraints = []
        for i, spec in enumerate(self.constraint_specs):
            constraints.append({
                'type': 'ineq',
                'fun': lambda x, idx=i: self._constraint_func(x, idx)
            })
        return constraints
    
    def _constraint_func(self, x, constraint_idx):
        """Evaluate single constraint (for SciPy)."""
        self.evaluate(x)  # Updates self.process
        return self.evaluate_constraints(x)[constraint_idx]

print("ConstrainedOptimizationProblem class defined")
```

```python
# Define equipment constraints
constraint_specs = [
    {
        'name': 'max_stage1_discharge_temp',
        'type': 'max',
        'limit': 423.15,  # 150°C
        'evaluator': lambda proc: proc.getUnit('stage1').getOutletStream().getTemperature('K')
    },
    {
        'name': 'max_stage2_discharge_temp',
        'type': 'max',
        'limit': 423.15,  # 150°C
        'evaluator': lambda proc: proc.getUnit('stage2').getOutletStream().getTemperature('K')
    },
    {
        'name': 'max_stage1_power',
        'type': 'max',
        'limit': 2500.0,  # kW
        'evaluator': lambda proc: proc.getUnit('stage1').getPower('kW')
    },
    {
        'name': 'max_stage2_power',
        'type': 'max',
        'limit': 2500.0,  # kW
        'evaluator': lambda proc: proc.getUnit('stage2').getPower('kW')
    },
    {
        'name': 'min_pressure_ratio_stage1',
        'type': 'min',
        'limit': 1.5,  # Minimum compression ratio
        'evaluator': lambda proc: (
            proc.getUnit('stage1').getOutletStream().getPressure('bara') /
            proc.getUnit('stage1').getInletStream().getPressure('bara')
        )
    }
]

# Create constrained problem
constrained_problem = ConstrainedOptimizationProblem(
    process_factory=create_gas_process,
    variable_specs=variable_specs,
    objective_func=total_power_objective,
    constraint_specs=constraint_specs
)

print(f"Defined {len(constraint_specs)} constraints:")
for spec in constraint_specs:
    print(f"  - {spec['name']}: {spec['type']} {spec['limit']}")
```

## 6.2 Compressor Curves and Surge/Choke Constraints

Compressor curves define the actual operating envelope. NeqSim supports:
- **Multi-speed performance maps** (head, efficiency vs flow at different speeds)
- **Surge curves** - minimum flow limit (causes instability)
- **Stone wall (choke) curves** - maximum flow limit

### Compressor Operating Envelope

```
                    Head
                     ↑
                     │        ╭──────────╮
                     │       ╱   Stone    ╲
                     │      ╱    Wall      ╲
              Surge │     ╱   (Choke)      ╲
              Curve │    ╱                  ╲
                    │   ╱                    ╲
                    │  ╱   Operating          ╲
                    │ ╱     Envelope           ╲
                    │╱                          ╲
                    └─────────────────────────────→ Flow
                         ↑                  ↑
                    Minimum Flow      Maximum Flow
                   (Surge Point)    (Stone Wall Point)
```

```python
# Import compressor curve classes
from jpype import JArray, JDouble

CompressorChart = jneqsim.process.equipment.compressor.CompressorChart
CompressorChartGenerator = jneqsim.process.equipment.compressor.CompressorChartGenerator

def create_process_with_compressor_curves():
    """
    Create a gas compression process with compressor performance curves.
    """
    # Create natural gas fluid
    gas = SystemSrkEos(288.15, 30.0)  # 15°C, 30 bara
    gas.addComponent("nitrogen", 0.02)
    gas.addComponent("methane", 0.85)
    gas.addComponent("ethane", 0.08)
    gas.addComponent("propane", 0.05)
    gas.setMixingRule("classic")
    
    # Create process system
    process = ProcessSystem()
    
    # Feed stream
    feed = Stream("feed", gas)
    feed.setFlowRate(5000.0, "Am3/hr")  # Actual m³/hr
    feed.setPressure(30.0, "bara")
    feed.setTemperature(288.15, "K")
    process.add(feed)
    
    # Create compressor
    compressor = Compressor("compressor", feed)
    compressor.setOutletPressure(80.0)  # bara
    compressor.setPolytropicEfficiency(0.78)
    process.add(compressor)
    
    # ========================================
    # SET UP COMPRESSOR PERFORMANCE CURVES
    # ========================================
    
    # Get or create compressor chart
    chart = compressor.getCompressorChart()
    
    # Chart conditions: [temperature (°C), pressure (bara), density (kg/m³), MW (g/mol)]
    chart_conditions = JArray(JDouble)([25.0, 30.0, 25.0, 18.0])
    
    # Define speed curves (RPM)
    speeds = JArray(JDouble)([9000, 10000, 11000, 12000])
    
    # Flow values for each speed (Am3/hr)
    # Using Python lists, then converting to Java arrays
    flow_data = [
        [2500, 3000, 3500, 4000, 4500, 5000, 5500],  # 9000 RPM
        [2800, 3300, 3800, 4300, 4800, 5300, 5800],  # 10000 RPM
        [3100, 3600, 4100, 4600, 5100, 5600, 6100],  # 11000 RPM
        [3400, 3900, 4400, 4900, 5400, 5900, 6400]   # 12000 RPM
    ]
    
    # Head values for each speed (kJ/kg)
    head_data = [
        [95, 92, 88, 82, 74, 64, 52],   # 9000 RPM
        [110, 107, 102, 95, 86, 75, 62], # 10000 RPM
        [127, 123, 117, 109, 99, 87, 73], # 11000 RPM
        [145, 140, 134, 125, 114, 100, 84] # 12000 RPM
    ]
    
    # Polytropic efficiency for each speed (%)
    eff_data = [
        [74, 77, 79, 80, 79, 76, 71],   # 9000 RPM
        [75, 78, 80, 81, 80, 77, 72],   # 10000 RPM
        [74, 77, 79, 80, 79, 76, 71],   # 11000 RPM
        [73, 76, 78, 79, 78, 75, 70]    # 12000 RPM
    ]
    
    # Convert to Java 2D arrays
    n_speeds = len(speeds)
    n_points = len(flow_data[0])
    
    flow_array = JArray(JArray(JDouble))(n_speeds)
    head_array = JArray(JArray(JDouble))(n_speeds)
    eff_array = JArray(JArray(JDouble))(n_speeds)
    
    for i in range(n_speeds):
        flow_array[i] = JArray(JDouble)(flow_data[i])
        head_array[i] = JArray(JDouble)(head_data[i])
        eff_array[i] = JArray(JDouble)(eff_data[i])
    
    # Set the curves on the chart
    chart.setCurves(chart_conditions, speeds, flow_array, head_array, flow_array, eff_array)
    chart.setHeadUnit("kJ/kg")
    
    # ========================================
    # SET SURGE CURVE
    # ========================================
    surge_flow = JArray(JDouble)([2300, 2600, 2900, 3200])  # Am3/hr at surge
    surge_head = JArray(JDouble)([90, 105, 122, 140])        # kJ/kg at surge
    chart.getSurgeCurve().setCurve(chart_conditions, surge_flow, surge_head)
    
    # ========================================
    # SET STONE WALL (CHOKE) CURVE
    # ========================================
    stonewall_flow = JArray(JDouble)([5700, 6000, 6300, 6600])  # Am3/hr at choke
    stonewall_head = JArray(JDouble)([50, 60, 70, 80])           # kJ/kg at choke
    chart.getStoneWallCurve().setCurve(chart_conditions, stonewall_flow, stonewall_head)
    
    # Set compressor speed
    compressor.setSpeed(10500)  # RPM
    compressor.setUsePolytropicCalc(True)
    
    # Aftercooler
    aftercooler = Cooler("aftercooler", compressor.getOutletStream())
    aftercooler.setOutTemperature(313.15)  # 40°C
    process.add(aftercooler)
    
    process.run()
    return process

# Create the process
process_curves = create_process_with_compressor_curves()

# Get compressor info
comp = process_curves.getUnit("compressor")
print("=== Compressor with Performance Curves ===")
print(f"Flow rate: {comp.getInletStream().getFlowRate('Am3/hr'):.0f} Am3/hr")
print(f"Speed: {comp.getSpeed():.0f} RPM")
print(f"Polytropic head: {comp.getPolytropicHead('kJ/kg'):.1f} kJ/kg")
print(f"Polytropic efficiency: {comp.getPolytropicEfficiency()*100:.1f} %")
print(f"Power: {comp.getPower('kW'):.1f} kW")
```

```python
# Check surge and choke margins
chart = comp.getCompressorChart()

# Get current operating point
flow = comp.getInletStream().getFlowRate("Am3/hr")
head = comp.getPolytropicHead("kJ/kg")

# Check distance to operating limits
surge_curve = chart.getSurgeCurve()
stonewall_curve = chart.getStoneWallCurve()

# Check if in surge or choked
is_surge = surge_curve.isSurge(head, flow)
is_stonewall = stonewall_curve.isStoneWall(head, flow)

print("\n=== Operating Limit Analysis ===")
print(f"Operating point: {flow:.0f} Am3/hr, {head:.1f} kJ/kg")
print(f"In surge: {is_surge}")
print(f"Is choked (stone wall): {is_stonewall}")

# Get surge and stonewall flows at current head
surge_flow = surge_curve.getSurgeFlow(head)
stonewall_flow = stonewall_curve.getStoneWallFlow(head)

print(f"\nAt head = {head:.1f} kJ/kg:")
print(f"  Surge flow: {surge_flow:.0f} Am3/hr")
print(f"  Stone wall flow: {stonewall_flow:.0f} Am3/hr")
print(f"  Current flow: {flow:.0f} Am3/hr")

# Calculate margins
surge_margin = (flow - surge_flow) / surge_flow * 100
stonewall_margin = (stonewall_flow - flow) / stonewall_flow * 100

print(f"\n  Surge margin: {surge_margin:.1f}%")
print(f"  Stone wall margin: {stonewall_margin:.1f}%")
```

### 6.2.1 Optimization with Compressor Curve Constraints

Now let's optimize while respecting the compressor curve limits (surge and choke).

```python
# Define compressor curve constraint evaluators

class CompressorCurveOptimization:
    """
    Optimization problem with compressor curve constraints.
    """
    
    def __init__(self):
        self.process = None
        self.eval_count = 0
        self.history = []
        
    def create_process(self):
        """Create fresh process with compressor curves."""
        return create_process_with_compressor_curves()
    
    def set_variables(self, x):
        """
        Set optimization variables.
        x[0] = flow rate (Am3/hr)
        x[1] = compressor speed (RPM)
        """
        flow_rate, speed = x
        self.process.getUnit("feed").setFlowRate(flow_rate, "Am3/hr")
        self.process.getUnit("compressor").setSpeed(speed)
    
    def objective(self, x):
        """
        Minimize specific power (kW per 1000 Am3/hr).
        """
        self.eval_count += 1
        self.process = self.create_process()
        self.set_variables(x)
        
        try:
            self.process.run()
            power = self.process.getUnit("compressor").getPower("kW")
            flow = x[0]  # Am3/hr
            specific_power = power / (flow / 1000.0)
            
            # Store history
            self.history.append({
                'x': x.copy(),
                'obj': specific_power,
                'feasible': self.check_feasibility()
            })
            
            return specific_power
            
        except Exception as e:
            print(f"Simulation failed: {e}")
            return 1e10
    
    def check_feasibility(self):
        """Check if operating point is within compressor envelope."""
        comp = self.process.getUnit("compressor")
        chart = comp.getCompressorChart()
        
        flow = comp.getInletStream().getFlowRate("Am3/hr")
        head = comp.getPolytropicHead("kJ/kg")
        
        is_surge = chart.getSurgeCurve().isSurge(head, flow)
        is_stonewall = chart.getStoneWallCurve().isStoneWall(head, flow)
        
        return not is_surge and not is_stonewall
    
    def surge_constraint(self, x):
        """
        Surge constraint: g(x) >= 0 means NOT in surge.
        Returns: (flow - surge_flow) / surge_flow
        """
        self.process = self.create_process()
        self.set_variables(x)
        self.process.run()
        
        comp = self.process.getUnit("compressor")
        chart = comp.getCompressorChart()
        
        flow = comp.getInletStream().getFlowRate("Am3/hr")
        head = comp.getPolytropicHead("kJ/kg")
        surge_flow = chart.getSurgeCurve().getSurgeFlow(head)
        
        # Positive = above surge, feasible
        return (flow - surge_flow) / surge_flow
    
    def stonewall_constraint(self, x):
        """
        Stone wall constraint: g(x) >= 0 means NOT choked.
        Returns: (stonewall_flow - flow) / stonewall_flow
        """
        comp = self.process.getUnit("compressor")
        chart = comp.getCompressorChart()
        
        flow = comp.getInletStream().getFlowRate("Am3/hr")
        head = comp.getPolytropicHead("kJ/kg")
        stonewall_flow = chart.getStoneWallCurve().getStoneWallFlow(head)
        
        # Positive = below stone wall, feasible
        return (stonewall_flow - flow) / stonewall_flow
    
    def min_surge_margin_constraint(self, x, min_margin=0.10):
        """
        Minimum surge margin constraint (default 10%).
        g(x) >= 0 means surge margin >= min_margin
        """
        self.process = self.create_process()
        self.set_variables(x)
        self.process.run()
        
        comp = self.process.getUnit("compressor")
        chart = comp.getCompressorChart()
        
        flow = comp.getInletStream().getFlowRate("Am3/hr")
        head = comp.getPolytropicHead("kJ/kg")
        surge_flow = chart.getSurgeCurve().getSurgeFlow(head)
        
        # Surge margin
        margin = (flow - surge_flow) / surge_flow
        
        # Positive = margin >= min_margin
        return margin - min_margin
    
    def max_power_constraint(self, x, max_power=3000.0):
        """
        Maximum power constraint.
        g(x) >= 0 means power <= max_power
        """
        comp = self.process.getUnit("compressor")
        power = comp.getPower("kW")
        
        return (max_power - power) / max_power

# Create optimization problem
curve_problem = CompressorCurveOptimization()

print("Compressor curve optimization problem defined")
print("Variables: [flow_rate (Am3/hr), speed (RPM)]")
print("Objective: Minimize specific power (kW per 1000 Am3/hr)")
print("Constraints:")
print("  - Surge margin >= 10%")
print("  - Not in stone wall (choke)")
print("  - Power <= 3000 kW")
```

```python
# Run constrained optimization with compressor curve limits

# Variable bounds
bounds = [
    (3000.0, 6000.0),   # Flow rate: 3000-6000 Am3/hr
    (9000.0, 12000.0)   # Speed: 9000-12000 RPM
]

# Initial point
x0 = np.array([4500.0, 10500.0])

# Define constraints for scipy
constraints = [
    {
        'type': 'ineq',
        'fun': lambda x: curve_problem.min_surge_margin_constraint(x, 0.10)
    },
    {
        'type': 'ineq',
        'fun': lambda x: curve_problem.stonewall_constraint(x)
    },
    {
        'type': 'ineq',
        'fun': lambda x: curve_problem.max_power_constraint(x, 3000.0)
    }
]

# Optimize
curve_problem.eval_count = 0
curve_problem.history = []

result_curves = optimize.minimize(
    curve_problem.objective,
    x0=x0,
    method='SLSQP',
    bounds=bounds,
    constraints=constraints,
    options={'maxiter': 100, 'disp': True}
)

print("\n=== Optimization with Compressor Curve Constraints ===")
print(f"Success: {result_curves.success}")
print(f"Function evaluations: {curve_problem.eval_count}")
print(f"\nOptimal values:")
print(f"  Flow rate: {result_curves.x[0]:.0f} Am3/hr")
print(f"  Speed: {result_curves.x[1]:.0f} RPM")
print(f"\nMinimum specific power: {result_curves.fun:.2f} kW per 1000 Am3/hr")

# Verify constraints at optimum
curve_problem.process = curve_problem.create_process()
curve_problem.set_variables(result_curves.x)
curve_problem.process.run()

comp = curve_problem.process.getUnit("compressor")
chart = comp.getCompressorChart()
flow = comp.getInletStream().getFlowRate("Am3/hr")
head = comp.getPolytropicHead("kJ/kg")
power = comp.getPower("kW")

surge_flow = chart.getSurgeCurve().getSurgeFlow(head)
surge_margin = (flow - surge_flow) / surge_flow * 100

print(f"\nAt optimum:")
print(f"  Polytropic head: {head:.1f} kJ/kg")
print(f"  Power: {power:.1f} kW")
print(f"  Surge flow: {surge_flow:.0f} Am3/hr")
print(f"  Surge margin: {surge_margin:.1f}% (minimum 10%)")
print(f"  In surge: {chart.getSurgeCurve().isSurge(head, flow)}")
```

### 6.2.2 Using CompressorChartGenerator (Automatic Curves)

NeqSim can automatically generate compressor curves from templates. This is useful when you don't have detailed vendor data.

```python
# Automatic curve generation using templates

def create_process_with_generated_curves():
    """
    Create process with automatically generated compressor curves.
    """
    gas = SystemSrkEos(288.15, 30.0)
    gas.addComponent("nitrogen", 0.02)
    gas.addComponent("methane", 0.85)
    gas.addComponent("ethane", 0.08)
    gas.addComponent("propane", 0.05)
    gas.setMixingRule("classic")
    
    process = ProcessSystem()
    
    feed = Stream("feed", gas)
    feed.setFlowRate(5000.0, "Am3/hr")
    feed.setPressure(30.0, "bara")
    feed.setTemperature(288.15, "K")
    process.add(feed)
    
    compressor = Compressor("compressor", feed)
    compressor.setOutletPressure(80.0)
    process.add(compressor)
    
    # ========================================
    # AUTOMATIC CURVE GENERATION
    # ========================================
    
    # Create chart generator
    generator = CompressorChartGenerator(compressor)
    
    # Available templates:
    # - "BASIC_CENTRIFUGAL": General purpose
    # - "PIPELINE": High efficiency for pipeline compression
    # - "PROCESS": Process gas compression
    # - "HIGH_RATIO": High pressure ratio applications
    # - "LOW_FLOW": Low flow applications
    # - "HIGH_SPEED": High speed (>15000 RPM)
    # - "OIL_GAS": Offshore/oil & gas applications
    
    # Generate chart from template
    # Parameters: (template_name, number_of_speed_curves)
    chart = generator.generateFromTemplate("PIPELINE", 5)
    
    # Set the generated chart on the compressor
    compressor.setCompressorChart(chart)
    
    # Set operating speed (within the generated range)
    compressor.setSpeed(10000)  # RPM
    compressor.setUsePolytropicCalc(True)
    
    aftercooler = Cooler("aftercooler", compressor.getOutletStream())
    aftercooler.setOutTemperature(313.15)
    process.add(aftercooler)
    
    process.run()
    return process

# Create process with auto-generated curves
process_auto = create_process_with_generated_curves()

comp_auto = process_auto.getUnit("compressor")
chart_auto = comp_auto.getCompressorChart()

print("=== Process with Auto-Generated Compressor Curves ===")
print(f"Flow rate: {comp_auto.getInletStream().getFlowRate('Am3/hr'):.0f} Am3/hr")
print(f"Speed: {comp_auto.getSpeed():.0f} RPM")
print(f"Polytropic head: {comp_auto.getPolytropicHead('kJ/kg'):.1f} kJ/kg")
print(f"Polytropic efficiency: {comp_auto.getPolytropicEfficiency()*100:.1f} %")
print(f"Power: {comp_auto.getPower('kW'):.1f} kW")

# Check operating limits with generated curves
flow_auto = comp_auto.getInletStream().getFlowRate("Am3/hr")
head_auto = comp_auto.getPolytropicHead("kJ/kg")

print(f"\nOperating limits:")
print(f"  In surge: {chart_auto.getSurgeCurve().isSurge(head_auto, flow_auto)}")
print(f"  Is choked: {chart_auto.getStoneWallCurve().isStoneWall(head_auto, flow_auto)}")
```

### 6.2.3 Multi-Map MW Interpolation for Varying Gas Composition

When gas composition varies, use `CompressorChartMWInterpolation` to interpolate between maps measured at different molecular weights.

```python
# Using MW interpolation charts for varying gas composition

CompressorChartMWInterpolation = jneqsim.process.equipment.compressor.CompressorChartMWInterpolation

def create_process_with_mw_chart():
    """
    Create process with multi-MW interpolation chart.
    """
    # Create gas with specific composition
    gas = SystemSrkEos(288.15, 30.0)
    gas.addComponent("nitrogen", 0.02)
    gas.addComponent("methane", 0.82)  # MW will be ~18 g/mol
    gas.addComponent("ethane", 0.10)
    gas.addComponent("propane", 0.06)
    gas.setMixingRule("classic")
    
    process = ProcessSystem()
    
    feed = Stream("feed", gas)
    feed.setFlowRate(4500.0, "Am3/hr")
    feed.setPressure(30.0, "bara")
    feed.setTemperature(288.15, "K")
    process.add(feed)
    
    compressor = Compressor("compressor", feed)
    compressor.setOutletPressure(80.0)
    process.add(compressor)
    
    # ========================================
    # MULTI-MW INTERPOLATION CHART
    # ========================================
    
    chart = CompressorChartMWInterpolation()
    chart.setHeadUnit("kJ/kg")
    chart.setAutoGenerateSurgeCurves(True)
    chart.setAutoGenerateStoneWallCurves(True)
    
    # Define speeds (common for all MW maps)
    speeds = JArray(JDouble)([9000, 10000, 11000, 12000])
    
    # Chart conditions
    chart_conditions = JArray(JDouble)([25.0, 30.0, 25.0, 18.0])
    
    # === MAP AT MW = 16 g/mol (lighter gas, e.g., high methane) ===
    flow_16 = [
        JArray(JDouble)([3200, 3700, 4200, 4700, 5200]),
        JArray(JDouble)([3500, 4000, 4500, 5000, 5500]),
        JArray(JDouble)([3800, 4300, 4800, 5300, 5800]),
        JArray(JDouble)([4100, 4600, 5100, 5600, 6100])
    ]
    head_16 = [
        JArray(JDouble)([100, 96, 90, 82, 72]),
        JArray(JDouble)([115, 111, 104, 95, 84]),
        JArray(JDouble)([132, 127, 119, 109, 96]),
        JArray(JDouble)([151, 145, 136, 125, 110])
    ]
    eff_16 = [
        JArray(JDouble)([76, 79, 81, 79, 75]),
        JArray(JDouble)([77, 80, 82, 80, 76]),
        JArray(JDouble)([76, 79, 81, 79, 75]),
        JArray(JDouble)([75, 78, 80, 78, 74])
    ]
    
    flow_16_arr = JArray(JArray(JDouble))(flow_16)
    head_16_arr = JArray(JArray(JDouble))(head_16)
    eff_16_arr = JArray(JArray(JDouble))(eff_16)
    
    chart.addMapAtMW(16.0, chart_conditions, speeds, flow_16_arr, head_16_arr, eff_16_arr)
    
    # === MAP AT MW = 20 g/mol (heavier gas, more C2/C3) ===
    flow_20 = [
        JArray(JDouble)([2900, 3400, 3900, 4400, 4900]),
        JArray(JDouble)([3200, 3700, 4200, 4700, 5200]),
        JArray(JDouble)([3500, 4000, 4500, 5000, 5500]),
        JArray(JDouble)([3800, 4300, 4800, 5300, 5800])
    ]
    head_20 = [
        JArray(JDouble)([88, 85, 80, 73, 64]),
        JArray(JDouble)([102, 98, 92, 84, 74]),
        JArray(JDouble)([117, 113, 106, 97, 85]),
        JArray(JDouble)([134, 129, 121, 111, 98])
    ]
    eff_20 = [
        JArray(JDouble)([74, 77, 79, 77, 73]),
        JArray(JDouble)([75, 78, 80, 78, 74]),
        JArray(JDouble)([74, 77, 79, 77, 73]),
        JArray(JDouble)([73, 76, 78, 76, 72])
    ]
    
    flow_20_arr = JArray(JArray(JDouble))(flow_20)
    head_20_arr = JArray(JArray(JDouble))(head_20)
    eff_20_arr = JArray(JArray(JDouble))(eff_20)
    
    chart.addMapAtMW(20.0, chart_conditions, speeds, flow_20_arr, head_20_arr, eff_20_arr)
    
    # Apply chart to compressor
    compressor.setCompressorChart(chart)
    compressor.setSpeed(10500)
    compressor.setUsePolytropicCalc(True)
    
    aftercooler = Cooler("aftercooler", compressor.getOutletStream())
    aftercooler.setOutTemperature(313.15)
    process.add(aftercooler)
    
    process.run()
    return process

# Create process with MW interpolation
process_mw = create_process_with_mw_chart()

comp_mw = process_mw.getUnit("compressor")
chart_mw = comp_mw.getCompressorChart()

# Get actual MW from the fluid
fluid = comp_mw.getInletStream().getFluid()
actual_mw = fluid.getMolarMass() * 1000  # Convert to g/mol

print("=== Multi-MW Interpolation Chart ===")
print(f"Actual gas MW: {actual_mw:.1f} g/mol")
print(f"(Chart interpolates between MW=16 and MW=20 maps)")
print(f"\nOperating point:")
print(f"  Flow rate: {comp_mw.getInletStream().getFlowRate('Am3/hr'):.0f} Am3/hr")
print(f"  Speed: {comp_mw.getSpeed():.0f} RPM")
print(f"  Polytropic head: {comp_mw.getPolytropicHead('kJ/kg'):.1f} kJ/kg")
print(f"  Polytropic efficiency: {comp_mw.getPolytropicEfficiency()*100:.1f} %")
print(f"  Power: {comp_mw.getPower('kW'):.1f} kW")
```

### Summary: Compressor Curve Constraints

| Constraint Type | Method | Description |
|----------------|--------|-------------|
| **Surge limit** | `getSurgeCurve().isSurge(head, flow)` | Returns True if in surge region |
| **Stone wall (choke)** | `getStoneWallCurve().isStoneWall(head, flow)` | Returns True if choked |
| **Surge flow** | `getSurgeCurve().getSurgeFlow(head)` | Minimum flow at given head |
| **Stone wall flow** | `getStoneWallCurve().getStoneWallFlow(head)` | Maximum flow at given head |
| **Surge margin** | `(flow - surge_flow) / surge_flow` | Percent above surge line |

### Key Classes

| Class | Use Case |
|-------|----------|
| `CompressorChart` | Standard multi-speed performance maps |
| `CompressorChartGenerator` | Auto-generate curves from templates |
| `CompressorChartMWInterpolation` | Multiple maps at different MWs |
| `CompressorChartKhader2015` | Automatic MW correction using sound speed scaling |

```python
# Optimize with constraints using SLSQP
constrained_problem.eval_count = 0

result_slsqp = optimize.minimize(
    constrained_problem,
    x0=constrained_problem.get_x0(),
    method='SLSQP',
    bounds=constrained_problem.get_bounds(),
    constraints=constrained_problem.get_scipy_constraints(),
    options={'maxiter': 100, 'disp': True}
)

print("\n=== Constrained Optimization Results (SLSQP) ===")
print(f"Success: {result_slsqp.success}")
print(f"Function evaluations: {constrained_problem.eval_count}")
print(f"\nOptimal values:")
print(f"  Intermediate pressure: {result_slsqp.x[0]:.2f} bara")
print(f"  Intercooler temperature: {result_slsqp.x[1] - 273.15:.1f} °C")
print(f"\nMinimum total power: {result_slsqp.fun:.1f} kW")

# Check constraints at optimum
print("\nConstraint values at optimum (positive = satisfied):")
constraint_values = constrained_problem.evaluate_constraints(result_slsqp.x)
for i, spec in enumerate(constraint_specs):
    status = "✓" if constraint_values[i] >= 0 else "✗"
    print(f"  {status} {spec['name']}: {constraint_values[i]:.2f}")
```

## 7. Multi-Objective with Pareto

For multi-objective optimization, we can use the weighted-sum method to generate Pareto points.

```python
# Define two objectives: minimize power, maximize throughput

def power_per_kg(process):
    """Power consumption per unit throughput (kW per 1000 kg/hr)."""
    total_power = (process.getUnit('stage1').getPower('kW') +
                   process.getUnit('stage2').getPower('kW'))
    flow = process.getUnit('feed').getFlowRate('kg/hr')
    return total_power / (flow / 1000.0)

def negative_throughput(process):
    """Negative throughput (for minimization)."""
    return -process.getUnit('aftercooler').getOutletStream().getFlowRate('kg/hr')

# Variables: flow rate and intermediate pressure
pareto_variables = [
    {
        'name': 'flow_rate',
        'min': 20000.0,
        'max': 100000.0,
        'setter': lambda proc, val: proc.getUnit('feed').setFlowRate(val, 'kg/hr')
    },
    {
        'name': 'intermediate_pressure',
        'min': 40.0,
        'max': 80.0,
        'setter': lambda proc, val: proc.getUnit('stage1').setOutletPressure(val)
    }
]

print("Multi-objective problem defined:")
print("  Objective 1: Minimize specific power (kW/1000 kg/hr)")
print("  Objective 2: Maximize throughput (kg/hr)")
```

```python
# Generate Pareto front using weighted-sum method

def weighted_objective(weights, objectives):
    """Create weighted-sum objective function."""
    def combined(process):
        values = [obj(process) for obj in objectives]
        return sum(w * v for w, v in zip(weights, values))
    return combined

# Weight combinations
n_points = 11
weight_sets = [(w, 1-w) for w in np.linspace(0, 1, n_points)]

pareto_points = []

print("Generating Pareto front...")
for i, weights in enumerate(weight_sets):
    # Normalize weights (since objectives have different scales)
    w1 = weights[0] * 100  # Scale specific power weight
    w2 = weights[1] * 0.001  # Scale throughput weight
    
    # Create problem with weighted objective
    combined_obj = weighted_objective(
        [w1, w2],
        [power_per_kg, negative_throughput]
    )
    
    problem_pareto = NeqSimOptimizationProblem(
        process_factory=create_gas_process,
        variable_specs=pareto_variables,
        objective_func=combined_obj
    )
    
    # Optimize
    result = optimize.minimize(
        problem_pareto,
        x0=problem_pareto.get_x0(),
        method='Powell',
        bounds=problem_pareto.get_bounds(),
        options={'maxiter': 50, 'disp': False}
    )
    
    # Evaluate both objectives at optimum
    problem_pareto.evaluate(result.x)
    spec_power = power_per_kg(problem_pareto.process)
    throughput = problem_pareto.process.getUnit('aftercooler').getOutletStream().getFlowRate('kg/hr')
    
    pareto_points.append({
        'weights': weights,
        'x': result.x,
        'specific_power': spec_power,
        'throughput': throughput
    })
    
    print(f"  Point {i+1}/{n_points}: w={weights[0]:.1f}, Power={spec_power:.2f}, Throughput={throughput:.0f}")

print("\nPareto front generated!")
```

```python
# Plot Pareto front
throughputs = [p['throughput'] for p in pareto_points]
spec_powers = [p['specific_power'] for p in pareto_points]

plt.figure(figsize=(10, 6))
plt.scatter(throughputs, spec_powers, c='blue', s=100, zorder=5)
plt.plot(throughputs, spec_powers, 'b--', alpha=0.5, zorder=4)

# Annotate some points
for i in [0, n_points//2, n_points-1]:
    plt.annotate(f'w={pareto_points[i]["weights"][0]:.1f}',
                 xy=(throughputs[i], spec_powers[i]),
                 xytext=(10, 10), textcoords='offset points')

plt.xlabel('Throughput (kg/hr)', fontsize=12)
plt.ylabel('Specific Power (kW per 1000 kg/hr)', fontsize=12)
plt.title('Pareto Front: Throughput vs Specific Power', fontsize=14)
plt.grid(True, alpha=0.3)
plt.tight_layout()
plt.show()
```

## 8. Global Optimization

For problems with multiple local optima, use global optimizers like `differential_evolution`.

```python
# Global optimization with Differential Evolution

# Create a more complex problem with 3 variables
global_variables = [
    {
        'name': 'flow_rate',
        'min': 30000.0,
        'max': 80000.0,
        'setter': lambda proc, val: proc.getUnit('feed').setFlowRate(val, 'kg/hr')
    },
    {
        'name': 'intermediate_pressure',
        'min': 40.0,
        'max': 85.0,
        'setter': lambda proc, val: proc.getUnit('stage1').setOutletPressure(val)
    },
    {
        'name': 'intercooler_temp',
        'min': 293.15,
        'max': 323.15,
        'setter': lambda proc, val: proc.getUnit('intercooler').setOutTemperature(val)
    }
]

global_problem = NeqSimOptimizationProblem(
    process_factory=create_gas_process,
    variable_specs=global_variables,
    objective_func=total_power_objective
)

print("Running Differential Evolution (global optimizer)...")
print("This may take a minute...\n")

result_de = optimize.differential_evolution(
    global_problem,
    bounds=global_problem.get_bounds(),
    maxiter=30,
    popsize=5,  # Small population for faster demo
    mutation=(0.5, 1.0),
    recombination=0.7,
    seed=42,
    disp=True,
    polish=True  # Use local optimization at the end
)

print("\n=== Differential Evolution Results ===")
print(f"Success: {result_de.success}")
print(f"Function evaluations: {result_de.nfev}")
print(f"\nOptimal values:")
for i, var in enumerate(global_variables):
    if 'temp' in var['name'].lower():
        print(f"  {var['name']}: {result_de.x[i] - 273.15:.1f} °C")
    else:
        print(f"  {var['name']}: {result_de.x[i]:.1f}")
print(f"\nMinimum total power: {result_de.fun:.1f} kW")
```

## 9. Gradient-Based Optimization

For smooth problems, gradient-based methods can be more efficient. We can estimate gradients numerically.

```python
# Gradient-based optimization with L-BFGS-B

problem.eval_count = 0

result_lbfgs = optimize.minimize(
    problem,
    x0=problem.get_x0(),
    method='L-BFGS-B',
    bounds=problem.get_bounds(),
    options={
        'maxiter': 100,
        'disp': True,
        'eps': 0.1  # Step size for numerical gradient
    }
)

print("\n=== L-BFGS-B Results ===")
print(f"Success: {result_lbfgs.success}")
print(f"Function evaluations: {problem.eval_count}")
print(f"Optimal intermediate pressure: {result_lbfgs.x[0]:.2f} bara")
print(f"Optimal intercooler temp: {result_lbfgs.x[1] - 273.15:.1f} °C")
print(f"Minimum total power: {result_lbfgs.fun:.1f} kW")
```

```python
# Custom gradient estimation with central differences

def estimate_gradient(problem, x, eps=0.1):
    """
    Estimate gradient using central differences.
    
    Parameters:
    - problem: Optimization problem
    - x: Point at which to evaluate gradient
    - eps: Step size
    
    Returns:
    - Gradient vector
    """
    n = len(x)
    grad = np.zeros(n)
    
    for i in range(n):
        x_plus = x.copy()
        x_minus = x.copy()
        x_plus[i] += eps
        x_minus[i] -= eps
        
        f_plus = problem(x_plus)
        f_minus = problem(x_minus)
        
        grad[i] = (f_plus - f_minus) / (2 * eps)
    
    return grad

# Estimate gradient at initial point
x0 = problem.get_x0()
grad = estimate_gradient(problem, x0, eps=1.0)

print(f"Gradient at initial point {x0}:")
print(f"  d(Power)/d(P_inter) = {grad[0]:.2f} kW/bara")
print(f"  d(Power)/d(T_inter) = {grad[1]:.2f} kW/K")
print(f"\nInterpretation:")
print(f"  {'Increase' if grad[0] < 0 else 'Decrease'} P_inter to reduce power")
print(f"  {'Increase' if grad[1] < 0 else 'Decrease'} T_inter to reduce power")
```

## 10. Best Practices

### Algorithm Selection Guide

| Problem Type | Recommended Algorithm | SciPy Function |
|--------------|----------------------|----------------|
| Smooth, unconstrained | L-BFGS-B | `minimize(..., method='L-BFGS-B')` |
| Smooth, constrained | SLSQP, trust-constr | `minimize(..., method='SLSQP')` |
| Non-smooth, low dimension | Nelder-Mead, Powell | `minimize(..., method='Nelder-Mead')` |
| Many local optima | Differential Evolution | `differential_evolution(...)` |
| Black-box, noisy | COBYLA, Nelder-Mead | `minimize(..., method='COBYLA')` |
| Multi-objective | Weighted sum, pymoo | Custom or pymoo.optimize |

### Tips for Success

```python
# Best practice: Complete optimization workflow

def optimize_neqsim_process(
    process_factory,
    variables,
    objective,
    constraints=None,
    method='Powell',
    maxiter=100
):
    """
    Complete workflow for optimizing a NeqSim process.
    
    Parameters:
    - process_factory: Callable that creates a ProcessSystem
    - variables: List of variable specifications
    - objective: Callable(process) -> float to minimize
    - constraints: Optional list of constraint specifications
    - method: Optimization algorithm
    - maxiter: Maximum iterations
    
    Returns:
    - Dictionary with results
    """
    # Create problem
    if constraints:
        problem = ConstrainedOptimizationProblem(
            process_factory, variables, objective, constraints
        )
        scipy_constraints = problem.get_scipy_constraints()
    else:
        problem = NeqSimOptimizationProblem(
            process_factory, variables, objective
        )
        scipy_constraints = None
    
    # Run optimization
    result = optimize.minimize(
        problem,
        x0=problem.get_x0(),
        method=method,
        bounds=problem.get_bounds(),
        constraints=scipy_constraints,
        options={'maxiter': maxiter, 'disp': False}
    )
    
    # Package results
    return {
        'success': result.success,
        'optimal_values': dict(zip([v['name'] for v in variables], result.x)),
        'objective': result.fun,
        'evaluations': problem.eval_count,
        'process': problem.process
    }

# Example usage
results = optimize_neqsim_process(
    process_factory=create_gas_process,
    variables=variable_specs,
    objective=total_power_objective,
    method='Powell'
)

print("=== Optimization Results ===")
print(f"Success: {results['success']}")
print(f"Evaluations: {results['evaluations']}")
print(f"\nOptimal values:")
for name, value in results['optimal_values'].items():
    print(f"  {name}: {value:.2f}")
print(f"\nObjective: {results['objective']:.1f} kW")
```

### Common Pitfalls and Solutions

| Problem | Solution |
|---------|----------|
| Simulation fails for some x | Return large penalty value |
| Process state carries over | Create fresh process each evaluation |
| Slow convergence | Normalize variables to similar scales |
| Local optima | Use global optimizer first, then polish |
| Constraint violations | Use penalty method or constrained optimizer |

### Key Takeaways

1. **Wrap NeqSim in a callable** that SciPy can optimize
2. **Create fresh process** each evaluation to avoid state issues
3. **Handle failures gracefully** with penalty values
4. **Choose algorithm** based on problem characteristics
5. **Validate results** by checking constraints and physical feasibility

## Summary

This notebook demonstrated:

✅ **Creating a wrapper class** for NeqSim process optimization  
✅ **Using SciPy optimizers** (Nelder-Mead, Powell, SLSQP, L-BFGS-B)  
✅ **Handling equipment constraints** with constraint functions  
✅ **Multi-objective optimization** with weighted-sum Pareto  
✅ **Global optimization** with differential evolution  
✅ **Gradient estimation** for gradient-based methods  

### Related Documentation

- [ProductionOptimizer Tutorial](ProductionOptimizer_Tutorial.ipynb) - NeqSim's built-in optimizer
- [External Optimizer Integration](../integration/EXTERNAL_OPTIMIZER_INTEGRATION.md) - ProcessSimulationEvaluator API
- [Optimization Overview](../process/optimization/OPTIMIZATION_OVERVIEW.md) - All optimization options

