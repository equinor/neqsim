package neqsim.process.equipment.powergeneration.gasturbine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link GasTurbineWashPlanner} and the on-line wash added to {@link GasTurbineDegradation}.
 */
public class GasTurbineWashPlannerTest {

  private GasTurbineWashPlanner trollCPlanner() {
    GasTurbineWashPlanner planner = new GasTurbineWashPlanner();
    planner.setShaftPowerW(22.1e6);
    planner.setBaselineHeatRateKJPerKWh(10090.0);
    planner.setFuelLhvKJPerSm3(36500.0);
    planner.setCo2PerSm3Fuel(2.34);
    planner.setEfficiencyLossRatePerFiredHour(GasTurbineWashPlanner.lossRateFromCorrectedEfficiencyTrend(0.24, 91.0));
    planner.setAnnualOperatingHours(8760.0);
    return planner;
  }

  @Test
  void lossRateFromTrendIsRelativeLoss() {
    double rate = GasTurbineWashPlanner.lossRateFromCorrectedEfficiencyTrend(0.24, 91.0);
    assertEquals(0.24 / 91.0 / 1000.0, rate, 1.0e-12);
    assertEquals(0.0, GasTurbineWashPlanner.lossRateFromCorrectedEfficiencyTrend(0.24, 0.0), 1.0e-12);
  }

  @Test
  void perfectWashGivesHalfSawtoothMeanLoss() {
    GasTurbineWashPlanner planner = trollCPlanner();
    planner.setRecoveryEffectiveness(1.0);
    double interval = 2000.0;
    double expected = planner.getEfficiencyLossRatePerFiredHour() * interval / 2.0;
    assertEquals(expected, planner.meanEfficiencyLossFraction(interval), 1.0e-6);
  }

  @Test
  void partialWashCarriesResidualLoss() {
    GasTurbineWashPlanner planner = trollCPlanner();
    planner.setRecoveryEffectiveness(0.40);
    double partial = planner.meanEfficiencyLossFraction(2000.0);
    planner.setRecoveryEffectiveness(1.0);
    double perfect = planner.meanEfficiencyLossFraction(2000.0);
    assertTrue(partial > perfect, "an imperfect wash must leave a residual loss");
  }

  @Test
  void cleanFuelRateMatchesHeatRateDefinition() {
    GasTurbineWashPlanner planner = trollCPlanner();
    // 10090 kJ/kWh * 22100 kW / 36500 kJ/Sm3
    assertEquals(10090.0 * 22100.0 / 36500.0, planner.cleanFuelSm3PerHour(), 1.0e-6);
  }

  @Test
  void longerIntervalBurnsMoreFuelAndEmitsMoreCo2() {
    GasTurbineWashPlanner planner = trollCPlanner();
    planner.setRecoveryEffectiveness(0.85);
    GasTurbineWashPlanner.WashPlan monthly = planner.evaluate(720.0);
    GasTurbineWashPlanner.WashPlan yearly = planner.evaluate(8760.0);
    assertTrue(yearly.getExtraFuelSm3PerYear() > monthly.getExtraFuelSm3PerYear());
    assertTrue(yearly.getExtraCo2TonnesPerYear() > monthly.getExtraCo2TonnesPerYear());
    assertTrue(monthly.getWashesPerYear() > yearly.getWashesPerYear());
    assertEquals(monthly.getExtraFuelSm3PerYear() * 2.34 / 1000.0, monthly.getExtraCo2TonnesPerYear(), 1.0e-6);
  }

  @Test
  void optimumBalancesFuelPenaltyAgainstWashCost() {
    GasTurbineWashPlanner planner = trollCPlanner();
    planner.setRecoveryEffectiveness(0.85);
    planner.setWashCostPerEvent(150000.0);
    planner.setOutageHoursPerWash(8.0);
    planner.setOutageCostPerHour(50000.0);
    planner.setFuelValuePerSm3(3.0);
    planner.setCo2PricePerTonne(2000.0);
    GasTurbineWashPlanner.WashPlan best = planner.optimize(168.0, 8760.0, 24.0);
    assertTrue(best.getWashIntervalHours() > 168.0, "an expensive wash must not be done every week");
    assertTrue(best.getWashIntervalHours() < 8760.0, "fuel penalty must force washing within a year");
    assertTrue(best.getTotalCostPerYear() <= planner.evaluate(8760.0).getTotalCostPerYear());
    assertTrue(best.getTotalCostPerYear() <= planner.evaluate(168.0).getTotalCostPerYear());
  }

  @Test
  void paybackIsInfiniteWhenNoSaving() {
    GasTurbineWashPlanner planner = trollCPlanner();
    planner.setRecoveryEffectiveness(0.85);
    planner.setFuelValuePerSm3(3.0);
    GasTurbineWashPlanner.WashPlan reference = planner.evaluate(2000.0);
    assertEquals(Double.POSITIVE_INFINITY, GasTurbineWashPlanner.paybackYears(1.0e6, reference, reference), 0.0);
    assertEquals(Double.POSITIVE_INFINITY, GasTurbineWashPlanner.paybackYears(1.0e6, null, reference), 0.0);
  }

  @Test
  void rejectsInvalidInput() {
    final GasTurbineWashPlanner planner = trollCPlanner();
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        planner.evaluate(0.0);
      }
    });
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        planner.optimize(100.0, 10.0, 5.0);
      }
    });
  }

  @Test
  void onlineWashRemovesPartOfTheAccumulatedPenalty() {
    GasTurbineDegradation degradation = new GasTurbineDegradation();
    degradation.addFiredHours(4000.0);
    double before = degradation.getRecoverablePenalty();
    degradation.onlineWash(0.40);
    double after = degradation.getRecoverablePenalty();
    assertEquals(2400.0, degradation.getHoursSinceWash(), 1.0e-9);
    assertEquals(before * 0.60, after, 1.0e-12);

    degradation.onlineWash(1.0);
    assertEquals(0.0, degradation.getHoursSinceWash(), 1.0e-9);

    degradation.addFiredHours(1000.0);
    degradation.onlineWash(-1.0);
    assertEquals(1000.0, degradation.getHoursSinceWash(), 1.0e-9);
  }
}
