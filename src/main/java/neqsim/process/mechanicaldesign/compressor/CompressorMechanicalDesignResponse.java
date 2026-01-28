package neqsim.process.mechanicaldesign.compressor;

import neqsim.process.mechanicaldesign.MechanicalDesignResponse;

/**
 * Response class for compressor mechanical design JSON export.
 *
 * <p>
 * Extends {@link MechanicalDesignResponse} with compressor-specific parameters including staging,
 * driver sizing, and rotordynamic data per API 617.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class CompressorMechanicalDesignResponse extends MechanicalDesignResponse {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  // ============================================================================
  // Compressor-Specific Parameters
  // ============================================================================

  /** Compressor type (centrifugal, reciprocating, screw, axial). */
  private String compressorType;

  /** Casing type (horizontal split, barrel, vertical split). */
  private String casingType;

  /** Number of compression stages. */
  private int numberOfStages;

  /** Polytropic head per stage [kJ/kg]. */
  private double headPerStage;

  /** Total polytropic head [kJ/kg]. */
  private double totalHead;

  /** Impeller diameter [mm]. */
  private double impellerDiameter;

  /** Shaft diameter [mm]. */
  private double shaftDiameter;

  /** Impeller tip speed [m/s]. */
  private double tipSpeed;

  /** Maximum continuous speed [rpm]. */
  private double maxContinuousSpeed;

  /** Trip speed [rpm]. */
  private double tripSpeed;

  /** First lateral critical speed [rpm]. */
  private double firstCriticalSpeed;

  /** Required driver power [kW]. */
  private double driverPower;

  /** Driver power margin factor. */
  private double driverMargin;

  /** Casing weight [kg]. */
  private double casingWeight;

  /** Rotor weight [kg]. */
  private double rotorWeight;

  /** Bundle weight [kg]. */
  private double bundleWeight;

  /** Bearing span [mm]. */
  private double bearingSpan;

  /** Inlet pressure [bara]. */
  private double inletPressure;

  /** Outlet pressure [bara]. */
  private double outletPressure;

  /** Pressure ratio. */
  private double pressureRatio;

  /** Polytropic efficiency. */
  private double polytropicEfficiency;

  /** Isentropic efficiency. */
  private double isentropicEfficiency;

  // ============================================================================
  // Process Design Parameters (added for TR3500 compliance)
  // ============================================================================

  /** Surge margin percentage. */
  private double surgeMarginPercent;

  /** Stonewall margin percentage. */
  private double stonewallMarginPercent;

  /** Minimum turndown percentage. */
  private double minTurndownPercent;

  /** Target polytropic efficiency. */
  private double targetPolytropicEfficiency;

  /** Seal type (dry gas, oil film, labyrinth). */
  private String sealType;

  /** Bearing type (tilting pad, plain, magnetic). */
  private String bearingType;

  /** NACE compliance required flag. */
  private boolean naceCompliance;

  /** Maximum discharge temperature [°C]. */
  private double maxDischargeTemperature;

  /** Maximum pressure ratio per stage. */
  private double maxPressureRatioPerStage;

  /** Maximum unfiltered vibration [mm/s]. */
  private double maxVibrationUnfiltered;

  // ============================================================================
  // Constructors
  // ============================================================================

  /**
   * Default constructor.
   */
  public CompressorMechanicalDesignResponse() {
    super();
    setEquipmentType("Compressor");
    setDesignStandard("API 617");
  }

  /**
   * Constructor from CompressorMechanicalDesign.
   *
   * @param mecDesign the compressor mechanical design
   */
  public CompressorMechanicalDesignResponse(CompressorMechanicalDesign mecDesign) {
    super(mecDesign);
    setEquipmentType("Compressor");
    setDesignStandard("API 617");
    populateFromCompressorDesign(mecDesign);
  }

  /**
   * Populate compressor-specific fields from CompressorMechanicalDesign.
   *
   * @param mecDesign the compressor mechanical design
   */
  public void populateFromCompressorDesign(CompressorMechanicalDesign mecDesign) {
    if (mecDesign == null) {
      return;
    }

    this.numberOfStages = mecDesign.getNumberOfStages();
    this.headPerStage = mecDesign.getHeadPerStage();
    this.impellerDiameter = mecDesign.getImpellerDiameter();
    this.shaftDiameter = mecDesign.getShaftDiameter();
    this.tipSpeed = mecDesign.getTipSpeed();
    this.maxContinuousSpeed = mecDesign.getMaxContinuousSpeed();
    this.tripSpeed = mecDesign.getTripSpeed();
    this.firstCriticalSpeed = mecDesign.getFirstCriticalSpeed();
    this.driverPower = mecDesign.getDriverPower();
    this.driverMargin = mecDesign.getDriverMargin();
    this.casingWeight = mecDesign.getCasingWeight();
    this.rotorWeight = mecDesign.getRotorWeight();
    this.bundleWeight = mecDesign.getBundleWeight();
    this.bearingSpan = mecDesign.getBearingSpan();
    this.inletPressure = mecDesign.getMinOperationPressure();
    this.outletPressure = mecDesign.getMaxOperationPressure();

    if (mecDesign.getCasingType() != null) {
      this.casingType = mecDesign.getCasingType().name();
    }

    // Calculate pressure ratio
    if (this.inletPressure > 0) {
      this.pressureRatio = this.outletPressure / this.inletPressure;
    }

    // Populate process design parameters
    this.surgeMarginPercent = mecDesign.getSurgeMarginPercent();
    this.stonewallMarginPercent = mecDesign.getStonewallMarginPercent();
    this.minTurndownPercent = mecDesign.getTurndownPercent();
    this.targetPolytropicEfficiency = mecDesign.getTargetPolytropicEfficiency();
    this.sealType = mecDesign.getSealType();
    this.bearingType = mecDesign.getBearingType();
    this.naceCompliance = mecDesign.isNaceCompliance();
    this.maxDischargeTemperature = mecDesign.getMaxDischargeTemperatureC();
    this.maxPressureRatioPerStage = mecDesign.getMaxPressureRatioPerStage();
    this.maxVibrationUnfiltered = mecDesign.getMaxVibrationMmPerSec();
  }

  // ============================================================================
  // Getters and Setters
  // ============================================================================

  public String getCompressorType() {
    return compressorType;
  }

  public void setCompressorType(String compressorType) {
    this.compressorType = compressorType;
  }

  public String getCasingType() {
    return casingType;
  }

  public void setCasingType(String casingType) {
    this.casingType = casingType;
  }

  public int getNumberOfStages() {
    return numberOfStages;
  }

  public void setNumberOfStages(int numberOfStages) {
    this.numberOfStages = numberOfStages;
  }

  public double getHeadPerStage() {
    return headPerStage;
  }

  public void setHeadPerStage(double headPerStage) {
    this.headPerStage = headPerStage;
  }

  public double getTotalHead() {
    return totalHead;
  }

  public void setTotalHead(double totalHead) {
    this.totalHead = totalHead;
  }

  public double getImpellerDiameter() {
    return impellerDiameter;
  }

  public void setImpellerDiameter(double impellerDiameter) {
    this.impellerDiameter = impellerDiameter;
  }

  public double getShaftDiameter() {
    return shaftDiameter;
  }

  public void setShaftDiameter(double shaftDiameter) {
    this.shaftDiameter = shaftDiameter;
  }

  public double getTipSpeed() {
    return tipSpeed;
  }

  public void setTipSpeed(double tipSpeed) {
    this.tipSpeed = tipSpeed;
  }

  public double getMaxContinuousSpeed() {
    return maxContinuousSpeed;
  }

  public void setMaxContinuousSpeed(double maxContinuousSpeed) {
    this.maxContinuousSpeed = maxContinuousSpeed;
  }

  public double getTripSpeed() {
    return tripSpeed;
  }

  public void setTripSpeed(double tripSpeed) {
    this.tripSpeed = tripSpeed;
  }

  public double getFirstCriticalSpeed() {
    return firstCriticalSpeed;
  }

  public void setFirstCriticalSpeed(double firstCriticalSpeed) {
    this.firstCriticalSpeed = firstCriticalSpeed;
  }

  public double getDriverPower() {
    return driverPower;
  }

  public void setDriverPower(double driverPower) {
    this.driverPower = driverPower;
  }

  public double getDriverMargin() {
    return driverMargin;
  }

  public void setDriverMargin(double driverMargin) {
    this.driverMargin = driverMargin;
  }

  public double getCasingWeight() {
    return casingWeight;
  }

  public void setCasingWeight(double casingWeight) {
    this.casingWeight = casingWeight;
  }

  public double getRotorWeight() {
    return rotorWeight;
  }

  public void setRotorWeight(double rotorWeight) {
    this.rotorWeight = rotorWeight;
  }

  public double getBundleWeight() {
    return bundleWeight;
  }

  public void setBundleWeight(double bundleWeight) {
    this.bundleWeight = bundleWeight;
  }

  public double getBearingSpan() {
    return bearingSpan;
  }

  public void setBearingSpan(double bearingSpan) {
    this.bearingSpan = bearingSpan;
  }

  public double getInletPressure() {
    return inletPressure;
  }

  public void setInletPressure(double inletPressure) {
    this.inletPressure = inletPressure;
  }

  public double getOutletPressure() {
    return outletPressure;
  }

  public void setOutletPressure(double outletPressure) {
    this.outletPressure = outletPressure;
  }

  public double getPressureRatio() {
    return pressureRatio;
  }

  public void setPressureRatio(double pressureRatio) {
    this.pressureRatio = pressureRatio;
  }

  public double getPolytropicEfficiency() {
    return polytropicEfficiency;
  }

  public void setPolytropicEfficiency(double polytropicEfficiency) {
    this.polytropicEfficiency = polytropicEfficiency;
  }

  public double getIsentropicEfficiency() {
    return isentropicEfficiency;
  }

  public void setIsentropicEfficiency(double isentropicEfficiency) {
    this.isentropicEfficiency = isentropicEfficiency;
  }

  // ============================================================================
  // Getters and Setters for Process Design Parameters
  // ============================================================================

  public double getSurgeMarginPercent() {
    return surgeMarginPercent;
  }

  public void setSurgeMarginPercent(double surgeMarginPercent) {
    this.surgeMarginPercent = surgeMarginPercent;
  }

  public double getStonewallMarginPercent() {
    return stonewallMarginPercent;
  }

  public void setStonewallMarginPercent(double stonewallMarginPercent) {
    this.stonewallMarginPercent = stonewallMarginPercent;
  }

  public double getMinTurndownPercent() {
    return minTurndownPercent;
  }

  public void setMinTurndownPercent(double minTurndownPercent) {
    this.minTurndownPercent = minTurndownPercent;
  }

  public double getTargetPolytropicEfficiency() {
    return targetPolytropicEfficiency;
  }

  public void setTargetPolytropicEfficiency(double targetPolytropicEfficiency) {
    this.targetPolytropicEfficiency = targetPolytropicEfficiency;
  }

  public String getSealType() {
    return sealType;
  }

  public void setSealType(String sealType) {
    this.sealType = sealType;
  }

  public String getBearingType() {
    return bearingType;
  }

  public void setBearingType(String bearingType) {
    this.bearingType = bearingType;
  }

  public boolean isNaceCompliance() {
    return naceCompliance;
  }

  public void setNaceCompliance(boolean naceCompliance) {
    this.naceCompliance = naceCompliance;
  }

  public double getMaxDischargeTemperature() {
    return maxDischargeTemperature;
  }

  public void setMaxDischargeTemperature(double maxDischargeTemperature) {
    this.maxDischargeTemperature = maxDischargeTemperature;
  }

  public double getMaxPressureRatioPerStage() {
    return maxPressureRatioPerStage;
  }

  public void setMaxPressureRatioPerStage(double maxPressureRatioPerStage) {
    this.maxPressureRatioPerStage = maxPressureRatioPerStage;
  }

  public double getMaxVibrationUnfiltered() {
    return maxVibrationUnfiltered;
  }

  public void setMaxVibrationUnfiltered(double maxVibrationUnfiltered) {
    this.maxVibrationUnfiltered = maxVibrationUnfiltered;
  }
}
