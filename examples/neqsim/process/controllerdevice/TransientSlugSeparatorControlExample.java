package neqsim.process.controllerdevice;

import java.util.ArrayList;
import java.util.List;
import neqsim.datapresentation.jfreechart.Graph2b;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Example demonstrating slug handling in an inlet separator with PID control.
 *
 * <p>
 * This example simulates how slugs arriving at a production separator cause pressure and level
 * disturbances, and how PID controllers respond to maintain stable operation. Slug arrivals are
 * modeled as periodic flow rate variations on the inlet stream, representing the cyclic nature of
 * terrain-induced slugging in multiphase pipelines.
 *
 * <p>
 * The simulation shows:
 * <ul>
 * <li>Level controller response to sudden liquid surges (slugs)</li>
 * <li>Pressure controller maintaining separator pressure during flow variations</li>
 * <li>How valve positions adjust to compensate for disturbances</li>
 * <li>The natural damping effect of separator volume on slug-induced transients</li>
 * </ul>
 *
 * <p>
 * Key learnings:
 * <ul>
 * <li>Larger separators provide more buffer capacity and smoother level changes</li>
 * <li>Controller tuning affects response speed vs. stability tradeoff</li>
 * <li>Level control typically uses slower tuning to avoid excessive valve movement</li>
 * <li>Pressure control typically uses faster tuning for tighter setpoint tracking</li>
 * </ul>
 */
public final class TransientSlugSeparatorControlExample {

  private TransientSlugSeparatorControlExample() {}

  /** Simple holder for the results of the simulation. */
  public static final class SimulationResult {
    private final String slugStatistics;
    private final double liquidLevel;
    private final double gasOutletPressure;
    private final List<Double> times;
    private final List<Double> liquidLevelHistory;
    private final List<Double> gasOutletPressureHistory;
    private final List<Double> separatorPressureHistory;
    private final List<Double> inletFlowRateHistory;
    private final List<Double> liquidValveOpeningHistory;
    private final List<Double> gasValveOpeningHistory;
    private final List<Double> levelSetpointHistory;
    private final List<Double> pressureSetpointHistory;
    // Slug characteristics
    private final List<Double> slugLiquidFractionHistory;
    private final List<Double> cumulativeSlugVolumeHistory;
    private final List<Double> separatorLiquidVolumeHistory;
    private final List<Double> slugVelocityHistory;
    // Pipe pressures
    private final List<Double> pipeInletPressureHistory;
    private final List<Double> pipeOutletPressureHistory;

    SimulationResult(String slugStatistics, double liquidLevel, double gasOutletPressure,
        List<Double> times, List<Double> liquidLevelHistory, List<Double> gasOutletPressureHistory,
        List<Double> separatorPressureHistory, List<Double> inletFlowRateHistory,
        List<Double> liquidValveOpeningHistory, List<Double> gasValveOpeningHistory,
        List<Double> levelSetpointHistory, List<Double> pressureSetpointHistory,
        List<Double> slugLiquidFractionHistory, List<Double> cumulativeSlugVolumeHistory,
        List<Double> separatorLiquidVolumeHistory, List<Double> slugVelocityHistory,
        List<Double> pipeInletPressureHistory, List<Double> pipeOutletPressureHistory) {
      this.slugStatistics = slugStatistics;
      this.liquidLevel = liquidLevel;
      this.gasOutletPressure = gasOutletPressure;
      this.times = new ArrayList<>(times);
      this.liquidLevelHistory = new ArrayList<>(liquidLevelHistory);
      this.gasOutletPressureHistory = new ArrayList<>(gasOutletPressureHistory);
      this.separatorPressureHistory = new ArrayList<>(separatorPressureHistory);
      this.inletFlowRateHistory = new ArrayList<>(inletFlowRateHistory);
      this.liquidValveOpeningHistory = new ArrayList<>(liquidValveOpeningHistory);
      this.gasValveOpeningHistory = new ArrayList<>(gasValveOpeningHistory);
      this.levelSetpointHistory = new ArrayList<>(levelSetpointHistory);
      this.pressureSetpointHistory = new ArrayList<>(pressureSetpointHistory);
      this.slugLiquidFractionHistory = new ArrayList<>(slugLiquidFractionHistory);
      this.cumulativeSlugVolumeHistory = new ArrayList<>(cumulativeSlugVolumeHistory);
      this.separatorLiquidVolumeHistory = new ArrayList<>(separatorLiquidVolumeHistory);
      this.slugVelocityHistory = new ArrayList<>(slugVelocityHistory);
      this.pipeInletPressureHistory = new ArrayList<>(pipeInletPressureHistory);
      this.pipeOutletPressureHistory = new ArrayList<>(pipeOutletPressureHistory);
    }

