package neqsim.process.equipment.distillation;

import java.util.Objects;
import java.util.UUID;
import neqsim.process.equipment.distillation.DoeBigHillVacuumFractionationCase.OperatingInputs;
import neqsim.process.equipment.distillation.DoeBigHillVacuumFractionationResult.ProductResult;

/**
 * Immutable feed-mass-flow sensitivity summary for the DOE Big Hill vacuum screening case.
 *
 * <p>
 * Feed mass flow is varied while all explicit column operating inputs and the feed composition
 * remain fixed. Each point is independently constructed, solved, and evaluated through the
 * qualified Big Hill case and result contracts. The sensitivity is numerical screening evidence,
 * not a measured or calibrated vacuum-column throughput envelope.
 * </p>
 */
public final class DoeBigHillVacuumFeedMassFlowSensitivity {
  private final PointResult[] points;
  private final double minimumOverheadMassFraction;
  private final double maximumOverheadMassFraction;
  private final double maximumMassClosureRelativeError;
  private final double maximumComponentMolarClosureRelativeError;
  private final double maximumColumnEnergyBalanceError;
  private final double maximumMeshResidualNorm;

  private DoeBigHillVacuumFeedMassFlowSensitivity(PointResult[] points) {
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
   * Run an independent feed-mass-flow sensitivity at explicit baseline operating inputs.
   *
   * @param caseNamePrefix non-blank prefix used for independently constructed point names
   * @param baselineInputs explicit source-unreported baseline column inputs
   * @param feedMassFlowsKgPerHour finite positive strictly increasing feed mass flows in kg/h
   * @return immutable sensitivity summary
   * @throws NullPointerException if {@code baselineInputs} or
   *         {@code feedMassFlowsKgPerHour} is null
   * @throws IllegalArgumentException if the name or feed mass flows are invalid
   * @throws IllegalStateException if a point does not solve or pass the qualified result gates
   */
  public static DoeBigHillVacuumFeedMassFlowSensitivity run(String caseNamePrefix,
      OperatingInputs baselineInputs, double[] feedMassFlowsKgPerHour) {
    if (caseNamePrefix == null || caseNamePrefix.trim().isEmpty()) {
      throw new IllegalArgumentException("Case-name prefix must be non-blank");
    }
    Objects.requireNonNull(baselineInputs, "baselineInputs");
    Objects.requireNonNull(feedMassFlowsKgPerHour, "feedMassFlowsKgPerHour");
    if (feedMassFlowsKgPerHour.length < 2) {
      throw new IllegalArgumentException("Feed-mass-flow sensitivity requires at least two points");
    }

    double[] massFlows = feedMassFlowsKgPerHour.clone();
    validateMassFlows(massFlows);
    PointResult[] evaluatedPoints = new PointResult[massFlows.length];
    for (int i = 0; i < massFlows.length; i++) {
      DoeBigHillVacuumFractionationCase model = DoeBigHillVacuumFractionationCase
          .create(caseNamePrefix + " feed-mass-flow point " + (i + 1), massFlows[i],
              baselineInputs);
      try {
        model.getColumn().run(UUID.randomUUID());
        DoeBigHillVacuumFractionationResult result =
            DoeBigHillVacuumFractionationResult.evaluate(model);
        evaluatedPoints[i] = new PointResult(massFlows[i], baselineInputs, result);
      } catch (RuntimeException exception) {
        throw new IllegalStateException("Vacuum feed-mass-flow sensitivity failed at point " + i
            + " with feed mass flow " + massFlows[i] + " kg/h", exception);
      }
    }
    return new DoeBigHillVacuumFeedMassFlowSensitivity(evaluatedPoints);
  }

  /** @return defensive copy of sensitivity points in increasing mass-flow order */
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
      throw new IndexOutOfBoundsException(
          "Feed-mass-flow sensitivity point index is outside the result");
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

  private static void validateMassFlows(double[] massFlows) {
    double previous = Double.NEGATIVE_INFINITY;
    for (double massFlow : massFlows) {
      if (!Double.isFinite(massFlow) || !(massFlow > 0.0)) {
        throw new IllegalArgumentException("Feed mass flows must be finite and positive");
      }
      if (!(massFlow > previous)) {
        throw new IllegalArgumentException(
            "Feed mass flows must be strictly increasing and unique");
      }
      previous = massFlow;
    }
  }

  /** Immutable result for one feed-mass-flow point. */
  public static final class PointResult {
    private final double feedMassFlowKgPerHour;
    private final OperatingInputs operatingInputs;
    private final DoeBigHillVacuumFractionationResult fractionationResult;

    private PointResult(double feedMassFlowKgPerHour, OperatingInputs operatingInputs,
        DoeBigHillVacuumFractionationResult fractionationResult) {
      this.feedMassFlowKgPerHour = feedMassFlowKgPerHour;
      this.operatingInputs = Objects.requireNonNull(operatingInputs, "operatingInputs");
      this.fractionationResult =
          Objects.requireNonNull(fractionationResult, "fractionationResult");
    }

    /** @return feed mass flow applied at this point in kg/h */
    public double getFeedMassFlowKgPerHour() {
      return feedMassFlowKgPerHour;
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
