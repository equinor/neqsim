package neqsim.process.fielddevelopment.economics;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Screening economics for a bid on a Production Sharing Contract (PSC) exploration block.
 *
 * <p>
 * The class evaluates the value of a bid before any discovery exists. The pre-discovery costs (signature bonus and
 * minimum exploration programme) are paid in every outcome, while the development cash flow only occurs on discovery.
 * The bid variable is the offered share of profit oil to the State, so the class can also solve the break-even offered
 * share at which the expected monetary value (EMV) of the bid is zero.
 * </p>
 *
 * <h2>Mechanics (per development year)</h2>
 * <ol>
 * <li>Royalty is a fraction of gross revenue.</li>
 * <li>Cost oil is the lower of the cost-oil cap times net revenue (after royalty) and the unrecovered cost pool (CAPEX,
 * OPEX and the recoverable exploration programme).</li>
 * <li>Profit oil is net revenue minus cost oil, split between the State and the contractor. The State share is the
 * offered share plus an optional price-linked step above a reference price.</li>
 * <li>Contractor income tax is applied to contractor revenue minus OPEX minus straight-line depreciation of CAPEX, with
 * loss carry-forward limited to a fraction of taxable income.</li>
 * </ol>
 *
 * <p>
 * The signature bonus is not cost recoverable. All parameters are inputs; the defaults are generic public values (15%
 * royalty, 50% cost-oil cap, 34% income tax) and must be replaced by the terms of the actual bid round contract. This
 * is a screening model, not a contract-compliant fiscal calculation.
 * </p>
 *
 * <pre>{@code
 * PscBidEconomics bid = new PscBidEconomics().setOilPrice(70.0).setDiscountRate(0.10).setProfitOilShareOffered(0.30)
 *     .setWorkingInterest(0.70).setSignatureBonus(20.0).setExplorationProgramCost(60.0).setDiscoveryDelayYears(4)
 *     .setDevelopmentProfile(production, capex, opex).setChanceOfDiscovery(0.25);
 * PscBidEconomics.Result r = bid.evaluate();
 * double breakEven = r.getBreakEvenProfitOilShare();
 * }</pre>
 *
 * <p>
 * Units: volumes in million barrels per year, money in million USD, prices in USD per barrel.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class PscBidEconomics implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Maximum State profit-oil share allowed in the solver. */
  private static final double MAX_SHARE = 0.99;

  private double oilPrice = 70.0;
  private double discountRate = 0.10;
  private double royaltyRate = 0.15;
  private double costOilCap = 0.50;
  private double profitOilShareOffered = 0.20;
  private double sharePerUsdAboveReference = 0.0;
  private double referencePrice = 40.0;
  private double taxRate = 0.34;
  private int depreciationYears = 5;
  private double lossOffsetCap = 0.30;
  private double workingInterest = 1.0;
  private double signatureBonus = 0.0;
  private double explorationProgramCost = 0.0;
  private int discoveryDelayYears = 4;
  private double chanceOfDiscovery = 1.0;
  private double[] production = new double[0];
  private double[] capex = new double[0];
  private double[] opex = new double[0];
  private double[] volumeScales = new double[] {1.0};
  private double[] volumeWeights = new double[] {1.0};

  /**
   * Sets the oil price.
   *
   * @param price oil price in USD per barrel
   * @return this object
   */
  public PscBidEconomics setOilPrice(double price) {
    this.oilPrice = price;
    return this;
  }

  /**
   * Sets the discount rate.
   *
   * @param rate discount rate as a fraction per year
   * @return this object
   */
  public PscBidEconomics setDiscountRate(double rate) {
    this.discountRate = rate;
    return this;
  }

  /**
   * Sets the royalty rate on gross revenue.
   *
   * @param rate royalty fraction
   * @return this object
   */
  public PscBidEconomics setRoyaltyRate(double rate) {
    this.royaltyRate = rate;
    return this;
  }

  /**
   * Sets the cost-oil cap.
   *
   * @param cap maximum fraction of net revenue (after royalty) that can be taken as cost oil
   * @return this object
   */
  public PscBidEconomics setCostOilCap(double cap) {
    this.costOilCap = cap;
    return this;
  }

  /**
   * Sets the offered State share of profit oil, which is the bid variable.
   *
   * @param share State share of profit oil as a fraction
   * @return this object
   */
  public PscBidEconomics setProfitOilShareOffered(double share) {
    this.profitOilShareOffered = share;
    return this;
  }

  /**
   * Sets an optional price-linked increase of the State profit-oil share.
   *
   * @param sharePerUsd added State share per USD per barrel above the reference price
   * @param referencePriceUsd reference price in USD per barrel above which the step applies
   * @return this object
   */
  public PscBidEconomics setPriceLinkedShare(double sharePerUsd, double referencePriceUsd) {
    this.sharePerUsdAboveReference = sharePerUsd;
    this.referencePrice = referencePriceUsd;
    return this;
  }

  /**
   * Sets contractor income tax parameters.
   *
   * @param rate income tax rate as a fraction
   * @param years straight-line depreciation period for CAPEX in years
   * @param lossCap maximum fraction of taxable income that carried-forward losses may offset
   * @return this object
   */
  public PscBidEconomics setTax(double rate, int years, double lossCap) {
    this.taxRate = rate;
    this.depreciationYears = Math.max(1, years);
    this.lossOffsetCap = lossCap;
    return this;
  }

  /**
   * Sets the working interest of the evaluating company.
   *
   * @param interest working interest as a fraction between 0 and 1
   * @return this object
   */
  public PscBidEconomics setWorkingInterest(double interest) {
    this.workingInterest = interest;
    return this;
  }

  /**
   * Sets the signature bonus, which is paid at bid date and is not cost recoverable.
   *
   * @param bonus signature bonus in million USD at 100%
   * @return this object
   */
  public PscBidEconomics setSignatureBonus(double bonus) {
    this.signatureBonus = bonus;
    return this;
  }

  /**
   * Sets the minimum exploration programme cost, which is paid before discovery and is cost recoverable on discovery.
   *
   * @param cost exploration programme cost in million USD at 100%
   * @return this object
   */
  public PscBidEconomics setExplorationProgramCost(double cost) {
    this.explorationProgramCost = cost;
    return this;
  }

  /**
   * Sets the years from bid date until the development profile starts.
   *
   * @param years delay in whole years, at least 1
   * @return this object
   */
  public PscBidEconomics setDiscoveryDelayYears(int years) {
    this.discoveryDelayYears = Math.max(1, years);
    return this;
  }

  /**
   * Sets the probability that the block contains a developable discovery.
   *
   * @param probability chance of discovery between 0 and 1
   * @return this object
   */
  public PscBidEconomics setChanceOfDiscovery(double probability) {
    this.chanceOfDiscovery = probability;
    return this;
  }

  /**
   * Sets the development profile on a yearly basis starting the year after discovery.
   *
   * @param productionMmbbl oil production in million barrels per year
   * @param capexMusd development CAPEX in million USD per year at 100%
   * @param opexMusd OPEX in million USD per year at 100%
   * @return this object
   * @throws IllegalArgumentException if the arrays differ in length
   */
  public PscBidEconomics setDevelopmentProfile(double[] productionMmbbl, double[] capexMusd, double[] opexMusd) {
    if (productionMmbbl.length != capexMusd.length || productionMmbbl.length != opexMusd.length) {
      throw new IllegalArgumentException("production, capex and opex must have the same length");
    }
    this.production = Arrays.copyOf(productionMmbbl, productionMmbbl.length);
    this.capex = Arrays.copyOf(capexMusd, capexMusd.length);
    this.opex = Arrays.copyOf(opexMusd, opexMusd.length);
    return this;
  }

  /**
   * Sets discrete volume outcomes given a discovery, for example P90, P50 and P10 with Swanson weights.
   *
   * @param scales multipliers applied to the production profile for each case
   * @param weights probability weights of the cases, normalised internally
   * @return this object
   * @throws IllegalArgumentException if lengths differ or the weights sum to zero
   */
  public PscBidEconomics setVolumeCases(double[] scales, double[] weights) {
    if (scales.length != weights.length || scales.length == 0) {
      throw new IllegalArgumentException("scales and weights must have the same non-zero length");
    }
    double sum = 0.0;
    for (double w : weights) {
      sum += w;
    }
    if (sum <= 0.0) {
      throw new IllegalArgumentException("weights must sum to a positive number");
    }
    this.volumeScales = Arrays.copyOf(scales, scales.length);
    this.volumeWeights = new double[weights.length];
    for (int i = 0; i < weights.length; i++) {
      this.volumeWeights[i] = weights[i] / sum;
    }
    return this;
  }

  /**
   * Evaluates the bid at the configured offered profit-oil share.
   *
   * @return result with development value, EMV, break-even share and government take
   */
  public Result evaluate() {
    Result r = evaluateAtShare(profitOilShareOffered);
    r.breakEvenProfitOilShare = solveBreakEvenShare();
    return r;
  }

  /**
   * Evaluates EMV for a given offered share without solving the break-even.
   *
   * @param share offered State share of profit oil
   * @return EMV in million USD net to the working interest
   */
  public double emvAtShare(double share) {
    return evaluateAtShare(share).emv;
  }

  /**
   * Solves the offered profit-oil share at which EMV is zero.
   *
   * @return break-even share, 0 if EMV is negative even at zero share, or the solver maximum if EMV stays positive
   */
  private double solveBreakEvenShare() {
    if (emvAtShare(0.0) <= 0.0) {
      return 0.0;
    }
    if (emvAtShare(MAX_SHARE) >= 0.0) {
      return MAX_SHARE;
    }
    double lo = 0.0;
    double hi = MAX_SHARE;
    for (int i = 0; i < 60; i++) {
      double mid = 0.5 * (lo + hi);
      if (emvAtShare(mid) > 0.0) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    return 0.5 * (lo + hi);
  }

  /**
   * Evaluates all value metrics at a given offered share.
   *
   * @param share offered State share of profit oil
   * @return populated result without break-even share
   */
  private Result evaluateAtShare(double share) {
    Result res = new Result();
    res.profitOilShareOffered = share;
    double pvDevExpected = 0.0;
    double govPvDev = 0.0;
    for (int k = 0; k < volumeScales.length; k++) {
      double[] cf = developmentCase(share, volumeScales[k], res, k == 0);
      pvDevExpected += volumeWeights[k] * cf[0];
      govPvDev += volumeWeights[k] * cf[1];
    }
    double pvPem = 0.0;
    for (int y = 1; y <= discoveryDelayYears; y++) {
      pvPem += (explorationProgramCost / discoveryDelayYears) / Math.pow(1.0 + discountRate, y);
    }
    res.pvDevelopmentGivenDiscovery = workingInterest * pvDevExpected;
    res.pvPreDiscoveryCost = -workingInterest * (signatureBonus + pvPem);
    res.emv = res.pvPreDiscoveryCost + chanceOfDiscovery * res.pvDevelopmentGivenDiscovery;
    res.pvStateTakeGivenDiscovery = workingInterest * govPvDev;
    res.chanceOfDiscovery = chanceOfDiscovery;
    return res;
  }

  /**
   * Runs the yearly PSC waterfall for one volume case.
   *
   * @param share offered State share of profit oil
   * @param scale production multiplier for the case
   * @param res result object to receive undiscounted totals when {@code record} is true
   * @param record true to store undiscounted totals in {@code res}
   * @return two-element array with PV of contractor cash flow at bid date and PV of State take at bid date, both at
   * 100%
   */
  private double[] developmentCase(double share, double scale, Result res, boolean record) {
    int n = production.length;
    double pool = explorationProgramCost;
    double lossPool = 0.0;
    double pvContractor = 0.0;
    double pvState = 0.0;
    double contractorSum = 0.0;
    double stateSum = 0.0;
    double netPreTaxSum = 0.0;
    double stateShare = Math.min(MAX_SHARE,
        share + sharePerUsdAboveReference * Math.max(0.0, oilPrice - referencePrice));
    for (int t = 0; t < n; t++) {
      double revenue = production[t] * scale * oilPrice;
      double royalty = royaltyRate * revenue;
      double net = revenue - royalty;
      pool += capex[t] + opex[t];
      double costOil = Math.min(costOilCap * net, pool);
      pool -= costOil;
      double profitOil = net - costOil;
      double stateProfitOil = stateShare * profitOil;
      double contractorRevenue = costOil + (profitOil - stateProfitOil);
      double depreciation = 0.0;
      for (int k = 0; k <= t; k++) {
        if (t - k < depreciationYears) {
          depreciation += capex[k] / depreciationYears;
        }
      }
      double taxable = contractorRevenue - opex[t] - depreciation;
      double tax = 0.0;
      if (taxable < 0.0) {
        lossPool += -taxable;
      } else {
        double offset = Math.min(lossPool, lossOffsetCap * taxable);
        lossPool -= offset;
        tax = taxRate * (taxable - offset);
      }
      double cash = contractorRevenue - capex[t] - opex[t] - tax;
      double state = royalty + stateProfitOil + tax;
      double df = Math.pow(1.0 + discountRate, -(discoveryDelayYears + t + 1));
      pvContractor += cash * df;
      pvState += state * df;
      contractorSum += cash;
      stateSum += state;
      netPreTaxSum += revenue - capex[t] - opex[t];
    }
    if (record) {
      res.contractorCashUndiscounted = workingInterest * contractorSum;
      res.stateTakeUndiscounted = workingInterest * stateSum;
      res.governmentTake = netPreTaxSum > 0.0 ? stateSum / netPreTaxSum : Double.NaN;
    }
    return new double[] {pvContractor, pvState};
  }

  /**
   * Result of a PSC bid evaluation. Money is in million USD net to the working interest unless stated otherwise.
   *
   * @author ESOL
   * @version 1.0
   */
  public static class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private double profitOilShareOffered;
    private double chanceOfDiscovery;
    private double pvDevelopmentGivenDiscovery;
    private double pvPreDiscoveryCost;
    private double emv;
    private double pvStateTakeGivenDiscovery;
    private double contractorCashUndiscounted;
    private double stateTakeUndiscounted;
    private double governmentTake;
    private double breakEvenProfitOilShare;

    /**
     * Gets the offered State profit-oil share used.
     *
     * @return State share of profit oil as a fraction
     */
    public double getProfitOilShareOffered() {
      return profitOilShareOffered;
    }

    /**
     * Gets the chance of discovery used.
     *
     * @return probability between 0 and 1
     */
    public double getChanceOfDiscovery() {
      return chanceOfDiscovery;
    }

    /**
     * Gets the expected PV of development cash flow given a discovery, at bid date.
     *
     * @return value in million USD net to the working interest
     */
    public double getPvDevelopmentGivenDiscovery() {
      return pvDevelopmentGivenDiscovery;
    }

    /**
     * Gets the PV of the costs paid before discovery (signature bonus and exploration programme).
     *
     * @return negative value in million USD net to the working interest
     */
    public double getPvPreDiscoveryCost() {
      return pvPreDiscoveryCost;
    }

    /**
     * Gets the expected monetary value of the bid.
     *
     * @return EMV in million USD net to the working interest
     */
    public double getEmv() {
      return emv;
    }

    /**
     * Gets the PV of State take given a discovery, at bid date.
     *
     * @return value in million USD net to the working interest
     */
    public double getPvStateTakeGivenDiscovery() {
      return pvStateTakeGivenDiscovery;
    }

    /**
     * Gets the undiscounted contractor cash flow given a discovery for the base volume case.
     *
     * @return value in million USD net to the working interest
     */
    public double getContractorCashUndiscounted() {
      return contractorCashUndiscounted;
    }

    /**
     * Gets the undiscounted State take given a discovery for the base volume case.
     *
     * @return value in million USD net to the working interest
     */
    public double getStateTakeUndiscounted() {
      return stateTakeUndiscounted;
    }

    /**
     * Gets the government take as a fraction of undiscounted pre-tax net cash flow for the base volume case.
     *
     * @return fraction, or NaN when pre-tax net cash flow is not positive
     */
    public double getGovernmentTake() {
      return governmentTake;
    }

    /**
     * Gets the offered profit-oil share at which EMV is zero.
     *
     * @return break-even State share; 0 if EMV is negative even at zero share
     */
    public double getBreakEvenProfitOilShare() {
      return breakEvenProfitOilShare;
    }

    /**
     * Tests whether the bid has positive expected value at the offered share.
     *
     * @return true if EMV is positive
     */
    public boolean isValueCreating() {
      return emv > 0.0;
    }

    /**
     * Gets the headroom between the break-even share and the offered share.
     *
     * @return break-even minus offered share; positive means room to raise the offer
     */
    public double getShareHeadroom() {
      return breakEvenProfitOilShare - profitOilShareOffered;
    }
  }
}
