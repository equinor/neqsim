package neqsim.process.fielddevelopment.economics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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

  private double[] priceStepThresholds = new double[0];
  private double[] priceStepShares = new double[0];
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
   * Sets a price-band table that adds to the State profit-oil share, as found in some contract terms.
   *
   * <p>
   * For an oil price, the added share is the entry whose threshold is the highest one not above the price. Below the
   * lowest threshold nothing is added. The values must come from the contract; this class does not supply any.
   * </p>
   *
   * @param thresholds oil price thresholds in USD per barrel, in increasing order
   * @param addedShares added State share of profit oil for each band, as a fraction
   * @return this object
   * @throws IllegalArgumentException if the arrays differ in length or thresholds are not increasing
   */
  public PscBidEconomics setPriceShareSteps(double[] thresholds, double[] addedShares) {
    if (thresholds.length != addedShares.length) {
      throw new IllegalArgumentException("thresholds and addedShares must have the same length");
    }
    for (int i = 1; i < thresholds.length; i++) {
      if (thresholds[i] <= thresholds[i - 1]) {
        throw new IllegalArgumentException("thresholds must be increasing");
      }
    }
    this.priceStepThresholds = Arrays.copyOf(thresholds, thresholds.length);
    this.priceStepShares = Arrays.copyOf(addedShares, addedShares.length);
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
        share + sharePerUsdAboveReference * Math.max(0.0, oilPrice - referencePrice) + priceStepShare());
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
   * Gets the share added by the price-band table at the current oil price.
   *
   * @return added State share, 0 if no band applies
   */
  private double priceStepShare() {
    double added = 0.0;
    for (int i = 0; i < priceStepThresholds.length; i++) {
      if (oilPrice >= priceStepThresholds[i]) {
        added = priceStepShares[i];
      }
    }
    return added;
  }

  /**
   * Creates an independent copy of this object.
   *
   * @return copy with the same inputs
   */
  public PscBidEconomics copy() {
    PscBidEconomics c = new PscBidEconomics();
    c.oilPrice = oilPrice;
    c.discountRate = discountRate;
    c.royaltyRate = royaltyRate;
    c.costOilCap = costOilCap;
    c.profitOilShareOffered = profitOilShareOffered;
    c.sharePerUsdAboveReference = sharePerUsdAboveReference;
    c.referencePrice = referencePrice;
    c.priceStepThresholds = Arrays.copyOf(priceStepThresholds, priceStepThresholds.length);
    c.priceStepShares = Arrays.copyOf(priceStepShares, priceStepShares.length);
    c.taxRate = taxRate;
    c.depreciationYears = depreciationYears;
    c.lossOffsetCap = lossOffsetCap;
    c.workingInterest = workingInterest;
    c.signatureBonus = signatureBonus;
    c.explorationProgramCost = explorationProgramCost;
    c.discoveryDelayYears = discoveryDelayYears;
    c.chanceOfDiscovery = chanceOfDiscovery;
    c.production = Arrays.copyOf(production, production.length);
    c.capex = Arrays.copyOf(capex, capex.length);
    c.opex = Arrays.copyOf(opex, opex.length);
    c.volumeScales = Arrays.copyOf(volumeScales, volumeScales.length);
    c.volumeWeights = Arrays.copyOf(volumeWeights, volumeWeights.length);
    return c;
  }

  /**
   * Runs a Monte Carlo on the development value given a discovery.
   *
   * <p>
   * Oil price, volume and CAPEX are multiplied by independent mean-preserving lognormal factors. The chance of
   * discovery is kept as a probability: each sample gives an EMV equal to the pre-discovery cost plus the chance of
   * discovery times the sampled development value. The probability of loss counts the dry outcome, which loses the
   * pre-discovery cost in every case.
   * </p>
   *
   * @param samples number of samples, at least 10
   * @param seed random seed for repeatable results
   * @param priceSigma lognormal sigma of the oil price multiplier
   * @param volumeSigma lognormal sigma of the volume multiplier
   * @param capexSigma lognormal sigma of the CAPEX multiplier
   * @return distribution summary
   * @throws IllegalArgumentException if fewer than 10 samples are requested
   */
  public Distribution monteCarlo(int samples, long seed, double priceSigma, double volumeSigma, double capexSigma) {
    if (samples < 10) {
      throw new IllegalArgumentException("samples must be at least 10");
    }
    Random rnd = new Random(seed);
    double[] emv = new double[samples];
    int lossGivenDiscovery = 0;
    double sum = 0.0;
    for (int i = 0; i < samples; i++) {
      PscBidEconomics c = copy();
      c.oilPrice = oilPrice * lognormal(rnd, priceSigma);
      double vm = lognormal(rnd, volumeSigma);
      for (int k = 0; k < c.volumeScales.length; k++) {
        c.volumeScales[k] *= vm;
      }
      double cm = lognormal(rnd, capexSigma);
      for (int k = 0; k < c.capex.length; k++) {
        c.capex[k] *= cm;
      }
      Result r = c.evaluateAtShare(profitOilShareOffered);
      emv[i] = r.emv;
      sum += r.emv;
      if (r.pvPreDiscoveryCost + r.pvDevelopmentGivenDiscovery < 0.0) {
        lossGivenDiscovery++;
      }
    }
    Arrays.sort(emv);
    Distribution d = new Distribution();
    d.samples = samples;
    d.emvMean = sum / samples;
    d.emvP90 = percentile(emv, 0.10);
    d.emvP50 = percentile(emv, 0.50);
    d.emvP10 = percentile(emv, 0.90);
    d.probabilityLossGivenDiscovery = (double) lossGivenDiscovery / samples;
    d.probabilityLoss = (1.0 - chanceOfDiscovery) + chanceOfDiscovery * d.probabilityLossGivenDiscovery;
    return d;
  }

  /**
   * Draws a mean-preserving lognormal multiplier.
   *
   * @param rnd random generator
   * @param sigma lognormal sigma
   * @return multiplier with expected value 1
   */
  private static double lognormal(Random rnd, double sigma) {
    return Math.exp(sigma * rnd.nextGaussian() - 0.5 * sigma * sigma);
  }

  /**
   * Gets a percentile of a sorted array by linear interpolation.
   *
   * @param sorted ascending values
   * @param p fraction between 0 and 1
   * @return interpolated value
   */
  private static double percentile(double[] sorted, double p) {
    double pos = p * (sorted.length - 1);
    int lo = (int) Math.floor(pos);
    int hi = (int) Math.ceil(pos);
    return sorted[lo] + (pos - lo) * (sorted[hi] - sorted[lo]);
  }

  /**
   * Runs a one-at-a-time sensitivity of EMV, ordered by swing.
   *
   * <p>
   * Each input is moved down and up by the relative swing and the two EMV values are reported with the lower first. The
   * chance of discovery is capped at 1.
   * </p>
   *
   * @param relativeSwing relative change, for example 0.2 for plus and minus 20 percent
   * @return map from input name to a two-element array {lowEmv, highEmv}, largest swing first
   */
  public Map<String, double[]> tornado(double relativeSwing) {
    final Map<String, double[]> unsorted = new LinkedHashMap<String, double[]>();
    double lo = 1.0 - relativeSwing;
    double hi = 1.0 + relativeSwing;
    unsorted.put("oil price", order(copy().setOilPrice(oilPrice * lo).evaluateAtShare(profitOilShareOffered).emv,
        copy().setOilPrice(oilPrice * hi).evaluateAtShare(profitOilShareOffered).emv));
    unsorted.put("volume", order(scaledVolume(lo), scaledVolume(hi)));
    unsorted.put("capex", order(scaledCapex(lo), scaledCapex(hi)));
    unsorted.put("chance of discovery", order(
        copy().setChanceOfDiscovery(chanceOfDiscovery * lo).evaluateAtShare(profitOilShareOffered).emv,
        copy().setChanceOfDiscovery(Math.min(1.0, chanceOfDiscovery * hi)).evaluateAtShare(profitOilShareOffered).emv));
    unsorted.put("discount rate",
        order(copy().setDiscountRate(discountRate * lo).evaluateAtShare(profitOilShareOffered).emv,
            copy().setDiscountRate(discountRate * hi).evaluateAtShare(profitOilShareOffered).emv));
    List<String> keys = new ArrayList<String>(unsorted.keySet());
    Collections.sort(keys, new Comparator<String>() {
      @Override
      public int compare(String a, String b) {
        double sa = unsorted.get(a)[1] - unsorted.get(a)[0];
        double sb = unsorted.get(b)[1] - unsorted.get(b)[0];
        return Double.compare(sb, sa);
      }
    });
    Map<String, double[]> rows = new LinkedHashMap<String, double[]>();
    for (String k : keys) {
      rows.put(k, unsorted.get(k));
    }
    return rows;
  }

  /**
   * Orders two values ascending.
   *
   * @param a first value
   * @param b second value
   * @return two-element array with the smaller value first
   */
  private static double[] order(double a, double b) {
    return a <= b ? new double[] {a, b} : new double[] {b, a};
  }

  /**
   * Gets the EMV with the volume cases scaled.
   *
   * @param factor multiplier on all volume scales
   * @return EMV in million USD net to the working interest
   */
  private double scaledVolume(double factor) {
    PscBidEconomics c = copy();
    for (int k = 0; k < c.volumeScales.length; k++) {
      c.volumeScales[k] *= factor;
    }
    return c.evaluateAtShare(profitOilShareOffered).emv;
  }

  /**
   * Gets the EMV with the CAPEX scaled.
   *
   * @param factor multiplier on the CAPEX profile
   * @return EMV in million USD net to the working interest
   */
  private double scaledCapex(double factor) {
    PscBidEconomics c = copy();
    for (int k = 0; k < c.capex.length; k++) {
      c.capex[k] *= factor;
    }
    return c.evaluateAtShare(profitOilShareOffered).emv;
  }

  /**
   * Summary of a Monte Carlo run on a PSC bid. Money is in million USD net to the working interest.
   *
   * @author ESOL
   * @version 1.0
   */
  public static class Distribution implements Serializable {
    private static final long serialVersionUID = 1000L;

    private int samples;
    private double emvMean;
    private double emvP90;
    private double emvP50;
    private double emvP10;
    private double probabilityLoss;
    private double probabilityLossGivenDiscovery;

    /**
     * Gets the number of samples.
     *
     * @return sample count
     */
    public int getSamples() {
      return samples;
    }

    /**
     * Gets the mean EMV.
     *
     * @return mean EMV
     */
    public double getEmvMean() {
      return emvMean;
    }

    /**
     * Gets the low EMV, exceeded with 90 percent probability.
     *
     * @return P90 EMV
     */
    public double getEmvP90() {
      return emvP90;
    }

    /**
     * Gets the median EMV.
     *
     * @return P50 EMV
     */
    public double getEmvP50() {
      return emvP50;
    }

    /**
     * Gets the high EMV, exceeded with 10 percent probability.
     *
     * @return P10 EMV
     */
    public double getEmvP10() {
      return emvP10;
    }

    /**
     * Gets the probability that the bid loses money, counting the dry outcome.
     *
     * @return probability between 0 and 1
     */
    public double getProbabilityLoss() {
      return probabilityLoss;
    }

    /**
     * Gets the probability of loss given a discovery.
     *
     * @return probability between 0 and 1
     */
    public double getProbabilityLossGivenDiscovery() {
      return probabilityLossGivenDiscovery;
    }
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
