package neqsim.process.equipment.compressor;

/**
 * Enumeration representing the operating states of a compressor in dynamic simulations.
 *
 * <p>
 * This enum provides explicit state tracking for compressor control logic, enabling proper handling
 * of startup sequences, shutdown procedures, and protective actions.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public enum CompressorState {

  /**
   * Compressor is stopped and not rotating. No flow or pressure rise. Ready for startup sequence.
   */
  STOPPED("Stopped", "Compressor is stopped and not rotating"),

  /**
   * Compressor is in the startup sequence. Speed is ramping up according to startup profile.
   * Anti-surge valve may be fully open.
   */
  STARTING("Starting", "Compressor is ramping up during startup sequence"),

  /**
   * Normal operating state. Compressor is running within design envelope. Speed and flow are within
   * acceptable limits.
   */
  RUNNING("Running", "Compressor is operating normally"),

  /**
   * Surge protection is active. Operating point is near or at surge line. Anti-surge valve is
   * modulating to protect compressor.
   */
  SURGE_PROTECTION("Surge Protection", "Anti-surge protection is active"),

  /**
   * Speed is limited by driver power or mechanical constraints. Compressor cannot achieve requested
   * operating point.
   */
  SPEED_LIMITED("Speed Limited", "Speed is limited by driver or mechanical constraints"),

  /**
   * Compressor is in controlled shutdown sequence. Speed is ramping down according to shutdown
   * profile.
   */
  SHUTDOWN("Shutdown", "Compressor is in controlled shutdown sequence"),

  /**
   * System is depressurizing. Compressor may be coasting down. Used during emergency shutdown or
   * planned depressurization.
   */
  DEPRESSURIZING("Depressurizing", "System is depressurizing"),

  /**
   * Emergency shutdown state. Compressor has tripped due to protective action. Requires operator
   * acknowledgment before restart.
   */
  TRIPPED("Tripped", "Emergency shutdown - compressor has tripped"),

  /**
   * Compressor is on standby, ready for quick start. May be pressurized but not rotating.
   */
  STANDBY("Standby", "Compressor is on standby, ready for quick start");

  private final String displayName;
  private final String description;

  /**
   * Constructor for CompressorState.
   *
   * @param displayName the human-readable name of the state
   * @param description a description of what the state means
   */
  CompressorState(String displayName, String description) {
    this.displayName = displayName;
    this.description = description;
  }

  /**
   * Get the display name of this state.
   *
   * @return the human-readable name
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Get the description of this state.
   *
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Check if the compressor is in a running state (RUNNING or SURGE_PROTECTION or SPEED_LIMITED).
   *
   * @return true if compressor is operational
   */
  public boolean isOperational() {
    return this == RUNNING || this == SURGE_PROTECTION || this == SPEED_LIMITED;
  }

  /**
   * Check if the compressor is in a transitional state (STARTING, SHUTDOWN, DEPRESSURIZING).
   *
   * @return true if compressor is in transition
   */
  public boolean isTransitional() {
    return this == STARTING || this == SHUTDOWN || this == DEPRESSURIZING;
  }

  /**
   * Check if the compressor is stopped (STOPPED, TRIPPED, or STANDBY).
   *
   * @return true if compressor is not rotating
   */
  public boolean isStopped() {
    return this == STOPPED || this == TRIPPED || this == STANDBY;
  }

  /**
   * Check if the compressor can be started from this state.
   *
   * @return true if startup is allowed
   */
  public boolean canStart() {
    return this == STOPPED || this == STANDBY;
  }

  /**
   * Check if the compressor requires operator acknowledgment before restart.
   *
   * @return true if acknowledgment is required
   */
  public boolean requiresAcknowledgment() {
    return this == TRIPPED;
  }

  @Override
  public String toString() {
    return displayName;
  }
}
