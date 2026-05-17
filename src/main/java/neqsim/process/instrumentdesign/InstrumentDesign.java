package neqsim.process.instrumentdesign;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import neqsim.process.equipment.ProcessEquipmentInterface;

/**
 * Base class for instrument design of process equipment.
 *
 * <p>
 * Mirrors the {@link neqsim.process.electricaldesign.ElectricalDesign} and
 * {@link neqsim.process.mechanicaldesign.MechanicalDesign} patterns. Each piece of process
 * equipment can have an associated instrument design that determines which instruments are
 * required, their specifications, I/O requirements, and estimated costs.
 * </p>
 *
 * <p>
 * The instrument design follows ISA-5.1 for instrument identification and tagging, IEC 61508 / IEC
 * 61511 for safety instrumented systems (SIS), and ISA-18.2 for alarm management.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class InstrumentDesign implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** The process equipment this instrument design belongs to. */
  private ProcessEquipmentInterface processEquipment;

  /** The list of instruments for this equipment. */
  private InstrumentList instrumentList;

  /** Hazardous area zone classification for the equipment location. */
  private String hazardousAreaZone = "Zone 1";

  /** Explosion protection concept (e.g. "Ex d", "Ex e", "Ex ia"). */
  private String protectionConcept = "Ex ia";

  /** Design standard for instrumentation (e.g. "IEC", "ISA"). */
  private String instrumentStandard = "IEC";

  /** Whether to include safety instrumented function (SIF) instruments. */
  private boolean includeSafetyInstruments = true;

  /** Default SIL level for safety instruments. */
  private int defaultSilLevel = 2;

  /**
   * Constructor for InstrumentDesign.
   *
   * @param processEquipment the process equipment this design belongs to
   */
  public InstrumentDesign(ProcessEquipmentInterface processEquipment) {
    this.processEquipment = processEquipment;
    this.instrumentList = new InstrumentList(processEquipment.getName());
  }

  /**
   * Run the instrument design calculation.
   *
   * <p>
   * Determines the required instruments based on the equipment type and process conditions.
   * Subclasses override this to add equipment-specific instruments.
   * </p>
   */
  public void calcDesign() {
    readDesignSpecifications();
  }

  /**
   * Read design specifications from data sources.
   *
   * <p>
   * Subclasses can override to load equipment-specific instrument specifications.
   * </p>
   */
  public void readDesignSpecifications() {}

  /**
   * Get the instrument list.
   *
   * @return the instrument list
   */
  public InstrumentList getInstrumentList() {
    return instrumentList;
  }

  /**
   * Get the total I/O count for this equipment.
   *
   * @return total I/O count
   */
  public int getTotalIOCount() {
    return instrumentList.getTotalIOCount();
  }

  /**
   * Get the total estimated instrument cost in USD.
   *
   * @return estimated cost in USD
   */
  public double getEstimatedCostUSD() {
    return instrumentList.getTotalCostUSD();
  }

  /**
   * Serialize the instrument design to JSON.
   *
   * @return JSON string with all instrument design data
   */
  public String toJson() {
    InstrumentDesignResponse response = new InstrumentDesignResponse(this);
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(JsonParser.parseString(response.toJson()));
  }

  // === Getters and Setters ===

  /**
   * Get the process equipment.
   *
   * @return the process equipment
   */
  public ProcessEquipmentInterface getProcessEquipment() {
    return processEquipment;
  }

  /**
   * Get hazardous area zone.
   *
   * @return hazardous area zone classification
   */
  public String getHazardousAreaZone() {
    return hazardousAreaZone;
  }

  /**
   * Set hazardous area zone.
   *
   * @param hazardousAreaZone hazardous area zone classification
   */
  public void setHazardousAreaZone(String hazardousAreaZone) {
    this.hazardousAreaZone = hazardousAreaZone;
  }

  /**
   * Get explosion protection concept.
   *
   * @return protection concept
   */
  public String getProtectionConcept() {
    return protectionConcept;
  }

  /**
   * Set explosion protection concept.
   *
   * @param protectionConcept protection concept (e.g. "Ex ia", "Ex d")
   */
  public void setProtectionConcept(String protectionConcept) {
    this.protectionConcept = protectionConcept;
  }

  /**
   * Get instrument standard.
   *
   * @return instrument standard
   */
  public String getInstrumentStandard() {
    return instrumentStandard;
  }

  /**
   * Set instrument standard.
   *
   * @param instrumentStandard instrument standard (e.g. "IEC", "ISA")
   */
  public void setInstrumentStandard(String instrumentStandard) {
    this.instrumentStandard = instrumentStandard;
  }

  /**
   * Check if safety instruments are included.
   *
   * @return true if SIF instruments are included
   */
  public boolean isIncludeSafetyInstruments() {
    return includeSafetyInstruments;
  }

  /**
   * Set whether to include safety instruments.
   *
   * @param includeSafetyInstruments true to include SIF instruments
   */
  public void setIncludeSafetyInstruments(boolean includeSafetyInstruments) {
    this.includeSafetyInstruments = includeSafetyInstruments;
  }

  /**
   * Get default SIL level for safety instruments.
   *
   * @return SIL level (1-3)
   */
  public int getDefaultSilLevel() {
    return defaultSilLevel;
  }

  /**
   * Set default SIL level for safety instruments.
   *
   * @param defaultSilLevel SIL level (1-3)
   */
  public void setDefaultSilLevel(int defaultSilLevel) {
    this.defaultSilLevel = defaultSilLevel;
  }
}
