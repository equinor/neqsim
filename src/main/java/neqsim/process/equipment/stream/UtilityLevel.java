package neqsim.process.equipment.stream;

/**
 * Standard utility grades used to describe the quality of a thermal energy stream.
 *
 * @author NeqSim
 * @version 1.0
 */
public enum UtilityLevel {
  /** No utility grade has been specified. */
  UNSPECIFIED,
  /** High-pressure steam. */
  HIGH_PRESSURE_STEAM,
  /** Medium-pressure steam. */
  MEDIUM_PRESSURE_STEAM,
  /** Low-pressure steam. */
  LOW_PRESSURE_STEAM,
  /** Hot-oil utility. */
  HOT_OIL,
  /** Cooling-water utility. */
  COOLING_WATER,
  /** Chilled-water utility. */
  CHILLED_WATER,
  /** Refrigeration utility. */
  REFRIGERATION,
  /** Ambient or air-cooling utility. */
  AMBIENT_COOLING
}
