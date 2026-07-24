package neqsim.process.fielddevelopment.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.economics.CashFlowEngine;
import neqsim.process.fielddevelopment.economics.ProductionProfileGenerator;
import neqsim.process.fielddevelopment.economics.ProductionProfileGenerator.DeclineType;

/**
 * Executable regression coverage for the field-development screening notebook.
 */
class FieldDevelopmentWorkflowDocumentationTest {
  private static final double DAYS_PER_YEAR = 365.25;

  private static Map<Integer, Double> createProfile(double plateauRateSm3PerDay) {
    return new ProductionProfileGenerator().generateFullProfile(plateauRateSm3PerDay, 1, 4, 0.12,
        DeclineType.EXPONENTIAL, 2028, 20);
  }

  private static CashFlowEngine createCashFlow(Map<Integer, Double> annualProfile, double totalCapexMusd,
      double gasPriceUsdPerSm3) {
    CashFlowEngine engine = new CashFlowEngine("NO");
    engine.setCapex(0.77 * totalCapexMusd, 2026);
    engine.addCapex(0.23 * totalCapexMusd, 2027);
    engine.setOpexPercentOfCapex(0.04);
    engine.setGasPrice(gasPriceUsdPerSm3);
    engine.setGasTariff(0.02);
    for (Map.Entry<Integer, Double> entry : annualProfile.entrySet()) {
      engine.addAnnualProduction(entry.getKey(), 0.0, entry.getValue(), 0.0);
    }
    return engine;
  }

  @Test
  void productionProfileConvertsDailyRateToAnnualVolumeExactlyOnce() {
    Map<Integer, Double> profile = createProfile(8.0e6);

    assertEquals(20, profile.size());
    assertEquals(8.0e6, profile.get(2028) / DAYS_PER_YEAR, 1.0e-6);
    assertTrue(profile.values().stream().allMatch(value -> value > 0.0));
  }

  @Test
  void deterministicCashFlowUsesAnnualProductionWithoutSecondDayConversion() {
    Map<Integer, Double> profile = createProfile(8.0e6);
    CashFlowEngine engine = createCashFlow(profile, 1384.5, 0.30);
    CashFlowEngine.CashFlowResult result = engine.calculate(0.08);
    double breakEvenGasPrice = engine.calculateBreakevenGasPrice(0.08);

    assertEquals(1384.5, result.getTotalCapex(), 1.0e-9);
    assertTrue(Double.isFinite(result.getNpv()));
    assertTrue(Double.isFinite(result.getIrr()));
    assertTrue(result.getPaybackYears() > 0.0);
    assertTrue(breakEvenGasPrice > 0.0 && breakEvenGasPrice < 2.0);
    assertTrue(result.getSummary().contains("Cash Flow Summary"));
  }

  @Test
  void boundedSensitivitiesMoveNpvInTheExpectedDirection() {
    CashFlowEngine.CashFlowResult lowerRate = createCashFlow(createProfile(6.0e6), 1384.5, 0.30).calculate(0.08);
    CashFlowEngine.CashFlowResult base = createCashFlow(createProfile(8.0e6), 1384.5, 0.30).calculate(0.08);
    CashFlowEngine.CashFlowResult higherCapex = createCashFlow(createProfile(8.0e6), 1700.0, 0.30).calculate(0.08);

    assertTrue(lowerRate.getNpv() < base.getNpv());
    assertTrue(higherCapex.getNpv() < base.getNpv());
  }
}
