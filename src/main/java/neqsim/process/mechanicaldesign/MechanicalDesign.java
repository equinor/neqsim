package neqsim.process.mechanicaldesign;

import java.awt.BorderLayout;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.mechanicaldesign.data.DatabaseMechanicalDesignDataSource;
import neqsim.process.mechanicaldesign.data.MechanicalDesignDataSource;
import neqsim.process.mechanicaldesign.designstandards.AdsorptionDehydrationDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.CompressorDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.DesignStandard;
import neqsim.process.mechanicaldesign.designstandards.GasScrubberDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.JointEfficiencyPlateStandard;
import neqsim.process.mechanicaldesign.designstandards.MaterialPipeDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.MaterialPlateDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.PipelineDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.PressureVesselDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.SeparatorDesignStandard;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * MechanicalDesign class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class MechanicalDesign implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Getter for the field <code>materialPipeDesignStandard</code>.
   * </p>
   *
   * @return the materialPipeDesignStandard
   */
  public MaterialPipeDesignStandard getMaterialPipeDesignStandard() {
    return materialPipeDesignStandard;
  }

  /**
   * <p>
   * Setter for the field <code>materialPipeDesignStandard</code>.
   * </p>
   *
   * @param materialPipeDesignStandard the materialPipeDesignStandard to set
   */
  public void setMaterialPipeDesignStandard(MaterialPipeDesignStandard materialPipeDesignStandard) {
    this.materialPipeDesignStandard = materialPipeDesignStandard;
  }

  /**
   * <p>
   * getMaterialDesignStandard.
   * </p>
   *
   * @return the materialDesignStandard
   */
  public MaterialPlateDesignStandard getMaterialDesignStandard() {
    return materialPlateDesignStandard;
  }

  /**
   * <p>
   * setMaterialDesignStandard.
   * </p>
   *
   * @param materialDesignStandard the materialDesignStandard to set
   */
  public void setMaterialDesignStandard(MaterialPlateDesignStandard materialDesignStandard) {
    this.materialPlateDesignStandard = materialDesignStandard;
  }

  private boolean hasSetCompanySpecificDesignStandards = false;
  private double maxOperationPressure = 100.0;
  private double minOperationPressure = 1.0;
  private double maxOperationTemperature = 100.0;
  private double minOperationTemperature = 1.0;
  public double maxDesignVolumeFlow = 0.0;
  public double minDesignVolumeFLow = 0.0;
  public double maxDesignGassVolumeFlow = 0.0;
  public double minDesignGassVolumeFLow = 0.0;
  public double maxDesignOilVolumeFlow = 0.0;
  public double minDesignOilFLow = 0.0;
  public double maxDesignWaterVolumeFlow = 0.0;
  public double minDesignWaterVolumeFLow = 0.0;
  public double maxDesignPower = 0.0;
  public double minDesignPower = 0.0;
  public double maxDesignDuty = 0.0;
  public double minDesignDuty = 0.0;
  private String companySpecificDesignStandards = "Statoil";
  private ProcessEquipmentInterface processEquipment = null;
  // private String pressureVesselDesignStandard = "ASME - Pressure Vessel Code";
  // private String pipingDesignStandard="Statoil";
  // private String valveDesignStandard="Statoil";
  private double tensileStrength = 483; // MPa
  private double jointEfficiency = 1.0; // fully radiographed
  private MaterialPlateDesignStandard materialPlateDesignStandard =
      new MaterialPlateDesignStandard();
  private MaterialPipeDesignStandard materialPipeDesignStandard = new MaterialPipeDesignStandard();
  private String construtionMaterial = "steel";
  private double corrosionAllowance = 0.0; // mm
  private double pressureMarginFactor = 0.1;
  private DesignLimitData designLimitData = DesignLimitData.EMPTY;
  private MechanicalDesignMarginResult lastMarginResult = MechanicalDesignMarginResult.EMPTY;
  private List<MechanicalDesignDataSource> designDataSources = new ArrayList<>();
  public double innerDiameter = 0.0;
  public double outerDiameter = 0.0;
  /** Wall thickness in mm. */
  public double wallThickness = 0.0;
  public double tantanLength = 0.0; // tantan is same as seamtoseam length
  private double weightTotal = 0.0;
  private double volumeTotal = 2.0;
  public double weigthInternals = 0.0;
  public double weightNozzle = 0.0;
  public double weightPiping = 0.0;
  public double weightElectroInstrument = 0.0;
  public double weightStructualSteel = 0.0;
  public double weightVessel = 0.0;
  public double weigthVesselShell = 0.0;
  public double moduleHeight = 0.0;
  public double moduleWidth = 0;
  public double moduleLength = 0.0;
  public Hashtable<String, DesignStandard> designStandard = new Hashtable<String, DesignStandard>();
  public UnitCostEstimateBaseClass costEstimate = null;
  double defaultLiquidDensity = 1000.0;
  double defaultLiquidViscosity = 0.001012;

  /**
   * <p>
   * Constructor for MechanicalDesign.
   * </p>
   *
   * @param processEquipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public MechanicalDesign(ProcessEquipmentInterface processEquipment) {
    this.processEquipment = processEquipment;
    costEstimate = new UnitCostEstimateBaseClass(this);
    initMechanicalDesign();
  }

  /** Initialize design data using configured data sources. */
  public void initMechanicalDesign() {
    loadDesignLimits();
  }

  private void loadDesignLimits() {
    String equipmentType = resolveEquipmentType();
    if (equipmentType.isEmpty()) {
      designLimitData = DesignLimitData.EMPTY;
      return;
    }

    String companyIdentifier = Objects.toString(companySpecificDesignStandards, "");
    DesignLimitData loadedData = null;
    for (MechanicalDesignDataSource dataSource : getActiveDesignDataSources()) {
      Optional<DesignLimitData> candidate =
          dataSource.getDesignLimits(equipmentType, companyIdentifier);
      if (candidate.isPresent()) {
        loadedData = candidate.get();
        break;
      }
    }

    if (loadedData == null) {
      designLimitData = DesignLimitData.EMPTY;
      return;
    }

    designLimitData = loadedData;

    if (!Double.isNaN(loadedData.getCorrosionAllowance())) {
      corrosionAllowance = loadedData.getCorrosionAllowance();
    }
    if (!Double.isNaN(loadedData.getJointEfficiency())) {
      jointEfficiency = loadedData.getJointEfficiency();
    }
  }

  private List<MechanicalDesignDataSource> getActiveDesignDataSources() {
    if (designDataSources.isEmpty()) {
      List<MechanicalDesignDataSource> defaults = new ArrayList<>();
      defaults.add(new DatabaseMechanicalDesignDataSource());
      return defaults;
    }
    return new ArrayList<>(designDataSources);
  }

  private String resolveEquipmentType() {
    if (processEquipment == null) {
      return "";
    }
    return processEquipment.getClass().getSimpleName();
  }

  /**
   * Configure a single data source to load design limits from.
   *
   * @param dataSource data source to use, {@code null} clears existing sources.
   */
  public void setDesignDataSource(MechanicalDesignDataSource dataSource) {
    if (dataSource == null) {
      designDataSources = new ArrayList<>();
    } else {
      designDataSources = new ArrayList<>();
      designDataSources.add(dataSource);
    }
    initMechanicalDesign();
  }

  /**
   * Configure the list of data sources to use when loading design limits.
   *
   * @param dataSources ordered list of data sources.
   */
  public void setDesignDataSources(List<MechanicalDesignDataSource> dataSources) {
    if (dataSources == null) {
      designDataSources = new ArrayList<>();
    } else {
      designDataSources = new ArrayList<>(dataSources);
    }
    initMechanicalDesign();
  }

  /** Add an additional data source used when loading design limits. */
  public void addDesignDataSource(MechanicalDesignDataSource dataSource) {
    if (dataSource == null) {
      return;
    }
    designDataSources.add(dataSource);
    initMechanicalDesign();
  }

  /**
   * Get the immutable list of configured data sources.
   *
   * @return list of data sources.
   */
  public List<MechanicalDesignDataSource> getDesignDataSources() {
    return Collections.unmodifiableList(designDataSources);
  }

  public DesignLimitData getDesignLimitData() {
    return designLimitData;
  }

  public double getDesignMaxPressureLimit() {
    return designLimitData.getMaxPressure();
  }

  public double getDesignMinPressureLimit() {
    return designLimitData.getMinPressure();
  }

  public double getDesignMaxTemperatureLimit() {
    return designLimitData.getMaxTemperature();
  }

  public double getDesignMinTemperatureLimit() {
    return designLimitData.getMinTemperature();
  }

  public double getDesignCorrosionAllowance() {
    return designLimitData.getCorrosionAllowance();
  }

  public double getDesignJointEfficiency() {
    return designLimitData.getJointEfficiency();
  }

  /**
   * Validate the current operating envelope against design limits.
   *
   * @return computed margin result.
   */
  public MechanicalDesignMarginResult validateOperatingEnvelope() {
    return validateOperatingEnvelope(maxOperationPressure, minOperationPressure,
        maxOperationTemperature, minOperationTemperature, corrosionAllowance, jointEfficiency);
  }

  /**
   * Validate a specific operating envelope against design limits.
   *
   * @param operatingMaxPressure maximum operating pressure.
   * @param operatingMinPressure minimum operating pressure.
   * @param operatingMaxTemperature maximum operating temperature.
   * @param operatingMinTemperature minimum operating temperature.
   * @param operatingCorrosionAllowance corrosion allowance used in operation.
   * @param operatingJointEfficiency joint efficiency achieved in operation.
   * @return computed margin result.
   */
  public MechanicalDesignMarginResult validateOperatingEnvelope(double operatingMaxPressure,
      double operatingMinPressure, double operatingMaxTemperature, double operatingMinTemperature,
      double operatingCorrosionAllowance, double operatingJointEfficiency) {
    double maxPressureMargin =
        marginToUpperLimit(designLimitData.getMaxPressure(), operatingMaxPressure);
    double minPressureMargin =
        marginFromLowerLimit(operatingMinPressure, designLimitData.getMinPressure());
    double maxTemperatureMargin =
        marginToUpperLimit(designLimitData.getMaxTemperature(), operatingMaxTemperature);
    double minTemperatureMargin =
        marginFromLowerLimit(operatingMinTemperature, designLimitData.getMinTemperature());
    double corrosionMargin =
        marginFromLowerLimit(operatingCorrosionAllowance, designLimitData.getCorrosionAllowance());
    double jointMargin =
        marginFromLowerLimit(operatingJointEfficiency, designLimitData.getJointEfficiency());

    lastMarginResult = new MechanicalDesignMarginResult(maxPressureMargin, minPressureMargin,
        maxTemperatureMargin, minTemperatureMargin, corrosionMargin, jointMargin);
    return lastMarginResult;
  }

  public MechanicalDesignMarginResult getLastMarginResult() {
    return lastMarginResult;
  }

  private double marginToUpperLimit(double limit, double value) {
    if (Double.isNaN(limit) || Double.isNaN(value)) {
      return Double.NaN;
    }
    return limit - value;
  }

  private double marginFromLowerLimit(double value, double limit) {
    if (Double.isNaN(limit) || Double.isNaN(value)) {
      return Double.NaN;
    }
    return value - limit;
  }

  /**
   * <p>
   * Getter for the field <code>maxOperationPressure</code>.
   * </p>
   *
   * @return the maxPressure
   */
  public double getMaxOperationPressure() {
    return maxOperationPressure;
  }

  /**
   * <p>
   * getMaxDesignPressure.
   * </p>
   *
   * @return a double
   */
  public double getMaxDesignPressure() {
    return getMaxOperationPressure() * (1.0 + pressureMarginFactor);
  }

  /**
   * <p>
   * getMinDesignPressure.
   * </p>
   *
   * @return a double
   */
  public double getMinDesignPressure() {
    return getMinOperationPressure() * (1.0 - pressureMarginFactor);
  }

  /**
   * <p>
   * readDesignSpecifications.
   * </p>
   */
  public void readDesignSpecifications() {}

  /**
   * <p>
   * Setter for the field <code>maxOperationPressure</code>.
   * </p>
   *
   * @param maxPressure the maxPressure to set
   */
  public void setMaxOperationPressure(double maxPressure) {
    this.maxOperationPressure = maxPressure;
  }

  /**
   * <p>
   * Getter for the field <code>minOperationPressure</code>.
   * </p>
   *
   * @return the minPressure
   */
  public double getMinOperationPressure() {
    return minOperationPressure;
  }

  /**
   * <p>
   * Setter for the field <code>minOperationPressure</code>.
   * </p>
   *
   * @param minPressure the minPressure to set
   */
  public void setMinOperationPressure(double minPressure) {
    this.minOperationPressure = minPressure;
  }

  /**
   * <p>
   * Getter for the field <code>maxOperationTemperature</code>.
   * </p>
   *
   * @return the maxTemperature
   */
  public double getMaxOperationTemperature() {
    return maxOperationTemperature;
  }

  /**
   * <p>
   * Setter for the field <code>maxOperationTemperature</code>.
   * </p>
   *
   * @param maxTemperature the maxTemperature to set
   */
  public void setMaxOperationTemperature(double maxTemperature) {
    this.maxOperationTemperature = maxTemperature;
  }

  /**
   * <p>
   * Getter for the field <code>minOperationTemperature</code>.
   * </p>
   *
   * @return the minTemperature
   */
  public double getMinOperationTemperature() {
    return minOperationTemperature;
  }

  /**
   * <p>
   * Setter for the field <code>minOperationTemperature</code>.
   * </p>
   *
   * @param minTemperature the minTemperature to set
   */
  public void setMinOperationTemperature(double minTemperature) {
    this.minOperationTemperature = minTemperature;
  }

  /**
   * <p>
   * Getter for the field <code>processEquipment</code>.
   * </p>
   *
   * @return the processEquipment
   */
  public ProcessEquipmentInterface getProcessEquipment() {
    return processEquipment;
  }

  /**
   * <p>
   * Setter for the field <code>processEquipment</code>.
   * </p>
   *
   * @param processEquipment the processEquipment to set
   */
  public void setProcessEquipment(ProcessEquipmentInterface processEquipment) {
    this.processEquipment = processEquipment;
  }

  /**
   * <p>
   * calcDesign.
   * </p>
   */
  public void calcDesign() {
    // System.out.println("reading design parameters for: " + processEquipment.getName());
    if (!hasSetCompanySpecificDesignStandards) {
      setCompanySpecificDesignStandards("default");
    }
    readDesignSpecifications();
  }

  /**
   * <p>
   * setDesign.
   * </p>
   */
  public void setDesign() {
    // System.out.println("reading design parameters for: " + processEquipment.getName());
    readDesignSpecifications();
  }

  /**
   * <p>
   * Getter for the field <code>tensileStrength</code>.
   * </p>
   *
   * @return the tensileStrength
   */
  public double getTensileStrength() {
    return tensileStrength;
  }

  /**
   * <p>
   * Setter for the field <code>tensileStrength</code>.
   * </p>
   *
   * @param tensileStrength the tensileStrength to set
   */
  public void setTensileStrength(double tensileStrength) {
    this.tensileStrength = tensileStrength;
  }

  /**
   * <p>
   * Getter for the field <code>construtionMaterial</code>.
   * </p>
   *
   * @return the construtionMaterial
   */
  public String getConstrutionMaterial() {
    return construtionMaterial;
  }

  /**
   * <p>
   * Setter for the field <code>construtionMaterial</code>.
   * </p>
   *
   * @param construtionMaterial the construtionMaterial to set
   */
  public void setConstrutionMaterial(String construtionMaterial) {
    this.construtionMaterial = construtionMaterial;
  }

  /**
   * <p>
   * Getter for the field <code>jointEfficiency</code>.
   * </p>
   *
   * @return the jointEfficiency
   */
  public double getJointEfficiency() {
    return jointEfficiency;
  }

  /**
   * <p>
   * getMaxAllowableStress.
   * </p>
   *
   * @return a double
   */
  public double getMaxAllowableStress() {
    return tensileStrength / 3.5;
  }

  /**
   * <p>
   * Setter for the field <code>jointEfficiency</code>.
   * </p>
   *
   * @param jointEfficiency the jointEfficiency to set
   */
  public void setJointEfficiency(double jointEfficiency) {
    this.jointEfficiency = jointEfficiency;
  }

  /**
   * <p>
   * Getter for the field <code>corrosionAllowance</code>.
   * </p>
   *
   * @return the corrosionAllowance
   */
  public double getCorrosionAllowance() {
    return corrosionAllowance;
  }

  /**
   * <p>
   * Setter for the field <code>corrosionAllowance</code>.
   * </p>
   *
   * @param corrosionAllowance the corrosionAllowance to set
   */
  public void setCorrosionAllowance(double corrosionAllowance) {
    this.corrosionAllowance = corrosionAllowance;
  }

  /**
   * <p>
   * Getter for the field <code>pressureMarginFactor</code>.
   * </p>
   *
   * @return the pressureMarginFactor
   */
  public double getPressureMarginFactor() {
    return pressureMarginFactor;
  }

  /**
   * <p>
   * Setter for the field <code>pressureMarginFactor</code>.
   * </p>
   *
   * @param pressureMarginFactor the pressureMarginFactor to set
   */
  public void setPressureMarginFactor(double pressureMarginFactor) {
    this.pressureMarginFactor = pressureMarginFactor;
  }

  /**
   * <p>
   * Getter for the field <code>outerDiameter</code>.
   * </p>
   *
   * @return a double
   */
  public double getOuterDiameter() {
    return outerDiameter;
  }

  /**
   * <p>
   * Getter for the field <code>companySpecificDesignStandards</code>.
   * </p>
   *
   * @return the companySpecificDesignStandards
   */
  public String getCompanySpecificDesignStandards() {
    return companySpecificDesignStandards;
  }

  /**
   * <p>
   * Setter for the field <code>companySpecificDesignStandards</code>.
   * </p>
   *
   * @param companySpecificDesignStandards the companySpecificDesignStandards to set
   */
  public void setCompanySpecificDesignStandards(String companySpecificDesignStandards) {
    this.companySpecificDesignStandards =
        companySpecificDesignStandards == null ? "" : companySpecificDesignStandards;

    if (this.companySpecificDesignStandards.equals("StatoilTR")) {
      getDesignStandard().put("pressure vessel design code",
          new PressureVesselDesignStandard("ASME - Pressure Vessel Code", this));
      getDesignStandard().put("separator process design",
          new SeparatorDesignStandard("StatoilTR", this));
      getDesignStandard().put("gas scrubber process design",
          new GasScrubberDesignStandard("Statoil_TR1414", this));
      getDesignStandard().put("adsorption dehydration process design",
          new AdsorptionDehydrationDesignStandard("", this));
      getDesignStandard().put("pipeline design codes",
          new PipelineDesignStandard("Statoil_TR1414", this));
      getDesignStandard().put("compressor design codes",
          new CompressorDesignStandard("Statoil_TR1414", this));
      getDesignStandard().put("material plate design codes",
          new MaterialPlateDesignStandard("Statoil_TR1414", this));
      getDesignStandard().put("plate Joint Efficiency design codes",
          new JointEfficiencyPlateStandard("Statoil_TR1414", this));
      getDesignStandard().put("material pipe design codes",
          new MaterialPipeDesignStandard("Statoil_TR1414", this));

      // pressureVesselDesignStandard = "ASME - Pressure Vessel Code";
      // setPipingDesignStandard("TR1945_Statoil");
      // setValveDesignStandard("TR1903_Statoil");
    } else {
      System.out.println("using default mechanical design standards...no design standard "
          + this.companySpecificDesignStandards);
      getDesignStandard().put("pressure vessel design code",
          new PressureVesselDesignStandard("ASME - Pressure Vessel Code", this));
      getDesignStandard().put("separator process design",
          new SeparatorDesignStandard("StatoilTR", this));
      getDesignStandard().put("gas scrubber process design",
          new GasScrubberDesignStandard("Statoil_TR1414", this));
      getDesignStandard().put("adsorption dehydration process design",
          new AdsorptionDehydrationDesignStandard("", this));
      getDesignStandard().put("pipeline design codes",
          new PipelineDesignStandard("Statoil_TR1414", this));
      getDesignStandard().put("compressor design codes",
          new CompressorDesignStandard("Statoil_TR1414", this));
      getDesignStandard().put("material plate design codes",
          new MaterialPlateDesignStandard("Statoil_TR1414", this));
      getDesignStandard().put("plate Joint Efficiency design codes",
          new JointEfficiencyPlateStandard("Statoil_TR1414", this));
      getDesignStandard().put("material pipe design codes",
          new MaterialPipeDesignStandard("Statoil_TR1414", this));
    }
    hasSetCompanySpecificDesignStandards = true;
    initMechanicalDesign();
  }

  /**
   * <p>
   * Getter for the field <code>innerDiameter</code>.
   * </p>
   *
   * @return the innerDiameter
   */
  public double getInnerDiameter() {
    return innerDiameter;
  }

  /**
   * <p>
   * Setter for the field <code>innerDiameter</code>.
   * </p>
   *
   * @param innerDiameter the innerDiameter to set
   */
  public void setInnerDiameter(double innerDiameter) {
    this.innerDiameter = innerDiameter;
  }

  /**
   * <p>
   * Setter for the field <code>outerDiameter</code>.
   * </p>
   *
   * @param outerDiameter the outerDiameter to set
   */
  public void setOuterDiameter(double outerDiameter) {
    this.outerDiameter = outerDiameter;
  }

  /**
   * <p>
   * Getter for the field <code>wallThickness</code>.
   * </p>
   *
   * @return the wallThickness
   */
  public double getWallThickness() {
    return wallThickness;
  }

  /**
   * <p>
   * Setter for the field <code>wallThickness</code>.
   * </p>
   *
   * @param wallThickness the wallThickness to set
   */
  public void setWallThickness(double wallThickness) {
    this.wallThickness = wallThickness;
  }

  /**
   * <p>
   * Getter for the field <code>tantanLength</code>.
   * </p>
   *
   * @return the tantanLength
   */
  public double getTantanLength() {
    return tantanLength;
  }

  /**
   * <p>
   * Setter for the field <code>tantanLength</code>.
   * </p>
   *
   * @param tantanLength the tantanLength to set
   */
  public void setTantanLength(double tantanLength) {
    this.tantanLength = tantanLength;
  }

  /**
   * <p>
   * Getter for the field <code>weightTotal</code>.
   * </p>
   *
   * @return the weightTotal
   */
  public double getWeightTotal() {
    return weightTotal;
  }

  /**
   * <p>
   * Setter for the field <code>weightTotal</code>.
   * </p>
   *
   * @param weightTotal the weightTotal to set
   */
  public void setWeightTotal(double weightTotal) {
    this.weightTotal = weightTotal;
  }

  /**
   * <p>
   * Getter for the field <code>weigthInternals</code>.
   * </p>
   *
   * @return the wigthInternals
   */
  public double getWeigthInternals() {
    return weigthInternals;
  }

  /**
   * <p>
   * Setter for the field <code>weigthInternals</code>.
   * </p>
   *
   * @param weigthInternals the weigthInternals to set
   */
  public void setWeigthInternals(double weigthInternals) {
    this.weigthInternals = weigthInternals;
  }

  /**
   * <p>
   * Getter for the field <code>weightVessel</code>.
   * </p>
   *
   * @return the weightShell
   */
  public double getWeightVessel() {
    return weightVessel;
  }

  /**
   * <p>
   * Setter for the field <code>weightVessel</code>.
   * </p>
   *
   * @param weightVessel the weightShell to set
   */
  public void setWeightVessel(double weightVessel) {
    this.weightVessel = weightVessel;
  }

  /**
   * <p>
   * Getter for the field <code>weightNozzle</code>.
   * </p>
   *
   * @return the weightNozzle
   */
  public double getWeightNozzle() {
    return weightNozzle;
  }

  /**
   * <p>
   * Setter for the field <code>weightNozzle</code>.
   * </p>
   *
   * @param weightNozzle the weightNozzle to set
   */
  public void setWeightNozzle(double weightNozzle) {
    this.weightNozzle = weightNozzle;
  }

  /**
   * <p>
   * Getter for the field <code>weightPiping</code>.
   * </p>
   *
   * @return the weightPiping
   */
  public double getWeightPiping() {
    return weightPiping;
  }

  /**
   * <p>
   * Setter for the field <code>weightPiping</code>.
   * </p>
   *
   * @param weightPiping the weightPiping to set
   */
  public void setWeightPiping(double weightPiping) {
    this.weightPiping = weightPiping;
  }

  /**
   * <p>
   * Getter for the field <code>weightElectroInstrument</code>.
   * </p>
   *
   * @return the weightElectroInstrument
   */
  public double getWeightElectroInstrument() {
    return weightElectroInstrument;
  }

  /**
   * <p>
   * Setter for the field <code>weightElectroInstrument</code>.
   * </p>
   *
   * @param weightElectroInstrument the weightElectroInstrument to set
   */
  public void setWeightElectroInstrument(double weightElectroInstrument) {
    this.weightElectroInstrument = weightElectroInstrument;
  }

  /**
   * <p>
   * Getter for the field <code>weightStructualSteel</code>.
   * </p>
   *
   * @return the weightStructualSteel
   */
  public double getWeightStructualSteel() {
    return weightStructualSteel;
  }

  /**
   * <p>
   * Setter for the field <code>weightStructualSteel</code>.
   * </p>
   *
   * @param weightStructualSteel the weightStructualSteel to set
   */
  public void setWeightStructualSteel(double weightStructualSteel) {
    this.weightStructualSteel = weightStructualSteel;
  }

  /**
   * <p>
   * Getter for the field <code>weigthVesselShell</code>.
   * </p>
   *
   * @return the weigthVesselShell
   */
  public double getWeigthVesselShell() {
    return weigthVesselShell;
  }

  /**
   * <p>
   * Setter for the field <code>weigthVesselShell</code>.
   * </p>
   *
   * @param weigthVesselShell the weigthVesselShell to set
   */
  public void setWeigthVesselShell(double weigthVesselShell) {
    this.weigthVesselShell = weigthVesselShell;
  }

  /**
   * <p>
   * Getter for the field <code>moduleHeight</code>.
   * </p>
   *
   * @return the moduleHeight
   */
  public double getModuleHeight() {
    return moduleHeight;
  }

  /**
   * <p>
   * Setter for the field <code>moduleHeight</code>.
   * </p>
   *
   * @param moduleHeight the moduleHeight to set
   */
  public void setModuleHeight(double moduleHeight) {
    this.moduleHeight = moduleHeight;
  }

  /**
   * <p>
   * Getter for the field <code>moduleWidth</code>.
   * </p>
   *
   * @return the moduleWidth
   */
  public double getModuleWidth() {
    return moduleWidth;
  }

  /**
   * <p>
   * Setter for the field <code>moduleWidth</code>.
   * </p>
   *
   * @param moduleWidth the moduleWidth to set
   */
  public void setModuleWidth(double moduleWidth) {
    this.moduleWidth = moduleWidth;
  }

  /**
   * <p>
   * Getter for the field <code>moduleLength</code>.
   * </p>
   *
   * @return the moduleLength
   */
  public double getModuleLength() {
    return moduleLength;
  }

  /**
   * <p>
   * Setter for the field <code>moduleLength</code>.
   * </p>
   *
   * @param moduleLength the moduleLength to set
   */
  public void setModuleLength(double moduleLength) {
    this.moduleLength = moduleLength;
  }

  /**
   * <p>
   * Getter for the field <code>designStandard</code>.
   * </p>
   *
   * @return the designStandard
   */
  public Hashtable<String, DesignStandard> getDesignStandard() {
    return designStandard;
  }

  /**
   * <p>
   * Setter for the field <code>designStandard</code>.
   * </p>
   *
   * @param designStandard the designStandard to set
   */
  public void setDesignStandard(Hashtable<String, DesignStandard> designStandard) {
    this.designStandard = designStandard;
  }

  /**
   * <p>
   * Getter for the field <code>maxDesignVolumeFlow</code>.
   * </p>
   *
   * @return the maxDesignVolumeFlow
   */
  public double getMaxDesignVolumeFlow() {
    return maxDesignVolumeFlow;
  }

  /**
   * <p>
   * Setter for the field <code>maxDesignVolumeFlow</code>.
   * </p>
   *
   * @param maxDesignVolumeFlow the maxDesignVolumeFlow to set
   */
  public void setMaxDesignVolumeFlow(double maxDesignVolumeFlow) {
    this.maxDesignVolumeFlow = maxDesignVolumeFlow;
  }

  /**
   * <p>
   * Getter for the field <code>minDesignVolumeFLow</code>.
   * </p>
   *
   * @return the minDesignVolumeFLow
   */
  public double getMinDesignVolumeFLow() {
    return minDesignVolumeFLow;
  }

  /**
   * <p>
   * Setter for the field <code>minDesignVolumeFLow</code>.
   * </p>
   *
   * @param minDesignVolumeFLow the minDesignVolumeFLow to set
   */
  public void setMinDesignVolumeFLow(double minDesignVolumeFLow) {
    this.minDesignVolumeFLow = minDesignVolumeFLow;
  }

  /**
   * <p>
   * Getter for the field <code>maxDesignGassVolumeFlow</code>.
   * </p>
   *
   * @return the maxDesignGassVolumeFlow
   */
  public double getMaxDesignGassVolumeFlow() {
    return maxDesignGassVolumeFlow;
  }

  /**
   * <p>
   * Setter for the field <code>maxDesignGassVolumeFlow</code>.
   * </p>
   *
   * @param maxDesignGassVolumeFlow the maxDesignGassVolumeFlow to set
   */
  public void setMaxDesignGassVolumeFlow(double maxDesignGassVolumeFlow) {
    this.maxDesignGassVolumeFlow = maxDesignGassVolumeFlow;
  }

  /**
   * <p>
   * Getter for the field <code>minDesignGassVolumeFLow</code>.
   * </p>
   *
   * @return the minDesignGassVolumeFLow
   */
  public double getMinDesignGassVolumeFLow() {
    return minDesignGassVolumeFLow;
  }

  /**
   * <p>
   * Setter for the field <code>minDesignGassVolumeFLow</code>.
   * </p>
   *
   * @param minDesignGassVolumeFLow the minDesignGassVolumeFLow to set
   */
  public void setMinDesignGassVolumeFLow(double minDesignGassVolumeFLow) {
    this.minDesignGassVolumeFLow = minDesignGassVolumeFLow;
  }

  /**
   * <p>
   * Getter for the field <code>maxDesignOilVolumeFlow</code>.
   * </p>
   *
   * @return the maxDesignOilVolumeFlow
   */
  public double getMaxDesignOilVolumeFlow() {
    return maxDesignOilVolumeFlow;
  }

  /**
   * <p>
   * Setter for the field <code>maxDesignOilVolumeFlow</code>.
   * </p>
   *
   * @param maxDesignOilVolumeFlow the maxDesignOilVolumeFlow to set
   */
  public void setMaxDesignOilVolumeFlow(double maxDesignOilVolumeFlow) {
    this.maxDesignOilVolumeFlow = maxDesignOilVolumeFlow;
  }

  /**
   * <p>
   * Getter for the field <code>minDesignOilFLow</code>.
   * </p>
   *
   * @return the minDesignOilFLow
   */
  public double getMinDesignOilFLow() {
    return minDesignOilFLow;
  }

  /**
   * <p>
   * Setter for the field <code>minDesignOilFLow</code>.
   * </p>
   *
   * @param minDesignOilFLow the minDesignOilFLow to set
   */
  public void setMinDesignOilFLow(double minDesignOilFLow) {
    this.minDesignOilFLow = minDesignOilFLow;
  }

  /**
   * <p>
   * Getter for the field <code>maxDesignWaterVolumeFlow</code>.
   * </p>
   *
   * @return the maxDesignWaterVolumeFlow
   */
  public double getMaxDesignWaterVolumeFlow() {
    return maxDesignWaterVolumeFlow;
  }

  /**
   * <p>
   * Setter for the field <code>maxDesignWaterVolumeFlow</code>.
   * </p>
   *
   * @param maxDesignWaterVolumeFlow the maxDesignWaterVolumeFlow to set
   */
  public void setMaxDesignWaterVolumeFlow(double maxDesignWaterVolumeFlow) {
    this.maxDesignWaterVolumeFlow = maxDesignWaterVolumeFlow;
  }

  /**
   * <p>
   * Getter for the field <code>minDesignWaterVolumeFLow</code>.
   * </p>
   *
   * @return the minDesignWaterVolumeFLow
   */
  public double getMinDesignWaterVolumeFLow() {
    return minDesignWaterVolumeFLow;
  }

  /**
   * <p>
   * Setter for the field <code>minDesignWaterVolumeFLow</code>.
   * </p>
   *
   * @param minDesignWaterVolumeFLow the minDesignWaterVolumeFLow to set
   */
  public void setMinDesignWaterVolumeFLow(double minDesignWaterVolumeFLow) {
    this.minDesignWaterVolumeFLow = minDesignWaterVolumeFLow;
  }

  /**
   * <p>
   * displayResults.
   * </p>
   */
  @ExcludeFromJacocoGeneratedReport
  public void displayResults() {
    JFrame dialog = new JFrame("Unit design " + getProcessEquipment().getName());
    Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new BorderLayout());

    String[][] table = new String[3][3]; // createTable(getProcessEquipment().getName());
    table[1][0] = getProcessEquipment().getName();
    table[1][1] = Double.toString(getWeightTotal());
    table[1][2] = Double.toString(getVolumeTotal());
    String[] names = {"", "Volume", "Weight"};
    JTable Jtab = new JTable(table, names);
    JScrollPane scrollpane = new JScrollPane(Jtab);
    dialogContentPane.add(scrollpane);
    dialog.setSize(800, 600); // pack();
    // dialog.pack();
    dialog.setVisible(true);
  }

  /**
   * <p>
   * Getter for the field <code>volumeTotal</code>.
   * </p>
   *
   * @return the volumeTotal
   */
  public double getVolumeTotal() {
    return volumeTotal;
  }

  /**
   * <p>
   * isHasSetCompanySpecificDesignStandards.
   * </p>
   *
   * @return the hasSetCompanySpecificDesignStandards
   */
  public boolean isHasSetCompanySpecificDesignStandards() {
    return hasSetCompanySpecificDesignStandards;
  }

  /**
   * <p>
   * Setter for the field <code>hasSetCompanySpecificDesignStandards</code>.
   * </p>
   *
   * @param hasSetCompanySpecificDesignStandards the hasSetCompanySpecificDesignStandards to set
   */
  public void setHasSetCompanySpecificDesignStandards(
      boolean hasSetCompanySpecificDesignStandards) {
    this.hasSetCompanySpecificDesignStandards = hasSetCompanySpecificDesignStandards;
  }

  /**
   * <p>
   * Getter for the field <code>costEstimate</code>.
   * </p>
   *
   * @return the costEstimate
   */
  public UnitCostEstimateBaseClass getCostEstimate() {
    return costEstimate;
  }

  /**
   * <p>
   * Setter for the field <code>defaultLiquidDensity</code>.
   * </p>
   *
   * @param defaultLiqDens a double
   */
  public void setDefaultLiquidDensity(double defaultLiqDens) {
    this.defaultLiquidDensity = defaultLiqDens;
  }

  /**
   * <p>
   * Getter for the field <code>defaultLiquidDensity</code>.
   * </p>
   *
   * @return a double
   */
  public double getDefaultLiquidDensity() {
    return defaultLiquidDensity;
  }

  /**
   * <p>
   * Setter for the field <code>defaultLiquidViscosity</code>.
   * </p>
   *
   * @param defaultLiqVisc a double
   */
  public void setDefaultLiquidViscosity(double defaultLiqVisc) {
    this.defaultLiquidViscosity = defaultLiqVisc;
  }

  /**
   * <p>
   * Getter for the field <code>defaultLiquidViscosity</code>.
   * </p>
   *
   * @return a double
   */
  public double getDefaultLiquidViscosity() {
    return defaultLiquidViscosity;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(companySpecificDesignStandards, construtionMaterial, corrosionAllowance,
        costEstimate, designStandard, hasSetCompanySpecificDesignStandards, innerDiameter,
        jointEfficiency, materialPipeDesignStandard, materialPlateDesignStandard,
        maxDesignGassVolumeFlow, maxDesignOilVolumeFlow, maxDesignVolumeFlow,
        maxDesignWaterVolumeFlow, maxOperationPressure, maxOperationTemperature,
        minDesignGassVolumeFLow, minDesignOilFLow, minDesignVolumeFLow, minDesignWaterVolumeFLow,
        minOperationPressure, minOperationTemperature, moduleHeight, moduleLength, moduleWidth,
        outerDiameter, pressureMarginFactor, processEquipment, tantanLength, tensileStrength,
        volumeTotal, wallThickness, weightElectroInstrument, weightNozzle, weightPiping,
        weightStructualSteel, weightTotal, weightVessel, weigthInternals, weigthVesselShell);
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    MechanicalDesign other = (MechanicalDesign) obj;
    return Objects.equals(companySpecificDesignStandards, other.companySpecificDesignStandards)
        && Objects.equals(construtionMaterial, other.construtionMaterial)
        && Double.doubleToLongBits(corrosionAllowance) == Double
            .doubleToLongBits(other.corrosionAllowance)
        && Objects.equals(costEstimate, other.costEstimate)
        && Objects.equals(designStandard, other.designStandard)
        && hasSetCompanySpecificDesignStandards == other.hasSetCompanySpecificDesignStandards
        && Double.doubleToLongBits(innerDiameter) == Double.doubleToLongBits(other.innerDiameter)
        && Double.doubleToLongBits(jointEfficiency) == Double
            .doubleToLongBits(other.jointEfficiency)
        && Objects.equals(materialPipeDesignStandard, other.materialPipeDesignStandard)
        && Objects.equals(materialPlateDesignStandard, other.materialPlateDesignStandard)
        && Double.doubleToLongBits(maxDesignGassVolumeFlow) == Double
            .doubleToLongBits(other.maxDesignGassVolumeFlow)
        && Double.doubleToLongBits(maxDesignOilVolumeFlow) == Double
            .doubleToLongBits(other.maxDesignOilVolumeFlow)
        && Double.doubleToLongBits(maxDesignVolumeFlow) == Double
            .doubleToLongBits(other.maxDesignVolumeFlow)
        && Double.doubleToLongBits(maxDesignWaterVolumeFlow) == Double
            .doubleToLongBits(other.maxDesignWaterVolumeFlow)
        && Double.doubleToLongBits(maxOperationPressure) == Double
            .doubleToLongBits(other.maxOperationPressure)
        && Double.doubleToLongBits(maxOperationTemperature) == Double
            .doubleToLongBits(other.maxOperationTemperature)
        && Double.doubleToLongBits(minDesignGassVolumeFLow) == Double
            .doubleToLongBits(other.minDesignGassVolumeFLow)
        && Double.doubleToLongBits(minDesignOilFLow) == Double
            .doubleToLongBits(other.minDesignOilFLow)
        && Double.doubleToLongBits(minDesignVolumeFLow) == Double
            .doubleToLongBits(other.minDesignVolumeFLow)
        && Double.doubleToLongBits(minDesignWaterVolumeFLow) == Double
            .doubleToLongBits(other.minDesignWaterVolumeFLow)
        && Double.doubleToLongBits(minOperationPressure) == Double
            .doubleToLongBits(other.minOperationPressure)
        && Double.doubleToLongBits(minOperationTemperature) == Double
            .doubleToLongBits(other.minOperationTemperature)
        && Double.doubleToLongBits(moduleHeight) == Double.doubleToLongBits(other.moduleHeight)
        && Double.doubleToLongBits(moduleLength) == Double.doubleToLongBits(other.moduleLength)
        && Double.doubleToLongBits(moduleWidth) == Double.doubleToLongBits(other.moduleWidth)
        && Double.doubleToLongBits(outerDiameter) == Double.doubleToLongBits(other.outerDiameter)
        && Double.doubleToLongBits(pressureMarginFactor) == Double
            .doubleToLongBits(other.pressureMarginFactor)
        && Objects.equals(processEquipment, other.processEquipment)
        && Double.doubleToLongBits(tantanLength) == Double.doubleToLongBits(other.tantanLength)
        && Double.doubleToLongBits(tensileStrength) == Double
            .doubleToLongBits(other.tensileStrength)
        && Double.doubleToLongBits(volumeTotal) == Double.doubleToLongBits(other.volumeTotal)
        && Double.doubleToLongBits(wallThickness) == Double.doubleToLongBits(other.wallThickness)
        && Double.doubleToLongBits(weightElectroInstrument) == Double
            .doubleToLongBits(other.weightElectroInstrument)
        && Double.doubleToLongBits(weightNozzle) == Double.doubleToLongBits(other.weightNozzle)
        && Double.doubleToLongBits(weightPiping) == Double.doubleToLongBits(other.weightPiping)
        && Double.doubleToLongBits(weightStructualSteel) == Double
            .doubleToLongBits(other.weightStructualSteel)
        && Double.doubleToLongBits(weightTotal) == Double.doubleToLongBits(other.weightTotal)
        && Double.doubleToLongBits(weightVessel) == Double.doubleToLongBits(other.weightVessel)
        && Double.doubleToLongBits(weigthInternals) == Double
            .doubleToLongBits(other.weigthInternals)
        && Double.doubleToLongBits(weigthVesselShell) == Double
            .doubleToLongBits(other.weigthVesselShell);
  }
}
