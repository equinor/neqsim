package neqsim.pvtsimulation.flowassurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for De Boer asphaltene screening.
 *
 * @author Even Solbraa
 */
public class DeBoerAsphalteneScreeningTest {
  private DeBoerAsphalteneScreening screening;

  @BeforeEach
  void setUp() {
    screening = new DeBoerAsphalteneScreening();
  }

  @Test
  void testDefaultConstructor() {
    assertNotNull(screening);
  }

  @Test
  void testConstructorWithParameters() {
    DeBoerAsphalteneScreening s = new DeBoerAsphalteneScreening(400.0, 150.0, 800.0);
    assertEquals(400.0, s.getReservoirPressure(), 0.001);
    assertEquals(150.0, s.getSaturationPressure(), 0.001);
    assertEquals(800.0, s.getInSituDensity(), 0.001);
  }

  @Test
  void testUndersaturationPressure() {
    screening.setReservoirPressure(400.0);
    screening.setSaturationPressure(150.0);

    assertEquals(250.0, screening.getUndersaturationPressure(), 0.001);
  }

  @Test
  void testAPIGravity() {
    // Density 850 kg/m3 -> SG = 0.85 -> API = 141.5/0.85 - 131.5 = 35.0
    screening.setInSituDensity(850.0);

    double api = screening.getAPIGravity();
    assertEquals(35.0, api, 0.5); // Allow some tolerance
  }

  @Test
  void testAPIGravityLightOil() {
    // Light oil: density 750 kg/m3 -> SG = 0.75 -> API = 141.5/0.75 - 131.5 = 57.2
    screening.setInSituDensity(750.0);

    double api = screening.getAPIGravity();
    assertTrue(api > 50, "Light oil should have high API: " + api);
  }

  @Test
  void testAPIGravityHeavyOil() {
    // Heavy oil: density 950 kg/m3 -> SG = 0.95 -> API = 141.5/0.95 - 131.5 = 17.4
    screening.setInSituDensity(950.0);

    double api = screening.getAPIGravity();
    assertTrue(api < 25, "Heavy oil should have low API: " + api);
  }

  @Test
  void testRiskEvaluationNoProblem() {
    // Low undersaturation with moderate density - should be no problem
    screening.setReservoirPressure(200.0);
    screening.setSaturationPressure(180.0); // Only 20 bar undersaturation
    screening.setInSituDensity(850.0);

    DeBoerAsphalteneScreening.DeBoerRisk risk = screening.evaluateRisk();

    assertEquals(DeBoerAsphalteneScreening.DeBoerRisk.NO_PROBLEM, risk);
  }

  @Test
  void testRiskEvaluationSevereProblem() {
    // High undersaturation with light oil - should be severe
    screening.setReservoirPressure(600.0);
    screening.setSaturationPressure(100.0); // 500 bar undersaturation
    screening.setInSituDensity(700.0); // Light oil

    DeBoerAsphalteneScreening.DeBoerRisk risk = screening.evaluateRisk();

    // Light oil with high undersaturation should be problematic
    assertTrue(risk.ordinal() >= DeBoerAsphalteneScreening.DeBoerRisk.MODERATE_PROBLEM.ordinal(),
        "Should be at least moderate problem: " + risk);
  }

  @Test
  void testRiskIndex() {
    screening.setReservoirPressure(400.0);
    screening.setSaturationPressure(150.0);
    screening.setInSituDensity(800.0);

    double riskIndex = screening.calculateRiskIndex();

    assertTrue(riskIndex >= 0, "Risk index should be non-negative");
  }

  @Test
  void testAsphalteneContentEffect() {
    screening.setReservoirPressure(350.0);
    screening.setSaturationPressure(150.0);
    screening.setInSituDensity(800.0);

    double riskWithoutAsphaltenes = screening.calculateRiskIndex();

    screening.setAsphalteneContent(0.10); // 10% asphaltenes
    double riskWithAsphaltenes = screening.calculateRiskIndex();

    assertTrue(riskWithAsphaltenes > riskWithoutAsphaltenes,
        "Higher asphaltene content should increase risk index");
  }

  @Test
  void testPerformScreening() {
    screening.setReservoirPressure(450.0);
    screening.setSaturationPressure(180.0);
    screening.setInSituDensity(780.0);
    screening.setAsphalteneContent(0.05);

    String result = screening.performScreening();

    assertNotNull(result);
    assertTrue(result.contains("DE BOER"));
    assertTrue(result.contains("Undersaturation"));
    assertTrue(result.contains("Risk Level"));
  }

  @Test
  void testGeneratePlotData() {
    double[][] plotData = screening.generatePlotData(650.0, 950.0, 10);

    assertNotNull(plotData);
    assertEquals(4, plotData.length); // densities + 3 boundary lines
    assertEquals(10, plotData[0].length); // 10 points

    // First point should be at min density
    assertEquals(650.0, plotData[0][0], 0.001);

    // Last point should be at max density
    assertEquals(950.0, plotData[0][9], 0.001);
  }

  @Test
  void testRiskEnumValues() {
    DeBoerAsphalteneScreening.DeBoerRisk[] risks = DeBoerAsphalteneScreening.DeBoerRisk.values();

    assertEquals(4, risks.length);
    assertEquals(DeBoerAsphalteneScreening.DeBoerRisk.NO_PROBLEM, risks[0]);
    assertEquals(DeBoerAsphalteneScreening.DeBoerRisk.SLIGHT_PROBLEM, risks[1]);
    assertEquals(DeBoerAsphalteneScreening.DeBoerRisk.MODERATE_PROBLEM, risks[2]);
    assertEquals(DeBoerAsphalteneScreening.DeBoerRisk.SEVERE_PROBLEM, risks[3]);
  }

  @Test
  void testRiskDescription() {
    DeBoerAsphalteneScreening.DeBoerRisk risk =
        DeBoerAsphalteneScreening.DeBoerRisk.MODERATE_PROBLEM;

    assertNotNull(risk.getDescription());
    assertTrue(risk.getDescription().length() > 0);
  }

  @Test
  void testWithThermodynamicSystem() {
    SystemInterface fluid = new SystemSrkEos(373.15, 300.0);
    fluid.addComponent("methane", 0.20);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.05);
    fluid.addComponent("n-heptane", 0.25);
    fluid.addComponent("nC10", 0.40);
    fluid.setMixingRule("classic");

    DeBoerAsphalteneScreening s = new DeBoerAsphalteneScreening(fluid, 400.0, 373.15);

    // Should be able to calculate properties from system
    s.calculateSaturationPressure();
    s.calculateInSituDensity();

    // Check that values were calculated
    assertTrue(s.getSaturationPressure() > 0 || Double.isNaN(s.getSaturationPressure()));
  }
}
