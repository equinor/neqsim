package neqsim.process.util.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import neqsim.process.safety.depressurization.PsvValveModel;
import org.junit.jupiter.api.Test;

/**
 * Tests for ReliefValveSizing class.
 *
 * @author ESOL
 */
public class ReliefValveSizingTest {
  @Test
  void testBasicPSVSizing() {
    // Size a PSV for methane gas at typical conditions
    double massFlowRate = 1.0; // kg/s
    double setPressure = 20e5; // 20 bar
    double overpressure = 0.10; // 10%
    double backPressure = 1.0e5; // Atmospheric
    double temperature = 300.0; // K
    double molecularWeight = 0.016; // kg/mol (methane)
    double Z = 0.95;
    double gamma = 1.3;

    ReliefValveSizing.PSVSizingResult result = ReliefValveSizing.calculateRequiredArea(massFlowRate, setPressure,
        overpressure, backPressure, temperature, molecularWeight, Z, gamma, false, false);

    assertNotNull(result);
    assertTrue(result.getRequiredArea() > 0, "Area should be positive");
    assertTrue(result.getRequiredAreaIn2() > 0, "Area in in² should be positive");
    assertNotNull(result.getRecommendedOrifice(), "Should recommend an orifice");
    assertTrue(result.getSelectedArea() >= result.getRequiredArea(), "Selected area should be >= required");
  }

  @Test
  void testFireCaseOverpressure() {
    // Fire case uses 21% overpressure
    double massFlowRate = 0.5; // kg/s
    double setPressure = 10e5; // 10 bar
    double overpressure = 0.21; // 21% for fire
    double backPressure = 1.0e5;
    double temperature = 350.0;
    double molecularWeight = 0.028; // kg/mol (nitrogen)
    double Z = 1.0;
    double gamma = 1.4;

    ReliefValveSizing.PSVSizingResult result = ReliefValveSizing.calculateRequiredArea(massFlowRate, setPressure,
        overpressure, backPressure, temperature, molecularWeight, Z, gamma, false, false);

    assertEquals(0.21, result.getOverpressureFraction(), 0.001);
    assertTrue(result.getBackPressureFraction() < 0.1, "Back pressure ratio should be low");
  }

  @Test
  void testBalancedBellowsPSV() {
    // Balanced bellows PSV with significant back pressure
    double massFlowRate = 2.0;
    double setPressure = 30e5;
    double overpressure = 0.10;
    double backPressure = 10e5; // ~30% of relieving pressure
    double temperature = 400.0;
    double molecularWeight = 0.044; // CO2
    double Z = 0.9;
    double gamma = 1.28;

    // With balanced bellows
    ReliefValveSizing.PSVSizingResult resultBalanced = ReliefValveSizing.calculateRequiredArea(massFlowRate,
        setPressure, overpressure, backPressure, temperature, molecularWeight, Z, gamma, true, false);

    // Without balanced bellows (conventional)
    ReliefValveSizing.PSVSizingResult resultConventional = ReliefValveSizing.calculateRequiredArea(massFlowRate,
        setPressure, overpressure, backPressure, temperature, molecularWeight, Z, gamma, false, false);

    // Balanced bellows should have better Kb factor
    assertTrue(resultBalanced.getBackPressureCorrectionFactor() >= 0.9, "Balanced bellows should maintain capacity");
  }

  @Test
  void testRuptureDiskCombination() {
    double massFlowRate = 1.5;
    double setPressure = 15e5;
    double overpressure = 0.10;
    double backPressure = 1e5;
    double temperature = 320.0;
    double molecularWeight = 0.029; // Air
    double Z = 1.0;
    double gamma = 1.4;

    // Without rupture disk
    ReliefValveSizing.PSVSizingResult resultPSVOnly = ReliefValveSizing.calculateRequiredArea(massFlowRate, setPressure,
        overpressure, backPressure, temperature, molecularWeight, Z, gamma, false, false);

    // With rupture disk
    ReliefValveSizing.PSVSizingResult resultWithDisk = ReliefValveSizing.calculateRequiredArea(massFlowRate,
        setPressure, overpressure, backPressure, temperature, molecularWeight, Z, gamma, false, true);

    assertEquals(1.0, resultPSVOnly.getCombinationCorrectionFactor(), 0.001);
    assertEquals(0.9, resultWithDisk.getCombinationCorrectionFactor(), 0.001);
    assertTrue(resultWithDisk.getRequiredArea() > resultPSVOnly.getRequiredArea(),
        "Rupture disk combo requires larger PSV");
  }

