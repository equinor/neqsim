---
title: PVT Laboratory-Test Simulations
description: Current NeqSim APIs, units, and an executable constant-mass-expansion example
---

NeqSim can reproduce common pressure-volume-temperature (PVT) laboratory experiments with
an equation-of-state fluid. Use measured composition and characterized heavy fractions as
the starting point, then compare simulated and measured values at the same temperature and
pressure schedule.

## Units and naming

The simulation classes do not all use the same setter signatures. Apply these conventions
explicitly:

| Quantity | Convention |
| --- | --- |
| EOS constructor temperature | K |
| EOS constructor pressure | bara |
| `addTBPfraction` and `addPlusFraction` molar mass | kg/mol |
| TBP/plus-fraction density | Specific gravity (numerically g/cm³); kg/m³ inputs are auto-detected and converted |
| `BasePVTsimulation.setTemperature(value, unit)` | Use `"C"` or `"K"` explicitly |
| Pressure arrays | bara |
| `SeparatorTest.setSeparatorConditions` temperature array | K |
| `ViscositySim.setTemperaturesAndPressures` temperature array | K |
| `ViscositySim` viscosity outputs | Pa·s; multiply by 1000 for cP |

Pseudo-component names must not contain `+`. For example, represent a C20-plus fraction as
`"C20"`, not `"C20+"`.

## Supported experiments

| Experiment | Class | Main results |
| --- | --- | --- |
| Constant mass expansion (CCE/CME) | `ConstantMassExpansion` | Relative volume, liquid volume as percent of saturation volume, gas Z-factor, Y-function |
| Constant volume depletion (CVD) | `ConstantVolumeDepletion` | Saturation pressure, relative volume, liquid dropout, cumulative mole-percent depletion |
| Differential liberation (DL) | `DifferentialLiberation` | Saturation pressure, Bo, Bg, Rs, oil density, gas Z-factor |
| Legacy separator test | `SeparatorTest` | Per-stage GOR and oil formation-volume factor arrays |
| Swelling test | `SwellingTest` | Pressure and relative-oil-volume arrays |
| Pressure/temperature viscosity grid | `ViscositySim` | Gas, oil, and aqueous viscosity arrays in Pa·s |

`SwellingTest` does not calculate minimum miscibility pressure (MMP). Determine MMP with a
dedicated slim-tube or multi-contact workflow; do not infer it from the swelling-factor
curve alone.

## Executable constant-mass-expansion example

