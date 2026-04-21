package neqsim.process.diagnostics;

/**
 * Enumeration of trip cause categories for process equipment.
 *
 * <p>
 * Each value represents a high-level category of trip initiator. The {@link TripEventDetector} maps
 * specific alarm and equipment state changes to these categories so that the
 * {@link RootCauseAnalyzer} can select relevant hypotheses.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public enum TripType {

  /** Compressor surge detected or anti-surge trip activated. */
  COMPRESSOR_SURGE("Compressor Surge", "Compressor tripped due to surge condition"),

  /** Pressure exceeded HIHI limit or PSV/HIPPS activated. */
  HIGH_PRESSURE("High Pressure", "Trip initiated by high pressure condition"),

  /** Separator or vessel liquid level exceeded HIHI limit. */
  HIGH_LEVEL("High Level", "Trip initiated by high liquid level"),

  /** Process temperature exceeded HIHI limit. */
  HIGH_TEMPERATURE("High Temperature", "Trip initiated by high temperature"),

  /** Flow dropped below LOLO limit or loss of flow detected. */
  LOW_FLOW("Low Flow", "Trip initiated by low or lost flow"),

  /** Emergency shutdown system activated. */
  ESD_ACTIVATED("ESD Activated", "Emergency shutdown system activated"),

  /** Manual trip initiated by operator. */
  MANUAL("Manual Trip", "Trip initiated manually by operator"),

  /** Instrument or sensor failure detected. */
  INSTRUMENT_FAILURE("Instrument Failure", "Trip caused by instrument malfunction"),

  /** Loss of electrical power or driver failure. */
  POWER_LOSS("Power Loss", "Trip caused by power or driver failure"),

  /** Compressor driver overloaded. */
  DRIVER_OVERLOAD("Driver Overload", "Trip caused by driver power limit exceeded"),

  /** High vibration on rotating equipment. */
  HIGH_VIBRATION("High Vibration", "Trip initiated by high vibration"),

  /** Unknown or unclassified trip cause. */
  UNKNOWN("Unknown", "Trip cause could not be classified");

  private final String displayName;
  private final String description;

  /**
   * Constructs a TripType enum constant.
   *
   * @param displayName human-readable display name
   * @param description longer description of the trip cause
   */
  TripType(String displayName, String description) {
    this.displayName = displayName;
    this.description = description;
  }

  /**
   * Returns the human-readable display name.
   *
   * @return display name string
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Returns a description of this trip type.
   *
   * @return description string
   */
  public String getDescription() {
    return description;
  }

  /**
   * Checks whether this trip type is related to rotating equipment.
   *
   * @return true if the trip is related to compressor or driver issues
   */
  public boolean isRotatingEquipmentRelated() {
    return this == COMPRESSOR_SURGE || this == DRIVER_OVERLOAD || this == HIGH_VIBRATION;
  }

  /**
   * Checks whether this trip type is safety-system initiated.
   *
   * @return true if the trip was initiated by a safety system
   */
  public boolean isSafetyInitiated() {
    return this == ESD_ACTIVATED || this == HIGH_PRESSURE;
  }

  @Override
  public String toString() {
    return displayName;
  }
}
