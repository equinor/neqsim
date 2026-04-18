package neqsim.physicalproperties.methods.diffusivity;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Validation of diffusivity models against published experimental data.
 *
 * <p>
 * Gas-phase reference data from:
 * </p>
 * <ul>
 * <li>Marrero, T.R. and Mason, E.A. (1972). J. Phys. Chem. Ref. Data, 1(1), 3-118.</li>
 * <li>Poling, B.E., Prausnitz, J.M. and O'Connell, J.P. (2001). "The Properties of Gases and
 * Liquids", 5th ed., McGraw-Hill, Tables 11-2 through 11-4.</li>
 * </ul>
 *
 * <p>
 * Liquid-phase reference data from:
 * </p>
 * <ul>
 * <li>Poling et al. (2001), Table 11-3.</li>
 * <li>Cussler, E.L. (2009). "Diffusion: Mass Transfer in Fluid Systems", 3rd ed.</li>
 * </ul>
 *
 * @author Even Solbraa
 */
public class DiffusivityExperimentalValidationTest {

  // Gas-phase experimental data at ~1 atm, in m2/s
  // CH4-N2 at 298 K: 0.22 cm2/s (Marrero & Mason 1972)
  private static final double EXP_CH4_N2_298K = 2.2e-5;
  // CO2-N2 at 298 K: 0.167 cm2/s (Poling 2001, Table 11-4)
  private static final double EXP_CO2_N2_298K = 1.67e-5;

  // Liquid-phase experimental data at 25C, in m2/s
  // CO2 in water at 25C: 1.92e-5 cm2/s (Poling 2001, Table 11-3; Tamimi 1994)
  private static final double EXP_CO2_WATER_298K = 1.92e-9;

  // Correlation accuracy: gas ±20%, liquid ±40%
  private static final double GAS_TOL = 0.25;
  private static final double LIQ_TOL = 0.50;

  // ---------- Gas-phase validation ----------

  @Test
  void testChapmanEnskogCH4N2() {
    SystemInterface sys = createBinaryGas("methane", "nitrogen", 298.15, 1.01325);
    double D = getGasDiffusionCoefficient(sys, 0, 1, "Chapman-Enskog");
    assertGasAccuracy("CE CH4-N2", D, EXP_CH4_N2_298K, GAS_TOL);
  }

  @Test
  void testChapmanEnskogCO2N2() {
    SystemInterface sys = createBinaryGas("CO2", "nitrogen", 298.15, 1.01325);
    double D = getGasDiffusionCoefficient(sys, 0, 1, "Chapman-Enskog");
    assertGasAccuracy("CE CO2-N2", D, EXP_CO2_N2_298K, GAS_TOL);
  }

  @Test
  void testFullerCH4N2() {
    SystemInterface sys = createBinaryGas("methane", "nitrogen", 298.15, 1.01325);
    double D = getGasDiffusionCoefficient(sys, 0, 1, "Fuller-Schettler-Giddings");
    assertGasAccuracy("Fuller CH4-N2", D, EXP_CH4_N2_298K, GAS_TOL);
  }

  @Test
  void testFullerCO2N2() {
    SystemInterface sys = createBinaryGas("CO2", "nitrogen", 298.15, 1.01325);
    double D = getGasDiffusionCoefficient(sys, 0, 1, "Fuller-Schettler-Giddings");
    assertGasAccuracy("Fuller CO2-N2", D, EXP_CO2_N2_298K, GAS_TOL);
  }

  @Test
  void testWilkeLeeCH4N2() {
    SystemInterface sys = createBinaryGas("methane", "nitrogen", 298.15, 1.01325);
    double D = getGasDiffusionCoefficient(sys, 0, 1, "Wilke Lee");
    assertGasAccuracy("WL CH4-N2", D, EXP_CH4_N2_298K, GAS_TOL);
  }

  @Test
  void testWilkeLeeCO2N2() {
    SystemInterface sys = createBinaryGas("CO2", "nitrogen", 298.15, 1.01325);
    double D = getGasDiffusionCoefficient(sys, 0, 1, "Wilke Lee");
    assertGasAccuracy("WL CO2-N2", D, EXP_CO2_N2_298K, GAS_TOL);
  }

  // Verify inverse pressure dependence: D ~ 1/P
  @Test
  void testFullerPressureDependence() {
    SystemInterface sys1 = createBinaryGas("methane", "nitrogen", 298.15, 1.01325);
    double D1 = getGasDiffusionCoefficient(sys1, 0, 1, "Fuller-Schettler-Giddings");

    SystemInterface sys5 = createBinaryGas("methane", "nitrogen", 298.15, 5.0);
    double D5 = getGasDiffusionCoefficient(sys5, 0, 1, "Fuller-Schettler-Giddings");

    // D should scale as 1/P: D1/D5 should be close to 5/1.01325
    double expectedRatio = 5.0 / 1.01325;
    double actualRatio = D1 / D5;
    double ratioError = Math.abs(actualRatio - expectedRatio) / expectedRatio;
    assertTrue(ratioError < 0.01,
        "1/P scaling: expected ratio " + expectedRatio + ", got " + actualRatio);
  }

  // Verify T^1.75 dependence for Fuller
  @Test
  void testFullerTemperatureDependence() {
    SystemInterface sys300 = createBinaryGas("methane", "nitrogen", 300.0, 1.01325);
    double D300 = getGasDiffusionCoefficient(sys300, 0, 1, "Fuller-Schettler-Giddings");

    SystemInterface sys400 = createBinaryGas("methane", "nitrogen", 400.0, 1.01325);
    double D400 = getGasDiffusionCoefficient(sys400, 0, 1, "Fuller-Schettler-Giddings");

    // D ~ T^1.75, so D400/D300 should be (400/300)^1.75
    double expectedRatio = Math.pow(400.0 / 300.0, 1.75);
    double actualRatio = D400 / D300;
    double ratioError = Math.abs(actualRatio - expectedRatio) / expectedRatio;
    assertTrue(ratioError < 0.01,
        "T^1.75 scaling: expected ratio " + expectedRatio + ", got " + actualRatio);
  }

