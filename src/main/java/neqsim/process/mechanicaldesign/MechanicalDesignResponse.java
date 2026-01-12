package neqsim.process.mechanicaldesign;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Response class for mechanical design JSON export.
 *
 * <p>
 * This class provides a structured representation of mechanical design data that can be easily
 * serialized to JSON format. It supports both individual equipment mechanical design and
 * system-wide aggregated data.
 * </p>
 *
 * <p>
 * The response includes:
 * </p>
 * <ul>
 * <li>Equipment identification and classification</li>
 * <li>Weight breakdown (total, vessel, piping, E&amp;I, structural)</li>
 * <li>Design conditions (pressure, temperature)</li>
 * <li>Dimensions and plot space</li>
 * <li>Utility requirements (power, duty)</li>
 * <li>System-level aggregations when applicable</li>
 * </ul>
 *
 * <p>
 * Usage example:
 * </p>
 * 
 * <pre>
 * {@code
 * // For individual equipment
 * MechanicalDesign mecDesign = valve.getMechanicalDesign();
 * mecDesign.calcDesign();
 * String json = mecDesign.toJson();
 *
 * // For system-wide
 * SystemMechanicalDesign sysMecDesign = new SystemMechanicalDesign(process);
 * sysMecDesign.runDesignCalculation();
 * String json = sysMecDesign.toJson();
 * }
 * </pre>
 *
 * @author esol
 * @version 1.0
 */
