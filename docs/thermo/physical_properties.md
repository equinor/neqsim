# Physical Property Calculations

NeqSim computes phase and mixture properties after thermodynamic initialization. This guide highlights the most used methods and how to choose appropriate models.

## Density and Compressibility
- Call `fluid.initPhysicalProperties()` or `fluid.initProperties()` after a flash to populate density (`getDensity()`) and compressibility (`getZ()`).
- Apply volume-shifted PR/SRK models when liquid density underpredicts lab data.

## Viscosity
- `getViscosity()` on a phase returns dynamic viscosity. Correlations switch automatically based on system type:
  - Corresponding-states for gases and light condensates.
  - Heavy-oil extensions when TBP fractions or high molar masses are present.
  - Association-corrected viscosity for CPA systems.
- Use `setMixingRule(7)` or CPA fluids when hydrogen bonding impacts rheology (water, glycols).

## Thermal Conductivity and Heat Capacity
- `getThermalConductivity()` provides phase thermal conductivity using dense-gas corrections.
- `getCp()` and `getEnthalpy()` support energy balances. Reinitialize (`init(3)`) if temperature changes substantially between calls.

## Surface and Interfacial Tension
- `getInterfacialTension(phase1, phase2)` calculates tension between phases (e.g., gas-oil, gas-water) using parachor correlations tied to the active EOS.
- Ensure both phases are present after a flash; otherwise, perform a two-phase flash at representative separator conditions first.

## Diffusion and Mass Transfer
- `getDiffusionCoefficient()` is available on phases for estimating film and molecular diffusion coefficients.
- In unit operations, enable mass-transfer calculations to access local Sherwood correlations and film models.

## Numerical Tips
- Always flash (`TPflash`, `PHflash`, etc.) before requesting properties; raw composition-only systems do not hold valid properties.
- When looping over many states, reuse fluids and call `initPhysicalProperties()` after each state change to refresh transport properties without repeating equilibrium calculations.
