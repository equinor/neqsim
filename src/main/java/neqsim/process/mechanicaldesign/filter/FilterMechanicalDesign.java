package neqsim.process.mechanicaldesign.filter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.filter.Filter;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * General mechanical design for filter vessels per ASME VIII Div 1.
 *
 * <p>
 * This class sizes a filter pressure vessel (diameter, length, wall thickness), selects filter
 * elements based on the required gas face velocity, estimates weight and cost, and calculates
 * maintenance intervals. It follows the standard pressure vessel design methodology (ASME Section
 * VIII Division 1) and industry practices for gas filtration equipment.
 * </p>
 *
 * <p>
 * Subclasses (e.g., {@link SulfurFilterMechanicalDesign}) can override the template methods to
 * provide filter-type-specific behaviour such as retrieving flow rates from specialised equipment
 * classes, adding contaminant-specific JSON sections, or customising the bill of materials.
 * </p>
 *
 * <p>
 * Design basis:
 * </p>
 * <ul>
 * <li>Vessel sizing: based on maximum gas velocity through filter elements</li>
 * <li>Wall thickness: ASME VIII Div 1 circumferential stress formula</li>
 * <li>Filter elements: cartridge type with specified area per element</li>
 * <li>Cost: based on vessel weight, element cost, and installation factors</li>
 * </ul>
 *
 * @author esol
 * @version 1.0
 */
public class FilterMechanicalDesign extends MechanicalDesign {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1002L;

  // ============================================================================
  // Design Parameters
  // ============================================================================

  /** Design pressure margin factor (1.1 = 10% above max operating). */
  private double designPressureMargin = 1.10;

  /** Design temperature margin above max operating in Celsius. */
  private double designTemperatureMarginC = 25.0;

  /** Material grade for pressure vessel shell. */
  private String materialGrade = "SA-516-70";

  /** Maximum allowable stress for SA-516-70 at design temperature (MPa). */
  private double allowableStress = 138.0;

  /** Joint efficiency for fully radiographed welds. */
  private double jointEfficiency = 0.85;

  /** Corrosion allowance in metres. */
  private double corrosionAllowance = 0.003;

  /** Maximum gas face velocity through filter elements (m/s). */
  private double maxFaceVelocity = 0.15;

  /** Filter element area per element (m2). Typical cartridge: 0.5-2.0 m2. */
  private double elementArea = 1.0;

  /** Minimum vessel L/D ratio. */
  private double minLDRatio = 2.0;

  /** Maximum vessel L/D ratio. */
  private double maxLDRatio = 5.0;

  // ============================================================================
  // Calculated Results
  // ============================================================================

  /** Design pressure in bara. */
  private double designPressure = 0.0;

  /** Design temperature in Celsius. */
  private double designTemperatureC = 0.0;

  /** Required number of filter elements. */
  private int requiredElements = 0;

  /** Vessel shell thickness in metres. */
  private double shellThickness = 0.0;

  /** Vessel head thickness in metres. */
  private double headThickness = 0.0;

  /** Vessel tangent-to-tangent length in metres. */
  private double vesselLength = 0.0;

  /** Vessel empty weight in kg. */
  private double emptyVesselWeight = 0.0;

  /** Filter element set weight in kg (all elements). */
  private double elementWeight = 0.0;

  /** Total equipped weight in kg. */
  private double totalEquippedWeight = 0.0;

  // ============================================================================
  // Cost Parameters
  // ============================================================================

  /** Steel cost per kg (USD). */
  private double steelCostPerKg = 6.0;

  /** Cost per filter element (USD). */
  private double elementCostUSD = 2500.0;

  /** Installation factor (multiplier on equipment cost). */
  private double installationFactor = 2.5;

  /** Labour rate for element change (USD/hr). */
  private double labourRateUSD = 150.0;

  /** Hours to change one set of filter elements. */
  private double elementChangeHours = 8.0;

