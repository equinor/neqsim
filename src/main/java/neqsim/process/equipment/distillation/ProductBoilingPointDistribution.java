package neqsim.process.equipment.distillation;

import java.util.Arrays;
import java.util.Objects;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Immutable discrete normal-boiling-point distribution for a material process product.
 *
 * <p>
 * The distribution uses positive pseudo-component mole fractions from a NeqSim stream. It is a
 * model diagnostic, not an ASTM D86, ASTM D1160, TBP, or continuous simulated-distillation curve.
 * </p>
 */
public final class ProductBoilingPointDistribution {
  private final double[] boilingPointTemperaturesKelvin;
  private final double[] cumulativeMoleFractions;
  private final double meanNormalBoilingPointKelvin;

  private ProductBoilingPointDistribution(double[] boilingPointTemperaturesKelvin,
      double[] cumulativeMoleFractions, double meanNormalBoilingPointKelvin) {
    this.boilingPointTemperaturesKelvin = boilingPointTemperaturesKelvin.clone();
    this.cumulativeMoleFractions = cumulativeMoleFractions.clone();
    this.meanNormalBoilingPointKelvin = meanNormalBoilingPointKelvin;
  }

  /**
   * Build a discrete product distribution from stream pseudo-components.
   *
   * @param stream material product stream
   * @return immutable distribution on cumulative product mole basis
   * @throws NullPointerException if {@code stream} is {@code null}
   * @throws IllegalStateException if composition and boiling-point data are missing, unaligned, or
   *         non-physical
   */
  public static ProductBoilingPointDistribution from(StreamInterface stream) {
    Objects.requireNonNull(stream, "stream");
    double[] composition = stream.getThermoSystem().getMolarComposition();
    double[] boilingPoints = stream.getThermoSystem().getNormalBoilingPointTemperatures();
    if (composition.length != boilingPoints.length || composition.length == 0) {
      throw new IllegalStateException("Product composition and boiling-point arrays must align");
    }

    double[][] points = new double[composition.length][2];
    double compositionSum = 0.0;
    double weightedBoilingPoint = 0.0;
    int positiveComponentCount = 0;
    for (int i = 0; i < composition.length; i++) {
      if (!Double.isFinite(composition[i]) || composition[i] < 0.0
          || !Double.isFinite(boilingPoints[i]) || !(boilingPoints[i] > 0.0)) {
        throw new IllegalStateException(
            "Product composition and boiling points must be physical");
      }
      points[i][0] = boilingPoints[i];
      points[i][1] = composition[i];
      compositionSum += composition[i];
      weightedBoilingPoint += composition[i] * boilingPoints[i];
      if (composition[i] > 0.0) {
        positiveComponentCount++;
      }
    }
    if (!Double.isFinite(compositionSum) || !(compositionSum > 0.0)
        || !Double.isFinite(weightedBoilingPoint) || positiveComponentCount == 0) {
      throw new IllegalStateException("Product boiling-point distribution is undefined");
    }

    Arrays.sort(points, (left, right) -> Double.compare(left[0], right[0]));
    double[] temperatures = new double[positiveComponentCount];
    double[] cumulativeFractions = new double[positiveComponentCount];
    double cumulativeFraction = 0.0;
    int resultIndex = 0;
    for (double[] point : points) {
      if (point[1] > 0.0) {
        cumulativeFraction += point[1] / compositionSum;
        temperatures[resultIndex] = point[0];
        cumulativeFractions[resultIndex] = cumulativeFraction;
        resultIndex++;
      }
    }
    cumulativeFractions[cumulativeFractions.length - 1] = 1.0;
    return new ProductBoilingPointDistribution(temperatures, cumulativeFractions,
        weightedBoilingPoint / compositionSum);
  }

  /** @return defensive copy of ascending pseudo-component normal boiling points in kelvin */
  public double[] getBoilingPointTemperaturesKelvin() {
    return boilingPointTemperaturesKelvin.clone();
  }

  /** @return defensive copy of normalized cumulative product mole fractions */
  public double[] getCumulativeMoleFractions() {
    return cumulativeMoleFractions.clone();
  }

  /** @return mole-weighted mean normal boiling point in kelvin */
  public double getMeanNormalBoilingPointKelvin() {
    return meanNormalBoilingPointKelvin;
  }

  /** @return mole-weighted mean normal boiling point in degrees Celsius */
  public double getMeanNormalBoilingPointCelsius() {
    return meanNormalBoilingPointKelvin - 273.15;
  }

  /**
   * Return the first support temperature whose cumulative mole fraction reaches the request.
   *
   * @param cumulativeMoleFraction requested cumulative product mole fraction in (0, 1]
   * @return discrete normal boiling point in kelvin
   * @throws IllegalArgumentException if the request is non-finite or outside (0, 1]
   */
  public double getNormalBoilingPointQuantileKelvin(double cumulativeMoleFraction) {
    if (!Double.isFinite(cumulativeMoleFraction) || !(cumulativeMoleFraction > 0.0)
        || cumulativeMoleFraction > 1.0) {
      throw new IllegalArgumentException(
          "Cumulative mole fraction must be finite and in (0, 1]");
    }
    for (int i = 0; i < cumulativeMoleFractions.length; i++) {
      if (cumulativeMoleFractions[i] >= cumulativeMoleFraction) {
        return boilingPointTemperaturesKelvin[i];
      }
    }
    return boilingPointTemperaturesKelvin[boilingPointTemperaturesKelvin.length - 1];
  }

  /**
   * Return the first support temperature whose cumulative mole fraction reaches the request.
   *
   * @param cumulativeMoleFraction requested cumulative product mole fraction in (0, 1]
   * @return discrete normal boiling point in degrees Celsius
   * @throws IllegalArgumentException if the request is non-finite or outside (0, 1]
   */
  public double getNormalBoilingPointQuantileCelsius(double cumulativeMoleFraction) {
    return getNormalBoilingPointQuantileKelvin(cumulativeMoleFraction) - 273.15;
  }
}
