package neqsim.process.mechanicaldesign.pipeline;

import neqsim.process.corrosion.NorsokM001MaterialSelection;
import neqsim.process.corrosion.NorsokM506CorrosionRate;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.pipeline.PipeLineInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.MechanicalDesignResponse;
import neqsim.process.mechanicaldesign.designstandards.MaterialPipeDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.PipelineDesignStandard;
import neqsim.thermo.system.SystemInterface;
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
 * <li>CO2 corrosion rate assessment per NORSOK M-506</li>
 * <li>Material selection and corrosion allowance per NORSOK M-001</li>
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

  /** NORSOK M-506 corrosion rate model. */
  private transient NorsokM506CorrosionRate corrosionModel;

  /** NORSOK M-001 material selection model. */
  private transient NorsokM001MaterialSelection materialSelector;

  /** Design life in years for corrosion allowance calculation. */
  private double designLifeYears = 25.0;

  /** Inhibitor efficiency (0 to 1) for corrosion rate calculation. */
  private double inhibitorEfficiency = 0.0;

  /** Glycol (MEG) weight fraction in aqueous phase (0 to 1). */
  private double glycolWeightFraction = 0.0;

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

  // ============================================================================
  // CORROSION EVALUATION (NORSOK M-506 / M-001)
  // ============================================================================

  /**
   * Returns the NORSOK M-506 corrosion rate model for this pipeline.
   *
   * <p>
   * The model is lazily initialized with default parameters. Use the setter methods on the returned
   * model to configure conditions, or call {@link #runCorrosionAnalysis()} to auto-populate from
   * the pipeline's operating stream.
   * </p>
   *
   * @return the corrosion rate model
   */
  public NorsokM506CorrosionRate getCorrosionModel() {
    if (corrosionModel == null) {
      corrosionModel = new NorsokM506CorrosionRate();
    }
    return corrosionModel;
  }

  /**
   * Returns the NORSOK M-001 material selection model for this pipeline.
   *
   * @return the material selection model
   */
  public NorsokM001MaterialSelection getMaterialSelector() {
    if (materialSelector == null) {
      materialSelector = new NorsokM001MaterialSelection();
    }
    return materialSelector;
  }

  /**
   * Sets the design life for corrosion allowance calculations.
   *
   * @param years design life in years (typically 20-30)
   */
  public void setDesignLifeYears(double years) {
    this.designLifeYears = Math.max(1.0, years);
  }

  /**
   * Gets the design life for corrosion allowance calculations.
   *
   * @return design life in years
   */
  public double getDesignLifeYears() {
    return designLifeYears;
  }

  /**
   * Sets the inhibitor efficiency for corrosion calculations.
   *
   * @param efficiency inhibitor efficiency (0.0 = none, 1.0 = perfect)
   */
  public void setInhibitorEfficiency(double efficiency) {
    this.inhibitorEfficiency = Math.max(0.0, Math.min(1.0, efficiency));
  }

  /**
   * Sets the glycol (MEG/DEG) weight fraction for corrosion calculations.
   *
   * @param fraction glycol weight fraction (0.0 to 1.0)
   */
  public void setGlycolWeightFraction(double fraction) {
    this.glycolWeightFraction = Math.max(0.0, Math.min(1.0, fraction));
  }

  /**
   * Runs a complete corrosion analysis using the pipeline's operating conditions.
   *
   * <p>
   * This method extracts temperature, pressure, fluid composition, and flow velocity from the
   * pipeline equipment and its inlet/outlet streams. It then runs the NORSOK M-506 corrosion rate
   * calculation followed by the NORSOK M-001 material selection evaluation.
   * </p>
   *
   * <p>
   * The corrosion allowance result is automatically applied to the mechanical design calculator
   * so that subsequent wall thickness calculations include the correct corrosion allowance.
   * </p>
   */
  public void runCorrosionAnalysis() {
    NorsokM506CorrosionRate model = getCorrosionModel();

    // Extract conditions from pipeline equipment
    double streamTempC = 20.0; // default if no stream available
    if (getProcessEquipment() instanceof PipeLineInterface) {
      PipeLineInterface pipe = (PipeLineInterface) getProcessEquipment();
      model.setPipeDiameterM(pipe.getDiameter());

      // Try to get conditions from the outlet stream (post-simulation)
      StreamInterface outStream = pipe.getOutletStream();
      if (outStream != null && outStream.getFluid() != null) {
        populateFromStream(model, outStream);
        streamTempC = outStream.getFluid().getTemperature() - 273.15;
      }
    }

    model.setInhibitorEfficiency(inhibitorEfficiency);
    model.setGlycolWeightFraction(glycolWeightFraction);
    model.calculate();

    // Run material selection based on corrosion results
    NorsokM001MaterialSelection selector = getMaterialSelector();
    selector.setCO2CorrosionRateMmyr(model.getCorrectedCorrosionRate());
    selector.setH2SPartialPressureBar(model.getH2SPartialPressureBar());
    selector.setCO2PartialPressureBar(model.getCO2PartialPressureBar());
    selector.setDesignTemperatureC(streamTempC);
    selector.setMaxDesignTemperatureC(streamTempC);
    selector.setDesignLifeYears(designLifeYears);
    selector.evaluate();

    // Apply the corrosion allowance to the mechanical design
    double caMm = selector.getRecommendedCorrosionAllowanceMm();
    setCorrosionAllowance(caMm);
    getCalculator().setCorrosionAllowance(caMm / 1000.0); // mm to m
  }

  /**
   * Populates the corrosion model with conditions extracted from a process stream.
   *
   * @param model the corrosion rate model to populate
   * @param stream the process stream to extract conditions from
   */
  private void populateFromStream(NorsokM506CorrosionRate model, StreamInterface stream) {
    SystemInterface fluid = stream.getFluid();
    if (fluid == null) {
      return;
    }

    double tempC = fluid.getTemperature() - 273.15;
    double pressBar = fluid.getPressure();
    model.setTemperatureCelsius(tempC);
    model.setTotalPressureBara(pressBar);

    // Extract CO2 and H2S mole fractions from the gas phase
    if (fluid.hasPhaseType("gas")) {
      int gasPhaseNum = fluid.getPhaseNumberOfPhase("gas");
      double co2Frac = getMoleFractionSafe(fluid, gasPhaseNum, "CO2");
      double h2sFrac = getMoleFractionSafe(fluid, gasPhaseNum, "H2S");
      model.setCO2MoleFraction(co2Frac);
      model.setH2SMoleFraction(h2sFrac);
    } else {
      // Single-phase liquid — use overall composition
      double co2Frac = getMoleFractionSafe(fluid, 0, "CO2");
      double h2sFrac = getMoleFractionSafe(fluid, 0, "H2S");
      model.setCO2MoleFraction(co2Frac);
      model.setH2SMoleFraction(h2sFrac);
    }

    // Extract liquid density and viscosity if liquid phase exists
    if (fluid.hasPhaseType("aqueous") || fluid.hasPhaseType("oil")) {
      String liquidType = fluid.hasPhaseType("aqueous") ? "aqueous" : "oil";
      try {
        fluid.initPhysicalProperties();
        double density = fluid.getPhase(liquidType).getDensity("kg/m3");
        if (density > 0) {
          model.setLiquidDensityKgM3(density);
        }
        double viscosity = fluid.getPhase(liquidType).getViscosity("kg/msec");
        if (viscosity > 0) {
          model.setLiquidViscosityPas(viscosity);
        }
      } catch (Exception ex) {
        // Use defaults if property extraction fails
      }
    }
  }

  /**
   * Safely gets a component mole fraction from a fluid phase.
   *
   * @param fluid the fluid system
   * @param phaseNum the phase number
   * @param componentName the component name
   * @return mole fraction, or 0 if component not present
   */
  private double getMoleFractionSafe(SystemInterface fluid, int phaseNum, String componentName) {
    try {
      if (fluid.getPhase(phaseNum).hasComponent(componentName)) {
        return fluid.getPhase(phaseNum).getComponent(componentName).getx();
      }
    } catch (Exception ex) {
      // Component not present
    }
    return 0.0;
  }

  /**
   * Gets the calculated corrosion rate after running corrosion analysis.
   *
   * @return corrected corrosion rate in mm/yr, or -1 if not yet calculated
   */
  public double getCorrosionRate() {
    if (corrosionModel == null) {
      return -1.0;
    }
    return corrosionModel.getCorrectedCorrosionRate();
  }

  /**
   * Gets the recommended material grade from corrosion analysis.
   *
   * @return recommended material string, or empty string if not yet evaluated
   */
  public String getRecommendedMaterial() {
    if (materialSelector == null) {
      return "";
    }
    return materialSelector.getRecommendedMaterial();
  }

  /**
   * Gets the recommended corrosion allowance from corrosion analysis.
   *
   * @return corrosion allowance in mm, or -1 if not yet evaluated
   */
  public double getRecommendedCorrosionAllowanceMm() {
    if (materialSelector == null) {
      return -1.0;
    }
    return materialSelector.getRecommendedCorrosionAllowanceMm();
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

    // Add corrosion analysis results if available
    if (corrosionModel != null) {
      com.google.gson.JsonObject corrosionObj =
          com.google.gson.JsonParser.parseString(corrosionModel.toJson()).getAsJsonObject();
      jsonObj.add("corrosionAnalysis_NORSOK_M506", corrosionObj);
    }
    if (materialSelector != null) {
      com.google.gson.JsonObject materialObj =
          com.google.gson.JsonParser.parseString(materialSelector.toJson()).getAsJsonObject();
      jsonObj.add("materialSelection_NORSOK_M001", materialObj);
    }

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
