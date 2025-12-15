package neqsim.process.fielddevelopment.facility;

/**
 * Pre-defined facility block types for rapid facility assembly.
 *
 * <p>
 * Each block type represents a common process unit or module that can be assembled into a complete
 * facility using the {@link FacilityBuilder}.
 *
 * @author ESOL
 * @version 1.0
 * @see FacilityBuilder
 */
public enum BlockType {
  /**
   * Inlet separation - initial phase separation at arrival pressure. Handles slug catching and
   * initial gas/liquid separation.
   */
  INLET_SEPARATION("Inlet Separation", "Initial phase separation"),

  /**
   * Two-phase separator - gas/liquid separation only.
   */
  TWO_PHASE_SEPARATOR("Two-Phase Separator", "Gas/liquid separation"),

  /**
   * Three-phase separator - gas/oil/water separation.
   */
  THREE_PHASE_SEPARATOR("Three-Phase Separator", "Gas/oil/water separation"),

  /**
   * Gas compression - multi-stage compression with intercooling.
   */
  COMPRESSION("Compression", "Gas compression train"),

  /**
   * TEG dehydration - glycol-based water removal.
   */
  TEG_DEHYDRATION("TEG Dehydration", "Triethylene glycol dehydration"),

  /**
   * MEG regeneration - monoethylene glycol regeneration.
   */
  MEG_REGENERATION("MEG Regeneration", "MEG reclamation and regeneration"),

  /**
   * CO2 removal via membrane.
   */
  CO2_REMOVAL_MEMBRANE("CO2 Membrane", "Membrane-based CO2 removal"),

  /**
   * CO2 removal via amine absorption.
   */
  CO2_REMOVAL_AMINE("CO2 Amine", "Amine-based CO2 absorption"),

  /**
   * H2S removal and sulfur recovery.
   */
  H2S_REMOVAL("H2S Removal", "Hydrogen sulfide removal"),

  /**
   * NGL recovery - extraction of C2+ components.
   */
  NGL_RECOVERY("NGL Recovery", "Natural gas liquids extraction"),

  /**
   * Dew point control - hydrocarbon dew point adjustment.
   */
  DEW_POINT_CONTROL("Dew Point Control", "Hydrocarbon dew point adjustment"),

  /**
   * Export gas conditioning - final spec adjustment for export.
   */
  EXPORT_CONDITIONING("Export Conditioning", "Final gas conditioning for export"),

  /**
   * Oil stabilization - flash separation for oil stabilization.
   */
  OIL_STABILIZATION("Oil Stabilization", "Crude oil stabilization"),

  /**
   * Water treatment - produced water handling and treatment.
   */
  WATER_TREATMENT("Water Treatment", "Produced water treatment"),

  /**
   * Subsea boosting - subsea multiphase pumping.
   */
  SUBSEA_BOOSTING("Subsea Boosting", "Subsea multiphase pump"),

  /**
   * Gas cooling - gas aftercooler for temperature control.
   */
  GAS_COOLING("Gas Cooling", "Gas cooling/aftercooling"),

  /**
   * Heat exchange network - process heat integration.
   */
  HEAT_EXCHANGE("Heat Exchange", "Process heat integration"),

  /**
   * Flare system - emergency and routine flaring.
   */
  FLARE_SYSTEM("Flare System", "Flare and vent system"),

  /**
   * Power generation - gas turbine or power from shore interface.
   */
  POWER_GENERATION("Power Generation", "Power generation system");

  private final String displayName;
  private final String description;

  BlockType(String displayName, String description) {
    this.displayName = displayName;
    this.description = description;
  }

  /**
   * Gets the display name for UI purposes.
   *
   * @return display name
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Gets the description.
   *
   * @return description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Checks if this block type consumes significant power.
   *
   * @return true if power consumer
   */
  public boolean isPowerConsumer() {
    return this == COMPRESSION || this == SUBSEA_BOOSTING || this == MEG_REGENERATION;
  }

  /**
   * Checks if this block type produces emissions.
   *
   * @return true if emissions source
   */
  public boolean isEmissionSource() {
    return this == POWER_GENERATION || this == FLARE_SYSTEM || this == MEG_REGENERATION;
  }

  /**
   * Checks if this block requires significant CAPEX.
   *
   * @return true if high CAPEX item
   */
  public boolean isHighCapex() {
    return this == COMPRESSION || this == CO2_REMOVAL_AMINE || this == NGL_RECOVERY
        || this == SUBSEA_BOOSTING;
  }
}
