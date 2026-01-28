package neqsim.process.mechanicaldesign.heatexchanger;

import neqsim.process.mechanicaldesign.MechanicalDesignResponse;

/**
 * Response class for heat exchanger mechanical design JSON export.
 *
 * <p>
 * Extends {@link MechanicalDesignResponse} with heat exchanger-specific parameters including TEMA
 * classification, thermal design data, and tube bundle specifications.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class HeatExchangerMechanicalDesignResponse extends MechanicalDesignResponse {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  // ============================================================================
  // Heat Exchanger-Specific Parameters
  // ============================================================================

  /** Heat exchanger type (shell-tube, plate, air-cooled, double-pipe). */
  private String heatExchangerType;

  /** TEMA type designation (e.g., AES, BEM, AKT). */
  private String temaType;

  /** Number of shells in series. */
  private int numberOfShells;

  /** Number of tube passes. */
  private int numberOfTubePasses;

  /** Number of shell passes. */
  private int numberOfShellPasses;

  /** Number of tubes per shell. */
  private int numberOfTubes;

  /** Tube outer diameter [mm]. */
  private double tubeOuterDiameter;

  /** Tube wall thickness [mm]. */
  private double tubeWallThickness;

  /** Tube length [m]. */
  private double tubeLength;

  /** Tube pitch [mm]. */
  private double tubePitch;

  /** Tube layout angle (30°, 45°, 60°, 90°). */
  private int tubeLayoutAngle;

  /** Tube material. */
  private String tubeMaterial;

  /** Shell inner diameter [mm]. */
  private double shellInnerDiameter;

  /** Shell wall thickness [mm]. */
  private double shellWallThickness;

  /** Shell material. */
  private String shellMaterial;

  /** Baffle type (single segmental, double segmental, no-tubes-in-window). */
  private String baffleType;

  /** Baffle cut [%]. */
  private double baffleCut;

  /** Baffle spacing [mm]. */
  private double baffleSpacing;

  /** Number of baffles. */
  private int numberOfBaffles;

  /** Baffle thickness [mm]. */
  private double baffleThickness;

  /** Heat transfer area [m²]. */
  private double heatTransferArea;

  /** Required heat transfer area [m²]. */
  private double requiredArea;

  /** Area margin [%]. */
  private double areaMargin;

  /** Overall heat transfer coefficient [W/m²K]. */
  private double overallHeatTransferCoeff;

  /** Heat duty [kW]. */
  private double heatDuty;

  /** Log mean temperature difference [K]. */
  private double lmtd;

  /** LMTD correction factor (F). */
  private double lmtdCorrectionFactor;

  /** Shell-side design pressure [bara]. */
  private double shellDesignPressure;

  /** Shell-side design temperature [°C]. */
  private double shellDesignTemperature;

  /** Tube-side design pressure [bara]. */
  private double tubeDesignPressure;

  /** Tube-side design temperature [°C]. */
  private double tubeDesignTemperature;

  /** Shell-side pressure drop [bar]. */
  private double shellPressureDrop;

  /** Tube-side pressure drop [bar]. */
  private double tubePressureDrop;

  /** Shell-side fouling resistance [m²K/W]. */
  private double shellFoulingResistance;

  /** Tube-side fouling resistance [m²K/W]. */
  private double tubeFoulingResistance;

  /** Bundle weight [kg]. */
  private double bundleWeight;

  /** Channel weight [kg]. */
  private double channelWeight;

  // ============================================================================
  // Process Design Parameters (added for TR3500 compliance)
  // ============================================================================

  /** Shell-side fouling resistance (design value) [m²K/W]. */
  private double designShellFoulingResistance;

  /** Tube-side fouling resistance (design value) [m²K/W]. */
  private double designTubeFoulingResistance;

  /** TEMA equipment class (R, C, or B). */
  private String temaClass;

  /** Maximum tube velocity [m/s]. */
  private double maxTubeVelocity;

  /** Maximum shell velocity [m/s]. */
  private double maxShellVelocity;

  /** Minimum approach temperature [°C]. */
  private double minApproachTemperature;

  /** Maximum tube length [m]. */
  private double maxTubeLength;

  /** Vibration analysis required flag. */
  private boolean vibrationAnalysisRequired;

  /** Clean overall heat transfer coefficient [W/m²K]. */
  private double cleanOverallHeatTransferCoeff;

  /** Fouled overall heat transfer coefficient [W/m²K]. */
  private double fouledOverallHeatTransferCoeff;

  // ============================================================================
  // Constructors
  // ============================================================================

  /**
   * Default constructor.
   */
  public HeatExchangerMechanicalDesignResponse() {
    super();
    setEquipmentType("HeatExchanger");
    setDesignStandard("TEMA / ASME VIII");
  }

  /**
   * Constructor from HeatExchangerMechanicalDesign.
   *
   * @param mecDesign the heat exchanger mechanical design
   */
  public HeatExchangerMechanicalDesignResponse(HeatExchangerMechanicalDesign mecDesign) {
    super(mecDesign);
    setEquipmentType("HeatExchanger");
    setDesignStandard("TEMA / ASME VIII");
    populateFromHeatExchangerDesign(mecDesign);
  }

  /**
   * Populate heat exchanger-specific fields from HeatExchangerMechanicalDesign.
   *
   * @param mecDesign the heat exchanger mechanical design
   */
  public void populateFromHeatExchangerDesign(HeatExchangerMechanicalDesign mecDesign) {
    if (mecDesign == null) {
      return;
    }

    // Populate process design parameters
    // Use shell-side HC fouling as default design value
    this.designShellFoulingResistance = mecDesign.getFoulingResistanceShellHC();
    // Use tube-side HC fouling as default design value
    this.designTubeFoulingResistance = mecDesign.getFoulingResistanceTubeHC();
    this.temaClass = mecDesign.getTemaClass();
    this.maxTubeVelocity = mecDesign.getMaxTubeVelocity();
    this.maxShellVelocity = mecDesign.getMaxShellVelocity();
    this.minApproachTemperature = mecDesign.getMinApproachTemperatureC();
    this.maxTubeLength = mecDesign.getMaxTubeLengthM();
    // Default to false - can be determined based on design conditions
    this.vibrationAnalysisRequired = false;
  }

  // ============================================================================
  // Getters and Setters
  // ============================================================================

  public String getHeatExchangerType() {
    return heatExchangerType;
  }

  public void setHeatExchangerType(String heatExchangerType) {
    this.heatExchangerType = heatExchangerType;
  }

  public String getTemaType() {
    return temaType;
  }

  public void setTemaType(String temaType) {
    this.temaType = temaType;
  }

  public int getNumberOfShells() {
    return numberOfShells;
  }

  public void setNumberOfShells(int numberOfShells) {
    this.numberOfShells = numberOfShells;
  }

  public int getNumberOfTubePasses() {
    return numberOfTubePasses;
  }

  public void setNumberOfTubePasses(int numberOfTubePasses) {
    this.numberOfTubePasses = numberOfTubePasses;
  }

  public int getNumberOfShellPasses() {
    return numberOfShellPasses;
  }

  public void setNumberOfShellPasses(int numberOfShellPasses) {
    this.numberOfShellPasses = numberOfShellPasses;
  }

  public int getNumberOfTubes() {
    return numberOfTubes;
  }

  public void setNumberOfTubes(int numberOfTubes) {
    this.numberOfTubes = numberOfTubes;
  }

  public double getTubeOuterDiameter() {
    return tubeOuterDiameter;
  }

  public void setTubeOuterDiameter(double tubeOuterDiameter) {
    this.tubeOuterDiameter = tubeOuterDiameter;
  }

  public double getTubeWallThickness() {
    return tubeWallThickness;
  }

  public void setTubeWallThickness(double tubeWallThickness) {
    this.tubeWallThickness = tubeWallThickness;
  }

  public double getTubeLength() {
    return tubeLength;
  }

  public void setTubeLength(double tubeLength) {
    this.tubeLength = tubeLength;
  }

  public double getTubePitch() {
    return tubePitch;
  }

  public void setTubePitch(double tubePitch) {
    this.tubePitch = tubePitch;
  }

  public int getTubeLayoutAngle() {
    return tubeLayoutAngle;
  }

  public void setTubeLayoutAngle(int tubeLayoutAngle) {
    this.tubeLayoutAngle = tubeLayoutAngle;
  }

  public String getTubeMaterial() {
    return tubeMaterial;
  }

  public void setTubeMaterial(String tubeMaterial) {
    this.tubeMaterial = tubeMaterial;
  }

  public double getShellInnerDiameter() {
    return shellInnerDiameter;
  }

  public void setShellInnerDiameter(double shellInnerDiameter) {
    this.shellInnerDiameter = shellInnerDiameter;
  }

  public double getShellWallThickness() {
    return shellWallThickness;
  }

  public void setShellWallThickness(double shellWallThickness) {
    this.shellWallThickness = shellWallThickness;
  }

  public String getShellMaterial() {
    return shellMaterial;
  }

  public void setShellMaterial(String shellMaterial) {
    this.shellMaterial = shellMaterial;
  }

  public String getBaffleType() {
    return baffleType;
  }

  public void setBaffleType(String baffleType) {
    this.baffleType = baffleType;
  }

  public double getBaffleCut() {
    return baffleCut;
  }

  public void setBaffleCut(double baffleCut) {
    this.baffleCut = baffleCut;
  }

  public double getBaffleSpacing() {
    return baffleSpacing;
  }

  public void setBaffleSpacing(double baffleSpacing) {
    this.baffleSpacing = baffleSpacing;
  }

  public int getNumberOfBaffles() {
    return numberOfBaffles;
  }

  public void setNumberOfBaffles(int numberOfBaffles) {
    this.numberOfBaffles = numberOfBaffles;
  }

  public double getBaffleThickness() {
    return baffleThickness;
  }

  public void setBaffleThickness(double baffleThickness) {
    this.baffleThickness = baffleThickness;
  }

  public double getHeatTransferArea() {
    return heatTransferArea;
  }

  public void setHeatTransferArea(double heatTransferArea) {
    this.heatTransferArea = heatTransferArea;
  }

  public double getRequiredArea() {
    return requiredArea;
  }

  public void setRequiredArea(double requiredArea) {
    this.requiredArea = requiredArea;
  }

  public double getAreaMargin() {
    return areaMargin;
  }

  public void setAreaMargin(double areaMargin) {
    this.areaMargin = areaMargin;
  }

  public double getOverallHeatTransferCoeff() {
    return overallHeatTransferCoeff;
  }

  public void setOverallHeatTransferCoeff(double overallHeatTransferCoeff) {
    this.overallHeatTransferCoeff = overallHeatTransferCoeff;
  }

  public double getHeatDuty() {
    return heatDuty;
  }

  public void setHeatDuty(double heatDuty) {
    this.heatDuty = heatDuty;
  }

  public double getLmtd() {
    return lmtd;
  }

  public void setLmtd(double lmtd) {
    this.lmtd = lmtd;
  }

  public double getLmtdCorrectionFactor() {
    return lmtdCorrectionFactor;
  }

  public void setLmtdCorrectionFactor(double lmtdCorrectionFactor) {
    this.lmtdCorrectionFactor = lmtdCorrectionFactor;
  }

  public double getShellDesignPressure() {
    return shellDesignPressure;
  }

  public void setShellDesignPressure(double shellDesignPressure) {
    this.shellDesignPressure = shellDesignPressure;
  }

  public double getShellDesignTemperature() {
    return shellDesignTemperature;
  }

  public void setShellDesignTemperature(double shellDesignTemperature) {
    this.shellDesignTemperature = shellDesignTemperature;
  }

  public double getTubeDesignPressure() {
    return tubeDesignPressure;
  }

  public void setTubeDesignPressure(double tubeDesignPressure) {
    this.tubeDesignPressure = tubeDesignPressure;
  }

  public double getTubeDesignTemperature() {
    return tubeDesignTemperature;
  }

  public void setTubeDesignTemperature(double tubeDesignTemperature) {
    this.tubeDesignTemperature = tubeDesignTemperature;
  }

  public double getShellPressureDrop() {
    return shellPressureDrop;
  }

  public void setShellPressureDrop(double shellPressureDrop) {
    this.shellPressureDrop = shellPressureDrop;
  }

  public double getTubePressureDrop() {
    return tubePressureDrop;
  }

  public void setTubePressureDrop(double tubePressureDrop) {
    this.tubePressureDrop = tubePressureDrop;
  }

  public double getShellFoulingResistance() {
    return shellFoulingResistance;
  }

  public void setShellFoulingResistance(double shellFoulingResistance) {
    this.shellFoulingResistance = shellFoulingResistance;
  }

  public double getTubeFoulingResistance() {
    return tubeFoulingResistance;
  }

  public void setTubeFoulingResistance(double tubeFoulingResistance) {
    this.tubeFoulingResistance = tubeFoulingResistance;
  }

  public double getBundleWeight() {
    return bundleWeight;
  }

  public void setBundleWeight(double bundleWeight) {
    this.bundleWeight = bundleWeight;
  }

  public double getChannelWeight() {
    return channelWeight;
  }

  public void setChannelWeight(double channelWeight) {
    this.channelWeight = channelWeight;
  }

  // ============================================================================
  // Getters and Setters for Process Design Parameters
  // ============================================================================

  public double getDesignShellFoulingResistance() {
    return designShellFoulingResistance;
  }

  public void setDesignShellFoulingResistance(double designShellFoulingResistance) {
    this.designShellFoulingResistance = designShellFoulingResistance;
  }

  public double getDesignTubeFoulingResistance() {
    return designTubeFoulingResistance;
  }

  public void setDesignTubeFoulingResistance(double designTubeFoulingResistance) {
    this.designTubeFoulingResistance = designTubeFoulingResistance;
  }

  public String getTemaClass() {
    return temaClass;
  }

  public void setTemaClass(String temaClass) {
    this.temaClass = temaClass;
  }

  public double getMaxTubeVelocity() {
    return maxTubeVelocity;
  }

  public void setMaxTubeVelocity(double maxTubeVelocity) {
    this.maxTubeVelocity = maxTubeVelocity;
  }

  public double getMaxShellVelocity() {
    return maxShellVelocity;
  }

  public void setMaxShellVelocity(double maxShellVelocity) {
    this.maxShellVelocity = maxShellVelocity;
  }

  public double getMinApproachTemperature() {
    return minApproachTemperature;
  }

  public void setMinApproachTemperature(double minApproachTemperature) {
    this.minApproachTemperature = minApproachTemperature;
  }

  public double getMaxTubeLength() {
    return maxTubeLength;
  }

  public void setMaxTubeLength(double maxTubeLength) {
    this.maxTubeLength = maxTubeLength;
  }

  public boolean isVibrationAnalysisRequired() {
    return vibrationAnalysisRequired;
  }

  public void setVibrationAnalysisRequired(boolean vibrationAnalysisRequired) {
    this.vibrationAnalysisRequired = vibrationAnalysisRequired;
  }

  public double getCleanOverallHeatTransferCoeff() {
    return cleanOverallHeatTransferCoeff;
  }

  public void setCleanOverallHeatTransferCoeff(double cleanOverallHeatTransferCoeff) {
    this.cleanOverallHeatTransferCoeff = cleanOverallHeatTransferCoeff;
  }

  public double getFouledOverallHeatTransferCoeff() {
    return fouledOverallHeatTransferCoeff;
  }

  public void setFouledOverallHeatTransferCoeff(double fouledOverallHeatTransferCoeff) {
    this.fouledOverallHeatTransferCoeff = fouledOverallHeatTransferCoeff;
  }
}
