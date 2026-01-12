package neqsim.process.mechanicaldesign.valve;

import neqsim.process.mechanicaldesign.MechanicalDesignResponse;

/**
 * Response class for valve mechanical design JSON export.
 *
 * <p>
 * Extends {@link MechanicalDesignResponse} with valve-specific parameters including sizing data,
 * actuator requirements, and flow characteristics per IEC 60534 and ANSI/ISA-75.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class ValveMechanicalDesignResponse extends MechanicalDesignResponse {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  // ============================================================================
  // Valve-Specific Parameters
  // ============================================================================

  /** Valve type (globe, ball, butterfly, gate, plug). */
  private String valveType;

  /** Valve characteristic (linear, equal percentage, quick opening). */
  private String valveCharacteristic;

  /** ANSI pressure class (150, 300, 600, 900, 1500, 2500). */
  private int ansiPressureClass;

  /** Nominal valve size [inches]. */
  private double nominalSizeInches;

  /** Required valve Cv. */
  private double cvRequired;

  /** Maximum valve Cv at full open. */
  private double cvMax;

  /** Valve opening percentage at design point. */
  private double valveOpening;

  /** Calculated Kv (metric flow coefficient). */
  private double kv;

  /** Face-to-face dimension [mm]. */
  private double faceToFace;

  /** Body wall thickness [mm]. */
  private double bodyWallThickness;

  /** Body weight [kg]. */
  private double bodyWeight;

  /** Actuator weight [kg]. */
  private double actuatorWeight;

  /** Actuator type (pneumatic, electric, hydraulic, manual). */
  private String actuatorType;

  /** Required actuator thrust [N]. */
  private double requiredActuatorThrust;

  /** Stem diameter [mm]. */
  private double stemDiameter;

  /** Flange type (RF, RTJ, FF). */
  private String flangeType;

  /** Inlet pressure [bara]. */
  private double inletPressure;

  /** Outlet pressure [bara]. */
  private double outletPressure;

  /** Pressure drop [bar]. */
  private double pressureDrop;

  /** Pressure recovery factor (FL). */
  private double flFactor;

  /** Pressure ratio factor (xT). */
  private double xtFactor;

  /** Flow regime (subcritical, critical, choked). */
  private String flowRegime;

  /** Mass flow rate [kg/h]. */
  private double massFlowRate;

  /** Volumetric flow rate [m³/h]. */
  private double volumetricFlowRate;

  /** Noise level [dBA]. */
  private double noiseLevel;

  /** Cavitation index. */
  private double cavitationIndex;

  /** Is flow choked? */
  private boolean isChoked;

  // ============================================================================
  // Constructors
  // ============================================================================

  /**
   * Default constructor.
   */
  public ValveMechanicalDesignResponse() {
    super();
    setEquipmentType("Valve");
    setDesignStandard("IEC 60534 / ANSI/ISA-75");
  }

  /**
   * Constructor from ValveMechanicalDesign.
   *
   * @param mecDesign the valve mechanical design
   */
  public ValveMechanicalDesignResponse(ValveMechanicalDesign mecDesign) {
    super(mecDesign);
    setEquipmentType("Valve");
    setDesignStandard("IEC 60534 / ANSI/ISA-75");
    populateFromValveDesign(mecDesign);
  }

  /**
   * Populate valve-specific fields from ValveMechanicalDesign.
   *
   * @param mecDesign the valve mechanical design
   */
  public void populateFromValveDesign(ValveMechanicalDesign mecDesign) {
    if (mecDesign == null) {
      return;
    }

    this.valveType = mecDesign.getValveType();
    this.valveCharacteristic = mecDesign.getValveCharacterization();
    this.ansiPressureClass = mecDesign.getAnsiPressureClass();
    this.nominalSizeInches = mecDesign.getNominalSizeInches();
    this.cvMax = mecDesign.getValveCvMax();
    this.faceToFace = mecDesign.getFaceToFace();
    this.bodyWallThickness = mecDesign.getBodyWallThickness();
    this.bodyWeight = mecDesign.getBodyWeight();
    this.actuatorWeight = mecDesign.getActuatorWeight();
    this.requiredActuatorThrust = mecDesign.getRequiredActuatorThrust();
    this.stemDiameter = mecDesign.getStemDiameter();
    this.flangeType = mecDesign.getFlangeType();
    this.inletPressure = mecDesign.getInletPressure();
    this.outletPressure = mecDesign.getOutletPressure();
    this.pressureDrop = mecDesign.getDp();
    this.flFactor = mecDesign.getFL();
    this.xtFactor = mecDesign.getxT();
  }

  // ============================================================================
  // Getters and Setters
  // ============================================================================

  public String getValveType() {
    return valveType;
  }

  public void setValveType(String valveType) {
    this.valveType = valveType;
  }

  public String getValveCharacteristic() {
    return valveCharacteristic;
  }

  public void setValveCharacteristic(String valveCharacteristic) {
    this.valveCharacteristic = valveCharacteristic;
  }

  public int getAnsiPressureClass() {
    return ansiPressureClass;
  }

  public void setAnsiPressureClass(int ansiPressureClass) {
    this.ansiPressureClass = ansiPressureClass;
  }

  public double getNominalSizeInches() {
    return nominalSizeInches;
  }

  public void setNominalSizeInches(double nominalSizeInches) {
    this.nominalSizeInches = nominalSizeInches;
  }

  public double getCvRequired() {
    return cvRequired;
  }

  public void setCvRequired(double cvRequired) {
    this.cvRequired = cvRequired;
  }

  public double getCvMax() {
    return cvMax;
  }

  public void setCvMax(double cvMax) {
    this.cvMax = cvMax;
  }

  public double getValveOpening() {
    return valveOpening;
  }

  public void setValveOpening(double valveOpening) {
    this.valveOpening = valveOpening;
  }

  public double getKv() {
    return kv;
  }

  public void setKv(double kv) {
    this.kv = kv;
  }

  public double getFaceToFace() {
    return faceToFace;
  }

  public void setFaceToFace(double faceToFace) {
    this.faceToFace = faceToFace;
  }

  public double getBodyWallThickness() {
    return bodyWallThickness;
  }

  public void setBodyWallThickness(double bodyWallThickness) {
    this.bodyWallThickness = bodyWallThickness;
  }

  public double getBodyWeight() {
    return bodyWeight;
  }

  public void setBodyWeight(double bodyWeight) {
    this.bodyWeight = bodyWeight;
  }

  public double getActuatorWeight() {
    return actuatorWeight;
  }

  public void setActuatorWeight(double actuatorWeight) {
    this.actuatorWeight = actuatorWeight;
  }

  public String getActuatorType() {
    return actuatorType;
  }

  public void setActuatorType(String actuatorType) {
    this.actuatorType = actuatorType;
  }

  public double getRequiredActuatorThrust() {
    return requiredActuatorThrust;
  }

  public void setRequiredActuatorThrust(double requiredActuatorThrust) {
    this.requiredActuatorThrust = requiredActuatorThrust;
  }

  public double getStemDiameter() {
    return stemDiameter;
  }

  public void setStemDiameter(double stemDiameter) {
    this.stemDiameter = stemDiameter;
  }

  public String getFlangeType() {
    return flangeType;
  }

  public void setFlangeType(String flangeType) {
    this.flangeType = flangeType;
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

  public double getPressureDrop() {
    return pressureDrop;
  }

  public void setPressureDrop(double pressureDrop) {
    this.pressureDrop = pressureDrop;
  }

  public double getFlFactor() {
    return flFactor;
  }

  public void setFlFactor(double flFactor) {
    this.flFactor = flFactor;
  }

  public double getXtFactor() {
    return xtFactor;
  }

  public void setXtFactor(double xtFactor) {
    this.xtFactor = xtFactor;
  }

  public String getFlowRegime() {
    return flowRegime;
  }

  public void setFlowRegime(String flowRegime) {
    this.flowRegime = flowRegime;
  }

  public double getMassFlowRate() {
    return massFlowRate;
  }

  public void setMassFlowRate(double massFlowRate) {
    this.massFlowRate = massFlowRate;
  }

  public double getVolumetricFlowRate() {
    return volumetricFlowRate;
  }

  public void setVolumetricFlowRate(double volumetricFlowRate) {
    this.volumetricFlowRate = volumetricFlowRate;
  }

  public double getNoiseLevel() {
    return noiseLevel;
  }

  public void setNoiseLevel(double noiseLevel) {
    this.noiseLevel = noiseLevel;
  }

  public double getCavitationIndex() {
    return cavitationIndex;
  }

  public void setCavitationIndex(double cavitationIndex) {
    this.cavitationIndex = cavitationIndex;
  }

  public boolean isChoked() {
    return isChoked;
  }

  public void setChoked(boolean isChoked) {
    this.isChoked = isChoked;
  }
}
