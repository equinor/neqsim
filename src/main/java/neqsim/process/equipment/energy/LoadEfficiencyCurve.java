package neqsim.process.equipment.energy;

import java.io.Serializable;
import java.util.Arrays;

/** Immutable piecewise-linear efficiency curve versus fractional useful output load. */
public final class LoadEfficiencyCurve implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final double[] loadFractions;
  private final double[] efficiencies;

  /**
   * Creates a validated curve.
   *
   * @param loadFractions strictly increasing fractions, beginning above zero
   * @param efficiencies efficiencies in (0, 1]
   */
  public LoadEfficiencyCurve(double[] loadFractions, double[] efficiencies) {
    if (loadFractions == null || efficiencies == null || loadFractions.length != efficiencies.length
        || loadFractions.length < 2) {
      throw new IllegalArgumentException("Load and efficiency arrays must have equal length of at least two");
    }
    this.loadFractions = loadFractions.clone();
    this.efficiencies = efficiencies.clone();
    double previous = 0.0;
    for (int index = 0; index < this.loadFractions.length; index++) {
      double load = this.loadFractions[index];
      double efficiency = this.efficiencies[index];
      if (!Double.isFinite(load) || load <= previous) {
        throw new IllegalArgumentException("Load fractions must be positive, finite, and strictly increasing");
      }
      if (!Double.isFinite(efficiency) || efficiency <= 0.0 || efficiency > 1.0) {
        throw new IllegalArgumentException("Curve efficiencies must be in (0, 1]");
      }
      previous = load;
    }
  }

  /** Evaluates with endpoint clamping. */
  public double getEfficiency(double loadFraction) {
    if (!Double.isFinite(loadFraction) || loadFraction < 0.0) {
      throw new IllegalArgumentException("Load fraction must be non-negative and finite");
    }
    if (loadFraction <= loadFractions[0]) {
      return efficiencies[0];
    }
    int last = loadFractions.length - 1;
    if (loadFraction >= loadFractions[last]) {
      return efficiencies[last];
    }
    for (int index = 1; index < loadFractions.length; index++) {
      if (loadFraction <= loadFractions[index]) {
        double fraction = (loadFraction - loadFractions[index - 1]) / (loadFractions[index] - loadFractions[index - 1]);
        return efficiencies[index - 1] + fraction * (efficiencies[index] - efficiencies[index - 1]);
      }
    }
    return efficiencies[last];
  }

  public double[] getLoadFractions() {
    return loadFractions.clone();
  }

  public double[] getEfficiencies() {
    return efficiencies.clone();
  }

  @Override
  public String toString() {
    return "LoadEfficiencyCurve" + Arrays.toString(loadFractions);
  }
}
