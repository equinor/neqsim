package neqsim.process.equipment.valve;

/**
 * Standard ISA-75 style control-valve inherent flow characteristics used by {@link ValveRangeabilityScreening}.
 *
 * @author NeqSim
 * @version 1.0
 */
public enum ValveTrimCharacteristic {
  /**
   * Linear trim: flow coefficient varies linearly with travel, subject to the valve rangeability (minimum controllable
   * flow fraction is {@code 1/rangeability}).
   */
  LINEAR,

  /**
   * Equal-percentage trim: equal increments of travel produce equal percentage changes in flow coefficient (the
   * defining ISA-75 relation {@code Cv(x) = Cv_max * R^(x-1)}).
   */
  EQUAL_PERCENTAGE,

  /**
   * Quick-opening trim, approximated with the commonly cited modified-parabolic relation
   * {@code Cv(x) = Cv_max * sqrt(x)} (see e.g. the Fisher Control Valve Handbook).
   */
  QUICK_OPENING;
}
