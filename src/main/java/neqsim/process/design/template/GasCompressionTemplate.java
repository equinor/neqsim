package neqsim.process.design.template;

import neqsim.process.design.ProcessBasis;
import neqsim.process.design.ProcessTemplate;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Template for creating multi-stage gas compression systems.
 *
 * <p>
 * This template creates a standard gas compression train consisting of multiple stages with
 * interstage cooling and liquid knockout. The number of stages is determined automatically based on
 * the overall pressure ratio or can be specified manually.
 * </p>
 *
 * <h2>Features</h2>
 * <ul>
 * <li>Automatic stage calculation based on optimal compression ratio per stage</li>
 * <li>Interstage coolers with configurable outlet temperature</li>
 * <li>Knockout drums for liquid removal between stages</li>
 * <li>Support for wet gas and condensate-rich applications</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>{@code
 * ProcessBasis basis = new ProcessBasis();
 * basis.setFeedFluid(gasFluid);
 * basis.setFeedPressure(5.0); // bara
 * basis.setParameter("dischargePressure", 100.0); // bara
 * basis.setParameter("interstageTemperature", 40.0); // °C
 * 
 * GasCompressionTemplate template = new GasCompressionTemplate();
 * ProcessSystem compression = template.create(basis);
 * compression.run();
 * }</pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class GasCompressionTemplate implements ProcessTemplate {

  /** Default interstage temperature in Celsius. */
  private static final double DEFAULT_INTERSTAGE_TEMP_C = 40.0;

  /** Default compressor polytropic efficiency. */
  private static final double DEFAULT_POLYTROPIC_EFFICIENCY = 0.75;

  /** Optimal compression ratio per stage (typically 2.5-4.0). */
  private static final double OPTIMAL_COMPRESSION_RATIO = 3.0;

  /** Maximum compression ratio per stage. */
  private static final double MAX_COMPRESSION_RATIO = 4.5;

  /** Default inlet temperature in Celsius. */
  private static final double DEFAULT_INLET_TEMP_C = 30.0;

  /**
   * Creates a new GasCompressionTemplate.
   */
  public GasCompressionTemplate() {
    // Default constructor
  }

  /** {@inheritDoc} */
  @Override
  public ProcessSystem create(ProcessBasis basis) {
    ProcessSystem process = new ProcessSystem();

    // Get parameters from basis
    SystemInterface feedFluid = basis.getFeedFluid();
    if (feedFluid == null) {
      throw new IllegalArgumentException("ProcessBasis must have a feed fluid defined");
    }

    double inletPressure = basis.getFeedPressure();
    if (Double.isNaN(inletPressure) || inletPressure <= 0) {
      inletPressure = 5.0; // Default 5 bara
    }

    double dischargePressure = basis.getParameter("dischargePressure", 100.0);
    double interstageTemp = basis.getParameter("interstageTemperature", DEFAULT_INTERSTAGE_TEMP_C);
    double polytropicEfficiency =
        basis.getParameter("polytropicEfficiency", DEFAULT_POLYTROPIC_EFFICIENCY);
    int numStages = (int) basis.getParameter("numberOfStages", 0);

    // Calculate number of stages if not specified
    if (numStages <= 0) {
      numStages = calculateOptimalStages(inletPressure, dischargePressure);
    }

    // Calculate pressure ratio per stage
    double overallRatio = dischargePressure / inletPressure;
    double stageRatio = Math.pow(overallRatio, 1.0 / numStages);

    // Get inlet temperature
    double inletTemperature = basis.getFeedTemperature();
    if (Double.isNaN(inletTemperature) || inletTemperature <= 0) {
      inletTemperature = DEFAULT_INLET_TEMP_C + 273.15;
    }

    // Get flow rate
    double flowRate = basis.getFeedFlowRate();
    String flowRateUnit = "kg/hr";

    // Create feed stream
    SystemInterface feed = feedFluid.clone();
    feed.setPressure(inletPressure, "bara");
    feed.setTemperature(inletTemperature, "K");
    Stream feedStream = new Stream("Compressor Feed", feed);
    if (flowRate > 0) {
      feedStream.setFlowRate(flowRate, flowRateUnit);
    }
    process.add(feedStream);

    // Build compression stages
    Stream currentStream = feedStream;
    double currentPressure = inletPressure;

    for (int stage = 1; stage <= numStages; stage++) {
      double stageDischargePressure;
      if (stage == numStages) {
        stageDischargePressure = dischargePressure; // Ensure exact final pressure
      } else {
        stageDischargePressure = currentPressure * stageRatio;
      }

      // Inlet separator (knockout drum)
      Separator knockoutDrum = new Separator("Stage " + stage + " KO Drum", currentStream);
      process.add(knockoutDrum);

      // Compressor
      Compressor compressor =
          new Compressor("Stage " + stage + " Compressor", knockoutDrum.getGasOutStream());
      compressor.setOutletPressure(stageDischargePressure);
      compressor.setPolytropicEfficiency(polytropicEfficiency);
      compressor.setUsePolytropicCalc(true);
      process.add(compressor);

      // Interstage cooler (except for last stage if no aftercooler needed)
      if (stage < numStages || basis.getParameter("includeAftercooler", 1.0) > 0) {
        Cooler cooler = new Cooler("Stage " + stage + " Cooler", compressor.getOutletStream());
        cooler.setOutTemperature(interstageTemp + 273.15); // Convert to K
        process.add(cooler);
        currentStream = (Stream) cooler.getOutletStream();
      } else {
        currentStream = (Stream) compressor.getOutletStream();
      }

      currentPressure = stageDischargePressure;
    }

    // Final discharge separator
    Separator dischargeSeparator = new Separator("Discharge KO Drum", currentStream);
    process.add(dischargeSeparator);

    return process;
  }

  /**
   * Calculates the optimal number of compression stages.
   *
   * @param inletPressure inlet pressure in bara
   * @param dischargePressure discharge pressure in bara
   * @return optimal number of stages
   */
  private int calculateOptimalStages(double inletPressure, double dischargePressure) {
    double overallRatio = dischargePressure / inletPressure;

    // Calculate stages needed for optimal compression ratio
    int stages = (int) Math.ceil(Math.log(overallRatio) / Math.log(OPTIMAL_COMPRESSION_RATIO));

    // Ensure at least 1 stage
    stages = Math.max(1, stages);

    // Check if single stage is feasible
    if (overallRatio <= MAX_COMPRESSION_RATIO) {
      return 1;
    }

    return stages;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isApplicable(SystemInterface fluid) {
    if (fluid == null) {
      return false;
    }

    try {
      SystemInterface testFluid = fluid.clone();
      testFluid.setTemperature(DEFAULT_INLET_TEMP_C + 273.15, "K");
      testFluid.setPressure(5.0, "bara");
      ThermodynamicOperations ops = new ThermodynamicOperations(testFluid);
      ops.TPflash();

      // Check if predominantly gas
      return testFluid.hasPhaseType("gas");
    } catch (Exception e) {
      return false;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String[] getRequiredEquipmentTypes() {
    return new String[] {"Compressor", "Cooler", "Separator"};
  }

  /** {@inheritDoc} */
  @Override
  public String[] getExpectedOutputs() {
    return new String[] {"Compressed Gas - High pressure gas from final stage",
        "Knockout Liquids - Condensate from each stage knockout drum",
        "Compression Power - Total shaft power required"};
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "Multi-Stage Gas Compression";
  }

  /** {@inheritDoc} */
  @Override
  public String getDescription() {
    return "Multi-stage gas compression train with interstage cooling and liquid knockout. "
        + "Automatically calculates optimal stage count based on pressure ratio. "
        + "Suitable for gas export, injection, and fuel gas systems.";
  }
}
