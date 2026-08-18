package neqsim.process.util.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Executes the relief-valve sizing workflow documented in the safety guide. */
public class ReliefValveSizingDocumentationTest {
  @Test
  void executesDocumentedGasLiquidTwoPhaseAndFireScreening() {
    ReliefValveSizing.PSVSizingResult gasResult = ReliefValveSizing.calculateRequiredArea(5000.0 / 3600.0, 110.0e5,
        0.10, 1.013e5, 333.15, 0.018, 0.95, 1.30, false, false);

    ReliefValveSizing.LiquidPSVSizingResult liquidResult = ReliefValveSizing.calculateLiquidReliefArea(50.0 / 3600.0,
        850.0, 25.0e5, 0.10, 1.013e5, 0.004, false);

    double twoPhaseArea = ReliefValveSizing.calculateTwoPhaseReliefArea(30000.0 / 3600.0, 80.0e5, 0.10, 5.0e5,
        373.15, 0.30, 50.0, 700.0, 250000.0, 2500.0);
    double fireHeatInput = ReliefValveSizing.calculateAPI521FireHeatInput(80.0, true, true);

    assertEquals(6.7388e-5, gasResult.getRequiredArea(), 1.0e-9);
    assertEquals("D", gasResult.getRecommendedOrifice());
    assertTrue(gasResult.getSelectedArea() >= gasResult.getRequiredArea());
    assertEquals(2.7061e-4, liquidResult.getRequiredAreaM2(), 1.0e-8);
    assertEquals("G", liquidResult.getRecommendedOrifice());
    assertEquals(5.8836e-5, twoPhaseArea, 1.0e-9);
    assertEquals(1.57015e6, fireHeatInput, 10.0);
  }

  @Test
  void preservesDocumentedFireHelperBoundary() {
    double withFireFighting = ReliefValveSizing.calculateAPI521FireHeatInput(80.0, true, true);
    double withoutFireFighting = ReliefValveSizing.calculateAPI521FireHeatInput(80.0, true, false);
    double withoutDrainage = ReliefValveSizing.calculateAPI521FireHeatInput(80.0, false, true);

    assertEquals(withFireFighting, withoutFireFighting, 0.0,
        "The current helper does not apply a firefighting credit");
    assertTrue(withoutDrainage > withFireFighting);
  }
}
