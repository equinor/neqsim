---
name: neqsim-reaction-engineering
description: "Reaction engineering patterns for NeqSim. USE WHEN: modeling chemical reactors (equilibrium, kinetic, CSTR, PFR), setting up reaction systems, analyzing conversion/selectivity, or designing reactor networks. Covers GibbsReactor (equilibrium), PlugFlowReactor (kinetic PFR), StirredTankReactor (CSTR), KineticReaction setup, and CatalystBed configuration."
last_verified: "2026-07-04"
---

# Reaction Engineering Patterns

Guide for chemical reactor modeling in NeqSim using the reactor equipment classes.

## Reactor Type Selection

| Reactor Type | NeqSim Class | When to Use |
|-------------|-------------|-------------|
| Equilibrium | `GibbsReactor` | High-T reactions approaching equilibrium (reforming, combustion, water-gas shift) |
| Plug Flow (PFR) | `PlugFlowReactor` | Tubular reactors with axial kinetics, catalytic fixed beds |
| Stirred Tank (CSTR) | `StirredTankReactor` | Perfectly mixed, steady-state with kinetics |
| Stoichiometric | `StoichiometricReaction` | Known fixed conversion, simple mass balance |
| CO2-specific equilibrium | `GibbsReactorCO2` | CO2 capture reactions with optimized convergence |
| Fermenter | `Fermenter` | Bio-reactions, enzymatic processes |

## Equilibrium Reactor (GibbsReactor)

Best for high-temperature reactions where equilibrium is reached.

### Setup Pattern

```java
import neqsim.process.equipment.reactor.GibbsReactor;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Create feed fluid with reactants
SystemInterface fluid = new SystemSrkEos(273.15 + 800.0, 30.0);
fluid.addComponent("methane", 1.0);
fluid.addComponent("water", 3.0);      // Steam-to-carbon ratio = 3
fluid.addComponent("hydrogen", 0.0001); // Products must be present
fluid.addComponent("CO", 0.0001);
fluid.addComponent("CO2", 0.0001);
fluid.setMixingRule("classic");

Stream feed = new Stream("feed", fluid);
feed.setFlowRate(1000.0, "kg/hr");

// Configure Gibbs reactor
GibbsReactor reactor = new GibbsReactor("SMR Reactor", feed);
reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
reactor.setMaxIterations(5000);
reactor.setConvergenceTolerance(1e-8);
reactor.addInertComponent("nitrogen");  // Exclude from reaction
reactor.run();

// Check results
if (reactor.hasConverged()) {
    Stream outlet = (Stream) reactor.getOutletStream();
    double methaneConversion = reactor.getComponentConversion("methane");
    double massBalErr = reactor.getMassBalanceError();
}
```

### Adiabatic Mode

```java
reactor.setEnergyMode(GibbsReactor.EnergyMode.ADIABATIC);
reactor.run();
double outletTemp = reactor.getOutletStream().getTemperature() - 273.15; // °C
double heatOfReaction = reactor.getEnthalpyOfReactions(); // W
```

## Kinetic PFR (PlugFlowReactor)

For reactions with known kinetics and axial profiles.

### Define Reactions

```java
import neqsim.process.equipment.reactor.KineticReaction;
import neqsim.process.equipment.reactor.CatalystBed;
import neqsim.process.equipment.reactor.PlugFlowReactor;

// Define reaction: CH4 + H2O -> CO + 3H2
KineticReaction smr = new KineticReaction("Steam Methane Reforming");
smr.addReactant("methane", 1, 1.0);    // (component, stoich coeff, reaction order)
smr.addReactant("water", 1, 0.5);
smr.addProduct("CO", 1);
smr.addProduct("hydrogen", 3);
smr.setPreExponentialFactor(1.17e15);   // mol/(m3·s)
smr.setActivationEnergy(240100.0);      // J/mol
smr.setHeatOfReaction(206000.0);        // J/mol (endothermic = positive)
smr.setReactionType(KineticReaction.ReactionType.POWER_LAW);
```

### Configure Catalyst

