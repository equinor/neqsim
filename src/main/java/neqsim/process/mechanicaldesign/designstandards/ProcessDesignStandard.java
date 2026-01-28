package neqsim.process.mechanicaldesign.designstandards;

import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * Process design standard providing design margins and safety factors for process equipment sizing.
 *
 * <p>
 * This class provides standardized design margins for:
 * <ul>
 * <li>Design pressure (margin above maximum operating pressure)</li>
 * <li>Design temperature (margin above maximum operating temperature)</li>
 * <li>Flow capacity (safety factor for design flow rates)</li>
 * <li>Duty margins (safety factor for heat transfer equipment)</li>
 * </ul>
 *
 * <p>
 * Values are loaded from the TechnicalRequirements_Process database table based on equipment type
 * and company-specific standards.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProcessDesignStandard extends DesignStandard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Default design pressure margin (10% above max operating). */
  public static final double DEFAULT_DESIGN_PRESSURE_MARGIN = 1.10;

  /** Default design temperature margin in Celsius. */
  public static final double DEFAULT_DESIGN_TEMPERATURE_MARGIN_C = 25.0;

  /** Default minimum design temperature for carbon steel in Celsius. */
  public static final double DEFAULT_MIN_DESIGN_TEMPERATURE_C = -29.0;

  /** Default flow safety factor. */
  public static final double DEFAULT_FLOW_SAFETY_FACTOR = 1.10;

  /** Default duty margin for heat exchangers. */
  public static final double DEFAULT_DUTY_MARGIN = 1.10;

  /** Default area margin for heat exchangers. */
  public static final double DEFAULT_AREA_MARGIN = 1.15;

  /** Design pressure margin factor. */
  private double designPressureMargin = DEFAULT_DESIGN_PRESSURE_MARGIN;

  /** Design temperature margin in Celsius. */
  private double designTemperatureMarginC = DEFAULT_DESIGN_TEMPERATURE_MARGIN_C;

  /** Minimum design temperature in Celsius. */
  private double minDesignTemperatureC = DEFAULT_MIN_DESIGN_TEMPERATURE_C;

  /** Flow safety factor for volumetric design. */
  private double flowSafetyFactor = DEFAULT_FLOW_SAFETY_FACTOR;

  /** Duty margin for heat transfer equipment. */
  private double dutyMargin = DEFAULT_DUTY_MARGIN;

  /** Area margin for heat transfer equipment. */
  private double areaMargin = DEFAULT_AREA_MARGIN;

  /** Equipment type this standard applies to. */
  private String equipmentType = "";

  /**
   * Constructs a ProcessDesignStandard with default values.
   */
  public ProcessDesignStandard() {
    super();
  }

  /**
   * Constructs a ProcessDesignStandard for the specified mechanical design.
   *
   * @param mechanicalDesign the mechanical design to apply standards to
   */
  public ProcessDesignStandard(MechanicalDesign mechanicalDesign) {
    super("ProcessDesignStandard", mechanicalDesign);
    loadDesignStandard();
  }

  /**
   * Constructs a ProcessDesignStandard for specific equipment type.
   *
   * @param equipmentType the type of equipment (e.g., "Separator", "HeatExchanger")
   * @param mechanicalDesign the mechanical design to apply standards to
   */
  public ProcessDesignStandard(String equipmentType, MechanicalDesign mechanicalDesign) {
    super("ProcessDesignStandard", mechanicalDesign);
    this.equipmentType = equipmentType;
    loadDesignStandard();
  }

  /**
   * Gets the mechanical design associated with this standard.
   *
   * @return the mechanical design
   */
  public MechanicalDesign getMechanicalDesign() {
    return equipment;
  }

  /**
   * Loads design standards from database based on equipment type.
   */
  private void loadDesignStandard() {
    if (getMechanicalDesign() == null) {
      return;
    }

    if (equipmentType.isEmpty() && getMechanicalDesign().getProcessEquipment() != null) {
      equipmentType = getMechanicalDesign().getProcessEquipment().getClass().getSimpleName();
    }

    // Load from TechnicalRequirements_Process table
    try {
      neqsim.util.database.NeqSimProcessDesignDataBase database =
          new neqsim.util.database.NeqSimProcessDesignDataBase();
      java.sql.ResultSet dataSet = database.getResultSet(
          "SELECT * FROM technicalrequirements_process WHERE " + "EQUIPMENTTYPE='" + equipmentType
              + "' AND Company='" + getMechanicalDesign().getCompanySpecificDesignStandards()
              + "'");

      while (dataSet.next()) {
        String spec = dataSet.getString("SPECIFICATION");
        double minVal = dataSet.getDouble("MINVALUE");
        double maxVal = dataSet.getDouble("MAXVALUE");
        double value = (minVal + maxVal) / 2.0; // Use average of range

        if (spec.equalsIgnoreCase("DesignPressureMargin")) {
          this.designPressureMargin = value;
        } else if (spec.equalsIgnoreCase("DesignTemperatureMargin")) {
          this.designTemperatureMarginC = value;
        } else if (spec.equalsIgnoreCase("MinDesignTemperature")) {
          this.minDesignTemperatureC = value;
        } else if (spec.equalsIgnoreCase("FlowDesignFactor")
            || spec.equalsIgnoreCase("VolumetricDesignFactor")) {
          this.flowSafetyFactor = value;
        } else if (spec.equalsIgnoreCase("DesignDutyMargin")
            || spec.equalsIgnoreCase("DutyMargin")) {
          this.dutyMargin = value;
        } else if (spec.equalsIgnoreCase("AreaMargin")) {
          this.areaMargin = value;
        }
      }
      dataSet.close();
    } catch (Exception ex) {
      // Use default values if database lookup fails
    }
  }

  /**
   * Calculates design pressure from maximum operating pressure.
   *
   * @param maxOperatingPressure maximum operating pressure in bar
   * @return design pressure in bar
   */
  public double calculateDesignPressure(double maxOperatingPressure) {
    return maxOperatingPressure * designPressureMargin;
  }

  /**
   * Calculates design temperature from maximum operating temperature.
   *
   * @param maxOperatingTemperatureC maximum operating temperature in Celsius
   * @return design temperature in Celsius
   */
  public double calculateDesignTemperature(double maxOperatingTemperatureC) {
    return maxOperatingTemperatureC + designTemperatureMarginC;
  }

  /**
   * Calculates minimum design temperature considering material limits.
   *
   * @param minOperatingTemperatureC minimum operating temperature in Celsius
   * @return minimum design temperature in Celsius
   */
  public double calculateMinDesignTemperature(double minOperatingTemperatureC) {
    // For carbon steel, typically limited to -29°C unless impact tested
    // Return the lower of (min operating - margin) or material limit
    double calcMin = minOperatingTemperatureC - 10.0; // 10°C margin below min operating
    return Math.max(calcMin, minDesignTemperatureC);
  }

  /**
   * Calculates design flow rate from normal flow.
   *
   * @param normalFlowRate normal operating flow rate
   * @return design flow rate with safety factor applied
   */
  public double calculateDesignFlowRate(double normalFlowRate) {
    return normalFlowRate * flowSafetyFactor;
  }

  /**
   * Calculates design duty from normal duty.
   *
   * @param normalDuty normal operating duty in kW
   * @return design duty with margin applied
   */
  public double calculateDesignDuty(double normalDuty) {
    return normalDuty * dutyMargin;
  }

  /**
   * Calculates required heat transfer area with margin.
   *
   * @param calculatedArea calculated required area in m²
   * @return design area with margin applied
   */
  public double calculateDesignArea(double calculatedArea) {
    return calculatedArea * areaMargin;
  }

  /**
   * Gets the design pressure margin factor.
   *
   * @return design pressure margin (e.g., 1.10 for 10% margin)
   */
  public double getDesignPressureMargin() {
    return designPressureMargin;
  }

  /**
   * Sets the design pressure margin factor.
   *
   * @param designPressureMargin margin factor (e.g., 1.10 for 10%)
   */
  public void setDesignPressureMargin(double designPressureMargin) {
    this.designPressureMargin = designPressureMargin;
  }

  /**
   * Gets the design temperature margin in Celsius.
   *
   * @return temperature margin in Celsius
   */
  public double getDesignTemperatureMarginC() {
    return designTemperatureMarginC;
  }

  /**
   * Sets the design temperature margin in Celsius.
   *
   * @param designTemperatureMarginC temperature margin in Celsius
   */
  public void setDesignTemperatureMarginC(double designTemperatureMarginC) {
    this.designTemperatureMarginC = designTemperatureMarginC;
  }

  /**
   * Gets the minimum design temperature in Celsius.
   *
   * @return minimum design temperature in Celsius
   */
  public double getMinDesignTemperatureC() {
    return minDesignTemperatureC;
  }

  /**
   * Sets the minimum design temperature in Celsius.
   *
   * @param minDesignTemperatureC minimum design temperature in Celsius
   */
  public void setMinDesignTemperatureC(double minDesignTemperatureC) {
    this.minDesignTemperatureC = minDesignTemperatureC;
  }

  /**
   * Gets the flow safety factor.
   *
   * @return flow safety factor
   */
  public double getFlowSafetyFactor() {
    return flowSafetyFactor;
  }

  /**
   * Sets the flow safety factor.
   *
   * @param flowSafetyFactor safety factor for flow design
   */
  public void setFlowSafetyFactor(double flowSafetyFactor) {
    this.flowSafetyFactor = flowSafetyFactor;
  }

  /**
   * Gets the duty margin for heat exchangers.
   *
   * @return duty margin factor
   */
  public double getDutyMargin() {
    return dutyMargin;
  }

  /**
   * Sets the duty margin for heat exchangers.
   *
   * @param dutyMargin duty margin factor
   */
  public void setDutyMargin(double dutyMargin) {
    this.dutyMargin = dutyMargin;
  }

  /**
   * Gets the area margin for heat exchangers.
   *
   * @return area margin factor
   */
  public double getAreaMargin() {
    return areaMargin;
  }

  /**
   * Sets the area margin for heat exchangers.
   *
   * @param areaMargin area margin factor
   */
  public void setAreaMargin(double areaMargin) {
    this.areaMargin = areaMargin;
  }

  /**
   * Gets the equipment type this standard applies to.
   *
   * @return equipment type name
   */
  public String getEquipmentType() {
    return equipmentType;
  }

  /**
   * Sets the equipment type this standard applies to.
   *
   * @param equipmentType equipment type name
   */
  public void setEquipmentType(String equipmentType) {
    this.equipmentType = equipmentType;
  }

  /** {@inheritDoc} */
  @Override
  public String getStandardName() {
    return "Process Design Standard";
  }
}
