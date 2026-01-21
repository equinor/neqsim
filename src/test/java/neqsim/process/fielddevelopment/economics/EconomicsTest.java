package neqsim.process.fielddevelopment.economics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.economics.CashFlowEngine.CashFlowResult;
import neqsim.process.fielddevelopment.economics.TaxModel.TaxResult;

/**
 * Unit tests for Norwegian petroleum tax model and cash flow engine.
 */
class EconomicsTest {
  private NorwegianTaxModel taxModel;
  private CashFlowEngine engine;

  @BeforeEach
  void setUp() {
    taxModel = new NorwegianTaxModel();
    engine = new CashFlowEngine();
  }

  // ============================================================================
  // NORWEGIAN TAX MODEL TESTS
  // ============================================================================

  @Test
  void testDefaultTaxRates() {
    assertEquals(0.22, NorwegianTaxModel.DEFAULT_CORPORATE_TAX_RATE, 0.001);
    assertEquals(0.56, NorwegianTaxModel.DEFAULT_PETROLEUM_TAX_RATE, 0.001);
    assertEquals(0.78, NorwegianTaxModel.TOTAL_MARGINAL_RATE, 0.001);
  }

  @Test
  void testBasicTaxCalculation() {
    // Simple case: 100 MUSD revenue, 20 MUSD OPEX, 10 MUSD depreciation, 5 MUSD uplift
    TaxResult result = taxModel.calculateTax(100.0, 20.0, 10.0, 5.0);

    // Corporate tax base = 100 - 20 - 10 = 70
    // Corporate tax = 70 * 0.22 = 15.4
    assertEquals(15.4, result.getCorporateTax(), 0.01);

    // Petroleum tax base = 100 - 20 - 10 - 5 = 65
    // Petroleum tax = 65 * 0.56 = 36.4
    assertEquals(36.4, result.getPetroleumTax(), 0.01);

    // Total tax = 15.4 + 36.4 = 51.8
    assertEquals(51.8, result.getTotalTax(), 0.01);

    // After-tax income = 100 - 20 - 51.8 = 28.2
    assertEquals(28.2, result.getAfterTaxIncome(), 0.01);
  }

  @Test
  void testZeroRevenueNoTax() {
    TaxResult result = taxModel.calculateTax(0.0, 20.0, 10.0, 5.0);

    assertEquals(0.0, result.getCorporateTax(), 0.001);
    assertEquals(0.0, result.getPetroleumTax(), 0.001);
    assertEquals(0.0, result.getTotalTax(), 0.001);
  }

  @Test
  void testLossCarryForward() {
    // First year: loss
    TaxResult year1 = taxModel.calculateTax(50.0, 80.0, 10.0, 5.0);
    assertEquals(0.0, year1.getTotalTax(), 0.001);

    // Check loss carry-forward was accumulated
    assertTrue(taxModel.getCorporateTaxLossCarryForward() > 0);

    // Second year: profit (should use loss carry-forward)
    TaxResult year2 = taxModel.calculateTax(100.0, 20.0, 0.0, 0.0);

    // Tax should be reduced due to loss carry-forward
    assertTrue(year2.getTotalTax() < 100 * 0.78);
  }

  @Test
  void testDepreciationCalculation() {
    double capex = 600.0; // 600 MUSD

    // Straight-line over 6 years = 100 per year
    for (int year = 1; year <= 6; year++) {
      double depreciation = taxModel.calculateDepreciation(capex, year);
      assertEquals(100.0, depreciation, 0.001);
    }

    // Year 7 and beyond: no depreciation
    assertEquals(0.0, taxModel.calculateDepreciation(capex, 7), 0.001);
    assertEquals(0.0, taxModel.calculateDepreciation(capex, 0), 0.001);
  }

  @Test
  void testUpliftCalculation() {
    double capex = 1000.0; // 1000 MUSD

    // Uplift = 5.5% per year for 4 years
    for (int year = 1; year <= 4; year++) {
      double uplift = taxModel.calculateUplift(capex, year);
      assertEquals(55.0, uplift, 0.001);
    }

    // Year 5 and beyond: no uplift
    assertEquals(0.0, taxModel.calculateUplift(capex, 5), 0.001);
    assertEquals(0.0, taxModel.calculateUplift(capex, 0), 0.001);
  }

  @Test
  void testEffectiveTaxRate() {
    double effectiveRate = taxModel.calculateEffectiveTaxRate(100.0, 20.0, 10.0, 5.0);

    // Should be less than marginal rate due to OPEX and deductions
    assertTrue(effectiveRate < 0.78);
    assertTrue(effectiveRate > 0);
  }

  @Test
  void testTotalMarginalRate() {
    assertEquals(0.78, taxModel.getTotalMarginalRate(), 0.001);
  }

  @Test
  void testResetLossCarryForward() {
    // Create some losses
    taxModel.calculateTax(50.0, 100.0, 10.0, 5.0);
    assertTrue(taxModel.getCorporateTaxLossCarryForward() > 0);

    // Reset
    taxModel.resetLossCarryForward();
    assertEquals(0.0, taxModel.getCorporateTaxLossCarryForward(), 0.001);
    assertEquals(0.0, taxModel.getPetroleumTaxLossCarryForward(), 0.001);
  }

  // ============================================================================
  // CASH FLOW ENGINE TESTS
  // ============================================================================