  /**
   * Creates a new FilterMechanicalDesign.
   *
   * @param equipment the Filter equipment to design
   */
  public FilterMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
  }

  // ============================================================================
  // Template methods — override in subclasses for filter-type-specific behaviour
  // ============================================================================

  /**
   * Returns the gas mass flow rate in kg/hr for element sizing.
   *
   * <p>
   * Default implementation reads from the inlet stream. Subclasses may override to use a
   * filter-type-specific method (e.g., a separate tracked flow rate field).
   * </p>
   *
   * @return gas flow rate in kg/hr
   */
  protected double getGasFlowRateKgHr() {
    Filter filter = (Filter) getProcessEquipment();
    if (filter.getInletStream() != null) {
      return filter.getInletStream().getFlowRate("kg/hr");
    }
    return 0.0;
  }

  /**
   * Returns the initial number of elements from the equipment before sizing.
   *
   * <p>
   * Default returns 1. Subclasses may override to read from the specific filter equipment.
   * </p>
   *
   * @return initial element count
   */
  protected int getInitialElementCount() {
    return 1;
  }

  /**
   * Updates the element count on the equipment after sizing.
   *
   * <p>
   * Default is a no-op. Subclasses should override to push the calculated count back to the
   * equipment object.
   * </p>
   *
   * @param count the calculated number of filter elements
   */
  protected void updateEquipmentElementCount(int count) {
    // no-op by default
  }

  /**
   * Returns the filter element change interval in hours.
   *
   * <p>
   * Default returns {@link Double#MAX_VALUE} (no scheduled replacement). Subclasses override to use
   * the specific filter's change interval calculation.
   * </p>
   *
   * @return change interval in hours
   */
  protected double getFilterChangeIntervalHours() {
    return Double.MAX_VALUE;
  }

  /**
   * Returns the equipment type name for JSON reporting.
   *
   * @return equipment type string
   */
  protected String getEquipmentTypeName() {
    return "Filter Vessel";
  }

  /**
   * Adds equipment-specific data sections to the JSON report map.
   *
   * <p>
   * Called by {@link #toJson()} after the common sections have been populated. Subclasses override
   * to add contaminant-specific data (e.g., sulfur removal rates, particle size data).
   * </p>
   *
   * @param result the JSON map to add sections to
   */
  protected void addEquipmentSpecificJson(Map<String, Object> result) {
    // no-op by default
  }

  /**
   * Returns the element material description for the BOM.
   *
   * @return element material string
   */
  protected String getElementMaterialDescription() {
    return "316L SS / Glass Fibre";
  }

  // ============================================================================
  // Core Design Calculations
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();

    Filter filter = (Filter) getProcessEquipment();

    // --- Operating conditions ---
    double operatingPressure = getMaxOperationPressure();
    if (operatingPressure < 1.0 && filter.getInletStream() != null) {
      operatingPressure = filter.getInletStream().getPressure("bara");
    }
    double operatingTempC = getMaxOperationTemperature() - 273.15;
    if (operatingTempC < -200.0 && filter.getInletStream() != null) {
      operatingTempC = filter.getInletStream().getTemperature("C");
    }

    // --- Design conditions ---
    designPressure = operatingPressure * designPressureMargin;
    designTemperatureC = operatingTempC + designTemperatureMarginC;

    // --- Gas volumetric flow for element sizing ---
    double gasFlowKgHr = getGasFlowRateKgHr();

    double gasDensity = 50.0; // default kg/m3
    if (filter.getInletStream() != null && filter.getInletStream().getThermoSystem() != null) {
      try {
        gasDensity = filter.getInletStream().getThermoSystem().getPhase(0).getDensity("kg/m3");
        if (gasDensity < 0.1) {
          gasDensity = 50.0;
        }
      } catch (Exception e) {
        gasDensity = 50.0;
      }
    }

    double volumetricFlowM3s = (gasFlowKgHr / 3600.0) / gasDensity;

    // --- Filter element sizing ---
    double requiredArea = volumetricFlowM3s / maxFaceVelocity;
    requiredElements = (int) Math.ceil(requiredArea / elementArea);
    requiredElements = Math.max(requiredElements, getInitialElementCount());
    updateEquipmentElementCount(requiredElements);

    // --- Vessel diameter from element bundle ---
    double elementDiameter = 0.15; // m per element envelope
    double bundleDiameter = Math.sqrt(requiredElements) * elementDiameter * 1.8;
    innerDiameter = Math.max(bundleDiameter + 0.2, 0.5); // min 500mm ID

    // --- Vessel length from L/D constraints ---
    double elementLength = 1.0;
    vesselLength = elementLength + innerDiameter;
    double ldRatio = vesselLength / innerDiameter;
    if (ldRatio < minLDRatio) {
      vesselLength = innerDiameter * minLDRatio;
    } else if (ldRatio > maxLDRatio) {
      innerDiameter = vesselLength / maxLDRatio;
    }

    // --- Wall thickness per ASME VIII Div 1 ---
    // Shell: t = PR / (SE - 0.6P)
    double P = designPressure * 0.1; // bara to MPa
    double S = allowableStress;
    double E = jointEfficiency;
    double R = innerDiameter / 2.0;
    shellThickness = (P * R) / (S * E - 0.6 * P) + corrosionAllowance;
    shellThickness = Math.max(shellThickness, 0.006); // min 6mm

    // Head (2:1 ellipsoidal): t = PD / (2SE - 0.2P)
    double D = innerDiameter;
    headThickness = (P * D) / (2.0 * S * E - 0.2 * P) + corrosionAllowance;
    headThickness = Math.max(headThickness, shellThickness);

    outerDiameter = innerDiameter + 2.0 * shellThickness;
    wallThickness = shellThickness;

    // --- Weight estimation ---
    double steelDensity = 7850.0; // kg/m3
    double shellArea = Math.PI * innerDiameter * vesselLength;
    double headArea = 2.0 * Math.PI * Math.pow(innerDiameter / 2.0, 2) * 1.084; // 2:1 ellipsoidal
    emptyVesselWeight = (shellArea + headArea) * shellThickness * steelDensity;

    double accessoriesWeight = emptyVesselWeight * 0.30;

    double weightPerElement = 10.0; // typical cartridge: 5-15 kg each
    elementWeight = requiredElements * weightPerElement;

    totalEquippedWeight = emptyVesselWeight + accessoriesWeight + elementWeight;

    // --- Set base class fields ---
    setWeigthVesselShell(emptyVesselWeight);
    setWeigthInternals(elementWeight);
    setWeightTotal(totalEquippedWeight);
    setInnerDiameter(innerDiameter);
    setOuterDiameter(outerDiameter);
    setWallThickness(shellThickness);
    tantanLength = vesselLength;
  }

  // ============================================================================
  // Cost Estimation
  // ============================================================================

  /**
   * Calculates the estimated equipment purchase cost in USD.
   *
   * @return purchased equipment cost in USD
   */
  public double getEquipmentCostUSD() {
    double vesselCost = emptyVesselWeight * steelCostPerKg;
    double elemCost = requiredElements * elementCostUSD;
    return vesselCost + elemCost;
  }

  /**
   * Calculates the total installed cost in USD (equipment + installation).
   *
   * @return installed cost in USD
   */
  public double getInstalledCostUSD() {
    return getEquipmentCostUSD() * installationFactor;
  }

  /**
   * Calculates annual maintenance cost for filter element replacement in USD.
   *
   * @return annual element replacement cost in USD
   */
  public double getAnnualMaintenanceCostUSD() {
    double changeIntervalHours = getFilterChangeIntervalHours();
    if (changeIntervalHours <= 0 || changeIntervalHours >= Double.MAX_VALUE) {
      return 0.0;
    }
    double changesPerYear = 8760.0 / changeIntervalHours;
    double elementCostPerChange = requiredElements * elementCostUSD;
    double labourCostPerChange = elementChangeHours * labourRateUSD;
    return changesPerYear * (elementCostPerChange + labourCostPerChange);
  }

  /**
   * Calculates total lifecycle cost in USD (CAPEX + OPEX).
   *
   * @param designLifeYears design life in years
   * @return total lifecycle cost in USD
   */
  public double getLifecycleCostUSD(double designLifeYears) {
    return getInstalledCostUSD() + getAnnualMaintenanceCostUSD() * designLifeYears;
  }

  /**
   * Generates a bill of materials.
   *
   * @return list of BOM line items as maps
   */
  public List<Map<String, Object>> generateBillOfMaterials() {
    List<Map<String, Object>> bom = new ArrayList<Map<String, Object>>();

    Map<String, Object> vessel = new LinkedHashMap<String, Object>();
    vessel.put("item", "Pressure Vessel Shell");
    vessel.put("material", materialGrade);
    vessel.put("quantity", 1);
    vessel.put("weight_kg", emptyVesselWeight);
    vessel.put("unitCost_USD", emptyVesselWeight * steelCostPerKg);
    bom.add(vessel);

    Map<String, Object> elements = new LinkedHashMap<String, Object>();
    elements.put("item", "Filter Cartridge Elements");
    elements.put("material", getElementMaterialDescription());
    elements.put("quantity", requiredElements);
    elements.put("weight_kg", elementWeight);
    elements.put("unitCost_USD", elementCostUSD);
    bom.add(elements);

    Map<String, Object> nozzles = new LinkedHashMap<String, Object>();
    nozzles.put("item", "Inlet/Outlet Nozzles & Flanges");
    nozzles.put("material", materialGrade);
    nozzles.put("quantity", 3); // inlet, outlet, drain
    nozzles.put("weight_kg", emptyVesselWeight * 0.10);
    nozzles.put("unitCost_USD", emptyVesselWeight * 0.10 * steelCostPerKg * 1.5);
    bom.add(nozzles);

    Map<String, Object> supports = new LinkedHashMap<String, Object>();
    supports.put("item", "Supports & Quick-Opening Closure");
    supports.put("material", "Carbon Steel");
    supports.put("quantity", 1);
    supports.put("weight_kg", emptyVesselWeight * 0.15);
    supports.put("unitCost_USD", emptyVesselWeight * 0.15 * steelCostPerKg * 2.0);
    bom.add(supports);

    return bom;
  }

  // ============================================================================
  // JSON Reporting
  // ============================================================================

  /**
   * Returns a comprehensive JSON design report.
   *
   * @return JSON string
   */
  @Override
  public String toJson() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    Filter filter = (Filter) getProcessEquipment();

    result.put("equipmentName", filter.getName());
    result.put("equipmentType", getEquipmentTypeName());
    result.put("designCode", "ASME VIII Div 1");
    result.put("materialGrade", materialGrade);

    // Design conditions
    Map<String, Object> design = new LinkedHashMap<String, Object>();
    design.put("designPressure_bara", designPressure);
    design.put("designTemperature_C", designTemperatureC);
    design.put("operatingPressure_bara", getMaxOperationPressure());
    design.put("designPressureMargin", designPressureMargin);
    design.put("allowableStress_MPa", allowableStress);
    design.put("jointEfficiency", jointEfficiency);
    design.put("corrosionAllowance_mm", corrosionAllowance * 1000.0);
    result.put("designConditions", design);

    // Vessel dimensions
    Map<String, Object> dimensions = new LinkedHashMap<String, Object>();
    dimensions.put("innerDiameter_mm", innerDiameter * 1000.0);
    dimensions.put("outerDiameter_mm", outerDiameter * 1000.0);
    dimensions.put("shellThickness_mm", shellThickness * 1000.0);
    dimensions.put("headThickness_mm", headThickness * 1000.0);
    dimensions.put("tangentLength_m", vesselLength);
    dimensions.put("ldRatio", vesselLength / Math.max(innerDiameter, 0.001));
    result.put("vesselDimensions", dimensions);

    // Filter elements
    Map<String, Object> elemData = new LinkedHashMap<String, Object>();
    elemData.put("numberOfElements", requiredElements);
    elemData.put("elementArea_m2", elementArea);
    elemData.put("totalFilterArea_m2", requiredElements * elementArea);
    elemData.put("maxFaceVelocity_ms", maxFaceVelocity);
    result.put("filterElements", elemData);

    // Weight
    Map<String, Object> weight = new LinkedHashMap<String, Object>();
    weight.put("emptyVessel_kg", emptyVesselWeight);
    weight.put("filterElements_kg", elementWeight);
    weight.put("totalEquipped_kg", totalEquippedWeight);
    result.put("weight", weight);

    // Cost estimation
    Map<String, Object> cost = new LinkedHashMap<String, Object>();
    cost.put("equipmentPurchase_USD", getEquipmentCostUSD());
    cost.put("installed_USD", getInstalledCostUSD());
    cost.put("annualMaintenance_USD", getAnnualMaintenanceCostUSD());
    cost.put("lifecycle25yr_USD", getLifecycleCostUSD(25.0));
    cost.put("steelRate_USDperKg", steelCostPerKg);
    cost.put("elementRate_USDperUnit", elementCostUSD);
    cost.put("installationFactor", installationFactor);
    cost.put("labourRate_USDperHr", labourRateUSD);
    cost.put("elementChangeHours", elementChangeHours);
    result.put("costEstimation", cost);

    // BOM
    result.put("billOfMaterials", generateBillOfMaterials());

    // Subclass-specific sections
    addEquipmentSpecificJson(result);

    return new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create()
        .toJson(result);
  }

  // ============================================================================
  // Getters and Setters
  // ============================================================================

  /**
   * Sets the material grade for the vessel shell.
   *
   * @param grade material grade (e.g., "SA-516-70", "SA-516-60")
   */
  public void setMaterialGrade(String grade) {
    this.materialGrade = grade;
  }

  /**
   * Returns the material grade.
   *
   * @return material grade string
   */
  public String getMaterialGrade() {
    return materialGrade;
  }

  /**
   * Sets the allowable stress for the material at design temperature.
   *
   * @param stressMPa allowable stress in MPa
   */
  public void setAllowableStress(double stressMPa) {
    this.allowableStress = stressMPa;
  }

  /**
   * Sets the joint efficiency.
   *
   * @param efficiency joint efficiency (0.0 to 1.0)
   */
  public void setJointEfficiency(double efficiency) {
    this.jointEfficiency = efficiency;
  }

  /**
   * Sets the corrosion allowance.
   *
   * @param allowanceMm corrosion allowance in millimetres
   */
  public void setCorrosionAllowanceMm(double allowanceMm) {
    this.corrosionAllowance = allowanceMm / 1000.0;
  }

  /**
   * Sets the maximum gas face velocity through filter elements.
   *
   * @param velocity face velocity in m/s (typically 0.05 to 0.20)
   */
  public void setMaxFaceVelocity(double velocity) {
    this.maxFaceVelocity = velocity;
  }

  /**
   * Sets the area per filter element.
   *
   * @param areaM2 element filtration area in m2
   */
  public void setElementArea(double areaM2) {
    this.elementArea = areaM2;
  }

  /**
   * Sets the cost per filter element.
   *
   * @param costUSD cost per element in USD
   */
  public void setElementCostUSD(double costUSD) {
    this.elementCostUSD = costUSD;
  }

  /**
   * Sets the steel cost per kg.
   *
   * @param costPerKg cost in USD per kg
   */
  public void setSteelCostPerKg(double costPerKg) {
    this.steelCostPerKg = costPerKg;
  }

  /**
   * Sets the installation factor.
   *
   * @param factor multiplier on equipment cost for installation
   */
  public void setInstallationFactor(double factor) {
    this.installationFactor = factor;
  }

  /**
   * Sets the design pressure margin.
   *
   * @param margin pressure margin factor (e.g., 1.10 for 10%)
   */
  public void setDesignPressureMargin(double margin) {
    this.designPressureMargin = margin;
  }

  /**
   * Returns the design pressure in bara.
   *
   * @return design pressure
   */
  public double getDesignPressure() {
    return designPressure;
  }

  /**
   * Returns the design temperature in Celsius.
   *
   * @return design temperature
   */
  public double getDesignTemperatureC() {
    return designTemperatureC;
  }

  /**
   * Returns the shell thickness in metres.
   *
   * @return shell thickness
   */
  public double getShellThickness() {
    return shellThickness;
  }

  /**
   * Returns the vessel tangent-to-tangent length.
   *
   * @return vessel length in metres
   */
  public double getVesselLength() {
    return vesselLength;
  }

  /**
   * Returns the required number of filter elements.
   *
   * @return element count
   */
  public int getRequiredElements() {
    return requiredElements;
  }

  /**
   * Returns the empty vessel weight in kg.
   *
   * @return vessel weight
   */
  public double getEmptyVesselWeight() {
    return emptyVesselWeight;
  }

  /**
   * Returns the total equipped weight in kg.
   *
   * @return total weight including elements and accessories
   */
  public double getTotalEquippedWeight() {
    return totalEquippedWeight;
  }

  /**
   * Returns the filter element area in m2.
   *
   * @return element area
   */
  public double getElementArea() {
    return elementArea;
  }

  /**
   * Returns the head thickness in metres.
   *
   * @return head thickness
   */
  public double getHeadThickness() {
    return headThickness;
  }

  /**
   * Sets the design temperature margin in Celsius.
   *
   * @param marginC temperature margin in degrees Celsius
   */
  public void setDesignTemperatureMarginC(double marginC) {
    this.designTemperatureMarginC = marginC;
  }

  /**
   * Sets the minimum L/D ratio for the vessel.
   *
   * @param ratio minimum L/D ratio
   */
  public void setMinLDRatio(double ratio) {
    this.minLDRatio = ratio;
  }

  /**
   * Sets the maximum L/D ratio for the vessel.
   *
   * @param ratio maximum L/D ratio
   */
  public void setMaxLDRatio(double ratio) {
    this.maxLDRatio = ratio;
  }

  /**
   * Sets the labour rate for element changes.
   *
   * @param rateUSD labour rate in USD per hour
   */
  public void setLabourRateUSD(double rateUSD) {
    this.labourRateUSD = rateUSD;
  }

  /**
   * Sets the hours required for an element change.
   *
   * @param hours hours per change
   */
  public void setElementChangeHours(double hours) {
    this.elementChangeHours = hours;
  }
}