This complete Java example uses SI molar masses for every characterized fraction. The
pressure schedule and returned arrays share the same index.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.pvtsimulation.simulation.ConstantMassExpansion;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public final class ConstantMassExpansionExample {
  private static final Logger logger =
      LogManager.getLogger(ConstantMassExpansionExample.class);

  private ConstantMassExpansionExample() {}

  public static void main(String[] args) {
    SystemInterface fluid = new SystemSrkEos(370.65, 350.0);
    fluid.addComponent("nitrogen", 0.39);
    fluid.addComponent("CO2", 0.30);
    fluid.addComponent("methane", 40.20);
    fluid.addComponent("ethane", 7.61);
    fluid.addComponent("propane", 7.95);
    fluid.addComponent("i-butane", 1.19);
    fluid.addComponent("n-butane", 4.08);
    fluid.addComponent("i-pentane", 1.39);
    fluid.addComponent("n-pentane", 2.15);
    fluid.addComponent("n-hexane", 2.79);
    fluid.addTBPfraction("C7", 4.28, 95.0 / 1000.0, 0.729);
    fluid.addTBPfraction("C8", 4.31, 106.0 / 1000.0, 0.749);
    fluid.addTBPfraction("C9", 3.08, 121.0 / 1000.0, 0.770);
    fluid.addTBPfraction("C10", 2.47, 135.0 / 1000.0, 0.786);
    fluid.addTBPfraction("C11", 1.91, 148.0 / 1000.0, 0.792);
    fluid.addTBPfraction("C12", 1.69, 161.0 / 1000.0, 0.804);
    fluid.addTBPfraction("C13", 1.59, 175.0 / 1000.0, 0.819);
    fluid.addTBPfraction("C14", 1.22, 196.0 / 1000.0, 0.833);
    fluid.addTBPfraction("C15", 1.25, 206.0 / 1000.0, 0.836);
    fluid.addTBPfraction("C16", 1.00, 225.0 / 1000.0, 0.843);
    fluid.addTBPfraction("C17", 0.99, 236.0 / 1000.0, 0.840);
    fluid.addTBPfraction("C18", 0.92, 245.0 / 1000.0, 0.846);
    fluid.addTBPfraction("C19", 0.60, 265.0 / 1000.0, 0.857);
    fluid.addPlusFraction("C20", 6.64, 453.0 / 1000.0, 0.918);
    fluid.getCharacterization().getLumpingModel().setNumberOfPseudoComponents(12);
    fluid.getCharacterization().characterisePlusFraction();
    fluid.setMixingRule("classic");

    double[] pressuresBara = {
      351.4, 323.2, 301.5, 275.9, 250.1, 226.1, 205.9, 197.3,
      189.3, 183.3, 165.0, 131.2, 108.3, 85.3, 55.6
    };

    ConstantMassExpansion cce = new ConstantMassExpansion(fluid);
    cce.setTemperature(97.5, "C");
    cce.setPressures(pressuresBara);
    cce.runCalc();

    double[] relativeVolume = cce.getRelativeVolume();
    double[] liquidRelativeVolume = cce.getLiquidRelativeVolume();
    double[] gasZ = cce.getZgas();
    double[] yFunction = cce.getYfactor();

    for (int i = 0; i < pressuresBara.length; i++) {
      logger.info("{} bara: Vrel={}, Vliq={} %, Zgas={}, Y={}", pressuresBara[i],
          relativeVolume[i], liquidRelativeVolume[i], gasZ[i], yFunction[i]);
    }
    logger.info("Saturation pressure: {} bara", cce.getSaturationPressure());
  }
}
```

The focused documentation regression executes this example and checks representative
finite values and array lengths. Treat calculated values as model results, not laboratory
measurements.

## Current API map

### Constant volume depletion

Configure `ConstantVolumeDepletion` with `setTemperature`, `setPressures`, and `runCalc`.
Read `getSaturationPressure`, `getRelativeVolume`, `getLiquidRelativeVolume`,
`getCummulativeMolePercDepleted`, `getZmix`, and `getZgas`. The spelling
`getCummulativeMolePercDepleted` is the current compatibility API.

### Differential liberation

Configure `DifferentialLiberation` with `setTemperature`, `setPressures`, and `runCalc`.
Read `getSaturationPressure`, `getBo`, `getBg`, `getRs`, `getOilDensity`, `getZgas`, and
`getRelGasGravity`. Result arrays align with the pressure schedule.

### Separator tests

The legacy `SeparatorTest` accepts all stages in one call:
`setSeparatorConditions(temperaturesK, pressuresBara)`. After `runCalc`, read the per-stage
arrays from `getGOR` and `getBofactor`. For named stages, reservoir-condition handling,
stock-tank results, and cumulative GOR, prefer `MultiStageSeparatorTest`; see the
[PVT simulation overview](README.md).

### Swelling tests

Set the injection fluid with `setInjectionGas`, supply cumulative injected-gas mole
percentages with `setCummulativeMolePercentGasInjected`, and call `runCalc`. Read the
pressure and relative-oil-volume arrays from `getPressures` and `getRelativeOilVolume`.

### Viscosity grids

`ViscositySim.setTemperaturesAndPressures(temperaturesK, pressuresBara)` defines paired
states. After `runCalc`, use `getGasViscosity`, `getOilViscosity`, and
`getAqueousViscosity`. These arrays are in Pa·s.

## Calibration workflow

Calibrate a fluid only against traceable measurements and keep a separate validation set.
A practical sequence is:

1. Verify composition, component names, molar-mass units, densities, and test conditions.
2. Characterize the plus fraction and freeze the chosen lumping scheme.
3. Compare untuned saturation pressure and volumetric trends with measurements.
4. Adjust only physically justified characterization or interaction parameters.
5. Re-run every experiment and report residuals, parameter bounds, and validation results.

There is no universal one-call tuning recipe for all PVT experiments. Regression choices
depend on the fluid, available measurements, and intended prediction range.

## See also

- [PVT simulation overview](README.md)
- [Fluid characterization](../thermo/pvt_fluid_characterization.md)
- [Thermodynamic models](../thermo/thermodynamic_models.md)
- [Phase-envelope guide](phase_envelope_guide.md)
