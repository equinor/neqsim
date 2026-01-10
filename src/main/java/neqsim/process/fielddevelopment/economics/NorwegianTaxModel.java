package neqsim.process.fielddevelopment.economics;

import java.io.Serializable;

/**
 * Norwegian Continental Shelf petroleum tax model.
 *
 * <p>
 * This class implements the Norwegian petroleum tax regime as of 2024, including:
 * </p>
 * <ul>
 * <li><b>Corporate tax (22%)</b>: Standard Norwegian corporate income tax applied to all net
 * income</li>
 * <li><b>Special petroleum tax (56%)</b>: Additional tax on petroleum extraction income</li>
 * <li><b>Uplift deduction</b>: Special deduction against the petroleum tax base (5.5% per year for
 * 4 years = 22% total)</li>
 * <li><b>Loss carry-forward</b>: Ability to carry forward losses with interest</li>
 * </ul>
 *
 * <h2>Tax Calculation</h2>
 * <p>
 * The total marginal tax rate is 78% (22% + 56%), but the effective rate varies based on:
 * </p>
 * <ul>
 * <li>Timing of investments (uplift deductions)</li>
 * <li>Financing structure</li>
 * <li>Depreciation schedules</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * NorwegianTaxModel taxModel = new NorwegianTaxModel();
 * 
 * // Calculate tax for a single year
 * TaxResult result = taxModel.calculateTax(500.0, // Gross revenue (MUSD)
 *     100.0, // OPEX (MUSD)
 *     80.0, // Depreciation (MUSD)
 *     44.0 // Uplift deduction (MUSD)
 * );
 * 
 * System.out.println("Corporate tax: " + result.getCorporateTax());
 * System.out.println("Petroleum tax: " + result.getPetroleumTax());
 * System.out.println("Total tax: " + result.getTotalTax());
 * System.out.println("After-tax income: " + result.getAfterTaxIncome());
 * }</pre>
 *
 * <h2>References</h2>
 * <ul>
 * <li>Norwegian Petroleum Tax Act (Petroleumsskatteloven)</li>
 * <li>Ministry of Finance circulars</li>
 * </ul>
 *
 * <p>
 * <b>Note:</b> This class is maintained for backward compatibility. For new code, consider using
 * {@link GenericTaxModel} with {@link TaxModelRegistry#getParameters(String)} for "NO".
 * </p>
 *
 * @author ESOL
 * @version 1.0
 * @see CashFlowEngine
 * @see TaxModel
 * @see GenericTaxModel
 */
public class NorwegianTaxModel implements TaxModel {
  private static final long serialVersionUID = 1000L;

  // ============================================================================
  // TAX RATE CONSTANTS
  // ============================================================================

  /**
   * Corporate income tax rate (2024). This is the standard Norwegian corporate tax rate applied to
   * all companies.
   */
  public static final double DEFAULT_CORPORATE_TAX_RATE = 0.22;

  /**
   * Special petroleum tax rate (2024). This additional tax applies only to petroleum extraction
   * activities on the Norwegian Continental Shelf.
   */
  public static final double DEFAULT_PETROLEUM_TAX_RATE = 0.56;

  /**
   * Total marginal tax rate. Sum of corporate and petroleum tax rates.
   */
  public static final double TOTAL_MARGINAL_RATE = 0.78;

  // ============================================================================
  // UPLIFT CONSTANTS
  // ============================================================================

  /**
   * Annual uplift rate. The uplift is a special deduction against the petroleum tax base to
   * compensate for the high marginal tax rate and encourage investment.
   */
  public static final double DEFAULT_UPLIFT_RATE = 0.055;

  /**
   * Number of years for uplift deduction. Uplift can be claimed for 4 years from the year of
   * investment.
   */
  public static final int DEFAULT_UPLIFT_YEARS = 4;

  /**
   * Total uplift percentage (5.5% x 4 years = 22%).
   */
  public static final double TOTAL_UPLIFT_PERCENTAGE = 0.22;

  // ============================================================================
  // DEPRECIATION CONSTANTS
  // ============================================================================

  /**
   * Standard depreciation period for offshore installations (years). Straight-line depreciation
   * over 6 years.
   */
  public static final int DEFAULT_DEPRECIATION_YEARS = 6;

  // ============================================================================
  // INSTANCE VARIABLES
  // ============================================================================

