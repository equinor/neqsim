package neqsim.pvtsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Test class for SolutionGasWaterRatio (Rsw) calculations.
 */
public class SolutionGasWaterRatioTest {

  private SystemInterface gasSystem;

  @BeforeEach
  void setUp() {
    // Create a typical reservoir gas system
    gasSystem = new SystemSrkCPAstatoil(373.15, 200.0);
    gasSystem.addComponent("methane", 0.85);
    gasSystem.addComponent("ethane", 0.08);
    gasSystem.addComponent("propane", 0.04);
    gasSystem.addComponent("CO2", 0.03);
    gasSystem.setMixingRule(10);
  }

  @Test
  void testConstructor() {
    SolutionGasWaterRatio rswCalc = new SolutionGasWaterRatio(gasSystem);
    assertNotNull(rswCalc);
    assertNotNull(rswCalc.getThermoSystem());
  }

  @Test
  void testSetCalculationMethod() {
    SolutionGasWaterRatio rswCalc = new SolutionGasWaterRatio(gasSystem);

    rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.MCCAIN);
    assertEquals(SolutionGasWaterRatio.CalculationMethod.MCCAIN, rswCalc.getCalculationMethod());

    rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.SOREIDE_WHITSON);
    assertEquals(SolutionGasWaterRatio.CalculationMethod.SOREIDE_WHITSON,
        rswCalc.getCalculationMethod());

    rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.ELECTROLYTE_CPA);
    assertEquals(SolutionGasWaterRatio.CalculationMethod.ELECTROLYTE_CPA,
        rswCalc.getCalculationMethod());
  }

  @Test
  void testSetCalculationMethodByString() {
    SolutionGasWaterRatio rswCalc = new SolutionGasWaterRatio(gasSystem);

    rswCalc.setCalculationMethod("McCain");
    assertEquals(SolutionGasWaterRatio.CalculationMethod.MCCAIN, rswCalc.getCalculationMethod());

    rswCalc.setCalculationMethod("Culberson-McKetta");
    assertEquals(SolutionGasWaterRatio.CalculationMethod.MCCAIN, rswCalc.getCalculationMethod());

    rswCalc.setCalculationMethod("Soreide-Whitson");
    assertEquals(SolutionGasWaterRatio.CalculationMethod.SOREIDE_WHITSON,
        rswCalc.getCalculationMethod());

    rswCalc.setCalculationMethod("Electrolyte-CPA");
    assertEquals(SolutionGasWaterRatio.CalculationMethod.ELECTROLYTE_CPA,
        rswCalc.getCalculationMethod());
  }

  @Test
  void testSetSalinityUnits() {
    SolutionGasWaterRatio rswCalc = new SolutionGasWaterRatio(gasSystem);

    // Test molality
    rswCalc.setSalinity(0.6, "molal");
    assertEquals(0.6, rswCalc.getSalinity(), 1e-10);

    // Test wt%
    rswCalc.setSalinity(3.5, "wt%");
    // 3.5 wt% NaCl ≈ 0.62 molal
    assertTrue(rswCalc.getSalinity() > 0.5 && rswCalc.getSalinity() < 0.7);

    // Test ppm
    rswCalc.setSalinity(35000, "ppm");
    // 35000 ppm ≈ 0.6 molal
    assertTrue(rswCalc.getSalinity() > 0.5 && rswCalc.getSalinity() < 0.7);
  }

  @Test
  void testInvalidMethodName() {
    SolutionGasWaterRatio rswCalc = new SolutionGasWaterRatio(gasSystem);
    assertThrows(IllegalArgumentException.class,
        () -> rswCalc.setCalculationMethod("InvalidMethod"));
  }

  @Test
  void testInvalidSalinityUnit() {
    SolutionGasWaterRatio rswCalc = new SolutionGasWaterRatio(gasSystem);
    assertThrows(IllegalArgumentException.class, () -> rswCalc.setSalinity(1.0, "invalid"));
  }

  @Test
  void testMcCainCorrelationPureWater() {
    SolutionGasWaterRatio rswCalc = new SolutionGasWaterRatio(gasSystem);
    rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.MCCAIN);
    rswCalc.setSalinity(0.0); // Pure water

    double[] temps = {373.15}; // 100°C
    double[] pres = {100.0}; // 100 bara
    rswCalc.setTemperaturesAndPressures(temps, pres);
    rswCalc.runCalc();

    double[] rsw = rswCalc.getRsw();
    assertNotNull(rsw);
    assertEquals(1, rsw.length);

    // Rsw should be positive for methane-water system at these conditions
    assertTrue(rsw[0] > 0, "Rsw should be positive");
    // McCain correlation typically gives Rsw in range 0.5-5 Sm3/Sm3 at these conditions
    assertTrue(rsw[0] < 10, "Rsw should be reasonable (< 10 Sm3/Sm3)");
  }

  @Test
  void testMcCainCorrelationWithSalinity() {
    SolutionGasWaterRatio rswCalc = new SolutionGasWaterRatio(gasSystem);
    rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.MCCAIN);

    double[] temps = {373.15}; // 100°C
    double[] pres = {100.0}; // 100 bara

    // Calculate for pure water
    rswCalc.setSalinity(0.0);
    rswCalc.setTemperaturesAndPressures(temps, pres);
    rswCalc.runCalc();
    double rswPure = rswCalc.getRsw(0);

    // Calculate for saline water (3.5 wt% NaCl)
    rswCalc.setSalinity(3.5, "wt%");
    rswCalc.runCalc();
    double rswSaline = rswCalc.getRsw(0);

    // Salinity should reduce gas solubility (salting-out effect)
    assertTrue(rswSaline < rswPure, "Salinity should reduce Rsw (salting-out effect)");
  }

  @Test
  void testRswIncreaseWithPressure() {
    SolutionGasWaterRatio rswCalc = new SolutionGasWaterRatio(gasSystem);
    rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.MCCAIN);
    rswCalc.setSalinity(0.0);

    double[] temps = {373.15, 373.15, 373.15};
    double[] pres = {50.0, 100.0, 200.0};
    rswCalc.setTemperaturesAndPressures(temps, pres);
    rswCalc.runCalc();

    double[] rsw = rswCalc.getRsw();
    // Rsw should increase with pressure
    assertTrue(rsw[1] > rsw[0], "Rsw should increase with pressure");
    assertTrue(rsw[2] > rsw[1], "Rsw should increase with pressure");
  }

  @Test
  void testSoreideWhitsonMethod() {
    // Create a gas system matching the Søreide-Whitson system requirements
    SystemInterface simpleGas = new SystemSrkCPAstatoil(323.15, 100.0);
    simpleGas.addComponent("methane", 0.90);
    simpleGas.addComponent("CO2", 0.05);
    simpleGas.addComponent("ethane", 0.05);
    simpleGas.setMixingRule(10);

    SolutionGasWaterRatio rswCalc = new SolutionGasWaterRatio(simpleGas);
    rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.SOREIDE_WHITSON);
    rswCalc.setSalinity(0.0);

    double[] temps = {323.15}; // 50°C
    double[] pres = {50.0}; // 50 bara
    rswCalc.setTemperaturesAndPressures(temps, pres);
    rswCalc.runCalc();

    double[] rsw = rswCalc.getRsw();
    assertNotNull(rsw);
    assertEquals(1, rsw.length);
    // Rsw should be non-negative
    assertTrue(rsw[0] >= 0, "Rsw should be non-negative");
  }

  @Test
  void testElectrolyteCPAMethodPureWater() {
    SolutionGasWaterRatio rswCalc = new SolutionGasWaterRatio(gasSystem);
    rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.ELECTROLYTE_CPA);
    rswCalc.setSalinity(0.0); // Pure water uses regular CPA

    double[] temps = {323.15}; // 50°C
    double[] pres = {50.0}; // 50 bara
    rswCalc.setTemperaturesAndPressures(temps, pres);
    rswCalc.runCalc();

    double[] rsw = rswCalc.getRsw();
    assertNotNull(rsw);
    assertEquals(1, rsw.length);
    // Rsw should be non-negative
    assertTrue(rsw[0] >= 0, "Rsw should be non-negative");
  }

  @Test
  void testMismatchedArrayLengths() {
    SolutionGasWaterRatio rswCalc = new SolutionGasWaterRatio(gasSystem);

    double[] temps = {373.15, 373.15};
    double[] pres = {100.0};

    assertThrows(IllegalArgumentException.class,
        () -> rswCalc.setTemperaturesAndPressures(temps, pres));
  }

  @Test
  void testNullMethodName() {
    SolutionGasWaterRatio rswCalc = new SolutionGasWaterRatio(gasSystem);
    assertThrows(IllegalArgumentException.class, () -> rswCalc.setCalculationMethod((String) null));
  }

  @Test
  void testNullSalinityUnit() {
    SolutionGasWaterRatio rswCalc = new SolutionGasWaterRatio(gasSystem);
    assertThrows(IllegalArgumentException.class, () -> rswCalc.setSalinity(1.0, null));
  }
}