    public String getSlugStatistics() {
      return slugStatistics;
    }

    public double getLiquidLevel() {
      return liquidLevel;
    }

    public double getGasOutletPressure() {
      return gasOutletPressure;
    }

    public List<Double> getTimes() {
      return times;
    }

    public List<Double> getLiquidLevelHistory() {
      return liquidLevelHistory;
    }

    public List<Double> getGasOutletPressureHistory() {
      return gasOutletPressureHistory;
    }

    public List<Double> getSeparatorPressureHistory() {
      return separatorPressureHistory;
    }

    public List<Double> getInletFlowRateHistory() {
      return inletFlowRateHistory;
    }

    public List<Double> getLiquidValveOpeningHistory() {
      return liquidValveOpeningHistory;
    }

    public List<Double> getGasValveOpeningHistory() {
      return gasValveOpeningHistory;
    }

    public List<Double> getLevelSetpointHistory() {
      return levelSetpointHistory;
    }

    public List<Double> getPressureSetpointHistory() {
      return pressureSetpointHistory;
    }

    public List<Double> getSlugLiquidFractionHistory() {
      return slugLiquidFractionHistory;
    }

    public List<Double> getCumulativeSlugVolumeHistory() {
      return cumulativeSlugVolumeHistory;
    }

    public List<Double> getSeparatorLiquidVolumeHistory() {
      return separatorLiquidVolumeHistory;
    }

    public List<Double> getSlugVelocityHistory() {
      return slugVelocityHistory;
    }

    public List<Double> getPipeInletPressureHistory() {
      return pipeInletPressureHistory;
    }

    public List<Double> getPipeOutletPressureHistory() {
      return pipeOutletPressureHistory;
    }
  }

  /**
   * Calculate flow rate with slug-like variations.
   *
   * <p>
   * Models terrain-induced slugging where liquid periodically accumulates in pipeline low-points
   * and then sweeps through as a slug. This creates a characteristic pattern:
   * <ul>
   * <li>Base flow: steady-state gas-dominated flow</li>
   * <li>Slug arrival: rapid increase in total flow (liquid surge)</li>
   * <li>Slug tail: gradual return to base flow</li>
   * </ul>
   *
   * @param time current simulation time in seconds
   * @param baseFlow base flow rate in kg/s
   * @param slugPeriod time between slug arrivals in seconds
   * @param slugDuration duration of each slug event in seconds
   * @param slugAmplitude maximum flow rate increase during slug (fraction of base flow)
   * @return current flow rate in kg/s
   */
  private static double calculateSlugFlow(double time, double baseFlow, double slugPeriod,
      double slugDuration, double slugAmplitude) {
    // Calculate position within current slug cycle
    double cyclePosition = time % slugPeriod;

    if (cyclePosition < slugDuration) {
      // During slug arrival - rapid rise then gradual decay
      double slugProgress = cyclePosition / slugDuration;
      // Asymmetric pulse: fast rise (first 20%), slow decay (remaining 80%)
      double slugFactor;
      if (slugProgress < 0.2) {
        // Fast rise
        slugFactor = slugProgress / 0.2;
      } else {
        // Exponential decay
        slugFactor = Math.exp(-3.0 * (slugProgress - 0.2) / 0.8);
      }
      return baseFlow * (1.0 + slugAmplitude * slugFactor);
    } else {
      // Between slugs - steady base flow with small random variation
      return baseFlow * (1.0 + 0.02 * Math.sin(time * 0.5)); // Small 2% variation
    }
  }

