package neqsim.process.util.fielddevelopment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for DCFCalculator.
 */
public class DCFCalculatorTest {

  @Test
  public void testBasicNPVCalculation() {
    DCFCalculator dcf = new DCFCalculator();
    dcf.setDiscountRate(0.10);
    dcf.setProjectLifeYears(10);
    dcf.setTaxRate(0.0); // No tax for simplicity
    dcf.setRoyaltyRate(0.0);
    dcf.setDepreciationYears(0);

    dcf.addCapex(0, 1000.0);

    double[] production = new double[10];
    for (int i = 1; i < 10; i++) {
      production[i] = 100.0;
    }
    dcf.setAnnualProduction(production);
    dcf.setProductPrice(2.0); // revenue = 200 per year
    dcf.setAnnualOpex(0.0);

    dcf.calculate();

    // NPV = -1000 + 200/1.1 + 200/1.1^2 + ... + 200/1.1^9
    // Sum of annuity factor for 9 years at 10% discount
    double npv = dcf.getNPV();
    assertTrue(npv > 0, "NPV should be positive for this profitable project");

    // IRR should exist
    double irr = dcf.getIRR();
    assertFalse(Double.isNaN(irr));
    assertTrue(irr > 0.0);

    // Payback should be <10
    int payback = dcf.getPaybackYear();
    assertTrue(payback > 0 && payback < 10);
  }

  @Test
  public void testNPVWithTax() {
    DCFCalculator dcf = new DCFCalculator();
    dcf.setDiscountRate(0.08);
    dcf.setProjectLifeYears(15);
    dcf.setTaxRate(0.22);
    dcf.setDepreciationYears(6);

    dcf.addCapex(0, 500e6);
    dcf.addCapex(1, 300e6);

    double[] production = new double[15];
    for (int i = 2; i < 15; i++) {
      production[i] = 10e6;
    }
    dcf.setAnnualProduction(production);
    dcf.setProductPrice(1.5);
    dcf.setAnnualOpex(50e6);

    dcf.calculate();

    // Revenues = 15M/yr, OPEX = 50M/yr for 13 years should give positive NPV
    double npv = dcf.getNPV();
    assertFalse(Double.isNaN(npv));

    // Annual cash flow should exist
    double[] cashFlow = dcf.getAnnualCashFlow();
    assertEquals(15, cashFlow.length);

    // Year 0 should be negative (CAPEX)
    assertTrue(cashFlow[0] < 0);
  }

  @Test
  public void testLossCarryForward() {
    DCFCalculator dcf = new DCFCalculator();
    dcf.setDiscountRate(0.10);
    dcf.setProjectLifeYears(5);
    dcf.setTaxRate(0.25);
    dcf.setDepreciationYears(3);

    dcf.addCapex(0, 300.0);

    double[] production = new double[] { 0, 10, 50, 100, 100 };
    dcf.setAnnualProduction(production);
    dcf.setProductPrice(2.0);
    dcf.setAnnualOpex(20.0);

    dcf.calculate();

    // Should have calculated without error
    double[] cumCF = dcf.getCumulativeCashFlow();
    assertEquals(5, cumCF.length);
  }

  @Test
  public void testProfitabilityIndex() {
    DCFCalculator dcf = new DCFCalculator();
    dcf.setDiscountRate(0.10);
    dcf.setProjectLifeYears(5);
    dcf.setTaxRate(0.0);
    dcf.setDepreciationYears(0);

    dcf.addCapex(0, 100.0);

    double[] production = new double[] { 0, 50, 50, 50, 50 };
    dcf.setAnnualProduction(production);
    dcf.setProductPrice(1.0);
    dcf.setAnnualOpex(0.0);

    dcf.calculate();

    double pi = dcf.getProfitabilityIndex();
    assertTrue(pi > 1.0, "PI should be > 1 for profitable project");
  }

  @Test
  public void testJsonOutput() {
    DCFCalculator dcf = new DCFCalculator();
    dcf.setDiscountRate(0.08);
    dcf.setProjectLifeYears(5);
    dcf.addCapex(0, 1000.0);

    double[] prod = new double[] { 0, 100, 100, 100, 100 };
    dcf.setAnnualProduction(prod);
    dcf.setProductPrice(5.0);

    dcf.calculate();

    String json = dcf.toJson();
    assertNotNull(json);
    assertTrue(json.contains("Discounted Cash Flow"));
    assertTrue(json.contains("NPV"));
    assertTrue(json.contains("IRR"));
    assertTrue(json.contains("cashFlowTable"));
  }

  @Test
  public void testIRRZeroForBreakevenProject() {
    // A project that roughly breaks even should have IRR near 0
    DCFCalculator dcf = new DCFCalculator();
    dcf.setDiscountRate(0.0);
    dcf.setProjectLifeYears(3);
    dcf.setTaxRate(0.0);
    dcf.setDepreciationYears(0);

    dcf.addCapex(0, 200.0);
    double[] production = new double[] { 0, 100, 100 };
    dcf.setAnnualProduction(production);
    dcf.setProductPrice(1.0);
    dcf.setAnnualOpex(0.0);

    dcf.calculate();

    // NPV at 0% discount = -200 + 100 + 100 = 0
    assertEquals(0.0, dcf.getNPV(), 1.0);

    // IRR should be near 0
    double irr = dcf.getIRR();
    assertTrue(Math.abs(irr) < 0.01);
  }

