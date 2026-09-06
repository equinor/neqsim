package neqsim.process.equipment.reactor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Exact piecewise-constant exposure trajectory for the published aqueous total-sulfide/O2 screening correlation.
 *
 * <p>
 * Each segment delegates its rate calculation to {@link AqueousHydrogenSulfideOxidationKinetics}. The total-sulfide
 * fraction therefore follows {@code exp(-sum(k_i [O2]_i dt_i))} without numerical timestep error. The reported
 * one-standard-deviation log-rate scatter is propagated as one common multiplicative correlation envelope.
 * </p>
 *
 * <p>
 * This class neither consumes oxygen nor assigns sulfur products. It does not accept pressure and does not qualify a
 * dense-phase CO2 or pipeline calculation.
 * </p>
 *
 * @author NeqSim Team
 * @version 1.0
 */
public final class AqueousHydrogenSulfideOxidationTrajectory implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Minimum initial total-sulfide molality covered by the source experiment [mol/kg water]. */
  public static final double MINIMUM_INITIAL_TOTAL_SULFIDE_MOLALITY = AqueousHydrogenSulfideOxidationKinetics.PUBLISHED_INITIAL_TOTAL_SULFIDE_MOLALITY
      - AqueousHydrogenSulfideOxidationKinetics.PUBLISHED_INITIAL_TOTAL_SULFIDE_SPREAD;

  /** Maximum initial total-sulfide molality covered by the source experiment [mol/kg water]. */
  public static final double MAXIMUM_INITIAL_TOTAL_SULFIDE_MOLALITY = AqueousHydrogenSulfideOxidationKinetics.PUBLISHED_INITIAL_TOTAL_SULFIDE_MOLALITY
      + AqueousHydrogenSulfideOxidationKinetics.PUBLISHED_INITIAL_TOTAL_SULFIDE_SPREAD;

  private AqueousHydrogenSulfideOxidationTrajectory() {
  }

  /**
   * Propagate total dissolved sulfide through ordered constant-state exposure segments.
   *
   * @param initialTotalSulfideMolality initial total-sulfide molality [mol/kg water]
   * @param segments non-empty ordered exposure segments
   * @return immutable trajectory result with per-segment evidence
   * @throws IllegalArgumentException when the initial molality is outside the source experiment, the segment list is
   * null or empty, a segment is null, or an accumulated value is not finite
   */
  public static Result advance(double initialTotalSulfideMolality, List<Segment> segments) {
    requireInitialTotalSulfide(initialTotalSulfideMolality);
    if (segments == null || segments.isEmpty()) {
      throw new IllegalArgumentException("at least one exposure segment is required");
    }

    List<SegmentResult> segmentResults = new ArrayList<SegmentResult>();
    double totalTimeHours = 0.0;
    double nominalExposure = 0.0;
    double lowerRateExposure = 0.0;
    double upperRateExposure = 0.0;

    for (int index = 0; index < segments.size(); index++) {
      Segment segment = segments.get(index);
      if (segment == null) {
        throw new IllegalArgumentException("exposure segment " + index + " cannot be null");
      }

      AqueousHydrogenSulfideOxidationKinetics.RateConstantRange rateRange = AqueousHydrogenSulfideOxidationKinetics
          .secondOrderRateConstantRange(segment.getTemperatureK(), segment.getPH(),
              segment.getIonicStrengthMolPerKgWater());
      double nominalPseudoFirstOrderRate = AqueousHydrogenSulfideOxidationKinetics.pseudoFirstOrderRateConstant(
          segment.getAirSaturatedOxygenMolality(), segment.getTemperatureK(), segment.getPH(),
          segment.getIonicStrengthMolPerKgWater());
      double lowerPseudoFirstOrderRate = rateRange.getLower() * segment.getAirSaturatedOxygenMolality();
      double upperPseudoFirstOrderRate = rateRange.getUpper() * segment.getAirSaturatedOxygenMolality();
      requireFinitePositive(lowerPseudoFirstOrderRate, "lower pseudo-first-order rate");
      requireFinitePositive(upperPseudoFirstOrderRate, "upper pseudo-first-order rate");

      double segmentNominalExposure = nominalPseudoFirstOrderRate * segment.getDurationHours();
      double segmentLowerRateExposure = lowerPseudoFirstOrderRate * segment.getDurationHours();
      double segmentUpperRateExposure = upperPseudoFirstOrderRate * segment.getDurationHours();
      requireFiniteNonNegative(segmentNominalExposure, "segment nominal exposure");
      requireFiniteNonNegative(segmentLowerRateExposure, "segment lower-rate exposure");
      requireFiniteNonNegative(segmentUpperRateExposure, "segment upper-rate exposure");

      totalTimeHours = finiteSum(totalTimeHours, segment.getDurationHours(), "total time");
      nominalExposure = finiteSum(nominalExposure, segmentNominalExposure, "nominal cumulative exposure");
      lowerRateExposure = finiteSum(lowerRateExposure, segmentLowerRateExposure, "lower-rate cumulative exposure");
      upperRateExposure = finiteSum(upperRateExposure, segmentUpperRateExposure, "upper-rate cumulative exposure");

      segmentResults.add(new SegmentResult(index, segment, rateRange.getLower(), rateRange.getNominal(),
          rateRange.getUpper(), lowerPseudoFirstOrderRate, nominalPseudoFirstOrderRate, upperPseudoFirstOrderRate,
          segmentLowerRateExposure, segmentNominalExposure, segmentUpperRateExposure, lowerRateExposure,
          nominalExposure, upperRateExposure));
    }

    double nominalRemainingFraction = Math.exp(-nominalExposure);
    double lowerRateRemainingFraction = Math.exp(-lowerRateExposure);
    double upperRateRemainingFraction = Math.exp(-upperRateExposure);
    double finalTotalSulfideMolality = initialTotalSulfideMolality * nominalRemainingFraction;
    double finalAtLowerRate = initialTotalSulfideMolality * lowerRateRemainingFraction;
    double finalAtUpperRate = initialTotalSulfideMolality * upperRateRemainingFraction;
    double reactedTotalSulfideMolality = initialTotalSulfideMolality - finalTotalSulfideMolality;
    double closureResidual = initialTotalSulfideMolality - finalTotalSulfideMolality - reactedTotalSulfideMolality;

    return new Result(initialTotalSulfideMolality, finalTotalSulfideMolality, reactedTotalSulfideMolality,
        finalAtLowerRate, finalAtUpperRate, totalTimeHours, nominalExposure, lowerRateExposure, upperRateExposure,
        nominalRemainingFraction, lowerRateRemainingFraction, upperRateRemainingFraction, closureResidual,
        segmentResults);
  }

  /**
   * Locate the first time a remaining-total-sulfide target is reached within ordered exposure segments.
   *
   * <p>
   * The calculation analytically inverts cumulative exposure for the lower, nominal, and upper rates. All three
   * fit-scatter paths must reach the requested target within the supplied finite trajectory. The result therefore never
   * extrapolates the final segment or returns an infinite crossing time.
   * </p>
   *
   * @param targetRemainingFraction target total-sulfide fraction in the interval (0, 1]
   * @param segments non-empty ordered exposure segments
   * @return immutable shortest, nominal, and longest target-crossing evidence
   * @throws IllegalArgumentException when the target or trajectory is invalid, a calculated value is not finite, or
   * the target is not reached for every fit-scatter path
   */
  public static TargetCrossingRangeResult timeToRemainingFractionRange(double targetRemainingFraction,
      List<Segment> segments) {
    Result trajectory = advance(AqueousHydrogenSulfideOxidationKinetics.PUBLISHED_INITIAL_TOTAL_SULFIDE_MOLALITY,
        segments);
    Segment firstSegment = trajectory.getSegmentResults().get(0).getSegment();
    AqueousHydrogenSulfideOxidationKinetics.TargetTimeRangeResult singleStateTarget = AqueousHydrogenSulfideOxidationKinetics
        .timeToRemainingFractionRange(firstSegment.getAirSaturatedOxygenMolality(), targetRemainingFraction,
            firstSegment.getTemperatureK(), firstSegment.getPH(), firstSegment.getIonicStrengthMolPerKgWater());
    double requiredExposure = singleStateTarget.getRequiredExposure();

    Crossing longest = locateCrossing(requiredExposure, trajectory.getSegmentResults(), RateCase.LOWER);
    Crossing nominal = locateCrossing(requiredExposure, trajectory.getSegmentResults(), RateCase.NOMINAL);
    Crossing shortest = locateCrossing(requiredExposure, trajectory.getSegmentResults(), RateCase.UPPER);

    return new TargetCrossingRangeResult(targetRemainingFraction, requiredExposure, shortest.timeHours,
        nominal.timeHours, longest.timeHours, shortest.segmentIndex, nominal.segmentIndex, longest.segmentIndex,
        trajectory.getTotalTimeHours());
  }

  private static Crossing locateCrossing(double requiredExposure, List<SegmentResult> segmentResults,
      RateCase rateCase) {
    if (requiredExposure == 0.0) {
      return new Crossing(0, 0.0);
    }

    double cumulativeExposure = 0.0;
    double elapsedTimeHours = 0.0;
    for (SegmentResult segmentResult : segmentResults) {
      double segmentExposure = rateCase.segmentExposure(segmentResult);
      double nextExposure = finiteSum(cumulativeExposure, segmentExposure, "target-crossing cumulative exposure");
      if (nextExposure >= requiredExposure) {
        double exposureWithinSegment = requiredExposure - cumulativeExposure;
        double timeWithinSegment = exposureWithinSegment / rateCase.pseudoFirstOrderRate(segmentResult);
        requireFiniteNonNegative(timeWithinSegment, "target-crossing time within segment");
        return new Crossing(segmentResult.getIndex(),
            finiteSum(elapsedTimeHours, timeWithinSegment, "target-crossing elapsed time"));
      }
      cumulativeExposure = nextExposure;
      elapsedTimeHours = finiteSum(elapsedTimeHours, segmentResult.getSegment().getDurationHours(),
          "target-crossing elapsed time");
    }

    throw new IllegalArgumentException(
        "target remaining fraction is not reached by every fit-scatter path within the supplied trajectory");
  }

  private enum RateCase {
    LOWER {
      @Override
      double segmentExposure(SegmentResult result) {
        return result.getLowerRateExposure();
      }

      @Override
      double pseudoFirstOrderRate(SegmentResult result) {
        return result.getLowerPseudoFirstOrderRate();
      }
    },
    NOMINAL {
      @Override
      double segmentExposure(SegmentResult result) {
        return result.getNominalExposure();
      }

      @Override
      double pseudoFirstOrderRate(SegmentResult result) {
        return result.getNominalPseudoFirstOrderRate();
      }
    },
    UPPER {
      @Override
      double segmentExposure(SegmentResult result) {
        return result.getUpperRateExposure();
      }

      @Override
      double pseudoFirstOrderRate(SegmentResult result) {
        return result.getUpperPseudoFirstOrderRate();
      }
    };

    abstract double segmentExposure(SegmentResult result);

    abstract double pseudoFirstOrderRate(SegmentResult result);
  }

  private static final class Crossing {
    private final int segmentIndex;
    private final double timeHours;

    private Crossing(int segmentIndex, double timeHours) {
      this.segmentIndex = segmentIndex;
      this.timeHours = timeHours;
    }
  }

  private static void requireInitialTotalSulfide(double molality) {
    if (!Double.isFinite(molality) || molality < MINIMUM_INITIAL_TOTAL_SULFIDE_MOLALITY
        || molality > MAXIMUM_INITIAL_TOTAL_SULFIDE_MOLALITY) {
      throw new IllegalArgumentException("initial total-sulfide molality must be within the source experiment range of "
          + MINIMUM_INITIAL_TOTAL_SULFIDE_MOLALITY + " to " + MAXIMUM_INITIAL_TOTAL_SULFIDE_MOLALITY + " mol/kg water");
    }
  }

  private static double finiteSum(double current, double increment, String name) {
    double sum = current + increment;
    if (!Double.isFinite(sum)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
    return sum;
  }

  private static void requireFinitePositive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  private static void requireFiniteNonNegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and non-negative");
    }
  }

  /** Immutable target-crossing range within a finite piecewise exposure trajectory. */
  public static final class TargetCrossingRangeResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double targetRemainingFraction;
    private final double requiredExposure;
    private final double shortestTimeHours;
    private final double nominalTimeHours;
    private final double longestTimeHours;
    private final int shortestCrossingSegmentIndex;
    private final int nominalCrossingSegmentIndex;
    private final int longestCrossingSegmentIndex;
    private final double suppliedTrajectoryTimeHours;

    private TargetCrossingRangeResult(double targetRemainingFraction, double requiredExposure,
        double shortestTimeHours, double nominalTimeHours, double longestTimeHours,
        int shortestCrossingSegmentIndex, int nominalCrossingSegmentIndex, int longestCrossingSegmentIndex,
        double suppliedTrajectoryTimeHours) {
      this.targetRemainingFraction = targetRemainingFraction;
      this.requiredExposure = requiredExposure;
      this.shortestTimeHours = shortestTimeHours;
      this.nominalTimeHours = nominalTimeHours;
      this.longestTimeHours = longestTimeHours;
      this.shortestCrossingSegmentIndex = shortestCrossingSegmentIndex;
      this.nominalCrossingSegmentIndex = nominalCrossingSegmentIndex;
      this.longestCrossingSegmentIndex = longestCrossingSegmentIndex;
      this.suppliedTrajectoryTimeHours = suppliedTrajectoryTimeHours;
    }

    /** @return requested remaining total-sulfide fraction. */
    public double getTargetRemainingFraction() {
      return targetRemainingFraction;
    }

    /** @return dimensionless cumulative exposure required to reach the target. */
    public double getRequiredExposure() {
      return requiredExposure;
    }

    /** @return earliest crossing time, obtained with the upper fit-scatter rates [h]. */
    public double getShortestTimeHours() {
      return shortestTimeHours;
    }

    /** @return nominal crossing time [h]. */
    public double getNominalTimeHours() {
      return nominalTimeHours;
    }

    /** @return latest crossing time, obtained with the lower fit-scatter rates [h]. */
    public double getLongestTimeHours() {
      return longestTimeHours;
    }

    /** @return source-order segment index containing the shortest crossing. */
    public int getShortestCrossingSegmentIndex() {
      return shortestCrossingSegmentIndex;
    }

    /** @return source-order segment index containing the nominal crossing. */
    public int getNominalCrossingSegmentIndex() {
      return nominalCrossingSegmentIndex;
    }

    /** @return source-order segment index containing the longest crossing. */
    public int getLongestCrossingSegmentIndex() {
      return longestCrossingSegmentIndex;
    }

    /** @return total duration of the supplied finite trajectory [h]. */
    public double getSuppliedTrajectoryTimeHours() {
      return suppliedTrajectoryTimeHours;
    }
  }

  /** Immutable piecewise-constant aqueous exposure definition. */
  public static final class Segment implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double durationHours;
    private final double temperatureK;
    private final double pH;
    private final double ionicStrengthMolPerKgWater;
    private final double airSaturatedOxygenMolality;

    /**
     * Create one constant-state exposure segment.
     *
     * @param durationHours segment duration [h]
     * @param temperatureK aqueous temperature [K]
     * @param pH aqueous pH on the source-compatible scale
     * @param ionicStrengthMolPerKgWater ionic strength [mol/kg water]
     * @param airSaturatedOxygenMolality independently established air-saturated dissolved oxygen molality [mol/kg
     * water]
     * @throws IllegalArgumentException when duration is negative or non-finite, oxygen is not finite and positive, or
     * the thermochemical state is outside the source range
     */
    public Segment(double durationHours, double temperatureK, double pH, double ionicStrengthMolPerKgWater,
        double airSaturatedOxygenMolality) {
      requireFiniteNonNegative(durationHours, "segment duration");
      AqueousHydrogenSulfideOxidationKinetics.pseudoFirstOrderRateConstant(airSaturatedOxygenMolality, temperatureK, pH,
          ionicStrengthMolPerKgWater);
      this.durationHours = durationHours;
      this.temperatureK = temperatureK;
      this.pH = pH;
      this.ionicStrengthMolPerKgWater = ionicStrengthMolPerKgWater;
      this.airSaturatedOxygenMolality = airSaturatedOxygenMolality;
    }

    /** @return segment duration [h]. */
    public double getDurationHours() {
      return durationHours;
    }

    /** @return aqueous temperature [K]. */
    public double getTemperatureK() {
      return temperatureK;
    }

    /** @return aqueous pH on the caller's source-compatible scale. */
    public double getPH() {
      return pH;
    }

    /** @return ionic strength [mol/kg water]. */
    public double getIonicStrengthMolPerKgWater() {
      return ionicStrengthMolPerKgWater;
    }

    /** @return caller-supplied air-saturated dissolved oxygen molality [mol/kg water]. */
    public double getAirSaturatedOxygenMolality() {
      return airSaturatedOxygenMolality;
    }
  }

  /** Immutable calculated evidence for one exposure segment. */
  public static final class SegmentResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final int index;
    private final Segment segment;
    private final double lowerSecondOrderRate;
    private final double nominalSecondOrderRate;
    private final double upperSecondOrderRate;
    private final double lowerPseudoFirstOrderRate;
    private final double nominalPseudoFirstOrderRate;
    private final double upperPseudoFirstOrderRate;
    private final double lowerRateExposure;
    private final double nominalExposure;
    private final double upperRateExposure;
    private final double cumulativeLowerRateExposure;
    private final double cumulativeNominalExposure;
    private final double cumulativeUpperRateExposure;

    private SegmentResult(int index, Segment segment, double lowerSecondOrderRate, double nominalSecondOrderRate,
        double upperSecondOrderRate, double lowerPseudoFirstOrderRate, double nominalPseudoFirstOrderRate,
        double upperPseudoFirstOrderRate, double lowerRateExposure, double nominalExposure, double upperRateExposure,
        double cumulativeLowerRateExposure, double cumulativeNominalExposure, double cumulativeUpperRateExposure) {
      this.index = index;
      this.segment = segment;
      this.lowerSecondOrderRate = lowerSecondOrderRate;
      this.nominalSecondOrderRate = nominalSecondOrderRate;
      this.upperSecondOrderRate = upperSecondOrderRate;
      this.lowerPseudoFirstOrderRate = lowerPseudoFirstOrderRate;
      this.nominalPseudoFirstOrderRate = nominalPseudoFirstOrderRate;
      this.upperPseudoFirstOrderRate = upperPseudoFirstOrderRate;
      this.lowerRateExposure = lowerRateExposure;
      this.nominalExposure = nominalExposure;
      this.upperRateExposure = upperRateExposure;
      this.cumulativeLowerRateExposure = cumulativeLowerRateExposure;
      this.cumulativeNominalExposure = cumulativeNominalExposure;
      this.cumulativeUpperRateExposure = cumulativeUpperRateExposure;
    }

    /** @return zero-based source-order segment index. */
    public int getIndex() {
      return index;
    }

    /** @return immutable segment input. */
    public Segment getSegment() {
      return segment;
    }

    /** @return lower second-order rate [kg water/(mol h)]. */
    public double getLowerSecondOrderRate() {
      return lowerSecondOrderRate;
    }

    /** @return nominal second-order rate [kg water/(mol h)]. */
    public double getNominalSecondOrderRate() {
      return nominalSecondOrderRate;
    }

    /** @return upper second-order rate [kg water/(mol h)]. */
    public double getUpperSecondOrderRate() {
      return upperSecondOrderRate;
    }

    /** @return lower pseudo-first-order rate [1/h]. */
    public double getLowerPseudoFirstOrderRate() {
      return lowerPseudoFirstOrderRate;
    }

    /** @return nominal pseudo-first-order rate [1/h]. */
    public double getNominalPseudoFirstOrderRate() {
      return nominalPseudoFirstOrderRate;
    }

    /** @return upper pseudo-first-order rate [1/h]. */
    public double getUpperPseudoFirstOrderRate() {
      return upperPseudoFirstOrderRate;
    }

    /** @return lower-rate segment exposure. */
    public double getLowerRateExposure() {
      return lowerRateExposure;
    }

    /** @return nominal segment exposure. */
    public double getNominalExposure() {
      return nominalExposure;
    }

    /** @return upper-rate segment exposure. */
    public double getUpperRateExposure() {
      return upperRateExposure;
    }

    /** @return cumulative lower-rate exposure through this segment. */
    public double getCumulativeLowerRateExposure() {
      return cumulativeLowerRateExposure;
    }

    /** @return cumulative nominal exposure through this segment. */
    public double getCumulativeNominalExposure() {
      return cumulativeNominalExposure;
    }

    /** @return cumulative upper-rate exposure through this segment. */
    public double getCumulativeUpperRateExposure() {
      return cumulativeUpperRateExposure;
    }
  }

  /** Immutable result of an exact piecewise exposure trajectory. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double initialTotalSulfideMolality;
    private final double finalTotalSulfideMolality;
    private final double reactedTotalSulfideMolality;
    private final double finalTotalSulfideMolalityAtLowerRate;
    private final double finalTotalSulfideMolalityAtUpperRate;
    private final double totalTimeHours;
    private final double nominalExposure;
    private final double lowerRateExposure;
    private final double upperRateExposure;
    private final double nominalRemainingFraction;
    private final double lowerRateRemainingFraction;
    private final double upperRateRemainingFraction;
    private final double totalSulfideClosureResidual;
    private final List<SegmentResult> segmentResults;

    private Result(double initialTotalSulfideMolality, double finalTotalSulfideMolality,
        double reactedTotalSulfideMolality, double finalTotalSulfideMolalityAtLowerRate,
        double finalTotalSulfideMolalityAtUpperRate, double totalTimeHours, double nominalExposure,
        double lowerRateExposure, double upperRateExposure, double nominalRemainingFraction,
        double lowerRateRemainingFraction, double upperRateRemainingFraction, double totalSulfideClosureResidual,
        List<SegmentResult> segmentResults) {
      this.initialTotalSulfideMolality = initialTotalSulfideMolality;
      this.finalTotalSulfideMolality = finalTotalSulfideMolality;
      this.reactedTotalSulfideMolality = reactedTotalSulfideMolality;
      this.finalTotalSulfideMolalityAtLowerRate = finalTotalSulfideMolalityAtLowerRate;
      this.finalTotalSulfideMolalityAtUpperRate = finalTotalSulfideMolalityAtUpperRate;
      this.totalTimeHours = totalTimeHours;
      this.nominalExposure = nominalExposure;
      this.lowerRateExposure = lowerRateExposure;
      this.upperRateExposure = upperRateExposure;
      this.nominalRemainingFraction = nominalRemainingFraction;
      this.lowerRateRemainingFraction = lowerRateRemainingFraction;
      this.upperRateRemainingFraction = upperRateRemainingFraction;
      this.totalSulfideClosureResidual = totalSulfideClosureResidual;
      this.segmentResults = Collections.unmodifiableList(new ArrayList<SegmentResult>(segmentResults));
    }

    /** @return initial total-sulfide molality [mol/kg water]. */
    public double getInitialTotalSulfideMolality() {
      return initialTotalSulfideMolality;
    }

    /** @return nominal final total-sulfide molality [mol/kg water]. */
    public double getFinalTotalSulfideMolality() {
      return finalTotalSulfideMolality;
    }

    /** @return nominal reacted total-sulfide molality [mol/kg water]. */
    public double getReactedTotalSulfideMolality() {
      return reactedTotalSulfideMolality;
    }

    /** @return final molality at the lower fit-scatter rate [mol/kg water]. */
    public double getFinalTotalSulfideMolalityAtLowerRate() {
      return finalTotalSulfideMolalityAtLowerRate;
    }

    /** @return final molality at the upper fit-scatter rate [mol/kg water]. */
    public double getFinalTotalSulfideMolalityAtUpperRate() {
      return finalTotalSulfideMolalityAtUpperRate;
    }

    /** @return total segment duration [h]. */
    public double getTotalTimeHours() {
      return totalTimeHours;
    }

    /** @return cumulative nominal dimensionless exposure. */
    public double getNominalExposure() {
      return nominalExposure;
    }

    /** @return cumulative lower-rate dimensionless exposure. */
    public double getLowerRateExposure() {
      return lowerRateExposure;
    }

    /** @return cumulative upper-rate dimensionless exposure. */
    public double getUpperRateExposure() {
      return upperRateExposure;
    }

    /** @return nominal remaining total-sulfide fraction. */
    public double getNominalRemainingFraction() {
      return nominalRemainingFraction;
    }

    /** @return remaining fraction at the lower fit-scatter rate. */
    public double getLowerRateRemainingFraction() {
      return lowerRateRemainingFraction;
    }

    /** @return remaining fraction at the upper fit-scatter rate. */
    public double getUpperRateRemainingFraction() {
      return upperRateRemainingFraction;
    }

    /** @return total-sulfide closure residual [mol/kg water]. */
    public double getTotalSulfideClosureResidual() {
      return totalSulfideClosureResidual;
    }

    /** @return immutable source-ordered segment diagnostics. */
    public List<SegmentResult> getSegmentResults() {
      return segmentResults;
    }
  }
}
