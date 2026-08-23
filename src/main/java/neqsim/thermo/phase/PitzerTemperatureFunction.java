package neqsim.thermo.phase;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Immutable PHREEQC-compatible six-coefficient temperature function for a Pitzer parameter.
 *
 * <p>
 * The function is evaluated on the absolute-temperature scale as
 * {@code a0 + a1(1/T - 1/Tr) + a2 ln(T/Tr) + a3(T - Tr) + a4(T^2 - Tr^2)
 * + a5(1/T^2 - 1/Tr^2)}. It follows the public-domain PHREEQC {@code calc_pitz_param} convention and performs no unit
 * or standard-state conversion.
 * </p>
 */
public final class PitzerTemperatureFunction implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** PHREEQC treats temperatures within this distance of the reference as identical. */
  private static final double REFERENCE_TOLERANCE_K = 1.0e-3;

  private final double referenceTemperature;
  private final double[] coefficients;

  /**
   * Creates a six-coefficient temperature function.
   *
   * @param referenceTemperature reference temperature in K
   * @param coefficients exactly six finite coefficients in PHREEQC order
   */
  public PitzerTemperatureFunction(double referenceTemperature, double[] coefficients) {
    if (!Double.isFinite(referenceTemperature) || referenceTemperature <= 0.0) {
      throw new IllegalArgumentException("Pitzer reference temperature must be finite and positive");
    }
    if (coefficients == null || coefficients.length != 6) {
      throw new IllegalArgumentException("Pitzer temperature function requires exactly six coefficients");
    }
    this.coefficients = coefficients.clone();
    for (double coefficient : this.coefficients) {
      if (!Double.isFinite(coefficient)) {
        throw new IllegalArgumentException("Pitzer temperature coefficients must be finite");
      }
    }
    this.referenceTemperature = referenceTemperature;
  }

  /**
   * Creates a temperature function from Kaasa (1998) Appendix F coefficient order.
   *
   * <p>
   * Appendix F equation (F.1) lists coefficients as {@code [a,b,c,d,e,f]} for the constant, {@code (T-Tr)},
   * {@code (T^2-Tr^2)}, {@code (1/T-1/Tr)}, {@code ln(T/Tr)}, and {@code (1/T^2-1/Tr^2)} terms. The internal PHREEQC
   * order is therefore {@code [a,d,e,b,c,f]}. This factory performs only that permutation; it does not copy a source
   * table or convert units, standard states, or parameter families.
   * </p>
   *
   * @param referenceTemperature reference temperature in K
   * @param coefficients exactly six finite coefficients in Kaasa Appendix F order
   * @return immutable temperature function in the internal PHREEQC order
   */
  public static PitzerTemperatureFunction fromKaasa1998(double referenceTemperature, double[] coefficients) {
    if (coefficients == null || coefficients.length != 6) {
      throw new IllegalArgumentException("Kaasa Pitzer temperature function requires exactly six coefficients");
    }
    return new PitzerTemperatureFunction(referenceTemperature, new double[] { coefficients[0], coefficients[3],
        coefficients[4], coefficients[1], coefficients[2], coefficients[5] });
  }

  /**
   * Evaluates the parameter at a temperature.
   *
   * @param temperature temperature in K
   * @return temperature-adjusted parameter in the same units as the coefficients
   */
  public double valueAt(double temperature) {
    if (!Double.isFinite(temperature) || temperature <= 0.0) {
      throw new IllegalArgumentException("Pitzer parameter temperature must be finite and positive");
    }
    if (Math.abs(temperature - referenceTemperature) < REFERENCE_TOLERANCE_K) {
      return coefficients[0];
    }
    double inverseTemperature = 1.0 / temperature;
    double inverseReference = 1.0 / referenceTemperature;
    return coefficients[0] + coefficients[1] * (inverseTemperature - inverseReference)
        + coefficients[2] * Math.log(temperature / referenceTemperature)
        + coefficients[3] * (temperature - referenceTemperature)
        + coefficients[4] * (temperature * temperature - referenceTemperature * referenceTemperature)
        + coefficients[5] * (inverseTemperature * inverseTemperature - inverseReference * inverseReference);
  }

  /**
   * Gets the reference temperature.
   *
   * @return reference temperature in K
   */
  public double getReferenceTemperature() {
    return referenceTemperature;
  }

  /**
   * Gets a defensive copy of the six coefficients.
   *
   * @return coefficients in PHREEQC order
   */
  public double[] getCoefficients() {
    return Arrays.copyOf(coefficients, coefficients.length);
  }
}
