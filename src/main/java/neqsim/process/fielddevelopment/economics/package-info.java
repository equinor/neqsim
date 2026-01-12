/**
 * Economics package for field development analysis.
 *
 * <p>
 * This package provides comprehensive economic modeling capabilities for oil and gas field
 * development, including:
 * </p>
 * <ul>
 * <li>{@link neqsim.process.fielddevelopment.economics.NorwegianTaxModel} - Norwegian Continental
 * Shelf petroleum tax model with corporate tax, special petroleum tax, and uplift deductions</li>
 * <li>{@link neqsim.process.fielddevelopment.economics.CashFlowEngine} - Full-lifecycle cash flow
 * generation with NPV, IRR, and breakeven analysis</li>
 * </ul>
 *
 * <h2>Norwegian Petroleum Tax Model</h2>
 * <p>
 * The Norwegian tax regime features:
 * </p>
 * <ul>
 * <li><b>22% corporate tax</b> on net income</li>
 * <li><b>56% special petroleum tax</b> on petroleum extraction income</li>
 * <li><b>Uplift deduction</b> of 5.5% per year for 4 years (22% total)</li>
 * <li><b>6-year straight-line depreciation</b> for offshore investments</li>
 * </ul>
 *
 * <h2>Cash Flow Analysis</h2>
 * <p>
 * The cash flow engine supports:
 * </p>
 * <ul>
 * <li>Year-by-year cash flow generation</li>
 * <li>Net Present Value (NPV) calculation</li>
 * <li>Internal Rate of Return (IRR) calculation</li>
 * <li>Breakeven price analysis</li>
 * <li>Payback period calculation</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * // Create tax model
 * NorwegianTaxModel taxModel = new NorwegianTaxModel();
 *
 * // Calculate tax for a single year
 * NorwegianTaxModel.TaxResult result = taxModel.calculateTax(500.0, // Gross revenue (MUSD)
 *     100.0, // OPEX (MUSD)
 *     80.0, // Depreciation (MUSD)
 *     44.0 // Uplift deduction (MUSD)
 * );
 *
 * System.out.println("After-tax income: " + result.getAfterTaxIncome());
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 */
package neqsim.process.fielddevelopment.economics;
