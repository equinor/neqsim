---
title: "PVT Simulation Workflows"
description: "This guide covers PVT simulation workflows in NeqSim, backed by regression tests under `src/test/java/neqsim/pvtsimulation/simulation`. Use these tested setups to reproduce experiments in your own stu..."
---

# PVT Simulation Workflows

This guide covers PVT simulation workflows in NeqSim, backed by regression tests under `src/test/java/neqsim/pvtsimulation/simulation`. Use these tested setups to reproduce experiments in your own studies.

---

## Constant Volume Depletion (CVD)

CVD simulation maintains reservoir volume constant while reducing pressure, measuring the liquid dropout from gas condensate reservoirs.

### Basic CVD Setup

```java
import neqsim.pvtsimulation.simulation.ConstantVolumeDepletion;
import neqsim.thermo.system.SystemSrkEos;

// Create fluid with TBP fractions
SystemSrkEos fluid = new SystemSrkEos(97.5 + 273.15, 300.0);  // T(K), P(bara)
fluid.addComponent("nitrogen", 0.34);
fluid.addComponent("CO2", 3.59);
fluid.addComponent("methane", 67.42);
fluid.addComponent("ethane", 9.02);
fluid.addComponent("propane", 4.31);
fluid.addComponent("i-butane", 0.93);
fluid.addComponent("n-butane", 1.71);
fluid.addComponent("i-pentane", 0.74);
fluid.addComponent("n-pentane", 0.85);
fluid.addTBPfraction("C6", 1.38, 86.0 / 1000, 0.664);
fluid.addTBPfraction("C7", 1.57, 96.0 / 1000, 0.738);
fluid.addTBPfraction("C8", 1.73, 107.0 / 1000, 0.765);
fluid.addTBPfraction("C9", 1.40, 121.0 / 1000, 0.781);
fluid.addTBPfraction("C10+", 5.01, 230.0 / 1000, 0.820);

fluid.setMixingRule("classic");
fluid.useVolumeCorrection(true);
fluid.init(0);
fluid.init(1);

// Configure CVD simulation
ConstantVolumeDepletion cvd = new ConstantVolumeDepletion(fluid);
cvd.setTemperature(97.5, "C");
cvd.setPressures(new double[] {300, 250, 200, 150, 100, 50});  // bara

// Run simulation
cvd.runCalc();

// Get results
double[] relativeVolume = cvd.getRelativeVolume();
double[] liquidVolumeFraction = cvd.getLiquidVolume();
double[] Zgas = cvd.getZgas();
```

### CVD Setup Checklist

1. Create `SystemInterface` with EOS and add components/TBP fractions
2. Enable database use and select a mixing rule
3. Initialize the system (state 0 and 1) before constructing `ConstantVolumeDepletion`
4. Call `setTemperature(...)`, `setPressures(...)`, and `runCalc()`
5. Optionally load experimental data for regression with `setExperimentalData(...)`
6. Retrieve results: `getRelativeVolume()`, `getLiquidVolume()`, `getZgas()`

---

## Differential Liberation (DL)

DL simulation removes liberated gas at each pressure step, measuring oil shrinkage and gas evolution - essential for black oil PVT tables.

### Basic DL Setup