  private double corporateTaxRate;
  private double petroleumTaxRate;
  private double upliftRate;
  private int upliftYears;
  private int depreciationYears;

  // Loss carry-forward
  private double corporateTaxLossCarryForward;
  private double petroleumTaxLossCarryForward;

  // ============================================================================
  // CONSTRUCTORS
  // ============================================================================

  /**
   * Creates a tax model with default Norwegian petroleum tax parameters.
   *
   * <p>
   * Default values (2024):
   * </p>
   * <ul>
   * <li>Corporate tax rate: 22%</li>
   * <li>Petroleum tax rate: 56%</li>
   * <li>Uplift rate: 5.5% per year</li>
   * <li>Uplift years: 4</li>
   * <li>Depreciation years: 6</li>
   * </ul>
   */
  public NorwegianTaxModel() {
    this.corporateTaxRate = DEFAULT_CORPORATE_TAX_RATE;
    this.petroleumTaxRate = DEFAULT_PETROLEUM_TAX_RATE;
    this.upliftRate = DEFAULT_UPLIFT_RATE;
    this.upliftYears = DEFAULT_UPLIFT_YEARS;
    this.depreciationYears = DEFAULT_DEPRECIATION_YEARS;
    this.corporateTaxLossCarryForward = 0.0;
    this.petroleumTaxLossCarryForward = 0.0;
  }

  /**
   * Creates a tax model with custom tax rates.
   *
   * @param corporateTaxRate corporate tax rate (0-1)
   * @param petroleumTaxRate petroleum tax rate (0-1)
   */
  public NorwegianTaxModel(double corporateTaxRate, double petroleumTaxRate) {
    this();
    this.corporateTaxRate = corporateTaxRate;
    this.petroleumTaxRate = petroleumTaxRate;
  }

  // ============================================================================
  // TAX CALCULATION METHODS
  // ============================================================================

  /**
   * Calculates tax for a single year.
   *
   * <p>
   * The calculation follows Norwegian petroleum tax rules:
   * </p>
   * <ol>
   * <li>Calculate corporate tax base = Revenue - OPEX - Depreciation</li>
   * <li>Apply loss carry-forward if available</li>
   * <li>Calculate corporate tax (22% of positive tax base)</li>
   * <li>Calculate petroleum tax base = Revenue - OPEX - Depreciation - Uplift</li>
   * <li>Apply petroleum tax loss carry-forward if available</li>
   * <li>Calculate petroleum tax (56% of positive tax base)</li>
   * </ol>
   *
   * @param grossRevenue total revenue for the year (any currency unit)
   * @param opex operating expenditure for the year (same unit as revenue)
   * @param depreciation depreciation deduction for the year (same unit as revenue)
   * @param uplift uplift deduction for the year (same unit as revenue)
   * @return tax calculation result
   */
  @Override
  public TaxModel.TaxResult calculateTax(double grossRevenue, double opex, double depreciation,
      double uplift) {
    // Corporate tax calculation
    double corporateTaxBase = grossRevenue - opex - depreciation;

    // Apply loss carry-forward
    if (corporateTaxBase > 0 && corporateTaxLossCarryForward > 0) {
      double usedLoss = Math.min(corporateTaxBase, corporateTaxLossCarryForward);
      corporateTaxBase -= usedLoss;
      corporateTaxLossCarryForward -= usedLoss;
    } else if (corporateTaxBase < 0) {
      corporateTaxLossCarryForward += Math.abs(corporateTaxBase);
      corporateTaxBase = 0;
    }

    double corporateTax = corporateTaxBase * corporateTaxRate;

    // Petroleum tax calculation (includes uplift deduction)
    double petroleumTaxBase = grossRevenue - opex - depreciation - uplift;

    // Apply petroleum tax loss carry-forward
    if (petroleumTaxBase > 0 && petroleumTaxLossCarryForward > 0) {
      double usedLoss = Math.min(petroleumTaxBase, petroleumTaxLossCarryForward);
      petroleumTaxBase -= usedLoss;
      petroleumTaxLossCarryForward -= usedLoss;
    } else if (petroleumTaxBase < 0) {
      petroleumTaxLossCarryForward += Math.abs(petroleumTaxBase);
      petroleumTaxBase = 0;
    }

    double petroleumTax = petroleumTaxBase * petroleumTaxRate;

    double totalTax = corporateTax + petroleumTax;
    double afterTaxIncome = grossRevenue - opex - totalTax;

    return new TaxModel.TaxResult(grossRevenue, opex, depreciation, uplift, corporateTaxBase,
        corporateTax, petroleumTaxBase, petroleumTax, totalTax, afterTaxIncome);
  }

