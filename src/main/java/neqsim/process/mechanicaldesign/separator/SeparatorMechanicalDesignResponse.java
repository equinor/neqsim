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
}
