package neqsim.process.mechanicaldesign.valve;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import neqsim.process.equipment.valve.ValveInterface;
import neqsim.process.mechanicaldesign.MechanicalDesignResponse;

/**
 * Response class for valve mechanical design JSON export.
 *
 * <p>
 * Extends {@link MechanicalDesignResponse} with valve-specific parameters including sizing data, actuator requirements,
 * and flow characteristics per IEC 60534 and ANSI/ISA-75.
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

  /** Selected/limiting trim maximum Cv, or legacy required Cv when no catalog is evaluated. */
  private double cvMax;

  /** Available vendor trim capacity options. */
  private List<ValveTrimOption> availableTrimOptions = new ArrayList<ValveTrimOption>();

  /** Trim capacity assessment status. */
  private String trimAssessmentStatus;

  /** Identifier of the automatically selected trim. */
  private String selectedTrimIdentifier;

  /** Relative size of the selected or limiting trim [%]. */
  private double relativeTrimSizePercent;

  /** Maximum design Cv of the selected or limiting trim. */
  private double selectedTrimMaximumCv;

  /** Largest maximum design Cv in the supplied trim catalog. */
  private double maximumAvailableTrimCv;

  /** Required-Cv utilization of the selected or limiting trim. */
  private double trimCvUtilization;

  /** Remaining Cv capacity of the selected or limiting trim. */
  private double trimCvCapacityMargin;

  /** Maximum allowed trim utilization used for selection. */
  private double maximumAllowedTrimUtilization;

  /** Whether a feasible trim was selected. */
  private boolean trimFeasible;

  /** Selected or limiting trim material. */
  private String trimMaterial;

  /** Selected or limiting trim construction. */
  private String trimConstruction;

  /** Engineering recommendation from the trim capacity assessment. */
  private String trimRecommendation;

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
    this.cvRequired = Double.isFinite(mecDesign.getRequiredCv()) ? mecDesign.getRequiredCv() : 0.0;
    this.cvMax = mecDesign.getValveCvMax();
    this.availableTrimOptions = new ArrayList<ValveTrimOption>(mecDesign.getAvailableTrimOptions());
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

    if (mecDesign.getProcessEquipment() instanceof ValveInterface) {
      this.valveOpening = ((ValveInterface) mecDesign.getProcessEquipment()).getPercentValveOpening();
    }

    ValveTrimSizingResult trimResult = mecDesign.getTrimSizingResult();
    this.trimAssessmentStatus = trimResult.getStatus().name();
    this.maximumAvailableTrimCv = trimResult.getMaximumAvailableCv();
    this.trimCvUtilization = trimResult.getUtilization();
    this.trimCvCapacityMargin = trimResult.getCapacityMarginCv();
    this.maximumAllowedTrimUtilization = trimResult.getMaximumAllowedUtilization();
    this.trimFeasible = trimResult.isFeasible();
    this.trimRecommendation = trimResult.getRecommendation();

    ValveTrimOption selectedOption = trimResult.getSelectedTrimOption();
    if (selectedOption != null) {
      this.selectedTrimIdentifier = selectedOption.getIdentifier();
    } else {
      this.selectedTrimIdentifier = "";
    }

    ValveTrimOption limitingOption = trimResult.getLimitingTrimOption();
    if (limitingOption != null) {
      this.cvMax = limitingOption.getMaximumDesignCv();
      this.relativeTrimSizePercent = limitingOption.getRelativeTrimSizePercent();
      this.selectedTrimMaximumCv = limitingOption.getMaximumDesignCv();
      this.trimMaterial = limitingOption.getMaterial();
      this.trimConstruction = limitingOption.getConstruction();
    } else {
      this.trimMaterial = "";
      this.trimConstruction = "";
    }
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

  public List<ValveTrimOption> getAvailableTrimOptions() {
    return Collections.unmodifiableList(new ArrayList<ValveTrimOption>(availableTrimOptions));
  }

  public void setAvailableTrimOptions(List<ValveTrimOption> availableTrimOptions) {
    this.availableTrimOptions = availableTrimOptions == null ? new ArrayList<ValveTrimOption>()
        : new ArrayList<ValveTrimOption>(availableTrimOptions);
  }

  public String getTrimAssessmentStatus() {
    return trimAssessmentStatus;
  }

  public void setTrimAssessmentStatus(String trimAssessmentStatus) {
    this.trimAssessmentStatus = trimAssessmentStatus;
  }

  public String getSelectedTrimIdentifier() {
    return selectedTrimIdentifier;
  }

  public void setSelectedTrimIdentifier(String selectedTrimIdentifier) {
    this.selectedTrimIdentifier = selectedTrimIdentifier;
  }

  public double getRelativeTrimSizePercent() {
    return relativeTrimSizePercent;
  }

  public void setRelativeTrimSizePercent(double relativeTrimSizePercent) {
    this.relativeTrimSizePercent = relativeTrimSizePercent;
  }

  public double getSelectedTrimMaximumCv() {
    return selectedTrimMaximumCv;
  }

  public void setSelectedTrimMaximumCv(double selectedTrimMaximumCv) {
    this.selectedTrimMaximumCv = selectedTrimMaximumCv;
  }

  public double getMaximumAvailableTrimCv() {
    return maximumAvailableTrimCv;
  }

  public void setMaximumAvailableTrimCv(double maximumAvailableTrimCv) {
    this.maximumAvailableTrimCv = maximumAvailableTrimCv;
  }

  public double getTrimCvUtilization() {
    return trimCvUtilization;
  }

  public void setTrimCvUtilization(double trimCvUtilization) {
    this.trimCvUtilization = trimCvUtilization;
  }

  public double getTrimCvCapacityMargin() {
    return trimCvCapacityMargin;
  }

  public void setTrimCvCapacityMargin(double trimCvCapacityMargin) {
    this.trimCvCapacityMargin = trimCvCapacityMargin;
  }

  public double getMaximumAllowedTrimUtilization() {
    return maximumAllowedTrimUtilization;
  }

  public void setMaximumAllowedTrimUtilization(double maximumAllowedTrimUtilization) {
    this.maximumAllowedTrimUtilization = maximumAllowedTrimUtilization;
  }

  public boolean isTrimFeasible() {
    return trimFeasible;
  }

  public void setTrimFeasible(boolean trimFeasible) {
    this.trimFeasible = trimFeasible;
  }

  public String getTrimMaterial() {
    return trimMaterial;
  }

  public void setTrimMaterial(String trimMaterial) {
    this.trimMaterial = trimMaterial;
  }

  public String getTrimConstruction() {
    return trimConstruction;
  }

  public void setTrimConstruction(String trimConstruction) {
    this.trimConstruction = trimConstruction;
  }

  public String getTrimRecommendation() {
    return trimRecommendation;
  }

  public void setTrimRecommendation(String trimRecommendation) {
    this.trimRecommendation = trimRecommendation;
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
