package neqsim.process.fielddevelopment.integrated;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Gas-lift performance curve (GLPC) relating injected lift-gas rate to produced oil rate.
 *
 * <p>
 * A gas-lifted well shows a characteristic response: as lift-gas rate increases the produced oil rate first rises
 * (reduced flowing gradient) then falls (excess friction from the added gas), passing through a single optimum. This
 * class stores that response either as tabulated points or as a smooth parametric form
 * </p>
 *
 * <p>
 * q_oil(q_lift) = q_base + a&radic;q_lift - b&middot;q_lift
 * </p>
 *
 * <p>
 * which has a unique maximum at q_lift* = (a / 2b)&sup2;. The incremental slope dq_oil/dq_lift is used by
 * {@link GasLiftNetworkOptimizer} to allocate a limited lift-gas budget across wells by the equal-slope (Lagrangian)
 * optimality criterion.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see GasLiftNetworkOptimizer
 */
public class GasLiftPerformanceCurve implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final boolean parametric;
  private final double qBase; // Sm3/day oil at zero lift
  private final double coeffA; // sqrt term coefficient
  private final double coeffB; // linear penalty coefficient
  private final double[] liftRates; // Sm3/day (ascending) for tabulated form
  private final double[] oilRates; // Sm3/day for tabulated form
  private final double maxLiftRate; // Sm3/day cap

  /**
   * Creates a parametric gas-lift performance curve.
   *
   * @param baseOilRateSm3PerDay oil rate at zero lift in Sm3/day
   * @param coeffA square-root response coefficient (&gt; 0)
   * @param coeffB linear friction-penalty coefficient (&gt; 0)
   * @param maxLiftRateSm3PerDay maximum lift-gas rate in Sm3/day
   */
  public GasLiftPerformanceCurve(double baseOilRateSm3PerDay, double coeffA, double coeffB,
      double maxLiftRateSm3PerDay) {
    this.parametric = true;
    this.qBase = baseOilRateSm3PerDay;
    this.coeffA = coeffA;
    this.coeffB = coeffB;
    this.liftRates = null;
    this.oilRates = null;
    this.maxLiftRate = maxLiftRateSm3PerDay;
  }

  /**
   * Creates a tabulated gas-lift performance curve.
   *
   * @param liftRatesSm3PerDay lift-gas rates in Sm3/day, strictly ascending; length &ge; 2
   * @param oilRatesSm3PerDay corresponding oil rates in Sm3/day
   */
  public GasLiftPerformanceCurve(double[] liftRatesSm3PerDay, double[] oilRatesSm3PerDay) {
    if (liftRatesSm3PerDay == null || oilRatesSm3PerDay == null || liftRatesSm3PerDay.length != oilRatesSm3PerDay.length
	|| liftRatesSm3PerDay.length < 2) {
      throw new IllegalArgumentException("lift and oil arrays must be equal length, >= 2 points");
    }
    this.parametric = false;
    this.qBase = oilRatesSm3PerDay[0];
    this.coeffA = 0.0;
    this.coeffB = 0.0;
    this.liftRates = Arrays.copyOf(liftRatesSm3PerDay, liftRatesSm3PerDay.length);
    this.oilRates = Arrays.copyOf(oilRatesSm3PerDay, oilRatesSm3PerDay.length);
    this.maxLiftRate = liftRatesSm3PerDay[liftRatesSm3PerDay.length - 1];
  }

  /**
   * Returns the produced oil rate at a given lift-gas rate.
   *
   * @param liftRateSm3PerDay lift-gas rate in Sm3/day
   * @return oil rate in Sm3/day
   */
  public double oilRateAt(double liftRateSm3PerDay) {
    double qLift = Math.max(0.0, Math.min(maxLiftRate, liftRateSm3PerDay));
    if (parametric) {
      return Math.max(0.0, qBase + coeffA * Math.sqrt(qLift) - coeffB * qLift);
    }
    if (qLift <= liftRates[0]) {
      return oilRates[0];
    }
    if (qLift >= liftRates[liftRates.length - 1]) {
      return oilRates[oilRates.length - 1];
    }
    int hi = Arrays.binarySearch(liftRates, qLift);
    if (hi >= 0) {
      return oilRates[hi];
    }
    hi = -hi - 1;
    int lo = hi - 1;
    double frac = (qLift - liftRates[lo]) / (liftRates[hi] - liftRates[lo]);
    return oilRates[lo] + frac * (oilRates[hi] - oilRates[lo]);
  }

  /**
   * Returns the incremental oil-per-lift slope (dq_oil/dq_lift) at a given lift-gas rate.
   *
   * @param liftRateSm3PerDay lift-gas rate in Sm3/day
   * @return incremental slope (dimensionless, Sm3 oil per Sm3 lift)
   */
  public double incrementalSlope(double liftRateSm3PerDay) {
    double qLift = Math.max(1.0e-6, Math.min(maxLiftRate, liftRateSm3PerDay));
    if (parametric) {
      return 0.5 * coeffA / Math.sqrt(qLift) - coeffB;
    }
    double dq = 0.01 * Math.max(1.0, maxLiftRate);
    double up = oilRateAt(qLift + dq);
    double dn = oilRateAt(Math.max(0.0, qLift - dq));
    return (up - dn) / (qLift + dq - Math.max(0.0, qLift - dq));
  }

  /**
   * Returns the lift-gas rate that maximises produced oil (the GLPC peak).
   *
   * @return optimal lift-gas rate in Sm3/day
   */
  public double optimalLiftRate() {
    if (parametric) {
      if (coeffB <= 0.0) {
	return maxLiftRate;
      }
      double qStar = (coeffA / (2.0 * coeffB)) * (coeffA / (2.0 * coeffB));
      return Math.max(0.0, Math.min(maxLiftRate, qStar));
    }
    double best = liftRates[0];
    double bestOil = oilRates[0];
    for (int i = 1; i < liftRates.length; i++) {
      if (oilRates[i] > bestOil) {
	bestOil = oilRates[i];
	best = liftRates[i];
      }
    }
    return best;
  }

  /**
   * Returns the maximum allowed lift-gas rate.
   *
   * @return maximum lift-gas rate in Sm3/day
   */
  public double getMaxLiftRate() {
    return maxLiftRate;
  }

  /**
   * Returns the base oil rate at zero lift.
   *
   * @return base oil rate in Sm3/day
   */
  public double getBaseOilRate() {
    return parametric ? qBase : oilRates[0];
  }
}
