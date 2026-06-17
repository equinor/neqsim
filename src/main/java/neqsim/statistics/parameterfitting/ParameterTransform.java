package neqsim.statistics.parameterfitting;

import java.io.Serializable;

/**
 * Parameter-space transform used by parameter fitting specifications.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public enum ParameterTransform implements Serializable {
  /** Linear physical parameter space. */
  LINEAR,
  /** Natural-log transformed parameter space for positive parameters. */
  LOG,
  /** Base-10-log transformed parameter space for positive parameters. */
  LOG10,
  /** Logistic transformed space for parameters bounded by finite lower and upper limits. */
  LOGISTIC;

  /** Numerical guard for open bounded transforms. */
  private static final double BOUND_EPSILON = 1.0e-12;

  /**
   * Converts a physical parameter value to the optimizer parameter value.
   *
   * @param value physical parameter value
   * @param lowerBound physical lower bound
   * @param upperBound physical upper bound
   * @return transformed optimizer value
   * @throws IllegalArgumentException if the value is outside the valid transform domain
   */
  public double toInternal(double value, double lowerBound, double upperBound) {
    validateFinite("value", value);
    if (this == LINEAR) {
      return value;
    } else if (this == LOG) {
      if (value <= 0.0) {
        throw new IllegalArgumentException("LOG transform requires a positive value");
      }
      return Math.log(value);
    } else if (this == LOG10) {
      if (value <= 0.0) {
        throw new IllegalArgumentException("LOG10 transform requires a positive value");
      }
      return Math.log10(value);
    }
    validateLogisticBounds(lowerBound, upperBound);
    if (value <= lowerBound || value >= upperBound) {
      throw new IllegalArgumentException(
          "LOGISTIC transform requires lowerBound < value < upperBound");
    }
    double scaled = (value - lowerBound) / (upperBound - value);
    return Math.log(scaled);
  }

  /**
   * Converts an optimizer parameter value to the physical parameter value.
   *
   * @param value optimizer parameter value
   * @param lowerBound physical lower bound
   * @param upperBound physical upper bound
   * @return physical parameter value
   * @throws IllegalArgumentException if bounds are invalid for the transform
   */
  public double toExternal(double value, double lowerBound, double upperBound) {
    validateFinite("value", value);
    if (this == LINEAR) {
      return value;
    } else if (this == LOG) {
      return Math.exp(value);
    } else if (this == LOG10) {
      return Math.pow(10.0, value);
    }
    validateLogisticBounds(lowerBound, upperBound);
    double width = upperBound - lowerBound;
    if (value > 40.0) {
      return upperBound - Math.max(BOUND_EPSILON * width, width * Math.exp(-value));
    } else if (value < -40.0) {
      return lowerBound + Math.max(BOUND_EPSILON * width, width * Math.exp(value));
    }
    return lowerBound + width / (1.0 + Math.exp(-value));
  }

  /**
   * Converts physical bounds to optimizer bounds.
   *
   * @param lowerBound physical lower bound
   * @param upperBound physical upper bound
   * @return optimizer bounds as {@code [lower, upper]}, or null when the transform is unbounded
   * @throws IllegalArgumentException if the physical bounds are invalid for the transform
   */
  public double[] toInternalBounds(double lowerBound, double upperBound) {
    if (this == LOGISTIC) {
      validateLogisticBounds(lowerBound, upperBound);
      return null;
    }
    if (this == LINEAR) {
      return new double[] {lowerBound, upperBound};
    }
    if (lowerBound <= 0.0 || upperBound <= 0.0) {
      throw new IllegalArgumentException("Log transforms require positive bounds");
    }
    if (this == LOG) {
      return new double[] {Math.log(lowerBound), Math.log(upperBound)};
    }
    return new double[] {Math.log10(lowerBound), Math.log10(upperBound)};
  }

  /**
   * Returns whether this transform changes the optimizer parameter space.
   *
   * @return true for non-linear or log-space transforms
   */
  public boolean isTransformed() {
    return this != LINEAR;
  }

  /**
   * Validates that a value is finite.
   *
   * @param name value name used in the exception message
   * @param value value to validate
   * @throws IllegalArgumentException if value is NaN or infinite
   */
  private static void validateFinite(String name, double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
  }

  /**
   * Validates finite bounds for logistic transforms.
   *
   * @param lowerBound lower physical bound
   * @param upperBound upper physical bound
   * @throws IllegalArgumentException if bounds are not finite or ordered
   */
  private static void validateLogisticBounds(double lowerBound, double upperBound) {
    validateFinite("lowerBound", lowerBound);
    validateFinite("upperBound", upperBound);
    if (lowerBound >= upperBound) {
      throw new IllegalArgumentException("lowerBound must be smaller than upperBound");
    }
  }
}
