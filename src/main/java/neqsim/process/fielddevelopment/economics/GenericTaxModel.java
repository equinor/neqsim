package neqsim.process.fielddevelopment.economics;

/**
 * Generic tax model implementation driven by FiscalParameters.
 *
 * <p>
 * This class provides a parameter-driven implementation of the {@link TaxModel} interface that can
 * model any concessionary or PSC-type fiscal system based on the provided {@link FiscalParameters}.
 * </p>
 *
 * <h2>Supported Features</h2>
 * <ul>
 * <li>Corporate and resource/petroleum tax</li>
 * <li>Royalties on gross revenue</li>
 * <li>Investment incentives (uplift, tax credits)</li>
 * <li>Multiple depreciation methods</li>
 * <li>Loss carry-forward with optional interest</li>
 * <li>Windfall/excess profit tax</li>
 * <li>PSC-style cost recovery and profit sharing</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * // Using predefined country parameters
 * FiscalParameters params = TaxModelRegistry.getParameters("UK");
 * TaxModel model = new GenericTaxModel(params);
 * 
 * // Calculate tax
 * TaxModel.TaxResult result = model.calculateTax(500.0, 100.0, 80.0, 0.0);
 * System.out.println("Total tax: " + result.getTotalTax());
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see FiscalParameters
 * @see TaxModel
 * @see TaxModelRegistry
 */
public class GenericTaxModel implements TaxModel {
  private static final long serialVersionUID = 1000L;

  // ============================================================================
  // INSTANCE VARIABLES
  // ============================================================================

  private final FiscalParameters parameters;

  // Loss carry-forward state
  private double corporateTaxLossCarryForward;
  private double resourceTaxLossCarryForward;

  // ============================================================================
  // CONSTRUCTORS
  // ============================================================================

  /**
   * Creates a new tax model with the specified parameters.
   *
   * @param parameters fiscal parameters for this model
   */
  public GenericTaxModel(FiscalParameters parameters) {
    if (parameters == null) {
      throw new IllegalArgumentException("FiscalParameters cannot be null");
    }
    this.parameters = parameters;
    this.corporateTaxLossCarryForward = 0.0;
    this.resourceTaxLossCarryForward = 0.0;
  }

  /**
   * Creates a tax model for a country using the registry.
   *
   * @param countryCode country code (e.g., "NO", "UK", "US-GOM")
   * @return tax model for the country
   * @throws IllegalArgumentException if country not found
   */
  public static GenericTaxModel forCountry(String countryCode) {
    FiscalParameters params = TaxModelRegistry.getParameters(countryCode);
    if (params == null) {
      throw new IllegalArgumentException("Unknown country code: " + countryCode);
    }
    return new GenericTaxModel(params);
  }

  // ============================================================================
  // INTERFACE IMPLEMENTATION
  // ============================================================================

  @Override
  public FiscalParameters getParameters() {
    return parameters;
  }

  @Override
  public String getCountryCode() {
    return parameters.getCountryCode();
  }

  @Override
  public String getCountryName() {
    return parameters.getCountryName();
  }

  @Override
  public TaxResult calculateTax(double grossRevenue, double opex, double depreciation,
      double uplift) {
    // Calculate royalty first (on gross revenue)
    double royalty = calculateRoyalty(grossRevenue);
    double revenueAfterRoyalty = grossRevenue - royalty;

    // Corporate tax calculation
    double corporateTaxBase = revenueAfterRoyalty - opex - depreciation;

    // Apply loss carry-forward for corporate tax
    if (parameters.isLossCarryForward()) {
      if (corporateTaxBase > 0 && corporateTaxLossCarryForward > 0) {
        double usedLoss = Math.min(corporateTaxBase, corporateTaxLossCarryForward);
        corporateTaxBase -= usedLoss;
        corporateTaxLossCarryForward -= usedLoss;
      } else if (corporateTaxBase < 0) {
        // Apply interest to carried-forward losses
        double interest = corporateTaxLossCarryForward * parameters.getLossCarryForwardInterest();
        corporateTaxLossCarryForward += Math.abs(corporateTaxBase) + interest;
        corporateTaxBase = 0;
      }
    }

    double corporateTax = Math.max(0, corporateTaxBase) * parameters.getCorporateTaxRate();

    // Resource/petroleum tax calculation (includes uplift deduction)
    double resourceTaxBase = revenueAfterRoyalty - opex - depreciation - uplift;

    // Apply loss carry-forward for resource tax
    if (parameters.isLossCarryForward()) {
      if (resourceTaxBase > 0 && resourceTaxLossCarryForward > 0) {
        double usedLoss = Math.min(resourceTaxBase, resourceTaxLossCarryForward);
        resourceTaxBase -= usedLoss;
        resourceTaxLossCarryForward -= usedLoss;
      } else if (resourceTaxBase < 0) {
        double interest = resourceTaxLossCarryForward * parameters.getLossCarryForwardInterest();
        resourceTaxLossCarryForward += Math.abs(resourceTaxBase) + interest;
        resourceTaxBase = 0;
      }
    }

    double resourceTax = Math.max(0, resourceTaxBase) * parameters.getResourceTaxRate();

    // Calculate windfall tax if applicable
    double windfallTax = 0;
    if (parameters.getWindfallTaxRate() > 0 && parameters.getWindfallTaxThreshold() > 0) {
      double excessProfit = resourceTaxBase - parameters.getWindfallTaxThreshold();
      if (excessProfit > 0) {
        windfallTax = excessProfit * parameters.getWindfallTaxRate();
      }
    }

    double totalTax = corporateTax + resourceTax + windfallTax;
    double afterTaxIncome = grossRevenue - opex - royalty - totalTax;

    return new TaxResult(grossRevenue, opex, depreciation, uplift, royalty, corporateTaxBase,
        corporateTax, resourceTaxBase, resourceTax + windfallTax, totalTax, afterTaxIncome);
  }

