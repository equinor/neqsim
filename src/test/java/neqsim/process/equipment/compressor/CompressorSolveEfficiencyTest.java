package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for {@link Compressor#solveEfficiency(double)}: the efficiency back-solve from a measured outlet
 * temperature must converge also when the target needs an efficiency far below the starting value (found while fitting
 * the stage efficiencies of a late-life recompression train to measured discharge temperatures).
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class CompressorSolveEfficiencyTest {

  /**
   * Creates a synthetic wet-gas compression case.
   *
   * @param startEfficiency initial polytropic efficiency
   * @return configured compressor with an initialized inlet
   */
  private Compressor createWetGasCompressor(double startEfficiency) {
    SystemInterface gas = new SystemSrkEos(273.15 + 27.0, 4.0);
    gas.addComponent("methane", 0.55);
    gas.addComponent("ethane", 0.12);
    gas.addComponent("propane", 0.12);
    gas.addComponent("n-butane", 0.06);
    gas.addComponent("n-pentane", 0.03);
    gas.addComponent("CO2", 0.02);
    gas.addComponent("water", 0.10);
    gas.setMixingRule(2);
    Stream inlet = new Stream("inlet", gas);
    inlet.setFlowRate(20000.0, "kg/hr");
    inlet.run();
    Compressor compressor = new Compressor("compressor", inlet);
    compressor.setUsePolytropicCalc(true);
    compressor.setOutletPressure(13.0, "barg");
    compressor.setPolytropicEfficiency(startEfficiency);
    return compressor;
  }

  /** Verifies recovery of the requested outlet temperature. */
  @Test
  @DisplayName("solveEfficiency reaches a hot outlet target far below the starting efficiency")
  public void testSolveEfficiencyLowEfficiencyTarget() {
    Compressor probe = createWetGasCompressor(0.40);
    probe.run();
    double targetK = probe.getOutletStream().getTemperature();
    Compressor compressor = createWetGasCompressor(0.75);
    double efficiency = compressor.solveEfficiency(targetK);
    assertEquals(0.40, efficiency, 0.01, "efficiency must be found far below the start, got " + efficiency);
    assertEquals(targetK, compressor.getOutletStream().getTemperature(), 0.1);
  }

  /** Verifies recovery of the requested outlet temperature. */
  @Test
  @DisplayName("solveEfficiency reaches a mild outlet target near the starting efficiency")
  public void testSolveEfficiencyNearTarget() {
    Compressor probe = createWetGasCompressor(0.75);
    probe.run();
    double targetK = probe.getOutletStream().getTemperature() + 5.0;
    Compressor compressor = createWetGasCompressor(0.75);
    double efficiency = compressor.solveEfficiency(targetK);
    assertTrue(efficiency < 0.75 && efficiency > 0.5, "efficiency should drop slightly, got " + efficiency);
    assertEquals(targetK, compressor.getOutletStream().getTemperature(), 0.1);
  }

  /** Verifies recovery of the requested outlet temperature. */
  @Test
  @DisplayName("solveEfficiency reaches a cold outlet target above the starting efficiency")
  public void testSolveEfficiencyHighEfficiencyTarget() {
    Compressor probe = createWetGasCompressor(0.75);
    probe.run();
    double targetK = probe.getOutletStream().getTemperature() - 4.0;
    Compressor compressor = createWetGasCompressor(0.75);
    double efficiency = compressor.solveEfficiency(targetK);
    assertTrue(efficiency > 0.75 && efficiency <= 1.0, "efficiency should rise, got " + efficiency);
    assertEquals(targetK, compressor.getOutletStream().getTemperature(), 0.1);
  }

  /** Verifies that a later run keeps the calibrated operating point and power. */
  @Test
  public void testRepeatedRunPreservesSolvedOperatingPoint() {
    Compressor probe = createWetGasCompressor(0.40);
    probe.run();
    double targetK = probe.getOutletStream().getTemperature();
    Compressor compressor = createWetGasCompressor(0.75);
    double solvedEfficiency = compressor.solveEfficiency(targetK);
    double solvedPower = compressor.getPower();
    compressor.run();
    assertEquals(targetK, compressor.getOutletStream().getTemperature(), 0.01);
    assertEquals(solvedPower, compressor.getPower(), Math.abs(solvedPower) * 1.0e-6);
    assertEquals(solvedEfficiency, compressor.getPolytropicEfficiency(), 1.0e-12);
  }

  /** Verifies that an exact starting solution is accepted without disturbing it. */
  @Test
  public void testExactTargetAtUnitEfficiency() {
    Compressor compressor = createWetGasCompressor(1.0);
    compressor.run();
    double targetK = compressor.getOutletStream().getTemperature();
    assertEquals(1.0, compressor.solveEfficiency(targetK), 1.0e-12);
    assertEquals(targetK, compressor.getOutletStream().getTemperature(), 1.0e-10);
  }

  /**
   * Verifies convergence from the upper physical efficiency bound.
   *
   * <p>
   * Wet-gas outlet temperature chatters by more than 0.01 K across the phase boundary on this polytropic path, so the
   * solve uses the 0.1 K tolerance shared with the other targets in this class.
   * </p>
   */
  @Test
  public void testSolveFromUnitEfficiency() {
    Compressor probe = createWetGasCompressor(0.40);
    probe.run();
    Compressor compressor = createWetGasCompressor(1.0);
    compressor.setEfficiencySolveTolerance(0.1);
    double targetK = probe.getOutletStream().getTemperature();
    assertEquals(0.40, compressor.solveEfficiency(targetK), 0.01);
    assertEquals(targetK, compressor.getOutletStream().getTemperature(), 0.1);
  }

  /** Verifies the isentropic mode with a different initial polytropic efficiency. */
  @Test
  public void testIsentropicEfficiencySolve() {
    Compressor probe = createWetGasCompressor(0.80);
    probe.setUsePolytropicCalc(false);
    probe.setIsentropicEfficiency(0.45);
    probe.run();
    double targetK = probe.getOutletStream().getTemperature();
    Compressor compressor = createWetGasCompressor(0.80);
    compressor.setUsePolytropicCalc(false);
    compressor.setIsentropicEfficiency(0.75);
    assertEquals(0.45, compressor.solveEfficiency(targetK), 0.001);
    compressor.run();
    assertEquals(targetK, compressor.getOutletStream().getTemperature(), 0.01);
  }

  /** Verifies failure is explicit when the target needs efficiency above unity. */
  @Test
  public void testUnreachableColdTarget() {
    Compressor probe = createWetGasCompressor(1.0);
    probe.run();
    double targetK = probe.getOutletStream().getTemperature() - 10.0;
    Compressor compressor = createWetGasCompressor(0.75);
    assertThrows(IllegalStateException.class, () -> compressor.solveEfficiency(targetK));
    compressor.run();
    assertTrue(compressor.getOutletStream().getTemperature() > targetK);
  }

  /** Verifies invalid temperature targets are rejected before simulation. */
  @Test
  public void testInvalidTargets() {
    Compressor compressor = createWetGasCompressor(0.75);
    for (double target : new double[] {Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0}) {
      assertThrows(IllegalArgumentException.class, () -> compressor.solveEfficiency(target));
    }
  }

  /** Verifies integration with the configured rigorous outlet-temperature calculation. */
  @Test
  public void testRigorousTemperatureSpecifiedMode() {
    Compressor probe = createWetGasCompressor(0.40);
    probe.run();
    double targetK = probe.getOutletStream().getTemperature();
    Compressor compressor = createWetGasCompressor(0.75);
    compressor.setOutletTemperature(targetK);
    compressor.setUseRigorousPolytropicMethod(true);
    compressor.run();
    assertEquals(0.40, compressor.getPolytropicEfficiency(), 0.01);
    double solvedEfficiency = compressor.getPolytropicEfficiency();
    assertEquals(targetK, compressor.getOutletStream().getTemperature(), 0.01);
    compressor.run();
    assertEquals(targetK, compressor.getOutletStream().getTemperature(), 0.01);
    assertEquals(solvedEfficiency, compressor.getPolytropicEfficiency(), 1.0e-12);
  }
}
