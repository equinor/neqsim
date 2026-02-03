---
title: Derivatives and Gradients in NeqSim
description: NeqSim provides two complementary approaches for computing derivatives of simulation results. This guide covers both methods with mathematical background, usage examples, and guidance on when to use e...
---

# Derivatives and Gradients in NeqSim

NeqSim provides two complementary approaches for computing derivatives of simulation results. This guide covers both methods with mathematical background, usage examples, and guidance on when to use each approach.

## Overview

| Method | Package | Use Case | Accuracy | Performance |
|--------|---------|----------|----------|-------------|
| **Thermodynamic Derivatives** | `neqsim.thermo.util.derivatives` | Flash results, property gradients | Analytical/semi-analytical | O(n³) per flash |
| **Process Derivatives** | `neqsim.process.mpc` | Full flowsheet Jacobians | Numerical (finite difference) | O(n) process runs |

---

## 1. Thermodynamic Property Derivatives

### 1.1 Mathematical Background

#### The Implicit Function Theorem

Flash calculations solve a nonlinear system of equations iteratively. Rather than differentiating through the solver (which is complex and numerically unstable), NeqSim uses the **implicit function theorem** to obtain exact gradients at the converged solution.

At vapor-liquid equilibrium, the residual equations $F(y; \theta) = 0$ are satisfied, where:
- $y = (K_1, \ldots, K_n, \beta)$ are solution variables (K-values and vapor fraction)
- $\theta = (T, P, z)$ are input parameters (temperature, pressure, composition)

By the implicit function theorem:

$$\frac{dy}{d\theta} = -\left(\frac{\partial F}{\partial y}\right)^{-1} \frac{\partial F}{\partial \theta}$$

This gives **exact gradients** at the converged solution without approximation.

#### Equilibrium Equations

For each component $i$, the equilibrium condition is:

$$F_i = \ln K_i + \ln \phi_i^L - \ln \phi_i^V = 0$$

The material balance (Rachford-Rice equation) closes the system:

$$F_{n_c+1} = \sum_{i=1}^{n_c} \frac{z_i(K_i - 1)}{1 + \beta(K_i - 1)} = 0$$

#### Fugacity Coefficient Derivatives

NeqSim's equations of state (SRK, PR, CPA, etc.) provide analytical derivatives of fugacity coefficients:

- $\frac{\partial \ln \phi_i}{\partial T}$ — temperature sensitivity
- $\frac{\partial \ln \phi_i}{\partial P}$ — pressure sensitivity  
- $\frac{\partial \ln \phi_i}{\partial n_j}$ — composition sensitivity

These are computed when calling `system.init(3)`, which `DifferentiableFlash` does automatically.

### 1.2 Key Classes

#### DifferentiableFlash

The main entry point for computing thermodynamic gradients.

```java
import neqsim.thermo.util.derivatives.DifferentiableFlash;
import neqsim.thermo.util.derivatives.FlashGradients;
import neqsim.thermo.util.derivatives.PropertyGradient;

// After running a flash calculation
DifferentiableFlash diffFlash = new DifferentiableFlash(system);

// Get flash variable gradients (K-values, vapor fraction)
FlashGradients grads = diffFlash.computeFlashGradients();

// Get property gradients (density, enthalpy, etc.)
PropertyGradient densityGrad = diffFlash.computePropertyGradient("density");
```

#### FlashGradients

Container for derivatives of flash variables:

```java
FlashGradients grads = diffFlash.computeFlashGradients();

// K-value derivatives
double[] dKdT = grads.getDKdT();    // ∂K_i/∂T for all components [1/K]
double[] dKdP = grads.getDKdP();    // ∂K_i/∂P for all components [1/bar]
double[][] dKdz = grads.getDKdz();  // ∂K_i/∂z_j composition matrix

// Vapor fraction derivatives  
double dBetadT = grads.getDBetadT();  // ∂β/∂T [1/K]
double dBetadP = grads.getDBetadP();  // ∂β/∂P [1/bar]
double[] dBetadz = grads.getDBetadz(); // ∂β/∂z_i for each component

// Validity check
if (grads.isValid()) {
    // Use gradients
}
```

#### PropertyGradient

Container for derivatives of scalar thermodynamic properties:

```java
PropertyGradient grad = diffFlash.computePropertyGradient("density");

// Access derivatives
double value = grad.getValue();                    // Current property value
double dT = grad.getDerivativeWrtTemperature();    // ∂property/∂T
double dP = grad.getDerivativeWrtPressure();       // ∂property/∂P
double[] dz = grad.getDerivativeWrtComposition();  // ∂property/∂z_i

// Convenience methods
double dRho_dMethane = grad.getDerivativeWrtComponent(0);
String unit = grad.getUnit();
String[] components = grad.getComponentNames();

// Directional derivative
double delta = grad.directionalDerivative(deltaT, deltaP, deltaZ);

// Export as array [dT, dP, dz_0, dz_1, ...]
double[] gradArray = grad.toArray();
```

#### FugacityJacobian

Low-level access to fugacity coefficient derivatives:

```java
// Extract from phase (0=liquid, 1=vapor)
FugacityJacobian jacV = diffFlash.extractFugacityJacobian(1);

double[] lnPhi = jacV.getLnPhi();           // ln(φ_i)
double[] dlnPhidT = jacV.getDlnPhidT();     // ∂ln(φ_i)/∂T
double[] dlnPhidP = jacV.getDlnPhidP();     // ∂ln(φ_i)/∂P
double[][] dlnPhidn = jacV.getDlnPhidn();   // ∂ln(φ_i)/∂n_j
```

### 1.3 Supported Properties

| Property | Name | Unit | Description |
|----------|------|------|-------------|
| Density | `"density"` | kg/m³ | Mixture mass density |
| Enthalpy | `"enthalpy"` | J/mol | Mixture molar enthalpy |
| Entropy | `"entropy"` | J/mol/K | Mixture molar entropy |
| Heat capacity (Cp) | `"Cp"` | J/mol/K | Isobaric heat capacity |
| Heat capacity (Cv) | `"Cv"` | J/mol/K | Isochoric heat capacity |
| Compressibility | `"compressibility"` or `"Z"` | - | Z-factor |
| Molar volume | `"molarvolume"` | m³/mol | Mixture molar volume |
| Molar mass | `"molarmass"` | kg/mol | Mixture molar mass |
| Viscosity | `"viscosity"` | kg/m/s | Dynamic viscosity |
| Thermal conductivity | `"thermalconductivity"` | W/m/K | Thermal conductivity |
| Sound speed | `"soundspeed"` | m/s | Speed of sound |
| Joule-Thomson | `"joulethomson"` | K/bar | Joule-Thomson coefficient |
| Kappa (Cp/Cv) | `"kappa"` or `"cpcvratio"` | - | Heat capacity ratio |
| Gamma | `"gamma"` | - | Isentropic exponent |
| Gibbs energy | `"gibbsenergy"` | J/mol | Gibbs free energy |
| Internal energy | `"internalenergy"` | J/mol | Internal energy |
| Vapor fraction | `"beta"` or `"vaporfraction"` | - | Molar vapor fraction |

