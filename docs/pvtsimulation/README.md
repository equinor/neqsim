---
title: PVT Simulation Package
description: Simulate standard PVT laboratory experiments and reservoir-fluid behaviour with NeqSim.
---

The `neqsim.pvtsimulation` package contains compositional simulations for
reservoir-fluid characterization, laboratory-test reconstruction, and PVT quality
control. Build and characterize the thermodynamic system first, then give each
experiment its own clone so that one pressure path does not affect another.

## Choose a simulation

| Engineering task | Main class | Typical results |
| --- | --- | --- |
| Bubble-point or dew-point pressure | `SaturationPressure` | Saturation pressure |
| Constant-composition expansion (CCE) | `ConstantMassExpansion` | Relative volume, Y-factor, density, and compressibility |
| Constant-volume depletion (CVD) | `ConstantVolumeDepletion` | Liquid dropout, depletion, Z-factor, and material-balance checks |
| Differential liberation (DL) | `DifferentialLiberation` | Oil FVF, solution GOR, gas FVF, and oil density |
| Surface-separation study | `SeparatorTest`, `MultiStageSeparatorTest` | Stage and total GOR, oil FVF, stock-tank density, and API gravity |
| Injection-gas swelling | `SwellingTest` | Saturation pressure and relative oil volume versus injected gas |
| Minimum miscibility pressure | `MMPCalculator` | MMP, recovery curve, and interpreted miscibility mechanism |
| Viscosity pressure sweep | `ViscositySim` | Gas, oil, and aqueous viscosities |

The CCE, CVD, DL, single-stage separator, swelling, and viscosity classes use
`runCalc()`. `SaturationPressure`, `MultiStageSeparatorTest`, and `MMPCalculator`
use `run()`.

## Runnable multi-stage separator example

This complete example uses kelvin and bara in the thermodynamic-system
constructor. `setReservoirConditions` and `addSeparatorStage` use degrees Celsius
and bara.

```java
import java.util.List;
import neqsim.pvtsimulation.simulation.MultiStageSeparatorTest;
import neqsim.pvtsimulation.simulation.MultiStageSeparatorTest.SeparatorStageResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public final class PvtSeparatorQuickStart {
  private PvtSeparatorQuickStart() {}

  public static void main(String[] args) {
    SystemInterface reservoirFluid = new SystemSrkEos(373.15, 300.0);
    reservoirFluid.addComponent("nitrogen", 0.5);
    reservoirFluid.addComponent("CO2", 2.0);
    reservoirFluid.addComponent("methane", 45.0);
    reservoirFluid.addComponent("ethane", 8.0);
    reservoirFluid.addComponent("propane", 5.0);
    reservoirFluid.addComponent("i-butane", 1.5);
    reservoirFluid.addComponent("n-butane", 2.5);
    reservoirFluid.addComponent("i-pentane", 1.0);
    reservoirFluid.addComponent("n-pentane", 1.5);
    reservoirFluid.addComponent("n-hexane", 3.0);
    reservoirFluid.addComponent("n-heptane", 30.0);
    reservoirFluid.setMixingRule("classic");
    reservoirFluid.setMultiPhaseCheck(true);

    MultiStageSeparatorTest separatorTest =
        new MultiStageSeparatorTest(reservoirFluid);
    separatorTest.setReservoirConditions(300.0, 100.0);
    separatorTest.addSeparatorStage(50.0, 40.0, "HP separator");
    separatorTest.addSeparatorStage(10.0, 30.0, "LP separator");
    separatorTest.addStockTankStage();
    separatorTest.run();

    List<SeparatorStageResult> stages = separatorTest.getStageResults();
    for (SeparatorStageResult stage : stages) {
      System.out.printf(
          "%s: %.3f bara, %.2f C, cumulative GOR %.3f Sm3/Sm3%n",
          stage.getStageName(), stage.getPressure(), stage.getTemperature(),
          stage.getCumulativeGOR());
    }

    System.out.printf("Total GOR: %.3f Sm3/Sm3%n", separatorTest.getTotalGOR());
    System.out.printf("Oil FVF: %.5f m3/Sm3%n", separatorTest.getBo());
    System.out.printf(
        "Stock-tank density: %.2f kg/m3%n",
        separatorTest.getStockTankOilDensity());
  }
}
```

The repository test
`neqsim.pvtsimulation.PvtSimulationDocumentationTest` compiles and executes this
same workflow. The result magnitudes depend on the fluid characterization and
equation of state; do not use the example composition as calibrated field data.

## Working with laboratory data

1. Reproduce the laboratory composition, plus-fraction characterization, equation
   of state, mixing rule, and volume-shift choices.
2. Use the measured temperature and pressure schedule without silently changing
   gauge, absolute, standard, or reservoir units.
3. Run each experiment from a separate clone of the characterized base fluid.
4. Compare primary observables and material balance before tuning model
   parameters.
5. Record the NeqSim version, input composition, characterization settings, and
   fitted parameters with the result.

Several simulations expose experimental-data and quality-control methods. Their
calibration interfaces are experiment-specific; verify the current Java source and
Javadocs before building an automated regression workflow.

## Related documentation

- [PVT laboratory-test guide](pvt_lab_tests.md)
- [Phase-envelope guide](phase_envelope_guide.md)
- [PVT and fluid characterization](../thermo/pvt_fluid_characterization.md)
- [Eclipse E300 fluid import](eclipse_e300_fluid_import.md)
- [NeqSim JSON fluid format](json_fluid_format.md)
- [Black-oil package](../blackoil/README.md)
- [Reservoir material balance](reservoir_material_balance.md)
- [Relative-permeability tables](relative_permeability.md)
- [Flow-assurance screening](flowassurance/README.md)
