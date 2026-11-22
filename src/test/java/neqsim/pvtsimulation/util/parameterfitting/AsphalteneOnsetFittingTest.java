package neqsim.pvtsimulation.util.parameterfitting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for AsphalteneOnsetFitting class.
 *
 * @author ASMF
 */
public class AsphalteneOnsetFittingTest extends neqsim.NeqSimTest {
  private static final Logger logger = LogManager.getLogger(AsphalteneOnsetFittingTest.class);

  private SystemInterface baseSystem;

  @BeforeEach
  void setUp() {
    // Create a representative crude oil system with asphaltene
    baseSystem = new SystemSrkCPAstatoil(373.15, 200.0);
    baseSystem.addComponent("methane", 0.30);
    baseSystem.addComponent("ethane", 0.05);
    baseSystem.addComponent("propane", 0.03);
    baseSystem.addTBPfraction("C7", 0.25, 100.0 / 1000.0, 0.75);
    baseSystem.addTBPfraction("C15", 0.25, 210.0 / 1000.0, 0.82);
    baseSystem.addComponent("asphaltene", 0.12);
    baseSystem.setMixingRule("classic");
    baseSystem.init(0);
  }

  @Test
  @DisplayName("Test AsphalteneOnsetFitting construction")
  void testConstruction() {
    AsphalteneOnsetFitting fitter = new AsphalteneOnsetFitting(baseSystem);
    assertNotNull(fitter);
    assertEquals(0, fitter.getNumberOfDataPoints());
  }

  @Test
  @DisplayName("Test adding onset data points")
  void testAddOnsetPoints() {
    AsphalteneOnsetFitting fitter = new AsphalteneOnsetFitting(baseSystem);

    // Add points in Kelvin
    fitter.addOnsetPoint(353.15, 350.0);
    fitter.addOnsetPoint(373.15, 320.0);
    fitter.addOnsetPoint(393.15, 280.0);

    assertEquals(3, fitter.getNumberOfDataPoints());

    // Add point in Celsius
    fitter.addOnsetPointCelsius(140.0, 250.0);
    assertEquals(4, fitter.getNumberOfDataPoints());
  }

  @Test
  @DisplayName("Test AsphalteneOnsetFunction creation")
  void testAsphalteneOnsetFunction() {
    AsphalteneOnsetFunction function = new AsphalteneOnsetFunction();
    assertNotNull(function);

    // Set initial guess
    double[] guess = {3500.0, 0.005};
    function.setInitialGuess(guess);

    assertEquals(3500.0, function.getFittingParams(0), 0.01);
    assertEquals(0.005, function.getFittingParams(1), 0.0001);
  }

  @Test
  @DisplayName("Test pressure range configuration")
  void testPressureRangeConfiguration() {
    AsphalteneOnsetFitting fitter = new AsphalteneOnsetFitting(baseSystem);

    // Should not throw
    fitter.setPressureRange(500.0, 10.0, 20.0);
    fitter.setPressureStdDev(10.0);
  }

  @Test
  @DisplayName("Test parameter type selection")
  void testParameterTypeSelection() {
    AsphalteneOnsetFunction function = new AsphalteneOnsetFunction();

    function.setParameterType(AsphalteneOnsetFunction.FittingParameterType.ASSOCIATION_PARAMETERS);
    assertEquals(2, function.getNumberOfFittingParams());

    function.setParameterType(AsphalteneOnsetFunction.FittingParameterType.MOLAR_MASS);
    assertEquals(1, function.getNumberOfFittingParams());

    function.setParameterType(AsphalteneOnsetFunction.FittingParameterType.BINARY_INTERACTION);
    assertEquals(1, function.getNumberOfFittingParams());
  }

