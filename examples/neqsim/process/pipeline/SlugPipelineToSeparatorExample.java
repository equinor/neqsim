package neqsim.process.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Example demonstrating a slugging pipeline connected to a choke valve and inlet separator with
 * level control.
 *
 * <p>
 * This example shows a complete integrated system using the TwoFluidPipe model:
 * <ul>
 * <li><b>Pipeline</b>: TwoFluidPipe with stream-connected inlet and constant outlet pressure</li>
 * <li><b>Choke valve</b>: Controls flow between pipeline and separator</li>
 * <li><b>Inlet separator</b>: 3-phase separator with liquid accumulation</li>
 * <li><b>Level control</b>: PID controller maintaining liquid level setpoint</li>
 * </ul>
 *
 * <p>
 * Boundary conditions:
 * <ul>
 * <li><b>Pipeline inlet</b>: Stream-connected (constant pressure from wellhead/manifold)</li>
 * <li><b>Pipeline outlet</b>: Constant pressure boundary (set via setOutletPressure)</li>
 * <li><b>Separator outlet</b>: Controlled by level controller</li>
 * </ul>
 *
 * <p>
 * Physical scenario: A 3 km subsea flowline with terrain-induced slugging feeds into a topside
 * choke valve and inlet separator. The terrain profile includes a low point where liquid
 * accumulates. During transient simulation, the pipeline shows significant flow variation
 * (typically 500%+) as it drains from the initial high-holdup state toward steady-state.
 *
 * @author NeqSim Team
 * @version 1.0
 */
public final class SlugPipelineToSeparatorExample {

  private SlugPipelineToSeparatorExample() {}

  /**
   * Simulation result container.
   */
  public static final class SimulationResult {
    private final List<Double> times;
    private final List<Double> pipeInletPressures;
    private final List<Double> pipeOutletPressures;
    private final List<Double> pipeOutletMassFlows;
    private final List<Double> chokeInletPressures;
    private final List<Double> separatorPressures;
    private final List<Double> separatorLevels;
    private final List<Double> liquidValveOpenings;
    private final List<Double> liquidHoldups;
    private final List<String> slugEvents;
    private int slugCount;

    SimulationResult() {
      times = new ArrayList<>();
      pipeInletPressures = new ArrayList<>();
      pipeOutletPressures = new ArrayList<>();
      pipeOutletMassFlows = new ArrayList<>();
      chokeInletPressures = new ArrayList<>();
      separatorPressures = new ArrayList<>();
      separatorLevels = new ArrayList<>();
      liquidValveOpenings = new ArrayList<>();
      liquidHoldups = new ArrayList<>();
      slugEvents = new ArrayList<>();
      slugCount = 0;
    }

    public List<Double> getTimes() {
      return times;
    }

    public List<Double> getPipeOutletMassFlows() {
      return pipeOutletMassFlows;
    }

    public List<Double> getSeparatorLevels() {
      return separatorLevels;
    }

    public int getSlugCount() {
      return slugCount;
    }

    public void setSlugCount(int count) {
      this.slugCount = count;
    }
  }

