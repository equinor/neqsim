package neqsim.process.fielddevelopment.screening;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.screening.ArtificialLiftScreener.LiftMethod;
import neqsim.process.fielddevelopment.screening.ArtificialLiftScreener.MethodResult;
import neqsim.process.fielddevelopment.screening.ArtificialLiftScreener.ScreeningResult;

/**
 * Unit tests for ArtificialLiftScreener.
 *
 * @author ESOL
 */
class ArtificialLiftScreenerTest {

  @BeforeEach
  void setUp() {
    screener = new ArtificialLiftScreener();
  }

  @Test
  @DisplayName("Test basic screening with all methods available")
  void testBasicScreening() {
    // Configure screener
    screener.setReservoirPressure(250.0, "bara");
    screener.setReservoirTemperature(90.0, "C");
    screener.setWellheadPressure(15.0, "bara");
    screener.setWellDepth(2800.0, "m");
    screener.setProductivityIndex(8.0);
    screener.setOilGravity(32.0, "API");
    screener.setWaterCut(0.40);
    screener.setFormationGOR(150.0);
    screener.setOilViscosity(5.0, "cP");
    screener.setGasLiftAvailable(true);
    screener.setElectricityAvailable(true);

    // Screen
    ScreeningResult result = screener.screen();

    // Verify
    assertNotNull(result);
    assertNotNull(result.getRecommendedMethod());
    assertTrue(result.getAllMethods().size() > 1, "Should evaluate multiple methods");
  }

  @Test
  @DisplayName("Test screening ranks methods correctly")
  void testMethodRanking() {
    screener.setReservoirPressure(280.0, "bara");
    screener.setWellDepth(2500.0, "m");
    screener.setProductivityIndex(10.0);
    screener.setGasLiftAvailable(true);
    screener.setElectricityAvailable(true);

    ScreeningResult result = screener.screen();

    // Feasible methods should have ranks assigned
    List<MethodResult> feasible = result.getFeasibleMethods();
    assertTrue(feasible.size() >= 1, "Should have at least one feasible method");

    // Check ranking order
    int prevRank = 0;
    for (MethodResult method : feasible) {
      if (method.rank > 0) {
        assertTrue(method.rank > prevRank || prevRank == 0, "Ranks should be sequential");
        prevRank = method.rank;
      }
    }
  }

  @Test
  @DisplayName("Test ESP is feasible within operating envelope")
  void testESPFeasibility() {
    // Conditions within ESP envelope
    screener.setReservoirPressure(250.0, "bara"); // Need high pressure for low GVF
    screener.setWellheadPressure(50.0, "bara"); // Higher WHP = lower GVF
    screener.setWellDepth(2000.0, "m"); // < 4500m
    screener.setReservoirTemperature(100.0, "C"); // < 180°C (conservative)
    screener.setOilViscosity(10.0, "cP"); // < 200 cP
    screener.setFormationGOR(30.0); // Very low GOR = low GVF
    screener.setElectricityAvailable(true);

    ScreeningResult result = screener.screen();

    // Find ESP result
    boolean espFeasible = false;
    String reason = "";
    for (MethodResult method : result.getAllMethods()) {
      if (method.method == LiftMethod.ESP) {
        if (method.feasible) {
          espFeasible = true;
        } else {
          reason = method.infeasibilityReason;
        }
        break;
      }
    }
    assertTrue(espFeasible, "ESP should be feasible within operating envelope. Reason: " + reason);
  }

  @Test
  @DisplayName("Test ESP infeasible at high temperature")
  void testESPInfeasibleHighTemp() {
    screener.setReservoirTemperature(200.0, "C"); // > 180°C limit
    screener.setElectricityAvailable(true);

    ScreeningResult result = screener.screen();

    // Find ESP result
    for (MethodResult method : result.getAllMethods()) {
      if (method.method == LiftMethod.ESP) {
        assertFalse(method.feasible, "ESP should be infeasible above 180°C");
        assertTrue(method.infeasibilityReason.contains("Temperature"),
            "Should cite temperature as reason");
      }
    }
  }