  @Override
  public double calculateDepreciation(double capex, int year) {
    if (year < 1) {
      return 0.0;
    }

    switch (parameters.getDepreciationMethod()) {
      case STRAIGHT_LINE:
        if (year > parameters.getDepreciationYears()) {
          return 0.0;
        }
        return capex / parameters.getDepreciationYears();

      case DECLINING_BALANCE:
        double rate = parameters.getDecliningBalanceRate();
        double remainingValue = capex * Math.pow(1 - rate, year - 1);
        return remainingValue * rate;

      case IMMEDIATE:
        return year == 1 ? capex : 0.0;

      case UNIT_OF_PRODUCTION:
        // Would need production profile - use straight-line as fallback
        if (year > parameters.getDepreciationYears()) {
          return 0.0;
        }
        return capex / parameters.getDepreciationYears();

      default:
        return capex / parameters.getDepreciationYears();
    }
  }

  @Override
  public double calculateUplift(double capex, int year) {
    if (year < 1 || year > parameters.getUpliftYears()) {
      return 0.0;
    }
    return capex * parameters.getUpliftRate();
  }

  @Override
  public double calculateRoyalty(double grossRevenue) {
    return grossRevenue * parameters.getRoyaltyRate();
  }

  @Override
  public double calculateEffectiveTaxRate(double grossRevenue, double opex, double depreciation,
      double uplift) {
    if (grossRevenue <= 0) {
      return 0.0;
    }
    TaxResult result = calculateTax(grossRevenue, opex, depreciation, uplift);
    return result.getTotalTax() / grossRevenue;
  }

  @Override
  public double getTotalMarginalTaxRate() {
    return parameters.getTotalMarginalTaxRate();
  }

  @Override
  public void reset() {
    this.corporateTaxLossCarryForward = 0.0;
    this.resourceTaxLossCarryForward = 0.0;
  }

  @Override
  public double getLossCarryForward() {
    return corporateTaxLossCarryForward + resourceTaxLossCarryForward;
  }

  // ============================================================================
  // ADDITIONAL METHODS
  // ============================================================================

  /**
   * Gets the corporate tax loss carry-forward.
   *
   * @return corporate tax loss carry-forward
   */
  public double getCorporateTaxLossCarryForward() {
    return corporateTaxLossCarryForward;
  }

  /**
   * Gets the resource tax loss carry-forward.
   *
   * @return resource tax loss carry-forward
   */
  public double getResourceTaxLossCarryForward() {
    return resourceTaxLossCarryForward;
  }

  /**
   * Sets the corporate tax loss carry-forward.
   *
   * @param lossCarryForward loss carry-forward amount
   */
  public void setCorporateTaxLossCarryForward(double lossCarryForward) {
    this.corporateTaxLossCarryForward = lossCarryForward;
  }

  /**
   * Sets the resource tax loss carry-forward.
   *
   * @param lossCarryForward loss carry-forward amount
   */
  public void setResourceTaxLossCarryForward(double lossCarryForward) {
    this.resourceTaxLossCarryForward = lossCarryForward;
  }

  /**
   * Calculates state/government participation share of revenue.
   *
   * @param netRevenue net revenue after costs
   * @return government participation share
   */
  public double calculateStateParticipation(double netRevenue) {
    return netRevenue * parameters.getStateParticipation();
  }

  /**
   * Calculates PSC-style profit sharing.
   *
   * <p>
   * For PSC systems, after cost recovery, the profit oil/gas is split between the government and
   * contractor according to the profit sharing terms.
   * </p>
   *
   * @param grossRevenue total revenue
   * @param costRecovery recoverable costs
   * @return array with [governmentShare, contractorShare]
   */
  public double[] calculateProfitSharing(double grossRevenue, double costRecovery) {
    if (!parameters.isPscSystem()) {
      return new double[] {0, grossRevenue - costRecovery};
    }

    // Limit cost recovery
    double maxRecovery = grossRevenue * parameters.getCostRecoveryLimit();
    double actualRecovery = Math.min(costRecovery, maxRecovery);

    // Profit oil/gas
    double profitOil = grossRevenue - actualRecovery;

    double governmentShare = profitOil * parameters.getProfitShareGovernment();
    double contractorShare = profitOil * parameters.getProfitShareContractor();

    return new double[] {governmentShare, contractorShare};
  }

  @Override
  public String toString() {
    return String.format("GenericTaxModel[%s]", parameters.toString());
  }
}
