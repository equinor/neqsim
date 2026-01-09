package neqsim.process.fielddevelopment.economics;

import java.io.Serializable;

/**
 * Data class holding all fiscal parameters for a specific country or region.
 *
 * <p>
 * This class encapsulates all the tax and fiscal parameters that vary by jurisdiction, including:
 * </p>
 * <ul>
 * <li>Tax rates (corporate, petroleum/resource, royalty)</li>
 * <li>Depreciation rules</li>
 * <li>Investment incentives (uplift, accelerated depreciation)</li>
 * <li>Loss carry-forward rules</li>
 * <li>Cost recovery limits</li>
 * </ul>
 *
 * <h2>Supported Fiscal System Types</h2>
 * <ul>
 * <li><b>Concessionary</b>: Company owns resources, pays taxes/royalties (e.g., Norway, UK, US)
 * </li>
 * <li><b>Production Sharing Contract (PSC)</b>: State owns resources, company recovers costs and
 * shares profit (e.g., Indonesia, Angola)</li>
 * <li><b>Service Contract</b>: Company provides services for fee (e.g., Iraq, Iran)</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * // Create custom parameters
 * FiscalParameters params = FiscalParameters.builder("CustomCountry").corporateTaxRate(0.25)
 *     .resourceTaxRate(0.40).royaltyRate(0.10).depreciationYears(5).build();
 * 
 * // Or use predefined country
 * FiscalParameters norway = FiscalRegimeRegistry.getParameters("Norway");
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see FiscalRegime
 * @see FiscalRegimeRegistry
 */
public final class FiscalParameters implements Serializable {
  private static final long serialVersionUID = 1000L;

  // ============================================================================
  // IDENTIFICATION
  // ============================================================================

  /** Country or region code (e.g., "NO", "UK", "US-GOM"). */
  private final String countryCode;

  /** Full country or region name. */
  private final String countryName;

  /** Description of the fiscal regime. */
  private final String description;

  /** Year the parameters are valid from. */
  private final int validFromYear;

  // ============================================================================
  // FISCAL SYSTEM TYPE
  // ============================================================================

  /** Type of fiscal system. */
  public enum FiscalSystemType {
    /** Concessionary system - company owns resources. */
    CONCESSIONARY,
    /** Production Sharing Contract - state owns resources. */
    PSC,
    /** Service Contract - fee-based. */
    SERVICE_CONTRACT,
    /** Risk Service Contract - cost recovery plus fee. */
    RISK_SERVICE_CONTRACT
  }

  private final FiscalSystemType fiscalSystemType;

  // ============================================================================
  // TAX RATES
  // ============================================================================

  /** Corporate income tax rate (0-1). */
  private final double corporateTaxRate;

  /** Resource/petroleum/mineral tax rate (0-1). */
  private final double resourceTaxRate;

  /** Royalty rate on gross production (0-1). */
  private final double royaltyRate;

  /** Windfall/excess profit tax rate (0-1). */
  private final double windfallTaxRate;

  /** Windfall tax threshold (price above which windfall tax applies). */
  private final double windfallTaxThreshold;

  /** State participation percentage (0-1). */
  private final double stateParticipation;

  // ============================================================================
  // DEPRECIATION
  // ============================================================================

  /** Depreciation method. */
  public enum DepreciationMethod {
    /** Straight-line depreciation. */
    STRAIGHT_LINE,
    /** Declining balance depreciation. */
    DECLINING_BALANCE,
    /** Unit of production depreciation. */
    UNIT_OF_PRODUCTION,
    /** Immediate expensing. */
    IMMEDIATE
  }

  private final DepreciationMethod depreciationMethod;

  /** Depreciation period in years (for straight-line). */
  private final int depreciationYears;

  /** Declining balance rate (for declining balance method). */
  private final double decliningBalanceRate;

  // ============================================================================
  // INVESTMENT INCENTIVES
  // ============================================================================

  /** Investment uplift rate per year (0-1). */
  private final double upliftRate;

  /** Number of years for uplift. */
  private final int upliftYears;

  /** Investment tax credit rate (0-1). */
  private final double investmentTaxCredit;