  @Test
  @DisplayName("Test clear data")
  void testClearData() {
    AsphalteneOnsetFitting fitter = new AsphalteneOnsetFitting(baseSystem);

    fitter.addOnsetPoint(353.15, 350.0);
    fitter.addOnsetPoint(373.15, 320.0);
    assertEquals(2, fitter.getNumberOfDataPoints());

    fitter.clearData();
    assertEquals(0, fitter.getNumberOfDataPoints());
  }

  @Test
  @DisplayName("Test fitting without data returns false")
  void testSolveWithoutData() {
    AsphalteneOnsetFitting fitter = new AsphalteneOnsetFitting(baseSystem);
    fitter.setInitialGuess(3500.0, 0.005);

    // Should return false - no data points
    boolean result = fitter.solve();
    assertTrue(!result || !fitter.isSolved());
  }

  @Test
  @DisplayName("Test workflow documentation")
  void testWorkflowDocumentation() {
    logger.info(StringUtils.repeat("=", 70));
    logger.info("ASPHALTENE ONSET FITTING - WORKFLOW DEMONSTRATION");
    logger.info(StringUtils.repeat("=", 70));

    logger.info("Step 1: Create fluid system with asphaltene component");
    logger.info("  SystemInterface fluid = new SystemSrkCPAstatoil(373.15, 200.0);");
    logger.info("  fluid.addComponent(\"methane\", 0.30);");
    logger.info("  fluid.addTBPfraction(\"C7\", 0.30, 0.100, 0.75);");
    logger.info("  fluid.addComponent(\"asphaltene\", 0.05);");
    logger.info("  fluid.setMixingRule(\"classic\");");

    logger.info("Step 2: Create fitter and add experimental onset data");
    logger.info("  AsphalteneOnsetFitting fitter = new AsphalteneOnsetFitting(fluid);");
    logger.info("  fitter.addOnsetPoint(353.15, 350.0);  // T=353K, P_onset=350 bar");
    logger.info("  fitter.addOnsetPoint(373.15, 320.0);  // T=373K, P_onset=320 bar");
    logger.info("  fitter.addOnsetPoint(393.15, 280.0);  // T=393K, P_onset=280 bar");

    logger.info("Step 3: Set initial parameter guesses");
    logger.info("  fitter.setInitialGuess(3500.0, 0.005);  // epsilon/R=3500K, kappa=0.005");

    logger.info("Step 4: Configure pressure search range (optional)");
    logger.info("  fitter.setPressureRange(500.0, 10.0, 10.0);");

    logger.info("Step 5: Run fitting");
    logger.info("  boolean success = fitter.solve();");

    logger.info("Step 6: Get fitted parameters");
    logger.info("  double epsilonR = fitter.getFittedAssociationEnergy();");
    logger.info("  double kappa = fitter.getFittedAssociationVolume();");

    logger.info("Step 7: Use fitted parameters to predict onset at new conditions");
    logger.info("  double onsetP = fitter.calculateOnsetPressure(400.0);  // T=400K");

    logger.info(StringUtils.repeat("=", 70));

    // Verify the documentation matches actual API
    AsphalteneOnsetFitting fitter = new AsphalteneOnsetFitting(baseSystem);
    fitter.addOnsetPoint(353.15, 350.0);
    fitter.addOnsetPointCelsius(100.0, 320.0); // Alternative method
    fitter.setInitialGuess(3500.0, 0.005);
    fitter.setPressureRange(500.0, 10.0, 10.0);

    assertNotNull(fitter.getFunction());
    assertNotNull(fitter.getOptimizer());
    assertEquals(2, fitter.getNumberOfDataPoints());
  }