```java
import neqsim.pvtsimulation.simulation.DifferentialLiberation;
import neqsim.thermo.system.SystemSrkEos;

// Create rich oil system with TBP characterization
SystemSrkEos fluid = new SystemSrkEos(97.5 + 273.15, 250.0);
fluid.addComponent("nitrogen", 0.5);
fluid.addComponent("CO2", 2.1);
fluid.addComponent("methane", 45.0);
fluid.addComponent("ethane", 7.5);
fluid.addComponent("propane", 5.2);
fluid.addComponent("i-butane", 1.1);
fluid.addComponent("n-butane", 2.8);
fluid.addComponent("i-pentane", 1.4);
fluid.addComponent("n-pentane", 1.9);
fluid.addTBPfraction("C6", 2.5, 86.0 / 1000, 0.685);
fluid.addTBPfraction("C7", 4.2, 96.0 / 1000, 0.755);
fluid.addTBPfraction("C8", 3.8, 107.0 / 1000, 0.775);
fluid.addTBPfraction("C9", 3.2, 121.0 / 1000, 0.790);
fluid.addTBPfraction("C10+", 18.8, 350.0 / 1000, 0.880);

fluid.setMixingRule("classic");
fluid.useVolumeCorrection(true);
fluid.init(0);
fluid.init(1);

// Configure DL simulation
DifferentialLiberation dl = new DifferentialLiberation(fluid);
dl.setTemperature(97.5, "C");
dl.setPressures(new double[] {250, 225, 200, 175, 150, 125, 100, 75, 50, 25, 1});

// Run simulation
dl.runCalc();

// Get results
double[] Bo = dl.getBo();           // Oil formation volume factor
double[] Rs = dl.getRs();           // Solution gas-oil ratio (Sm3/Sm3)
double[] Bg = dl.getBg();           // Gas formation volume factor
double[] oilDensity = dl.getOilDensity();
```

### Interpreting DL Outputs

| Property | Description | Expected Trend |
|----------|-------------|----------------|
| **Bo** | Oil formation volume factor (Vres/Vstock) | Decreases from ~1.7 to ~1.05 as pressure drops |
| **Rs** | Solution gas-oil ratio | Decreases to zero at final stage |
| **Bg** | Gas formation volume factor | Increases as gas expands at lower pressure |
| **Oil Density** | Density of remaining oil | Increases as light components liberate |

---

## Constant Composition Expansion (CCE)

CCE measures PV behavior without removing any material - used to determine bubble/dew point pressure.

```java
import neqsim.pvtsimulation.simulation.ConstantMassExpansion;

// After creating and initializing fluid...
ConstantMassExpansion cce = new ConstantMassExpansion(fluid);
cce.setTemperature(100.0, "C");

// Run to find saturation pressure
cce.runCalc();

double saturationPressure = cce.getSaturationPressure();
double[] relativeVolume = cce.getRelativeVolume();
double[] Ytfactor = cce.getYfactor();
```

---

## Saturation Pressure Calculation

Quick calculation of bubble or dew point pressure:

```java
import neqsim.thermodynamicoperations.ThermodynamicOperations;

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

// For bubble point (oil system)
ops.calcBubblePoint();
double bubbleP = fluid.getPressure("bara");

// For dew point (gas condensate)
ops.calcDewPoint();
double dewP = fluid.getPressure("bara");
```

---

## Slim Tube Simulation

Minimum miscibility pressure (MMP) determination:

```java
import neqsim.pvtsimulation.simulation.SlimTubeSim;

// Create injection gas
SystemSrkEos injectionGas = new SystemSrkEos(373.15, 200.0);
injectionGas.addComponent("CO2", 1.0);
injectionGas.setMixingRule("classic");

// Configure slim tube
SlimTubeSim slimTube = new SlimTubeSim(reservoirFluid, injectionGas);
slimTube.setTemperature(100.0, "C");
slimTube.setPressures(new double[] {150, 200, 250, 300, 350, 400});
slimTube.runCalc();

double[] recovery = slimTube.getOilRecovery();
```

---

## Best Practices

1. **Always initialize** - Set mixing rule and call `init(0)` and `init(1)` before creating PVT simulations
2. **Set temperature explicitly** - Use `setTemperature()` on each simulation to avoid state carryover
3. **Use volume correction** - Enable `useVolumeCorrection(true)` for better liquid density predictions
4. **Validate against lab data** - Use `setExperimentalData()` methods for regression
5. **Check convergence** - Verify flash calculations converge at each pressure step

---

## Related Documentation

- [PVT Module Overview](../pvtsimulation/README.md)
- [Fluid Characterization](fluid_characterization.md)
- [Black Oil Flash Playbook](black_oil_flash_playbook.md)
- [Thermodynamics Guide](thermodynamics_guide.md)
