package neqsim.process.fielddevelopment.economics;

import java.io.Serializable;

/**
 * Interface for country-specific tax model calculations.
 *
 * <p>
 * This interface defines the contract for calculating taxes and deductions under any fiscal regime.
 * Implementations handle the specific rules for different countries and regions.
 * </p>
 *
 * <h2>Implementations</h2>
 * <ul>
 * <li>{@link GenericTaxModel} - Parameter-driven generic implementation</li>
 * <li>{@link NorwegianTaxModel} - Legacy Norwegian-specific implementation</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * // Get tax model for a country
 * TaxModel model = TaxModelRegistry.createModel("NO");
 * 
 * // Calculate tax
 * TaxModel.TaxResult result = model.calculateTax(500.0, 100.0, 80.0, 44.0);
 * System.out.println("Total tax: " + result.getTotalTax());
 * System.out.println("After-tax income: " + result.getAfterTaxIncome());
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see FiscalParameters
 * @see GenericTaxModel
 * @see TaxModelRegistry
 */
public interface TaxModel extends Serializable {

  /**
   * Gets the fiscal parameters for this model.
   *
   * @return fiscal parameters
   */
  FiscalParameters getParameters();

  /**
   * Gets the country code.
   *
   * @return country code
   */
  String getCountryCode();

  /**
   * Gets the country name.
   *
   * @return country name
   */
  String getCountryName();

  /**
   * Calculates tax for a single year.
   *
   * @param grossRevenue total revenue for the year
   * @param opex operating expenditure for the year
   * @param depreciation depreciation deduction for the year
   * @param uplift uplift/incentive deduction for the year
   * @return tax calculation result
   */
  TaxResult calculateTax(double grossRevenue, double opex, double depreciation, double uplift);

  /**
   * Calculates annual depreciation.
   *
   * @param capex total capital expenditure
   * @param year year number (1 = first year of depreciation)
   * @return depreciation amount for the specified year
   */
  double calculateDepreciation(double capex, int year);

  /**
   * Calculates uplift/investment incentive deduction.
   *
   * @param capex total capital expenditure
   * @param year year number (1 = first year of eligibility)
   * @return uplift amount for the specified year
   */
  double calculateUplift(double capex, int year);

  /**
   * Calculates royalty on gross revenue.
   *
   * @param grossRevenue total revenue for the year
   * @return royalty amount
   */
  double calculateRoyalty(double grossRevenue);

  /**
   * Calculates the effective tax rate.
   *
   * @param grossRevenue total revenue
   * @param opex operating expenditure
   * @param depreciation depreciation deduction
   * @param uplift uplift deduction
   * @return effective tax rate (0-1)
   */
  double calculateEffectiveTaxRate(double grossRevenue, double opex, double depreciation,
      double uplift);

  /**
   * Gets the total marginal tax rate.
   *
   * @return sum of all tax rates (0-1)
   */
  double getTotalMarginalTaxRate();

  /**
   * Resets any accumulated state (loss carry-forward, etc.).
   */
  void reset();

  /**
   * Gets the current loss carry-forward balance.
   *
   * @return loss carry-forward amount
   */
  double getLossCarryForward();

  /**
   * Result of a tax calculation.
   *
   * <p>
   * This class is shared across all TaxModel implementations to provide consistent results.
   * </p>
   */
  public static class TaxResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double grossRevenue;
    private final double opex;
    private final double depreciation;
    private final double uplift;
    private final double royalty;
    private final double corporateTaxBase;
    private final double corporateTax;
    private final double resourceTaxBase;
    private final double resourceTax;
    private final double totalTax;
    private final double afterTaxIncome;
    private final double governmentTake;

    /**
     * Creates a new tax result.
     *
     * @param grossRevenue gross revenue
     * @param opex operating expenditure
     * @param depreciation depreciation
     * @param uplift uplift deduction
     * @param royalty royalty
     * @param corporateTaxBase corporate tax base
     * @param corporateTax corporate tax
     * @param resourceTaxBase resource tax base
     * @param resourceTax resource tax
     * @param totalTax total tax
     * @param afterTaxIncome after-tax income
     */
    public TaxResult(double grossRevenue, double opex, double depreciation, double uplift,
        double royalty, double corporateTaxBase, double corporateTax, double resourceTaxBase,
        double resourceTax, double totalTax, double afterTaxIncome) {
      this.grossRevenue = grossRevenue;
      this.opex = opex;
      this.depreciation = depreciation;
      this.uplift = uplift;
      this.royalty = royalty;
      this.corporateTaxBase = corporateTaxBase;
      this.corporateTax = corporateTax;
      this.resourceTaxBase = resourceTaxBase;
      this.resourceTax = resourceTax;
      this.totalTax = totalTax;
      this.afterTaxIncome = afterTaxIncome;
      this.governmentTake = royalty + totalTax;
    }

    /**
     * Creates a tax result without royalty (for backward compatibility).
     *
     * @param grossRevenue gross revenue
     * @param opex operating expenditure
     * @param depreciation depreciation
     * @param uplift uplift deduction
     * @param corporateTaxBase corporate tax base
     * @param corporateTax corporate tax
     * @param resourceTaxBase resource tax base
     * @param resourceTax resource tax
     * @param totalTax total tax
     * @param afterTaxIncome after-tax income
     */
    public TaxResult(double grossRevenue, double opex, double depreciation, double uplift,
        double corporateTaxBase, double corporateTax, double resourceTaxBase, double resourceTax,
        double totalTax, double afterTaxIncome) {
      this(grossRevenue, opex, depreciation, uplift, 0.0, corporateTaxBase, corporateTax,
          resourceTaxBase, resourceTax, totalTax, afterTaxIncome);
    }

    // Getters

    public double getGrossRevenue() {
      return grossRevenue;
    }

    public double getOpex() {
      return opex;
    }

    public double getDepreciation() {
      return depreciation;
    }

    public double getUplift() {
      return uplift;
    }

    public double getRoyalty() {
      return royalty;
    }

    public double getCorporateTaxBase() {
      return corporateTaxBase;
    }

    public double getCorporateTax() {
      return corporateTax;
    }

    public double getResourceTaxBase() {
      return resourceTaxBase;
    }

    public double getResourceTax() {
      return resourceTax;
    }

    /**
     * Gets petroleum tax (alias for resource tax).
     *
     * @return petroleum/resource tax
     */
    public double getPetroleumTax() {
      return resourceTax;
    }

    /**
     * Gets petroleum tax base (alias for resource tax base).
     *
     * @return petroleum/resource tax base
     */
    public double getPetroleumTaxBase() {
      return resourceTaxBase;
    }

    public double getTotalTax() {
      return totalTax;
    }

    public double getAfterTaxIncome() {
      return afterTaxIncome;
    }

    public double getGovernmentTake() {
      return governmentTake;
    }

    /**
     * Gets the effective tax rate.
     *
     * @return effective tax rate (0-1)
     */
    public double getEffectiveTaxRate() {
      if (grossRevenue <= 0) {
        return 0.0;
      }
      return totalTax / grossRevenue;
    }

    /**
     * Gets the government take percentage.
     *
     * @return government take as fraction of revenue (0-1)
     */
    public double getGovernmentTakePercentage() {
      if (grossRevenue <= 0) {
        return 0.0;
      }
      return governmentTake / grossRevenue;
    }

    @Override
    public String toString() {
      return String.format("TaxResult[revenue=%.2f, tax=%.2f (%.1f%%), afterTax=%.2f]",
          grossRevenue, totalTax, getEffectiveTaxRate() * 100, afterTaxIncome);
    }
  }
}
