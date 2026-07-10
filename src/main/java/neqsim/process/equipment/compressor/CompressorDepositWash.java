package neqsim.process.equipment.compressor;

import java.io.Serializable;
import java.util.EnumMap;
import java.util.Map;

/**
 * Plans and simulates online (live) or offline compressor washing to remove {@link CompressorDeposit deposits} with a
 * {@link WashFluid wash fluid}.
 *
 * <p>
 * Washing is modelled as screening-level dissolution: for each deposit mechanism the wash fluid can dissolve, the mass
 * removed over the wash is
 * </p>
 *
 * <pre>
 * removed = min(deposit mass, washFluidRate * solubility * contactEfficiency * duration)
 * </pre>
 *
 * <p>
 * where {@code solubility} (kg deposit / kg fluid) comes from {@link WashFluid} and {@code contactEfficiency} accounts
 * for imperfect contact during online washing (droplet carry-through, short residence time). Deposits the fluid cannot
 * dissolve are left untouched — so water removes salt but leaves elemental sulfur, while xylene removes sulfur and wax
 * but leaves salt. This drives both wash-fluid recommendation and wash-rate / duration planning.
 * </p>
 *
 * <h2>Typical usage</h2>
 *
 * <pre>{@code
 * CompressorDepositWash wash = new CompressorDepositWash();
 * WashFluid fluid = CompressorDepositWash.recommend(deposit); // pick the best fluid
 * CompressorDepositWash.WashResult r = wash.wash(deposit, fluid, 200.0, 2.0); // 200 kg/hr, 2 h
 * // deposit now has less mass -> compressor.run() recovers performance
 * }</pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CompressorDepositWash implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Contact efficiency (fraction, 1 = ideal, lower for online washing). */
  private double contactEfficiency = 0.7;

  /**
   * Result of a wash operation.
   */
  public static class WashResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private final WashFluid fluid;
    private final double durationHours;
    private final double fluidUsedKg;
    private final Map<DepositMechanism, Double> removedKg;
    private final double totalRemovedKg;
    private final double efficiencyMultiplierBefore;
    private final double efficiencyMultiplierAfter;
    private final double headMultiplierBefore;
    private final double headMultiplierAfter;
    private final double remainingDepositKg;

    WashResult(WashFluid fluid, double durationHours, double fluidUsedKg, Map<DepositMechanism, Double> removedKg,
        double totalRemovedKg, double efficiencyMultiplierBefore, double efficiencyMultiplierAfter,
        double headMultiplierBefore, double headMultiplierAfter, double remainingDepositKg) {
      this.fluid = fluid;
      this.durationHours = durationHours;
      this.fluidUsedKg = fluidUsedKg;
      this.removedKg = removedKg;
      this.totalRemovedKg = totalRemovedKg;
      this.efficiencyMultiplierBefore = efficiencyMultiplierBefore;
      this.efficiencyMultiplierAfter = efficiencyMultiplierAfter;
      this.headMultiplierBefore = headMultiplierBefore;
      this.headMultiplierAfter = headMultiplierAfter;
      this.remainingDepositKg = remainingDepositKg;
    }

    /**
     * Wash fluid used.
     *
     * @return wash fluid
     */
    public WashFluid getFluid() {
      return fluid;
    }

    /**
     * Wash duration.
     *
     * @return duration in hours
     */
    public double getDurationHours() {
      return durationHours;
    }

    /**
     * Total wash fluid consumed.
     *
     * @return fluid used in kg
     */
    public double getFluidUsedKg() {
      return fluidUsedKg;
    }

    /**
     * Deposit mass removed per mechanism.
     *
     * @return map of mechanism to removed mass in kg
     */
    public Map<DepositMechanism, Double> getRemovedKg() {
      return removedKg;
    }

    /**
     * Total deposit mass removed.
     *
     * @return total removed in kg
     */
    public double getTotalRemovedKg() {
      return totalRemovedKg;
    }

    /**
     * Efficiency multiplier before the wash.
     *
     * @return efficiency multiplier (0-1)
     */
    public double getEfficiencyMultiplierBefore() {
      return efficiencyMultiplierBefore;
    }

    /**
     * Efficiency multiplier after the wash (recovered).
     *
     * @return efficiency multiplier (0-1)
     */
    public double getEfficiencyMultiplierAfter() {
      return efficiencyMultiplierAfter;
    }

    /**
     * Head multiplier before the wash.
     *
     * @return head multiplier (0-1)
     */
    public double getHeadMultiplierBefore() {
      return headMultiplierBefore;
    }

    /**
     * Head multiplier after the wash (recovered).
     *
     * @return head multiplier (0-1)
     */
    public double getHeadMultiplierAfter() {
      return headMultiplierAfter;
    }

    /**
     * Deposit mass remaining after the wash.
     *
     * @return remaining deposit in kg
     */
    public double getRemainingDepositKg() {
      return remainingDepositKg;
    }
  }

  /**
   * Recommend the wash fluid that can dissolve the largest total deposit mass currently present.
   *
   * @param deposit deposit inventory to clean
   * @return the recommended wash fluid, or {@code null} if no fluid dissolves any present deposit
   */
  public static WashFluid recommend(CompressorDeposit deposit) {
    if (deposit == null) {
      return null;
    }
    WashFluid best = null;
    double bestRemovable = 0.0;
    for (WashFluid fluid : WashFluid.values()) {
      double removable = 0.0;
      for (DepositMechanism mechanism : DepositMechanism.values()) {
        if (fluid.dissolves(mechanism)) {
          removable += deposit.getDepositMass(mechanism) * fluid.getSolubility(mechanism);
        }
      }
      if (removable > bestRemovable) {
        bestRemovable = removable;
        best = fluid;
      }
    }
    return best;
  }

  /**
   * Simulate a wash: dissolve deposit from the inventory with the given fluid, rate and duration. The
   * {@link CompressorDeposit} is updated in place, so a subsequent compressor run reflects the recovered performance.
   *
   * @param deposit deposit inventory to clean (modified in place)
   * @param fluid wash fluid
   * @param fluidRateKgHr wash fluid mass rate in kg/hr
   * @param durationHours wash duration in hours
   * @return a {@link WashResult} describing what was removed and the performance recovery
   */
  public WashResult wash(CompressorDeposit deposit, WashFluid fluid, double fluidRateKgHr, double durationHours) {
    Map<DepositMechanism, Double> removed = new EnumMap<>(DepositMechanism.class);
    double effBefore = deposit == null ? 1.0 : deposit.getEfficiencyMultiplier();
    double headBefore = deposit == null ? 1.0 : deposit.getHeadMultiplier();
    double totalRemoved = 0.0;
    if (deposit != null && fluid != null && fluidRateKgHr > 0.0 && durationHours > 0.0) {
      for (DepositMechanism mechanism : DepositMechanism.values()) {
        double solubility = fluid.getSolubility(mechanism);
        if (solubility <= 0.0) {
          continue;
        }
        double capacity = fluidRateKgHr * solubility * contactEfficiency * durationHours;
        double removedMass = deposit.removeDeposit(mechanism, capacity);
        if (removedMass > 0.0) {
          removed.put(mechanism, removedMass);
          totalRemoved += removedMass;
        }
      }
    }
    double effAfter = deposit == null ? 1.0 : deposit.getEfficiencyMultiplier();
    double headAfter = deposit == null ? 1.0 : deposit.getHeadMultiplier();
    double remaining = deposit == null ? 0.0 : deposit.getTotalDepositMass();
    double fluidUsed = fluidRateKgHr * durationHours;
    return new WashResult(fluid, durationHours, fluidUsed, removed, totalRemoved, effBefore, effAfter, headBefore,
        headAfter, remaining);
  }

  /**
   * Initial dissolution rate for a fluid acting on the current deposit (kg deposit/hr), summed over every mechanism the
   * fluid can dissolve that is currently present.
   *
   * @param deposit deposit inventory
   * @param fluid wash fluid
   * @param fluidRateKgHr wash fluid rate in kg/hr
   * @return dissolution rate in kg deposit / hr
   */
  public double dissolutionRateKgHr(CompressorDeposit deposit, WashFluid fluid, double fluidRateKgHr) {
    if (deposit == null || fluid == null || fluidRateKgHr <= 0.0) {
      return 0.0;
    }
    double rate = 0.0;
    for (DepositMechanism mechanism : DepositMechanism.values()) {
      if (fluid.dissolves(mechanism) && deposit.getDepositMass(mechanism) > 0.0) {
        rate += fluidRateKgHr * fluid.getSolubility(mechanism) * contactEfficiency;
      }
    }
    return rate;
  }

  /**
   * Screening estimate of the wash duration needed to remove a target deposit mass at a given wash rate (assumes the
   * initial dissolution rate; ignores depletion, so it is a lower bound).
   *
   * @param deposit deposit inventory
   * @param fluid wash fluid
   * @param fluidRateKgHr wash fluid rate in kg/hr
   * @param targetMassKg deposit mass to remove in kg
   * @return duration in hours (or {@link Double#POSITIVE_INFINITY} if the fluid cannot dissolve it)
   */
  public double hoursToRemoveMass(CompressorDeposit deposit, WashFluid fluid, double fluidRateKgHr,
      double targetMassKg) {
    double rate = dissolutionRateKgHr(deposit, fluid, fluidRateKgHr);
    if (rate <= 0.0) {
      return Double.POSITIVE_INFINITY;
    }
    return targetMassKg / rate;
  }

  /**
   * Screening estimate of the wash-fluid rate needed to remove a target deposit mass in a target duration.
   *
   * @param deposit deposit inventory
   * @param fluid wash fluid
   * @param targetMassKg deposit mass to remove in kg
   * @param durationHours available wash duration in hours
   * @return required wash-fluid rate in kg/hr (or {@link Double#POSITIVE_INFINITY} if not possible)
   */
  public double requiredFluidRateKgHr(CompressorDeposit deposit, WashFluid fluid, double targetMassKg,
      double durationHours) {
    if (deposit == null || fluid == null || durationHours <= 0.0) {
      return Double.POSITIVE_INFINITY;
    }
    double sumSolubility = 0.0;
    for (DepositMechanism mechanism : DepositMechanism.values()) {
      if (fluid.dissolves(mechanism) && deposit.getDepositMass(mechanism) > 0.0) {
        sumSolubility += fluid.getSolubility(mechanism);
      }
    }
    if (sumSolubility <= 0.0) {
      return Double.POSITIVE_INFINITY;
    }
    return targetMassKg / (durationHours * contactEfficiency * sumSolubility);
  }

  /**
   * Get the contact efficiency.
   *
   * @return contact efficiency (0-1)
   */
  public double getContactEfficiency() {
    return contactEfficiency;
  }

  /**
   * Set the contact efficiency (lower for online washing, higher for offline soak).
   *
   * @param contactEfficiency contact efficiency (0-1)
   */
  public void setContactEfficiency(double contactEfficiency) {
    this.contactEfficiency = Math.max(0.0, Math.min(1.0, contactEfficiency));
  }
}
