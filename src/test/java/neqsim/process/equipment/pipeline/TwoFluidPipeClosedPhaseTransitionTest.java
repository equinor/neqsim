package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Closed SRK-CPA water-dew-point transition regressions for the two-fluid thermal model. */
@Tag("slow")
class TwoFluidPipeClosedPhaseTransitionTest {
  private static final double ABSOLUTE_MASS_TOLERANCE_KG = 1.0e-7;
  private static final double RELATIVE_MASS_TOLERANCE = 1.0e-10;
  private static final UUID TRANSIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000012792");

  /**
   * Verify phase appearance and disappearance in a closed cooled and reheated pipe.
   *
   * <p>
   * The synthetic wet gas uses SRK-CPA with mixing rule 10 at 70 bara absolute. Its mole fractions are CO2 0.02,
   * nitrogen 0.01, methane {@code 0.9 - 22e-6}, ethane 0.05, propane 0.01, i-butane 0.005, n-butane 0.005, and water
   * {@code 22e-6}. The pipe is 20 m long and 0.20 m in diameter. A 5000 W/(m2 K) test heat-transfer coefficient and a 5
   * mm wall with density 1000 kg/m3 and heat capacity 100 J/(kg K) create a short, stable regression transient; they
   * are numerical test values, not a design recommendation. The mass-transfer relaxation time is 30 s.
   * </p>
   *
   * @throws Exception if the water-dew-point flash fails
   */
  @Test
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  void closedCpaCooldownAndReheatClosePhaseMassAcrossRefinement() throws Exception {
    SystemInterface wetGas = createWetGas();
    SystemInterface dewPointFluid = wetGas.clone();
    new ThermodynamicOperations(dewPointFluid).waterDewPointTemperatureMultiphaseFlash();
    double dewPointTemperatureK = dewPointFluid.getTemperature("K");

    TransitionResult coarse = runTransition("coarse", wetGas, dewPointTemperatureK, 2, 0.10);
    TransitionResult repeated = runTransition("repeat", wetGas, dewPointTemperatureK, 2, 0.10);
    TransitionResult refined = runTransition("refined", wetGas, dewPointTemperatureK, 4, 0.05);

    assertTransitionCrossed(coarse, dewPointTemperatureK);
    assertTransitionCrossed(refined, dewPointTemperatureK);
    assertEquals(coarse.cooledTemperatureK, repeated.cooledTemperatureK, 1.0e-9);
    assertEquals(coarse.reheatedTemperatureK, repeated.reheatedTemperatureK, 1.0e-9);
    assertEquals(coarse.condensedWaterKg, repeated.condensedWaterKg, 1.0e-9);
    assertEquals(coarse.evaporatedWaterKg, repeated.evaporatedWaterKg, 1.0e-9);

    double condensationScale = Math.max(Math.abs(refined.condensedWaterKg), 1.0e-12);
    double condensationRefinementError = Math.abs(coarse.condensedWaterKg - refined.condensedWaterKg)
        / condensationScale;
    assertTrue(condensationRefinementError < 0.15,
        "Time-step and mesh refinement changed condensed water by " + condensationRefinementError);
  }

  private TransitionResult runTransition(String name, SystemInterface fluidTemplate, double dewPointTemperatureK,
      int sections, double macroTimeStepSeconds) {
    SystemInterface fluid = fluidTemplate.clone();
    fluid.setTemperature(dewPointTemperatureK + 0.5, "K");

    Stream inlet = new Stream(name + "-closed-transition-inlet", fluid);
    inlet.setFlowRate(6.0, "kg/sec");
    inlet.setTemperature(dewPointTemperatureK + 0.5, "K");
    inlet.setPressure(70.0, "bara");
    inlet.run();

    TwoFluidPipe pipe = new TwoFluidPipe(name + "-closed-transition-pipe", inlet);
    pipe.setLength(20.0);
    pipe.setDiameter(0.20);
    pipe.setRoughness(1.0e-5);
    pipe.setNumberOfSections(sections);
    pipe.setTimeIntegrationMethod(TimeIntegrator.Method.EULER);
    pipe.setEnableAdaptiveTimestepping(false);
    pipe.setEnableSlugTracking(false);
    pipe.setIncludeMassTransfer(true);
    pipe.setMassTransferRelaxationTime(30.0);
    pipe.setThermodynamicUpdateInterval(1);
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    pipe.run();

    pipe.closeInlet();
    pipe.closeOutlet();
    pipe.setEnableJouleThomson(false);
    pipe.setWallProperties(0.005, 1000.0, 100.0);
    pipe.setHeatTransferCoefficient(5000.0);
    pipe.setSurfaceTemperature(dewPointTemperatureK - 10.0, "K");

    int cooldownSteps = (int) Math.round(0.30 / macroTimeStepSeconds);
    TransitionAccumulator cooldown = advanceAndAccumulate(pipe, cooldownSteps, macroTimeStepSeconds);
    double cooledTemperatureK = mean(pipe.getTemperatureProfile());

    pipe.setSurfaceTemperature(dewPointTemperatureK + 10.0, "K");
    int reheatSteps = (int) Math.round(0.60 / macroTimeStepSeconds);
    TransitionAccumulator reheat = advanceAndAccumulate(pipe, reheatSteps, macroTimeStepSeconds);
    double reheatedTemperatureK = mean(pipe.getTemperatureProfile());

    assertEquals(0.0, cooldown.oilSourceKg, ABSOLUTE_MASS_TOLERANCE_KG);
    assertEquals(0.0, reheat.oilSourceKg, ABSOLUTE_MASS_TOLERANCE_KG);
    assertEquals(cooldown.finalWaterMassKg - cooldown.initialWaterMassKg, cooldown.waterSourceKg,
        ABSOLUTE_MASS_TOLERANCE_KG);
    assertEquals(reheat.finalWaterMassKg - reheat.initialWaterMassKg, reheat.waterSourceKg, ABSOLUTE_MASS_TOLERANCE_KG);

    return new TransitionResult(cooledTemperatureK, reheatedTemperatureK, cooldown.waterSourceKg, reheat.waterSourceKg);
  }

