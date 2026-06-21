package neqsim.process.safety.api14c;

/**
 * API RP 14C / ISO 10418 equipment categories used to look up the prescribed safety-device set.
 *
 * <p>
 * The SAFE-chart concept assigns each piece of offshore process equipment to a category, and the standard then defines
 * which protective devices (PSH, PSL, LSH, LSL, TSH, USV, SDV, BDV, PSV, FSV) are mandatory.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public enum Api14cEquipmentCategory {

  /** Pressure vessel (separator, scrubber, knock-out drum, flash drum). */
  PRESSURE_VESSEL("Pressure vessel - separator / scrubber / KO drum"),

  /** Atmospheric / low-pressure tank. */
  ATMOSPHERIC_VESSEL("Atmospheric or low-pressure tank"),

  /** Fired heater or process boiler. */
  FIRED_VESSEL("Fired vessel - heater / boiler"),

  /** Pipeline segment between protective devices. */
  PIPELINE_SEGMENT("Pipeline segment"),

  /** Centrifugal / reciprocating compressor unit. */
  COMPRESSOR("Compressor"),

  /** Centrifugal / positive displacement pump unit. */
  PUMP("Pump"),

  /** Heat exchanger (shell-and-tube, plate, air cooled). */
  HEAT_EXCHANGER("Heat exchanger"),

  /** Wellhead and Christmas tree. */
  WELLHEAD("Wellhead / Christmas tree");

  private final String description;

  Api14cEquipmentCategory(String description) {
    this.description = description;
  }

  /**
   * @return human readable category description
   */
  public String getDescription() {
    return description;
  }
}
