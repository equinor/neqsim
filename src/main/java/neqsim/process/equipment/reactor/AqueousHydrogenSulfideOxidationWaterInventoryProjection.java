package neqsim.process.equipment.reactor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Projects qualified aqueous H2S/O2 segment evidence onto an explicit water inventory.
 *
 * <p>
 * The projection multiplies the existing molality-basis mean total-sulfide loss rates and reacted molalities by a
 * caller-supplied liquid-water inventory. It does not derive water holdup, assign reaction products, consume oxygen, or
 * mutate a process or thermodynamic system.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationWaterInventoryProjection {
  private static final double SECONDS_PER_HOUR = 3600.0;

  private AqueousHydrogenSulfideOxidationWaterInventoryProjection() {
  }

  /**
   * Project one immutable trajectory segment onto an explicit liquid-water inventory.
   *
   * @param segmentResult qualified segment result
   * @param waterInventoryKg liquid-water inventory [kg]
   * @return immutable dimensional total-sulfide loss evidence
   */
  public static Result project(AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segmentResult,
      double waterInventoryKg) {
    if (segmentResult == null) {
      throw new IllegalArgumentException("Segment result cannot be null");
    }
    requirePositiveFinite(waterInventoryKg, "Water inventory");

    double lowerRateMeanLossMolesPerHour = finiteProduct(segmentResult.getLowerRateMeanLossRateMolalityPerHour(),
        waterInventoryKg, "Lower-rate mean total-sulfide loss");
    double nominalMeanLossMolesPerHour = finiteProduct(segmentResult.getNominalMeanLossRateMolalityPerHour(),
        waterInventoryKg, "Nominal mean total-sulfide loss");
    double upperRateMeanLossMolesPerHour = finiteProduct(segmentResult.getUpperRateMeanLossRateMolalityPerHour(),
        waterInventoryKg, "Upper-rate mean total-sulfide loss");

    double lowerRateReactedMoles = finiteProduct(segmentResult.getLowerRateReactedTotalSulfideMolality(),
        waterInventoryKg, "Lower-rate reacted total sulfide");
    double nominalReactedMoles = finiteProduct(segmentResult.getNominalReactedTotalSulfideMolality(), waterInventoryKg,
        "Nominal reacted total sulfide");
    double upperRateReactedMoles = finiteProduct(segmentResult.getUpperRateReactedTotalSulfideMolality(),
        waterInventoryKg, "Upper-rate reacted total sulfide");

    return new Result(segmentResult.getIndex(), segmentResult.getSegment().getDurationHours(), waterInventoryKg,
        lowerRateMeanLossMolesPerHour, nominalMeanLossMolesPerHour, upperRateMeanLossMolesPerHour,
        lowerRateReactedMoles, nominalReactedMoles, upperRateReactedMoles);
  }

  /**
   * Project a complete immutable trajectory onto one constant liquid-water inventory.
   *
   * <p>
   * The same inventory is applied to every source-ordered segment. This preserves the trajectory's telescoping
   * inventory balance without implying that water holdup has been calculated.
   * </p>
   *
   * @param trajectoryResult qualified trajectory result
   * @param waterInventoryKg constant liquid-water inventory [kg]
   * @return immutable dimensional trajectory evidence
   */
  public static TrajectoryResult project(AqueousHydrogenSulfideOxidationTrajectory.Result trajectoryResult,
      double waterInventoryKg) {
    if (trajectoryResult == null) {
      throw new IllegalArgumentException("Trajectory result cannot be null");
    }
    requirePositiveFinite(waterInventoryKg, "Water inventory");

    List<Result> segmentProjections = new ArrayList<Result>();
    double lowerRateReactedMoles = 0.0;
    double nominalReactedMoles = 0.0;
    double upperRateReactedMoles = 0.0;
    for (AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segmentResult : trajectoryResult.getSegmentResults()) {
      Result projection = project(segmentResult, waterInventoryKg);
      segmentProjections.add(projection);
      lowerRateReactedMoles = finiteSum(lowerRateReactedMoles, projection.getLowerRateReactedMoles(),
          "Cumulative lower-rate reacted total sulfide");
      nominalReactedMoles = finiteSum(nominalReactedMoles, projection.getNominalReactedMoles(),
          "Cumulative nominal reacted total sulfide");
      upperRateReactedMoles = finiteSum(upperRateReactedMoles, projection.getUpperRateReactedMoles(),
          "Cumulative upper-rate reacted total sulfide");
    }

    double expectedLowerRateReactedMoles = finiteProduct(
        trajectoryResult.getInitialTotalSulfideMolality() - trajectoryResult.getFinalTotalSulfideMolalityAtLowerRate(),
        waterInventoryKg, "Expected cumulative lower-rate reacted total sulfide");
    double expectedNominalReactedMoles = finiteProduct(trajectoryResult.getReactedTotalSulfideMolality(),
        waterInventoryKg, "Expected cumulative nominal reacted total sulfide");
    double expectedUpperRateReactedMoles = finiteProduct(
        trajectoryResult.getInitialTotalSulfideMolality() - trajectoryResult.getFinalTotalSulfideMolalityAtUpperRate(),
        waterInventoryKg, "Expected cumulative upper-rate reacted total sulfide");

    return new TrajectoryResult(waterInventoryKg, trajectoryResult.getTotalTimeHours(), segmentProjections,
        lowerRateReactedMoles, nominalReactedMoles, upperRateReactedMoles,
        finiteDifference(expectedLowerRateReactedMoles, lowerRateReactedMoles,
            "Lower-rate trajectory closure residual"),
        finiteDifference(expectedNominalReactedMoles, nominalReactedMoles, "Nominal trajectory closure residual"),
        finiteDifference(expectedUpperRateReactedMoles, upperRateReactedMoles,
            "Upper-rate trajectory closure residual"));
  }

  private static void requirePositiveFinite(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  private static double finiteProduct(double first, double second, String name) {
    double product = first * second;
    if (!Double.isFinite(product) || product < 0.0 || (first > 0.0 && second > 0.0 && product == 0.0)) {
      throw new IllegalArgumentException(name + " is not finite and non-negative");
    }
    return product;
  }

  private static double finiteSum(double accumulator, double increment, String name) {
    double sum = accumulator + increment;
    if (!Double.isFinite(sum) || sum < 0.0) {
      throw new IllegalArgumentException(name + " is not finite and non-negative");
    }
    return sum;
  }

  private static double finiteDifference(double minuend, double subtrahend, String name) {
    double difference = minuend - subtrahend;
    if (!Double.isFinite(difference)) {
      throw new IllegalArgumentException(name + " is not finite");
    }
    return difference;
  }

  /** Immutable dimensional projection for one constant-water trajectory. */
  public static final class TrajectoryResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double waterInventoryKg;
    private final double totalTimeHours;
    private final List<Result> segmentProjections;
    private final double lowerRateReactedMoles;
    private final double nominalReactedMoles;
    private final double upperRateReactedMoles;
    private final double lowerRateClosureResidualMoles;
    private final double nominalClosureResidualMoles;
    private final double upperRateClosureResidualMoles;

    private TrajectoryResult(double waterInventoryKg, double totalTimeHours, List<Result> segmentProjections,
        double lowerRateReactedMoles, double nominalReactedMoles, double upperRateReactedMoles,
        double lowerRateClosureResidualMoles, double nominalClosureResidualMoles,
        double upperRateClosureResidualMoles) {
      this.waterInventoryKg = waterInventoryKg;
      this.totalTimeHours = totalTimeHours;
      this.segmentProjections = Collections.unmodifiableList(new ArrayList<Result>(segmentProjections));
      this.lowerRateReactedMoles = lowerRateReactedMoles;
      this.nominalReactedMoles = nominalReactedMoles;
      this.upperRateReactedMoles = upperRateReactedMoles;
      this.lowerRateClosureResidualMoles = lowerRateClosureResidualMoles;
      this.nominalClosureResidualMoles = nominalClosureResidualMoles;
      this.upperRateClosureResidualMoles = upperRateClosureResidualMoles;
    }

    /** @return caller-supplied constant liquid-water inventory [kg]. */
    public double getWaterInventoryKg() {
      return waterInventoryKg;
    }

    /** @return total trajectory duration [h]. */
    public double getTotalTimeHours() {
      return totalTimeHours;
    }

    /** @return immutable source-ordered dimensional segment projections. */
    public List<Result> getSegmentProjections() {
      return Collections.unmodifiableList(new ArrayList<Result>(segmentProjections));
    }

    /** @return cumulative reacted total sulfide for the lower-rate path [mol]. */
    public double getLowerRateReactedMoles() {
      return lowerRateReactedMoles;
    }

    /** @return cumulative reacted total sulfide for the nominal path [mol]. */
    public double getNominalReactedMoles() {
      return nominalReactedMoles;
    }

    /** @return cumulative reacted total sulfide for the upper-rate path [mol]. */
    public double getUpperRateReactedMoles() {
      return upperRateReactedMoles;
    }

    /** @return lower-rate constant-water inventory closure residual [mol]. */
    public double getLowerRateClosureResidualMoles() {
      return lowerRateClosureResidualMoles;
    }

    /** @return nominal constant-water inventory closure residual [mol]. */
    public double getNominalClosureResidualMoles() {
      return nominalClosureResidualMoles;
    }

    /** @return upper-rate constant-water inventory closure residual [mol]. */
    public double getUpperRateClosureResidualMoles() {
      return upperRateClosureResidualMoles;
    }
  }

  /** Immutable dimensional projection for one trajectory segment. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final int segmentIndex;
    private final double durationHours;
    private final double waterInventoryKg;
    private final double lowerRateMeanLossMolesPerHour;
    private final double nominalMeanLossMolesPerHour;
    private final double upperRateMeanLossMolesPerHour;
    private final double lowerRateReactedMoles;
    private final double nominalReactedMoles;
    private final double upperRateReactedMoles;

    private Result(int segmentIndex, double durationHours, double waterInventoryKg,
        double lowerRateMeanLossMolesPerHour, double nominalMeanLossMolesPerHour, double upperRateMeanLossMolesPerHour,
        double lowerRateReactedMoles, double nominalReactedMoles, double upperRateReactedMoles) {
      this.segmentIndex = segmentIndex;
      this.durationHours = durationHours;
      this.waterInventoryKg = waterInventoryKg;
      this.lowerRateMeanLossMolesPerHour = lowerRateMeanLossMolesPerHour;
      this.nominalMeanLossMolesPerHour = nominalMeanLossMolesPerHour;
      this.upperRateMeanLossMolesPerHour = upperRateMeanLossMolesPerHour;
      this.lowerRateReactedMoles = lowerRateReactedMoles;
      this.nominalReactedMoles = nominalReactedMoles;
      this.upperRateReactedMoles = upperRateReactedMoles;
    }

    /** @return source-order segment index. */
    public int getSegmentIndex() {
      return segmentIndex;
    }

    /** @return segment duration [h]. */
    public double getDurationHours() {
      return durationHours;
    }

    /** @return caller-supplied liquid-water inventory [kg]. */
    public double getWaterInventoryKg() {
      return waterInventoryKg;
    }

    /** @return lower-rate mean total-sulfide loss [mol/h]. */
    public double getLowerRateMeanLossMolesPerHour() {
      return lowerRateMeanLossMolesPerHour;
    }

    /** @return nominal mean total-sulfide loss [mol/h]. */
    public double getNominalMeanLossMolesPerHour() {
      return nominalMeanLossMolesPerHour;
    }

    /** @return upper-rate mean total-sulfide loss [mol/h]. */
    public double getUpperRateMeanLossMolesPerHour() {
      return upperRateMeanLossMolesPerHour;
    }

    /** @return lower-rate mean total-sulfide loss [mol/s]. */
    public double getLowerRateMeanLossMolesPerSecond() {
      return lowerRateMeanLossMolesPerHour / SECONDS_PER_HOUR;
    }

    /** @return nominal mean total-sulfide loss [mol/s]. */
    public double getNominalMeanLossMolesPerSecond() {
      return nominalMeanLossMolesPerHour / SECONDS_PER_HOUR;
    }

    /** @return upper-rate mean total-sulfide loss [mol/s]. */
    public double getUpperRateMeanLossMolesPerSecond() {
      return upperRateMeanLossMolesPerHour / SECONDS_PER_HOUR;
    }

    /** @return reacted total sulfide for the lower-rate path [mol]. */
    public double getLowerRateReactedMoles() {
      return lowerRateReactedMoles;
    }

    /** @return reacted total sulfide for the nominal path [mol]. */
    public double getNominalReactedMoles() {
      return nominalReactedMoles;
    }

    /** @return reacted total sulfide for the upper-rate path [mol]. */
    public double getUpperRateReactedMoles() {
      return upperRateReactedMoles;
    }
  }
}