### 1.4 Complete Example

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.util.derivatives.DifferentiableFlash;
import neqsim.thermo.util.derivatives.FlashGradients;
import neqsim.thermo.util.derivatives.PropertyGradient;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class DifferentiableFlashExample {
    public static void main(String[] args) {
        // 1. Create and flash a system
        SystemInterface system = new SystemSrkEos(300.0, 50.0);
        system.addComponent("methane", 0.8);
        system.addComponent("ethane", 0.15);
        system.addComponent("propane", 0.05);
        system.setMixingRule("classic");

        ThermodynamicOperations ops = new ThermodynamicOperations(system);
        ops.TPflash();

        // 2. Create differentiable flash wrapper
        DifferentiableFlash diffFlash = new DifferentiableFlash(system);

        // 3. Compute flash gradients
        FlashGradients flashGrads = diffFlash.computeFlashGradients();
        
        if (flashGrads.isValid()) {
            System.out.println("Vapor fraction = " + system.getBeta());
            System.out.println("∂β/∂T = " + flashGrads.getDBetadT() + " 1/K");
            System.out.println("∂β/∂P = " + flashGrads.getDBetadP() + " 1/bar");
            
            double[] dKdT = flashGrads.getDKdT();
            System.out.println("∂K_methane/∂T = " + dKdT[0] + " 1/K");
        }

        // 4. Compute property gradients
        PropertyGradient densityGrad = diffFlash.computePropertyGradient("density");
        System.out.println("\nDensity = " + densityGrad.getValue() + " " + densityGrad.getUnit());
        System.out.println("∂ρ/∂T = " + densityGrad.getDerivativeWrtTemperature() + " kg/m³/K");
        System.out.println("∂ρ/∂P = " + densityGrad.getDerivativeWrtPressure() + " kg/m³/bar");

        PropertyGradient cpGrad = diffFlash.computePropertyGradient("Cp");
        System.out.println("\nCp = " + cpGrad.getValue() + " " + cpGrad.getUnit());
        System.out.println("∂Cp/∂T = " + cpGrad.getDerivativeWrtTemperature() + " J/mol/K²");
    }
}
```

---

## 2. Process System Derivatives

### 2.1 Mathematical Background

#### Finite Difference Methods

For complex process flowsheets where analytical derivatives are not available, `ProcessDerivativeCalculator` uses numerical differentiation.

**Forward Difference** (first-order accurate):
$$\frac{\partial f}{\partial x} \approx \frac{f(x + h) - f(x)}{h}$$

**Central Difference** (second-order accurate, default):
$$\frac{\partial f}{\partial x} \approx \frac{f(x + h) - f(x - h)}{2h}$$

**Second-Order Central Difference** (with error estimation):
$$\frac{\partial f}{\partial x} \approx \frac{-f(x+2h) + 8f(x+h) - 8f(x-h) + f(x-2h)}{12h}$$

#### Step Size Selection

The step size $h$ balances two competing errors:
- **Truncation error**: Decreases as $h \to 0$
- **Round-off error**: Increases as $h \to 0$ due to floating-point arithmetic

Optimal step size is typically $h \approx \sqrt{\epsilon_{\text{machine}}} \cdot |x| \approx 10^{-8} \cdot |x|$.

`ProcessDerivativeCalculator` uses **adaptive step sizing** based on variable type:

| Variable Type | Typical Range | Default Step |
|---------------|---------------|--------------|
| Pressure | 1-1000 bar | 0.01% relative |
| Temperature | 200-600 K | 0.01% relative |
| Flow rate | varies | 0.01% relative |
| Composition | 0-1 | 0.0001 absolute |
| Level | 0-1 | 0.001 absolute |

### 2.2 Key Classes

#### ProcessDerivativeCalculator

```java
import neqsim.process.mpc.ProcessDerivativeCalculator;

// Create calculator
ProcessDerivativeCalculator calc = new ProcessDerivativeCalculator(processSystem);

// Define input variables (manipulated variables)
calc.addInputVariable("Feed.flowRate", "kg/hr");
calc.addInputVariable("Heater.outTemperature", "K");

// Define output variables (controlled variables)
calc.addOutputVariable("Separator.gasOutStream.flowRate", "kg/hr");
calc.addOutputVariable("Separator.liquidLevel", "fraction");

// Calculate full Jacobian matrix
double[][] jacobian = calc.calculateJacobian();
// jacobian[i][j] = ∂output_i/∂input_j
```

#### Configuration Options

```java
// Set derivative method
calc.setMethod(ProcessDerivativeCalculator.DerivativeMethod.CENTRAL_DIFFERENCE);

// Custom step size for specific variable
calc.addInputVariable("Feed.pressure", "bara", 0.01); // 0.01 bar step

// Enable parallel computation (for many inputs)
calc.setParallelEnabled(true);
calc.setNumThreads(8);

// Set relative step size (default 1e-4)
calc.setRelativeStepSize(1e-5);
```

#### Fluent API

```java
double[][] jacobian = new ProcessDerivativeCalculator(process)
    .addInputVariable("Feed.flowRate", "kg/hr")
    .addInputVariable("Feed.pressure", "bara")
    .addOutputVariable("Product.temperature", "K")
    .addOutputVariable("Product.flowRate", "kg/hr")
    .setMethod(ProcessDerivativeCalculator.DerivativeMethod.CENTRAL_DIFFERENCE)
    .calculateJacobian();
