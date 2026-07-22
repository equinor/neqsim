package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GpsaOrificeCalculator}.
 */
public class GpsaOrificeCalculatorTest {
  /**
   * A liquid orifice should produce a positive mass flow with an expansion factor of exactly 1.
   */
  @Test
  void testLiquidFlow() {
    GpsaOrificeCalculator calc = new GpsaOrificeCalculator();
    calc.setGeometry(0.0508, 0.1023);
    calc.setFlowConditions(25000.0, 1.0e6, 800.0);
    calc.setFluidService(GpsaOrificeCalculator.FluidService.LIQUID, 1.3);
    calc.setDischargeCoefficient(0.61);
    calc.calcFlow();

    assertEquals(1.0, calc.getExpansionFactor(), 1e-9, "Liquid expansion factor should be 1");
    assertTrue(calc.getMassFlowRate() > 0.0, "Mass flow should be positive");
    assertTrue(calc.getVolumetricFlowRate() > 0.0, "Volumetric flow should be positive");
    assertEquals(0.0508 / 0.1023, calc.getBetaRatio(), 1e-9, "Beta ratio should be d/D");
    assertNotNull(calc.toJson());
  }

  /**
   * A steam/vapour orifice should produce an expansion factor below 1 (compressible).
   */
  @Test
  void testSteamExpansionFactorBelowOne() {
    GpsaOrificeCalculator calc = new GpsaOrificeCalculator();
    calc.setGeometry(0.0508, 0.1023);
    calc.setFlowConditions(25000.0, 1.0e6, 5.0);
    calc.setFluidService(GpsaOrificeCalculator.FluidService.STEAM, 1.3);
    calc.calcFlow();

    assertTrue(calc.getExpansionFactor() <= 1.0, "Steam expansion factor should not exceed 1");
    assertTrue(calc.getExpansionFactor() > 0.8, "Steam expansion factor should be physically reasonable");
    assertTrue(calc.getMassFlowRate() > 0.0, "Steam mass flow should be positive");
  }

  /**
   * Sizing the orifice for a target flow should reproduce approximately that flow.
   */
  @Test
  void testSizeOrificeForFlow() {
    GpsaOrificeCalculator calc = new GpsaOrificeCalculator();
    calc.setGeometry(0.0508, 0.1023);
    calc.setFlowConditions(25000.0, 1.0e6, 800.0);
    calc.setFluidService(GpsaOrificeCalculator.FluidService.LIQUID, 1.3);

    double targetFlow = 30.0;
    double bore = calc.sizeOrificeForFlow(targetFlow);

    assertTrue(bore > 0.0 && bore < 0.1023, "Sized bore should be within the pipe diameter");
    assertEquals(targetFlow, calc.getMassFlowRate(), targetFlow * 0.05,
        "Sized orifice should reproduce the target flow within 5%");
  }

  /**
   * Higher differential pressure should yield higher mass flow.
   */
  @Test
  void testHigherDpHigherFlow() {
    GpsaOrificeCalculator low = new GpsaOrificeCalculator();
    low.setGeometry(0.0508, 0.1023);
    low.setFlowConditions(10000.0, 1.0e6, 800.0);
    low.calcFlow();

    GpsaOrificeCalculator high = new GpsaOrificeCalculator();
    high.setGeometry(0.0508, 0.1023);
    high.setFlowConditions(40000.0, 1.0e6, 800.0);
    high.calcFlow();

    assertTrue(high.getMassFlowRate() > low.getMassFlowRate(),
        "Higher differential pressure should give higher mass flow");
  }
}
