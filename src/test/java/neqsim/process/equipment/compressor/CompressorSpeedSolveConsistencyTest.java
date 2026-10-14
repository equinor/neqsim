package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.NeqSimTest;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression coverage for speed-solve pressure consistency (issue #4321).
 *
 * @author Even Solbraa
 * @version 1.0
 */
class CompressorSpeedSolveConsistencyTest extends NeqSimTest {
  /**
   * Build the public synthetic chart and fluid from the issue.
   *
   * @return a configured fixed-speed compressor
   */
  private Compressor createCompressor() {
    return createCompressor("kJ/kg");
  }

  /**
   * Build equivalent chart data in either supported head unit.
   *
   * @param headUnit chart head unit, kJ/kg or meter
   * @return configured compressor
   */
  private Compressor createCompressor(String headUnit) {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 4.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("ethane", 0.2);
    fluid.addComponent("propane", 0.1);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();
    Compressor compressor = new Compressor("compressor", feed);
    compressor.setUsePolytropicCalc(true);
    double[][] flow = {{100.0, 140.0, 180.0}, {200.0, 260.0, 320.0}, {300.0, 380.0, 460.0}};
    double[][] head = {{100.0, 80.0, 60.0}, {180.0, 140.0, 100.0}, {260.0, 200.0, 140.0}};
    if ("meter".equals(headUnit)) {
      for (double[] curve : head) {
        for (int i = 0; i < curve.length; i++) {
          curve[i] *= 1000.0 / 9.81;
        }
      }
    }
    double[][] efficiency = {{70.0, 71.0, 70.0}, {72.0, 73.0, 72.0}, {74.0, 75.0, 74.0}};
    compressor.getCompressorChart().setCurves(new double[] {25.0, 300.0, 1.0, 1.0},
        new double[] {1000.0, 2000.0, 3000.0}, flow, head, flow, efficiency);
    compressor.getCompressorChart().setHeadUnit(headUnit);
    compressor.setSpeed(2000.0);
    compressor.setMinimumSpeed(1000.0);
    compressor.setMaximumSpeed(3000.0);
    return compressor;
  }

  /**
   * A returned result must agree with a separate fixed-speed calculation, including failed searches.
   *
   * @param target requested pressure in bara
   */
  @ParameterizedTest
  @ValueSource(doubles = {6.0, 8.0, 14.0})
  void outletMatchesForwardReplay(double target) {
    Compressor compressor = createCompressor();
    compressor.setOutletPressure(target, "bara");
    compressor.setSolveSpeed(true);
    UUID id = UUID.randomUUID();
    compressor.run(id);
    Compressor forward = createCompressor();
    forward.setSpeed(compressor.getSpeed());
    forward.run();
    assertEquals(forward.getOutletStream().getPressure("bara"), compressor.getOutletStream().getPressure("bara"),
        1.0e-9);
    assertEquals(forward.getPower(), compressor.getPower(), 1.0e-6);
    assertEquals(forward.getOutletStream().getTemperature(), compressor.getOutletStream().getTemperature(), 1.0e-7);
    assertEquals(target == 14.0, compressor.isSpeedSolveConverged());
    assertEquals(target == 14.0, compressor.solved());
    assertEquals(target == 14.0, compressor.isSimulationValid());
    if (target != 14.0) {
      assertTrue(compressor.getSimulationValidationErrors().stream()
          .anyMatch(error -> error.contains("Speed solve did not converge")));
    }
    assertEquals(compressor.getOutletStream().getPressure("bara") - target, compressor.getSpeedSolvePressureResidual(),
        1.0e-12);
    assertEquals(target, compressor.getOutletPressure(), 0.0);
    assertEquals(id, compressor.getCalculationIdentifier());
    assertEquals(id, compressor.getOutletStream().getCalculationIdentifier());
    assertFalse(compressor.needRecalculation());
    assertTrue(Double.isFinite(compressor.getPower()));
    assertEquals(1000.0, compressor.getOutletStream().getFlowRate("kg/hr"), 1.0e-6);
    org.apache.logging.log4j.LogManager.getLogger(getClass()).info(
        "Target {} bara: speed {} rpm, achieved {} bara, converged {}", target, compressor.getSpeed(),
        compressor.getOutletStream().getPressure("bara"), compressor.isSpeedSolveConverged());
  }

  /** Failed searches must not replace the target or leave stale successful diagnostics. */
  @Test
  void repeatedRunsKeepTargetAndResetDiagnostics() {
    Compressor compressor = createCompressor();
    assertFalse(compressor.isSpeedSolveConverged());
    assertTrue(Double.isNaN(compressor.getSpeedSolvePressureResidual()));
    compressor.setSolveSpeed(true);
    for (double target : new double[] {6.0, 14.0, 8.0, 14.0}) {
      compressor.setOutletPressure(target, "bara");
      compressor.run();
      assertEquals(target == 14.0, compressor.isSpeedSolveConverged());
      compressor.run();
      assertEquals(target, compressor.getOutletPressure(), 0.0);
      assertEquals(target == 14.0, compressor.isSpeedSolveConverged());
    }
    compressor.setSolveSpeed(false);
    compressor.run();
    assertFalse(compressor.isSpeedSolveConverged());
    assertTrue(Double.isNaN(compressor.getSpeedSolvePressureResidual()));
    assertTrue(compressor.solved());
  }

  /**
   * A bounded solve must publish the boundary's forward result without losing the target or run mode.
   *
   * @param target requested pressure in bara
   */
  @ParameterizedTest
  @ValueSource(doubles = {6.0, 100.0})
  void speedLimitProducesConsistentFailure(double target) {
    Compressor compressor = createCompressor();
    compressor.setMinimumSpeed(2000.0);
    compressor.setMaximumSpeed(2000.0);
    compressor.setLimitSpeed(true);
    compressor.setSolveSpeed(true);
    compressor.setOutletPressure(target, "bara");
    compressor.run();
    Compressor forward = createCompressor();
    forward.run();
    assertEquals(2000.0, compressor.getSpeed(), 0.0);
    assertEquals(forward.getOutletStream().getPressure("bara"), compressor.getOutletStream().getPressure("bara"),
        1.0e-9);
    assertEquals(forward.getPower(), compressor.getPower(), 1.0e-6);
    assertFalse(compressor.isSpeedSolveConverged());
    assertFalse(compressor.solved());
    assertTrue(compressor.isSolveSpeed());
    assertFalse(compressor.isCalcPressureOut());
    assertEquals(target, compressor.getOutletPressure(), 0.0);
    compressor.run();
    assertFalse(compressor.isSpeedSolveConverged());
    assertEquals(target, compressor.getOutletPressure(), 0.0);
  }

  /** Disabling limits retains the documented ability to extrapolate beyond the speed bounds. */
  @Test
  void unlimitedSearchCanConvergeOutsideBounds() {
    Compressor compressor = createCompressor();
    compressor.setMaximumSpeed(2100.0);
    compressor.setOutletPressure(14.0, "bara");
    compressor.setSolveSpeed(true);
    compressor.run();
    assertTrue(compressor.isSpeedSolveConverged());
    assertTrue(compressor.getSpeed() > compressor.getMaximumSpeed());
  }

  /** Setpoint conversion and residuals must use absolute pressure while retaining the caller's unit. */
  @Test
  void targetInMpaMatchesBaraAndSurvivesReplay() {
    Compressor compressor = createCompressor();
    compressor.setOutletPressure(1.4, "MPa");
    compressor.setSolveSpeed(true);
    for (int i = 0; i < 2; i++) {
      compressor.run();
      assertTrue(compressor.isSpeedSolveConverged());
      assertEquals(14.0, compressor.getOutletStream().getPressure("bara"), 0.001);
      assertEquals(1.4, compressor.getOutletPressure(), 0.0);
      assertEquals(compressor.getOutletStream().getPressure("bara") - 14.0, compressor.getSpeedSolvePressureResidual(),
          1.0e-12);
    }
  }

  /** A guide-vane correction must be included in both the search and its final forward result. */
  @Test
  void guideVaneSolveMatchesForwardModel() {
    Compressor forward = createCompressor();
    forward.setInletGuideVaneOpening(0.9);
    forward.setSpeed(2200.0);
    forward.run();
    Compressor compressor = createCompressor();
    compressor.setInletGuideVaneOpening(0.9);
    compressor.setOutletPressure(forward.getOutletStream().getPressure("bara"), "bara");
    compressor.setSolveSpeed(true);
    compressor.run();
    assertTrue(compressor.isSpeedSolveConverged());
    assertEquals(2200.0, compressor.getSpeed(), 0.2);
  }

  /** Anti-surge flow changes must be reflected in the final state and pressure-convergence decision. */
  @Test
  void antiSurgeResultMatchesForwardReplay() {
    Compressor compressor = createCompressor();
    Compressor forward = createCompressor();
    for (Compressor machine : new Compressor[] {compressor, forward}) {
      machine.getCompressorChart().getSurgeCurve().setCurve(new double[] {25.0, 300.0, 1.0, 1.0},
          new double[] {390.0, 400.0, 410.0}, new double[] {100.0, 200.0, 300.0});
      machine.getAntiSurge().setActive(true);
    }
    compressor.setOutletPressure(14.0, "bara");
    compressor.setSolveSpeed(true);
    compressor.run();
    forward.setSpeed(compressor.getSpeed());
    forward.run();
    assertEquals(forward.getOutletStream().getPressure("bara"), compressor.getOutletStream().getPressure("bara"),
        1.0e-9);
    assertEquals(forward.getPower(), compressor.getPower(), 1.0e-6);
    assertFalse(compressor.isSpeedSolveConverged());
    assertTrue(compressor.getAntiSurge().getCurrentSurgeFraction() > 0.0);
    assertEquals(1000.0, compressor.getOutletStream().getFlowRate("kg/hr"), 1.0e-6);
  }

  /** Invalid targets and invalid chart values cannot reuse a previous success. */
  @Test
  void invalidSearchDoesNotReportConvergence() {
    Compressor compressor = createCompressor();
    compressor.setOutletPressure(14.0, "bara");
    compressor.setSolveSpeed(true);
    compressor.run();
    assertTrue(compressor.isSpeedSolveConverged());
    compressor.setOutletPressure(Double.NaN, "bara");
    assertThrows(IllegalArgumentException.class, () -> compressor.run());
    assertFalse(compressor.isSpeedSolveConverged());
    assertTrue(Double.isNaN(compressor.getSpeedSolvePressureResidual()));
    compressor.setOutletPressure(14.0, "bara");
    compressor.setSpeed(Double.NaN);
    assertThrows(IllegalStateException.class, () -> compressor.run());
    assertFalse(compressor.isSpeedSolveConverged());
    assertTrue(compressor.isSolveSpeed());
  }

  /** Changing bounds or setpoint units must invalidate the cached pressure-target calculation. */
  @Test
  void speedLimitsAndUnitsInvalidateCachedResult() {
    Compressor compressor = createCompressor();
    compressor.setSolveSpeed(true);
    compressor.setOutletPressure(14.0, "bara");
    compressor.run();
    assertFalse(compressor.needRecalculation());
    compressor.setMaximumSpeed(2100.0);
    assertTrue(compressor.needRecalculation());
    compressor.run();
    compressor.setMinimumSpeed(1100.0);
    assertTrue(compressor.needRecalculation());
    compressor.run();
    compressor.setLimitSpeed(true);
    assertTrue(compressor.needRecalculation());
    compressor.run();
    assertFalse(compressor.isSpeedSolveConverged());
    assertEquals(14.0, compressor.getOutletPressure(), 0.0);
    compressor.setOutletPressure(14.0, "barg");
    assertTrue(compressor.needRecalculation());
  }

  /** Equivalent meter and kJ/kg maps must give the same solve result and forward power. */
  @Test
  void meterHeadMapMatchesEnergyHeadMap() {
    Compressor energyHead = createCompressor();
    Compressor meterHead = createCompressor("meter");
    for (Compressor compressor : new Compressor[] {energyHead, meterHead}) {
      compressor.setOutletPressure(14.0, "bara");
      compressor.setSolveSpeed(true);
      compressor.run();
      assertTrue(compressor.isSpeedSolveConverged());
    }
    assertEquals(energyHead.getSpeed(), meterHead.getSpeed(), 1.0e-8);
    assertEquals(energyHead.getPower(), meterHead.getPower(), 1.0e-6);
    assertEquals(energyHead.getOutletStream().getPressure("bara"), meterHead.getOutletStream().getPressure("bara"),
        1.0e-9);
  }
}
