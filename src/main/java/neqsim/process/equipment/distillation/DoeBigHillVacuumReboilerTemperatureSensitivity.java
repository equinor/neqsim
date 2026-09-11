package neqsim.process.equipment.distillation;

import java.util.Objects;
import java.util.UUID;
import neqsim.process.equipment.distillation.DoeBigHillVacuumFractionationCase.OperatingInputs;
import neqsim.process.equipment.distillation.DoeBigHillVacuumFractionationResult.ProductResult;

/**
 * Immutable reboiler-temperature sensitivity summary for the DOE Big Hill vacuum screening case.
 *
 * <p>
 * Reboiler outlet temperature is varied while all other explicit engineering inputs remain fixed.
 * Each point is independently constructed, solved, and evaluated through the qualified Big Hill
 * case and result contracts. The sensitivity is numerical screening evidence, not a measured or
 * calibrated vacuum-column operating envelope.
 * </p>
 */
public final class DoeBigHillVacuumReboilerTemperatureSensitivity {
  private final PointResult[] points;
  private final double minimumOverheadMassFraction;
  private final double maximumOverheadMassFraction;
  private final double maximumMassClosureRelativeError;
  private final double maximumComponentMolarClosureRelativeError;
  private final double maximumColumnEnergyBalanceError;
  private final double maximumMeshResidualNorm;

  private DoeBigHillVacuumReboilerTemperatureSensitivity(PointResult[] points) {
    this.points = points.clone();

    double minimumOverheadFraction = Double.POSITIVE_INFINITY;
    double maximumOverheadFraction = Double.NEGATIVE_INFINITY;
    double maximumMassClosure = 0.0;
    double maximumComponentClosure = 0.0;
    double maximumEnergyError = 0.0;
    double maximumMeshResidual = 0.0;
    for (PointResult point : points) {
      DoeBigHillVacuumFractionationResult result = point.getFractionationResult();
      double overheadFraction = result.getProduct("Overhead").getMassFractionOfFeed();
      minimumOverheadFraction = Math.min(minimumOverheadFraction, overheadFraction);
      maximumOverheadFraction = Math.max(maximumOverheadFraction, overheadFraction);
      maximumMassClosure = Math.max(maximumMassClosure, result.getMassClosureRelativeError());
      maximumComponentClosure = Math.max(maximumComponentClosure,
          result.getMaximumComponentMolarClosureRelativeError());
      maximumEnergyError = Math.max(maximumEnergyError, result.getColumnEnergyBalanceError());
      maximumMeshResidual = Math.max(maximumMeshResidual, result.getMeshResidualNorm());
    }

    minimumOverheadMassFraction = minimumOverheadFraction;
    maximumOverheadMassFraction = maximumOverheadFraction;
    maximumMassClosureRelativeError = maximumMassClosure;
    maximumComponentMolarClosureRelativeError = maximumComponentClosure;
    maximumColumnEnergyBalanceError = maximumEnergyError;
    maximumMeshResidualNorm = maximumMeshResidual;
  }

  /**
   * Run an independent reboiler-temperature sensitivity around explicit baseline operating inputs.
   *
   * @param caseNamePrefix non-blank prefix used for independently constructed point names
   * @param feedMassFlowKgPerHour finite positive feed mass flow in kg/h
   * @param baselineInputs explicit source-unreported baseline column inputs
   * @param reboilerTemperaturesKelvin finite positive strictly increasing temperatures in kelvin
   * @return immutable sensitivity summary
   * @throws NullPointerException if {@code baselineInputs} or
   *         {@code reboilerTemperaturesKelvin} is null
   * @throws IllegalArgumentException if the name, feed flow, or temperatures are invalid
   * @throws IllegalStateException if a point does not solve or pass the qualified result gates
   */
  public static DoeBigHillVacuumReboilerTemperatureSensitivity run(String caseNamePrefix,
      double feedMassFlowKgPerHour, OperatingInputs baselineInputs,
      double[] reboilerTemperaturesKelvin) {
    if (caseNamePrefix == null || caseNamePrefix.trim().isEmpty()) {
      throw new IllegalArgumentException("Case-name prefix must be non-blank");
    }
    if (!Double.isFinite(feedMassFlowKgPerHour) || !(feedMassFlowKgPerHour > 0.0)) {
      throw new IllegalArgumentException("Feed mass flow must be finite and positive");
    }
    Objects.requireNonNull(baselineInputs, "baselineInputs");
    Objects.requireNonNull(reboilerTemperaturesKelvin, "reboilerTemperaturesKelvin");
    if (reboilerTemperaturesKelvin.length < 2) {
      throw new IllegalArgumentException("Reboiler-temperature sensitivity requires at least two points");
    }

    double[] temperatures = reboilerTemperaturesKelvin.clone();
    validateTemperatures(temperatures, baselineInputs.getFeedTemperatureKelvin());
    PointResult[] evaluatedPoints = new PointResult[temperatures.length];
    for (int i = 0; i < temperatures.length; i++) {
      OperatingInputs pointInputs = temperatureInputs(baselineInputs, temperatures[i]);
      DoeBigHillVacuumFractionationCase model = DoeBigHillVacuumFractionationCase.create(
          caseNamePrefix + " reboiler-temperature point " + (i + 1), feedMassFlowKgPerHour,
          pointInputs);
      try {
        model.getColumn().run(UUID.randomUUID());
        DoeBigHillVacuumFractionationResult result =
            DoeBigHillVacuumFractionationResult.evaluate(model);
        evaluatedPoints[i] = new PointResult(temperatures[i], pointInputs, result);
      } catch (RuntimeException exception) {
        throw new IllegalStateException("Vacuum reboiler-temperature sensitivity failed at point "
            + i + " with reboiler temperature " + temperatures[i] + " K", exception);
      }
    }
    return new DoeBigHillVacuumReboilerTemperatureSensitivity(evaluatedPoints);
  }