```

### 2.3 Variable Path Syntax

Variables are accessed using dot notation: `"UnitName.propertyName"`

Common patterns:
- `"Feed.flowRate"` — stream flow rate
- `"Feed.pressure"` — stream pressure  
- `"Feed.temperature"` — stream temperature
- `"Heater.outTemperature"` — heater outlet temperature
- `"Separator.gasOutStream.flowRate"` — separator gas outlet flow
- `"Separator.liquidLevel"` — separator liquid level

### 2.4 Complete Example

```java
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.mpc.ProcessDerivativeCalculator;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class ProcessDerivativeExample {
    public static void main(String[] args) {
        // 1. Build process flowsheet
        SystemInterface feed = new SystemSrkEos(300.0, 50.0);
        feed.addComponent("methane", 0.8);
        feed.addComponent("ethane", 0.15);
        feed.addComponent("propane", 0.05);
        feed.setMixingRule("classic");

        ProcessSystem process = new ProcessSystem();

        Stream feedStream = new Stream("Feed", feed);
        feedStream.setFlowRate(1000.0, "kg/hr");
        process.add(feedStream);

        Heater heater = new Heater("Heater", feedStream);
        heater.setOutTemperature(350.0, "K");
        process.add(heater);

        Separator separator = new Separator("Separator", heater.getOutletStream());
        process.add(separator);

        process.run();

        // 2. Create derivative calculator
        ProcessDerivativeCalculator calc = new ProcessDerivativeCalculator(process);

        // 3. Define inputs (what we can manipulate)
        calc.addInputVariable("Feed.flowRate", "kg/hr");
        calc.addInputVariable("Heater.outTemperature", "K");

        // 4. Define outputs (what we want to control/observe)
        calc.addOutputVariable("Separator.gasOutStream.flowRate", "kg/hr");
        calc.addOutputVariable("Separator.gasOutStream.temperature", "K");

        // 5. Calculate Jacobian
        double[][] J = calc.calculateJacobian();

        System.out.println("Jacobian matrix (∂outputs/∂inputs):");
        System.out.println("                           Feed.flowRate  Heater.outTemp");
        System.out.printf("Gas flow rate:             %12.4f  %12.4f%n", J[0][0], J[0][1]);
        System.out.printf("Gas temperature:           %12.4f  %12.4f%n", J[1][0], J[1][1]);

        // 6. Get individual derivative
        double dGasFlow_dFeedFlow = calc.getDerivative(
            "Separator.gasOutStream.flowRate", "Feed.flowRate");
        System.out.println("\n∂(gas flow)/∂(feed flow) = " + dGasFlow_dFeedFlow);
    }
}
```

---

## 3. Choosing the Right Method

| Criterion | Thermodynamic Derivatives | Process Derivatives |
|-----------|---------------------------|---------------------|
| **Scope** | Single flash calculation | Full process flowsheet |
| **Accuracy** | Exact (analytical) | Approximate (numerical) |
| **Speed** | Fast (one matrix inversion) | Slower (multiple process runs) |
| **Properties** | T, P, z dependencies only | Any input/output relationship |
| **Complexity** | Simple systems | Complex multi-unit processes |

### When to Use Thermodynamic Derivatives

- Gradient-based optimization of thermodynamic conditions
- Integration with ML frameworks (JAX, PyTorch)
- Sensitivity analysis of flash calculations
- Physics-informed neural network training
- Real-time applications requiring fast gradient computation

### When to Use Process Derivatives

- Model Predictive Control (MPC) gain matrices
- Flowsheet optimization
- Control system design
- Steady-state sensitivity analysis
- Any case involving multiple unit operations

---

## 4. Integration with Machine Learning

### 4.1 JAX Integration

```python
import jax
from jax import custom_vjp
import jax.numpy as jnp
import jpype

# Start JVM
jpype.startJVM(classpath=['neqsim.jar'])
from neqsim.thermo.system import SystemSrkEos
from neqsim.thermo.util.derivatives import DifferentiableFlash
from neqsim.thermodynamicoperations import ThermodynamicOperations

@custom_vjp
def flash_density(T, P, z):
    """JAX-differentiable flash calculation."""
    system = SystemSrkEos(float(T), float(P))
    for i, zi in enumerate(z):
        system.addComponent(f"comp_{i}", float(zi))
    system.setMixingRule("classic")
    
    ops = ThermodynamicOperations(system)
    ops.TPflash()
    
    return system.getDensity("kg/m3")

