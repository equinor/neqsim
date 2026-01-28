package neqsim.process.mechanicaldesign.separator;

import neqsim.process.mechanicaldesign.MechanicalDesignResponse;

/**
 * Response class for separator mechanical design JSON export.
 *
 * <p>
 * Extends {@link MechanicalDesignResponse} with separator-specific parameters including vessel
 * sizing, internals, and process design data per API 12J and ASME VIII.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class SeparatorMechanicalDesignResponse extends MechanicalDesignResponse {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  // ============================================================================
  // Separator-Specific Parameters
  // ============================================================================

  /** Separator orientation (horizontal, vertical). */
  private String orientation;

  /** Separator type (two-phase, three-phase, test separator). */
  private String separatorType;

  /** Gas load factor (K-factor). */
  private double gasLoadFactor;

  /** Volumetric design safety factor. */
  private double volumeSafetyFactor;

  /** Liquid level fraction (Fg). */
  private double liquidLevelFraction;

  /** Liquid retention time [s]. */
  private double retentionTime;

  /** Demister type (wire mesh, vane, cyclone). */
  private String demisterType;

  /** Demister efficiency. */
  private double demisterEfficiency;

  /** Number of inlet nozzles. */
  private int numberOfInletNozzles;

  /** Inlet nozzle diameter [mm]. */
  private double inletNozzleDiameter;

  /** Gas outlet nozzle diameter [mm]. */
  private double gasOutletNozzleDiameter;

  /** Liquid outlet nozzle diameter [mm]. */
  private double liquidOutletNozzleDiameter;

  /** Water outlet nozzle diameter (for 3-phase) [mm]. */
  private double waterOutletNozzleDiameter;

  /** Head type (hemispherical, 2:1 ellipsoidal, torispherical). */
  private String headType;

  /** Head thickness [mm]. */
  private double headThickness;

  /** Shell course thickness [mm]. */
  private double shellThickness;

  /** Design code (ASME VIII Div 1, Div 2). */
  private String designCode;

  /** Gas design velocity [m/s]. */
  private double gasDesignVelocity;

  /** Actual gas velocity [m/s]. */
  private double actualGasVelocity;

  /** Allowable gas velocity [m/s]. */
  private double allowableGasVelocity;

  /** Design gas flow [Am³/h]. */
  private double designGasFlow;

  /** Design liquid flow [m³/h]. */
  private double designLiquidFlow;

  /** Design water flow (for 3-phase) [m³/h]. */
  private double designWaterFlow;

  /** Normal liquid level [m]. */
  private double normalLiquidLevel;

  /** High liquid level [m]. */
  private double highLiquidLevel;

  /** Low liquid level [m]. */
  private double lowLiquidLevel;

  /** Oil-water interface level (for 3-phase) [m]. */
  private double interfaceLevel;

  /** Liquid surge volume [m³]. */
  private double surgeVolume;

  /** Liquid holdup volume [m³]. */
  private double holdupVolume;

  /** Empty vessel weight [kg]. */
  private double emptyVesselWeight;

  /** Operating liquid volume [m³]. */
  private double operatingLiquidVolume;

  /** Liquid density [kg/m³]. */
  private double liquidDensity;

  /** Gas density [kg/m³]. */
  private double gasDensity;

  // ============================================================================
  // Liquid Level Design Parameters (added January 2026)
  // ============================================================================

  /** Effective length for liquid separation [m]. */
  private double effectiveLengthLiquid;

  /** Effective length for gas separation [m]. */
  private double effectiveLengthGas;

  // ============================================================================
  // Process Design Parameters (added for TR3500 compliance)
  // ============================================================================

  /** Foam allowance factor. */
  private double foamAllowanceFactor;

  /** Design droplet diameter for gas-liquid separation [um]. */
  private double dropletDiameterGasLiquid;

  /** Design droplet diameter for liquid-liquid separation [um]. */
  private double dropletDiameterLiquidLiquid;

  /** Design pressure margin factor. */
  private double designPressureMarginFactor;

  /** Design temperature margin [C]. */
  private double designTemperatureMarginC;

  /** Maximum gas velocity [m/s]. */
  private double maxGasVelocity;

  /** Maximum liquid velocity [m/s]. */
  private double maxLiquidVelocity;

  /** Demister pressure drop [mbar]. */
  private double demisterPressureDrop;

  /** Demister void fraction. */
  private double demisterVoidFraction;

  /** Minimum oil retention time [min]. */
  private double minOilRetentionTime;

  /** Minimum water retention time [min]. */
  private double minWaterRetentionTime;

  // ============================================================================
  // Liquid Level Design Parameters (added January 2026)
  // ============================================================================

  /** High-High Liquid Level fraction of ID. */
  private double hhllFraction;

  /** High Liquid Level fraction of ID. */
  private double hllFraction;

  /** Normal Liquid Level fraction of ID. */
  private double nllFraction;

  /** Low Liquid Level fraction of ID. */
  private double lllFraction;

  /** Weir height fraction of ID. */
  private double weirFraction;

  /** High Interface Level fraction of ID. */
  private double hilFraction;

  /** Normal Interface Level fraction of ID. */
  private double nilFraction;

  /** Low Interface Level fraction of ID. */
  private double lilFraction;

  /** High-High Liquid Level [m]. */
  private double hhll;

  /** High Liquid Level [m] - duplicate for explicit naming. */
  private double hll;

  /** Normal Liquid Level [m] - duplicate for explicit naming. */
  private double nll;

  /** Low Liquid Level [m] - duplicate for explicit naming. */
  private double lll;

  /** Weir height [m]. */
  private double weirHeight;

  /** High Interface Level [m]. */
  private double hil;

  /** Normal Interface Level [m]. */
  private double nil;

  /** Low Interface Level [m]. */
  private double lil;

  // ============================================================================
  // Constructors
  // ============================================================================

  /**
   * Default constructor.
   */
  public SeparatorMechanicalDesignResponse() {
    super();
    setEquipmentType("Separator");
    setDesignStandard("ASME VIII / API 12J");
  }

  /**
   * Constructor from SeparatorMechanicalDesign.
   *
   * @param mecDesign the separator mechanical design
   */
  public SeparatorMechanicalDesignResponse(SeparatorMechanicalDesign mecDesign) {
    super(mecDesign);
    setEquipmentType("Separator");
    setDesignStandard("ASME VIII / API 12J");
    populateFromSeparatorDesign(mecDesign);
  }

  /**
   * Populate separator-specific fields from SeparatorMechanicalDesign.
   *
   * @param mecDesign the separator mechanical design
   */
  public void populateFromSeparatorDesign(SeparatorMechanicalDesign mecDesign) {
    if (mecDesign == null) {
      return;
    }

    this.gasLoadFactor = mecDesign.getGasLoadFactor();
    this.volumeSafetyFactor = mecDesign.getVolumeSafetyFactor();
    this.liquidLevelFraction = mecDesign.getFg();
    this.retentionTime = mecDesign.getRetentionTime();

    // Populate nozzle sizes (convert m to mm)
    this.inletNozzleDiameter = mecDesign.getInletNozzleID() * 1000.0;
    this.gasOutletNozzleDiameter = mecDesign.getGasOutletNozzleID() * 1000.0;
    this.liquidOutletNozzleDiameter = mecDesign.getOilOutletNozzleID() * 1000.0;
    this.waterOutletNozzleDiameter = mecDesign.getWaterOutletNozzleID() * 1000.0;

    // Populate liquid levels
    this.normalLiquidLevel = mecDesign.getNLL();
    this.highLiquidLevel = mecDesign.getHLL();
    this.lowLiquidLevel = mecDesign.getLLL();

    // Populate effective lengths
    this.effectiveLengthLiquid = mecDesign.getEffectiveLengthLiquid();
    this.effectiveLengthGas = mecDesign.getEffectiveLengthGas();

    // Populate level fractions
    this.hhllFraction = mecDesign.getHHLLFraction();
    this.hllFraction = mecDesign.getHLLFraction();
    this.nllFraction = mecDesign.getNLLFraction();
    this.lllFraction = mecDesign.getLLLFraction();
    this.weirFraction = mecDesign.getWeirFraction();
    this.hilFraction = mecDesign.getHILFraction();
    this.nilFraction = mecDesign.getNILFraction();
    this.lilFraction = mecDesign.getLILFraction();

    // Populate absolute level heights
    this.hhll = mecDesign.getHHLL();
    this.hll = mecDesign.getHLL();
    this.nll = mecDesign.getNLL();
    this.lll = mecDesign.getLLL();
    this.weirHeight = mecDesign.getWeirHeight();
    this.hil = mecDesign.getHIL();
    this.nil = mecDesign.getNIL();
    this.lil = mecDesign.getLIL();

    // Populate process design parameters
    this.foamAllowanceFactor = mecDesign.getFoamAllowanceFactor();
    this.dropletDiameterGasLiquid = mecDesign.getDropletDiameterGasLiquid();
    this.dropletDiameterLiquidLiquid = mecDesign.getDropletDiameterLiquidLiquid();
    this.designPressureMarginFactor = mecDesign.getDesignPressureMargin();
    this.designTemperatureMarginC = mecDesign.getDesignTemperatureMarginC();
    this.maxGasVelocity = mecDesign.getMaxGasVelocityLimit();
    this.maxLiquidVelocity = mecDesign.getMaxLiquidVelocity();
    this.demisterPressureDrop = mecDesign.getDemisterPressureDrop();
    this.demisterVoidFraction = mecDesign.getDemisterVoidFraction();
    this.minOilRetentionTime = mecDesign.getMinOilRetentionTime();
    this.minWaterRetentionTime = mecDesign.getMinWaterRetentionTime();
  }

  // ============================================================================
  // Getters and Setters
  // ============================================================================

  public String getOrientation() {
    return orientation;
  }

  public void setOrientation(String orientation) {
    this.orientation = orientation;
  }

  public String getSeparatorType() {
    return separatorType;
  }

  public void setSeparatorType(String separatorType) {
    this.separatorType = separatorType;
  }

  public double getGasLoadFactor() {
    return gasLoadFactor;
  }

  public void setGasLoadFactor(double gasLoadFactor) {
    this.gasLoadFactor = gasLoadFactor;
  }

  public double getVolumeSafetyFactor() {
    return volumeSafetyFactor;
  }

  public void setVolumeSafetyFactor(double volumeSafetyFactor) {
    this.volumeSafetyFactor = volumeSafetyFactor;
  }

  public double getLiquidLevelFraction() {
    return liquidLevelFraction;
  }

  public void setLiquidLevelFraction(double liquidLevelFraction) {
    this.liquidLevelFraction = liquidLevelFraction;
  }

  public double getRetentionTime() {
    return retentionTime;
  }

  public void setRetentionTime(double retentionTime) {
    this.retentionTime = retentionTime;
  }

  public String getDemisterType() {
    return demisterType;
  }

  public void setDemisterType(String demisterType) {
    this.demisterType = demisterType;
  }

  public double getDemisterEfficiency() {
    return demisterEfficiency;
  }

  public void setDemisterEfficiency(double demisterEfficiency) {
    this.demisterEfficiency = demisterEfficiency;
  }

  public int getNumberOfInletNozzles() {
    return numberOfInletNozzles;
  }

  public void setNumberOfInletNozzles(int numberOfInletNozzles) {
    this.numberOfInletNozzles = numberOfInletNozzles;
  }

  public double getInletNozzleDiameter() {
    return inletNozzleDiameter;
  }

  public void setInletNozzleDiameter(double inletNozzleDiameter) {
    this.inletNozzleDiameter = inletNozzleDiameter;
  }

  public double getGasOutletNozzleDiameter() {
    return gasOutletNozzleDiameter;
  }

  public void setGasOutletNozzleDiameter(double gasOutletNozzleDiameter) {
    this.gasOutletNozzleDiameter = gasOutletNozzleDiameter;
  }

  public double getLiquidOutletNozzleDiameter() {
    return liquidOutletNozzleDiameter;
  }

  public void setLiquidOutletNozzleDiameter(double liquidOutletNozzleDiameter) {
    this.liquidOutletNozzleDiameter = liquidOutletNozzleDiameter;
  }

  public double getWaterOutletNozzleDiameter() {
    return waterOutletNozzleDiameter;
  }

  public void setWaterOutletNozzleDiameter(double waterOutletNozzleDiameter) {
    this.waterOutletNozzleDiameter = waterOutletNozzleDiameter;
  }

  public String getHeadType() {
    return headType;
  }

  public void setHeadType(String headType) {
    this.headType = headType;
  }

  public double getHeadThickness() {
    return headThickness;
  }

  public void setHeadThickness(double headThickness) {
    this.headThickness = headThickness;
  }

  public double getShellThickness() {
    return shellThickness;
  }

  public void setShellThickness(double shellThickness) {
    this.shellThickness = shellThickness;
  }

  public String getDesignCode() {
    return designCode;
  }

  public void setDesignCode(String designCode) {
    this.designCode = designCode;
  }

  public double getGasDesignVelocity() {
    return gasDesignVelocity;
  }

  public void setGasDesignVelocity(double gasDesignVelocity) {
    this.gasDesignVelocity = gasDesignVelocity;
  }

  public double getActualGasVelocity() {
    return actualGasVelocity;
  }

  public void setActualGasVelocity(double actualGasVelocity) {
    this.actualGasVelocity = actualGasVelocity;
  }

  public double getAllowableGasVelocity() {
    return allowableGasVelocity;
  }

  public void setAllowableGasVelocity(double allowableGasVelocity) {
    this.allowableGasVelocity = allowableGasVelocity;
  }

  public double getDesignGasFlow() {
    return designGasFlow;
  }

  public void setDesignGasFlow(double designGasFlow) {
    this.designGasFlow = designGasFlow;
  }

  public double getDesignLiquidFlow() {
    return designLiquidFlow;
  }

  public void setDesignLiquidFlow(double designLiquidFlow) {
    this.designLiquidFlow = designLiquidFlow;
  }

  public double getDesignWaterFlow() {
    return designWaterFlow;
  }

  public void setDesignWaterFlow(double designWaterFlow) {
    this.designWaterFlow = designWaterFlow;
  }

  public double getNormalLiquidLevel() {
    return normalLiquidLevel;
  }

  public void setNormalLiquidLevel(double normalLiquidLevel) {
    this.normalLiquidLevel = normalLiquidLevel;
  }

  public double getHighLiquidLevel() {
    return highLiquidLevel;
  }

  public void setHighLiquidLevel(double highLiquidLevel) {
    this.highLiquidLevel = highLiquidLevel;
  }

  public double getLowLiquidLevel() {
    return lowLiquidLevel;
  }

  public void setLowLiquidLevel(double lowLiquidLevel) {
    this.lowLiquidLevel = lowLiquidLevel;
  }

  public double getInterfaceLevel() {
    return interfaceLevel;
  }

  public void setInterfaceLevel(double interfaceLevel) {
    this.interfaceLevel = interfaceLevel;
  }

  public double getSurgeVolume() {
    return surgeVolume;
  }

  public void setSurgeVolume(double surgeVolume) {
    this.surgeVolume = surgeVolume;
  }

  public double getHoldupVolume() {
    return holdupVolume;
  }

  public void setHoldupVolume(double holdupVolume) {
    this.holdupVolume = holdupVolume;
  }

  public double getEmptyVesselWeight() {
    return emptyVesselWeight;
  }

  public void setEmptyVesselWeight(double emptyVesselWeight) {
    this.emptyVesselWeight = emptyVesselWeight;
  }

  public double getOperatingLiquidVolume() {
    return operatingLiquidVolume;
  }

  public void setOperatingLiquidVolume(double operatingLiquidVolume) {
    this.operatingLiquidVolume = operatingLiquidVolume;
  }

  public double getLiquidDensity() {
    return liquidDensity;
  }

  public void setLiquidDensity(double liquidDensity) {
    this.liquidDensity = liquidDensity;
  }

  public double getGasDensity() {
    return gasDensity;
  }

  public void setGasDensity(double gasDensity) {
    this.gasDensity = gasDensity;
  }

  // ============================================================================
  // Getters and Setters for Process Design Parameters
  // ============================================================================

  public double getFoamAllowanceFactor() {
    return foamAllowanceFactor;
  }

  public void setFoamAllowanceFactor(double foamAllowanceFactor) {
    this.foamAllowanceFactor = foamAllowanceFactor;
  }

  public double getDropletDiameterGasLiquid() {
    return dropletDiameterGasLiquid;
  }

  public void setDropletDiameterGasLiquid(double dropletDiameterGasLiquid) {
    this.dropletDiameterGasLiquid = dropletDiameterGasLiquid;
  }

  public double getDropletDiameterLiquidLiquid() {
    return dropletDiameterLiquidLiquid;
  }

  public void setDropletDiameterLiquidLiquid(double dropletDiameterLiquidLiquid) {
    this.dropletDiameterLiquidLiquid = dropletDiameterLiquidLiquid;
  }

  public double getDesignPressureMarginFactor() {
    return designPressureMarginFactor;
  }

  public void setDesignPressureMarginFactor(double designPressureMarginFactor) {
    this.designPressureMarginFactor = designPressureMarginFactor;
  }

  public double getDesignTemperatureMarginC() {
    return designTemperatureMarginC;
  }

  public void setDesignTemperatureMarginC(double designTemperatureMarginC) {
    this.designTemperatureMarginC = designTemperatureMarginC;
  }

  public double getMaxGasVelocity() {
    return maxGasVelocity;
  }

  public void setMaxGasVelocity(double maxGasVelocity) {
    this.maxGasVelocity = maxGasVelocity;
  }

  public double getMaxLiquidVelocity() {
    return maxLiquidVelocity;
  }

  public void setMaxLiquidVelocity(double maxLiquidVelocity) {
    this.maxLiquidVelocity = maxLiquidVelocity;
  }

  public double getDemisterPressureDrop() {
    return demisterPressureDrop;
  }

  public void setDemisterPressureDrop(double demisterPressureDrop) {
    this.demisterPressureDrop = demisterPressureDrop;
  }

  public double getDemisterVoidFraction() {
    return demisterVoidFraction;
  }

  public void setDemisterVoidFraction(double demisterVoidFraction) {
    this.demisterVoidFraction = demisterVoidFraction;
  }

  public double getMinOilRetentionTime() {
    return minOilRetentionTime;
  }

  public void setMinOilRetentionTime(double minOilRetentionTime) {
    this.minOilRetentionTime = minOilRetentionTime;
  }

  public double getMinWaterRetentionTime() {
    return minWaterRetentionTime;
  }

  public void setMinWaterRetentionTime(double minWaterRetentionTime) {
    this.minWaterRetentionTime = minWaterRetentionTime;
  }
}
