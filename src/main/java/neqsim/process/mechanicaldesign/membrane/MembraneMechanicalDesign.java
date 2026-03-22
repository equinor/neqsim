package neqsim.process.mechanicaldesign.membrane;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.membrane.MembraneSeparator;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * Mechanical design for membrane separation modules.
 *
 * <p>
 * Covers hollow-fiber and spiral-wound module sizing, pressure vessel housing design per ASME VIII,
 * membrane area calculation from permeability data, stage-cut optimization, and weight/cost
 * estimation. Applicable to gas separation (CO2/CH4, N2/O2, H2 recovery) and vapor permeation.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class MembraneMechanicalDesign extends MechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  // ============================================================================
  // Design Parameters
  // ============================================================================

  /** Module configuration: "HOLLOW_FIBER" or "SPIRAL_WOUND". */
  private String moduleType = "HOLLOW_FIBER";

  /** Number of modules required. */
  private int numberOfModules = 0;

  /** Membrane area per module in m2. */
  private double areaPerModule = 100.0;

  /** Total required membrane area in m2. */
  private double totalMembraneArea = 0.0;

  /** Module housing inner diameter in meters. */
  private double housingDiameter = 0.2;

  /** Module housing length in meters. */
  private double housingLength = 1.2;

  /** Housing wall thickness in mm. */
  private double housingWallThickness = 0.0;

  /** Design pressure in bara. */
  private double designPressure = 0.0;

  /** Design pressure margin factor. */
  private double designPressureMargin = 1.10;

  /** Design temperature margin above max operating in Celsius. */
  private double designTemperatureMarginC = 25.0;

  /** Membrane replacement interval in months. */
  private int membraneLifeMonths = 60;

  /** Feed-side pressure drop in bar. */
  private double feedPressureDrop = 0.5;

  /** Permeate-side pressure drop in bar. */
  private double permeatePressureDrop = 0.2;

  /** Stage cut (permeate fraction). */
  private double stageCut = 0.0;

  /** Permeate purity (mole fraction of target component). */
  private double permeatePurity = 0.0;

  /** Total weight of all modules in kg. */
  private double totalModuleWeight = 0.0;

  /** Skid weight including frame in kg. */
  private double totalSkidWeight = 0.0;

  /** Skid footprint in m2. */
  private double skidFootprint = 0.0;

  /** Allowable stress for housing material in MPa. */
  private double allowableStressMPa = 137.9; // SA-516-70 at moderate temp

  /**
   * Constructor for MembraneMechanicalDesign.
   *
   * @param equipment the membrane separator equipment
   */
  public MembraneMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();

    MembraneSeparator membrane = (MembraneSeparator) getProcessEquipment();
    if (membrane.getInletStream() == null || membrane.getInletStream().getThermoSystem() == null) {
      return;
    }

    double feedPressure = membrane.getInletStream().getPressure("bara");
    double feedTemperatureC = membrane.getInletStream().getTemperature("C");
    double feedFlowMoleSec = membrane.getInletStream().getFlowRate("mole/sec");

    // Get membrane area from equipment if set
    double equipmentArea = membrane.getMembraneArea();
    if (equipmentArea > 0) {
      totalMembraneArea = equipmentArea;
    } else {
      // Estimate based on flow rate and typical flux
      // Typical gas membrane flux: 10-100 GPU = 3.35e-10 to 3.35e-9 mol/(m2*s*Pa)
      double typicalFluxMolM2sPa = 1.0e-9;
      double drivingPressurePa = feedPressure * 0.5 * 1.0e5; // assume 50% pressure ratio
      totalMembraneArea = feedFlowMoleSec / (typicalFluxMolM2sPa * drivingPressurePa);
      totalMembraneArea = Math.max(totalMembraneArea, 1.0);
    }

    // === Module Count ===
    if ("SPIRAL_WOUND".equals(moduleType)) {
      areaPerModule = 40.0; // m2 per element, 6 elements per vessel
      housingDiameter = 0.2; // 8 inch standard
      housingLength = 6.0; // 6-element vessel
    } else {
      areaPerModule = 100.0; // hollow fiber: higher packing density
      housingDiameter = 0.3;
      housingLength = 1.2;
    }
    numberOfModules = Math.max(1, (int) Math.ceil(totalMembraneArea / areaPerModule));

    // === Stage Cut ===
    if (membrane.getPermeateStream() != null
        && membrane.getPermeateStream().getThermoSystem() != null) {
      double permeateFlow = membrane.getPermeateStream().getFlowRate("mole/sec");
      if (feedFlowMoleSec > 0) {
        stageCut = permeateFlow / feedFlowMoleSec;
      }
    }

    // === Design Pressure ===
    designPressure = feedPressure * designPressureMargin;
    double designTemperatureC2 = feedTemperatureC + designTemperatureMarginC;

    // === Housing Wall Thickness (ASME VIII Div 1) ===
    // t = PR / (SE - 0.6P) + CA
    double pMPa = designPressure * 0.1; // bara to MPa
    double rM = housingDiameter / 2.0;
    double ca = getCorrosionAllowance() / 1000.0; // mm to m
    housingWallThickness =
        (pMPa * rM * 1000.0) / (allowableStressMPa * getJointEfficiency() - 0.6 * pMPa) + ca;
    housingWallThickness = Math.max(housingWallThickness, 4.0); // minimum 4mm

    // === Weight Estimation ===
    double steelDensity = 7850.0;
    double shellVolume =
        Math.PI * housingDiameter * housingLength * (housingWallThickness / 1000.0);
    double singleHousingWeight = shellVolume * steelDensity;

    // Module internals weight
    double internalsWeightPerModule;
    if ("SPIRAL_WOUND".equals(moduleType)) {
      internalsWeightPerModule = 15.0 * (areaPerModule / 40.0); // ~15 kg per element
    } else {
      internalsWeightPerModule = 25.0; // hollow fiber cartridge
    }

    totalModuleWeight = numberOfModules * (singleHousingWeight + internalsWeightPerModule);

    // Skid: frame, piping, instrumentation add ~40%
    totalSkidWeight = totalModuleWeight * 1.40;

    // === Dimensions ===
    // Modules arranged in rows
    int modulesPerRow = Math.min(numberOfModules, 6);
    int rows = (int) Math.ceil((double) numberOfModules / modulesPerRow);
    double skidLength = modulesPerRow * (housingLength + 0.3) + 1.0;
    double skidWidth = rows * (housingDiameter + 0.5) + 1.0;
    skidFootprint = skidLength * skidWidth;

    // === Set base class fields ===
    innerDiameter = housingDiameter;
    outerDiameter = housingDiameter + 2.0 * housingWallThickness / 1000.0;
    wallThickness = housingWallThickness;
    tantanLength = housingLength;
    moduleLength = skidLength;
    moduleWidth = skidWidth;
    moduleHeight = 2.5;
    setWeightTotal(totalSkidWeight);
  }

  /**
   * Gets the module configuration type.
   *
   * @return "HOLLOW_FIBER" or "SPIRAL_WOUND"
   */
  public String getModuleType() {
    return moduleType;
  }

  /**
   * Sets the module configuration type.
   *
   * @param moduleType "HOLLOW_FIBER" or "SPIRAL_WOUND"
   */
  public void setModuleType(String moduleType) {
    this.moduleType = moduleType;
  }

  /**
   * Gets the number of modules.
   *
   * @return number of modules
   */
  public int getNumberOfModules() {
    return numberOfModules;
  }

  /**
   * Gets the total membrane area.
   *
   * @return area in m2
   */
  public double getTotalMembraneArea() {
    return totalMembraneArea;
  }

  /**
   * Gets the stage cut.
   *
   * @return stage cut (0-1)
   */
  public double getStageCut() {
    return stageCut;
  }

  /**
   * Gets the housing wall thickness.
   *
   * @return wall thickness in mm
   */
  public double getHousingWallThickness() {
    return housingWallThickness;
  }

  /**
   * Gets the total skid weight.
   *
   * @return weight in kg
   */
  public double getTotalSkidWeight() {
    return totalSkidWeight;
  }

  /**
   * Gets the skid footprint.
   *
   * @return footprint in m2
   */
  public double getSkidFootprint() {
    return skidFootprint;
  }

  /**
   * Sets the membrane area per module.
   *
   * @param area area in m2
   */
  public void setAreaPerModule(double area) {
    this.areaPerModule = area;
  }

  /**
   * Sets the membrane replacement interval.
   *
   * @param months replacement interval in months
   */
  public void setMembraneLifeMonths(int months) {
    this.membraneLifeMonths = months;
  }

  /**
   * Gets the membrane replacement interval.
   *
   * @return interval in months
   */
  public int getMembraneLifeMonths() {
    return membraneLifeMonths;
  }

  /**
   * Gets the total module weight.
   *
   * @return weight in kg
   */
  public double getTotalModuleWeight() {
    return totalModuleWeight;
  }
}