  // ---------- Liquid-phase validation ----------

  @Test
  void testSiddiqiLucasCO2Water() {
    double D = getLiquidDiffusionCO2Water("Siddiqi Lucas");
    assertLiquidAccuracy("SL CO2-water", D, EXP_CO2_WATER_298K, LIQ_TOL);
  }

  @Test
  void testWilkeChangCO2Water() {
    double D = getLiquidDiffusionCO2Water("Wilke-Chang");
    assertLiquidAccuracy("WC CO2-water", D, EXP_CO2_WATER_298K, LIQ_TOL);
  }

  @Test
  void testHaydukMinhasCO2Water() {
    double D = getLiquidDiffusionCO2Water("Hayduk-Minhas");
    assertLiquidAccuracy("HM CO2-water", D, EXP_CO2_WATER_298K, LIQ_TOL);
  }

  // All liquid models should agree within a factor of 5
  @Test
  void testLiquidModelsAgreement() {
    double dSL = getLiquidDiffusionCO2Water("Siddiqi Lucas");
    double dWC = getLiquidDiffusionCO2Water("Wilke-Chang");
    double dHM = getLiquidDiffusionCO2Water("Hayduk-Minhas");
    double dTC = getLiquidDiffusionCO2Water("Tyn-Calus");

    assertModelsAgree("WC/SL", dWC, dSL, 5.0);
    assertModelsAgree("HM/SL", dHM, dSL, 5.0);
    assertModelsAgree("TC/SL", dTC, dSL, 5.0);
  }

  // All gas models should agree within a factor of 2
  @Test
  void testGasModelsAgreement() {
    SystemInterface sys = createBinaryGas("methane", "nitrogen", 298.15, 1.01325);
    double dCE = getGasDiffusionCoefficient(sys, 0, 1, "Chapman-Enskog");

    sys = createBinaryGas("methane", "nitrogen", 298.15, 1.01325);
    double dFSG = getGasDiffusionCoefficient(sys, 0, 1, "Fuller-Schettler-Giddings");

    sys = createBinaryGas("methane", "nitrogen", 298.15, 1.01325);
    double dWL = getGasDiffusionCoefficient(sys, 0, 1, "Wilke Lee");

    assertModelsAgree("FSG/CE", dFSG, dCE, 2.0);
    assertModelsAgree("WL/CE", dWL, dCE, 2.0);
  }

  // ---------- Helper methods ----------

  private SystemInterface createBinaryGas(String comp1, String comp2, double T, double P) {
    SystemInterface sys = new SystemSrkEos(T, P);
    sys.addComponent(comp1, 0.5);
    sys.addComponent(comp2, 0.5);
    sys.createDatabase(true);
    sys.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    sys.initPhysicalProperties();
    return sys;
  }

  private double getGasDiffusionCoefficient(SystemInterface sys, int i, int j, String model) {
    assertTrue(sys.hasPhaseType("gas"), "Gas phase should exist");
    sys.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel(model);
    return sys.getPhase("gas").getPhysicalProperties().diffusivityCalc
        .calcBinaryDiffusionCoefficient(i, j, 0);
  }

  private double getLiquidDiffusionCO2Water(String model) {
    SystemInterface sys = new SystemSrkEos(298.15, 1.01325);
    sys.addComponent("CO2", 0.01);
    sys.addComponent("water", 0.99);
    sys.createDatabase(true);
    sys.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    sys.initPhysicalProperties();

    assertTrue(sys.hasPhaseType("aqueous"), "Aqueous phase should exist");
    sys.getPhase("aqueous").getPhysicalProperties().setDiffusionCoefficientModel(model);
    // CO2 is solute (i=0), water is solvent (j=1)
    double D = sys.getPhase("aqueous").getPhysicalProperties().diffusivityCalc
        .calcBinaryDiffusionCoefficient(0, 1, 0);
    System.out.println(model + " CO2/water: D = " + D + " m2/s (" + (D * 1e4) + " cm2/s), exp = "
        + EXP_CO2_WATER_298K + " m2/s");
    return D;
  }

  private void assertGasAccuracy(String label, double calc, double exp, double tol) {
    double error = Math.abs(calc - exp) / exp;
    System.out.println(label + ": calc=" + (calc * 1e4) + " cm2/s, exp=" + (exp * 1e4)
        + " cm2/s, error=" + String.format("%.1f", error * 100) + "%");
    assertTrue(error < tol,
        label + " error " + String.format("%.1f", error * 100) + "% exceeds " + (tol * 100) + "%");
  }

  private void assertLiquidAccuracy(String label, double calc, double exp, double tol) {
    double error = Math.abs(calc - exp) / exp;
    System.out.println(label + ": calc=" + calc + " m2/s, exp=" + exp + " m2/s, error="
        + String.format("%.1f", error * 100) + "%");
    assertTrue(calc > 1e-12 && calc < 1e-6,
        label + " value " + calc + " outside reasonable liquid D range");
    assertTrue(error < tol,
        label + " error " + String.format("%.1f", error * 100) + "% exceeds " + (tol * 100) + "%");
  }

  private void assertModelsAgree(String label, double d1, double d2, double maxRatio) {
    double ratio = d1 / d2;
    assertTrue(ratio > 1.0 / maxRatio && ratio < maxRatio,
        label + " ratio " + ratio + " outside [" + (1.0 / maxRatio) + ", " + maxRatio + "]");
  }
}
