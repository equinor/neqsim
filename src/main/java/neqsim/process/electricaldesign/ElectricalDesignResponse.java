package neqsim.process.electricaldesign;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Response class for electrical design JSON export.
 *
 * <p>
 * Provides a structured representation of electrical design data for JSON serialization. Includes
 * equipment-level electrical parameters, motor data, VFD data, cable data, switchgear data, and
 * hazardous area classification.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ElectricalDesignResponse implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  // === Equipment identification ===
  private String equipmentName;
  private String equipmentType;

  // === Power summary ===
  private double shaftPowerKW;
  private double electricalInputKW;
  private double apparentPowerKVA;
  private double reactivePowerKVAR;
  private double powerFactor;
  private double totalLossesKW;

  // === Voltage and frequency ===
  private double ratedVoltageV;
  private double frequencyHz;
  private int phases;

  // === Component data ===
  private Map<String, Object> motorData;
  private Map<String, Object> vfdData;
  private Map<String, Object> powerCableData;
  private Map<String, Object> controlCableData;
  private Map<String, Object> switchgearData;
  private Map<String, Object> transformerData;
  private Map<String, Object> hazAreaData;

  /**
   * Default constructor.
   */
  public ElectricalDesignResponse() {}

  /**
   * Constructor from ElectricalDesign.
   *
   * @param design the electrical design object
   */
  public ElectricalDesignResponse(ElectricalDesign design) {
    populateFromElectricalDesign(design);
  }

  /**
   * Populate from an ElectricalDesign instance.
   *
   * @param design the electrical design object
   */
  public void populateFromElectricalDesign(ElectricalDesign design) {
    if (design == null) {
      return;
    }

    // Equipment identification
    if (design.getProcessEquipment() != null) {
      this.equipmentName = design.getProcessEquipment().getName();
      this.equipmentType = design.getProcessEquipment().getClass().getSimpleName();
    }

    // Power summary
    this.shaftPowerKW = design.getShaftPowerKW();
    this.electricalInputKW = design.getElectricalInputKW();
    this.apparentPowerKVA = design.getApparentPowerKVA();
    this.reactivePowerKVAR = design.getReactivePowerKVAR();
    this.powerFactor = design.getPowerFactor();
    this.totalLossesKW = design.getTotalElectricalLossesKW();

    // Voltage and frequency
    this.ratedVoltageV = design.getRatedVoltageV();
    this.frequencyHz = design.getFrequencyHz();
    this.phases = design.getPhases();

    // Component data
    if (design.getMotor() != null) {
      this.motorData = design.getMotor().toMap();
    }
    if (design.getVfd() != null) {
      this.vfdData = design.getVfd().toMap();
    }
    if (design.getPowerCable() != null) {
      this.powerCableData = design.getPowerCable().toMap();
    }
    if (design.getControlCable() != null) {
      this.controlCableData = design.getControlCable().toMap();
    }
    if (design.getSwitchgear() != null) {
      this.switchgearData = design.getSwitchgear().toMap();
    }
    if (design.getTransformer() != null) {
      this.transformerData = design.getTransformer().toMap();
    }
    if (design.getHazArea() != null) {
      this.hazAreaData = design.getHazArea().toMap();
    }
  }

  /**
   * Convert to JSON string.
   *
   * @return JSON representation
   */
  public String toJson() {
    Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting()
        .serializeNulls().create();
    return gson.toJson(this);
  }

  /**
   * Convert to compact JSON string.
   *
   * @return compact JSON string
   */
  public String toCompactJson() {
    Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
    return gson.toJson(this);
  }

  // === Getters ===

  /**
   * Get equipment name.
   *
   * @return equipment name
   */
  public String getEquipmentName() {
    return equipmentName;
  }

  /**
   * Get equipment type.
   *
   * @return equipment type
   */
  public String getEquipmentType() {
    return equipmentType;
  }

  /**
   * Get shaft power in kW.
   *
   * @return shaft power in kW
   */
  public double getShaftPowerKW() {
    return shaftPowerKW;
  }

  /**
   * Get electrical input in kW.
   *
   * @return electrical input in kW
   */
  public double getElectricalInputKW() {
    return electricalInputKW;
  }

  /**
   * Get apparent power in kVA.
   *
   * @return apparent power in kVA
   */
  public double getApparentPowerKVA() {
    return apparentPowerKVA;
  }

  /**
   * Get reactive power in kVAR.
   *
   * @return reactive power in kVAR
   */
  public double getReactivePowerKVAR() {
    return reactivePowerKVAR;
  }

  /**
   * Get power factor.
   *
   * @return power factor
   */
  public double getPowerFactor() {
    return powerFactor;
  }

  /**
   * Get total losses in kW.
   *
   * @return total losses in kW
   */
  public double getTotalLossesKW() {
    return totalLossesKW;
  }

  /**
   * Get motor data map.
   *
   * @return motor data
   */
  public Map<String, Object> getMotorData() {
    return motorData;
  }

  /**
   * Get VFD data map.
   *
   * @return VFD data
   */
  public Map<String, Object> getVfdData() {
    return vfdData;
  }

  /**
   * Get switchgear data map.
   *
   * @return switchgear data
   */
  public Map<String, Object> getSwitchgearData() {
    return switchgearData;
  }
}
