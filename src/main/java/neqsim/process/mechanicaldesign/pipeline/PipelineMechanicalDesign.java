package neqsim.process.mechanicaldesign.pipeline;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.pipeline.PipeLineInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.MechanicalDesignResponse;
import neqsim.process.mechanicaldesign.designstandards.MaterialPipeDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.PipelineDesignStandard;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Mechanical design class for pipelines.
 *
 * <p>
 * This class integrates pipeline mechanical design calculations with the NeqSim mechanical design
 * framework, providing:
 * </p>
 * <ul>
 * <li>Wall thickness calculation per ASME B31.3/B31.4/B31.8 and DNV-OS-F101</li>
 * <li>Integration with TORG (Technical Requirements Documents)</li>
 * <li>JSON export for data exchange</li>
 * <li>Database lookup for material properties and design standards</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>{@code
 * AdiabaticPipe pipe = new AdiabaticPipe("pipeline", stream);
 * pipe.setDiameter(0.508); // 20 inch
 * pipe.setLength(50000.0); // 50 km
 *
 * // Initialize mechanical design
 * pipe.initMechanicalDesign();
 * PipelineMechanicalDesign design = (PipelineMechanicalDesign) pipe.getMechanicalDesign();
 *
 * // Set design conditions
 * design.setMaxOperationPressure(150.0); // bara
 * design.setMaxOperationTemperature(80.0 + 273.15); // K
 *
 * // Apply company standards
 * design.setCompanySpecificDesignStandards("Equinor");
 * design.readDesignSpecifications();
 *
 * // Calculate design
 * design.calcDesign();
 *
 * // Export to JSON
 * String json = design.toJson();
 * }</pre>
 *
 * @author ESOL
 * @version 2.0
 */