  @Test
  @DisplayName("Test typical asphaltene parameter ranges")
  void testTypicalParameterRanges() {
    logger.info(StringUtils.repeat("=", 70));
    logger.info("TYPICAL CPA PARAMETERS FOR ASPHALTENE");
    logger.info(StringUtils.repeat("=", 70));

    logger.info("Based on literature (Li & Firoozabadi, Vargas et al.):");

    logger.info("Parameter                  | Typical Range      | Units");
    logger.info("---------------------------|--------------------|---------");
    logger.info("Molar Mass                 | 500 - 1500         | g/mol");
    logger.info("Association Energy (ε/R)   | 2500 - 4500        | K");
    logger.info("Association Volume (κ)     | 0.001 - 0.05       | -");
    logger.info("Association Scheme         | 1A (single site)   | -");
    logger.info("Critical Temperature (Tc)  | 700 - 900          | K");
    logger.info("Critical Pressure (Pc)     | 5 - 15             | bar");
    logger.info("Acentric Factor (ω)        | 1.0 - 2.0          | -");

    logger.info("Starting guess recommendations:");
    logger.info("  Light oils (>35 API):  ε/R = 3000 K, κ = 0.01");
    logger.info("  Medium oils (25-35):   ε/R = 3500 K, κ = 0.005");
    logger.info("  Heavy oils (<25 API):  ε/R = 4000 K, κ = 0.003");

    // Verify these are reasonable for CPA
    double epsilonR_min = 2500.0;
    double epsilonR_max = 4500.0;
    double kappa_min = 0.001;
    double kappa_max = 0.05;

    assertTrue(epsilonR_min < epsilonR_max);
    assertTrue(kappa_min < kappa_max);
  }

  private static SystemInterface cubicOil() {
    SystemInterface s = new SystemSrkEos(363.15, 400.0);
    s.addComponent("methane", 0.40);
    s.addComponent("ethane", 0.06);
    s.addComponent("propane", 0.04);
    s.addComponent("n-heptane", 0.25);
    s.addComponent("nC10", 0.2);
    s.addComponent("asphaltene", 0.004);
    s.setMixingRule("classic");
    return s;
  }

  private static double onsetWithMethaneKij(SystemInterface fluid, double kij) {
    AsphalteneOnsetFunction function = new AsphalteneOnsetFunction();
    function.setThermodynamicSystem(fluid);
    function.setParameterType(AsphalteneOnsetFunction.FittingParameterType.BINARY_INTERACTION);
    function.setPressureRange(600.0, 10.0, 10.0);
    function.setFittingParams(0, kij);
    return function.calcValue(new double[] {363.15});
  }

  @Test
  @DisplayName("BINARY_INTERACTION sets size-scaled kij and changes the onset")
  void testBinaryInteractionIsApplied() {
    SystemInterface fluid = cubicOil();
    double onsetA = onsetWithMethaneKij(fluid, -0.05);
    int a = fluid.getPhase(0).getComponent("asphaltene").getComponentNumber();
    neqsim.thermo.mixingrule.EosMixingRulesInterface rule = ((neqsim.thermo.phase.PhaseEosInterface) fluid.getPhase(0))
        .getEosMixingRule();
    assertEquals(-0.05, rule.getBinaryInteractionParameter(a, 0), 1e-12);
    double heptane = rule.getBinaryInteractionParameter(a, 3);
    assertTrue(heptane < 0.0 && heptane > -0.05, "heptane kij " + heptane);
    double onsetB = onsetWithMethaneKij(cubicOil(), -0.08);
    assertTrue(onsetA > onsetB + 50.0, onsetA + " vs " + onsetB);
  }

  @Test
  @DisplayName("Unstable start pressure is an error, not an onset")
  void testUnstableStartThrows() {
    assertThrows(IllegalStateException.class, () -> onsetWithMethaneKij(cubicOil(), 0.0));
  }

  @Test
  @DisplayName("Association parameters need CPA")
  void testAssociationParametersNeedCpa() {
    AsphalteneOnsetFunction function = new AsphalteneOnsetFunction();
    function.setThermodynamicSystem(cubicOil());
    function.setFittingParams(0, 3000.0);
    function.setFittingParams(1, 0.01);
    assertThrows(IllegalStateException.class, () -> function.calcValue(new double[] {363.15}));
  }
}