  @Test
  public void testMultiProductRevenueEqualsSumOfProducts() {
    // Multi-product model: oil + gas + condensate priced separately should give the same
    // NPV as a single legacy product whose annual revenue equals the summed product revenue.
    int years = 6;
    double[] oil = new double[] { 0, 100, 100, 80, 60, 40 };
    double[] gas = new double[] { 0, 200, 200, 200, 150, 120 };
    double[] cond = new double[] { 0, 10, 10, 8, 6, 4 };
    double oilPrice = 4.0;
    double gasPrice = 1.5;
    double condPrice = 3.0;

    DCFCalculator multi = new DCFCalculator();
    multi.setDiscountRate(0.08);
    multi.setProjectLifeYears(years);
    multi.setTaxRate(0.0);
    multi.setDepreciationYears(0);
    multi.setRoyaltyRate(0.0);
    multi.addCapex(0, 500.0);
    multi.addProduct("oil", oil, oilPrice);
    multi.addProduct("gas", gas, gasPrice);
    multi.addProduct("condensate", cond, condPrice);
    multi.setAnnualOpex(0.0);
    multi.calculate();

    // Equivalent single-product reference: production array equals total revenue, price = 1.0
    double[] totalRevenue = new double[years];
    for (int y = 0; y < years; y++) {
      totalRevenue[y] = oil[y] * oilPrice + gas[y] * gasPrice + cond[y] * condPrice;
    }
    DCFCalculator ref = new DCFCalculator();
    ref.setDiscountRate(0.08);
    ref.setProjectLifeYears(years);
    ref.setTaxRate(0.0);
    ref.setDepreciationYears(0);
    ref.setRoyaltyRate(0.0);
    ref.addCapex(0, 500.0);
    ref.setAnnualProduction(totalRevenue);
    ref.setProductPrice(1.0);
    ref.setAnnualOpex(0.0);
    ref.calculate();

    assertEquals(ref.getNPV(), multi.getNPV(), 1e-6, "Multi-product NPV must equal sum of individual product revenues");
    assertEquals(3, multi.getProductNames().size());
    assertTrue(multi.getProductNames().contains("oil"));
  }

  @Test
  public void testProductRevenueAccessor() {
    DCFCalculator dcf = new DCFCalculator();
    dcf.setProjectLifeYears(4);
    double[] oil = new double[] { 0, 100, 50, 25 };
    dcf.addProduct("oil", oil, 4.0);
    dcf.calculate();

    assertEquals(400.0, dcf.getProductRevenue("oil", 1), 1e-9);
    assertEquals(100.0, dcf.getProductRevenue("oil", 3), 1e-9);
    assertEquals(0.0, dcf.getProductRevenue("oil", 0), 1e-9);
    assertEquals(0.0, dcf.getProductRevenue("missing", 1), 1e-9);
    assertEquals(0.0, dcf.getProductRevenue("oil", 99), 1e-9);
  }

  @Test
  public void testVariableCostReducesNPV() {
    // A produced-water handling cost that scales with water volume should reduce NPV.
    int years = 5;
    double[] oil = new double[] { 0, 100, 100, 100, 100 };
    double[] water = new double[] { 0, 50, 80, 120, 160 };

    DCFCalculator withoutCost = new DCFCalculator();
    withoutCost.setDiscountRate(0.08);
    withoutCost.setProjectLifeYears(years);
    withoutCost.setTaxRate(0.0);
    withoutCost.setDepreciationYears(0);
    withoutCost.addCapex(0, 100.0);
    withoutCost.addProduct("oil", oil, 4.0);
    withoutCost.calculate();

    DCFCalculator withCost = new DCFCalculator();
    withCost.setDiscountRate(0.08);
    withCost.setProjectLifeYears(years);
    withCost.setTaxRate(0.0);
    withCost.setDepreciationYears(0);
    withCost.addCapex(0, 100.0);
    withCost.addProduct("oil", oil, 4.0);
    withCost.addVariableCost("waterHandling", water, 0.5);
    withCost.calculate();

    assertTrue(withCost.getNPV() < withoutCost.getNPV(), "Adding a volume-linked variable cost must reduce NPV");
  }

  @Test
  public void testMultiProductJsonContainsProducts() {
    DCFCalculator dcf = new DCFCalculator();
    dcf.setProjectLifeYears(3);
    dcf.addCapex(0, 100.0);
    dcf.addProduct("oil", new double[] { 0, 100, 100 }, 4.0);
    dcf.addProduct("gas", new double[] { 0, 200, 200 }, 1.5);
    dcf.calculate();

    String json = dcf.toJson();
    assertNotNull(json);
    assertTrue(json.contains("products"));
    assertTrue(json.contains("oil"));
    assertTrue(json.contains("gas"));
    assertTrue(json.contains("revenue"));
  }

  @Test
  public void testLegacyApiUnchangedWhenNoProductsAdded() {
    // Backward compatibility: with no products added, behaviour must match the legacy model.
    DCFCalculator dcf = new DCFCalculator();
    dcf.setDiscountRate(0.10);
    dcf.setProjectLifeYears(5);
    dcf.setTaxRate(0.0);
    dcf.setDepreciationYears(0);
    dcf.addCapex(0, 100.0);
    dcf.setAnnualProduction(new double[] { 0, 50, 50, 50, 50 });
    dcf.setProductPrice(1.0);
    dcf.calculate();

    // NPV = -100 + 50/1.1 + 50/1.1^2 + 50/1.1^3 + 50/1.1^4
    double expected = -100.0;
    for (int y = 1; y <= 4; y++) {
      expected += 50.0 / Math.pow(1.10, y);
    }
    assertEquals(expected, dcf.getNPV(), 1e-6);
    assertTrue(dcf.getProductNames().isEmpty());
  }
}
