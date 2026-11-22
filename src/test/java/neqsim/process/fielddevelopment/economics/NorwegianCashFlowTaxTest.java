package neqsim.process.fielddevelopment.economics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests the Norwegian 2022 cash-flow tax preset and the immediate loss refund option.
 *
 * @author ESOL
 * @version 1.0
 */
public class NorwegianCashFlowTaxTest {
  /**
   * After-tax income equals 22 % of the pre-tax cash flow when the investment is expensed in full.
   */
  @Test
  void afterTaxCashFlowIsTwentyTwoPercentOfPreTax() {
    GenericTaxModel model = new GenericTaxModel(FiscalParameters.norwegianCashFlowTax2022());
    assertTrue(model.getParameters().isLossRefund());
    double revenue = 1000.0;
    double opex = 200.0;
    double capex = 300.0;
    double depreciation = model.calculateDepreciation(capex, 1);
    assertEquals(capex, depreciation, 1e-9);
    TaxModel.TaxResult result = model.calculateTax(revenue, opex, depreciation, 0.0);
    double preTax = revenue - opex - capex;
    assertEquals(0.78 * preTax, result.getTotalTax(), 1e-9);
    assertEquals(0.22 * preTax, result.getAfterTaxIncome() - capex, 1e-9);
  }

  /** A negative tax base is refunded in the same year at the 78 % marginal rate. */
  @Test
  void lossIsRefundedImmediately() {
    GenericTaxModel model = new GenericTaxModel(FiscalParameters.norwegianCashFlowTax2022());
    TaxModel.TaxResult result = model.calculateTax(0.0, 0.0, 500.0, 0.0);
    assertEquals(-0.78 * 500.0, result.getTotalTax(), 1e-9);
  }

  /** The default Norwegian model still carries losses forward and never refunds. */
  @Test
  void defaultModelDoesNotRefund() {
    GenericTaxModel model = GenericTaxModel.forCountry("NO");
    TaxModel.TaxResult result = model.calculateTax(0.0, 0.0, 500.0, 0.0);
    assertEquals(0.0, result.getTotalTax(), 1e-9);
  }
}
