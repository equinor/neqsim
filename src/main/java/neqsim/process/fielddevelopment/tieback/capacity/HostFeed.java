package neqsim.process.fielddevelopment.tieback.capacity;

import java.io.Serializable;

/**
 * Molar feed of a host process stream: a composition on the stream's components and a molar rate.
 *
 * <p>
 * A {@link HostFeedProvider} returns this object for each trial production load, so the host process model is solved
 * with the composition that the load implies instead of a fixed composition scaled in rate.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class HostFeed implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Mole fractions on the components of the host stream fluid, normalised to a sum of one. */
  private final double[] moleFractions;

  /** Molar flow rate in mol/s. */
  private final double molarRateMolPerSec;

  /**
   * Creates a molar feed.
   *
   * @param moleFractions mole fractions in the component order of the host stream fluid; normalised internally
   * @param molarRateMolPerSec molar flow rate in mol/s, zero or positive
   * @throws IllegalArgumentException if the composition is empty, has a negative or non-finite value or a zero sum, or
   * the rate is negative or non-finite
   */
  public HostFeed(double[] moleFractions, double molarRateMolPerSec) {
    if (moleFractions == null || moleFractions.length == 0) {
      throw new IllegalArgumentException("moleFractions must not be empty");
    }
    if (Double.isNaN(molarRateMolPerSec) || Double.isInfinite(molarRateMolPerSec) || molarRateMolPerSec < 0.0) {
      throw new IllegalArgumentException("molarRateMolPerSec must be finite and not negative");
    }
    double sum = 0.0;
    for (double value : moleFractions) {
      if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0) {
        throw new IllegalArgumentException("mole fractions must be finite and not negative");
      }
      sum += value;
    }
    if (sum <= 0.0) {
      throw new IllegalArgumentException("mole fractions must have a positive sum");
    }
    this.moleFractions = new double[moleFractions.length];
    for (int i = 0; i < moleFractions.length; i++) {
      this.moleFractions[i] = moleFractions[i] / sum;
    }
    this.molarRateMolPerSec = molarRateMolPerSec;
  }

  /**
   * Gets the normalised mole fractions.
   *
   * @return copy of the mole fractions in the host stream component order
   */
  public double[] getMoleFractions() {
    return moleFractions.clone();
  }

  /**
   * Gets the number of components.
   *
   * @return length of the composition
   */
  public int getNumberOfComponents() {
    return moleFractions.length;
  }

  /**
   * Gets the molar rate.
   *
   * @return molar flow rate in mol/s
   */
  public double getMolarRateMolPerSec() {
    return molarRateMolPerSec;
  }
}
