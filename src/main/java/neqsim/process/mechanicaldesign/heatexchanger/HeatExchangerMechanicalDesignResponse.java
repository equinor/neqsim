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

    // The base HeatExchangerMechanicalDesign may have limited fields
    // This will be populated based on what's available
    // Subclasses can override to add more specific data
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
}