  /** @return defensive copy of sensitivity points in increasing temperature order */
  public PointResult[] getPoints() {
    return points.clone();
  }

  /**
   * Return one sensitivity point by zero-based index.
   *
   * @param index zero-based point index
   * @return immutable point result
   * @throws IndexOutOfBoundsException if {@code index} is outside the sensitivity
   */
  public PointResult getPoint(int index) {
    if (index < 0 || index >= points.length) {
      throw new IndexOutOfBoundsException("Reboiler-temperature sensitivity point index is outside the result");
    }
    return points[index];
  }

  /** @return smallest overhead mass fraction across the qualified points */
  public double getMinimumOverheadMassFraction() {
    return minimumOverheadMassFraction;
  }

  /** @return largest overhead mass fraction across the qualified points */
  public double getMaximumOverheadMassFraction() {
    return maximumOverheadMassFraction;
  }

  /** @return largest external mass-closure relative error across the qualified points */
  public double getMaximumMassClosureRelativeError() {
    return maximumMassClosureRelativeError;
  }

  /** @return largest component molar-closure relative error across the qualified points */
  public double getMaximumComponentMolarClosureRelativeError() {
    return maximumComponentMolarClosureRelativeError;
  }

  /** @return largest column energy-balance error across the qualified points */
  public double getMaximumColumnEnergyBalanceError() {
    return maximumColumnEnergyBalanceError;
  }

  /** @return largest final MESH residual norm across the qualified points */
  public double getMaximumMeshResidualNorm() {
    return maximumMeshResidualNorm;
  }

  private static void validateTemperatures(double[] temperatures, double feedTemperatureKelvin) {
    double previous = Double.NEGATIVE_INFINITY;
    for (double temperature : temperatures) {
      if (!Double.isFinite(temperature) || !(temperature > feedTemperatureKelvin)) {
        throw new IllegalArgumentException(
            "Reboiler temperatures must be finite and greater than the feed temperature");
      }
      if (!(temperature > previous)) {
        throw new IllegalArgumentException(
            "Reboiler temperatures must be strictly increasing and unique");
      }
      previous = temperature;
    }
  }

  private static OperatingInputs temperatureInputs(OperatingInputs baseline,
      double reboilerTemperatureKelvin) {
    return new OperatingInputs(baseline.getSimpleTrayCount(), baseline.getFeedTrayIndex(),
        baseline.getFeedTemperatureKelvin(), baseline.getFeedPressureBara(),
        baseline.getTopPressureBara(), baseline.getBottomPressureBara(),
        reboilerTemperatureKelvin, baseline.getCondenserRefluxRatio());
  }

  /** Immutable result for one reboiler-temperature point. */
  public static final class PointResult {
    private final double reboilerTemperatureKelvin;
    private final OperatingInputs operatingInputs;
    private final DoeBigHillVacuumFractionationResult fractionationResult;

    private PointResult(double reboilerTemperatureKelvin, OperatingInputs operatingInputs,
        DoeBigHillVacuumFractionationResult fractionationResult) {
      this.reboilerTemperatureKelvin = reboilerTemperatureKelvin;
      this.operatingInputs = Objects.requireNonNull(operatingInputs, "operatingInputs");
      this.fractionationResult = Objects.requireNonNull(fractionationResult, "fractionationResult");
    }

    /** @return reboiler outlet temperature applied at this point in kelvin */
    public double getReboilerTemperatureKelvin() {
      return reboilerTemperatureKelvin;
    }

    /** @return immutable operating inputs applied at this point */
    public OperatingInputs getOperatingInputs() {
      return operatingInputs;
    }

    /** @return qualified immutable fractionation result for this point */
    public DoeBigHillVacuumFractionationResult getFractionationResult() {
      return fractionationResult;
    }

    /** @return overhead mass fraction of feed at this point */
    public double getOverheadMassFraction() {
      return fractionationResult.getProduct("Overhead").getMassFractionOfFeed();
    }

    /**
     * Return a discrete overhead normal-boiling-point diagnostic.
     *
     * @param cumulativeMoleFraction cumulative product mole fraction in (0, 1]
     * @return overhead pseudo-component quantile in kelvin
     */
    public double getOverheadBoilingPointQuantileKelvin(double cumulativeMoleFraction) {
      ProductResult overhead = fractionationResult.getProduct("Overhead");
      return overhead.getNormalBoilingPointQuantileKelvin(cumulativeMoleFraction);
    }
  }
}
