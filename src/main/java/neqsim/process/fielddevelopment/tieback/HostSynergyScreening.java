package neqsim.process.fielddevelopment.tieback;

import java.io.Serializable;

/**
 * Screening of how much of a development's demand a host can take, and the value of a near-field tie-back over a
 * stand-alone concept.
 *
 * <p>
 * The host ullage per year comes from the host owner (capacity minus planned production). This class only compares a
 * demand profile with that ullage and reports the cover, the first binding year and the demand that does not fit. A
 * tie-back is only credited with its synergy value if the demand fits in every year.
 * </p>
 *
 * <p>
 * Demand and ullage must use the same unit and start in the same year.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class HostSynergyScreening {

  private HostSynergyScreening() {
  }

  /**
   * Compares yearly demand with yearly host ullage.
   *
   * @param demand yearly demand on the host
   * @param ullage yearly host ullage in the same unit
   * @return cover summary
   * @throws IllegalArgumentException if the arrays differ in length or are empty
   */
  public static UllageCover ullageCover(double[] demand, double[] ullage) {
    if (demand.length != ullage.length || demand.length == 0) {
      throw new IllegalArgumentException("demand and ullage must have the same non-zero length");
    }
    double minCover = Double.POSITIVE_INFINITY;
    int binding = -1;
    double unmet = 0.0;
    double demandSum = 0.0;
    for (int i = 0; i < demand.length; i++) {
      double cover = demand[i] > 0.0 ? ullage[i] / demand[i] : Double.POSITIVE_INFINITY;
      if (cover < minCover) {
        minCover = cover;
      }
      if (cover < 1.0 && binding < 0) {
        binding = i;
      }
      unmet += Math.max(0.0, demand[i] - ullage[i]);
      demandSum += demand[i];
    }
    UllageCover r = new UllageCover();
    r.minCoverRatio = minCover;
    r.firstBindingYearIndex = binding;
    r.unmetDemandTotal = unmet;
    r.fractionServed = demandSum > 0.0 ? 1.0 - unmet / demandSum : 1.0;
    return r;
  }

  /**
   * Gets the value of the tie-back over the stand-alone concept, credited only if the demand fits the host.
   *
   * @param tiebackEmv EMV of the tie-back concept
   * @param standaloneEmv EMV of the stand-alone concept
   * @param cover ullage cover of the tie-back demand
   * @return synergy value in the unit of the EMV inputs; 0 if the tie-back does not fit the host
   */
  public static double synergyValue(double tiebackEmv, double standaloneEmv, UllageCover cover) {
    if (!cover.fits()) {
      return 0.0;
    }
    return tiebackEmv - standaloneEmv;
  }

  /**
   * Result of comparing demand with host ullage.
   *
   * @author ESOL
   * @version 1.0
   */
  public static class UllageCover implements Serializable {
    private static final long serialVersionUID = 1000L;

    private double minCoverRatio;
    private int firstBindingYearIndex;
    private double unmetDemandTotal;
    private double fractionServed;

    /**
     * Gets the minimum ratio of ullage to demand over the years.
     *
     * @return minimum cover ratio
     */
    public double getMinCoverRatio() {
      return minCoverRatio;
    }

    /**
     * Gets the first year index where demand exceeds ullage.
     *
     * @return zero-based index, or -1 if the demand always fits
     */
    public int getFirstBindingYearIndex() {
      return firstBindingYearIndex;
    }

    /**
     * Gets the total demand that does not fit.
     *
     * @return unmet demand in the unit of the inputs
     */
    public double getUnmetDemandTotal() {
      return unmetDemandTotal;
    }

    /**
     * Gets the fraction of the demand the host can take.
     *
     * @return fraction between 0 and 1
     */
    public double getFractionServed() {
      return fractionServed;
    }

    /**
     * Tests whether the demand fits in every year.
     *
     * @return true if no year is binding
     */
    public boolean fits() {
      return firstBindingYearIndex < 0;
    }
  }
}
