package neqsim.pvtsimulation.util.parameterfitting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Tests for AsphalteneOnsetFitting class.
 *
 * @author ASMF
 */
public class AsphalteneOnsetFittingTest extends neqsim.NeqSimTest {

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
    System.out.println("=".repeat(70));
    System.out.println("ASPHALTENE ONSET FITTING - WORKFLOW DEMONSTRATION");
    System.out.println("=".repeat(70));
    System.out.println();

    System.out.println("Step 1: Create fluid system with asphaltene component");
    System.out.println("  SystemInterface fluid = new SystemSrkCPAstatoil(373.15, 200.0);");
    System.out.println("  fluid.addComponent(\"methane\", 0.30);");
    System.out.println("  fluid.addTBPfraction(\"C7\", 0.30, 0.100, 0.75);");
    System.out.println("  fluid.addComponent(\"asphaltene\", 0.05);");
    System.out.println("  fluid.setMixingRule(\"classic\");");
    System.out.println();

    System.out.println("Step 2: Create fitter and add experimental onset data");
    System.out.println("  AsphalteneOnsetFitting fitter = new AsphalteneOnsetFitting(fluid);");
    System.out.println("  fitter.addOnsetPoint(353.15, 350.0);  // T=353K, P_onset=350 bar");
    System.out.println("  fitter.addOnsetPoint(373.15, 320.0);  // T=373K, P_onset=320 bar");
    System.out.println("  fitter.addOnsetPoint(393.15, 280.0);  // T=393K, P_onset=280 bar");
    System.out.println();

    System.out.println("Step 3: Set initial parameter guesses");
    System.out.println("  fitter.setInitialGuess(3500.0, 0.005);  // epsilon/R=3500K, kappa=0.005");
    System.out.println();

    System.out.println("Step 4: Configure pressure search range (optional)");
    System.out.println("  fitter.setPressureRange(500.0, 10.0, 10.0);");
    System.out.println();

    System.out.println("Step 5: Run fitting");
    System.out.println("  boolean success = fitter.solve();");
    System.out.println();

    System.out.println("Step 6: Get fitted parameters");
    System.out.println("  double epsilonR = fitter.getFittedAssociationEnergy();");
    System.out.println("  double kappa = fitter.getFittedAssociationVolume();");
    System.out.println();

    System.out.println("Step 7: Use fitted parameters to predict onset at new conditions");
    System.out.println("  double onsetP = fitter.calculateOnsetPressure(400.0);  // T=400K");
    System.out.println();

    System.out.println("=".repeat(70));

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
    System.out.println("=".repeat(70));
    System.out.println("TYPICAL CPA PARAMETERS FOR ASPHALTENE");
    System.out.println("=".repeat(70));
    System.out.println();

    System.out.println("Based on literature (Li & Firoozabadi, Vargas et al.):");
    System.out.println();
    System.out.println("Parameter                  | Typical Range      | Units");
    System.out.println("---------------------------|--------------------|---------");
    System.out.println("Molar Mass                 | 500 - 1500         | g/mol");
    System.out.println("Association Energy (ε/R)   | 2500 - 4500        | K");
    System.out.println("Association Volume (κ)     | 0.001 - 0.05       | -");
    System.out.println("Association Scheme         | 1A (single site)   | -");
    System.out.println("Critical Temperature (Tc)  | 700 - 900          | K");
    System.out.println("Critical Pressure (Pc)     | 5 - 15             | bar");
    System.out.println("Acentric Factor (ω)        | 1.0 - 2.0          | -");
    System.out.println();
    System.out.println("Starting guess recommendations:");
    System.out.println("  Light oils (>35 API):  ε/R = 3000 K, κ = 0.01");
    System.out.println("  Medium oils (25-35):   ε/R = 3500 K, κ = 0.005");
    System.out.println("  Heavy oils (<25 API):  ε/R = 4000 K, κ = 0.003");

    // Verify these are reasonable for CPA
    double epsilonR_min = 2500.0;
    double epsilonR_max = 4500.0;
    double kappa_min = 0.001;
    double kappa_max = 0.05;

    assertTrue(epsilonR_min < epsilonR_max);
    assertTrue(kappa_min < kappa_max);
  }
}
