package neqsim.process.equipment.reactor;

import java.io.Serializable;

/**
 * Projects qualified aqueous H2S/O2 segment evidence onto an explicit water inventory.
 *
 * <p>
 * The projection multiplies the existing molality-basis mean total-sulfide loss rates and reacted
 * molalities by a caller-supplied liquid-water inventory. It does not derive water holdup, assign
 * reaction products, consume oxygen, or mutate a process or thermodynamic system.
 * </p>
 *
 * @author esol
 * @version $Id: $
 */
public final class AqueousHydrogenSulfideOxidationWaterInventoryProjection {
  private static final double SECONDS_PER_HOUR = 3600.0;

  private AqueousHydrogenSulfideOxidationWaterInventoryProjection() {}

  /**
   * Project one immutable trajectory segment onto an explicit liquid-water inventory.
   *
   * @param segmentResult qualified segment result
   * @param waterInventoryKg liquid-water inventory [kg]
   * @return immutable dimensional total-sulfide loss evidence
   */
  public static Result project(
      AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segmentResult,
      double waterInventoryKg) {
    if (segmentResult == null) {
      throw new IllegalArgumentException("Segment result cannot be null");
    }
    requirePositiveFinite(waterInventoryKg, "Water inventory");

    double lowerRateMeanLossMolesPerHour = finiteProduct(
        segmentResult.getLowerRateMeanLossRateMolalityPerHour(), waterInventoryKg,
        "Lower-rate mean total-sulfide loss");
    double nominalMeanLossMolesPerHour = finiteProduct(
        segmentResult.getNominalMeanLossRateMolalityPerHour(), waterInventoryKg,
        "Nominal mean total-sulfide loss");
    double upperRateMeanLossMolesPerHour = finiteProduct(
        segmentResult.getUpperRateMeanLossRateMolalityPerHour(), waterInventoryKg,
        "Upper-rate mean total-sulfide loss");

    double lowerRateReactedMoles = finiteProduct(
        segmentResult.getLowerRateReactedTotalSulfideMolality(), waterInventoryKg,
        "Lower-rate reacted total sulfide");
    double nominalReactedMoles = finiteProduct(
        segmentResult.getNominalReactedTotalSulfideMolality(), waterInventoryKg,
        "Nominal reacted total sulfide");
    double upperRateReactedMoles = finiteProduct(
        segmentResult.getUpperRateReactedTotalSulfideMolality(), waterInventoryKg,
        "Upper-rate reacted total sulfide");

    return new Result(segmentResult.getSegmentIndex(), segmentResult.getSegment().getDurationHours(),
        waterInventoryKg, lowerRateMeanLossMolesPerHour, nominalMeanLossMolesPerHour,
        upperRateMeanLossMolesPerHour, lowerRateReactedMoles, nominalReactedMoles,
        upperRateReactedMoles);
  }

  private static void requirePositiveFinite(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  private static double finiteProduct(double first, double second, String name) {
    double product = first * second;
    if (!Double.isFinite(product) || product < 0.0) {
      throw new IllegalArgumentException(name + " is not finite and non-negative");
    }
    return product;
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
        double lowerRateMeanLossMolesPerHour, double nominalMeanLossMolesPerHour,
        double upperRateMeanLossMolesPerHour, double lowerRateReactedMoles,
        double nominalReactedMoles, double upperRateReactedMoles) {
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
