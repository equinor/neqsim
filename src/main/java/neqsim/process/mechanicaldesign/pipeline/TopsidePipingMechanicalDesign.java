package neqsim.process.mechanicaldesign.pipeline;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.pipeline.TopsidePiping;
import neqsim.process.mechanicaldesign.MechanicalDesignResponse;

/**
 * Mechanical design class for topside (offshore platform and onshore facility) piping.
 *
 * &lt;p&gt; This class provides mechanical design capabilities for topside process piping
 * including: &lt;/p&gt; &lt;ul&gt; &lt;li&gt;Wall thickness per ASME B31.3 Process
 * Piping&lt;/li&gt; &lt;li&gt;Erosional velocity per API RP 14E&lt;/li&gt; &lt;li&gt;Pipe support
 * spacing per NORSOK L-002&lt;/li&gt; &lt;li&gt;Flow-induced vibration (FIV) screening&lt;/li&gt;
 * &lt;li&gt;Acoustic-induced vibration (AIV) analysis&lt;/li&gt; &lt;li&gt;Thermal expansion stress
 * analysis&lt;/li&gt; &lt;/ul&gt;
 *
 * &lt;h2&gt;Usage Example&lt;/h2&gt;
 *
 * &lt;pre&gt;{@code
 * // Create topside piping
 * TopsidePiping pipe = new TopsidePiping("HP Gas Header", stream);
 * pipe.setDiameter(0.2032); // 8 inch
 * pipe.setLength(50.0);
 * pipe.run();
 *
 * // Get and configure mechanical design
 * TopsidePipingMechanicalDesign design = pipe.getTopsideMechanicalDesign();
 * design.setMaxOperationPressure(100.0);
 * design.setMaxOperationTemperature(80.0 + 273.15);
 * design.setMaterialGrade("A106-B");
 * design.setDesignStandardCode("ASME-B31.3");
 * design.setCompanySpecificDesignStandards("Equinor");
 *
 * // Load standards and calculate
 * design.readDesignSpecifications();
 * design.calcDesign();
 *
 * // Get results
 * String json = design.toJson();
 * boolean velocityOk = design.getCalculator().isVelocityCheckPassed();
 * double supportSpacing = design.getCalculator().getSupportSpacing();
 * }&lt;/pre&gt;
 *
 * @author ASMF
 * @version 1.0
 */
