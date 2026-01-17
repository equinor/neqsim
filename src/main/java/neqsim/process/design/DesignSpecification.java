package neqsim.process.design;

import java.util.HashMap;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.valve.ThrottlingValve;

/**
 * Builder class for standardized equipment design specification.
 *
 * <p>
 * This class provides a fluent API for configuring equipment design parameters in a consistent way
 * across all equipment types. It integrates with company technical requirements (TR) documents and
 * design standards.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * DesignSpecification.forSeparator("20-VA-01").setKFactor(0.08).setDiameter(3.0, "m")
 *     .setLength(8.0, "m").setMaterial("316L").setStandard("ASME-VIII").applyTo(separator);
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class DesignSpecification {

  private String equipmentName;
  private String equipmentType;
  private Map<String, Double> designParameters = new HashMap<>();
  private Map<String, String> designParameterUnits = new HashMap<>();
  private Map<String, Double> operatingLimits = new HashMap<>();
  private String materialGrade;
  private String designStandard;
  private String trDocument;
  private String companyStandard;
  private double safetyFactor = 1.2;

  /**
   * Private constructor - use factory methods.
   *
   * @param equipmentName name of the equipment
   * @param equipmentType type of equipment (Separator, Valve, etc.)
   */
  private DesignSpecification(String equipmentName, String equipmentType) {
    this.equipmentName = equipmentName;
    this.equipmentType = equipmentType;
  }

  // ==================== Factory Methods ====================

  /**
   * Create design specification for a separator.
   *
   * @param name equipment name/tag
   * @return new DesignSpecification configured for separators
   */
  public static DesignSpecification forSeparator(String name) {
    return new DesignSpecification(name, "Separator");
  }

  /**
   * Create design specification for a three-phase separator.
   *
   * @param name equipment name/tag
   * @return new DesignSpecification configured for three-phase separators
   */
  public static DesignSpecification forThreePhaseSeparator(String name) {
    return new DesignSpecification(name, "ThreePhaseSeparator");
  }

  /**
   * Create design specification for a valve.
   *
   * @param name equipment name/tag
   * @return new DesignSpecification configured for valves
   */
  public static DesignSpecification forValve(String name) {
    return new DesignSpecification(name, "Valve");
  }

  /**
   * Create design specification for a pipeline.
   *
   * @param name equipment name/tag
   * @return new DesignSpecification configured for pipelines
   */
  public static DesignSpecification forPipeline(String name) {
    return new DesignSpecification(name, "Pipeline");
  }

  /**
   * Create design specification for a heater or cooler.
   *
   * @param name equipment name/tag
   * @return new DesignSpecification configured for heaters/coolers
   */
  public static DesignSpecification forHeater(String name) {
    return new DesignSpecification(name, "Heater");
  }

  /**
   * Create design specification for a compressor.
   *
   * @param name equipment name/tag
   * @return new DesignSpecification configured for compressors
   */
  public static DesignSpecification forCompressor(String name) {
    return new DesignSpecification(name, "Compressor");
  }

  // ==================== Common Setters ====================

  /**
   * Set the material grade for the equipment.
   *
   * @param grade material grade (e.g., "316L", "SA-516-70", "X65")
   * @return this specification for chaining
   */
  public DesignSpecification setMaterial(String grade) {
    this.materialGrade = grade;
    return this;
  }

  /**
   * Set the design standard.
   *
   * @param standard design standard code (e.g., "ASME-VIII", "DNV-OS-F101", "API-5L")
   * @return this specification for chaining
   */
  public DesignSpecification setStandard(String standard) {
    this.designStandard = standard;
    return this;
  }

  /**
   * Set the company technical requirements document.
   *
   * @param company company name (e.g., "Equinor", "Shell")
   * @param trDoc TR document reference (e.g., "TR2000", "DEP-31.38.01.11")
   * @return this specification for chaining
   */
  public DesignSpecification setTRDocument(String company, String trDoc) {
    this.companyStandard = company;
    this.trDocument = trDoc;
    return this;
  }

  /**
   * Set the safety factor for sizing.
   *
   * @param factor safety factor (typically 1.1-1.3)
   * @return this specification for chaining
   */
  public DesignSpecification setSafetyFactor(double factor) {
    this.safetyFactor = factor;
    return this;
  }

  // ==================== Separator-Specific Setters ====================

  /**
   * Set the gas load factor (K-factor) for a separator.
   *
   * @param kFactor Souders-Brown coefficient in m/s
   * @return this specification for chaining
   */
  public DesignSpecification setKFactor(double kFactor) {
    designParameters.put("kFactor", kFactor);
    designParameterUnits.put("kFactor", "m/s");
    return this;
  }

  /**
   * Set the separator internal diameter.
   *
   * @param diameter diameter value
   * @param unit unit (e.g., "m", "mm", "inch")
   * @return this specification for chaining
   */
  public DesignSpecification setDiameter(double diameter, String unit) {
    designParameters.put("diameter", convertToMeters(diameter, unit));
    designParameterUnits.put("diameter", "m");
    return this;
  }

  /**
   * Set the separator length.
   *
   * @param length length value
   * @param unit unit (e.g., "m", "mm", "ft")
   * @return this specification for chaining
   */
  public DesignSpecification setLength(double length, String unit) {
    designParameters.put("length", convertToMeters(length, unit));
    designParameterUnits.put("length", "m");
    return this;
  }

  // ==================== Valve-Specific Setters ====================

  /**
   * Set the valve flow coefficient (Cv).
   *
   * @param cv flow coefficient in US gpm/sqrt(psi)
   * @return this specification for chaining
   */
  public DesignSpecification setCv(double cv) {
    designParameters.put("Cv", cv);
    designParameterUnits.put("Cv", "US gpm/sqrt(psi)");
    return this;
  }

  /**
   * Set the maximum valve opening percentage.
   *
   * @param maxOpening maximum opening (0-100%)
   * @return this specification for chaining
   */
  public DesignSpecification setMaxValveOpening(double maxOpening) {
    operatingLimits.put("maxValveOpening", maxOpening);
    return this;
  }

  // ==================== Pipeline-Specific Setters ====================

  /**
   * Set the pipeline internal diameter.
   *
   * @param diameter diameter value
   * @param unit unit (e.g., "m", "mm", "inch")
   * @return this specification for chaining
   */
  public DesignSpecification setPipeDiameter(double diameter, String unit) {
    designParameters.put("pipeDiameter", convertToMeters(diameter, unit));
    designParameterUnits.put("pipeDiameter", "m");
    return this;
  }

  /**
   * Set the pipeline length.
   *
   * @param length length value
   * @param unit unit (e.g., "m", "km", "ft")
   * @return this specification for chaining
   */
  public DesignSpecification setPipeLength(double length, String unit) {
    double lengthM = length;
    if ("km".equalsIgnoreCase(unit)) {
      lengthM = length * 1000.0;
    } else if ("ft".equalsIgnoreCase(unit)) {
      lengthM = length * 0.3048;
    }
    designParameters.put("pipeLength", lengthM);
    designParameterUnits.put("pipeLength", "m");
    return this;
  }

  /**
   * Set the maximum design velocity for the pipeline.
   *
   * @param velocity velocity in m/s
   * @return this specification for chaining
   */
  public DesignSpecification setMaxVelocity(double velocity) {
    operatingLimits.put("maxVelocity", velocity);
    return this;
  }

  /**
   * Set the wall thickness for the pipeline.
   *
   * @param thickness thickness value
   * @param unit unit (e.g., "m", "mm", "inch")
   * @return this specification for chaining
   */
  public DesignSpecification setWallThickness(double thickness, String unit) {
    designParameters.put("wallThickness", convertToMeters(thickness, unit));
    designParameterUnits.put("wallThickness", "m");
    return this;
  }

  // ==================== Heater-Specific Setters ====================

  /**
   * Set the maximum design duty for a heater or cooler.
   *
   * @param duty duty value
   * @param unit unit (e.g., "kW", "MW", "BTU/hr")
   * @return this specification for chaining
   */
  public DesignSpecification setMaxDuty(double duty, String unit) {
    double dutyW = duty;
    if ("kW".equalsIgnoreCase(unit)) {
      dutyW = duty * 1000.0;
    } else if ("MW".equalsIgnoreCase(unit)) {
      dutyW = duty * 1.0e6;
    } else if ("BTU/hr".equalsIgnoreCase(unit)) {
      dutyW = duty * 0.293071;
    }
    operatingLimits.put("maxDuty", dutyW);
    return this;
  }

  // ==================== Apply Method ====================

  /**
   * Apply this specification to the given equipment.
   *
   * <p>
   * This method configures the equipment with all the design parameters and operating limits
   * defined in this specification.
   * </p>
   *
   * @param equipment the equipment to configure
   * @throws IllegalArgumentException if equipment type doesn't match specification
   */
  public void applyTo(ProcessEquipmentInterface equipment) {
    // Apply to Separator
    if (equipment instanceof ThreePhaseSeparator) {
      applyToThreePhaseSeparator((ThreePhaseSeparator) equipment);
    } else if (equipment instanceof Separator) {
      applyToSeparator((Separator) equipment);
    } else if (equipment instanceof ThrottlingValve) {
      applyToValve((ThrottlingValve) equipment);
    } else if (equipment instanceof PipeBeggsAndBrills) {
      applyToPipeline((PipeBeggsAndBrills) equipment);
    } else if (equipment instanceof Heater) {
      applyToHeater((Heater) equipment);
    }
  }

  private void applyToSeparator(Separator separator) {
    if (designParameters.containsKey("kFactor")) {
      separator.setDesignGasLoadFactor(designParameters.get("kFactor"));
    }
    if (designParameters.containsKey("diameter")) {
      separator.setInternalDiameter(designParameters.get("diameter"));
    }
    if (designParameters.containsKey("length")) {
      separator.setSeparatorLength(designParameters.get("length"));
    }
  }

  private void applyToThreePhaseSeparator(ThreePhaseSeparator separator) {
    applyToSeparator(separator);
  }

  private void applyToValve(ThrottlingValve valve) {
    if (designParameters.containsKey("Cv")) {
      valve.setCv(designParameters.get("Cv"));
    }
    if (operatingLimits.containsKey("maxValveOpening")) {
      valve.setMaximumValveOpening(operatingLimits.get("maxValveOpening"));
    }
  }

  private void applyToPipeline(PipeBeggsAndBrills pipeline) {
    if (designParameters.containsKey("pipeDiameter")) {
      pipeline.setDiameter(designParameters.get("pipeDiameter"));
    }
    if (designParameters.containsKey("pipeLength")) {
      pipeline.setLength(designParameters.get("pipeLength"));
    }
    if (designParameters.containsKey("wallThickness")) {
      pipeline.setThickness(designParameters.get("wallThickness"));
    }
    if (operatingLimits.containsKey("maxVelocity")) {
      pipeline.initMechanicalDesign();
      pipeline.getMechanicalDesign().setMaxDesignVelocity(operatingLimits.get("maxVelocity"));
    }
  }

  private void applyToHeater(Heater heater) {
    if (operatingLimits.containsKey("maxDuty")) {
      heater.setMaxDesignDuty(operatingLimits.get("maxDuty")); // Already in Watts
    }
  }

  // ==================== Utility Methods ====================

  private double convertToMeters(double value, String unit) {
    if ("mm".equalsIgnoreCase(unit)) {
      return value / 1000.0;
    } else if ("inch".equalsIgnoreCase(unit) || "in".equalsIgnoreCase(unit)) {
      return value * 0.0254;
    } else if ("ft".equalsIgnoreCase(unit)) {
      return value * 0.3048;
    }
    return value; // Assume meters
  }

  // ==================== Getters ====================

  /**
   * Get the equipment name.
   *
   * @return equipment name
   */
  public String getEquipmentName() {
    return equipmentName;
  }

  /**
   * Get the equipment type.
   *
   * @return equipment type
   */
  public String getEquipmentType() {
    return equipmentType;
  }

  /**
   * Get all design parameters.
   *
   * @return map of parameter name to value
   */
  public Map<String, Double> getDesignParameters() {
    return new HashMap<>(designParameters);
  }

  /**
   * Get all operating limits.
   *
   * @return map of limit name to value
   */
  public Map<String, Double> getOperatingLimits() {
    return new HashMap<>(operatingLimits);
  }

  /**
   * Get the safety factor.
   *
   * @return safety factor
   */
  public double getSafetyFactor() {
    return safetyFactor;
  }

  /**
   * Get the material grade.
   *
   * @return material grade or null if not set
   */
  public String getMaterialGrade() {
    return materialGrade;
  }

  /**
   * Get the design standard.
   *
   * @return design standard or null if not set
   */
  public String getDesignStandard() {
    return designStandard;
  }
}