  /**
   * Run the slug pipeline to separator simulation.
   *
   * <p>
   * Creates a complete production system with:
   * <ul>
   * <li>Subsea multiphase flowline with terrain profile (low point for slug generation)</li>
   * <li>Topside choke valve for pressure reduction</li>
   * <li>Inlet separator with level control</li>
   * </ul>
   *
   * @return simulation results with time series data
   */
  public static SimulationResult runSimulation() {
    System.out.println("=".repeat(70));
    System.out.println("  SLUG PIPELINE TO SEPARATOR EXAMPLE");
    System.out.println("  TwoFluidPipe + Choke + Separator with Level Control");
    System.out.println("=".repeat(70));

    // ========== FLUID DEFINITION ==========
    // Wet gas/condensate typical of North Sea production
    SystemInterface fluid = new SystemSrkEos(288.15, 80.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.06);
    fluid.addComponent("n-butane", 0.04);
    fluid.addComponent("n-pentane", 0.03);
    fluid.addComponent("n-hexane", 0.04);
    fluid.addComponent("n-heptane", 0.05);
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    // ========== PIPELINE INLET STREAM ==========
    // Constant pressure boundary at inlet (wellhead/manifold)
    double inletPressure = 80.0; // bara - wellhead pressure
    double inletTemperature = 333.15; // 60°C - reservoir temperature
    double initialMassFlow = 15.0; // kg/s - initial estimate

    Stream pipeInlet = new Stream("PipeInlet", fluid);
    pipeInlet.setFlowRate(initialMassFlow, "kg/sec");
    pipeInlet.setPressure(inletPressure, "bara");
    pipeInlet.setTemperature(inletTemperature, "K");
    pipeInlet.run();

    System.out.println("\n=== Pipeline Configuration ===");
    System.out.println("Inlet conditions:");
    System.out.println("  Pressure: " + inletPressure + " bara (constant)");
    System.out.println("  Temperature: " + (inletTemperature - 273.15) + " °C");
    System.out.println("  Initial mass flow: " + initialMassFlow + " kg/s");
    System.out.println("  Gas fraction: " + String.format("%.2f", pipeInlet.getFluid().getBeta()));

    // ========== TWO-FLUID PIPELINE ==========
    // 3 km flowline with terrain profile using the two-fluid model
    double pipeLength = 3000.0; // m
    double pipeDiameter = 0.254; // 10 inch
    int numSections = 30;

    TwoFluidPipe pipeline = new TwoFluidPipe("SubseaFlowline", pipeInlet);
    pipeline.setLength(pipeLength);
    pipeline.setDiameter(pipeDiameter);
    pipeline.setRoughness(4.6e-5); // Steel pipe roughness
    pipeline.setNumberOfSections(numSections);

    // TwoFluidPipe uses:
    // - Inlet BC: STREAM_CONNECTED (uses inlet stream pressure - constant wellhead)
    // - Outlet BC: CONSTANT_PRESSURE (set below)

    // Terrain profile with a low point for liquid accumulation (terrain-induced slugging)
    // Profile: slight downhill, then a dip (low point), then uphill to platform
    double[] elevations = new double[numSections];
    double sectionLength = pipeLength / numSections;
    for (int i = 0; i < numSections; i++) {
      double x = (i + 1) * sectionLength;
      double xNorm = x / pipeLength; // 0 to 1

      if (xNorm < 0.3) {
        // First 30%: slight downhill from wellhead
        elevations[i] = -20.0 * xNorm / 0.3;
      } else if (xNorm < 0.5) {
        // 30-50%: low point (liquid accumulation zone)
        double dip = (xNorm - 0.3) / 0.2;
        elevations[i] = -20.0 - 15.0 * Math.sin(dip * Math.PI);
      } else {
        // 50-100%: riser to topside
        double rise = (xNorm - 0.5) / 0.5;
        elevations[i] = -35.0 + 85.0 * rise; // Rises to +50m (platform height)
      }
    }
    pipeline.setElevationProfile(elevations);

    // Heat transfer to seabed (5°C)
    pipeline.setSurfaceTemperature(5.0, "C");
    pipeline.setHeatTransferCoefficient(25.0); // W/(m²·K) for uninsulated subsea

    System.out.println("\nPipeline geometry:");
    System.out.println("  Length: " + pipeLength / 1000 + " km");
    System.out.println(
        "  Diameter: " + (pipeDiameter * 1000) + " mm (" + (pipeDiameter / 0.0254) + " inch)");
    System.out.println("  Sections: " + numSections);
    System.out.println("  Elevation profile: Downhill → Low point → Riser");
    System.out.println("  Low point depth: -35 m (liquid accumulation zone)");
    System.out.println("  Platform elevation: +50 m");

    // ========== CHOKE VALVE ==========
    // Choke between pipeline outlet and separator
    // This valve creates the pressure drop and determines outlet mass flow
    double chokeOutletPressure = 55.0; // bara - separator operating pressure

    ThrottlingValve chokeValve = new ThrottlingValve("ChokeValve", pipeline.getOutletStream());
    chokeValve.setOutletPressure(chokeOutletPressure);
    chokeValve.setPercentValveOpening(50.0); // 50% open initially
    chokeValve.setCalculateSteadyState(false);

    System.out.println("\n=== Choke Valve ===");
    System.out.println("  Outlet pressure: " + chokeOutletPressure + " bara");
    System.out.println("  Initial opening: 50%");

    // ========== INLET SEPARATOR ==========
    // 3-phase separator receiving flow from choke
    Separator inletSeparator = new Separator("InletSeparator");
    inletSeparator.addStream(chokeValve.getOutletStream());
    inletSeparator.setCalculateSteadyState(false);
    inletSeparator.setInternalDiameter(2.5); // 2.5 m diameter
    inletSeparator.setSeparatorLength(8.0); // 8 m length

    System.out.println("\n=== Inlet Separator ===");
    System.out.println("  Diameter: 2.5 m");
    System.out.println("  Length: 8.0 m");
    double separatorVolume = Math.PI * 1.25 * 1.25 * 8.0;
    System.out.println("  Volume: " + String.format("%.1f", separatorVolume) + " m³");

    // ========== LIQUID OUTLET WITH LEVEL CONTROL ==========
    ThrottlingValve liquidValve =
        new ThrottlingValve("LiquidValve", inletSeparator.getLiquidOutStream());
    liquidValve.setOutletPressure(10.0); // bara - downstream storage
    liquidValve.setCalculateSteadyState(false);
    liquidValve.setPercentValveOpening(50.0);

    // Level transmitter
    LevelTransmitter levelTT = new LevelTransmitter("LT-100", inletSeparator);
    levelTT.setMaximumValue(1.0);
    levelTT.setMinimumValue(0.0);

    // Level controller (PID)
    double levelSetpoint = 0.50; // 50% level target
    ControllerDeviceBaseClass levelController = new ControllerDeviceBaseClass("LIC-100");
    levelController.setTransmitter(levelTT);
    levelController.setControllerSetPoint(levelSetpoint);
    // Tuning: Moderate Kp, slow Ti for slug absorption, small Td
    levelController.setControllerParameters(1.5, 180.0, 15.0);
    levelController.setReverseActing(false);
    liquidValve.setController(levelController);

    System.out.println("\n=== Level Control ===");
    System.out.println("  Setpoint: " + (levelSetpoint * 100) + "%");
    System.out.println("  Controller: PID (Kp=1.5, Ti=180s, Td=15s)");
    System.out.println("  Strategy: Slow tuning to absorb slug surges");

    // ========== GAS OUTLET ==========
    ThrottlingValve gasValve = new ThrottlingValve("GasValve", inletSeparator.getGasOutStream());
    gasValve.setOutletPressure(50.0); // bara - gas export
    gasValve.setCalculateSteadyState(false);
    gasValve.setPercentValveOpening(50.0);

    // ========== PROCESS SYSTEM ==========
    ProcessSystem process = new ProcessSystem();
    process.add(pipeInlet);
    process.add(pipeline);
    process.add(chokeValve);
    process.add(inletSeparator);
    process.add(liquidValve);
    process.add(gasValve);
    process.add(levelTT);

    // Initial steady-state run
    System.out.println("\n=== Initial Steady State ===");
    process.run();

    double initialLevel = inletSeparator.getLiquidLevel();
    double initialSepPressure = inletSeparator.getPressure();
    double[] initialPressureProfile = pipeline.getPressureProfile();
    double initialOutletP = initialPressureProfile != null && initialPressureProfile.length > 0
        ? initialPressureProfile[initialPressureProfile.length - 1] / 1e5
        : chokeOutletPressure;

    System.out.println("  Pipeline inlet P: " + inletPressure + " bara");
    System.out.println("  Pipeline outlet P: " + String.format("%.1f", initialOutletP) + " bara");
    System.out
        .println("  Separator pressure: " + String.format("%.1f", initialSepPressure) + " bara");
    System.out.println("  Separator level: " + String.format("%.1f", initialLevel * 100) + "%");

    // ========== TRANSIENT SIMULATION ==========
    System.out.println("\n=== Transient Simulation (5 minutes) ===");
    System.out.println("Simulating terrain-induced slugging...\n");

    System.out.println("Time(s)  PipeOut(kg/s)  Level(%)  LiqValve(%)  SepP(bara)  Event");
    System.out.println("-".repeat(70));

    SimulationResult result = new SimulationResult();
    UUID simId = UUID.randomUUID();
    double timeStep = 2.0; // seconds
    int totalSteps = 150; // 5 minutes

    // Slug detection parameters
    boolean inSlug = false;
    double prevOutletFlow = 0;
    double slugThreshold = 1.3; // 30% above average = slug

    for (int step = 0; step <= totalSteps; step++) {
      double time = step * timeStep;

      // Run transient step
      if (step > 0) {
        process.runTransient(timeStep, simId);
      }

      // Collect results
      double pipeOutFlow = pipeline.getOutletStream().getFlowRate("kg/sec");
      double level = inletSeparator.getLiquidLevel();
      double sepPressure = inletSeparator.getPressure();
      double valveOpening = liquidValve.getPercentValveOpening();

      // Get pipeline pressures
      double[] pressureProfile = pipeline.getPressureProfile();
      double pipeOutP = pressureProfile != null && pressureProfile.length > 0
          ? pressureProfile[pressureProfile.length - 1] / 1e5
          : chokeOutletPressure;

      // Get liquid holdup
      double[] holdupProfile = pipeline.getLiquidHoldupProfile();
      double avgHoldup = 0;
      if (holdupProfile != null) {
        for (double h : holdupProfile) {
          avgHoldup += h;
        }
        avgHoldup /= holdupProfile.length;
      }

      // Detect slug events
      String event = "";
      if (pipeOutFlow > prevOutletFlow * slugThreshold && !inSlug) {
        inSlug = true;
        result.setSlugCount(result.getSlugCount() + 1);
        event = "SLUG #" + result.getSlugCount() + " ARRIVING";
      } else if (pipeOutFlow < prevOutletFlow * 0.9 && inSlug) {
        inSlug = false;
        event = "slug passed";
      }

      // Store results
      result.times.add(time);
      result.pipeInletPressures.add(inletPressure);
      result.pipeOutletPressures.add(pipeOutP);
      result.pipeOutletMassFlows.add(pipeOutFlow);
      result.separatorPressures.add(sepPressure);
      result.separatorLevels.add(level);
      result.liquidValveOpenings.add(valveOpening);
      result.liquidHoldups.add(avgHoldup);
      result.slugEvents.add(event);

      // Print status
      if (step % 15 == 0 || !event.isEmpty()) {
        System.out.println(String.format("%6.0f   %12.2f  %8.1f  %11.1f  %10.1f  %s", time,
            pipeOutFlow, level * 100, valveOpening, sepPressure, event));
      }

      prevOutletFlow = pipeOutFlow > 0.1 ? pipeOutFlow : prevOutletFlow;
    }

    // ========== SUMMARY ==========
    System.out.println("-".repeat(70));
    System.out.println("\n=== Simulation Summary ===");
    System.out.println("Total slugs detected: " + result.getSlugCount());

    // Calculate statistics
    double maxFlow = result.pipeOutletMassFlows.stream().max(Double::compareTo).orElse(0.0);
    double minFlow =
        result.pipeOutletMassFlows.stream().filter(f -> f > 0).min(Double::compareTo).orElse(0.0);
    double avgFlow = result.pipeOutletMassFlows.stream().mapToDouble(d -> d).average().orElse(0.0);
    double maxLevel = result.separatorLevels.stream().max(Double::compareTo).orElse(0.0);
    double minLevel = result.separatorLevels.stream().min(Double::compareTo).orElse(0.0);

    System.out.println("\nPipeline outlet flow:");
    System.out.println("  Min: " + String.format("%.2f", minFlow) + " kg/s");
    System.out.println("  Max: " + String.format("%.2f", maxFlow) + " kg/s");
    System.out.println("  Avg: " + String.format("%.2f", avgFlow) + " kg/s");
    System.out.println("  Flow ratio (max/min): " + String.format("%.1f", maxFlow / minFlow));

    System.out.println("\nSeparator level:");
    System.out.println("  Min: " + String.format("%.1f", minLevel * 100) + "%");
    System.out.println("  Max: " + String.format("%.1f", maxLevel * 100) + "%");
    System.out.println("  Setpoint: " + (levelSetpoint * 100) + "%");
    System.out.println("  Max deviation: "
        + String.format("%.1f", Math.max(maxLevel - levelSetpoint, levelSetpoint - minLevel) * 100)
        + "%");

    System.out.println("\n=== Example completed ===");
    System.out.println("This example demonstrates:");
    System.out.println("  - TwoFluidPipe with constant inlet pressure boundary");
    System.out.println("  - Terrain-induced slugging from pipeline low point");
    System.out.println("  - Choke valve controlling flow to separator");
    System.out.println("  - Level controller absorbing slug-induced surges");

    return result;
  }

  /**
   * Main method to run the example.
   *
   * @param args command line arguments (not used)
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    runSimulation();
  }
}