  /**
   * Calculates annual depreciation using straight-line method.
   *
   * @param capex total capital expenditure
   * @param year year number (1 = first year of depreciation)
   * @return depreciation amount for the specified year
   */
  @Override
  public double calculateDepreciation(double capex, int year) {
    if (year < 1 || year > depreciationYears) {
      return 0.0;
    }
    return capex / depreciationYears;
  }

  /**
   * Calculates uplift deduction for a specific year.
   *
   * @param capex total capital expenditure
   * @param year year number (1 = first year of uplift eligibility)
   * @return uplift amount for the specified year
   */
  @Override
  public double calculateUplift(double capex, int year) {
    if (year < 1 || year > upliftYears) {
      return 0.0;
    }
    return capex * upliftRate;
  }

  /**
   * Calculates the effective tax rate for a given income structure.
   *
   * <p>
   * The effective rate is typically lower than the 78% marginal rate due to:
   * </p>
   * <ul>
   * <li>Uplift deductions</li>
   * <li>Timing of investments</li>
   * <li>Loss carry-forward</li>
   * </ul>
   *
   * @param grossRevenue total revenue
   * @param opex operating expenditure
   * @param depreciation depreciation deduction
   * @param uplift uplift deduction
   * @return effective tax rate (0-1)
   */
  @Override
  public double calculateEffectiveTaxRate(double grossRevenue, double opex, double depreciation,
      double uplift) {
    if (grossRevenue <= 0) {
      return 0.0;
    }
    TaxModel.TaxResult result = calculateTax(grossRevenue, opex, depreciation, uplift);
    return result.getTotalTax() / grossRevenue;
  }

  /**
   * Calculates government take percentage.
   *
   * <p>
   * Government take includes:
   * </p>
   * <ul>
   * <li>Corporate tax</li>
   * <li>Petroleum tax</li>
   * <li>State's direct financial interest (SDFI) royalties (if applicable)</li>
   * </ul>
   *
   * @param grossRevenue total revenue
   * @param opex operating expenditure
   * @param depreciation depreciation deduction
   * @param uplift uplift deduction
   * @return government take percentage (0-1)
   */
  public double calculateGovernmentTake(double grossRevenue, double opex, double depreciation,
      double uplift) {
    double netIncome = grossRevenue - opex;
    if (netIncome <= 0) {
      return 0.0;
    }
    TaxModel.TaxResult result = calculateTax(grossRevenue, opex, depreciation, uplift);
    return result.getTotalTax() / netIncome;
  }

  // ============================================================================
  // UTILITY METHODS
  // ============================================================================

  /**
   * Resets loss carry-forward balances.
   *
   * <p>
   * Call this method when starting a new project evaluation to ensure losses from previous
   * calculations don't affect the new project.
   * </p>
   */
  public void resetLossCarryForward() {
    this.corporateTaxLossCarryForward = 0.0;
    this.petroleumTaxLossCarryForward = 0.0;
  }

  /**
   * Gets the total marginal tax rate.
   *
   * @return total marginal rate (corporate + petroleum)
   */
  public double getTotalMarginalRate() {
    return corporateTaxRate + petroleumTaxRate;
  }

  // ============================================================================
  // GETTERS AND SETTERS
  // ============================================================================

  /**
   * Gets the corporate tax rate.
   *
   * @return corporate tax rate (0-1)
   */
  public double getCorporateTaxRate() {
    return corporateTaxRate;
  }

  /**
   * Sets the corporate tax rate.
   *
   * @param corporateTaxRate corporate tax rate (0-1)
   */
  public void setCorporateTaxRate(double corporateTaxRate) {
    this.corporateTaxRate = corporateTaxRate;
  }

  /**
   * Gets the petroleum tax rate.
   *
   * @return petroleum tax rate (0-1)
   */
  public double getPetroleumTaxRate() {
    return petroleumTaxRate;
  }