public class MechanicalDesignResponse implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  // ============================================================================
  // Equipment Identification
  // ============================================================================

  /** Equipment name. */
  private String name;

  /** Equipment type (e.g., "Separator", "Compressor", "Valve"). */
  private String equipmentType;

  /** Equipment class name. */
  private String equipmentClass;

  /** Design standard used (e.g., "API 610", "ASME VIII"). */
  private String designStandard;

  // ============================================================================
  // Weight Data
  // ============================================================================

  /** Total weight in kg. */
  private double totalWeight;

  /** Vessel shell weight in kg. */
  private double vesselWeight;

  /** Internals weight in kg. */
  private double internalsWeight;

  /** Piping weight in kg. */
  private double pipingWeight;

  /** Nozzles weight in kg. */
  private double nozzlesWeight;

  /** Electrical and instrumentation weight in kg. */
  private double eiWeight;

  /** Structural steel weight in kg. */
  private double structuralWeight;

  /** Operating weight (with contents) in kg. */
  private double operatingWeight;

  // ============================================================================
  // Design Conditions
  // ============================================================================

  /** Maximum design pressure in bara. */
  private double maxDesignPressure;

  /** Minimum design pressure in bara. */
  private double minDesignPressure;

  /** Maximum design temperature in °C. */
  private double maxDesignTemperature;

  /** Minimum design temperature in °C. */
  private double minDesignTemperature;

  /** Maximum operating pressure in bara. */
  private double maxOperatingPressure;

  /** Maximum operating temperature in °C. */
  private double maxOperatingTemperature;

  // ============================================================================
  // Dimensions
  // ============================================================================

  /** Inner diameter in meters. */
  private double innerDiameter;

  /** Outer diameter in meters. */
  private double outerDiameter;

  /** Tangent-to-tangent length in meters. */
  private double tangentLength;

  /** Wall thickness in mm. */
  private double wallThickness;

  /** Module length (plot space) in meters. */
  private double moduleLength;

  /** Module width (plot space) in meters. */
  private double moduleWidth;

  /** Module height in meters. */
  private double moduleHeight;

  /** Total volume in m3. */
  private double totalVolume;

  // ============================================================================
  // Materials
  // ============================================================================

  /** Shell material. */
  private String shellMaterial;

  /** Head material. */
  private String headMaterial;

  /** Corrosion allowance in mm. */
  private double corrosionAllowance;

  // ============================================================================
  // Utility Requirements
  // ============================================================================

  /** Power requirement in kW (positive = consumed, negative = produced). */
  private double power;

  /** Heating/Cooling duty in kW (positive = heating, negative = cooling). */
  private double duty;

  // ============================================================================
  // Equipment-Specific Data
  // ============================================================================

  /** Equipment-specific design parameters. */
  private Map<String, Object> specificParameters = new LinkedHashMap<String, Object>();

  // ============================================================================
  // System-Level Data (for SystemMechanicalDesign)
  // ============================================================================

  /** Flag indicating if this is a system-level response. */
  private boolean isSystemLevel = false;

  /** Process/system name. */
  private String processName;

  /** Total equipment count. */
  private int equipmentCount;

  /** Total power required in kW. */
  private double totalPowerRequired;

  /** Total power recovered in kW. */
  private double totalPowerRecovered;

  /** Net power requirement in kW. */
  private double netPower;

  /** Total heating duty in kW. */
  private double totalHeatingDuty;

  /** Total cooling duty in kW. */
  private double totalCoolingDuty;

  /** Total plot space in m2. */
  private double totalPlotSpace;

  /** Footprint length in m. */
  private double footprintLength;

  /** Footprint width in m. */
  private double footprintWidth;

  /** Maximum height in m. */
  private double maxHeight;

  /** Weight breakdown by equipment type. */
  private Map<String, Double> weightByType = new LinkedHashMap<String, Double>();

  /** Count breakdown by equipment type. */
  private Map<String, Integer> countByType = new LinkedHashMap<String, Integer>();

  /** Weight breakdown by discipline. */
  private Map<String, Double> weightByDiscipline = new LinkedHashMap<String, Double>();

  /** Equipment list with summaries. */
  private List<EquipmentSummary> equipmentList = new ArrayList<EquipmentSummary>();

  // ============================================================================
  // Equipment Summary Inner Class
  // ============================================================================

  /**
   * Summary of individual equipment for system-level reports.
   */
  public static class EquipmentSummary implements java.io.Serializable {
    private static final long serialVersionUID = 1000L;

    private String name;
    private String type;
    private double weight;
    private double designPressure;
    private double designTemperature;
    private double power;
    private double duty;
    private String dimensions;

    /**
     * Constructor.
     *
     * @param name equipment name
     * @param type equipment type
     */
    public EquipmentSummary(String name, String type) {
      this.name = name;
      this.type = type;
    }

    // Getters and setters
    public String getName() {
      return name;
    }

    public String getType() {
      return type;
    }

    public double getWeight() {
      return weight;
    }

    public void setWeight(double weight) {
      this.weight = weight;
    }

    public double getDesignPressure() {
      return designPressure;
    }

    public void setDesignPressure(double designPressure) {
      this.designPressure = designPressure;
    }

    public double getDesignTemperature() {
      return designTemperature;
    }

    public void setDesignTemperature(double designTemperature) {
      this.designTemperature = designTemperature;
    }

    public double getPower() {
      return power;
    }

    public void setPower(double power) {
      this.power = power;
    }

    public double getDuty() {
      return duty;
    }

    public void setDuty(double duty) {
      this.duty = duty;
    }

    public String getDimensions() {
      return dimensions;
    }

    public void setDimensions(String dimensions) {
      this.dimensions = dimensions;
    }
  }

  // ============================================================================
  // Constructors
  // ============================================================================

  /**
   * Default constructor.
   */
  public MechanicalDesignResponse() {}

  /**
   * Constructor from individual MechanicalDesign.
   *
   * @param mecDesign the mechanical design object
   */
  public MechanicalDesignResponse(MechanicalDesign mecDesign) {
    populateFromMechanicalDesign(mecDesign);
  }

  /**
   * Constructor from SystemMechanicalDesign.
   *
   * @param sysMecDesign the system mechanical design object
   */
  public MechanicalDesignResponse(SystemMechanicalDesign sysMecDesign) {
    populateFromSystemMechanicalDesign(sysMecDesign);
  }

  // ============================================================================
  // Population Methods
  // ============================================================================

  /**
   * Populate from individual MechanicalDesign.
   *
   * @param mecDesign the mechanical design object
   */
  public void populateFromMechanicalDesign(MechanicalDesign mecDesign) {
    if (mecDesign == null) {
      return;
    }

    this.isSystemLevel = false;

    // Equipment identification
    if (mecDesign.getProcessEquipment() != null) {
      this.name = mecDesign.getProcessEquipment().getName();
      this.equipmentClass = mecDesign.getProcessEquipment().getClass().getSimpleName();
    }

    // Weight data
    this.totalWeight = mecDesign.getWeightTotal();
    this.vesselWeight = mecDesign.getWeigthVesselShell();
    this.internalsWeight = mecDesign.getWeigthInternals();
    this.pipingWeight = mecDesign.getWeightPiping();
    this.nozzlesWeight = mecDesign.getWeightNozzle();
    this.eiWeight = mecDesign.getWeightElectroInstrument();
    this.structuralWeight = mecDesign.getWeightStructualSteel();

    // Design conditions
    this.maxDesignPressure = mecDesign.getMaxDesignPressure();
    this.maxDesignTemperature = mecDesign.getDesignMaxTemperatureLimit();
    this.minDesignTemperature = mecDesign.getDesignMinTemperatureLimit();
    this.maxOperatingPressure = mecDesign.getMaxOperationPressure();
    this.maxOperatingTemperature = mecDesign.getMaxOperationTemperature();

    // Dimensions
    this.innerDiameter = mecDesign.getInnerDiameter();
    this.outerDiameter = mecDesign.getOuterDiameter();
    this.tangentLength = mecDesign.getTantanLength();
    this.wallThickness = mecDesign.getWallThickness();
    this.moduleLength = mecDesign.getModuleLength();
    this.moduleWidth = mecDesign.getModuleWidth();
    this.moduleHeight = mecDesign.getModuleHeight();
    this.totalVolume = mecDesign.getVolumeTotal();

    // Materials
    this.corrosionAllowance = mecDesign.getCorrosionAllowance();
  }

  /**
   * Populate from SystemMechanicalDesign.
   *
   * @param sysMecDesign the system mechanical design object
   */
  public void populateFromSystemMechanicalDesign(SystemMechanicalDesign sysMecDesign) {
    if (sysMecDesign == null) {
      return;
    }

    this.isSystemLevel = true;

    // Process identification
    if (sysMecDesign.getProcess() != null) {
      this.processName = sysMecDesign.getProcess().getName();
    }

    // Totals
    this.totalWeight = sysMecDesign.getTotalWeight();
    this.totalVolume = sysMecDesign.getTotalVolume();
    this.totalPlotSpace = sysMecDesign.getTotalPlotSpace();
    this.equipmentCount = sysMecDesign.getTotalNumberOfModules();

    // Utility requirements
    this.totalPowerRequired = sysMecDesign.getTotalPowerRequired();
    this.totalPowerRecovered = sysMecDesign.getTotalPowerRecovered();
    this.netPower = sysMecDesign.getNetPowerRequirement();
    this.totalHeatingDuty = sysMecDesign.getTotalHeatingDuty();
    this.totalCoolingDuty = sysMecDesign.getTotalCoolingDuty();

    // Footprint
    this.footprintLength = sysMecDesign.getTotalFootprintLength();
    this.footprintWidth = sysMecDesign.getTotalFootprintWidth();
    this.maxHeight = sysMecDesign.getMaxEquipmentHeight();

    // Breakdowns
    this.weightByType = new LinkedHashMap<String, Double>(sysMecDesign.getWeightByEquipmentType());
    this.countByType = new LinkedHashMap<String, Integer>(sysMecDesign.getEquipmentCountByType());
    this.weightByDiscipline =
        new LinkedHashMap<String, Double>(sysMecDesign.getWeightByDiscipline());

    // Equipment list
    for (SystemMechanicalDesign.EquipmentDesignSummary eds : sysMecDesign.getEquipmentList()) {
      EquipmentSummary summary = new EquipmentSummary(eds.getName(), eds.getType());
      summary.setWeight(eds.getWeight());
      summary.setDesignPressure(eds.getDesignPressure());
      summary.setDesignTemperature(eds.getDesignTemperature());
      summary.setPower(eds.getPower());
      summary.setDuty(eds.getDuty());
      summary.setDimensions(eds.getDimensions());
      this.equipmentList.add(summary);
    }
  }

  // ============================================================================
  // JSON Export Methods
  // ============================================================================

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
   * Convert to JSON string with compact format (no pretty printing).
   *
   * @return compact JSON representation
   */
  public String toCompactJson() {
    Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
    return gson.toJson(this);
  }

  /**
   * Parse from JSON string.
   *
   * @param json the JSON string
   * @return MechanicalDesignResponse object
   */
  public static MechanicalDesignResponse fromJson(String json) {
    Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
    return gson.fromJson(json, MechanicalDesignResponse.class);
  }

  /**
   * Merge this response with JSON from equipment toJson().
   *
   * @param equipmentJson JSON from equipment.toJson()
   * @return merged JSON string
   */
  public String mergeWithEquipmentJson(String equipmentJson) {
    if (equipmentJson == null || equipmentJson.isEmpty()) {
      return toJson();
    }

    try {
      Gson gson =
          new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();

      // Parse both JSONs
      JsonObject mecDesignJson = JsonParser.parseString(toJson()).getAsJsonObject();
      JsonObject eqJson = JsonParser.parseString(equipmentJson).getAsJsonObject();

      // Create combined object
      JsonObject combined = new JsonObject();
      combined.add("processData", eqJson);
      combined.add("mechanicalDesign", mecDesignJson);

      return gson.toJson(combined);
    } catch (Exception e) {
      return toJson();
    }
  }

  // ============================================================================
  // Getters and Setters
  // ============================================================================

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEquipmentType() {
    return equipmentType;
  }

  public void setEquipmentType(String equipmentType) {
    this.equipmentType = equipmentType;
  }

  public String getEquipmentClass() {
    return equipmentClass;
  }

  public void setEquipmentClass(String equipmentClass) {
    this.equipmentClass = equipmentClass;
  }

  public String getDesignStandard() {
    return designStandard;
  }

  public void setDesignStandard(String designStandard) {
    this.designStandard = designStandard;
  }

  public double getTotalWeight() {
    return totalWeight;
  }

  public void setTotalWeight(double totalWeight) {
    this.totalWeight = totalWeight;
  }

  public double getVesselWeight() {
    return vesselWeight;
  }

  public void setVesselWeight(double vesselWeight) {
    this.vesselWeight = vesselWeight;
  }

  public double getInternalsWeight() {
    return internalsWeight;
  }

  public void setInternalsWeight(double internalsWeight) {
    this.internalsWeight = internalsWeight;
  }

  public double getPipingWeight() {
    return pipingWeight;
  }

  public void setPipingWeight(double pipingWeight) {
    this.pipingWeight = pipingWeight;
  }

  public double getNozzlesWeight() {
    return nozzlesWeight;
  }

  public void setNozzlesWeight(double nozzlesWeight) {
    this.nozzlesWeight = nozzlesWeight;
  }

  public double getEiWeight() {
    return eiWeight;
  }

  public void setEiWeight(double eiWeight) {
    this.eiWeight = eiWeight;
  }

  public double getStructuralWeight() {
    return structuralWeight;
  }

  public void setStructuralWeight(double structuralWeight) {
    this.structuralWeight = structuralWeight;
  }

  public double getOperatingWeight() {
    return operatingWeight;
  }

  public void setOperatingWeight(double operatingWeight) {
    this.operatingWeight = operatingWeight;
  }

  public double getMaxDesignPressure() {
    return maxDesignPressure;
  }

  public void setMaxDesignPressure(double maxDesignPressure) {
    this.maxDesignPressure = maxDesignPressure;
  }

  public double getMinDesignPressure() {
    return minDesignPressure;
  }

  public void setMinDesignPressure(double minDesignPressure) {
    this.minDesignPressure = minDesignPressure;
  }

  public double getMaxDesignTemperature() {
    return maxDesignTemperature;
  }

  public void setMaxDesignTemperature(double maxDesignTemperature) {
    this.maxDesignTemperature = maxDesignTemperature;
  }

  public double getMinDesignTemperature() {
    return minDesignTemperature;
  }

  public void setMinDesignTemperature(double minDesignTemperature) {
    this.minDesignTemperature = minDesignTemperature;
  }

  public double getMaxOperatingPressure() {
    return maxOperatingPressure;
  }

  public void setMaxOperatingPressure(double maxOperatingPressure) {
    this.maxOperatingPressure = maxOperatingPressure;
  }

  public double getMaxOperatingTemperature() {
    return maxOperatingTemperature;
  }

  public void setMaxOperatingTemperature(double maxOperatingTemperature) {
    this.maxOperatingTemperature = maxOperatingTemperature;
  }

  public double getInnerDiameter() {
    return innerDiameter;
  }

  public void setInnerDiameter(double innerDiameter) {
    this.innerDiameter = innerDiameter;
  }

  public double getOuterDiameter() {
    return outerDiameter;
  }

  public void setOuterDiameter(double outerDiameter) {
    this.outerDiameter = outerDiameter;
  }

  public double getTangentLength() {
    return tangentLength;
  }

  public void setTangentLength(double tangentLength) {
    this.tangentLength = tangentLength;
  }

  public double getWallThickness() {
    return wallThickness;
  }

  public void setWallThickness(double wallThickness) {
    this.wallThickness = wallThickness;
  }

  public double getModuleLength() {
    return moduleLength;
  }

  public void setModuleLength(double moduleLength) {
    this.moduleLength = moduleLength;
  }

  public double getModuleWidth() {
    return moduleWidth;
  }

  public void setModuleWidth(double moduleWidth) {
    this.moduleWidth = moduleWidth;
  }

  public double getModuleHeight() {
    return moduleHeight;
  }

  public void setModuleHeight(double moduleHeight) {
    this.moduleHeight = moduleHeight;
  }

  public double getTotalVolume() {
    return totalVolume;
  }

  public void setTotalVolume(double totalVolume) {
    this.totalVolume = totalVolume;
  }

  public String getShellMaterial() {
    return shellMaterial;
  }

  public void setShellMaterial(String shellMaterial) {
    this.shellMaterial = shellMaterial;
  }

  public String getHeadMaterial() {
    return headMaterial;
  }

  public void setHeadMaterial(String headMaterial) {
    this.headMaterial = headMaterial;
  }

  public double getCorrosionAllowance() {
    return corrosionAllowance;
  }

  public void setCorrosionAllowance(double corrosionAllowance) {
    this.corrosionAllowance = corrosionAllowance;
  }

  public double getPower() {
    return power;
  }

  public void setPower(double power) {
    this.power = power;
  }

  public double getDuty() {
    return duty;
  }

  public void setDuty(double duty) {
    this.duty = duty;
  }

  public Map<String, Object> getSpecificParameters() {
    return specificParameters;
  }

  public void setSpecificParameters(Map<String, Object> specificParameters) {
    this.specificParameters = specificParameters;
  }

  public void addSpecificParameter(String key, Object value) {
    this.specificParameters.put(key, value);
  }

  public boolean isSystemLevel() {
    return isSystemLevel;
  }

  public void setSystemLevel(boolean isSystemLevel) {
    this.isSystemLevel = isSystemLevel;
  }

  public String getProcessName() {
    return processName;
  }

  public void setProcessName(String processName) {
    this.processName = processName;
  }

  public int getEquipmentCount() {
    return equipmentCount;
  }

  public void setEquipmentCount(int equipmentCount) {
    this.equipmentCount = equipmentCount;
  }

  public double getTotalPowerRequired() {
    return totalPowerRequired;
  }

  public void setTotalPowerRequired(double totalPowerRequired) {
    this.totalPowerRequired = totalPowerRequired;
  }

  public double getTotalPowerRecovered() {
    return totalPowerRecovered;
  }

  public void setTotalPowerRecovered(double totalPowerRecovered) {
    this.totalPowerRecovered = totalPowerRecovered;
  }

  public double getNetPower() {
    return netPower;
  }

  public void setNetPower(double netPower) {
    this.netPower = netPower;
  }

  public double getTotalHeatingDuty() {
    return totalHeatingDuty;
  }

  public void setTotalHeatingDuty(double totalHeatingDuty) {
    this.totalHeatingDuty = totalHeatingDuty;
  }

  public double getTotalCoolingDuty() {
    return totalCoolingDuty;
  }

  public void setTotalCoolingDuty(double totalCoolingDuty) {
    this.totalCoolingDuty = totalCoolingDuty;
  }

  public double getTotalPlotSpace() {
    return totalPlotSpace;
  }

  public void setTotalPlotSpace(double totalPlotSpace) {
    this.totalPlotSpace = totalPlotSpace;
  }

  public double getFootprintLength() {
    return footprintLength;
  }

  public void setFootprintLength(double footprintLength) {
    this.footprintLength = footprintLength;
  }

  public double getFootprintWidth() {
    return footprintWidth;
  }

  public void setFootprintWidth(double footprintWidth) {
    this.footprintWidth = footprintWidth;
  }

  public double getMaxHeight() {
    return maxHeight;
  }

  public void setMaxHeight(double maxHeight) {
    this.maxHeight = maxHeight;
  }

  public Map<String, Double> getWeightByType() {
    return weightByType;
  }

  public void setWeightByType(Map<String, Double> weightByType) {
    this.weightByType = weightByType;
  }

  public Map<String, Integer> getCountByType() {
    return countByType;
  }

  public void setCountByType(Map<String, Integer> countByType) {
    this.countByType = countByType;
  }

  public Map<String, Double> getWeightByDiscipline() {
    return weightByDiscipline;
  }

  public void setWeightByDiscipline(Map<String, Double> weightByDiscipline) {
    this.weightByDiscipline = weightByDiscipline;
  }

  public List<EquipmentSummary> getEquipmentList() {
    return equipmentList;
  }

  public void setEquipmentList(List<EquipmentSummary> equipmentList) {
    this.equipmentList = equipmentList;
  }
}