  @Test
  void testSimpleGasFieldCashFlow() {
    // Set CAPEX
    engine.setCapex(800.0, 2025); // 800 MUSD in 2025

    // Set prices
    engine.setGasPrice(0.25); // USD/Sm3
    engine.setGasTariff(0.02); // USD/Sm3

    // Set OPEX
    engine.setOpexPercentOfCapex(0.04); // 4% of CAPEX

    // Add production (10 years)
    engine.addAnnualProduction(2026, 0, 5.0e9, 0); // 5 GSm3
    engine.addAnnualProduction(2027, 0, 10.0e9, 0); // 10 GSm3
    engine.addAnnualProduction(2028, 0, 10.0e9, 0);
    engine.addAnnualProduction(2029, 0, 9.0e9, 0);
    engine.addAnnualProduction(2030, 0, 8.0e9, 0);
    engine.addAnnualProduction(2031, 0, 7.0e9, 0);
    engine.addAnnualProduction(2032, 0, 6.0e9, 0);
    engine.addAnnualProduction(2033, 0, 5.0e9, 0);
    engine.addAnnualProduction(2034, 0, 4.0e9, 0);
    engine.addAnnualProduction(2035, 0, 3.0e9, 0);

    // Calculate at 8% discount rate
    CashFlowResult result = engine.calculate(0.08);

    // Verify basic properties
    assertEquals(800.0, result.getTotalCapex(), 0.1);
    assertTrue(result.getTotalRevenue() > 0);
    assertTrue(result.getNpv() > 0); // Should be profitable

    // IRR should be reasonable (> 10%)
    assertTrue(result.getIrr() > 0.10);

    // Project duration
    assertEquals(11, result.getProjectDuration()); // 2025-2035
  }

  @Test
  void testBreakevenGasPrice() {
    // Set up a marginal project
    engine.setCapex(500.0, 2025);
    engine.setGasPrice(0.15);
    engine.setOpexPercentOfCapex(0.05);

    for (int year = 2026; year <= 2035; year++) {
      engine.addAnnualProduction(year, 0, 3.0e9, 0);
    }

    double breakeven = engine.calculateBreakevenGasPrice(0.08);

    // Breakeven should be positive and reasonable
    assertTrue(breakeven > 0);
    assertTrue(breakeven < 0.50);

    // Verify: at breakeven price, NPV should be ~0
    engine.setGasPrice(breakeven);
    double npvAtBreakeven = engine.calculateNPV(0.08);
    assertEquals(0.0, npvAtBreakeven, 1.0); // Within 1 MUSD tolerance
  }

  @Test
  void testOilFieldCashFlow() {
    // Oil field with 50 MMbbl production
    engine.setCapex(1200.0, 2025);
    engine.setOilPrice(75.0); // USD/bbl
    engine.setOilTariff(2.0); // USD/bbl
    engine.setOpexPercentOfCapex(0.04);

    // Ramp-up, plateau, decline
    engine.addAnnualProduction(2026, 5.0e6, 0, 0); // 5 MMbbl
    engine.addAnnualProduction(2027, 10.0e6, 0, 0);
    engine.addAnnualProduction(2028, 10.0e6, 0, 0);
    engine.addAnnualProduction(2029, 8.0e6, 0, 0);
    engine.addAnnualProduction(2030, 6.0e6, 0, 0);
    engine.addAnnualProduction(2031, 5.0e6, 0, 0);
    engine.addAnnualProduction(2032, 4.0e6, 0, 0);
    engine.addAnnualProduction(2033, 2.0e6, 0, 0);

    CashFlowResult result = engine.calculate(0.08);

    // Should be profitable at $75/bbl
    assertTrue(result.getNpv() > 0);
    assertTrue(result.getTotalRevenue() > 1000); // > 1 billion USD
  }

  @Test
  void testCashFlowSummary() {
    engine.setCapex(800.0, 2025);
    engine.setGasPrice(0.25);
    engine.addAnnualProduction(2026, 0, 5.0e9, 0);
    engine.addAnnualProduction(2027, 0, 8.0e9, 0);

    CashFlowResult result = engine.calculate(0.08);

    String summary = result.getSummary();
    assertTrue(summary.contains("NPV"));
    assertTrue(summary.contains("IRR"));
    assertTrue(summary.contains("CAPEX"));
  }

  @Test
  void testCashFlowMarkdownTable() {
    engine.setCapex(500.0, 2025);
    engine.setGasPrice(0.20);
    engine.addAnnualProduction(2026, 0, 3.0e9, 0);
    engine.addAnnualProduction(2027, 0, 5.0e9, 0);

    CashFlowResult result = engine.calculate(0.08);

    String table = result.toMarkdownTable();
    assertTrue(table.contains("| Year |"));
    assertTrue(table.contains("| Revenue |"));
  }

  @Test
  void testMultiYearCapex() {
    // Phased CAPEX
    engine.addCapex(300.0, 2024);
    engine.addCapex(400.0, 2025);
    engine.addCapex(100.0, 2026);

    engine.setGasPrice(0.25);
    for (int year = 2027; year <= 2036; year++) {
      engine.addAnnualProduction(year, 0, 5.0e9, 0);
    }

    CashFlowResult result = engine.calculate(0.08);

    assertEquals(800.0, result.getTotalCapex(), 0.1);
    assertTrue(result.getAnnualCashFlows().size() > 10);
  }

  @Test
  void testCustomTaxModel() {
    // Use lower tax rates (international scenario)
    NorwegianTaxModel customTax = new NorwegianTaxModel(0.25, 0.0); // 25% corporate, no petroleum
    engine.setTaxModel(customTax);

    engine.setCapex(500.0, 2025);
    engine.setGasPrice(0.25);
    engine.addAnnualProduction(2026, 0, 5.0e9, 0);

    CashFlowResult resultLowTax = engine.calculate(0.08);

    // Reset to default Norwegian tax
    engine.setTaxModel(new NorwegianTaxModel());
    CashFlowResult resultHighTax = engine.calculate(0.08);

    // Lower tax should give higher NPV
    assertTrue(resultLowTax.getNpv() > resultHighTax.getNpv());
  }
}
