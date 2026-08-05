package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
  private static final double INITIAL_SUPERHEAT_K = 0.02;
  private static final double COOLDOWN_SURFACE_OFFSET_K = -10.0;
  private static final double REHEAT_SURFACE_OFFSET_K = 30.0;
  private static final double COOLDOWN_DURATION_SECONDS = 0.30;
  private static final double REHEAT_DURATION_SECONDS = 0.80;
  private static final UUID TRANSIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000012792");

  /**
   * Verify phase appearance and disappearance in a closed cooled and reheated pipe.
   *
   * <p>
   * The synthetic wet gas uses SRK-CPA with mixing rule 10 at 70 bara absolute. Its mole fractions are CO2 0.02,
   * nitrogen 0.01, methane {@code 0.9 - 22e-6}, ethane 0.05, propane 0.01, i-butane 0.005, n-butane 0.005, and water
   * {@code 22e-6}. The pipe is 20 m long and 0.20 m in diameter. A 5000 W/(m2 K) test heat-transfer coefficient and a 5
   * mm wall with density 1000 kg/m3 and heat capacity 100 J/(kg K) create a short, stable regression transient; they
   * are numerical test values, not a design recommendation. The fluid starts 0.02 K above the calculated water dew
   * point, cools for 0.30 s against a surface 10 K below it, then reheats for 0.80 s against a surface 30 K above it.
   * The mass-transfer relaxation time is 30 s.
   * </p>
   *
   * @throws Exception if the water-dew-point flash fails
   */
  @Test
  @Timeout(value = 10, unit = TimeUnit.MINUTES)
  void closedCpaCooldownAndReheatClosePhaseMassAcrossRefinement() throws Exception {
    SystemInterface wetGas = createWetGas();
    SystemInterface dewPointFluid = wetGas.clone();
    new ThermodynamicOperations(dewPointFluid).waterDewPointTemperatureMultiphaseFlash();
    double dewPointTemperatureK = dewPointFluid.getTemperature("K");

    TransitionResult coarse = runTransition("coarse", wetGas, dewPointTemperatureK, 2, 0.10, true);
    TransitionResult repeated = runTransition("repeat", wetGas, dewPointTemperatureK, 2, 0.10, false);
    TransitionResult refined = runTransition("refined", wetGas, dewPointTemperatureK, 4, 0.05, false);

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

  @Test
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  void closedCpaCooldownWithMassTransferDisabledKeepsPhaseInventories() throws Exception {
    SystemInterface wetGas = createWetGas();
    SystemInterface dewPointFluid = wetGas.clone();
    new ThermodynamicOperations(dewPointFluid).waterDewPointTemperatureMultiphaseFlash();
    double dewPointTemperatureK = dewPointFluid.getTemperature("K");
    TwoFluidPipe pipe = createClosedTransitionPipe("disabled-transfer", wetGas, dewPointTemperatureK, 2, false);

    for (int step = 0; step < 3; step++) {
      pipe.runTransient(0.10, TRANSIENT_ID);
      TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
      for (Phase phase : Phase.values()) {
        assertEquals(0.0, report.getSourceMassKg(phase), ABSOLUTE_MASS_TOLERANCE_KG);
        assertEquals(0.0, report.getInletMassKg(phase), 1.0e-12);
        assertEquals(0.0, report.getOutletMassKg(phase), 1.0e-12);
        assertEquals(report.getInitialMassKg(phase), report.getFinalMassKg(phase), ABSOLUTE_MASS_TOLERANCE_KG);
        assertTrue(report.isWithinTolerance(phase, ABSOLUTE_MASS_TOLERANCE_KG, RELATIVE_MASS_TOLERANCE));
      }
    }

    assertTrue(mean(pipe.getTemperatureProfile()) < dewPointTemperatureK,
        "The disabled-transfer control must still cross below the SRK-CPA water dew point");
  }

  private TransitionResult runTransition(String name, SystemInterface fluidTemplate, double dewPointTemperatureK,
      int sections, double macroTimeStepSeconds, boolean validateSerializedCopy) {
    TwoFluidPipe pipe = createClosedTransitionPipe(name, fluidTemplate, dewPointTemperatureK, sections, true);

    int cooldownSteps = (int) Math.round(COOLDOWN_DURATION_SECONDS / macroTimeStepSeconds);
    TransitionAccumulator cooldown = advanceAndAccumulate(pipe, cooldownSteps, macroTimeStepSeconds);
    double cooledTemperatureK = mean(pipe.getTemperatureProfile());
    TwoFluidPipe copied = validateSerializedCopy ? (TwoFluidPipe) pipe.copy() : null;

    pipe.setSurfaceTemperature(dewPointTemperatureK + REHEAT_SURFACE_OFFSET_K, "K");
    int reheatSteps = (int) Math.round(REHEAT_DURATION_SECONDS / macroTimeStepSeconds);
    TransitionAccumulator reheat = advanceAndAccumulate(pipe, reheatSteps, macroTimeStepSeconds);

    if (copied != null) {
      copied.setSurfaceTemperature(dewPointTemperatureK + REHEAT_SURFACE_OFFSET_K, "K");
      TransitionAccumulator copiedReheat = advanceAndAccumulate(copied, reheatSteps, macroTimeStepSeconds);
      assertArrayEquals(pipe.getTemperatureProfile(), copied.getTemperatureProfile(), 1.0e-9);
      assertArrayEquals(pipe.getOilHoldupProfile(), copied.getOilHoldupProfile(), 1.0e-12);
      assertArrayEquals(pipe.getWaterHoldupProfile(), copied.getWaterHoldupProfile(), 1.0e-12);
      assertEquals(reheat.waterSourceKg, copiedReheat.waterSourceKg, 1.0e-9);
      assertEquals(reheat.condensedWaterKg, copiedReheat.condensedWaterKg, 1.0e-9);
      assertEquals(reheat.evaporatedWaterKg, copiedReheat.evaporatedWaterKg, 1.0e-9);
      assertEquals(reheat.oilSourceKg, copiedReheat.oilSourceKg, 1.0e-9);
    }
    double reheatedTemperatureK = mean(pipe.getTemperatureProfile());

    assertEquals(0.0, cooldown.oilSourceKg, ABSOLUTE_MASS_TOLERANCE_KG);
    assertEquals(0.0, reheat.oilSourceKg, ABSOLUTE_MASS_TOLERANCE_KG);
    assertEquals(cooldown.finalWaterMassKg - cooldown.initialWaterMassKg, cooldown.waterSourceKg,
        ABSOLUTE_MASS_TOLERANCE_KG);
    assertEquals(reheat.finalWaterMassKg - reheat.initialWaterMassKg, reheat.waterSourceKg, ABSOLUTE_MASS_TOLERANCE_KG);

    return new TransitionResult(cooledTemperatureK, reheatedTemperatureK, cooldown.condensedWaterKg,
        reheat.evaporatedWaterKg);
  }

  private TwoFluidPipe createClosedTransitionPipe(String name, SystemInterface fluidTemplate,
      double dewPointTemperatureK, int sections, boolean includeMassTransfer) {
    SystemInterface fluid = fluidTemplate.clone();
    fluid.setTemperature(dewPointTemperatureK + INITIAL_SUPERHEAT_K, "K");

    Stream inlet = new Stream(name + "-closed-transition-inlet", fluid);
    inlet.setFlowRate(6.0, "kg/sec");
    inlet.setTemperature(dewPointTemperatureK + INITIAL_SUPERHEAT_K, "K");
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
    pipe.setIncludeMassTransfer(includeMassTransfer);
    pipe.setMassTransferRelaxationTime(30.0);
    pipe.setThermodynamicUpdateInterval(1);
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    pipe.run();

    pipe.closeInlet();
    pipe.closeOutlet();
    pipe.setEnableJouleThomson(false);
    pipe.setWallProperties(0.005, 1000.0, 100.0);
    pipe.setHeatTransferCoefficient(5000.0);
    pipe.setSurfaceTemperature(dewPointTemperatureK + COOLDOWN_SURFACE_OFFSET_K, "K");
    return pipe;
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
      double waterSourceKg = report.getSourceMassKg(Phase.WATER);
      accumulator.waterSourceKg += waterSourceKg;
      accumulator.condensedWaterKg += Math.max(waterSourceKg, 0.0);
      accumulator.evaporatedWaterKg += Math.min(waterSourceKg, 0.0);
      accumulator.oilSourceKg += report.getSourceMassKg(Phase.OIL);

      assertEquals(0.0, report.getInletMassKg(Phase.TOTAL), 1.0e-12);
      assertEquals(0.0, report.getOutletMassKg(Phase.TOTAL), 1.0e-12);
      assertEquals(0.0, report.getSourceMassKg(Phase.TOTAL), 1.0e-12);
      assertEquals(report.getInitialMassKg(Phase.TOTAL), report.getFinalMassKg(Phase.TOTAL),
          ABSOLUTE_MASS_TOLERANCE_KG);
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
    private double condensedWaterKg;
    private double evaporatedWaterKg;
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
