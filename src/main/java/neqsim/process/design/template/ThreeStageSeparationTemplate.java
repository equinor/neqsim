package neqsim.process.design.template;

import neqsim.process.design.ProcessBasis;
import neqsim.process.design.ProcessTemplate;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * Template for creating a three-stage separation train.
 *
 * <p>
 * This template creates a standard three-stage oil/gas separation process consisting of:
 * </p>
 * <ul>
 * <li>High-pressure separator (HP)</li>
 * <li>Medium-pressure separator (MP)</li>
 * <li>Low-pressure separator (LP)</li>
 * </ul>
 *
 * <p>
 * Each stage has a pressure let-down valve before the separator, except for the first stage.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ThreeStageSeparationTemplate implements ProcessTemplate {

  /** Default HP separator pressure in bara. */
  private static final double DEFAULT_HP_PRESSURE = 80.0;

  /** Default MP separator pressure in bara. */
  private static final double DEFAULT_MP_PRESSURE = 20.0;

  /** Default LP separator pressure in bara. */
  private static final double DEFAULT_LP_PRESSURE = 2.0;

  /** Default inlet temperature in Kelvin. */
  private static final double DEFAULT_INLET_TEMP_K = 323.15;

  /**
   * Creates a new ThreeStageSeparationTemplate.
   */
  public ThreeStageSeparationTemplate() {
    // Default constructor
  }

  /** {@inheritDoc} */
  @Override
  public ProcessSystem create(ProcessBasis basis) {
    ProcessSystem process = new ProcessSystem();

    // Get pressures from basis or use defaults
    double hpPressure = getStagePresure(basis, 1, DEFAULT_HP_PRESSURE);
    double mpPressure = getStagePresure(basis, 2, DEFAULT_MP_PRESSURE);
    double lpPressure = getStagePresure(basis, 3, DEFAULT_LP_PRESSURE);

    // Get feed fluid
    SystemInterface feedFluid = basis.getFeedFluid();
    if (feedFluid == null) {
      throw new IllegalArgumentException("ProcessBasis must have a feed fluid defined");
    }

    // Get flow rate
    double flowRate = basis.getFeedFlowRate();
    String flowRateUnit = "kg/hr"; // Default unit

    // Set inlet pressure and temperature
    double inletPressure = basis.getFeedPressure();
    if (Double.isNaN(inletPressure) || inletPressure <= 0) {
      inletPressure = hpPressure + 5.0; // 5 bar above HP separator
    }

    double inletTemperature = basis.getFeedTemperature();
    if (Double.isNaN(inletTemperature) || inletTemperature <= 0) {
      inletTemperature = DEFAULT_INLET_TEMP_K;
    }

    // Create feed stream
    SystemInterface feed = feedFluid.clone();
    feed.setPressure(inletPressure, "bara");
    feed.setTemperature(inletTemperature, "K");
    Stream feedStream = new Stream("Feed", feed);
    feedStream.setFlowRate(flowRate, flowRateUnit);
    process.add(feedStream);

    // Create HP Separator
    Separator hpSeparator = new Separator("HP Separator", feedStream);
    process.add(hpSeparator);

    // Create HP to MP valve
    ThrottlingValve hpToMpValve =
        new ThrottlingValve("HP-MP Valve", hpSeparator.getLiquidOutStream());
    hpToMpValve.setOutletPressure(mpPressure, "bara");
    process.add(hpToMpValve);

    // Create MP Separator
    Separator mpSeparator = new Separator("MP Separator", hpToMpValve.getOutletStream());
    process.add(mpSeparator);

    // Create MP to LP valve
    ThrottlingValve mpToLpValve =
        new ThrottlingValve("MP-LP Valve", mpSeparator.getLiquidOutStream());
    mpToLpValve.setOutletPressure(lpPressure, "bara");
    process.add(mpToLpValve);

    // Create LP Separator
    Separator lpSeparator = new Separator("LP Separator", mpToLpValve.getOutletStream());
    process.add(lpSeparator);

    return process;
  }

  /**
   * Gets stage pressure from basis or returns default.
   *
   * @param basis the process basis
   * @param stageNumber the stage number (1=HP, 2=MP, 3=LP)
   * @param defaultPressure the default pressure if not specified
   * @return the stage pressure in bara
   */
  private double getStagePresure(ProcessBasis basis, int stageNumber, double defaultPressure) {
    double pressure = basis.getStagePressure(stageNumber);
    if (Double.isNaN(pressure) || pressure <= 0) {
      return defaultPressure;
    }
    return pressure;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isApplicable(SystemInterface fluid) {
    // This template is applicable for multiphase fluids (oil/gas/water)
    if (fluid == null) {
      return false;
    }

    // Run a flash to check phases present
    try {
      SystemInterface testFluid = fluid.clone();
      testFluid.setTemperature(DEFAULT_INLET_TEMP_K, "K");
      testFluid.setPressure(DEFAULT_HP_PRESSURE, "bara");
      neqsim.thermodynamicoperations.ThermodynamicOperations ops =
          new neqsim.thermodynamicoperations.ThermodynamicOperations(testFluid);
      ops.TPflash();

      // Check if we have both gas and liquid phases
      boolean hasGas = testFluid.hasPhaseType("gas");
      boolean hasLiquid = testFluid.hasPhaseType("oil") || testFluid.hasPhaseType("aqueous");

      return hasGas && hasLiquid;
    } catch (Exception e) {
      return false;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String[] getRequiredEquipmentTypes() {
    return new String[] {"Separator", "ThrottlingValve"};
  }

  /** {@inheritDoc} */
  @Override
  public String[] getExpectedOutputs() {
    return new String[] {"HP Gas - Gas stream from HP separator",
        "HP Liquid - Liquid stream from HP separator to MP valve",
        "MP Gas - Gas stream from MP separator",
        "MP Liquid - Liquid stream from MP separator to LP valve",
        "LP Gas - Gas stream from LP separator", "LP Liquid - Stabilized oil from LP separator"};
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "Three-Stage Separation";
  }

  /** {@inheritDoc} */
  @Override
  public String getDescription() {
    return "Standard three-stage oil/gas separation train with HP, MP, and LP separators. "
        + "Suitable for oil stabilization and flash gas recovery.";
  }
}
