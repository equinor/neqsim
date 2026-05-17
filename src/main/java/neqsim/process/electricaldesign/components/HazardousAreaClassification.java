package neqsim.process.electricaldesign.components;

import com.google.gson.GsonBuilder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Model for hazardous area classification of process equipment locations.
 *
 * <p>
 * Supports IEC 60079 / ATEX zone classification (Zone 0, 1, 2 for gas, Zone 20, 21, 22 for dust)
 * and API RP 500/505 classification. Determines required Ex protection type for electrical
 * equipment in classified areas.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class HazardousAreaClassification implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private String zone = "Zone 2";
  private String gasGroup = "IIA";
  private String temperatureClass = "T3";
  private String classificationStandard = "IECEx";
  private String requiredExProtection = "Ex e";
  private String equipmentProtectionLevel = "Gb";
  private String dustGroup = "";

  /**
   * Determine the hazardous area classification for a given equipment type.
   *
   * <p>
   * Sets the zone, gas group, temperature class and required Ex protection level based on the
   * equipment type and process fluid properties.
   * </p>
   *
   * @param equipmentType type of process equipment (Compressor, Pump, Separator, etc.)
   * @param containsHydrocarbons whether process fluid contains hydrocarbons
   * @param maxSurfaceTempC maximum surface temperature in degrees C
   */
  public void classify(String equipmentType, boolean containsHydrocarbons,
      double maxSurfaceTempC) {
    if (!containsHydrocarbons) {
      zone = "Safe area";
      requiredExProtection = "None";
      equipmentProtectionLevel = "None";
      return;
    }

    // Determine zone based on equipment type
    if ("Compressor".equals(equipmentType) || "Pump".equals(equipmentType)) {
      zone = "Zone 1";
      equipmentProtectionLevel = "Gb";
    } else if ("Separator".equals(equipmentType) || "Distillation".equals(equipmentType)) {
      zone = "Zone 1";
      equipmentProtectionLevel = "Gb";
    } else if ("HeatExchanger".equals(equipmentType) || "Cooler".equals(equipmentType)) {
      zone = "Zone 2";
      equipmentProtectionLevel = "Gc";
    } else if ("Pipeline".equals(equipmentType)) {
      zone = "Zone 2";
      equipmentProtectionLevel = "Gc";
    } else {
      zone = "Zone 2";
      equipmentProtectionLevel = "Gc";
    }

    // Determine temperature class based on max surface temperature
    if (maxSurfaceTempC <= 85) {
      temperatureClass = "T6";
    } else if (maxSurfaceTempC <= 100) {
      temperatureClass = "T5";
    } else if (maxSurfaceTempC <= 135) {
      temperatureClass = "T4";
    } else if (maxSurfaceTempC <= 200) {
      temperatureClass = "T3";
    } else if (maxSurfaceTempC <= 300) {
      temperatureClass = "T2";
    } else {
      temperatureClass = "T1";
    }

    // Determine required Ex protection based on zone
    if ("Zone 0".equals(zone)) {
      requiredExProtection = "Ex ia";
    } else if ("Zone 1".equals(zone)) {
      requiredExProtection = "Ex d";
    } else if ("Zone 2".equals(zone)) {
      requiredExProtection = "Ex e";
    }
  }

  /**
   * Get the full Ex marking string.
   *
   * @return Ex marking (e.g. "Ex d IIB T3 Gb")
   */
  public String getExMarking() {
    if ("Safe area".equals(zone)) {
      return "None";
    }
    return requiredExProtection + " " + gasGroup + " " + temperatureClass + " "
        + equipmentProtectionLevel;
  }

  /**
   * Serialize to JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    Map<String, Object> map = toMap();
    return new GsonBuilder().setPrettyPrinting().create().toJson(map);
  }

  /**
   * Convert to a map.
   *
   * @return map of classification parameters
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("zone", zone);
    map.put("gasGroup", gasGroup);
    map.put("temperatureClass", temperatureClass);
    map.put("classificationStandard", classificationStandard);
    map.put("requiredExProtection", requiredExProtection);
    map.put("equipmentProtectionLevel", equipmentProtectionLevel);
    map.put("exMarking", getExMarking());
    return map;
  }

  // === Getters and Setters ===

  /**
   * Get the zone classification.
   *
   * @return zone (Zone 0, Zone 1, Zone 2, or Safe area)
   */
  public String getZone() {
    return zone;
  }

  /**
   * Set the zone classification.
   *
   * @param zone zone classification
   */
  public void setZone(String zone) {
    this.zone = zone;
  }

  /**
   * Get the gas group.
   *
   * @return gas group (IIA, IIB, IIC)
   */
  public String getGasGroup() {
    return gasGroup;
  }

  /**
   * Set the gas group.
   *
   * @param gasGroup gas group
   */
  public void setGasGroup(String gasGroup) {
    this.gasGroup = gasGroup;
  }

  /**
   * Get the temperature class.
   *
   * @return temperature class (T1-T6)
   */
  public String getTemperatureClass() {
    return temperatureClass;
  }

  /**
   * Set the temperature class.
   *
   * @param temperatureClass temperature class
   */
  public void setTemperatureClass(String temperatureClass) {
    this.temperatureClass = temperatureClass;
  }

  /**
   * Get the classification standard.
   *
   * @return classification standard (IECEx, ATEX, NEC)
   */
  public String getClassificationStandard() {
    return classificationStandard;
  }

  /**
   * Set the classification standard.
   *
   * @param classificationStandard classification standard
   */
  public void setClassificationStandard(String classificationStandard) {
    this.classificationStandard = classificationStandard;
  }

  /**
   * Get the required Ex protection type.
   *
   * @return Ex protection type
   */
  public String getRequiredExProtection() {
    return requiredExProtection;
  }

  /**
   * Set the required Ex protection type.
   *
   * @param requiredExProtection Ex protection type
   */
  public void setRequiredExProtection(String requiredExProtection) {
    this.requiredExProtection = requiredExProtection;
  }

  /**
   * Get the equipment protection level.
   *
   * @return equipment protection level (Ga, Gb, Gc)
   */
  public String getEquipmentProtectionLevel() {
    return equipmentProtectionLevel;
  }

  /**
   * Set the equipment protection level.
   *
   * @param equipmentProtectionLevel equipment protection level
   */
  public void setEquipmentProtectionLevel(String equipmentProtectionLevel) {
    this.equipmentProtectionLevel = equipmentProtectionLevel;
  }
}
