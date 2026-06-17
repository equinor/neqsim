package neqsim.process.util.fielddevelopment;

import static org.junit.jupiter.api.Assertions.*;
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

    double[] production = new double[] {0, 10, 50, 100, 100};
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

    double[] production = new double[] {0, 50, 50, 50, 50};
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

    double[] prod = new double[] {0, 100, 100, 100, 100};
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
    double[] production = new double[] {0, 100, 100};
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
}
