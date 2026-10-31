---
title: PVT Simulation Package
description: Simulate standard PVT laboratory experiments and reservoir-fluid behaviour with NeqSim.
---

The `neqsim.pvtsimulation` package contains compositional simulations for
reservoir-fluid characterization, laboratory-test reconstruction, and PVT quality
control. Build and characterize the thermodynamic system first, then give each
experiment its own clone so that one pressure path does not affect another.

See the [Pedersen-method qualification matrix](pedersen_method_qualification)
for the distinction between implemented APIs, numerical verification and measured
accuracy, including current gas-injection limitations.

For a complete sample-to-model study with professional reporting, full parameter
capture and fresh-process E300 replay, use the
[PVT and fluid-characterization study workflow](pvt_fluid_study_report).

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

<!-- pvt-separator-quickstart-example: multi-stage -->
```java
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.pvtsimulation.simulation.MultiStageSeparatorTest;
import neqsim.pvtsimulation.simulation.MultiStageSeparatorTest.SeparatorStageResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Runs the PVT landing-page separator workflow.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class PvtSeparatorQuickStart {
  private static final Logger logger =
      LogManager.getLogger(PvtSeparatorQuickStart.class);

  private PvtSeparatorQuickStart() {}

  /**
   * Runs the example and reports its stage results.
   *
   * @param args command-line arguments; not used
   */
  public static void main(String[] args) {
    MultiStageSeparatorTest separatorTest = runExample();
    List<SeparatorStageResult> stages = separatorTest.getStageResults();
    for (SeparatorStageResult stage : stages) {
      logger.info(
          "{}: {} bara, {} C, cumulative GOR {} Sm3/Sm3",
          stage.getStageName(), stage.getPressure(), stage.getTemperature(),
          stage.getCumulativeGOR());
    }

    logger.info("Total GOR: {} Sm3/Sm3", separatorTest.getTotalGOR());
    logger.info("Oil FVF: {} m3/Sm3", separatorTest.getBo());
    logger.info(
        "Stock-tank density: {} kg/m3",
        separatorTest.getStockTankOilDensity());
  }

  /**
   * Builds and executes the documented multi-stage separator study.
   *
   * @return completed separator study
   */
  public static MultiStageSeparatorTest runExample() {
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
    return separatorTest;
  }
}
```

The repository test
`neqsim.pvtsimulation.PvtSimulationDocumentationTest` extracts, compiles, and executes this exact
marked source, then checks the returned stage results. The result magnitudes depend on the fluid characterization and
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

- [PVT laboratory-test guide](pvt_lab_tests)
- [Phase-envelope guide](phase_envelope_guide)
- [PVT and fluid characterization](../thermo/pvt_fluid_characterization)
- [Eclipse E300 fluid import](eclipse_e300_fluid_import)
- [NeqSim JSON fluid format](json_fluid_format)
- [Black-oil package](../blackoil/README)
- [Reservoir material balance, generalized Rs/Rv history estimates and tuned PVT](reservoir_material_balance)
- [Relative-permeability tables](relative_permeability)
- [Flow-assurance screening](flowassurance/README)