  /**
   * Run the slug separator control simulation.
   *
   * <p>
   * Creates a production separator with level and pressure control, then subjects it to periodic
   * slug arrivals. The simulation tracks how the level and pressure vary, and how the control
   * valves respond to maintain setpoints.
   *
   * @return simulation results including time series data for plotting
   */
  public static SimulationResult runSimulation() {
    // Create a wet gas/condensate fluid typical of offshore production
    SystemInterface wetGas = new SystemSrkEos(288.15, 55.0);
    wetGas.addComponent("methane", 0.72);
    wetGas.addComponent("ethane", 0.06);
    wetGas.addComponent("propane", 0.05);
    wetGas.addComponent("n-butane", 0.04);
    wetGas.addComponent("n-pentane", 0.03);
    wetGas.addComponent("n-hexane", 0.05);
    wetGas.addComponent("n-heptane", 0.05);
    wetGas.createDatabase(true);
    wetGas.setMixingRule("classic");
    wetGas.setMultiPhaseCheck(true);

    // Base flow rate (kg/s) - this is the average steady-state flow
    double baseFlowRate = 20.0;

    // Slug parameters - tuned for realistic terrain-induced slugging
    double slugPeriod = 60.0; // Slug arrives every 60 seconds
    double slugDuration = 15.0; // Each slug lasts 15 seconds
    double slugAmplitude = 0.8; // Flow increases by up to 80% during slug

    // Create inlet stream at separator inlet pressure
    Stream separatorInlet = new Stream("separatorInlet", wetGas);
    separatorInlet.setFlowRate(baseFlowRate, "kg/sec");
    separatorInlet.setPressure(55.0, "bara");
    separatorInlet.run();

    // Configure production separator
    // Size chosen to provide ~3 minute liquid residence time at base flow
    Separator inletSeparator = new Separator("inletSeparator");
    inletSeparator.addStream(separatorInlet);
    inletSeparator.setCalculateSteadyState(false);
    inletSeparator.setInternalDiameter(2.2); // 2.2m diameter vessel
    inletSeparator.setSeparatorLength(7.0); // 7m length (L/D ~ 3.2)

    // Liquid level control valve - dumps to downstream processing
    ThrottlingValve liquidCv =
        new ThrottlingValve("liquidControlValve", inletSeparator.getLiquidOutStream());
    liquidCv.setOutletPressure(40.0);
    liquidCv.setCalculateSteadyState(false);
    liquidCv.setPercentValveOpening(50.0);

    // Gas pressure control valve - to export/compression
    ThrottlingValve gasCv =
        new ThrottlingValve("gasControlValve", inletSeparator.getGasOutStream());
    gasCv.setOutletPressure(45.0);
    gasCv.setCalculateSteadyState(false);
    gasCv.setPercentValveOpening(50.0);

    // Level transmitter for separator liquid level (0-100% range)
    LevelTransmitter levelTt = new LevelTransmitter("LT-100", inletSeparator);
    levelTt.setMaximumValue(1.0);
    levelTt.setMinimumValue(0.0);

    // Level controller (PID) - tuned for slug absorption
    // Using slower tuning to allow the separator to absorb slugs naturally
    // rather than fighting every disturbance with aggressive valve movement
    double levelSetpoint = 0.50; // Target 50% level
    ControllerDeviceBaseClass levelController = new ControllerDeviceBaseClass("LIC-100");
    levelController.setTransmitter(levelTt);
    levelController.setControllerSetPoint(levelSetpoint);
    // Kp=1.5, Ti=200s (slow integral for smooth response), Td=20s
    levelController.setControllerParameters(1.5, 200.0, 20.0);
    levelController.setReverseActing(false);
    liquidCv.setController(levelController);

    // Separator pressure transmitter (gas space)
    PressureTransmitter separatorPressureTt =
        new PressureTransmitter("PT-SEP", inletSeparator.getGasOutStream());
    separatorPressureTt.setUnit("bar");
    separatorPressureTt.setMaximumValue(80.0);
    separatorPressureTt.setMinimumValue(20.0);

    // Pressure controller - faster tuning for tighter pressure control
    double pressureSetpoint = 52.0;
    ControllerDeviceBaseClass pressureController = new ControllerDeviceBaseClass("PIC-100");
    pressureController.setTransmitter(separatorPressureTt);
    pressureController.setControllerSetPoint(pressureSetpoint);
    // Kp=1.2, Ti=80s, Td=10s - faster response than level controller
    pressureController.setControllerParameters(1.2, 80.0, 10.0);
    pressureController.setReverseActing(false);
    gasCv.setController(pressureController);

    // Build process system
    ProcessSystem process = new ProcessSystem();
    process.add(separatorInlet);
    process.add(inletSeparator);
    process.add(liquidCv);
    process.add(gasCv);
    process.add(levelTt);
    process.add(separatorPressureTt);

    process.run();

    // Simulation parameters - extended for longer observation
    double timeStep = 1.0; // 1 second time step for good resolution
    int numberOfSteps = 3600; // 60 minutes (1 hour) total simulation time
    process.setTimeStep(timeStep);

    // Pipe/flowline parameters for slug tracking (simulated upstream pipe)
    double pipeDiameter = 0.25; // 10-inch pipe
    double pipeLength = 3000.0; // 3 km flowline
    // Simulated pipe pressures - inlet pressure drops with higher flow (friction)
    double pipeInletPressureBase = 62.0; // bara at wellhead/manifold
    double pipeOutletPressureBase = 55.0; // bara at separator inlet
    double pipeArea = Math.PI * pipeDiameter * pipeDiameter / 4.0;
    double liquidDensity = 750.0; // kg/m3 (condensate/oil)
    double gasDensity = 50.0; // kg/m3 at operating conditions

    // Initialize history lists
    List<Double> times = new ArrayList<>(numberOfSteps + 1);
    List<Double> liquidLevels = new ArrayList<>(numberOfSteps + 1);
    List<Double> gasPressures = new ArrayList<>(numberOfSteps + 1);
    List<Double> separatorPressures = new ArrayList<>(numberOfSteps + 1);
    List<Double> inletFlowRates = new ArrayList<>(numberOfSteps + 1);
    List<Double> liquidValveOpenings = new ArrayList<>(numberOfSteps + 1);
    List<Double> gasValveOpenings = new ArrayList<>(numberOfSteps + 1);
    List<Double> levelSetpoints = new ArrayList<>(numberOfSteps + 1);
    List<Double> pressureSetpoints = new ArrayList<>(numberOfSteps + 1);
    // Slug characteristic tracking
    List<Double> slugLiquidFractions = new ArrayList<>(numberOfSteps + 1);
    List<Double> cumulativeSlugVolumes = new ArrayList<>(numberOfSteps + 1);
    List<Double> separatorLiquidVolumes = new ArrayList<>(numberOfSteps + 1);
    List<Double> slugVelocities = new ArrayList<>(numberOfSteps + 1);
    // Pipe pressure tracking
    List<Double> pipeInletPressures = new ArrayList<>(numberOfSteps + 1);
    List<Double> pipeOutletPressures = new ArrayList<>(numberOfSteps + 1);

    // Cumulative slug volume tracker
    double cumulativeSlugVolume = 0.0;

    // Record initial state
    times.add(0.0);
    liquidLevels.add(inletSeparator.getLiquidLevel());
    gasPressures.add(gasCv.getOutletStream().getPressure());
    separatorPressures.add(inletSeparator.getGasOutStream().getPressure());
    inletFlowRates.add(separatorInlet.getFlowRate("kg/sec"));
    liquidValveOpenings.add(liquidCv.getPercentValveOpening());
    slugLiquidFractions.add(0.0);
    cumulativeSlugVolumes.add(0.0);
    double initialLiquidVolume = Math.PI * Math.pow(inletSeparator.getInternalDiameter() / 2.0, 2)
        * inletSeparator.getSeparatorLength() * inletSeparator.getLiquidLevel();
    separatorLiquidVolumes.add(initialLiquidVolume);
    slugVelocities.add(0.0);
    // Initial pipe pressures (at base flow)
    pipeInletPressures.add(pipeInletPressureBase);
    pipeOutletPressures.add(pipeOutletPressureBase);
    gasValveOpenings.add(gasCv.getPercentValveOpening());
    levelSetpoints.add(levelSetpoint);
    pressureSetpoints.add(pressureSetpoint);

    // Slug event tracking for statistics
    int slugCount = 0;
    double maxLevelDeviation = 0.0;
    double maxPressureDeviation = 0.0;

    // Run transient simulation with slug-like flow variations
    for (int i = 1; i <= numberOfSteps; i++) {
      double currentTime = i * timeStep;

      // Calculate and apply slug-modulated flow rate
      double currentFlowRate =
          calculateSlugFlow(currentTime, baseFlowRate, slugPeriod, slugDuration, slugAmplitude);
      separatorInlet.setFlowRate(currentFlowRate, "kg/sec");

      // Track slug events
      double previousFlowRate = inletFlowRates.get(inletFlowRates.size() - 1);
      if (currentFlowRate > baseFlowRate * 1.3 && previousFlowRate <= baseFlowRate * 1.3) {
        slugCount++;
      }

      // Run transient step
      process.runTransient();

      // Record time series data
      double currentLevel = inletSeparator.getLiquidLevel();
      double currentPressure = inletSeparator.getGasOutStream().getPressure();

      times.add(currentTime);
      liquidLevels.add(currentLevel);
      gasPressures.add(gasCv.getOutletStream().getPressure());
      separatorPressures.add(currentPressure);
      inletFlowRates.add(currentFlowRate);
      liquidValveOpenings.add(liquidCv.getPercentValveOpening());
      gasValveOpenings.add(gasCv.getPercentValveOpening());
      levelSetpoints.add(levelSetpoint);
      pressureSetpoints.add(pressureSetpoint);

      // Calculate and track slug characteristics
      // Slug liquid fraction based on flow rate increase (higher flow = more liquid)
      double slugLiquidFraction = 0.0;
      double slugVelocity = 0.0;
      if (currentFlowRate > baseFlowRate * 1.1) {
        // During slug - estimate liquid fraction from flow surge
        double flowSurge = (currentFlowRate - baseFlowRate) / baseFlowRate;
        slugLiquidFraction = Math.min(0.9, 0.3 + 0.6 * flowSurge / slugAmplitude);
        // Slug velocity increases with flow rate
        slugVelocity = currentFlowRate / (pipeArea
            * (slugLiquidFraction * liquidDensity + (1 - slugLiquidFraction) * gasDensity));
        // Accumulate liquid volume entering separator from slug
        double liquidMassRate = currentFlowRate * slugLiquidFraction;
        cumulativeSlugVolume += (liquidMassRate / liquidDensity) * timeStep;
      }
      slugLiquidFractions.add(slugLiquidFraction);
      cumulativeSlugVolumes.add(cumulativeSlugVolume);
      double liquidVolume = Math.PI * Math.pow(inletSeparator.getInternalDiameter() / 2.0, 2)
          * inletSeparator.getSeparatorLength() * inletSeparator.getLiquidLevel();
      separatorLiquidVolumes.add(liquidVolume);
      slugVelocities.add(slugVelocity);

      // Calculate simulated pipe pressures
      // Friction pressure drop increases with flow rate squared
      double flowRatio = currentFlowRate / baseFlowRate;
      double frictionFactor = flowRatio * flowRatio; // Proportional to velocity squared
      double baseDeltaP = pipeInletPressureBase - pipeOutletPressureBase; // 7 bar at base flow
      double currentDeltaP = baseDeltaP * frictionFactor;
      // Pipe outlet pressure is separator inlet pressure
      double pipeOutletPressure = inletSeparator.getGasOutStream().getPressure();
      // Pipe inlet pressure = outlet + friction drop
      double pipeInletPressure = pipeOutletPressure + currentDeltaP;
      pipeInletPressures.add(pipeInletPressure);
      pipeOutletPressures.add(pipeOutletPressure);

      // Track maximum deviations
      maxLevelDeviation = Math.max(maxLevelDeviation, Math.abs(currentLevel - levelSetpoint));
      maxPressureDeviation =
          Math.max(maxPressureDeviation, Math.abs(currentPressure - pressureSetpoint));
    }

    // Generate statistics string
    String statistics = String.format(
        "Slug Events: %d%n" + "Slug Period: %.0f seconds%n" + "Slug Duration: %.0f seconds%n"
            + "Slug Amplitude: %.0f%% of base flow%n" + "Max Level Deviation: %.3f (%.1f%%)%n"
            + "Max Pressure Deviation: %.2f bar%n" + "Final Level: %.3f (setpoint: %.2f)%n"
            + "Final Pressure: %.2f bar (setpoint: %.2f bar)",
        slugCount, slugPeriod, slugDuration, slugAmplitude * 100, maxLevelDeviation,
        maxLevelDeviation * 100, maxPressureDeviation, inletSeparator.getLiquidLevel(),
        levelSetpoint, inletSeparator.getGasOutStream().getPressure(), pressureSetpoint);

    return new SimulationResult(statistics, inletSeparator.getLiquidLevel(),
        inletSeparator.getGasOutStream().getPressure(), times, liquidLevels, gasPressures,
        separatorPressures, inletFlowRates, liquidValveOpenings, gasValveOpenings, levelSetpoints,
        pressureSetpoints, slugLiquidFractions, cumulativeSlugVolumes, separatorLiquidVolumes,
        slugVelocities, pipeInletPressures, pipeOutletPressures);
  }

