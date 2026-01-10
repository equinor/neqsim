package neqsim.process.fielddevelopment.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.evaluation.SeparatorSizingCalculator.DesignStandard;
import neqsim.process.fielddevelopment.evaluation.SeparatorSizingCalculator.SeparatorSizingResult;
import neqsim.process.fielddevelopment.evaluation.SeparatorSizingCalculator.SeparatorType;

/**
 * Unit tests for SeparatorSizingCalculator.
 *
 * @author ESOL
 * @version 1.0
 */
public class SeparatorSizingCalculatorTest {
  private SeparatorSizingCalculator calculator;

  @BeforeEach
  void setUp() {
    calculator = new SeparatorSizingCalculator();
  }

  @Test
  void testAPI12JRetentionTime() {
    // Returns seconds per API 12J
    double retentionLight = calculator.getAPI12JRetentionTime(800.0);
    assertEquals(60.0, retentionLight, 0.01, "Light oil should have 60 sec (1 min) retention");

    double retentionHeavy = calculator.getAPI12JRetentionTime(950.0);
    assertEquals(180.0, retentionHeavy, 0.01, "Heavy oil should have 180 sec (3 min) retention");
  }

  @Test
  void testStokesSettlingVelocity() {
    double velocity = calculator.stokesSettlingVelocity(100e-6, 800.0, 50.0, 0.00001);
    assertTrue(velocity > 0, "Settling velocity should be positive");
  }

  @Test
  void testSoudersBrownGasVelocity() {
    double velocity = calculator.soudersbrownGasVelocity(0.1, 800.0, 50.0);
    assertTrue(velocity > 0, "Gas velocity should be positive");
  }

  @Test
  void testRecommendedKFactor() {
    double kVertical = calculator.getRecommendedKFactor(SeparatorType.VERTICAL, false);
    double kHorizontal = calculator.getRecommendedKFactor(SeparatorType.HORIZONTAL, false);
    assertTrue(kHorizontal > kVertical, "Horizontal K should be higher than vertical");
  }

  @Test
  void testSeparatorSizing() {
    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemSrkEos(288.15, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("nC10", 0.15);
    fluid.setMixingRule("classic");

    neqsim.process.equipment.stream.Stream feed =
        new neqsim.process.equipment.stream.Stream("Feed", fluid);
    feed.setFlowRate(50000, "kg/hr");
    feed.run();

    SeparatorSizingResult result =
        calculator.sizeSeparator(feed, SeparatorType.HORIZONTAL, DesignStandard.API_12J);

    assertNotNull(result, "Sizing result should not be null");
    assertTrue(result.internalDiameter > 0, "Diameter should be positive");
    assertTrue(result.tanTanLength > 0, "Length should be positive");
  }
}
