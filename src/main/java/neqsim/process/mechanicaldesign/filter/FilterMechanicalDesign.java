package neqsim.process.mechanicaldesign.filter;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.costestimation.filter.FilterCostEstimate;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.filter.Filter;
import neqsim.process.equipment.filter.FilterType;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * General mechanical design for oil and gas process filters.
 *
 * <p>
 * This class sizes a filter pressure vessel (diameter, length, wall thickness), selects filter elements based on the
 * required process face velocity, estimates weight and cost, and calculates maintenance intervals. It follows the
 * pressure vessel design methodology (ASME Section VIII Division 1) and configurable, type-specific filtration
 * practices. The calculation is preliminary engineering and does not replace code calculations, certified material
 * allowables, nozzle reinforcement, external-load checks, fatigue assessment, or vendor element qualification.
 * </p>
 *
 * <p>
 * Subclasses (e.g., {@link SulfurFilterMechanicalDesign}) can override the template methods to provide
 * filter-type-specific behaviour such as retrieving flow rates from specialised equipment classes, adding
 * contaminant-specific JSON sections, or customising the bill of materials.
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

  /** Minimum absolute design-pressure increment above operation in bar. */
  private double minimumDesignPressureMarginBar = 3.5;

  /** Design temperature margin above max operating in Celsius. */
  private double designTemperatureMarginC = 25.0;

  /** Material grade for pressure vessel shell. */
  private String materialGrade = "SA-516-70";

  /** Maximum allowable stress for SA-516-70 at design temperature (MPa). */
  private double allowableStress = 138.0;

  /** Configurable welded-joint efficiency used in the screening calculation. */
  private double jointEfficiency = 0.85;

  /** Corrosion allowance in metres. */
  private double corrosionAllowance = 0.003;

  /** Maximum gas face velocity through filter elements (m/s). */
  private double maxFaceVelocity = 0.15;

  /** Filter element area per element (m2). Typical cartridge: 0.5-2.0 m2. */
  private double elementArea = 1.0;

  /** Whether maximum face velocity was explicitly overridden. */
  private boolean maxFaceVelocityUserSpecified = false;

  /** Whether element area was explicitly overridden. */
  private boolean elementAreaUserSpecified = false;

  /** Maximum process velocity through inlet and outlet nozzles in m/s. */
  private double maxNozzleVelocity = Double.NaN;

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

  /** Required inlet/outlet nozzle inside diameter in metres. */
  private double requiredNozzleDiameter = 0.0;

  /** Selected preliminary nominal nozzle diameter in millimetres. */
  private double selectedNozzleDiameterMm = 0.0;

  /** Actual element face velocity at the design flow in m/s. */
  private double calculatedFaceVelocity = 0.0;

  /** Actual inlet/outlet nozzle velocity at the selected diameter in m/s. */
  private double calculatedNozzleVelocity = 0.0;

  /** Current differential-pressure utilization of the terminal replacement limit. */
  private double terminalDifferentialPressureUtilization = 0.0;

  /** Current differential-pressure utilization of the element collapse rating. */
  private double elementCollapsePressureUtilization = 0.0;

  /** Design warnings and failed preliminary checks. */
  private List<String> designWarnings = new ArrayList<String>();

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
    costEstimate = new FilterCostEstimate(this);
  }

  /**
   * Restores safe defaults for fields added to older serialized mechanical designs.
   *
   * @param input serialized object input
   * @throws IOException if the serialized form cannot be read
   * @throws ClassNotFoundException if a serialized class cannot be resolved
   */
  private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
    input.defaultReadObject();
    if (designWarnings == null) {
      designWarnings = new ArrayList<String>();
    }
    if (maxNozzleVelocity <= 0.0) {
      maxNozzleVelocity = Double.NaN;
    }
  }

  // ============================================================================
  // Template methods — override in subclasses for filter-type-specific behaviour
  // ============================================================================

  /**
   * Returns the gas mass flow rate in kg/hr for element sizing.
   *
   * <p>
   * Default implementation reads from the inlet stream. Subclasses may override to use a filter-type-specific method
   * (e.g., a separate tracked flow rate field).
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
    return ((Filter) getProcessEquipment()).getNumberOfElements();
  }

  /**
   * Updates the element count on the equipment after sizing.
   *
   * <p>
   * Default is a no-op. Subclasses should override to push the calculated count back to the equipment object.
   * </p>
   *
   * @param count the calculated number of filter elements
   */
  protected void updateEquipmentElementCount(int count) {
    ((Filter) getProcessEquipment()).setNumberOfElements(count);
  }

  /**
   * Returns the filter element change interval in hours.
   *
   * <p>
   * Default returns {@link Double#MAX_VALUE} (no scheduled replacement). Subclasses override to use the specific
   * filter's change interval calculation.
   * </p>
   *
   * @return change interval in hours
   */
  protected double getFilterChangeIntervalHours() {
    Filter filter = (Filter) getProcessEquipment();
    double capturedRate = filter.getSolidsLoadingRate();
    if (capturedRate <= 0.0 || Double.isInfinite(filter.getLoadingCapacity())) {
      return Double.MAX_VALUE;
    }
    return Math.max(0.0, filter.getLoadingCapacity() - filter.getSolidsLoading()) / capturedRate;
  }

  /**
   * Returns the equipment type name for JSON reporting.
   *
   * @return equipment type string
   */
  protected String getEquipmentTypeName() {
    return ((Filter) getProcessEquipment()).getFilterServiceType().getDisplayName() + " Filter Vessel";
  }

  /**
   * Adds equipment-specific data sections to the JSON report map.
   *
   * <p>
   * Called by {@link #toJson()} after the common sections have been populated. Subclasses override to add
   * contaminant-specific data (e.g., sulfur removal rates, particle size data).
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
    FilterType type = ((Filter) getProcessEquipment()).getFilterServiceType();
    if (type == FilterType.Y_STRAINER || type == FilterType.BASKET_STRAINER) {
      return "316L SS screen";
    }
    if (type == FilterType.GRANULAR_MEDIA || type == FilterType.BACKWASHABLE_MEDIA
        || type == FilterType.ACTIVATED_CARBON) {
      return type.getDisplayName() + " media and support screens";
    }
    return "316L SS / synthetic or glass-fibre media";
  }

  // ============================================================================
  // Core Design Calculations
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();

    Filter filter = (Filter) getProcessEquipment();

    designWarnings.clear();
    FilterType type = filter.getFilterServiceType();

    // --- Operating and design conditions ---
    double operatingPressure = getMaxOperationPressure();
    double operatingTemperatureK = getMaxOperationTemperature();
    if (filter.getInletStream() != null) {
      if (!Double.isFinite(operatingPressure) || operatingPressure < 1.0) {
        operatingPressure = filter.getInletStream().getPressure("bara");
      }
      if (!Double.isFinite(operatingTemperatureK) || operatingTemperatureK < 150.0) {
        operatingTemperatureK = filter.getInletStream().getTemperature("K");
      }
    }
    double operatingTempC = operatingTemperatureK - 273.15;
    setMaxOperationPressure(operatingPressure);
    setMaxOperationTemperature(operatingTemperatureK);
    designPressure = Math.max(operatingPressure * designPressureMargin,
        operatingPressure + minimumDesignPressureMarginBar);
    designTemperatureC = operatingTempC + designTemperatureMarginC;

    // --- Actual flow and preliminary type defaults ---
    double volumetricFlowM3s = 0.0;
    double fluidDensity = 50.0;
    if (filter.getInletStream() != null && filter.getInletStream().getThermoSystem() != null) {
      try {
        volumetricFlowM3s = Math.max(0.0, filter.getInletStream().getFlowRate("m3/hr") / 3600.0);
        fluidDensity = filter.getInletStream().getThermoSystem().getDensity("kg/m3");
      } catch (Exception e) {
        volumetricFlowM3s = 0.0;
      }
    }
    if (!Double.isFinite(fluidDensity) || fluidDensity <= 0.0) {
      fluidDensity = 50.0;
    }
    if (volumetricFlowM3s <= 0.0) {
      volumetricFlowM3s = Math.max(0.0, getGasFlowRateKgHr() / 3600.0 / fluidDensity);
    }

    double designFaceVelocity = maxFaceVelocityUserSpecified ? maxFaceVelocity
        : type.getDefaultMaximumFaceVelocity();
    double designElementArea = elementAreaUserSpecified ? elementArea : type.getDefaultElementArea();
    maxFaceVelocity = designFaceVelocity;
    elementArea = designElementArea;
    double requiredArea = volumetricFlowM3s / Math.max(designFaceVelocity, 1.0e-12);

    // --- Filter elements and vessel diameter ---
    boolean granularMedia = type == FilterType.GRANULAR_MEDIA || type == FilterType.BACKWASHABLE_MEDIA
        || type == FilterType.ACTIVATED_CARBON;
    if (granularMedia) {
      requiredElements = 1;
      innerDiameter = Math.max(Math.sqrt(4.0 * requiredArea / Math.PI), 0.5);
      calculatedFaceVelocity = volumetricFlowM3s / (Math.PI * innerDiameter * innerDiameter / 4.0);
    } else {
      requiredElements = (int) Math.ceil(requiredArea / Math.max(designElementArea, 1.0e-12));
      requiredElements = Math.max(1, Math.max(requiredElements, getInitialElementCount()));
      double bundleDiameter = Math.sqrt(requiredElements) * type.getDefaultElementDiameter() * 1.8;
      innerDiameter = Math.max(bundleDiameter + 0.2, 0.5);
      calculatedFaceVelocity = volumetricFlowM3s / (requiredElements * designElementArea);
    }
    updateEquipmentElementCount(requiredElements);

    // --- Vessel length from element or media depth and L/D constraints ---
    double activeLength = granularMedia ? filter.getMediaBedDepth() : type.getDefaultElementLength();
    vesselLength = activeLength + innerDiameter;
    double ldRatio = vesselLength / innerDiameter;
    if (ldRatio < minLDRatio) {
      vesselLength = innerDiameter * minLDRatio;
    } else if (ldRatio > maxLDRatio) {
      innerDiameter = vesselLength / maxLDRatio;
    }
    if (granularMedia) {
      double mediaArea = Math.PI * innerDiameter * innerDiameter / 4.0;
      calculatedFaceVelocity = volumetricFlowM3s / mediaArea;
      filter.setMediaGeometry(mediaArea, filter.getMediaBedDepth(), filter.getMediaParticleDiameter(),
          filter.getMediaVoidFraction());
    }

    // --- Inlet/outlet nozzle velocity sizing ---
    double designNozzleVelocity = Double.isFinite(maxNozzleVelocity) ? maxNozzleVelocity
        : (fluidDensity < 200.0 ? 20.0 : 3.0);
    requiredNozzleDiameter = volumetricFlowM3s > 0.0
        ? Math.sqrt(4.0 * volumetricFlowM3s / (Math.PI * designNozzleVelocity)) : 0.0;
    selectedNozzleDiameterMm = selectNominalNozzleDiameterMm(requiredNozzleDiameter * 1000.0);
    double selectedArea = Math.PI * Math.pow(selectedNozzleDiameterMm / 1000.0, 2.0) / 4.0;
    calculatedNozzleVelocity = selectedArea > 0.0 ? volumetricFlowM3s / selectedArea : 0.0;

    // --- Pressure boundary screening calculation ---
    // Shell: t = PR / (SE - 0.6P), using gauge pressure for membrane stress.
    double referencePressureBar = 1.01325;
    double P = Math.max(0.0, designPressure - referencePressureBar) * 0.1;
    double S = allowableStress;
    double E = jointEfficiency;
    double R = innerDiameter / 2.0;
    double shellDenominator = S * E - 0.6 * P;
    if (shellDenominator <= 0.0) {
      throw new IllegalStateException("Filter shell design pressure exceeds the range of the configured material data");
    }
    shellThickness = (P * R) / shellDenominator + corrosionAllowance;
    shellThickness = Math.max(shellThickness, 0.006); // min 6mm

    // Head (2:1 ellipsoidal): t = PD / (2SE - 0.2P)
    double D = innerDiameter;
    headThickness = (P * D) / (2.0 * S * E - 0.2 * P) + corrosionAllowance;
    headThickness = Math.max(headThickness, shellThickness);

    outerDiameter = innerDiameter + 2.0 * shellThickness;
    wallThickness = shellThickness;

    terminalDifferentialPressureUtilization = filter.getDifferentialPressureUtilization();
    elementCollapsePressureUtilization = filter.getElementCollapsePressure() > 0.0
        ? filter.getUnrestrictedDeltaP() / filter.getElementCollapsePressure() : Double.POSITIVE_INFINITY;
    setMaxDesignPressureDrop(filter.getTerminalDeltaP());
    populateDesignWarnings(filter, calculatedNozzleVelocity, designNozzleVelocity);

    // --- Weight estimation ---
    double steelDensity = 7850.0; // kg/m3
    double shellArea = Math.PI * innerDiameter * vesselLength;
    double headArea = 2.0 * Math.PI * Math.pow(innerDiameter / 2.0, 2) * 1.084; // 2:1 ellipsoidal
    emptyVesselWeight = (shellArea + headArea) * shellThickness * steelDensity;

    double accessoriesWeight = emptyVesselWeight * 0.30;

    if (granularMedia) {
      double mediaBulkDensity = type == FilterType.ACTIVATED_CARBON ? 500.0 : 1000.0;
      elementWeight = Math.PI * innerDiameter * innerDiameter / 4.0 * filter.getMediaBedDepth()
          * mediaBulkDensity;
    } else {
      double weightPerElement = Math.max(2.0, type.getDefaultElementLength() * 10.0);
      elementWeight = requiredElements * weightPerElement;
    }

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

  /**
   * Selects the next larger preliminary nominal nozzle inside diameter.
   *
   * @param requiredDiameterMm required hydraulic inside diameter in mm
   * @return selected nominal diameter in mm
   */
  private double selectNominalNozzleDiameterMm(double requiredDiameterMm) {
    double[] nominalDiametersMm = {15.0, 20.0, 25.0, 40.0, 50.0, 65.0, 80.0, 100.0, 150.0, 200.0,
        250.0, 300.0, 350.0, 400.0, 450.0, 500.0, 600.0, 750.0, 900.0, 1050.0, 1200.0};
    if (requiredDiameterMm <= 0.0) {
      return 0.0;
    }
    for (double nominalDiameterMm : nominalDiametersMm) {
      if (nominalDiameterMm >= requiredDiameterMm) {
        return nominalDiameterMm;
      }
    }
    return requiredDiameterMm;
  }

  /**
   * Populates preliminary integrity, operability, and sizing warnings.
   *
   * @param filter filter equipment
   * @param nozzleVelocity calculated nozzle velocity in m/s
   * @param maximumNozzleVelocity configured maximum nozzle velocity in m/s
   */
  private void populateDesignWarnings(Filter filter, double nozzleVelocity, double maximumNozzleVelocity) {
    if (terminalDifferentialPressureUtilization >= 1.0) {
      designWarnings.add("Terminal differential pressure is exceeded; replace or backwash the element");
    }
    if (elementCollapsePressureUtilization >= 1.0) {
      designWarnings.add("Calculated differential pressure exceeds the element collapse/burst rating");
    }
    if (filter.getTerminalDeltaP() >= filter.getElementCollapsePressure()) {
      designWarnings.add("Terminal replacement differential pressure must be below the element collapse rating");
    }
    if (calculatedFaceVelocity > maxFaceVelocity * 1.000001) {
      designWarnings.add("Calculated element face velocity exceeds the configured design limit");
    }
    if (nozzleVelocity > maximumNozzleVelocity * 1.000001) {
      designWarnings.add("Selected preliminary nozzle size exceeds the configured velocity limit");
    }
    if (!filter.isElementIntegrityVerified()) {
      designWarnings.add("Filter element fabrication integrity has not been recorded as verified");
    }
    if (filter.getPerformanceCurve().size() == 0 && filter.getNominalRemovalEfficiency() <= 0.0) {
      designWarnings.add("No particle-size efficiency or beta-ratio test data have been configured");
    }
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
  @Override
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
    elements.put("item", ((Filter) getProcessEquipment()).getFilterServiceType().getDisplayName()
        + " Elements/Media");
    elements.put("material", getElementMaterialDescription());
    elements.put("quantity", requiredElements);
    elements.put("weight_kg", elementWeight);
    elements.put("unitCost_USD", elementCostUSD);
    bom.add(elements);

    Map<String, Object> nozzles = new LinkedHashMap<String, Object>();
    nozzles.put("item", "Inlet/Outlet Nozzles & Flanges");
    nozzles.put("material", materialGrade);
    nozzles.put("quantity", 3); // inlet, outlet, drain
    nozzles.put("nominalDiameter_mm", selectedNozzleDiameterMm);
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
    result.put("filterType", filter.getFilterServiceType().name());
    result.put("designCode", "ASME Section VIII Division 1 screening equations");
    result.put("materialGrade", materialGrade);

    Map<String, Object> standardBasis = new LinkedHashMap<String, Object>();
    standardBasis.put("pressureBoundary", "ASME Section VIII Division 1 screening only");
    standardBasis.put("particlePerformance", filter.getPerformanceCurve().getTestStandard());
    standardBasis.put("pressureDropTest", filter.getPressureDropCurve().getTestStandard());
    standardBasis.put("elementIntegrity", "Project/vendor verification; ISO 2942 method may be recorded");
    standardBasis.put("disclaimer", "Not a certified code calculation or vendor element qualification");
    result.put("standardBasis", standardBasis);

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
    boolean mediaFilter = filter.getFilterServiceType() == FilterType.GRANULAR_MEDIA
        || filter.getFilterServiceType() == FilterType.BACKWASHABLE_MEDIA
        || filter.getFilterServiceType() == FilterType.ACTIVATED_CARBON;
    elemData.put("totalFilterArea_m2", mediaFilter ? filter.getMediaArea() : requiredElements * elementArea);
    elemData.put("maxFaceVelocity_ms", maxFaceVelocity);
    elemData.put("calculatedFaceVelocity_ms", calculatedFaceVelocity);
    elemData.put("terminalDeltaP_bar", filter.getTerminalDeltaP());
    elemData.put("collapsePressure_bar", filter.getElementCollapsePressure());
    elemData.put("integrityVerified", filter.isElementIntegrityVerified());
    result.put("filterElements", elemData);

    Map<String, Object> nozzles = new LinkedHashMap<String, Object>();
    nozzles.put("requiredInsideDiameter_mm", requiredNozzleDiameter * 1000.0);
    nozzles.put("selectedNominalDiameter_mm", selectedNozzleDiameterMm);
    nozzles.put("calculatedVelocity_ms", calculatedNozzleVelocity);
    result.put("nozzleSizing", nozzles);

    Map<String, Object> checks = new LinkedHashMap<String, Object>();
    checks.put("terminalDifferentialPressureUtilization", terminalDifferentialPressureUtilization);
    checks.put("elementCollapsePressureUtilization", elementCollapsePressureUtilization);
    checks.put("terminalDifferentialPressurePassed", terminalDifferentialPressureUtilization < 1.0);
    checks.put("elementCollapsePressurePassed", elementCollapsePressureUtilization < 1.0);
    checks.put("warnings", new ArrayList<String>(designWarnings));
    result.put("mechanicalChecks", checks);

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

    return new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create().toJson(result);
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
  @Override
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
    if (!Double.isFinite(velocity) || velocity <= 0.0) {
      throw new IllegalArgumentException("Maximum face velocity must be finite and positive");
    }
    this.maxFaceVelocity = velocity;
    this.maxFaceVelocityUserSpecified = true;
  }

  /**
   * Sets the area per filter element.
   *
   * @param areaM2 element filtration area in m2
   */
  public void setElementArea(double areaM2) {
    if (!Double.isFinite(areaM2) || areaM2 <= 0.0) {
      throw new IllegalArgumentException("Element area must be finite and positive");
    }
    this.elementArea = areaM2;
    this.elementAreaUserSpecified = true;
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
    if (!Double.isFinite(margin) || margin < 1.0) {
      throw new IllegalArgumentException("Design pressure margin factor must be at least one");
    }
    this.designPressureMargin = margin;
  }

  /**
   * Sets the minimum design-pressure increment above the maximum operating pressure.
   *
   * @param marginBar minimum increment in bar
   */
  public void setMinimumDesignPressureMarginBar(double marginBar) {
    if (!Double.isFinite(marginBar) || marginBar < 0.0) {
      throw new IllegalArgumentException("Minimum design pressure margin must be finite and non-negative");
    }
    this.minimumDesignPressureMarginBar = marginBar;
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

  /** @return calculated element face velocity in m/s */
  public double getCalculatedFaceVelocity() {
    return calculatedFaceVelocity;
  }

  /** @return required hydraulic nozzle inside diameter in m */
  public double getRequiredNozzleDiameter() {
    return requiredNozzleDiameter;
  }

  /** @return selected preliminary nominal nozzle diameter in mm */
  public double getSelectedNozzleDiameterMm() {
    return selectedNozzleDiameterMm;
  }

  /** @return calculated velocity through the selected nozzle in m/s */
  public double getCalculatedNozzleVelocity() {
    return calculatedNozzleVelocity;
  }

  /**
   * Sets the maximum nozzle velocity used for preliminary inlet/outlet sizing.
   *
   * @param velocity maximum velocity in m/s
   */
  public void setMaxNozzleVelocity(double velocity) {
    if (!Double.isFinite(velocity) || velocity <= 0.0) {
      throw new IllegalArgumentException("Maximum nozzle velocity must be finite and positive");
    }
    maxNozzleVelocity = velocity;
  }

  /** @return terminal differential-pressure utilization */
  public double getTerminalDifferentialPressureUtilization() {
    return terminalDifferentialPressureUtilization;
  }

  /** @return element collapse/burst differential-pressure utilization */
  public double getElementCollapsePressureUtilization() {
    return elementCollapsePressureUtilization;
  }

  /** @return true when terminal and collapse differential-pressure checks pass */
  public boolean isDifferentialPressureDesignAcceptable() {
    return terminalDifferentialPressureUtilization < 1.0 && elementCollapsePressureUtilization < 1.0;
  }

  /** @return defensive copy of preliminary design warnings */
  public List<String> getDesignWarnings() {
    return new ArrayList<String>(designWarnings);
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
