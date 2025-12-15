package neqsim.process.safety;

/**
 * Enumeration of initiating events for safety scenarios.
 *
 * <p>
 * Initiating events are the root causes that trigger a safety scenario. They can be categorized as:
 * <ul>
 * <li>Process upsets (blocked outlets, utility loss)</li>
 * <li>Equipment failures (PSV lift, rupture)</li>
 * <li>External events (fire exposure)</li>
 * <li>Human errors (incorrect operation)</li>
 * </ul>
 *
 * <p>
 * This enum is used with {@link ProcessSafetyScenario} to define the type of initiating event and
 * enable automatic configuration of appropriate boundary conditions and response logic.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 * @see ProcessSafetyScenario
 */
public enum InitiatingEvent {

  /** Emergency Shutdown activation - controlled depressurization via blowdown. */
  ESD("Emergency Shutdown", "Controlled depressurization via ESD valve"),

  /** Pressure Safety Valve lift - uncontrolled relief through PSV. */
  PSV_LIFT("PSV Lift", "Pressure safety valve has opened"),

  /** Vessel or pipe rupture - catastrophic failure. */
  RUPTURE("Rupture", "Catastrophic vessel or pipe rupture"),

  /** Small leak - hole diameter less than 10mm. */
  LEAK_SMALL("Small Leak", "Leak with hole diameter < 10mm"),

  /** Medium leak - hole diameter between 10-50mm. */
  LEAK_MEDIUM("Medium Leak", "Leak with hole diameter 10-50mm"),

  /** Large leak - hole diameter greater than 50mm. */
  LEAK_LARGE("Large Leak", "Leak with hole diameter > 50mm"),

  /** Full bore rupture - complete pipe or nozzle failure. */
  FULL_BORE_RUPTURE("Full Bore Rupture", "Complete failure of pipe or nozzle"),

  /** Blocked outlet - loss of discharge path. */
  BLOCKED_OUTLET("Blocked Outlet", "Discharge path blocked causing pressure buildup"),

  /** Loss of utility - cooling water, instrument air, power, etc. */
  UTILITY_LOSS("Utility Loss", "Loss of cooling water, instrument air, or power"),

  /** Fire exposure - external fire impingement. */
  FIRE_EXPOSURE("Fire Exposure", "External fire impingement on equipment"),

  /** Runaway reaction - exothermic reaction out of control. */
  RUNAWAY_REACTION("Runaway Reaction", "Exothermic reaction exceeding heat removal capacity"),

  /** Thermal expansion - blocked-in liquid heating. */
  THERMAL_EXPANSION("Thermal Expansion", "Liquid trapped and heated causing overpressure"),

  /** Tube rupture - heat exchanger tube failure. */
  TUBE_RUPTURE("Tube Rupture", "Heat exchanger tube failure"),

  /** Control valve failure - stuck open or closed. */
  CONTROL_VALVE_FAILURE("Control Valve Failure", "Control valve fails in wrong position"),

  /** Compressor surge - compressor operating in surge region. */
  COMPRESSOR_SURGE("Compressor Surge", "Compressor operating in unstable surge region"),

  /** Loss of containment - generic leak or spill. */
  LOSS_OF_CONTAINMENT("Loss of Containment", "Generic release of hazardous material"),

  /** Manual intervention - operator-initiated action. */
  MANUAL_INTERVENTION("Manual Intervention", "Operator-initiated emergency action");

  private final String displayName;
  private final String description;

  /**
   * Creates an initiating event with name and description.
   *
   * @param displayName human-readable name
   * @param description detailed description of the event
   */
  InitiatingEvent(String displayName, String description) {
    this.displayName = displayName;
    this.description = description;
  }

  /**
   * Gets the human-readable display name.
   *
   * @return display name
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Gets the detailed description.
   *
   * @return description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Checks if this event typically results in a release to atmosphere.
   *
   * @return true if release to atmosphere is expected
   */
  public boolean isReleaseEvent() {
    return this == LEAK_SMALL || this == LEAK_MEDIUM || this == LEAK_LARGE
        || this == FULL_BORE_RUPTURE || this == RUPTURE || this == PSV_LIFT
        || this == LOSS_OF_CONTAINMENT;
  }

  /**
   * Checks if this event requires fire case analysis.
   *
   * @return true if fire case should be considered
   */
  public boolean requiresFireAnalysis() {
    return this == FIRE_EXPOSURE;
  }

  /**
   * Checks if this event triggers emergency depressurization.
   *
   * @return true if blowdown should be initiated
   */
  public boolean triggersDepressurization() {
    return this == ESD || this == FIRE_EXPOSURE;
  }

  /**
   * Gets the typical hole diameter range for leak events [mm].
   *
   * @return array of [min, max] hole diameter in mm, or null for non-leak events
   */
  public double[] getTypicalHoleDiameter() {
    switch (this) {
      case LEAK_SMALL:
        return new double[] {1.0, 10.0};
      case LEAK_MEDIUM:
        return new double[] {10.0, 50.0};
      case LEAK_LARGE:
        return new double[] {50.0, 150.0};
      case FULL_BORE_RUPTURE:
        return new double[] {150.0, 500.0};
      default:
        return null;
    }
  }

  @Override
  public String toString() {
    return displayName;
  }
}
