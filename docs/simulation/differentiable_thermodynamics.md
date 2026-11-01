---
title: "Differentiable Thermodynamics"
description: "Implicit VLE sensitivities and batched property gradients, with explicit composition directions and validity limits."
---

# Differentiable Thermodynamics

DifferentiableFlash reads the supplied current equilibrium on each call and uses detached snapshots.
It does not initialize, perturb or reflash the caller. Keep the caller unchanged during a call.

## Scope and validity

Flash sensitivities require exactly one gas and one liquid phase, positive component fractions,
an interior vapor fraction, conservative compositions and converged interphase fugacities.
Check FlashGradients.isValid() before reading derivatives. Property gradients support a single
fluid phase or the same interior VLE state. Reactions, ions, solids/hydrates, liquid-liquid and
three-phase states are unsupported. Unsupported property gradients throw rather than return zeros.
Boundary and singular critical states need another derivative formulation.

Temperature derivatives are per kelvin; pressure derivatives are **per bar**.
Molar volume is EOS volume in **m³/mol**; Gibbs/internal energy are **J/mol**. Earlier versions
returned the internal volume unit and extensive Gibbs energy despite these documented molar units.
The internal-energy property previously requested an unsupported unit from the underlying getter.

## Normalized composition directions

Column $j$ follows a normalized addition at fixed total feed moles:

$$z_i(h)=\frac{z_i+h\delta_{ij}}{1+h},\qquad z'_i(0)=\delta_{ij}-z_i.$$

Here $h$ is dimensionless and $\delta_{ij}$ is the Kronecker delta.
For a physical tangent vector $\Delta z$ with $\sum_i\Delta z_i=0$, its dot product with the
returned columns is the constrained directional derivative. Divide a column by total moles
to obtain a derivative with respect to feed moles. Columns are not independent mole-fraction
partials holding every other fraction fixed.

## Example

This complete example is exercised by DifferentiableFlashCompositionTest.documentationExample.
Import the system, operations and derivative classes from their standard NeqSim packages.

~~~java
SystemInterface fluid = new SystemSrkEos(250.0, 30.0);
fluid.addComponent("methane", 0.8);
fluid.addComponent("propane", 0.2);
fluid.setMixingRule(2);
new ThermodynamicOperations(fluid).TPflash();

DifferentiableFlash derivatives = new DifferentiableFlash(fluid);
FlashGradients flash = derivatives.computeFlashGradients();
if (!flash.isValid()) {
  throw new IllegalStateException(flash.getErrorMessage());
}
PropertyGradient[] properties = derivatives.computePropertyGradients(
    new String[] {"density", "enthalpy", "beta"});
double densityPerBar = properties[0].getDerivativeWrtPressure();
double enthalpyAlongFeedChange = properties[1].directionalDerivative(
    0.0, 0.0, new double[] {0.01, -0.01});
~~~

The scalar computePropertyGradient(name) API retains full-equilibrium finite differences on detached clones, including real normalized composition derivatives. The new batch API is additive: use it for shared implicit/local property evaluations. Scalar full reflashes provide a separate reference path, with explicit rejection if a perturbation changes phase topology.
Results are not cached across calls: mutable fluid and EOS changes cannot reuse old gradients.
extractFugacityJacobian(index) also initializes a detached snapshot. Phase-array order is not
a gas/liquid convention. getFugacityJacobians() returns liquid then vapor, or null when unsupported.

## Method

For $K_i=y_i/x_i$, the equilibrium equations are

$$F_i=\ln K_i-\ln\phi_i^L+\ln\phi_i^V=0,\qquad R=\sum_i\frac{z_i(K_i-1)}{1+\beta(K_i-1)}=0.$$

The internal unknowns are $q=(\ln K,\beta)$. One EJML LU factorization solves all input columns:

$$F_q q_\theta=-F_\theta.$$

The feed columns include both phase-composition responses to changing $z$.
No explicit matrix inverse is formed. Finite outputs and scaled linear residuals are checked.
These checks do not prove global phase stability or bound the condition number near criticality.

Batched property gradients propagate the implicit phase directions through centered **local** EOS/property
evaluations, with no TP reflash. These are semi-analytical derivatives, not automatic differentiation
or exact analytical derivatives of every property correlation. Steps stay inside the phase/composition
domain; unresolved boundary steps fail. Entropy/Gibbs composition derivatives at an exactly zero feed are singular and rejected. A locally smooth property for a zero-feed single-phase species uses a second-order one-sided
evaluation. Molecular-weight and vapor-fraction gradients use their direct formulas.

## Properties and units

| Names | Unit |
| --- | --- |
| density | kg/m³, using the configured physical density model |
| enthalpy, gibbsenergy, internalenergy | J/mol |
| entropy, Cp, Cv | J/mol/K |
| molarvolume | m³/mol, EOS volume |
| molarmass | kg/mol |
| compressibility, Z, beta, vaporfraction, kappa, cpcvratio, gamma | dimensionless |
| viscosity | kg/m/s |
| thermalconductivity | W/m/K |
| soundspeed | m/s |
| joulethomson | K/bar |

The equilibrium enthalpy temperature derivative includes phase redistribution and generally differs
from the frozen-phase getCp() value. Transport and sound-speed derivatives retain the physical
assumptions of the underlying getter; this interface does not add a new acoustic or transfer model.

## Evidence

DifferentiableFlashCompositionTest compares against independent **full TP reflashes**, at two
probe sizes, for SRK/PR four-component hydrocarbons and associating CPA methane/water.
It checks T/P/feed derivatives, units, inventory scaling, mutable-input reuse, caller preservation
and invalid states. Molecular weight has the exact normalized-addition check $M_j-M_{\rm feed}$.
The existing DifferentiableFlashTest remains part of the regression suite.

See [the numerical mathematics assessment](recent_numerical_mathematics.md) for selection and benchmarks.