def flash_density_fwd(T, P, z):
    """Forward pass with gradient caching."""
    # Run flash
    system = create_system(T, P, z)
    ops = ThermodynamicOperations(system)
    ops.TPflash()
    
    value = system.getDensity("kg/m3")
    
    # Get analytical gradients
    diff_flash = DifferentiableFlash(system)
    grads = diff_flash.computePropertyGradient("density")
    
    return value, grads

def flash_density_bwd(grads, g):
    """Backward pass using NeqSim gradients."""
    dT = g * grads.getDerivativeWrtTemperature()
    dP = g * grads.getDerivativeWrtPressure()
    dz = g * jnp.array(grads.getDerivativeWrtComposition())
    return (dT, dP, dz)

flash_density.defvjp(flash_density_fwd, flash_density_bwd)

# Now use with JAX autodiff
grad_fn = jax.grad(flash_density, argnums=(0, 1))
dT, dP = grad_fn(300.0, 50.0, jnp.array([0.8, 0.2]))
```

### 4.2 PyTorch Integration

```python
import torch
from torch.autograd import Function
import jpype

class FlashDensity(Function):
    @staticmethod
    def forward(ctx, T, P, z):
        # Run NeqSim flash
        system = create_system(T.item(), P.item(), z.numpy())
        ops = ThermodynamicOperations(system)
        ops.TPflash()
        
        value = system.getDensity("kg/m3")
        
        # Cache gradients
        diff_flash = DifferentiableFlash(system)
        grads = diff_flash.computePropertyGradient("density")
        ctx.save_for_backward(
            torch.tensor(grads.getDerivativeWrtTemperature()),
            torch.tensor(grads.getDerivativeWrtPressure()),
            torch.tensor(grads.getDerivativeWrtComposition())
        )
        
        return torch.tensor(value)
    
    @staticmethod
    def backward(ctx, grad_output):
        dT, dP, dz = ctx.saved_tensors
        return grad_output * dT, grad_output * dP, grad_output * dz

# Usage
flash_density = FlashDensity.apply
T = torch.tensor(300.0, requires_grad=True)
P = torch.tensor(50.0, requires_grad=True)
z = torch.tensor([0.8, 0.2], requires_grad=True)

rho = flash_density(T, P, z)
rho.backward()

print(f"∂ρ/∂T = {T.grad}")
print(f"∂ρ/∂P = {P.grad}")
```

---

## 5. Performance Considerations

### Thermodynamic Derivatives

1. **Complexity**: O(n³) due to matrix inversion, where n = number of components
2. **Caching**: `DifferentiableFlash` caches computed gradients—call `computeFlashGradients()` once and reuse
3. **init(3)**: Automatically called to compute fugacity derivatives; adds ~10-20% overhead to flash

### Process Derivatives

1. **Evaluations**: Central difference requires 2 process runs per input variable
2. **Parallelization**: Enable `setParallelEnabled(true)` for many inputs
3. **Step size**: Tune `relativeStepSize` if derivatives appear noisy or incorrect
4. **Caching**: Base case is cached—invalidated when variables change

---

## 6. Validation and Testing

Both methods are validated against numerical finite differences:

```java
// Validate thermodynamic gradients
double analyticalGrad = densityGrad.getDerivativeWrtTemperature();

// Finite difference check
double h = 1e-4;
system.setTemperature(T + h);
ops.TPflash();
double rhoPlus = system.getDensity("kg/m3");

system.setTemperature(T - h);
ops.TPflash();
double rhoMinus = system.getDensity("kg/m3");

double numericalGrad = (rhoPlus - rhoMinus) / (2 * h);
double ratio = analyticalGrad / numericalGrad;  // Should be ≈ 1.0

System.out.println("Analytical/Numerical ratio: " + ratio);
```

See test classes for comprehensive validation:
- `DifferentiableFlashTest.java`
- `ProcessDerivativeCalculatorTest.java`

---

## 7. See Also

- [Differentiable Thermodynamics](differentiable_thermodynamics.md) — detailed thermodynamic derivatives
- [MPC Integration Guide](../integration/mpc_integration.md) — using derivatives for control
- [AI Platform Integration](../integration/ai_platform_integration.md) — ML framework integration
- [MPC Integration Tutorial](../examples/MPC_Integration_Tutorial.ipynb) — interactive examples