  /**
   * Run the example and display plots showing separator behavior during slug arrivals.
   *
   * <p>
   * The plots demonstrate:
   * <ul>
   * <li>How liquid level varies as slugs arrive and the level controller responds</li>
   * <li>How separator pressure fluctuates and is controlled</li>
   * <li>How control valves adjust their opening to maintain setpoints</li>
   * <li>Inlet flow rate variations showing the slug pattern</li>
   * </ul>
   *
   * @param args program args: --series for CSV output, --noplot to skip graphical display
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    System.out.println("=== Transient Slug Separator Control Example ===");
    System.out.println("Simulating slug arrivals at a production separator...\n");

    SimulationResult results = runSimulation();

    boolean printSeries = false;
    boolean showPlots = true;

    for (String arg : args) {
      if ("--series".equals(arg)) {
        printSeries = true;
      }
      if ("--noplot".equals(arg)) {
        showPlots = false;
      }
    }

    // Print slug statistics
    System.out.println("--- Simulation Results ---");
    System.out.println(results.getSlugStatistics());

    if (printSeries) {
      System.out.println("\n--- Time Series Data ---");
      System.out.println("time_s,liquid_level,separator_pressure_bar,gas_outlet_pressure_bar,"
          + "inlet_flow_kg_s,liquid_valve_pct,gas_valve_pct");
      for (int i = 0; i < results.getTimes().size(); i++) {
        System.out.printf("%6.1f,%.6f,%.4f,%.4f,%.4f,%.2f,%.2f%n", results.getTimes().get(i),
            results.getLiquidLevelHistory().get(i), results.getSeparatorPressureHistory().get(i),
            results.getGasOutletPressureHistory().get(i), results.getInletFlowRateHistory().get(i),
            results.getLiquidValveOpeningHistory().get(i),
            results.getGasValveOpeningHistory().get(i));
      }
    }

    // Display plots if enabled
    if (showPlots) {
      displayPlots(results);
    }
  }

  /**
   * Display graphical plots of the simulation results.
   *
   * @param results the simulation results to plot
   */
  @ExcludeFromJacocoGeneratedReport
  private static void displayPlots(SimulationResult results) {
    int n = results.getTimes().size();

    // Only plot last 600 seconds (10 minutes) where system has stabilized around setpoints
    int startIndex = Math.max(0, n - 600);
    int plotLength = n - startIndex;

    // Convert lists to arrays for plotting (only the stabilized portion)
    double[] timeArray =
        results.getTimes().stream().skip(startIndex).mapToDouble(Double::doubleValue).toArray();
    double[] levelArray = results.getLiquidLevelHistory().stream().skip(startIndex)
        .mapToDouble(Double::doubleValue).toArray();
    double[] levelSpArray = results.getLevelSetpointHistory().stream().skip(startIndex)
        .mapToDouble(Double::doubleValue).toArray();
    double[] sepPressureArray = results.getSeparatorPressureHistory().stream().skip(startIndex)
        .mapToDouble(Double::doubleValue).toArray();
    double[] pressureSpArray = results.getPressureSetpointHistory().stream().skip(startIndex)
        .mapToDouble(Double::doubleValue).toArray();
    double[] inletFlowArray = results.getInletFlowRateHistory().stream().skip(startIndex)
        .mapToDouble(Double::doubleValue).toArray();
    double[] liquidValveArray = results.getLiquidValveOpeningHistory().stream().skip(startIndex)
        .mapToDouble(Double::doubleValue).toArray();
    double[] gasValveArray = results.getGasValveOpeningHistory().stream().skip(startIndex)
        .mapToDouble(Double::doubleValue).toArray();

    // Plot 1: Inlet Flow Rate showing slug pattern
    double[][] flowData = new double[2][plotLength];
    System.arraycopy(timeArray, 0, flowData[0], 0, plotLength);
    System.arraycopy(inletFlowArray, 0, flowData[1], 0, plotLength);

    Graph2b flowGraph = new Graph2b(flowData, new String[] {"Inlet Flow Rate"},
        "Slug Pattern - Inlet Flow Rate (Stabilized)", "Time (s)", "Flow Rate (kg/s)");
    flowGraph.setVisible(true);

    // Plot 2: Separator Liquid Level vs Time
    double[][] levelData = new double[4][plotLength];
    System.arraycopy(timeArray, 0, levelData[0], 0, plotLength);
    System.arraycopy(levelArray, 0, levelData[1], 0, plotLength);
    System.arraycopy(timeArray, 0, levelData[2], 0, plotLength);
    System.arraycopy(levelSpArray, 0, levelData[3], 0, plotLength);

    Graph2b levelGraph = new Graph2b(levelData, new String[] {"Liquid Level", "Setpoint"},
        "Separator Level at Setpoint (Stabilized)", "Time (s)", "Liquid Level (fraction)");
    levelGraph.setVisible(true);

    // Plot 3: Separator Pressure vs Time
    double[][] pressureData = new double[4][plotLength];
    System.arraycopy(timeArray, 0, pressureData[0], 0, plotLength);
    System.arraycopy(sepPressureArray, 0, pressureData[1], 0, plotLength);
    System.arraycopy(timeArray, 0, pressureData[2], 0, plotLength);
    System.arraycopy(pressureSpArray, 0, pressureData[3], 0, plotLength);

    Graph2b pressureGraph =
        new Graph2b(pressureData, new String[] {"Separator Pressure", "Setpoint"},
            "Separator Pressure at Setpoint (Stabilized)", "Time (s)", "Pressure (bar)");
    pressureGraph.setVisible(true);

    // Plot 4: Control Valve Positions vs Time
    double[][] valveData = new double[4][plotLength];
    System.arraycopy(timeArray, 0, valveData[0], 0, plotLength);
    System.arraycopy(liquidValveArray, 0, valveData[1], 0, plotLength);
    System.arraycopy(timeArray, 0, valveData[2], 0, plotLength);
    System.arraycopy(gasValveArray, 0, valveData[3], 0, plotLength);

    Graph2b valveGraph = new Graph2b(valveData,
        new String[] {"Liquid CV (Level Control)", "Gas CV (Pressure Control)"},
        "Control Valve Response (Stabilized)", "Time (s)", "Valve Opening (%)");
    valveGraph.setVisible(true);

    // Plot 5: Slug Characteristics - Liquid Fraction and Velocity
    double[] slugLiqFracArray = results.getSlugLiquidFractionHistory().stream().skip(startIndex)
        .mapToDouble(Double::doubleValue).toArray();
    double[] slugVelArray = results.getSlugVelocityHistory().stream().skip(startIndex)
        .mapToDouble(Double::doubleValue).toArray();

    double[][] slugCharData = new double[4][plotLength];
    System.arraycopy(timeArray, 0, slugCharData[0], 0, plotLength);
    System.arraycopy(slugLiqFracArray, 0, slugCharData[1], 0, plotLength);
    System.arraycopy(timeArray, 0, slugCharData[2], 0, plotLength);
    // Scale velocity to fit on same chart (divide by 10)
    for (int i = 0; i < plotLength; i++) {
      slugCharData[3][i] = slugVelArray[i] / 10.0;
    }

    Graph2b slugCharGraph =
        new Graph2b(slugCharData, new String[] {"Liquid Fraction", "Slug Velocity / 10 (m/s)"},
            "Slug Characteristics (Stabilized)", "Time (s)", "Liquid Fraction / Velocity");
    slugCharGraph.setVisible(true);

    // Plot 6: Liquid Volumes - Separator and Cumulative Slug
    double[] sepLiqVolArray = results.getSeparatorLiquidVolumeHistory().stream().skip(startIndex)
        .mapToDouble(Double::doubleValue).toArray();
    double[] cumSlugVolArray = results.getCumulativeSlugVolumeHistory().stream().skip(startIndex)
        .mapToDouble(Double::doubleValue).toArray();

    double[][] volumeData = new double[4][plotLength];
    System.arraycopy(timeArray, 0, volumeData[0], 0, plotLength);
    System.arraycopy(sepLiqVolArray, 0, volumeData[1], 0, plotLength);
    System.arraycopy(timeArray, 0, volumeData[2], 0, plotLength);
    System.arraycopy(cumSlugVolArray, 0, volumeData[3], 0, plotLength);

    Graph2b volumeGraph =
        new Graph2b(volumeData, new String[] {"Separator Liquid Volume", "Cumulative Slug Volume"},
            "Liquid Volume (Stabilized)", "Time (s)", "Volume (m³)");
    volumeGraph.setVisible(true);

    // Plot 7: Pipe Inlet and Outlet Pressures
    double[] pipeInletPressArray = results.getPipeInletPressureHistory().stream().skip(startIndex)
        .mapToDouble(Double::doubleValue).toArray();
    double[] pipeOutletPressArray = results.getPipeOutletPressureHistory().stream().skip(startIndex)
        .mapToDouble(Double::doubleValue).toArray();

    double[][] pipePressureData = new double[4][plotLength];
    System.arraycopy(timeArray, 0, pipePressureData[0], 0, plotLength);
    System.arraycopy(pipeInletPressArray, 0, pipePressureData[1], 0, plotLength);
    System.arraycopy(timeArray, 0, pipePressureData[2], 0, plotLength);
    System.arraycopy(pipeOutletPressArray, 0, pipePressureData[3], 0, plotLength);

    Graph2b pipePressureGraph =
        new Graph2b(pipePressureData, new String[] {"Pipe Inlet Pressure", "Pipe Outlet Pressure"},
            "Pipe Pressure Profile (Stabilized)", "Time (s)", "Pressure (bar)");
    pipePressureGraph.setVisible(true);

    System.out
        .println("\nPlots displayed (last 10 minutes - stabilized). Close plot windows to exit.");
  }
}