  /**
   * Sets the petroleum tax rate.
   *
   * @param petroleumTaxRate petroleum tax rate (0-1)
   */
  public void setPetroleumTaxRate(double petroleumTaxRate) {
    this.petroleumTaxRate = petroleumTaxRate;
  }

  /**
   * Gets the annual uplift rate.
   *
   * @return uplift rate (0-1)
   */
  public double getUpliftRate() {
    return upliftRate;
  }

  /**
   * Sets the annual uplift rate.
   *
   * @param upliftRate uplift rate (0-1)
   */
  public void setUpliftRate(double upliftRate) {
    this.upliftRate = upliftRate;
  }

  /**
   * Gets the number of years for uplift deduction.
   *
   * @return number of uplift years
   */
  public int getUpliftYears() {
    return upliftYears;
  }

  /**
   * Sets the number of years for uplift deduction.
   *
   * @param upliftYears number of years
   */
  public void setUpliftYears(int upliftYears) {
    this.upliftYears = upliftYears;
  }

  /**
   * Gets the depreciation period in years.
   *
   * @return depreciation years
   */
  public int getDepreciationYears() {
    return depreciationYears;
  }

  /**
   * Sets the depreciation period in years.
   *
   * @param depreciationYears number of years
   */
  public void setDepreciationYears(int depreciationYears) {
    this.depreciationYears = depreciationYears;
  }

  /**
   * Gets the current corporate tax loss carry-forward balance.
   *
   * @return loss carry-forward amount
   */
  public double getCorporateTaxLossCarryForward() {
    return corporateTaxLossCarryForward;
  }

  /**
   * Gets the current petroleum tax loss carry-forward balance.
   *
   * @return loss carry-forward amount
   */
  public double getPetroleumTaxLossCarryForward() {
    return petroleumTaxLossCarryForward;
  }

  // ============================================================================
  // TAX MODEL INTEGRATION
  // ============================================================================

  /**
   * Creates a TaxModel instance for Norway using the registry.
   *
   * <p>
   * This factory method provides integration with the country-independent tax model framework.
   * </p>
   *
   * @return TaxModel for Norway
   */
  public static TaxModel createTaxModel() {
    return TaxModelRegistry.createModel("NO");
  }

  /**
   * Gets the fiscal parameters for Norway from the registry.
   *
   * @return Norwegian fiscal parameters
   */
  public static FiscalParameters getFiscalParameters() {
    return TaxModelRegistry.getParameters("NO");
  }

  /**
   * Gets fiscal parameters for this model.
   *
   * <p>
   * Creates a FiscalParameters instance based on current model settings.
   * </p>
   *
   * @return fiscal parameters for Norway
   */
  @Override
  public FiscalParameters getParameters() {
    return FiscalParameters.builder("NO").countryName("Norway")
        .fiscalSystemType(FiscalParameters.FiscalSystemType.CONCESSIONARY)
        .corporateTaxRate(corporateTaxRate).resourceTaxRate(petroleumTaxRate).royaltyRate(0.0)
        .depreciation(FiscalParameters.DepreciationMethod.STRAIGHT_LINE, depreciationYears)
        .uplift(upliftRate, upliftYears).build();
  }

  /**
   * Gets the country code.
   *
   * @return "NO" for Norway
   */
  @Override
  public String getCountryCode() {
    return "NO";
  }

  /**
   * Gets the country name.
   *
   * @return "Norway"
   */
  @Override
  public String getCountryName() {
    return "Norway";
  }

  /**
   * Calculates royalty on gross revenue.
   *
   * <p>
   * Norway has no royalty - returns 0.
   * </p>
   *
   * @param grossRevenue total revenue
   * @return 0 (no royalty in Norway)
   */
  @Override
  public double calculateRoyalty(double grossRevenue) {
    return 0.0; // Norway has no royalty
  }

  /**
   * Gets the total marginal tax rate.
   *
   * @return 0.78 (22% + 56%)
   */
  @Override
  public double getTotalMarginalTaxRate() {
    return corporateTaxRate + petroleumTaxRate;
  }

  /**
   * Resets accumulated state.
   */
  @Override
  public void reset() {
    resetLossCarryForward();
  }

  /**
   * Gets the total loss carry-forward.
   *
   * @return sum of corporate and petroleum tax loss carry-forward
   */
  @Override
  public double getLossCarryForward() {
    return corporateTaxLossCarryForward + petroleumTaxLossCarryForward;
  }

