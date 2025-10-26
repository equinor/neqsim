package neqsim.process.equipment.valve;

/**
 * Enumerates the available dynamic travel models that can be applied to valves.
 */
public enum ValveTravelModel {
  /**
   * No dynamic travel is applied; the valve position changes instantaneously.
   */
  NONE,

  /**
   * The valve position changes with a limited slew rate corresponding to a defined travel time.
   */
  LINEAR_RATE_LIMIT,

  /**
   * The valve position follows a first order response with a configurable time constant.
   */
  FIRST_ORDER_LAG;
}