```java
CatalystBed catalyst = new CatalystBed();
catalyst.setParticleDiameter(3.0, "mm");
catalyst.setVoidFraction(0.40);
catalyst.setBulkDensity(800.0);         // kg/m3
catalyst.setCatalystDensity(2100.0);    // kg/m3
catalyst.setTortuosity(3.5);
catalyst.setSpecificSurfaceArea(50.0);  // m2/g
```

### Build and Run PFR

```java
PlugFlowReactor pfr = new PlugFlowReactor("PFR-1", feedStream);
pfr.addReaction(smr);
pfr.setCatalystBed(catalyst);
pfr.setLength(5.0, "m");
pfr.setDiameter(0.5, "m");
pfr.setNumberOfIncrements(200);
pfr.setIntegrationMethod(PlugFlowReactor.IntegrationMethod.RK4);
pfr.setEnergyMode(PlugFlowReactor.EnergyMode.ADIABATIC);
pfr.run();

// Get axial profiles
double[] positions = pfr.getAxialPositions();
double[] temperatures = pfr.getTemperatureProfile();
double[] conversions = pfr.getConversionProfile("methane");
double[] pressures = pfr.getPressureProfile();
```

### Cooled PFR with External Heat Exchange

```java
pfr.setEnergyMode(PlugFlowReactor.EnergyMode.COOLANT);
pfr.setCoolantTemperature(273.15 + 300.0);  // Kelvin
pfr.setOverallHeatTransferCoefficient(150.0); // W/(m2·K)
```

## CSTR (StirredTankReactor)

For perfectly mixed continuous reactors.

```java
import neqsim.process.equipment.reactor.StirredTankReactor;

StirredTankReactor cstr = new StirredTankReactor("CSTR-1", feedStream);
cstr.addReaction(reaction);
cstr.setVolume(10.0);          // m3
cstr.run();

double outletConcentration = cstr.getOutletStream()
    .getFluid().getComponent("product").getx();
```

## Stoichiometric Reactor

For known conversions without kinetics.

```java
import neqsim.process.equipment.reactor.StoichiometricReaction;

StoichiometricReaction reactor = new StoichiometricReaction("fixed-conv", feed);
// Specify fixed conversion
reactor.run();
```

## Process Integration with Reactors

```java
ProcessSystem process = new ProcessSystem();

Stream feed = new Stream("feed", fluid);
feed.setFlowRate(5000.0, "kg/hr");
process.add(feed);

// Pre-heat
Heater preheater = new Heater("preheat", feed);
preheater.setOutTemperature(273.15 + 800.0);
process.add(preheater);

// React
GibbsReactor reactor = new GibbsReactor("reactor", preheater.getOutletStream());
reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
process.add(reactor);

// Cool product
Cooler cooler = new Cooler("cool", reactor.getOutletStream());
cooler.setOutTemperature(273.15 + 40.0);
process.add(cooler);

// Separate
Separator sep = new Separator("flash", cooler.getOutletStream());
process.add(sep);

process.run();
```

## Key Analysis Metrics

| Metric | How to Calculate |
|--------|-----------------|
| Conversion | `reactor.getComponentConversion("methane")` |
| Mass balance error | `reactor.getMassBalanceError()` |
| Heat of reaction | `reactor.getEnthalpyOfReactions()` |
| Outlet temperature | `reactor.getOutletStream().getTemperature() - 273.15` |
| Convergence | `reactor.hasConverged()` |

## Common Pitfalls

1. **Products must be in feed**: For GibbsReactor, add trace amounts (1e-4) of expected products
2. **Missing inerts**: Always mark inert components with `addInertComponent()`
3. **Convergence**: Increase `setMaxIterations()` and decrease `setConvergenceTolerance()` for difficult systems
4. **Damping**: Use `setDampingComposition(0.01)` for oscillating systems
5. **Energy mode**: Choose ISOTHERMAL vs ADIABATIC based on the physical reactor — wrong choice gives wrong results
6. **Unit consistency**: Activation energy in J/mol, heat of reaction in J/mol

---

## Bioprocessing Reactors

NeqSim includes specialized bioreactors beyond the generic `StirredTankReactor`/`Fermenter`.

### AnaerobicDigester

