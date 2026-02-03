---
title: Differentiable Thermodynamics
description: NeqSim provides automatic differentiation capabilities for thermodynamic calculations through the `neqsim.thermo.util.derivatives` package. This enables gradient-based optimization, integration with M...
---

# Differentiable Thermodynamics

NeqSim provides automatic differentiation capabilities for thermodynamic calculations through the `neqsim.thermo.util.derivatives` package. This enables gradient-based optimization, integration with ML frameworks, and sensitivity analysis.

## Overview

The key classes are:

- **`DifferentiableFlash`** - Computes gradients of flash calculation results using the implicit function theorem
- **`FlashGradients`** - Container for K-value and phase fraction sensitivities
- **`PropertyGradient`** - Container for scalar property derivatives (density, enthalpy, Cp, etc.)
- **`FugacityJacobian`** - Jacobian matrix of fugacity coefficients

## Quick Start

### Computing Flash Gradients

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.util.derivatives.DifferentiableFlash;
import neqsim.thermo.util.derivatives.FlashGradients;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create and flash a system
SystemInterface system = new SystemSrkEos(300.0, 50.0);
system.addComponent("methane", 0.8);
system.addComponent("ethane", 0.15);
system.addComponent("propane", 0.05);
system.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(system);
ops.TPflash();

// Compute gradients (automatically calls init(3) for fugacity derivatives)
DifferentiableFlash diffFlash = new DifferentiableFlash(system);
FlashGradients grads = diffFlash.computeFlashGradients();

if (grads.isValid()) {
    // Get K-value sensitivities
    double[] dKdT = grads.getDKdT();  // dK_i/dT for all components
    double[] dKdP = grads.getDKdP();  // dK_i/dP for all components
    
    // Get vapor fraction sensitivities
    double dBetadT = grads.getDBetadT();  // dβ/dT
    double dBetadP = grads.getDBetadP();  // dβ/dP
    
    System.out.println("dK_methane/dT = " + dKdT[0] + " 1/K");
    System.out.println("dβ/dP = " + dBetadP + " 1/bar");
}
```

### Computing Property Gradients

```java
import neqsim.thermo.util.derivatives.PropertyGradient;

// Compute density gradient
PropertyGradient densityGrad = diffFlash.computePropertyGradient("density");

double dRhodT = densityGrad.getDerivativeWrtTemperature();  // d(density)/dT
double dRhodP = densityGrad.getDerivativeWrtPressure();     // d(density)/dP
double[] dRhodz = densityGrad.getDerivativeWrtComposition(); // d(density)/dz_i

System.out.println("Density = " + densityGrad.getValue() + " kg/m³");
System.out.println("dDensity/dT = " + dRhodT + " kg/m³/K");

// Compute heat capacity gradient
PropertyGradient cpGrad = diffFlash.computePropertyGradient("Cp");

double dCpdT = cpGrad.getDerivativeWrtTemperature();  // dCp/dT
double dCpdP = cpGrad.getDerivativeWrtPressure();     // dCp/dP

System.out.println("Cp = " + cpGrad.getValue() + " " + cpGrad.getUnit());
System.out.println("dCp/dT = " + dCpdT + " J/mol/K²");
```

### Accessing Fugacity Jacobian

```java
import neqsim.thermo.util.derivatives.FugacityJacobian;

// Note: computeFlashGradients() automatically calls init(3) to compute
// fugacity derivatives. If accessing the Jacobian directly, ensure
// init(3) has been called on the system first.

// Get fugacity derivatives for vapor phase
FugacityJacobian jacV = diffFlash.extractFugacityJacobian(1);

double[] lnPhi = jacV.getLnPhi();           // ln(φ_i)
double[] dlnPhidT = jacV.getDlnPhidT();     // d(ln φ_i)/dT
double[] dlnPhidP = jacV.getDlnPhidP();     // d(ln φ_i)/dP
double[][] dlnPhidn = jacV.getDlnPhidn();   // d(ln φ_i)/dn_j (composition derivatives)
```

## Integration with Python/JAX

The gradients can be used to create custom backward passes for JAX:

```python
import jax
from jax import custom_vjp
import jpype

# Start JVM and import NeqSim classes
jpype.startJVM(classpath=['neqsim.jar'])
from neqsim.thermo.system import SystemSrkEos
from neqsim.thermo.util.derivatives import DifferentiableFlash
from neqsim.thermodynamicoperations import ThermodynamicOperations