  /** Whether R&amp;D expenses are deductible at enhanced rate. */
  private final boolean enhancedRdDeduction;

  /** R&amp;D enhancement factor (e.g., 1.5 for 150% deduction). */
  private final double rdEnhancementFactor;

  // ============================================================================
  // COST RECOVERY (for PSC)
  // ============================================================================

  /** Maximum cost recovery per period as fraction of revenue (0-1). */
  private final double costRecoveryLimit;

  /** Profit oil/gas government share (0-1). */
  private final double profitShareGovernment;

  /** Profit oil/gas contractor share (0-1). */
  private final double profitShareContractor;

  // ============================================================================
  // LOSS HANDLING
  // ============================================================================

  /** Whether losses can be carried forward. */
  private final boolean lossCarryForward;

  /** Maximum years for loss carry-forward (0 = unlimited). */
  private final int lossCarryForwardYears;

  /** Interest rate on carried-forward losses (0-1). */
  private final double lossCarryForwardInterest;

  /** Whether losses can be carried back. */
  private final boolean lossCarryBack;

  /** Maximum years for loss carry-back. */
  private final int lossCarryBackYears;

  // ============================================================================
  // RING-FENCING
  // ============================================================================

  /** Whether ring-fencing applies (losses cannot offset other fields). */
  private final boolean ringFenced;

  /** Ring-fence level (FIELD, LICENSE, COMPANY). */
  public enum RingFenceLevel {
    /** Each field is separate for tax purposes. */
    FIELD,
    /** Each license is separate. */
    LICENSE,
    /** Company-wide consolidation allowed. */
    COMPANY
  }

  private final RingFenceLevel ringFenceLevel;

  // ============================================================================
  // DECOMMISSIONING
  // ============================================================================

  /** Whether decommissioning costs are tax deductible. */
  private final boolean decommissioningDeductible;

  /** Whether decommissioning fund contributions are tax deductible. */
  private final boolean decommissioningFundDeductible;

  // ============================================================================
  // CONSTRUCTOR (via Builder)
  // ============================================================================

  private FiscalParameters(Builder builder) {
    this.countryCode = builder.countryCode;
    this.countryName = builder.countryName;
    this.description = builder.description;
    this.validFromYear = builder.validFromYear;
    this.fiscalSystemType = builder.fiscalSystemType;
    this.corporateTaxRate = builder.corporateTaxRate;
    this.resourceTaxRate = builder.resourceTaxRate;
    this.royaltyRate = builder.royaltyRate;
    this.windfallTaxRate = builder.windfallTaxRate;
    this.windfallTaxThreshold = builder.windfallTaxThreshold;
    this.stateParticipation = builder.stateParticipation;
    this.depreciationMethod = builder.depreciationMethod;
    this.depreciationYears = builder.depreciationYears;
    this.decliningBalanceRate = builder.decliningBalanceRate;
    this.upliftRate = builder.upliftRate;
    this.upliftYears = builder.upliftYears;
    this.investmentTaxCredit = builder.investmentTaxCredit;
    this.enhancedRdDeduction = builder.enhancedRdDeduction;
    this.rdEnhancementFactor = builder.rdEnhancementFactor;
    this.costRecoveryLimit = builder.costRecoveryLimit;
    this.profitShareGovernment = builder.profitShareGovernment;
    this.profitShareContractor = builder.profitShareContractor;
    this.lossCarryForward = builder.lossCarryForward;
    this.lossCarryForwardYears = builder.lossCarryForwardYears;
    this.lossCarryForwardInterest = builder.lossCarryForwardInterest;
    this.lossCarryBack = builder.lossCarryBack;
    this.lossCarryBackYears = builder.lossCarryBackYears;
    this.ringFenced = builder.ringFenced;
    this.ringFenceLevel = builder.ringFenceLevel;
    this.decommissioningDeductible = builder.decommissioningDeductible;
    this.decommissioningFundDeductible = builder.decommissioningFundDeductible;
  }

  // ============================================================================
  // STATIC FACTORY METHODS
  // ============================================================================

  /**
   * Creates a builder for custom fiscal parameters.
   *
   * @param countryCode country or region code
   * @return new builder instance
   */
  public static Builder builder(String countryCode) {
    return new Builder(countryCode);
  }

