package neqsim.process.equipment.stream;

/**
 * Strategy used to select generation when an energy network has more offered supply than accepted demand.
 *
 * <p>
 * Demand allocation always retains the existing priority/proportional policy. The strategy applies independently to
 * normal producers and balancing generators, preserving the operational role of balancing equipment as reserve rather
 * than allowing it to displace normal generation.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public enum EnergyDispatchStrategy {
  /** Lower priority numbers dispatch first; equal-priority sources share proportionally. */
  PRIORITY_PROPORTIONAL,

  /** Lower marginal energy price dispatches first; equal-price and equal-priority sources share proportionally. */
  MINIMUM_COST,

  /**
   * Lower CO2-equivalent emission factor dispatches first; equal-factor and equal-priority sources share
   * proportionally.
   */
  MINIMUM_EMISSIONS
}
