package neqsim.process.mechanicaldesign.compressor;

import java.util.List;
import java.util.Map;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.mechanicaldesign.MechanicalDesignResponse;

/**
 * Response class for compressor mechanical design JSON export.
 *
 * <p>
 * Extends {@link MechanicalDesignResponse} with compressor-specific parameters including staging, driver sizing, and
 * rotordynamic data per API 617.
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
  private double headPerStage = Double.NaN;

  /** Total polytropic head [kJ/kg]. */
  private double totalHead = Double.NaN;

  /** Impeller diameter [mm]. */
  private double impellerDiameter = Double.NaN;

  /** Shaft diameter [mm]. */
  private double shaftDiameter = Double.NaN;

  /** Impeller tip speed [m/s]. */
  private double tipSpeed = Double.NaN;

  /** Inlet flow coefficient Q / (D squared * U). */
  private double flowCoefficient = Double.NaN;

  /** Shaft speed used for impeller sizing [rpm]. */
  private double impellerSizingSpeedRPM = Double.NaN;

  /** Whether the preliminary coupled impeller sizing checks pass. */
  private boolean impellerSizingFeasible;

  /** Impeller sizing limit violations and stale-input diagnostics. */
  private List<String> impellerSizingIssues;

  /** Maximum continuous speed [rpm]. */
  private double maxContinuousSpeed = Double.NaN;

  /** Trip speed [rpm]. */
  private double tripSpeed = Double.NaN;

  /** First lateral critical speed [rpm]. */
  private double firstCriticalSpeed = Double.NaN;

  /** Required driver power [kW]. */
  private double driverPower = Double.NaN;

  /** Driver power margin factor. */
  private double driverMargin = Double.NaN;

  /** Casing weight [kg]. */
  private double casingWeight = Double.NaN;

  /** Rotor weight [kg]. */
  private double rotorWeight = Double.NaN;

  /** Bundle weight [kg]. */
  private double bundleWeight = Double.NaN;

  /** Bearing span [mm]. */
  private double bearingSpan = Double.NaN;

  /** Inlet pressure [bara]. */
  private double inletPressure = Double.NaN;

  /** Outlet pressure [bara]. */
  private double outletPressure = Double.NaN;

  /** Pressure ratio. */
  private double pressureRatio = Double.NaN;

  /** Polytropic efficiency. */
  private double polytropicEfficiency = Double.NaN;

  /** Isentropic efficiency. */
  private double isentropicEfficiency = Double.NaN;

  // ============================================================================
  // Process Design Parameters (added for TR3500 compliance)
  // ============================================================================

  /** Surge margin percentage. */
  private double surgeMarginPercent = Double.NaN;

  /** Stonewall margin percentage. */
  private double stonewallMarginPercent = Double.NaN;

  /** Minimum turndown percentage. */
  private double minTurndownPercent = Double.NaN;

  /** Target polytropic efficiency. */
  private double targetPolytropicEfficiency = Double.NaN;

  /** Seal type (dry gas, oil film, labyrinth). */
  private String sealType;

  /** Bearing type (tilting pad, plain, magnetic). */
  private String bearingType;

  /** NACE compliance required flag. */
  private boolean naceCompliance;

  /** Maximum discharge temperature [°C]. */
  private double maxDischargeTemperature = Double.NaN;

  /** Maximum pressure ratio per stage. */
  private double maxPressureRatioPerStage = Double.NaN;

  /** Maximum unfiltered vibration [mm/s]. */
  private double maxVibrationUnfiltered = Double.NaN;

  // ============================================================================
  // Casing Design Parameters (API 617 / ASME VIII)
  // ============================================================================

  /** Casing design results from CompressorCasingDesignCalculator. */
  private Map<String, Object> casingDesign;

  // ============================================================================
  // Constructors
  // ============================================================================

  /**
   * Default constructor.
   */
  public CompressorMechanicalDesignResponse() {
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
    this.flowCoefficient = mecDesign.getFlowCoefficient();
    this.impellerSizingSpeedRPM = mecDesign.getImpellerSizingSpeedRPM();
    this.impellerSizingIssues = mecDesign.getImpellerSizingIssues();
    this.impellerSizingFeasible = impellerSizingIssues.isEmpty();
    this.maxContinuousSpeed = mecDesign.getMaxContinuousSpeed();
    this.tripSpeed = mecDesign.getTripSpeed();
    this.firstCriticalSpeed = mecDesign.getFirstCriticalSpeed();
    this.driverPower = mecDesign.getDriverPower();
    this.driverMargin = mecDesign.getDriverMargin();
    this.casingWeight = mecDesign.getCasingWeight();
    this.rotorWeight = mecDesign.getRotorWeight();
    this.bundleWeight = mecDesign.getBundleWeight();
    this.bearingSpan = mecDesign.getBearingSpan();
    this.inletPressure = Double.NaN;
    this.outletPressure = Double.NaN;
    this.pressureRatio = Double.NaN;
    if (mecDesign.getProcessEquipment() instanceof Compressor) {
      Compressor compressor = (Compressor) mecDesign.getProcessEquipment();
      if (compressor.getInletStream() != null && compressor.getOutletStream() != null) {
        this.inletPressure = compressor.getInletStream().getPressure("bara");
        this.outletPressure = compressor.getOutletStream().getPressure("bara");
      }
      this.polytropicEfficiency = compressor.getPolytropicEfficiency();
      this.isentropicEfficiency = compressor.getIsentropicEfficiency();
      this.totalHead = compressor.getPolytropicFluidHead();
      setPower(compressor.getPower("kW"));
    }
    setMaxDesignPressure(mecDesign.getDesignPressure() > 0.0 ? mecDesign.getDesignPressure() : Double.NaN);
    setMaxDesignTemperature(mecDesign.getDesignPressure() > 0.0 ? mecDesign.getDesignTemperature() : Double.NaN);

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

    // Populate casing design calculation results
    CompressorCasingDesignCalculator casingCalc = mecDesign.getCasingDesignCalculator();
    this.casingDesign = casingCalc == null ? null : casingCalc.toMap();
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
