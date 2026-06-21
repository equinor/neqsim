package neqsim.process.safety.hazid;

/**
 * ISO 17776 Major Accident Hazard categories for offshore production installations.
 *
 * @author ESOL
 * @version 1.0
 */
public enum MahType {

  /** Topside hydrocarbon release (gas, oil, condensate). */
  TOPSIDE_HYDROCARBON_RELEASE("Topside hydrocarbon release"),

  /** Riser / sub-sea pipeline leak. */
  RISER_LEAK("Riser or sub-sea pipeline leak"),

  /** Uncontrolled well blowout (drilling, completion, or production). */
  WELL_BLOWOUT("Uncontrolled well blowout"),

  /** Structural collapse of the installation (fatigue, overload, foundation failure). */
  STRUCTURAL_COLLAPSE("Structural collapse"),

  /** Dropped object / lifted load impact. */
  DROPPED_OBJECT("Dropped object impact"),

  /** Helicopter accident (approach, take-off, deck operations). */
  HELICOPTER_LOSS("Helicopter loss"),

  /** Ship collision (passing-vessel or attendant-vessel). */
  SHIP_COLLISION("Passing or attendant vessel collision"),

  /** Process fire and / or explosion. */
  FIRE_EXPLOSION("Process fire / explosion"),

  /** Toxic substance release (H2S, mercury, MEG, etc.). */
  TOXIC_RELEASE("Toxic substance release"),

  /** Loss of buoyancy / stability (floaters). */
  LOSS_OF_BUOYANCY("Loss of buoyancy or stability"),

  /** Severe weather event beyond design (hurricane, ice). */
  EXTREME_WEATHER("Extreme weather beyond design");

  private final String description;

  MahType(String description) {
    this.description = description;
  }

  /**
   * @return MAH description
   */
  public String getDescription() {
    return description;
  }
}
