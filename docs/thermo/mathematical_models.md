# Mathematical Models in NeqSim

NeqSim bundles several thermodynamic and transport models so you can switch between correlations without rewriting system setup code. The sections below summarize the most commonly used options and when to consider them.

## Equations of State (EoS)

NeqSim primarily uses cubic equations of state of the general form:

\[
P = \frac{RT}{v - b} - \frac{a(T)}{(v + \epsilon b)(v + \sigma b)}
\]

where $P$ is pressure, $T$ is temperature, $v$ is molar volume, $R$ is the gas constant, and $a(T), b$ are the energy and co-volume parameters.

### Supported Families

- **Peng–Robinson (PR) family** ($\epsilon = 1 - \sqrt{2}, \sigma = 1 + \sqrt{2}$):
  Standard PR (`SystemPrEos`), volume-corrected variants (Peneloux), and tuned versions such as the Søreide–Whitson model for sour service. Suitable for general gas/condensate and light-oil systems.

- **Soave–Redlich–Kwong (SRK) family** ($\epsilon = 0, \sigma = 1$):
  Core SRK (`SystemSrkEos`), SRK-Twu, and CPA-SRK for associating fluids such as water and glycols.

- **Cubic-Plus-Association (CPA)**:
  Adds an association term to the SRK or PR equation to represent hydrogen bonding:
  \[
  P = P_{\text{cubic}} - \frac{1}{2} RT \rho \sum_i x_i \sum_{A_i} \left( 1 - X_{A_i} \right) \frac{\partial \ln g}{\partial v}
  \]
  where $X_{A_i}$ is the fraction of site A on molecule i not bonded to other active sites.

- **Activity-coefficient hybrids**:
  Huron–Vidal and Wong–Sandler mixing rules combine cubic EoS with excess-Gibbs models ($G^E$) for improved liquid-phase behavior.

### Selecting an EoS
```java
SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("n-heptane", 0.1);
fluid.setMixingRule(2); // 2 = Huron–Vidal; use 1 for classical van der Waals
```
Use `SystemSrkCPAstatoil` or `SystemSrkCPAs` when hydrogen bonding is important, and prefer PR variants for high-pressure gas processing.

## Activity-Coefficient Models
NeqSim provides NRTL/UNIQUAC/UNIFAC variants for non-ideal liquid mixtures. They can be used directly for gamma-phi flashes or combined with cubic EoS via Wong–Sandler mixing rules.

```java
SystemInterface fluid = new SystemFurstElectrolyteEos(298.15, 1.0);
fluid.addComponent("water", 1.0);
fluid.addComponent("ethanol", 1.0);
fluid.setMixingRule(4); // enables Wong–Sandler (NRTL) coupling
```
Load binary-interaction parameters via `mixingRuleName` or by reading custom datasets to align with lab data.

## Hydrate and Solid Models
For hydrate prediction, enable `hydrateCheck(true)` and select a hydrate model (`CPA`-based or classical van der Waals–Platteeuw) depending on accuracy and speed requirements. Wax precipitation can be modeled using solid-phase enabled systems (e.g., PR with solid checks) and tuned heavy-end characterizations.

## Transport and Physical Property Correlations
- **Viscosity**: Low-pressure correlations (e.g., Chung) and high-pressure gas correlations plus heavy-oil extensions. CPA fluids can include association corrections for viscosity.
- **Thermal conductivity**: Dense-phase corrections layered on dilute-gas references.
- **Surface tension**: Parachor correlations tied to component critical properties.
- **Diffusion and mass transfer**: Fickian diffusion coefficients and film models for unit operations.

Choose property packages that match the flow regime: cubic EoS with corresponding-state transport for gas processing, CPA with association corrections for aqueous systems, and heavy-oil tuned correlations for late-life reservoirs.