  private TransitionAccumulator advanceAndAccumulate(TwoFluidPipe pipe, int steps, double timeStepSeconds) {
    TransitionAccumulator accumulator = new TransitionAccumulator();
    for (int step = 0; step < steps; step++) {
      pipe.runTransient(timeStepSeconds, TRANSIENT_ID);
      TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
      if (step == 0) {
        accumulator.initialWaterMassKg = report.getInitialMassKg(Phase.WATER);
      }
      accumulator.finalWaterMassKg = report.getFinalMassKg(Phase.WATER);
      accumulator.waterSourceKg += report.getSourceMassKg(Phase.WATER);
      accumulator.oilSourceKg += report.getSourceMassKg(Phase.OIL);

      assertEquals(0.0, report.getInletMassKg(Phase.TOTAL), 1.0e-12);
      assertEquals(0.0, report.getOutletMassKg(Phase.TOTAL), 1.0e-12);
      for (Phase phase : Phase.values()) {
        assertTrue(report.isWithinTolerance(phase, ABSOLUTE_MASS_TOLERANCE_KG, RELATIVE_MASS_TOLERANCE),
            phase + " residual was " + report.getResidualKg(phase) + " kg");
      }
    }
    return accumulator;
  }

  private void assertTransitionCrossed(TransitionResult result, double dewPointTemperatureK) {
    assertTrue(result.cooledTemperatureK < dewPointTemperatureK,
        "Cooldown stopped above the real SRK-CPA water dew point");
    assertTrue(result.reheatedTemperatureK > dewPointTemperatureK,
        "Reheat stopped below the real SRK-CPA water dew point");
    assertTrue(result.condensedWaterKg > 0.0, "Cooling below the dew point must condense aqueous liquid");
    assertTrue(result.evaporatedWaterKg < 0.0, "Reheating above the dew point must evaporate aqueous liquid");
  }

  private double mean(double[] values) {
    double sum = 0.0;
    for (double value : values) {
      sum += value;
    }
    return sum / values.length;
  }

  private SystemInterface createWetGas() {
    double waterMoleFraction = 22.0e-6;
    SystemInterface fluid = new SystemSrkCPAstatoil(260.15, 70.0);
    fluid.addComponent("CO2", 0.02);
    fluid.addComponent("nitrogen", 0.01);
    fluid.addComponent("methane", 0.9 - waterMoleFraction);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.01);
    fluid.addComponent("i-butane", 0.005);
    fluid.addComponent("n-butane", 0.005);
    fluid.addComponent("water", waterMoleFraction);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /** Mutable integration totals for one cooling or reheating interval. */
  private static final class TransitionAccumulator {
    private double initialWaterMassKg;
    private double finalWaterMassKg;
    private double waterSourceKg;
    private double oilSourceKg;
  }

  /** Immutable outputs used for deterministic and refinement comparisons. */
  private static final class TransitionResult {
    private final double cooledTemperatureK;
    private final double reheatedTemperatureK;
    private final double condensedWaterKg;
    private final double evaporatedWaterKg;

    private TransitionResult(double cooledTemperatureK, double reheatedTemperatureK, double condensedWaterKg,
        double evaporatedWaterKg) {
      this.cooledTemperatureK = cooledTemperatureK;
      this.reheatedTemperatureK = reheatedTemperatureK;
      this.condensedWaterKg = condensedWaterKg;
      this.evaporatedWaterKg = evaporatedWaterKg;
    }
  }
}
