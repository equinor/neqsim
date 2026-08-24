package neqsim.thermo.phase;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Immutable sparse Pitzer interaction involving at least one neutral solute.
 *
 * <p>
 * The activity and osmotic multiplicities follow the public-domain PHREEQC implementation. Lambda is a pair containing
 * at least one neutral solute (neutral-ion or neutral-neutral), zeta is neutral-cation-anion, mu is
 * neutral-neutral-neutral, and eta is neutral-cation-cation or neutral-anion-anion. Component order is immaterial.
 * </p>
 */
final class PitzerNeutralInteraction implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private final int family;
  private final int[] componentIndexes;
  private final double[] logGammaCoefficients;
  private final double osmoticCoefficient;
  private final PitzerTemperatureFunction temperatureFunction;

  /**
   * Creates an immutable interaction and its PHREEQC multiplicities.
   *
   * @param family parameter-family identifier
   * @param componentIndexes component indexes, already validated by {@link PhasePitzer}
   * @param temperatureFunction parameter temperature function
   */
  PitzerNeutralInteraction(int family, int[] componentIndexes, PitzerTemperatureFunction temperatureFunction) {
    this.family = family;
    this.componentIndexes = componentIndexes.clone();
    Arrays.sort(this.componentIndexes);
    this.temperatureFunction = temperatureFunction;

    if (this.componentIndexes.length == 2) {
      if (this.componentIndexes[0] == this.componentIndexes[1]) {
        // PHREEQC differentiates both slots before accumulating them for the repeated species.
        logGammaCoefficients = new double[] { 1.0, 1.0 };
        osmoticCoefficient = 0.5;
      } else {
        logGammaCoefficients = new double[] { 2.0, 2.0 };
        osmoticCoefficient = 1.0;
      }
    } else if (family == PhasePitzer.NEUTRAL_FAMILY_MU) {
      double multiplicity;
      if (this.componentIndexes[0] == this.componentIndexes[2]) {
        multiplicity = 1.0;
      } else if (this.componentIndexes[0] == this.componentIndexes[1]
          || this.componentIndexes[1] == this.componentIndexes[2]) {
        multiplicity = 3.0;
      } else {
        multiplicity = 6.0;
      }
      logGammaCoefficients = new double[] { multiplicity, multiplicity, multiplicity };
      osmoticCoefficient = multiplicity;
    } else {
      logGammaCoefficients = new double[] { 1.0, 1.0, 1.0 };
      osmoticCoefficient = 1.0;
    }
  }

  /** @return parameter-family identifier */
  int getFamily() {
    return family;
  }

  /**
   * Returns the parameter value at temperature.
   *
   * @param temperature temperature in K
   * @return parameter value
   */
  double valueAt(double temperature) {
    return temperatureFunction.valueAt(temperature);
  }

  /**
   * Calculates this interaction's contribution to one component's natural-log activity coefficient.
   *
   * @param componentIndex target component index
   * @param phase Pitzer phase containing current molalities
   * @param temperature temperature in K
   * @return contribution to ln(gamma)
   */
  double logGammaContribution(int componentIndex, PhasePitzer phase, double temperature) {
    double contribution = 0.0;
    double parameter = valueAt(temperature);
    for (int position = 0; position < componentIndexes.length; position++) {
      if (componentIndexes[position] != componentIndex) {
        continue;
      }
      double molalityProduct = 1.0;
      for (int other = 0; other < componentIndexes.length; other++) {
        if (other != position) {
          molalityProduct *= phase.getComponent(componentIndexes[other]).getMolality(phase);
        }
      }
      contribution += logGammaCoefficients[position] * molalityProduct * parameter;
    }
    return contribution;
  }

  /**
   * Calculates this interaction's contribution to PHREEQC's osmotic sum.
   *
   * @param phase Pitzer phase containing current molalities
   * @param temperature temperature in K
   * @return contribution before the common {@code 2/sum(m)} factor
   */
  double osmoticContribution(PhasePitzer phase, double temperature) {
    double molalityProduct = 1.0;
    for (int componentIndex : componentIndexes) {
      molalityProduct *= phase.getComponent(componentIndex).getMolality(phase);
    }
    return osmoticCoefficient * molalityProduct * valueAt(temperature);
  }
}