@custom_vjp
def flash_density(T, P, z):
    """JAX-differentiable flash calculation returning density."""
    system = create_system(z)
    system.setTemperature(float(T))
    system.setPressure(float(P))
    
    ops = ThermodynamicOperations(system)
    ops.TPflash()
    system.initProperties()  # Required for volume-corrected density
    
    return system.getDensity("kg/m3")

def flash_density_fwd(T, P, z):
    """Forward pass: compute density and cache gradients."""
    value = flash_density(T, P, z)
    
    # Get analytical gradients from NeqSim
    diff_flash = DifferentiableFlash(system)
    grads = diff_flash.computePropertyGradient("density")
    
    return value, grads

def flash_density_bwd(grads, g):
    """Backward pass: use NeqSim's analytical gradients."""
    dT = g * grads.getDerivativeWrtTemperature()
    dP = g * grads.getDerivativeWrtPressure()
    dz = g * jnp.array(grads.getDerivativeWrtComposition())
    return (dT, dP, dz)

flash_density.defvjp(flash_density_fwd, flash_density_bwd)

# Now you can use JAX's grad!
grad_fn = jax.grad(flash_density, argnums=(0, 1))
dT, dP = grad_fn(300.0, 50.0, z)
```

## Mathematical Background

### Implicit Function Theorem

The key insight is that we don't need to differentiate through the iterative flash solver. At equilibrium, the residual equations $F(y; \theta) = 0$ are satisfied, where:
- $y = (K_1, \ldots, K_n, \beta)$ are solution variables
- $\theta = (T, P, z)$ are parameters

By the implicit function theorem:

$$\frac{dy}{d\theta} = -\left(\frac{\partial F}{\partial y}\right)^{-1} \frac{\partial F}{\partial \theta}$$

This gives exact gradients at the converged solution.

### Equilibrium Equations

For vapor-liquid equilibrium:

$$F_i = \ln K_i + \ln \phi_i^L - \ln \phi_i^V = 0 \quad \text{for } i = 1, \ldots, n_c$$

$$F_{n_c+1} = \sum_i \frac{z_i(K_i - 1)}{1 + \beta(K_i - 1)} = 0 \quad \text{(Rachford-Rice)}$$

## Supported Properties

| Property | Name | Unit |
|----------|------|------|
| Density | `"density"` | kg/m³ |
| Enthalpy | `"enthalpy"` | J/mol |
| Entropy | `"entropy"` | J/mol/K |
| Heat capacity (Cp) | `"Cp"` | J/mol/K |
| Heat capacity (Cv) | `"Cv"` | J/mol/K |
| Compressibility | `"compressibility"` or `"Z"` | - |
| Molar volume | `"molarvolume"` | m³/mol |
| Molar mass | `"molarmass"` | kg/mol |
| Viscosity | `"viscosity"` | kg/m/s |
| Thermal conductivity | `"thermalconductivity"` | W/m/K |
| Sound speed | `"soundspeed"` | m/s |
| Joule-Thomson | `"joulethomson"` | K/bar |
| Kappa (Cp/Cv) | `"kappa"` or `"cpcvratio"` | - |
| Gamma | `"gamma"` | - |
| Gibbs energy | `"gibbsenergy"` | J/mol |
| Internal energy | `"internalenergy"` | J/mol |
| Vapor fraction | `"beta"` or `"vaporfraction"` | - |

## Performance Considerations

1. **Gradient computation is O(n³)** due to matrix inversion, where n is the number of components
2. **Cache results** when computing multiple property gradients - the flash gradients only need to be computed once
3. **Use analytical gradients** over finite differences when available - they're more accurate and often faster
4. **init(3) is called automatically** by `computeFlashGradients()` to ensure fugacity derivatives are computed

## Validation

The analytical gradients have been validated against numerical finite differences with excellent agreement (ratio ≈ 1.00) for:
- K-value gradients (∂K/∂T, ∂K/∂P)
- Vapor fraction gradients (∂β/∂T, ∂β/∂P)
- Density gradients (∂ρ/∂T, ∂ρ/∂P)
- Heat capacity gradients (∂Cp/∂T, ∂Cp/∂P)

See `DifferentiableFlashTest.java` for validation tests.

## See Also

- [AI Platform Integration Guide](../integration/ai_platform_integration) for ML workflows
- [MPC Integration Guide](../integration/mpc_integration) for model predictive control