  // ============================================================================
  // INNER CLASS - TAX RESULT
  // ============================================================================

  /**
   * Immutable result of a tax calculation.
   *
   * <p>
   * Contains all components of the tax calculation including:
   * </p>
   * <ul>
   * <li>Input values (revenue, opex, depreciation, uplift)</li>
   * <li>Tax bases (corporate and petroleum)</li>
   * <li>Calculated taxes</li>
   * <li>After-tax income</li>
   * </ul>
   */
  public static final class TaxResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double grossRevenue;
    private final double opex;
    private final double depreciation;
    private final double uplift;
    private final double corporateTaxBase;
    private final double corporateTax;
    private final double petroleumTaxBase;
    private final double petroleumTax;
    private final double totalTax;
    private final double afterTaxIncome;

    /**
     * Creates a new tax result.
     *
     * @param grossRevenue gross revenue
     * @param opex operating expenditure
     * @param depreciation depreciation deduction
     * @param uplift uplift deduction
     * @param corporateTaxBase corporate tax base after deductions
     * @param corporateTax corporate tax amount
     * @param petroleumTaxBase petroleum tax base after deductions
     * @param petroleumTax petroleum tax amount
     * @param totalTax total tax (corporate + petroleum)
     * @param afterTaxIncome after-tax income
     */
    public TaxResult(double grossRevenue, double opex, double depreciation, double uplift,
        double corporateTaxBase, double corporateTax, double petroleumTaxBase, double petroleumTax,
        double totalTax, double afterTaxIncome) {
      this.grossRevenue = grossRevenue;
      this.opex = opex;
      this.depreciation = depreciation;
      this.uplift = uplift;
      this.corporateTaxBase = corporateTaxBase;
      this.corporateTax = corporateTax;
      this.petroleumTaxBase = petroleumTaxBase;
      this.petroleumTax = petroleumTax;
      this.totalTax = totalTax;
      this.afterTaxIncome = afterTaxIncome;
    }

    /**
     * Gets the gross revenue.
     *
     * @return gross revenue
     */
    public double getGrossRevenue() {
      return grossRevenue;
    }

    /**
     * Gets the operating expenditure.
     *
     * @return opex
     */
    public double getOpex() {
      return opex;
    }

    /**
     * Gets the depreciation deduction.
     *
     * @return depreciation
     */
    public double getDepreciation() {
      return depreciation;
    }

    /**
     * Gets the uplift deduction.
     *
     * @return uplift
     */
    public double getUplift() {
      return uplift;
    }

    /**
     * Gets the corporate tax base.
     *
     * @return corporate tax base
     */
    public double getCorporateTaxBase() {
      return corporateTaxBase;
    }

    /**
     * Gets the corporate tax amount.
     *
     * @return corporate tax
     */
    public double getCorporateTax() {
      return corporateTax;
    }

    /**
     * Gets the petroleum tax base.
     *
     * @return petroleum tax base
     */
    public double getPetroleumTaxBase() {
      return petroleumTaxBase;
    }

    /**
     * Gets the petroleum tax amount.
     *
     * @return petroleum tax
     */
    public double getPetroleumTax() {
      return petroleumTax;
    }

    /**
     * Gets the total tax (corporate + petroleum).
     *
     * @return total tax
     */
    public double getTotalTax() {
      return totalTax;
    }

    /**
     * Gets the after-tax income.
     *
     * @return after-tax income
     */
    public double getAfterTaxIncome() {
      return afterTaxIncome;
    }

    /**
     * Gets the effective tax rate (total tax / gross revenue).
     *
     * @return effective tax rate (0-1)
     */
    public double getEffectiveTaxRate() {
      if (grossRevenue <= 0) {
        return 0.0;
      }
      return totalTax / grossRevenue;
    }

    @Override
    public String toString() {
      return String.format(
          "TaxResult[revenue=%.2f, opex=%.2f, depreciation=%.2f, uplift=%.2f, "
              + "corporateTax=%.2f, petroleumTax=%.2f, totalTax=%.2f, afterTax=%.2f]",
          grossRevenue, opex, depreciation, uplift, corporateTax, petroleumTax, totalTax,
          afterTaxIncome);
    }
  }
}
