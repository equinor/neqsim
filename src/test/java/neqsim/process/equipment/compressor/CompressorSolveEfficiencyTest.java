package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for {@link Compressor#solveEfficiency(double)}: the efficiency back-solve from a measured outlet
 * temperature must converge also when the target needs an efficiency far below the starting value (found while fitting
 * the stage efficiencies of a late-life recompression train to measured discharge temperatures).
 */
public class CompressorSolveEfficiencyTest {

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
}
