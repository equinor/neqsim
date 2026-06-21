package neqsim.process.safety.api14c;

/**
 * Standard API RP 14C / ISO 10418 protective device types.
 *
 * @author ESOL
 * @version 1.0
 */
public enum Api14cDeviceType {

  /** Pressure Safety High (high pressure shutdown sensor). */
  PSH("Pressure Safety High"),

  /** Pressure Safety Low (low pressure shutdown sensor). */
  PSL("Pressure Safety Low"),

  /** Level Safety High (high level shutdown sensor). */
  LSH("Level Safety High"),

  /** Level Safety Low (low level shutdown sensor). */
  LSL("Level Safety Low"),

  /** Temperature Safety High. */
  TSH("Temperature Safety High"),

  /** Temperature Safety Low. */
  TSL("Temperature Safety Low"),

  /** Underwater Safety Valve / surface controlled subsurface safety valve. */
  USV("Underwater / subsurface safety valve"),

  /** Shutdown Valve (ESV at boundary). */
  SDV("Shutdown valve (ESV)"),

  /** Blowdown valve. */
  BDV("Blowdown valve"),

  /** Pressure Safety Valve (PSV / relief valve). */
  PSV("Pressure safety valve / relief valve"),

  /** Flow Safety Valve (check valve preventing reverse flow). */
  FSV("Flow safety valve (check valve)"),

  /** Fire detection sensor. */
  FIRE("Fire detection"),

  /** Combustible gas detection sensor. */
  GAS("Combustible gas detection");

  private final String description;

  Api14cDeviceType(String description) {
    this.description = description;
  }

  /**
   * @return human readable device description
   */
  public String getDescription() {
    return description;
  }
}
