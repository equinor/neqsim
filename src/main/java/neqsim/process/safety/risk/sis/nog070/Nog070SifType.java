package neqsim.process.safety.risk.sis.nog070;

/**
 * Standard Safety Instrumented Function types catalogued by the Norwegian Oil &amp; Gas guideline 070 (NOG 070,
 * "Application of IEC 61508 and IEC 61511 in the Norwegian petroleum industry").
 *
 * <p>
 * NOG 070 prescribes a minimum SIL for each recognised SIF on the Norwegian Continental Shelf. Selecting a SIL below
 * the minimum from this catalogue is not accepted by Equinor / the operator without a documented deviation.
 * </p>
 *
 * <p>
 * The list below covers the most frequently referenced SIF types from Appendix A of NOG 070 (2020 revision). Project
 * specific SIFs should map to the closest type or use {@link #CUSTOM} together with an explicit minimum SIL.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public enum Nog070SifType {

  /** Process shutdown (PSD) of a topside segment on high/low process variable. */
  PSD_PROCESS_SEGMENT("PSD - Process segment shutdown"),

  /** Emergency shutdown valve (ESV) closing a topside isolation boundary. */
  ESD_TOPSIDE_ISOLATION("ESD - Topside isolation"),

  /** Subsea isolation valve closure (DHSV / surface controlled subsurface safety valve). */
  ESD_SUBSEA_ISOLATION("ESD - Subsea isolation / DHSV"),

  /** High Integrity Pressure Protection System on a downstream pipeline or vessel. */
  HIPPS_PIPELINE("HIPPS - Pipeline overpressure protection"),

  /** Blowdown function (BDV) on a hydrocarbon segment. */
  BLOWDOWN_HYDROCARBON_SEGMENT("Blowdown - Hydrocarbon segment"),

  /** Fire and gas detection - confirmed gas leak resulting in ESD/blowdown. */
  FG_GAS_DETECTION_ESD("F&amp;G - Confirmed gas detection -&gt; ESD"),

  /** Fire and gas detection - confirmed fire resulting in ESD/blowdown. */
  FG_FIRE_DETECTION_ESD("F&amp;G - Confirmed fire detection -&gt; ESD"),

  /** Fire and gas detection - HVAC shutdown / damper closure on smoke or gas. */
  FG_HVAC_SHUTDOWN("F&amp;G - HVAC shutdown"),

  /** Deluge / firewater release on confirmed fire. */
  FG_DELUGE_RELEASE("F&amp;G - Deluge release"),

  /** Compressor anti-surge shutdown on detected surge. */
  PSD_COMPRESSOR_ANTI_SURGE("PSD - Compressor anti-surge trip"),

  /** Compressor or pump shutdown on high vibration. */
  PSD_HIGH_VIBRATION("PSD - High vibration trip"),

  /** High level shutdown of separator / scrubber preventing liquid carryover. */
  PSD_HIGH_LEVEL("PSD - High level shutdown"),

  /** Low low level shutdown preventing gas blow-by. */
  PSD_LOW_LOW_LEVEL("PSD - Low low level shutdown"),

  /** Fired heater / boiler trip on burner management system fault. */
  PSD_BURNER_MANAGEMENT("PSD - Burner management system trip"),

  /** Riser ESD - emergency shutdown on the riser ESV. */
  ESD_RISER("ESD - Riser ESV closure"),

  /** Wellhead master valve / wing valve closure on production tree. */
  ESD_WELLHEAD("ESD - Wellhead master / wing valve closure"),

  /** Loading hose / loading arm emergency release coupling actuation. */
  ESD_LOADING_RELEASE("ESD - Loading emergency release"),

  /** Other / custom SIF; combined with an explicit minimum SIL. */
  CUSTOM("Custom / project specific");

  private final String description;

  Nog070SifType(String description) {
    this.description = description;
  }

  /**
   * Returns a human readable description of the SIF type.
   *
   * @return description string
   */
  public String getDescription() {
    return description;
  }
}