Substrate-specific biogas production from organic waste. Not a flash-based reactor — uses empirical biogas yield models.

```java
AnaerobicDigester digester = new AnaerobicDigester("AD-1", feedStream);
digester.setSubstrateType(AnaerobicDigester.SubstrateType.FOOD_WASTE);
// Options: FOOD_WASTE, SEWAGE_SLUDGE, MANURE, CROP_RESIDUE, MIXED_WASTE
digester.setDigesterTemperature(37.0, "C");  // mesophilic
digester.setFeedRate(10000.0, 0.25);         // kg/hr, total solids fraction
digester.setVesselVolume(6000.0);            // m³
digester.run();
// Outputs: getBiogasOutStream(), getDigestateOutStream()
// Metrics: getMethaneProductionNm3PerDay(), getBiogasFlowRateNm3PerDay()
```

### FermentationReactor

Extends `Fermenter` with kinetic models and operation modes.

```java
FermentationReactor reactor = new FermentationReactor("FR-1", sugarFeed);
reactor.setKineticModel(FermentationReactor.KineticModel.MONOD);
// Options: MONOD, CONTOIS, SUBSTRATE_INHIBITED
reactor.setOperationMode(FermentationReactor.OperationMode.CONTINUOUS);
// Options: BATCH, FED_BATCH, CONTINUOUS
reactor.setSubstrateConcentration(100.0);    // g/L
reactor.setBiomassConcentration(1.0);        // g/L
reactor.setMaxSpecificGrowthRate(0.30);      // 1/hr
reactor.setMonodConstant(1.0);               // g/L (Ks)
reactor.setYieldBiomass(0.10);               // g biomass / g substrate
reactor.setYieldProduct(0.45);               // g product / g substrate
reactor.setResidenceTime(10.0, "hr");
reactor.run();
Map<String, Object> results = reactor.getResults();
// Keys: substrateConversion, finalProductConc_g_per_L, finalBiomassConc_g_per_L,
//       productivity_g_per_L_hr
```

### BiogasUpgrader

Splits biogas into biomethane + offgas using technology-specific split factors.

```java
BiogasUpgrader upgrader = new BiogasUpgrader("BGU-1", biogasStream);
upgrader.setTechnology(BiogasUpgrader.UpgradingTechnology.MEMBRANE);
// Options: WATER_SCRUBBING, AMINE_SCRUBBING, PSA, MEMBRANE
upgrader.run();
// Outputs: getBiomethaneOutStream(), getOffgasOutStream()
// Enum getters: tech.getMethaneRecovery(), tech.getCo2RemovalEfficiency()
```

### Pre-built Biorefinery Modules

| Module | Purpose | Key Methods |
|--------|---------|-------------|
| `BiogasToGridModule` | AD → upgrading → compression → grid | `setGridPressureBara()`, `setGridTemperatureC()`, `getResults()` |
| `GasificationSynthesisModule` | Biomass → syngas → FT liquids | `setBiomass(BiomassCharacterization, kgPerHr)` |
| `WasteToEnergyCHPModule` | AD → gas engine CHP | `setElectricalEfficiency()`, `getElectricalPowerKW()`, `getHeatOutputKW()` |

### SustainabilityMetrics

Utility class for CO₂-equivalent tracking and LCA:

```java
SustainabilityMetrics metrics = new SustainabilityMetrics();
metrics.setBiogasProductionNm3PerYear(3_000_000.0);
metrics.setMethaneContentFraction(0.60);
metrics.setMethaneSlipPercent(1.5);
metrics.setElectricityProductionMWhPerYear(8000.0);
metrics.setHeatProductionMWhPerYear(10000.0);
metrics.setParasiticElectricityMWhPerYear(800.0);
metrics.setFossilReferenceEmissionFactor(0.450);  // kgCO2/kWh
metrics.setFossilHeatEmissionFactor(0.250);
metrics.calculate();
// getTotalEmissionsTCO2eqPerYear(), getCarbonIntensityKgCO2PerMWh()
// getFossilFuelDisplacementTCO2PerYear(), getNetCarbonBalanceTCO2PerYear()
// getEnergyReturnOnInvestment(), getRenewableEnergyFraction()
```