public class TopsidePipingMechanicalDesign extends PipelineMechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Topside-specific calculator. */
  private transient TopsidePipingMechanicalDesignCalculator topsideCalculator;

  /** Data source for loading design parameters. */
  private transient TopsidePipingMechanicalDesignDataSource dataSource;

  /** Service type for this piping. */
  private String serviceType = "PROCESS_GAS";

  /** Pipe schedule. */
  private String pipeSchedule = "SCH_40";

  // ============ Operating Envelope ============
  /** Minimum operating temperature in Kelvin. */
  private double minOperationTemperature = 273.15;

  /** Installation temperature in Kelvin. */
  private double installationTemperature = 293.15;

  // ============ Layout Configuration ============
  /** Number of 90-degree elbows. */
  private int numberOfElbows90 = 0;

  /** Number of tees. */
  private int numberOfTees = 0;

  /** Number of valves. */
  private int numberOfValves = 0;

  /** Number of expansion loops. */
  private int numberOfExpansionLoops = 0;

  /** Calculated support spacing in meters. */
  private double calculatedSupportSpacing = 0.0;

  /** Number of calculated supports. */
  private int numberOfSupports = 0;

  /**
   * Constructor for TopsidePipingMechanicalDesign.
   *
   * @param equipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public TopsidePipingMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
    initializeCalculator();
    initializeFromEquipment();
  }

  /**
   * Initialize the topside-specific calculator.
   */
  private void initializeCalculator() {
    topsideCalculator = new TopsidePipingMechanicalDesignCalculator();
    dataSource = new TopsidePipingMechanicalDesignDataSource();
  }

  /**
   * Initialize parameters from the connected equipment.
   */
  private void initializeFromEquipment() {
    if (getProcessEquipment() instanceof TopsidePiping) {
      TopsidePiping pipe = (TopsidePiping) getProcessEquipment();

      serviceType = pipe.getServiceType().name();
      pipeSchedule = pipe.getPipeScheduleEnum().getDisplayName();

      numberOfElbows90 = pipe.getNumberOfElbows90();
      numberOfTees = pipe.getNumberOfTees();
      numberOfValves = pipe.getNumberOfValves();
      numberOfExpansionLoops = pipe.getNumberOfExpansionLoops();

      if (pipe.getMaxOperatingPressure() > 0) {
        setMaxOperationPressure(pipe.getMaxOperatingPressure());
      }
      if (pipe.getMaxOperatingTemperature() > -273) {
        setMaxOperationTemperature(pipe.getMaxOperatingTemperature() + 273.15);
      }
      if (pipe.getMinOperatingTemperature() > -273) {
        minOperationTemperature = pipe.getMinOperatingTemperature() + 273.15;
      }
    }

    // Get flow properties from stream
    if (getProcessEquipment() instanceof PipeBeggsAndBrills) {
      PipeBeggsAndBrills pipe = (PipeBeggsAndBrills) getProcessEquipment();
      if (pipe.getInletStream() != null && pipe.getInletStream().getFluid() != null) {
        try {
          double massFlow = pipe.getInletStream().getFlowRate("kg/sec");
          double density = pipe.getInletStream().getFluid().getDensity("kg/m3");
          topsideCalculator.setMassFlowRate(massFlow);
          topsideCalculator.setMixtureDensity(density);

          // Get phase fractions
          if (pipe.getInletStream().getFluid().hasPhaseType("gas")) {
            double gasDensity =
                pipe.getInletStream().getFluid().getPhase("gas").getDensity("kg/m3");
            topsideCalculator.setGasDensity(gasDensity);
          }
          if (pipe.getInletStream().getFluid().hasPhaseType("oil")
              || pipe.getInletStream().getFluid().hasPhaseType("aqueous")) {
            double liqFrac = 0.0;
            if (pipe.getInletStream().getFluid().hasPhaseType("oil")) {
              liqFrac += pipe.getInletStream().getFluid().getPhase("oil").getVolume()
                  / pipe.getInletStream().getFluid().getVolume();
            }
            if (pipe.getInletStream().getFluid().hasPhaseType("aqueous")) {
              liqFrac += pipe.getInletStream().getFluid().getPhase("aqueous").getVolume()
                  / pipe.getInletStream().getFluid().getVolume();
            }
            topsideCalculator.setLiquidFraction(liqFrac);
          }
        } catch (Exception e) {
          // Stream not yet run - will update later
        }
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    // First read base pipeline specifications
    super.readDesignSpecifications();

    // Ensure calculator is initialized
    if (topsideCalculator == null) {
      initializeCalculator();
    }
    if (dataSource == null) {
      dataSource = new TopsidePipingMechanicalDesignDataSource();
    }

    // Load topside-specific parameters from database
    dataSource.loadIntoCalculator(topsideCalculator, getCompanySpecificDesignStandards(),
        getDesignStandardCode(), serviceType);

    // Load velocity limits
    dataSource.loadVelocityLimits(topsideCalculator, getCompanySpecificDesignStandards(),
        serviceType);

    // Load vibration parameters
    dataSource.loadVibrationParameters(topsideCalculator, getCompanySpecificDesignStandards());

    // Set material properties
    topsideCalculator.setMaterialGrade(getMaterialGrade());
    topsideCalculator.setDesignCode(PipeMechanicalDesignCalculator.ASME_B31_3);
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    // Ensure calculator is initialized
    if (topsideCalculator == null) {
      initializeCalculator();
      initializeFromEquipment();
    }

    // Set geometry from equipment
    if (getProcessEquipment() instanceof PipeBeggsAndBrills) {
      PipeBeggsAndBrills pipe = (PipeBeggsAndBrills) getProcessEquipment();

      double innerDia = pipe.getDiameter();
      double wallThick = pipe.getWallThickness();
      if (wallThick <= 0) {
        wallThick = innerDia * 0.05; // Estimate if not set
      }

      topsideCalculator.setOuterDiameter(innerDia + 2 * wallThick);
      topsideCalculator.setNominalWallThickness(wallThick);
      topsideCalculator.setPipelineLength(pipe.getLength());
    }

    // Set design conditions
    topsideCalculator.setDesignPressure(getMaxOperationPressure() * 1.1 / 10.0); // bara to MPa
    topsideCalculator.setDesignTemperature(getMaxOperationTemperature() - 273.15); // K to C
    topsideCalculator.setInstallationTemperature(installationTemperature - 273.15);
    topsideCalculator.setOperatingTemperature(getMaxOperationTemperature() - 273.15);
    topsideCalculator.setServiceType(serviceType);

    // Perform calculations
    // 1. Wall thickness
    double minThickness = topsideCalculator.calculateMinimumWallThickness();
    setWallThickness(minThickness);

    // 2. Velocity analysis
    topsideCalculator.calculateActualVelocity();
    topsideCalculator.calculateErosionalVelocity();
    topsideCalculator.checkVelocityLimits();

    // 3. Support spacing
    calculatedSupportSpacing = topsideCalculator.calculateSupportSpacing();
    numberOfSupports =
        topsideCalculator.calculateNumberOfSupports(topsideCalculator.getPipelineLength());

    // 4. Stress analysis
    topsideCalculator.calculateAllowableStress();
    topsideCalculator.calculateSustainedStress(calculatedSupportSpacing);
    topsideCalculator.calculateThermalExpansionStress(100.0); // Assume 100m anchor spacing

    // 5. Vibration screening (if applicable)
    if (topsideCalculator.getActualVelocity() > 15.0) {
      topsideCalculator.calculateFIVScreening(calculatedSupportSpacing);
    }

    // Store results in parent calculator as well
    super.calcDesign();
  }

  /**
   * Get the topside-specific calculator.
   *
   * @return topside calculator
   */
  public TopsidePipingMechanicalDesignCalculator getTopsideCalculator() {
    if (topsideCalculator == null) {
      initializeCalculator();
    }
    return topsideCalculator;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    // Build comprehensive JSON output
    MechanicalDesignResponse response = new MechanicalDesignResponse(this);
    JsonObject jsonObj = JsonParser.parseString(response.toJson()).getAsJsonObject();

    // Add topside-specific fields
    jsonObj.addProperty("serviceType", serviceType);
    jsonObj.addProperty("pipeSchedule", pipeSchedule);
    jsonObj.addProperty("designStandardCode", getDesignStandardCode());
    jsonObj.addProperty("minOperationTemperature_K", minOperationTemperature);
    jsonObj.addProperty("installationTemperature_K", installationTemperature);

    // Add layout configuration
    JsonObject layoutObj = new JsonObject();
    layoutObj.addProperty("numberOfElbows90", numberOfElbows90);
    layoutObj.addProperty("numberOfTees", numberOfTees);
    layoutObj.addProperty("numberOfValves", numberOfValves);
    layoutObj.addProperty("numberOfExpansionLoops", numberOfExpansionLoops);
    layoutObj.addProperty("calculatedSupportSpacing_m", calculatedSupportSpacing);
    layoutObj.addProperty("numberOfSupports", numberOfSupports);
    jsonObj.add("layoutConfiguration", layoutObj);

    // Add calculator results
    if (topsideCalculator != null) {
      JsonObject calcObj = JsonParser.parseString(topsideCalculator.toJson()).getAsJsonObject();
      jsonObj.add("calculatorResults", calcObj);
    }

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(jsonObj);
  }

  // ============================================================================
  // GETTERS AND SETTERS
  // ============================================================================

  /**
   * Get service type.
   *
   * @return service type
   */
  public String getServiceType() {
    return serviceType;
  }

  /**
   * Set service type.
   *
   * @param serviceType service type
   */
  public void setServiceType(String serviceType) {
    this.serviceType = serviceType;
  }

  /**
   * Get pipe schedule.
   *
   * @return pipe schedule
   */
  public String getPipeSchedule() {
    return pipeSchedule;
  }

  /**
   * Set pipe schedule.
   *
   * @param pipeSchedule pipe schedule
   */
  public void setPipeSchedule(String pipeSchedule) {
    this.pipeSchedule = pipeSchedule;
  }

  /**
   * Get minimum operation temperature.
   *
   * @return minimum operation temperature in Kelvin
   */
  public double getMinOperationTemperature() {
    return minOperationTemperature;
  }

  /**
   * Set minimum operation temperature.
   *
   * @param minOperationTemperature minimum operation temperature in Kelvin
   */
  public void setMinOperationTemperature(double minOperationTemperature) {
    this.minOperationTemperature = minOperationTemperature;
  }

  /**
   * Get installation temperature.
   *
   * @return installation temperature in Kelvin
   */
  public double getInstallationTemperature() {
    return installationTemperature;
  }

  /**
   * Set installation temperature.
   *
   * @param installationTemperature installation temperature in Kelvin
   */
  public void setInstallationTemperature(double installationTemperature) {
    this.installationTemperature = installationTemperature;
  }

  /**
   * Get number of 90-degree elbows.
   *
   * @return number of elbows
   */
  public int getNumberOfElbows90() {
    return numberOfElbows90;
  }

  /**
   * Set number of 90-degree elbows.
   *
   * @param numberOfElbows90 number of elbows
   */
  public void setNumberOfElbows90(int numberOfElbows90) {
    this.numberOfElbows90 = numberOfElbows90;
  }

  /**
   * Get number of tees.
   *
   * @return number of tees
   */
  public int getNumberOfTees() {
    return numberOfTees;
  }

  /**
   * Set number of tees.
   *
   * @param numberOfTees number of tees
   */
  public void setNumberOfTees(int numberOfTees) {
    this.numberOfTees = numberOfTees;
  }

  /**
   * Get number of valves.
   *
   * @return number of valves
   */
  public int getNumberOfValves() {
    return numberOfValves;
  }

  /**
   * Set number of valves.
   *
   * @param numberOfValves number of valves
   */
  public void setNumberOfValves(int numberOfValves) {
    this.numberOfValves = numberOfValves;
  }

  /**
   * Get calculated support spacing.
   *
   * @return support spacing in meters
   */
  public double getCalculatedSupportSpacing() {
    return calculatedSupportSpacing;
  }

  /**
   * Get number of supports.
   *
   * @return number of supports
   */
  public int getNumberOfSupports() {
    return numberOfSupports;
  }
}
