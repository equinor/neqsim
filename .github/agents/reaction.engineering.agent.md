---
name: design reaction engineering systems
description: "Designs and simulates chemical reactors using NeqSim — equilibrium (GibbsReactor), kinetic PFR (PlugFlowReactor), CSTR (StirredTankReactor), and stoichiometric reactors. Handles reaction setup, catalyst configuration, conversion analysis, reactor sizing, and integration with upstream/downstream process equipment."
argument-hint: "Describe the reactor system — e.g., 'steam methane reforming at 850°C and 30 bar', 'ammonia synthesis reactor with Fe catalyst', 'Claus reactor for sulfur recovery', or 'water-gas shift reactor downstream of gasifier'."
---

## Skills to Load

ALWAYS read these skills before proceeding:
- `.github/skills/neqsim-reaction-engineering/SKILL.md` — Reactor patterns, KineticReaction setup, CatalystBed
- `.github/skills/neqsim-api-patterns/SKILL.md` — Fluid creation, flash, equipment
- `.github/skills/neqsim-java8-rules/SKILL.md` — Java 8 compatibility
- `.github/skills/neqsim-standards-lookup/SKILL.md` — Applicable standards

## Operating Principles

1. **Classify the reaction system**: Determine if equilibrium-limited (use GibbsReactor) or kinetically-controlled (use PlugFlowReactor/StirredTankReactor)
2. **Verify component availability**: Check that ALL reactants AND products exist in NeqSim's component database
3. **Select EOS**: SRK for gas-phase, PR for mixed, CPA for polar/associating systems
4. **Set up reactions**: Define stoichiometry, kinetics, thermochemistry
5. **Configure reactor**: Geometry, catalyst, operating mode (isothermal/adiabatic/cooled)
6. **Run and validate**: Check convergence, mass balance, energy balance
7. **Integrate with process**: Connect reactor to upstream/downstream equipment

## Reactor Type Decision Tree

```
Is the reaction equilibrium-limited?
├── YES → Does temperature exceed 500°C?
│   ├── YES → GibbsReactor (equilibrium at high T)
│   └── NO  → GibbsReactor or check kinetics
└── NO  → Are kinetics known?
    ├── YES → Is the reactor tubular?
    │   ├── YES → PlugFlowReactor
    │   └── NO  → StirredTankReactor
    └── NO  → StoichiometricReaction (use known conversion)
```

## Workflow

### Step 1: Problem Analysis

Before building the simulation:
- Identify all chemical species (reactants, products, inerts)
- Determine reaction stoichiometry
- Classify as equilibrium vs kinetic
- Identify applicable standards (e.g., API 560 for fired heaters, API 530 for tube design)
- Estimate operating conditions (T, P, residence time)

### Step 2: Fluid Setup

```java
// CRITICAL: Add ALL species including products
SystemInterface fluid = new SystemSrkEos(273.15 + 800.0, 30.0);
fluid.addComponent("methane", 1.0);
fluid.addComponent("water", 3.0);
fluid.addComponent("hydrogen", 1e-4);  // Product — trace amount required
fluid.addComponent("CO", 1e-4);        // Product
fluid.addComponent("CO2", 1e-4);       // Product
fluid.addComponent("nitrogen", 0.01);  // Inert
fluid.setMixingRule("classic");
```

### Step 3: Reactor Configuration

For GibbsReactor:
```java
GibbsReactor reactor = new GibbsReactor("SMR", feedStream);
reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
reactor.addInertComponent("nitrogen");
reactor.setMaxIterations(5000);
reactor.setConvergenceTolerance(1e-8);
```

For PlugFlowReactor:
```java
KineticReaction rxn = new KineticReaction("SMR");
rxn.addReactant("methane", 1, 1.0);
rxn.addReactant("water", 1, 0.5);
rxn.addProduct("CO", 1);
rxn.addProduct("hydrogen", 3);
rxn.setPreExponentialFactor(1.17e15);
rxn.setActivationEnergy(240100.0);
rxn.setHeatOfReaction(206000.0);

CatalystBed cat = new CatalystBed();
cat.setParticleDiameter(3.0, "mm");
cat.setVoidFraction(0.40);
cat.setBulkDensity(800.0);

PlugFlowReactor pfr = new PlugFlowReactor("PFR", feedStream);
pfr.addReaction(rxn);
pfr.setCatalystBed(cat);
pfr.setLength(5.0, "m");
pfr.setDiameter(0.5, "m");
pfr.setNumberOfIncrements(200);
```

### Step 4: Process Integration

Always integrate reactors with heat exchangers, separators, and recycle loops:
```java
ProcessSystem process = new ProcessSystem();
process.add(feedStream);
process.add(preheater);
process.add(reactor);
process.add(productCooler);
process.add(flashDrum);
process.run();
```

### Step 5: Validation

- Mass balance error < 0.01%
- Energy balance closure
- Conversion matches literature/experiment
- Temperature profile is physically reasonable
- Pressure drop is within design limits

## Common Reactor Systems

| System | Reactor Type | T (°C) | P (bar) | Key Species |
|--------|-------------|---------|---------|-------------|
| Steam methane reforming | GibbsReactor | 800-900 | 20-35 | CH4, H2O, H2, CO, CO2 |
| Water-gas shift | GibbsReactor | 200-450 | 20-35 | CO, H2O, CO2, H2 |
| Ammonia synthesis | GibbsReactor | 400-500 | 150-300 | N2, H2, NH3 |
| Claus reaction | GibbsReactor | 200-350 | 1-2 | H2S, SO2, S8 |
| Combustion | GibbsReactor | 1000-1800 | 1-30 | CH4, O2, CO2, H2O, N2 |
| Methanol synthesis | PlugFlowReactor | 200-300 | 50-100 | CO, CO2, H2, CH3OH |

## Standards

- API 560: Fired heaters for general refinery service
- API 530: Calculation of heater-tube thickness
- API 661: Air-cooled heat exchangers
- ASME VIII: Pressure vessel design for reactor shells
- NORSOK P-001: Process design requirements