  // ============================================================================
  // COMPUTED PROPERTIES
  // ============================================================================

  /**
   * Gets the total marginal tax rate.
   *
   * @return sum of corporate and resource tax rates
   */
  public double getTotalMarginalTaxRate() {
    return corporateTaxRate + resourceTaxRate;
  }

  /**
   * Gets the total uplift percentage.
   *
   * @return uplift rate times uplift years
   */
  public double getTotalUpliftPercentage() {
    return upliftRate * upliftYears;
  }

  /**
   * Checks if this is a PSC-type fiscal system.
   *
   * @return true if PSC or similar
   */
  public boolean isPscSystem() {
    return fiscalSystemType == FiscalSystemType.PSC
        || fiscalSystemType == FiscalSystemType.RISK_SERVICE_CONTRACT;
  }

  /**
   * Checks if investment incentives are available.
   *
   * @return true if uplift or investment tax credit available
   */
  public boolean hasInvestmentIncentives() {
    return upliftRate > 0 || investmentTaxCredit > 0;
  }

  // ============================================================================
  // GETTERS
  // ============================================================================

  public String getCountryCode() {
    return countryCode;
  }

  public String getCountryName() {
    return countryName;
  }

  public String getDescription() {
    return description;
  }

  public int getValidFromYear() {
    return validFromYear;
  }

  public FiscalSystemType getFiscalSystemType() {
    return fiscalSystemType;
  }

  public double getCorporateTaxRate() {
    return corporateTaxRate;
  }

  public double getResourceTaxRate() {
    return resourceTaxRate;
  }

  public double getRoyaltyRate() {
    return royaltyRate;
  }

  public double getWindfallTaxRate() {
    return windfallTaxRate;
  }

  public double getWindfallTaxThreshold() {
    return windfallTaxThreshold;
  }

  public double getStateParticipation() {
    return stateParticipation;
  }

  public DepreciationMethod getDepreciationMethod() {
    return depreciationMethod;
  }

  public int getDepreciationYears() {
    return depreciationYears;
  }

  public double getDecliningBalanceRate() {
    return decliningBalanceRate;
  }

  public double getUpliftRate() {
    return upliftRate;
  }

  public int getUpliftYears() {
    return upliftYears;
  }

  public double getInvestmentTaxCredit() {
    return investmentTaxCredit;
  }

  public boolean isEnhancedRdDeduction() {
    return enhancedRdDeduction;
  }

  public double getRdEnhancementFactor() {
    return rdEnhancementFactor;
  }

  public double getCostRecoveryLimit() {
    return costRecoveryLimit;
  }

  public double getProfitShareGovernment() {
    return profitShareGovernment;
  }

  public double getProfitShareContractor() {
    return profitShareContractor;
  }

  public boolean isLossCarryForward() {
    return lossCarryForward;
  }

  public int getLossCarryForwardYears() {
    return lossCarryForwardYears;
  }

  public double getLossCarryForwardInterest() {
    return lossCarryForwardInterest;
  }

  public boolean isLossCarryBack() {
    return lossCarryBack;
  }

  public int getLossCarryBackYears() {
    return lossCarryBackYears;
  }

  public boolean isRingFenced() {
    return ringFenced;
  }

  public RingFenceLevel getRingFenceLevel() {
    return ringFenceLevel;
  }

  public boolean isDecommissioningDeductible() {
    return decommissioningDeductible;
  }

  public boolean isDecommissioningFundDeductible() {
    return decommissioningFundDeductible;
  }

  @Override
  public String toString() {
    return String.format("%s (%s) - Corporate: %.0f%%, Resource: %.0f%%, Total: %.0f%%",
        countryName, countryCode, corporateTaxRate * 100, resourceTaxRate * 100,
        getTotalMarginalTaxRate() * 100);
  }

  // ============================================================================
  // BUILDER
  // ============================================================================

