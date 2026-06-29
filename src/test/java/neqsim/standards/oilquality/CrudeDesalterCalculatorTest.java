package neqsim.standards.oilquality;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CrudeDesalterCalculator}.
 */
public class CrudeDesalterCalculatorTest {
  /**
   * A single-stage desalter should reduce the salt content.
   */
  @Test
  void testSingleStage() {
    CrudeDesalterCalculator calc = new CrudeDesalterCalculator();
    calc.setFeedConditions(50.0, 0.06, 1.5);
    calc.setStageConfiguration(1, 0.9, 0.003);
    calc.calcPerformance();

    assertTrue(calc.getOutletSaltContent() < 50.0, "Outlet salt should be below inlet");
    assertTrue(calc.getRemovalEfficiency() > 0.0 && calc.getRemovalEfficiency() < 1.0,
        "Removal efficiency should be between 0 and 1");
    assertNotNull(calc.toJson());
  }

  /**
   * A two-stage desalter should outperform a single stage for the same conditions.
   */
  @Test
  void testTwoStageBetterThanSingle() {
    CrudeDesalterCalculator single = new CrudeDesalterCalculator();
    single.setFeedConditions(50.0, 0.06, 1.5);
    single.setStageConfiguration(1, 0.9, 0.003);
    single.calcPerformance();

    CrudeDesalterCalculator twoStage = new CrudeDesalterCalculator();
    twoStage.setFeedConditions(50.0, 0.06, 1.5);
    twoStage.setStageConfiguration(2, 0.9, 0.003);
    twoStage.calcPerformance();

    assertTrue(twoStage.getOutletSaltContent() < single.getOutletSaltContent(),
        "Two stages should leave less residual salt");
    assertTrue(twoStage.getRemovalEfficiency() > single.getRemovalEfficiency(),
        "Two stages should have higher removal efficiency");
  }

  /**
   * More wash water should improve the dilution and removal.
   */
  @Test
  void testMoreWashWaterImprovesRemoval() {
    CrudeDesalterCalculator low = new CrudeDesalterCalculator();
    low.setFeedConditions(50.0, 0.04, 1.5);
    low.setStageConfiguration(1, 0.9, 0.003);
    low.calcPerformance();

    CrudeDesalterCalculator high = new CrudeDesalterCalculator();
    high.setFeedConditions(50.0, 0.10, 1.5);
    high.setStageConfiguration(1, 0.9, 0.003);
    high.calcPerformance();

    assertTrue(high.getRemovalEfficiency() > low.getRemovalEfficiency(),
        "More wash water should improve removal efficiency");
  }

  /**
   * The {@code fromStreams} bridge should derive the wash-water fraction from run process streams.
   */
  @Test
  void testFromProcessStreams() {
    neqsim.thermo.system.SystemSrkEos crudeFluid =
        new neqsim.thermo.system.SystemSrkEos(298.15, 5.0);
    crudeFluid.addComponent("nC10", 1.0);
    crudeFluid.setMixingRule("classic");
    neqsim.process.equipment.stream.Stream crude =
        new neqsim.process.equipment.stream.Stream("crude", crudeFluid);
    crude.setFlowRate(100000.0, "kg/hr");
    crude.setTemperature(25.0, "C");
    crude.setPressure(5.0, "bara");
    crude.run();

    neqsim.thermo.system.SystemSrkEos washFluid =
        new neqsim.thermo.system.SystemSrkEos(298.15, 5.0);
    washFluid.addComponent("water", 1.0);
    washFluid.setMixingRule("classic");
    neqsim.process.equipment.stream.Stream wash =
        new neqsim.process.equipment.stream.Stream("wash", washFluid);
    wash.setFlowRate(6000.0, "kg/hr");
    wash.setTemperature(25.0, "C");
    wash.setPressure(5.0, "bara");
    wash.run();

    CrudeDesalterCalculator calc = new CrudeDesalterCalculator();
    calc.fromStreams(crude, wash, 50.0);
    calc.setStageConfiguration(1, 0.9, 0.003);
    calc.calcPerformance();

    assertTrue(calc.getEffectiveWashFraction() > 0.0,
        "Effective wash fraction should be derived from the streams");
    assertTrue(calc.getOutletSaltContent() < 50.0, "Outlet salt should be below the 50 PTB inlet");
    assertNotNull(calc.toJson());
  }
}
