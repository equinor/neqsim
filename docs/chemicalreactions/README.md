---
title: Chemical Reactions Package
description: Supported equilibrium workflows, reaction-data provenance, diagnostics, and kinetics boundaries in NeqSim.
---

The `neqsim.chemicalreactions` package supplies reaction definitions, database-backed
aqueous-equilibrium operations, residual diagnostics, and coupled kinetic helpers. Simultaneous
phase and chemical equilibrium is exposed through the reactive-flash operations.

## Choose the right workflow

| Need | Supported entry point | Evidence |
|---|---|---|
| Simultaneous TP phase and chemical equilibrium | `ThermodynamicOperations.reactiveTPflash()` | [Reactive-flash guide](../thermo/reactive_flash) |
| Direct control of the modified-RAND TP solver | `ReactiveMultiphaseTPflash` | [Reactive-flash tests](https://github.com/equinor/neqsim/tree/master/src/test/java/neqsim/thermodynamicoperations/flashops/reactiveflash) |
| Reactive PH or PS equilibrium | `reactivePHflash(...)` or `reactivePSflash(...)` | [Reactive-flash guide](../thermo/reactive_flash#reactive-ph-flash-isenthalpic-and-ps-flash-isentropic) |
| Inspect the initialized aqueous reaction set and closure diagnostics | `system.getChemicalReactionOperations()` | [Reaction-model audit guide](../thermo/reaction_model_audit) |
| Screen experimental CO₂ impurity kinetics | Dedicated, evidence-gated reactor and transport classes | [Qualified execution guide](co2_impurity_qualified_execution) |

A normal `TPflash()` solves phase equilibrium only. It does not become reactive through a
boolean switch. Use a reactive-flash operation explicitly so the calculation intent is visible.

## Equilibrium basis

At fixed temperature and pressure, equilibrium minimizes total Gibbs energy subject to elemental
conservation:

$$
\min G = \sum_i n_i \mu_i
$$

$$
\sum_i a_{ji} n_i = b_j
$$

Here, $n_i$ is the amount of species $i$, $\mu_i$ its chemical potential, $a_{ji}$ the amount of
element $j$ in species $i$, and $b_j$ the conserved feed inventory of that element. Ionic
aqueous calculations also require electroneutrality.

NeqSim contains two related implementations:

- `neqsim.chemicalreactions.ChemicalReactionOperations` owns the database-selected reaction list
  attached to a thermodynamic system, aqueous reaction closure, and residual diagnostics.
- `neqsim.thermodynamicoperations.flashops.reactiveflash` contains the modified-RAND solvers for
  simultaneous phase and chemical equilibrium. `FormulaMatrix` derives the element constraints
  and number of independent reactions from the selected components.

Application code should create and configure the thermodynamic system, then call the relevant
thermodynamic operation. Do not construct the low-level equilibrium or linear-programming helpers
as standalone solvers; their constructors require internal matrices, components, reference
potentials, and phase context.

## Complete Java example

The build-verified
[ChemicalReactionEquilibriumExample.java](../examples/ChemicalReactionEquilibriumExample.java)
solves the water-gas-shift system

$$
\mathrm{CO + H_2O \rightleftharpoons CO_2 + H_2}
$$

at 600 K and 1 bar. It uses Java 8-compatible syntax and parameterized Log4j2 reporting, checks
finite bounded mole fractions, verifies the expected reaction direction, and checks carbon and
hydrogen balances.

The example's core public call is:

```java
ThermodynamicOperations operations = new ThermodynamicOperations(system);
operations.reactiveTPflash();
```

Run the complete source from a checkout after compiling NeqSim:

```bash
./mvnw -q -DskipTests package dependency:copy-dependencies
javac -cp "target/classes:target/dependency/*" docs/examples/ChemicalReactionEquilibriumExample.java
java -cp "target/classes:target/dependency/*:docs/examples" ChemicalReactionEquilibriumExample
```

Classpath syntax differs on Windows. The repository's
`StandaloneJavaDocumentationCompilationTest` compiles the exact source against the current API.
`ReactiveFlashBenchmarkTest.testWaterGasShiftEquilibrium` executes the same feed and conditions
and verifies convergence, reaction direction, and elemental conservation.

## System setup

A reproducible reactive calculation should make each of these choices explicit:

1. Select a thermodynamic model appropriate for every phase and species.
2. Add all feed species with physical, non-negative amounts.
3. Set the mixing rule and phase limit.
4. Initialize the system before direct solver use.
5. Run the dedicated reactive operation.
6. Check convergence and conservation before interpreting composition.
7. Record reaction-data provenance and the thermodynamic model with the result.

The reactive solver can redistribute species only within the elemental inventory represented by
the configured components. The availability and validity of reaction data depend on the selected
data source and component set; the package does not imply that every named industrial reaction is
qualified.

## Aqueous chemistry and charge balance

Database-backed `ChemicalReactionOperations` is intended for a liquid reactive phase, especially
aqueous electrolyte chemistry. Its initialized reaction set is owned by the thermodynamic system.
After initialization, obtain it through `system.getChemicalReactionOperations()` to inspect:

- whether reactions were selected;
- the reaction-data source and provenance;
- natural-log reaction residuals;
- reactive-phase charge residuals; and
- element-balance residuals.

For ions, the complete reactive phase must satisfy

$$
\sum_i z_i n_i = 0
$$

where $z_i$ is ionic charge. Do not seed an arbitrary unbalanced ion inventory merely to force
solver initialization. Use a supported electrolyte model, a charge-balanced feed, and the
diagnostic limits documented in the [reactive-flash guide](../thermo/reactive_flash).

## Reaction data and provenance

`ChemicalReactionList` selects applicable database reactions from the system's components and
builds stoichiometric matrices. `ChemicalReactionDataSource` identifies the selected parameter
set. Treat the identifier and cited reference as part of the calculation result.

Reaction identifiers, correlations, temperature ranges, and reference states are data, not generic
user-defined strings accepted by the thermodynamic system. Adding or changing reaction data is a
model-development task that requires source evidence, validation, and review; it is not a
documentation-only configuration step.

Use the [reaction-model audit guide](../thermo/reaction_model_audit) to list the initialized model without
re-evaluating potentially underflowing activity products. Use the
[qualified CO₂ impurity execution guide](co2_impurity_qualified_execution) when an experimental kinetic network
requires evidence-bound execution.

## Kinetics boundary

The legacy `neqsim.chemicalreactions.kinetics.Kinetics` class is coupled to an initialized
`ChemicalReactionOperations` instance. It is not a general-purpose Arrhenius builder with
independent setters for a pre-exponential factor and activation energy.

### Reaction-specific temperature laws and migration

`ChemicalReaction.getRateFactor(phase)` evaluates the temperature law reported by
`getKineticRateLaw()`. Directly constructed reaction objects use
`REFERENCE_ARRHENIUS`:

$$k(T)=k_{\mathrm{ref}}\exp\left[-\frac{E_a}{R}\left(\frac{1}{T}-\frac{1}{T_{\mathrm{ref}}}\right)\right].$$

The constructor rate factor is the rate at the reference temperature, **not** a
pre-exponential factor. Activation energy is in **J/mol** and temperatures are in
**K**. Rate-factor units remain those required by the caller's rate law: for
example, s⁻¹ for a first-order law or m³/(mol·s) for a second-order law using
mol/m³ concentrations. This temperature evaluation does not convert activities,
molality, molarity or reaction order. The legacy `Kinetics` caller continues to
construct its concentration-like factors as `x_i * density / componentMolarMass`;
this patch does not revise or qualify that concentration convention.

Earlier versions ignored all three supplied kinetic parameters and evaluated
`2.576e9 * exp(-6024.0 / T) / 1000.0` for every reaction. Ignoring parameters of a
custom reaction is an API defect. That observation alone does not establish that
the historical temperature correlation is wrong for every original application.

Database reactions loaded by `ChemicalReactionFactory` and `ChemicalReactionList`
explicitly retain `LEGACY_TEMPERATURE_CORRELATION`. Their `R`, `ACTENERGY` and
`TREF` columns have not been qualified here as reaction-specific rate constants
and J/mol activation energies; no conversion is guessed from the magnitude of a
stored number. This preserves existing database-driven kinetic results. The
historical correlation is a compatibility option, **not a validated universal
kinetic model**. Its source and validity range require separate qualification.

To migrate a reaction, call
`setReferenceKinetics(referenceRate, activationEnergyJPerMol, referenceTemperatureK)`
with a complete, independently supported parameter set. To reproduce the old
temperature law for a custom object, call `useLegacyKineticRateLaw()` explicitly.
`setRateFactor` and `setActivationEnergy` update stored values but do not switch a
legacy reaction to a different law. The no-argument `getRateFactor()` returns the
stored reference factor, which is unused in legacy mode. Invalid reference-law
parameters are rejected at evaluation or when using `setReferenceKinetics`.
Older serialized objects without a law selector retain legacy behavior.

Tests establish the reference-temperature identity, zero-activation-energy limit,
Arrhenius slope, parameter propagation through `Kinetics`, serialization and
database compatibility. They do not establish experimental kinetic accuracy or
restore the thesis's fitted rate constants. Review the change in behavior for
direct constructor callers before adopting it in an existing application.

For rate-limited process models, choose a dedicated implementation whose reaction definition,
units, parameter provenance, validity range, integration method, and conservation behavior are
documented and tested. Current examples include:

- [CO₂ impurity kinetics](co2_impurity_kinetics_guide);
- [Damköhler CO₂ transport reaction-kinetics screening](co2_transport_reaction_kinetics);
- [qualified CO₂ impurity execution guide](co2_impurity_qualified_execution);
- [aqueous H2S/O2 kinetics guide](h2s_oxygen_kinetics); and
- [CO₂ hydration temperature-trajectory guide](co2_hydration_temperature_trajectory).

Do not substitute equilibrium composition for residence-time-dependent conversion, or infer a rate
constant from equilibrium data alone.

## Validation checklist

Before engineering use:

- confirm the reaction set and parameter source;
- confirm that temperature, pressure, composition, and phase regime are inside the evidence range;
- require solver convergence;
- verify element conservation and, for electrolytes, charge closure;
- inspect reaction residuals instead of relying only on a returned composition;
- compare with an independent benchmark or measurement; and
- distinguish equilibrium predictions from kinetic or transport limitations.

The WGS quickstart is an API and conservation example. Its values are not a design basis or a
validated reactor model.

## Package map

| Area | Responsibility |
|---|---|
| `ChemicalReactionOperations` | System-owned aqueous reaction selection, solution, and diagnostics |
| `chemicalreaction` | Reaction records, lists, data sources, correlations, and model audit |
| `chemicalequilibrium` | Internal equilibrium and initialization helpers |
| `kinetics` | Helpers coupled to initialized reaction operations |
| `thermodynamicoperations.flashops.reactiveflash` | Public reactive TP/PH/PS workflows and modified-RAND implementation |

## Related documentation

- [Reactive flash calculations](../thermo/reactive_flash)
- [Thermodynamic operations](../thermo/thermodynamic_operations)
- [Fluid creation](../thermo/fluid_creation_guide)
- [Electrolyte models](../thermo/ElectrolyteCPAModel)
- [Reaction-model audit](../thermo/reaction_model_audit)