  /**
   * Builder for FiscalParameters.
   */
  public static final class Builder {
    private String countryCode;
    private String countryName;
    private String description = "";
    private int validFromYear = 2024;
    private FiscalSystemType fiscalSystemType = FiscalSystemType.CONCESSIONARY;
    private double corporateTaxRate = 0.25;
    private double resourceTaxRate = 0.0;
    private double royaltyRate = 0.0;
    private double windfallTaxRate = 0.0;
    private double windfallTaxThreshold = 0.0;
    private double stateParticipation = 0.0;
    private DepreciationMethod depreciationMethod = DepreciationMethod.STRAIGHT_LINE;
    private int depreciationYears = 6;
    private double decliningBalanceRate = 0.25;
    private double upliftRate = 0.0;
    private int upliftYears = 0;
    private double investmentTaxCredit = 0.0;
    private boolean enhancedRdDeduction = false;
    private double rdEnhancementFactor = 1.0;
    private double costRecoveryLimit = 1.0;
    private double profitShareGovernment = 0.0;
    private double profitShareContractor = 1.0;
    private boolean lossCarryForward = true;
    private int lossCarryForwardYears = 0; // 0 = unlimited
    private double lossCarryForwardInterest = 0.0;
    private boolean lossCarryBack = false;
    private int lossCarryBackYears = 0;
    private boolean ringFenced = false;
    private RingFenceLevel ringFenceLevel = RingFenceLevel.COMPANY;
    private boolean decommissioningDeductible = true;
    private boolean decommissioningFundDeductible = false;

    private Builder(String countryCode) {
      this.countryCode = countryCode;
      this.countryName = countryCode;
    }

    public Builder countryName(String name) {
      this.countryName = name;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder validFromYear(int year) {
      this.validFromYear = year;
      return this;
    }

    public Builder fiscalSystemType(FiscalSystemType type) {
      this.fiscalSystemType = type;
      return this;
    }

    public Builder corporateTaxRate(double rate) {
      this.corporateTaxRate = rate;
      return this;
    }

    public Builder resourceTaxRate(double rate) {
      this.resourceTaxRate = rate;
      return this;
    }

    public Builder royaltyRate(double rate) {
      this.royaltyRate = rate;
      return this;
    }

    public Builder windfallTax(double rate, double threshold) {
      this.windfallTaxRate = rate;
      this.windfallTaxThreshold = threshold;
      return this;
    }

    public Builder stateParticipation(double participation) {
      this.stateParticipation = participation;
      return this;
    }

    public Builder depreciation(DepreciationMethod method, int years) {
      this.depreciationMethod = method;
      this.depreciationYears = years;
      return this;
    }

    public Builder depreciationYears(int years) {
      this.depreciationYears = years;
      return this;
    }

    public Builder decliningBalanceRate(double rate) {
      this.decliningBalanceRate = rate;
      return this;
    }

    public Builder uplift(double rate, int years) {
      this.upliftRate = rate;
      this.upliftYears = years;
      return this;
    }

    public Builder investmentTaxCredit(double rate) {
      this.investmentTaxCredit = rate;
      return this;
    }

    public Builder enhancedRdDeduction(double factor) {
      this.enhancedRdDeduction = factor > 1.0;
      this.rdEnhancementFactor = factor;
      return this;
    }

    public Builder costRecoveryLimit(double limit) {
      this.costRecoveryLimit = limit;
      return this;
    }

    public Builder profitSharing(double governmentShare, double contractorShare) {
      this.profitShareGovernment = governmentShare;
      this.profitShareContractor = contractorShare;
      return this;
    }

    public Builder lossCarryForward(int years, double interestRate) {
      this.lossCarryForward = true;
      this.lossCarryForwardYears = years;
      this.lossCarryForwardInterest = interestRate;
      return this;
    }

    public Builder lossCarryBack(int years) {
      this.lossCarryBack = years > 0;
      this.lossCarryBackYears = years;
      return this;
    }

    public Builder ringFenced(RingFenceLevel level) {
      this.ringFenced = level != RingFenceLevel.COMPANY;
      this.ringFenceLevel = level;
      return this;
    }

    public Builder decommissioning(boolean deductible, boolean fundDeductible) {
      this.decommissioningDeductible = deductible;
      this.decommissioningFundDeductible = fundDeductible;
      return this;
    }

    public FiscalParameters build() {
      return new FiscalParameters(this);
    }
  }
}