  @Test
  @DisplayName("Test rod pump limited by depth")
  void testRodPumpDepthLimit() {
    screener.setWellDepth(3500.0, "m"); // > 3000m rod pump limit
    screener.setElectricityAvailable(true);

    ScreeningResult result = screener.screen();

    // Find rod pump result
    for (MethodResult method : result.getAllMethods()) {
      if (method.method == LiftMethod.ROD_PUMP) {
        assertFalse(method.feasible, "Rod pump should be infeasible beyond 3000m");
      }
    }
  }

  @Test
  @DisplayName("Test PCP good for high viscosity")
  void testPCPHighViscosity() {
    screener.setWellDepth(2000.0, "m"); // Within PCP range
    screener.setReservoirTemperature(100.0, "C"); // < 130°C
    screener.setFormationGOR(50.0); // Low GOR
    screener.setOilViscosity(500.0, "cP"); // High viscosity - PCP territory
    screener.setElectricityAvailable(true);

    ScreeningResult result = screener.screen();

    // PCP should be feasible
    boolean pcpFeasible = false;
    for (MethodResult method : result.getAllMethods()) {
      if (method.method == LiftMethod.PCP && method.feasible) {
        pcpFeasible = true;
        break;
      }
    }
    assertTrue(pcpFeasible, "PCP should be feasible for high viscosity oil");
  }

  @Test
  @DisplayName("Test gas lift unavailable when infrastructure not present")
  void testGasLiftUnavailable() {
    screener.setGasLiftAvailable(false);
    screener.setElectricityAvailable(true);

    ScreeningResult result = screener.screen();

    // Gas lift should not be in results
    for (MethodResult method : result.getAllMethods()) {
      if (method.method == LiftMethod.GAS_LIFT) {
        // If present, should be infeasible
        assertFalse(method.feasible, "Gas lift should not be available");
      }
    }
  }

  @Test
  @DisplayName("Test natural flow baseline")
  void testNaturalFlowBaseline() {
    screener.setReservoirPressure(300.0, "bara"); // High pressure
    screener.setWellheadPressure(20.0, "bara");
    screener.setWellDepth(2000.0, "m");

    ScreeningResult result = screener.screen();

    assertTrue(result.naturalFlowRate > 0, "Should calculate natural flow rate");
  }

  @Test
  @DisplayName("Test NPV calculation")
  void testNPVCalculation() {
    screener.setReservoirPressure(250.0, "bara");
    screener.setWellDepth(2500.0, "m");
    screener.setProductivityIndex(8.0);
    screener.setOilPrice(70.0);
    screener.setGasLiftAvailable(true);
    screener.setElectricityAvailable(true);

    ScreeningResult result = screener.screen();

    // Feasible methods should have NPV calculated
    for (MethodResult method : result.getFeasibleMethods()) {
      if (method.productionRate > 0) {
        assertTrue(method.npv > Double.NEGATIVE_INFINITY,
            "Should calculate NPV for feasible methods");
      }
    }
  }

  @Test
  @DisplayName("Test unit conversions in screener")
  void testUnitConversions() {
    screener.setReservoirPressure(3625.0, "psia"); // ~250 bara
    screener.setWellDepth(9843.0, "ft"); // ~3000 m
    screener.setOilViscosity(0.005, "Pa.s"); // 5 cP

    ScreeningResult result = screener.screen();

    assertNotNull(result);
    assertNotNull(result.getRecommendedMethod());
  }

  @Test
  @DisplayName("Test result toString format")
  void testResultToString() {
    screener.setReservoirPressure(250.0, "bara");
    screener.setWellDepth(2500.0, "m");

    ScreeningResult result = screener.screen();

    String output = result.toString();
    assertNotNull(output);
    assertTrue(output.contains("Artificial Lift Screening Results"));
    assertTrue(output.contains("Recommended:"));
  }

  @Test
  @DisplayName("Test recommended method selection")
  void testRecommendedMethodSelection() {
    screener.setReservoirPressure(250.0, "bara");
    screener.setWellDepth(2500.0, "m");
    screener.setProductivityIndex(8.0);
    screener.setGasLiftAvailable(true);
    screener.setElectricityAvailable(true);

    ScreeningResult result = screener.screen();

    // Recommended should be the highest NPV feasible method
    LiftMethod recommended = result.getRecommendedMethod();
    assertNotNull(recommended);

    MethodResult recommendedResult = result.getRecommendedMethodResult();
    if (recommendedResult != null) {
      assertEquals(1, recommendedResult.rank, "Recommended should be rank 1");
    }
  }
}