public class PipelineMechanicalDesign extends MechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Inner diameter in meters. */
  double innerDiameter = 1.0;

  /** Design standard code. */
  String designStandardCode = "ANSI/ASME Standard B31.8";

  /** Internal calculator for standards-based calculations. */
  private transient PipeMechanicalDesignCalculator calculator;

  /** Pipeline length in meters. */
  private double pipelineLength = 1000.0;

  /** Material grade per API 5L. */
  private String materialGrade = "X65";

  /** Location class per ASME B31.8. */
  private int locationClass = 1;

  /** Data source for loading from database. */
  private transient PipelineMechanicalDesignDataSource dataSource;

  /**
   * Constructor for PipelineMechanicalDesign.
   *
   * @param equipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public PipelineMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
    initializeFromPipeline();
  }

  /**
   * Initialize design parameters from pipeline equipment.
   */
  private void initializeFromPipeline() {
    if (getProcessEquipment() instanceof PipeLineInterface) {
      PipeLineInterface pipe = (PipeLineInterface) getProcessEquipment();
      this.innerDiameter = pipe.getDiameter();
      this.pipelineLength = pipe.getLength();
      this.outerDiameter = pipe.getDiameter() + 2 * pipe.getWallThickness();
      if (pipe.getWallThickness() > 0) {
        this.wallThickness = pipe.getWallThickness() * 1000; // Convert to mm
      }
    }
  }

  /**
   * Get the data source for database access.
   *
   * @return the data source instance
   */
  public PipelineMechanicalDesignDataSource getDataSource() {
    if (dataSource == null) {
      dataSource = new PipelineMechanicalDesignDataSource();
    }
    return dataSource;
  }

  /**
   * Get the internal calculator for standards-based calculations.
   *
   * @return the calculator instance
   */
  public PipeMechanicalDesignCalculator getCalculator() {
    if (calculator == null) {
      calculator = new PipeMechanicalDesignCalculator();
      // Initialize from current settings
      calculator.setOuterDiameter(outerDiameter > 0 ? outerDiameter : innerDiameter + 0.02);
      calculator.setDesignPressure(getMaxOperationPressure() * 0.1); // bar to MPa
      calculator.setDesignTemperature(getMaxOperationTemperature() - 273.15); // K to C
      calculator.setMaterialGrade(materialGrade);
      calculator.setLocationClass(locationClass);
    }
    return calculator;
  }

  /**
   * Load all design parameters from database tables.
   *
   * <p>
   * This method loads material properties and design factors from the NeqSim process design
   * database based on the configured material grade and company identifier.
   * </p>
   */
  public void loadFromDatabase() {
    String company = getCompanySpecificDesignStandards();
    if (company == null || company.isEmpty()) {
      company = "default";
    }

    // Load into calculator via data source
    getDataSource().loadIntoCalculator(materialGrade, company, getCalculator());

    // Sync location class back
    this.locationClass = getCalculator().getLocationClass();
  }

  /**
   * Load material properties from database.
   *
   * @param grade API 5L material grade (e.g., "X52", "X65", "X70")
   */
  public void loadMaterialFromDatabase(String grade) {
    this.materialGrade = grade;
    java.util.Optional<PipelineMechanicalDesignDataSource.PipeMaterialData> materialOpt =
        getDataSource().loadMaterialProperties(grade);

    if (materialOpt.isPresent()) {
      PipelineMechanicalDesignDataSource.PipeMaterialData material = materialOpt.get();
      getCalculator().setSmys(material.smys);
      getCalculator().setSmts(material.smts);
    } else {
      // Fall back to built-in API 5L data
      getCalculator().setMaterialGrade(grade);
    }
  }

  /**
   * Load design factors from database for the current company.
   */
  public void loadDesignFactorsFromDatabase() {
    String company = getCompanySpecificDesignStandards();
    if (company == null || company.isEmpty()) {
      return;
    }

    PipelineMechanicalDesignDataSource.PipeDesignFactors factors =
        getDataSource().loadDesignFactors(company);

    // Apply to calculator
    getCalculator().setDesignFactor(factors.designFactor);
    getCalculator().setJointFactor(factors.jointFactor);
    getCalculator().setCorrosionAllowance(factors.corrosionAllowance / 1000.0); // mm to m
    getCalculator().setFabricationTolerance(factors.fabricationTolerance);
    this.locationClass = factors.locationClass;
    getCalculator().setLocationClass(factors.locationClass);
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    super.readDesignSpecifications();

    // Load from database based on company standards
    if (getCompanySpecificDesignStandards() != null
        && !getCompanySpecificDesignStandards().isEmpty()) {
      loadFromDatabase();
    }

    if (getDesignStandard().containsKey("material pipe design codes")) {
      MaterialPipeDesignStandard matStd =
          (MaterialPipeDesignStandard) getDesignStandard().get("material pipe design codes");
      matStd.getDesignFactor();
      // Sync with calculator
      getCalculator().setDesignFactor(matStd.getDesignFactor());
      getCalculator().setJointFactor(matStd.getEfactor());
    }

    if (getDesignStandard().containsKey("pipeline design codes")) {
      PipelineDesignStandard pipeStd =
          (PipelineDesignStandard) getDesignStandard().get("pipeline design codes");
      wallThickness = pipeStd.calcPipelineWallThickness();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();

    // Sync calculator with current settings
    PipeMechanicalDesignCalculator calc = getCalculator();
    calc.setOuterDiameter(outerDiameter > 0 ? outerDiameter : innerDiameter + 0.02);
    calc.setDesignPressure(getMaxOperationPressure() * 0.1); // bar to MPa
    calc.setDesignTemperature(getMaxOperationTemperature() - 273.15); // K to C

    // Map design code
    if ("ANSI/ASME Standard B31.8".equals(designStandardCode)) {
      calc.setDesignCode(PipeMechanicalDesignCalculator.ASME_B31_8);
      wallThickness = calc.calculateMinimumWallThickness() * 1000; // m to mm
    } else if ("ANSI/ASME Standard B31.3".equals(designStandardCode)) {
      calc.setDesignCode(PipeMechanicalDesignCalculator.ASME_B31_3);
      wallThickness = calc.calculateMinimumWallThickness() * 1000;
    } else if ("ANSI/ASME Standard B31.4".equals(designStandardCode)) {
      calc.setDesignCode(PipeMechanicalDesignCalculator.ASME_B31_4);
      wallThickness = calc.calculateMinimumWallThickness() * 1000;
    } else if ("DNV-OS-F101".equals(designStandardCode)) {
      calc.setDesignCode(PipeMechanicalDesignCalculator.DNV_OS_F101);
      wallThickness = calc.calculateMinimumWallThickness() * 1000;
    } else {
      // Default B31.8 calculation
      calc.setDesignCode(PipeMechanicalDesignCalculator.ASME_B31_8);
      wallThickness = calc.calculateMinimumWallThickness() * 1000;
    }

    // Calculate MAOP and stresses
    calc.calculateMAOP();
    calc.calculateHoopStress(calc.getDesignPressure());
    calc.calculateVonMisesStress(calc.getDesignPressure(), 0, true);
  }

  /**
   * Get the calculated MAOP (Maximum Allowable Operating Pressure).
   *
   * @param unit pressure unit ("bar", "MPa", "psi")
   * @return MAOP in specified unit
   */
  public double getMAOP(String unit) {
    return getCalculator().getMaop(unit);
  }

  /**
   * Get the calculated test pressure.
   *
   * @return test pressure in MPa
   */
  public double getTestPressure() {
    return getCalculator().calculateTestPressure();
  }

  /**
   * Check if the mechanical design is within safe limits.
   *
   * @return true if design is safe
   */
  public boolean isDesignSafe() {
    return getCalculator().isDesignSafe();
  }

  /**
   * Set the material grade per API 5L.
   *
   * @param grade material grade (e.g., "X52", "X65", "X70")
   */
  public void setMaterialGrade(String grade) {
    this.materialGrade = grade;
    if (calculator != null) {
      calculator.setMaterialGrade(grade);
    }
  }

  /**
   * Get the material grade.
   *
   * @return material grade
   */
  public String getMaterialGrade() {
    return materialGrade;
  }

  /**
   * Set the location class per ASME B31.8.
   *
   * @param locationClass location class 1-4
   */
  public void setLocationClass(int locationClass) {
    this.locationClass = Math.max(1, Math.min(4, locationClass));
    if (calculator != null) {
      calculator.setLocationClass(this.locationClass);
    }
  }

  /**
   * Get the location class.
   *
   * @return location class 1-4
   */
  public int getLocationClass() {
    return locationClass;
  }

  /**
   * Set the design standard code.
   *
   * @param code design standard code
   */
  public void setDesignStandardCode(String code) {
    this.designStandardCode = code;
  }

  /**
   * Get the design standard code.
   *
   * @return design standard code
   */
  public String getDesignStandardCode() {
    return designStandardCode;
  }

  /**
   * Get the pipeline length.
   *
   * @return pipeline length in meters
   */
  public double getPipelineLength() {
    return pipelineLength;
  }

  /**
   * Set the pipeline length.
   *
   * @param length pipeline length in meters
   */
  public void setPipelineLength(double length) {
    this.pipelineLength = length;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    // Create enhanced response with pipeline-specific data
    MechanicalDesignResponse response = new MechanicalDesignResponse(this);

    // Add pipeline-specific data from calculator
    java.util.Map<String, Object> calcData = getCalculator().toMap();

    // Merge calculator data into response
    com.google.gson.JsonObject jsonObj =
        com.google.gson.JsonParser.parseString(response.toJson()).getAsJsonObject();
    com.google.gson.JsonObject calcObj =
        com.google.gson.JsonParser.parseString(getCalculator().toJson()).getAsJsonObject();

    // Add pipeline-specific fields
    jsonObj.addProperty("pipelineLength_m", pipelineLength);
    jsonObj.addProperty("materialGrade", materialGrade);
    jsonObj.addProperty("locationClass", locationClass);
    jsonObj.addProperty("designStandardCode", designStandardCode);
    jsonObj.add("pipelineDesignCalculations", calcObj);

    return new com.google.gson.GsonBuilder().setPrettyPrinting()
        .serializeSpecialFloatingPointValues().create().toJson(jsonObj);
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 20.0), 90.00);
    testSystem.addComponent("methane", 600e3, "kg/hr");
    testSystem.addComponent("ethane", 7.00e3, "kg/hr");
    testSystem.addComponent("propane", 12.0e3, "kg/hr");

    testSystem.createDatabase(true);
    testSystem.setMultiPhaseCheck(true);
    testSystem.setMixingRule(2);

    Stream stream_1 = new Stream("Stream1", testSystem);

    AdiabaticPipe pipe = new AdiabaticPipe("pipe", stream_1);
    pipe.setDiameter(1.0);
    pipe.setLength(1000.0);
    pipe.getMechanicalDesign().setMaxOperationPressure(100.0);
    pipe.getMechanicalDesign().setMaxOperationTemperature(273.155 + 60.0);
    pipe.getMechanicalDesign().setMinOperationPressure(50.0);
    pipe.getMechanicalDesign().setMaxDesignGassVolumeFlow(100.0);

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_1);
    operations.add(pipe);

    // operations.getSystemMechanicalDesign().setCompanySpecificDesignStandards("Statoil");
    // operations.getSystemMechanicalDesign().runDesignCalculation();
    // operations.getSystemMechanicalDesign().setDesign();
    operations.run();
  }
}