  @Test
  void gasCapacityMatchesIndependentNozzleValuesAndOpenPsv() {
    double relievingPressure = 5.0e6;
    double criticalPressure = relievingPressure * Math.pow(2.0 / 2.3, 1.3 / 0.3);
    // Independent isentropic nozzle values for 100 mm2, Kd=0.975, M=0.020 kg/mol, Z=1 and 300 K.
    double[] backPressures = {1.0e5, criticalPressure * 0.999999, criticalPressure * 1.000001, 4.0e6};
    double[] expectedKgPerS = {0.92110368, 0.92110368, 0.92110368, 0.76684207};
    for (int i = 0; i < backPressures.length; i++) {
      double capacity = ReliefValveSizing.calculateMassFlowCapacity(1.0e-4, relievingPressure, 0.0, backPressures[i],
          300.0, 0.020, 1.0, 1.3, 0.975);
      assertEquals(expectedKgPerS[i], capacity, 1.0e-6);

      PsvValveModel valve = new PsvValveModel(relievingPressure, 0.07, 0.975, 1.0e-4, backPressures[i]);
      valve.update(relievingPressure);
      assertEquals(valve.massFlowKgPerS(relievingPressure, 300.0, 0.020, 1.3, 1.0), capacity, 3.0e-5);
    }
  }

  @Test
  void forwardGasCapacityIsZeroAtOrAboveRelievingPressure() {
    // Overpressure is a fraction of the 38.98675 barg set pressure.
    for (double backPressure : new double[] {4974668.75, 5.1e6}) {
      assertEquals(0.0, ReliefValveSizing.calculateMassFlowCapacity(1.0e-4, 4.0e6, 0.25, backPressure, 300.0, 0.020,
          1.0, 1.3, 0.975));
    }
  }

  @Test
  void testChokingConditions() {
    // Test that choked flow is detected correctly
    double orificeArea = 5e-4; // m²
    double setPressure = 10e5;
    double overpressure = 0.10;
    double temperature = 350.0;
    double molecularWeight = 0.028;
    double Z = 1.0;
    double gamma = 1.4;
    double Kd = 0.975;

    // Low back pressure (choked)
    double chokedCapacity = ReliefValveSizing.calculateMassFlowCapacity(orificeArea, setPressure, overpressure, 1e5,
        temperature, molecularWeight, Z, gamma, Kd);

    // High back pressure (subsonic) - 90% of relieving pressure
    double subsonicCapacity = ReliefValveSizing.calculateMassFlowCapacity(orificeArea, setPressure, overpressure,
        0.9 * setPressure * 1.1, temperature, molecularWeight, Z, gamma, Kd);

    assertTrue(chokedCapacity > subsonicCapacity, "Choked flow should have higher capacity");
  }

  @Test
  void testDynamicFireSizing() {
    double initialMass = 1000.0; // kg
    double initialPressure = 15e5; // Pa
    double setPressure = 20e5; // Pa
    double initialTemperature = 300.0; // K
    double fireHeatInput = 500000.0; // 500 kW
    double vesselVolume = 10.0; // m³
    double molecularWeight = 0.002; // hydrogen
    double gamma = 1.4;
    double Z = 1.0;
    double Cp = 14000.0; // J/(kg*K) for hydrogen
    double blowdownTime = 900.0; // 15 minutes

    ReliefValveSizing.PSVSizingResult result = ReliefValveSizing.dynamicFireSizing(initialMass, initialPressure,
        setPressure, initialTemperature, fireHeatInput, vesselVolume, molecularWeight, gamma, Z, Cp, blowdownTime);

    assertNotNull(result);
    assertEquals(0.21, result.getOverpressureFraction(), 0.001, "Fire case uses 21% overpressure");
    assertTrue(result.getRequiredArea() > 0, "Should size a PSV");
  }

