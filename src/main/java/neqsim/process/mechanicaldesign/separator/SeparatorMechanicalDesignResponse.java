package neqsim.process.mechanicaldesign.separator;

import neqsim.process.mechanicaldesign.MechanicalDesignResponse;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;

/**
 * Response class for separator mechanical design JSON export.
 *
 * <p>
 * Extends {@link MechanicalDesignResponse} with separator-specific parameters including vessel sizing, internals, and
 * process design data per API 12J and ASME VIII.
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
  private double gasLoadFactor = Double.NaN;

  /** Volumetric design safety factor. */
  private double volumeSafetyFactor = Double.NaN;

  /** Liquid level fraction (Fg). */
  private double liquidLevelFraction = Double.NaN;

  /** Liquid retention time [s]. */
  private double retentionTime = Double.NaN;

  /** Demister type (wire mesh, vane, cyclone). */
  private String demisterType;

  /** Demister efficiency. */
  private double demisterEfficiency = Double.NaN;

  /** Number of inlet nozzles. */
  private int numberOfInletNozzles;

  /** Inlet nozzle diameter [mm]. */
  private double inletNozzleDiameter = Double.NaN;

  /** Gas outlet nozzle diameter [mm]. */
  private double gasOutletNozzleDiameter = Double.NaN;

  /** Liquid outlet nozzle diameter [mm]. */
  private double liquidOutletNozzleDiameter = Double.NaN;

  /** Water outlet nozzle diameter (for 3-phase) [mm]. */
  private double waterOutletNozzleDiameter = Double.NaN;

  /** Head type (hemispherical, 2:1 ellipsoidal, torispherical). */
  private String headType;

  /** Head thickness [mm]. */
  private double headThickness = Double.NaN;

  /** Shell course thickness [mm]. */
  private double shellThickness = Double.NaN;

  /** Design code (ASME VIII Div 1, Div 2). */
  private String designCode;

  /** Gas design velocity [m/s]. */
  private double gasDesignVelocity = Double.NaN;

  /** Actual gas velocity [m/s]. */
  private double actualGasVelocity = Double.NaN;

  /** Allowable gas velocity [m/s]. */
  private double allowableGasVelocity = Double.NaN;

  /** Design gas flow [Am³/h]. */
  private double designGasFlow = Double.NaN;

  /** Design liquid flow [m³/h]. */
  private double designLiquidFlow = Double.NaN;

  /** Design water flow (for 3-phase) [m³/h]. */
  private double designWaterFlow = Double.NaN;

  /** Normal liquid level [m]. */
  private double normalLiquidLevel = Double.NaN;

  /** High liquid level [m]. */
  private double highLiquidLevel = Double.NaN;

  /** Low liquid level [m]. */
  private double lowLiquidLevel = Double.NaN;

  /** Oil-water interface level (for 3-phase) [m]. */
  private double interfaceLevel = Double.NaN;

  /** Liquid surge volume [m³]. */
  private double surgeVolume = Double.NaN;

  /** Liquid holdup volume [m³]. */
  private double holdupVolume = Double.NaN;

  /** Empty vessel weight [kg]. */
  private double emptyVesselWeight = Double.NaN;

  /** Operating liquid volume [m³]. */
  private double operatingLiquidVolume = Double.NaN;

  /** Liquid density [kg/m³]. */
  private double liquidDensity = Double.NaN;

  /** Gas density [kg/m³]. */
  private double gasDensity = Double.NaN;

  // ============================================================================
  // Liquid Level Design Parameters (added January 2026)
  // ============================================================================

  /** Effective length for liquid separation [m]. */
  private double effectiveLengthLiquid = Double.NaN;

  /** Effective length for gas separation [m]. */
  private double effectiveLengthGas = Double.NaN;

  // ============================================================================
  // Process Design Parameters (added for TR3500 compliance)
  // ============================================================================

  /** Foam allowance factor. */
  private double foamAllowanceFactor = Double.NaN;

  /** Design droplet diameter for gas-liquid separation [um]. */
  private double dropletDiameterGasLiquid = Double.NaN;

  /** Design droplet diameter for liquid-liquid separation [um]. */
  private double dropletDiameterLiquidLiquid = Double.NaN;

  /** Design pressure margin factor. */
  private double designPressureMarginFactor = Double.NaN;

  /** Design temperature margin [C]. */
  private double designTemperatureMarginC = Double.NaN;

  /** Maximum gas velocity [m/s]. */
  private double maxGasVelocity = Double.NaN;

  /** Maximum liquid velocity [m/s]. */
  private double maxLiquidVelocity = Double.NaN;

  /** Demister pressure drop [mbar]. */
  private double demisterPressureDrop = Double.NaN;

  /** Demister void fraction. */
  private double demisterVoidFraction = Double.NaN;

  /** Minimum oil retention time [min]. */
  private double minOilRetentionTime = Double.NaN;

  /** Minimum water retention time [min]. */
  private double minWaterRetentionTime = Double.NaN;

  // ============================================================================
  // Liquid Level Design Parameters (added January 2026)
  // ============================================================================

  /** High-High Liquid Level fraction of ID. */
  private double hhllFraction = Double.NaN;

  /** High Liquid Level fraction of ID. */
  private double hllFraction = Double.NaN;

  /** Normal Liquid Level fraction of ID. */
  private double nllFraction = Double.NaN;

  /** Low Liquid Level fraction of ID. */
  private double lllFraction = Double.NaN;

  /** Weir height fraction of ID. */
  private double weirFraction = Double.NaN;

  /** High Interface Level fraction of ID. */
  private double hilFraction = Double.NaN;

  /** Normal Interface Level fraction of ID. */
  private double nilFraction = Double.NaN;

  /** Low Interface Level fraction of ID. */
  private double lilFraction = Double.NaN;

  // ============================================================================
  // Entrainment Performance Results
  // ============================================================================

  /** Whether detailed entrainment calculation was used. */
  private boolean detailedEntrainmentUsed;
  /** Oil-in-gas entrainment fraction [0-1]. */
  private double oilInGasFraction = Double.NaN;
  /** Water-in-gas entrainment fraction [0-1]. */
  private double waterInGasFraction = Double.NaN;
  /** Gas-in-oil carry-under fraction [0-1]. */
  private double gasInOilFraction = Double.NaN;
  /** Gas-in-water carry-under fraction [0-1]. */
  private double gasInWaterFraction = Double.NaN;
  /** Oil-in-water entrainment fraction [0-1]. */
  private double oilInWaterFraction = Double.NaN;
  /** Water-in-oil entrainment fraction [0-1]. */
  private double waterInOilFraction = Double.NaN;
  /** Overall gas-liquid separation efficiency [0-1]. */
  private double overallGasLiquidEfficiency = Double.NaN;
  /** Mist eliminator efficiency [0-1]. */
  private double mistEliminatorEfficiency = Double.NaN;
  /** K-factor utilization (actual/design) [0-1]. */
  private double kFactorUtilization = Double.NaN;
  /** Whether the mist eliminator is flooded. */
  private boolean mistEliminatorFlooded;
  /** Liquid-in-gas calibration factor. */
  private double liquidInGasCalibrationFactor = 1.0;
  /** Gas carry-under calibration factor. */
  private double gasCarryUnderCalibrationFactor = 1.0;
  /** Liquid-liquid calibration factor. */
  private double liquidLiquidCalibrationFactor = 1.0;
  /** Detailed entrainment JSON (transient, not serialized in response). */
  private transient String entrainmentDetailJson;

  /** High-High Liquid Level [m]. */
  private double hhll = Double.NaN;

  /** High Liquid Level [m] - duplicate for explicit naming. */
  private double hll = Double.NaN;

  /** Normal Liquid Level [m] - duplicate for explicit naming. */
  private double nll = Double.NaN;

  /** Low Liquid Level [m] - duplicate for explicit naming. */
  private double lll = Double.NaN;

  /** Weir height [m]. */
  private double weirHeight = Double.NaN;

  /** High Interface Level [m]. */
  private double hil = Double.NaN;

  /** Normal Interface Level [m]. */
  private double nil = Double.NaN;

  /** Low Interface Level [m]. */
  private double lil = Double.NaN;

  // ============================================================================
  // Constructors
  // ============================================================================

  /**
   * Default constructor.
   */
  public SeparatorMechanicalDesignResponse() {
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

    if (mecDesign.getProcessEquipment() instanceof Separator) {
      Separator separator = (Separator) mecDesign.getProcessEquipment();
      this.orientation = separator.getOrientation();
      this.separatorType = separator instanceof ThreePhaseSeparator ? "three-phase" : "two-phase";
    }
    this.shellThickness = mecDesign.getWallThickness() > 0.0 ? mecDesign.getWallThickness() * 1000.0 : Double.NaN;
    this.demisterType = mecDesign.getDemisterType();
    // Head geometry has no authoritative owner in this model and remains unavailable.
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

    // Populate entrainment performance results
    this.detailedEntrainmentUsed = mecDesign.isDetailedEntrainmentUsed();
    this.oilInGasFraction = mecDesign.getOilInGasFraction();
    this.waterInGasFraction = mecDesign.getWaterInGasFraction();
    this.gasInOilFraction = mecDesign.getGasInOilFraction();
    this.gasInWaterFraction = mecDesign.getGasInWaterFraction();
    this.oilInWaterFraction = mecDesign.getOilInWaterFraction();
    this.waterInOilFraction = mecDesign.getWaterInOilFraction();
    this.overallGasLiquidEfficiency = mecDesign.getOverallGasLiquidEfficiency();
    this.mistEliminatorEfficiency = mecDesign.getMistEliminatorEfficiency();
    this.kFactorUtilization = mecDesign.getKFactorUtilization();
    this.mistEliminatorFlooded = mecDesign.isMistEliminatorFlooded();
    this.liquidInGasCalibrationFactor = mecDesign.getLiquidInGasCalibrationFactor();
    this.gasCarryUnderCalibrationFactor = mecDesign.getGasCarryUnderCalibrationFactor();
    this.liquidLiquidCalibrationFactor = mecDesign.getLiquidLiquidCalibrationFactor();
    this.entrainmentDetailJson = mecDesign.getEntrainmentDetailJson();
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

  // ============================================================================
  // Getters for Entrainment Performance Results
  // ============================================================================

  public boolean isDetailedEntrainmentUsed() {
    return detailedEntrainmentUsed;
  }

  public double getOilInGasFraction() {
    return oilInGasFraction;
  }

  public double getWaterInGasFraction() {
    return waterInGasFraction;
  }

  public double getGasInOilFraction() {
    return gasInOilFraction;
  }

  public double getGasInWaterFraction() {
    return gasInWaterFraction;
  }

  public double getOilInWaterFraction() {
    return oilInWaterFraction;
  }

  public double getWaterInOilFraction() {
    return waterInOilFraction;
  }

  public double getOverallGasLiquidEfficiency() {
    return overallGasLiquidEfficiency;
  }

  public double getMistEliminatorEfficiency() {
    return mistEliminatorEfficiency;
  }

  public double getKFactorUtilization() {
    return kFactorUtilization;
  }

  public boolean isMistEliminatorFlooded() {
    return mistEliminatorFlooded;
  }

  public double getLiquidInGasCalibrationFactor() {
    return liquidInGasCalibrationFactor;
  }

  public double getGasCarryUnderCalibrationFactor() {
    return gasCarryUnderCalibrationFactor;
  }

  public double getLiquidLiquidCalibrationFactor() {
    return liquidLiquidCalibrationFactor;
  }

  public String getEntrainmentDetailJson() {
    return entrainmentDetailJson;
  }
}