  @Test
  void testBlowdownPressure() {
    double setPressure = 20e5;
    double blowdownPercent = 7.0; // Typical PSV blowdown

    double reseatPressure = ReliefValveSizing.calculateBlowdownPressure(setPressure, blowdownPercent);

    assertEquals(setPressure * 0.93, reseatPressure, 100.0);
  }

  @Test
  void testCvCalculation() {
    double orificeArea = 1e-3; // m²
    double Kd = 0.975;

    double Cv = ReliefValveSizing.calculateCv(orificeArea, Kd);

    assertTrue(Cv > 0, "Cv should be positive");
    assertTrue(Cv > 10, "Reasonable Cv for this orifice size");
  }

  @Test
  void testStandardOrificeAreas() {
    // Test that we can get standard orifice areas
    double areaD = ReliefValveSizing.getStandardOrificeArea("D");
    double areaE = ReliefValveSizing.getStandardOrificeArea("E");
    double areaT = ReliefValveSizing.getStandardOrificeArea("T");

    assertTrue(areaD < areaE, "D should be smaller than E");
    assertTrue(areaE < areaT, "E should be smaller than T");
    assertEquals(0.110 * 6.4516e-4, areaD, 1e-7, "D orifice = 0.110 in²");
  }

  @Test
  void testNextLargerOrifice() {
    assertEquals("E", ReliefValveSizing.getNextLargerOrifice("D"));
    assertEquals("F", ReliefValveSizing.getNextLargerOrifice("E"));
    assertEquals("T", ReliefValveSizing.getNextLargerOrifice("T")); // Max stays at T
  }

  @Test
  void testValidationFireCase() {
    // Create a properly sized fire case result
    double massFlowRate = 1.0;
    double setPressure = 20e5;
    double overpressure = 0.21; // Correct for fire
    double backPressure = 1e5;
    double temperature = 350.0;
    double molecularWeight = 0.016;
    double Z = 0.95;
    double gamma = 1.3;

    ReliefValveSizing.PSVSizingResult result = ReliefValveSizing.calculateRequiredArea(massFlowRate, setPressure,
        overpressure, backPressure, temperature, molecularWeight, Z, gamma, false, false);

    String issues = ReliefValveSizing.validateSizing(result, true);

    assertTrue(issues.isEmpty() || !issues.contains("21%"), "Should not flag overpressure issue for 21%");
  }

  @Test
  void testValidationLowOverpressure() {
    // Create an improperly sized fire case with low overpressure
    double massFlowRate = 1.0;
    double setPressure = 20e5;
    double overpressure = 0.10; // Wrong for fire - should be 21%
    double backPressure = 1e5;
    double temperature = 350.0;
    double molecularWeight = 0.016;
    double Z = 0.95;
    double gamma = 1.3;

    ReliefValveSizing.PSVSizingResult result = ReliefValveSizing.calculateRequiredArea(massFlowRate, setPressure,
        overpressure, backPressure, temperature, molecularWeight, Z, gamma, false, false);

    String issues = ReliefValveSizing.validateSizing(result, true);

    assertTrue(issues.contains("21%"), "Should flag that fire case needs 21% overpressure");
  }

  /** Pressure ratios use gauge set pressure; no size is available against an equal outlet pressure. */
  @Test
  void pressureBasisAndForwardSizingBoundary() {
    double outletPa = 121325.0 + 2000.0;
    assertEquals(outletPa, ReliefValveSizing.relievingPressurePa(121325.0, 0.10), 1e-9);
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> ReliefValveSizing
        .calculateRequiredArea(1.0, 121325.0, 0.10, outletPa, 300.0, 0.020, 1.0, 1.3, false, false));
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
        () -> ReliefValveSizing.calculateLiquidReliefArea(0.01, 800.0, 121325.0, 0.10, outletPa, 0.001, false));
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> ReliefValveSizing
        .calculateTwoPhaseReliefArea(1.0, 121325.0, 0.10, outletPa, 300.0, 0.3, 12.0, 800.0, 2e5, 2e3));
  }

}
