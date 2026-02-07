package neqsim.process.util.example;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.valve.ControlValve;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.valve.HIPPSValve;
import neqsim.process.equipment.valve.SafetyValve;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.flare.Flare;
import neqsim.process.measurementdevice.PushButton;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.measurementdevice.TemperatureTransmitter;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.logic.startup.StartupLogic;
import neqsim.process.logic.ProcessLogic;
import neqsim.process.logic.LogicState;

import neqsim.process.logic.action.SetSplitterAction;
import neqsim.process.logic.action.CloseValveAction;
import neqsim.process.logic.action.OpenValveAction;
import neqsim.process.logic.action.SetValveOpeningAction;
import neqsim.process.logic.action.SetSeparatorModeAction;
import neqsim.process.logic.action.EnergizeESDValveAction;
import neqsim.process.logic.condition.PressureCondition;
import neqsim.process.logic.condition.TemperatureCondition;
import neqsim.process.logic.condition.TimerCondition;
import neqsim.process.logic.condition.ValvePositionCondition;
import neqsim.process.safety.ProcessSafetyScenario;
import neqsim.process.util.scenario.ProcessScenarioRunner;
import neqsim.process.util.scenario.ScenarioTestRunner;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Enhanced integrated safety system example using the complete NeqSim process logic framework.
 * 
 * <p>
 * This example demonstrates:
 * <ul>
 * <li>ESD Logic with custom actions and manual triggers</li>
 * <li>Startup Logic with permissive conditions</li>
 * <li>ProcessScenarioRunner for automated scenario testing</li>
 * <li>ProcessSafetyScenario for systematic perturbation testing</li>
 * <li>Integration of safety instrumented functions (SIF)</li>
 * </ul>
 * 
 * <p>
 * Safety architecture:
 * <ul>
 * <li>SIL-2: ESD system with manual and automatic triggers</li>
 * <li>SIL-1: Process alarms and operator intervention</li>
 * <li>PSV: Final mechanical protection layer</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class ProcessLogicIntegratedExample {

  // Safety setpoints
  private static final double NORMAL_OPERATING_PRESSURE = 50.0; // bara
  private static final double HIGH_PRESSURE_ALARM = 55.0; // bara (SIL-1)
  private static final double ESD_ACTIVATION_PRESSURE = 58.0; // bara (SIL-2)
  private static final double PSV_SET_PRESSURE = 65.0; // bara (mechanical protection)

  /**
   * Main method demonstrating process logic integration.
   *
   * @param args command-line arguments (not used)
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    System.out.println("╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║    PROCESS LOGIC INTEGRATED SAFETY SYSTEM EXAMPLE              ║");
    System.out.println("║    Using Enhanced NeqSim Framework                             ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    // Build process system
    ProcessSystem processSystem = buildProcessSystem();

    // Setup instrumentation
    InstrumentationSetup instruments = setupInstrumentation(processSystem);

    // Setup process logic
    ProcessLogicSetup logicSetup = setupProcessLogic(processSystem, instruments);

    // Create scenario runner and initialize steady-state
    ProcessScenarioRunner runner = new ProcessScenarioRunner(processSystem);
    runner.initializeSteadyState(); // This calls processSystem.run() and shows initial conditions

    runner.addLogic(logicSetup.hippsLogic);
    runner.addLogic(logicSetup.esdLogic);
    runner.addLogic(logicSetup.startupLogic);

    // Run scenarios
    runTestScenarios(runner);

    System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║    ALL SCENARIOS COMPLETED SUCCESSFULLY                        ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝");
  }

  /**
   * Builds the complete process system.
   *
   * @return the configured process system
   */
  private static ProcessSystem buildProcessSystem() {
    ProcessSystem system = new ProcessSystem();

    // Create feed gas
    SystemInterface feedGas = new SystemSrkEos(298.15, 55.0);
    feedGas.addComponent("nitrogen", 1.0);
    feedGas.addComponent("methane", 85.0);
    feedGas.addComponent("ethane", 10.0);
    feedGas.addComponent("propane", 3.0);
    feedGas.addComponent("n-butane", 1.0);
    feedGas.setMixingRule(2);

    Stream highPressureFeed = new Stream("HP Feed", feedGas);
    highPressureFeed.setFlowRate(15000.0, "kg/hr");
    highPressureFeed.setPressure(55.0, "bara");
    highPressureFeed.setTemperature(25.0, "C");

    // Inlet Control Valve (Flow Control)
    ControlValve inletValve = new ControlValve("Inlet Control Valve", highPressureFeed);
    inletValve.setPercentValveOpening(80.0);
    inletValve.setCv(600.0);

    // HIPPS Valve (High Integrity Pressure Protection System) - downstream of inlet control
    HIPPSValve hippsValve = new HIPPSValve("HIPPS-001", inletValve.getOutletStream());
    hippsValve.setPercentValveOpening(100.0); // Normally open
    hippsValve.setCv(400.0);
    hippsValve.setClosureTime(3.0); // Fast closure for HIPPS
    hippsValve.setSILRating(2); // SIL-2 rated system

    // ESD Inlet Isolation Valve (Emergency Shutdown) - downstream of HIPPS
    ESDValve esdInletValve = new ESDValve("ESD Inlet Isolation", hippsValve.getOutletStream());
    esdInletValve.setPercentValveOpening(100.0); // Normally open
    esdInletValve.setCv(350.0);
    esdInletValve.setStrokeTime(4.0); // 4 seconds to close
    esdInletValve.energize(); // Start energized (open) for normal operation

    // High-pressure separator
    Separator separator = new Separator("HP Separator", esdInletValve.getOutletStream());
    separator.setCalculateSteadyState(true);
    separator.setInternalDiameter(2.0);

    // Gas routing splitter (process/PSV/ESD) - 3 outputs
    Splitter gasSplitter = new Splitter("Gas Splitter", separator.getGasOutStream(), 3);
    gasSplitter.setSplitFactors(new double[] {1.0, 0.0, 0.0}); // Normal: all to process

    // Process outlet stream (after splitter)
    Stream processStream = new Stream("Process Stream", gasSplitter.getSplitStream(0));

    // Pressure Safety Valve (connected to splitter stream 1)
    SafetyValve psv = new SafetyValve("PSV-001", gasSplitter.getSplitStream(1));
    psv.setPressureSpec(PSV_SET_PRESSURE);
    psv.setFullOpenPressure(PSV_SET_PRESSURE + 2.0);
    psv.setBlowdown(7.0);

    // ESD Blowdown valve (Emergency Shutdown) - connected to splitter stream 2
    ESDValve bdValve = new ESDValve("ESD Blowdown Valve", gasSplitter.getSplitStream(2));
    bdValve.setStrokeTime(5.0); // 5 seconds to close when de-energized
    bdValve.setCv(250.0);
    bdValve.setFailSafePosition(100.0); // Fail-safe to open position for blowdown
    bdValve.deEnergize(); // Start de-energized (closed) for safety

    // Flare header (combines blowdown and PSV)
    Mixer flareHeader = new Mixer("Flare Header");
    flareHeader.addStream(bdValve.getOutletStream());
    flareHeader.addStream(psv.getOutletStream());

    // Emergency flare
    Flare flare = new Flare("Emergency Flare", flareHeader.getOutletStream());
    flare.setFlameHeight(50.0);
    flare.setRadiantFraction(0.25);

    // Add all equipment to system
    system.add(highPressureFeed);
    system.add(inletValve);
    system.add(hippsValve);
    system.add(esdInletValve);
    system.add(separator);
    system.add(gasSplitter);
    system.add(processStream);
    system.add(psv);
    system.add(bdValve);
    system.add(flareHeader);
    system.add(flare);

    System.out
        .println("Process system built with " + system.getUnitOperations().size() + " units:");
    for (int i = 0; i < system.getUnitOperations().size(); i++) {
      System.out.println("  " + (i + 1) + ". " + system.getUnitOperations().get(i).getName());
    }
    System.out.println();

    return system;
  }

  /**
   * Sets up instrumentation for the process.
   *
   * @param system the process system to set up instrumentation for
   * @return the configured instrumentation setup
   */
  private static InstrumentationSetup setupInstrumentation(ProcessSystem system) {
    InstrumentationSetup setup = new InstrumentationSetup();

    // Get equipment references
    ControlValve inletValve = (ControlValve) system.getUnit("Inlet Control Valve");
    HIPPSValve hippsValve = (HIPPSValve) system.getUnit("HIPPS-001");
    Separator separator = (Separator) system.getUnit("HP Separator");

    // Pressure and temperature monitoring on separator
    setup.separatorPT = new PressureTransmitter("PT-101", separator.getGasOutStream());
    setup.separatorTT = new TemperatureTransmitter("TT-101", separator.getGasOutStream());

    // Configure HIPPS pressure transmitters for SIL-2 (2oo3 voting)
    // Monitor pressure at inlet control valve outlet (upstream of HIPPS)
    PressureTransmitter hippsPT1 =
        new PressureTransmitter("PT-HIPPS-1", inletValve.getOutletStream());
    PressureTransmitter hippsPT2 =
        new PressureTransmitter("PT-HIPPS-2", inletValve.getOutletStream());
    PressureTransmitter hippsPT3 =
        new PressureTransmitter("PT-HIPPS-3", inletValve.getOutletStream());

    // Add pressure transmitters to HIPPS valve
    hippsValve.addPressureTransmitter(hippsPT1);
    hippsValve.addPressureTransmitter(hippsPT2);
    hippsValve.addPressureTransmitter(hippsPT3);

    // ESD push button
    setup.esdButton = new PushButton("ESD-PB-001");

    System.out.println("Instrumentation setup completed:");
    System.out.println("  - Separator monitoring: PT-101, TT-101");
    System.out.println(
        "  - HIPPS monitoring: PT-HIPPS-1, PT-HIPPS-2, PT-HIPPS-3 (2oo3 voting) at inlet valve outlet");
    System.out.println("  - Manual ESD button: ESD-PB-001");
    System.out.println();

    return setup;
  }

  /**
   * Sets up process logic sequences.
   */
  private static ProcessLogicSetup setupProcessLogic(ProcessSystem system,
      InstrumentationSetup instruments) {
    ProcessLogicSetup setup = new ProcessLogicSetup();

    // Get equipment references
    ControlValve inletValve = (ControlValve) system.getUnit("Inlet Control Valve");
    ESDValve esdInletValve = (ESDValve) system.getUnit("ESD Inlet Isolation");
    Separator separator = (Separator) system.getUnit("HP Separator");
    Splitter gasSplitter = (Splitter) system.getUnit("Gas Splitter");
    ESDValve bdValve = (ESDValve) system.getUnit("ESD Blowdown Valve");
    HIPPSValve hippsValve = (HIPPSValve) system.getUnit("HIPPS-001");

    // HIPPS Logic (SIL-2) - Independent fast-acting pressure protection
    setup.hippsLogic = createHIPPSLogic(hippsValve);

    // ESD Logic (SIL-2) - Emergency shutdown without HIPPS
    setup.esdLogic = new ESDLogic("ESD Level 1");
    setup.esdLogic.addAction(new CloseValveAction(inletValve), 0.0); // Close inlet control
                                                                     // immediately
    setup.esdLogic.addAction(new CloseValveAction(esdInletValve), 0.0); // Close ESD inlet
                                                                        // isolation
    setup.esdLogic.addAction(new SetSplitterAction(gasSplitter, new double[] {0.0, 0.0, 1.0}), 0.5); // Route
                                                                                                     // to
                                                                                                     // ESD
    setup.esdLogic.addAction(new EnergizeESDValveAction(bdValve, 100.0), 0.5); // Energize blowdown
                                                                               // after 0.5s
    setup.esdLogic.addAction(new SetSeparatorModeAction(separator, false), 1.0); // Switch to
                                                                                 // transient

    // Link ESD button to logic
    instruments.esdButton.linkToLogic(setup.esdLogic);

    // Startup Logic with permissives
    setup.startupLogic = new StartupLogic("System Startup");

    // Add startup permissives (all must be true before starting)
    setup.startupLogic.addPermissive(new PressureCondition(separator, 5.0, "<")); // Depressurized
    setup.startupLogic.addPermissive(new ValvePositionCondition(bdValve, "<", 5.0)); // BD closed
    setup.startupLogic.addPermissive(new TemperatureCondition(separator, 50.0, "<")); // Cool
    setup.startupLogic.addPermissive(new TimerCondition(10.0)); // 10s minimum wait

    // Add startup actions (executed in sequence with delays)
    setup.startupLogic.addAction(new OpenValveAction(inletValve), 0.0); // Open inlet
    setup.startupLogic.addAction(new SetValveOpeningAction(inletValve, 50.0), 5.0); // 50% after 5s
    setup.startupLogic.addAction(new SetValveOpeningAction(inletValve, 80.0), 10.0); // 80% after
                                                                                     // 10s
    setup.startupLogic.addAction(new SetSeparatorModeAction(separator, true), 15.0); // Steady state

    System.out.println("Process logic setup completed:");
    System.out.println("  - HIPPS Logic: Independent fast-acting pressure protection");
    System.out
        .println("  - ESD Logic: 5-step emergency shutdown sequence (inlet isolation + blowdown)");
    System.out.println("  - Startup Logic: 4 permissives + 4-step startup sequence");
    System.out.println("  - ESD button linked to ESD logic");
    System.out.println();

    return setup;
  }

  /**
   * Runs comprehensive test scenarios using the batch execution API.
   */
  private static void runTestScenarios(ProcessScenarioRunner runner) {
    // Create test runner with automatic KPI collection
    ScenarioTestRunner testRunner = new ScenarioTestRunner(runner);

    // Execute all scenarios in batch with automatic header and dashboard display
    testRunner.batch()
        .add("Normal Startup", ProcessSafetyScenario.builder("Normal Startup").build(),
            "System Startup", 30.0, 1.0)

        .addDelayed("Manual ESD", ProcessSafetyScenario.builder("Manual ESD").build(),
            "ESD Level 1", 5000, "OPERATOR ACTIVATES ESD BUTTON", 25.0, 0.5)

        .addDelayed("High Pressure", ProcessSafetyScenario.builder("High Pressure")
            .customManipulator("HP Feed", equipment -> {
              if (equipment instanceof Stream) {
                ((Stream) equipment).setPressure(70.0, "bara");
                System.out
                    .println("  - Feed pressure increased to 70 bara (simulating upstream upset)");
              }
            }).build(), "ESD Level 1", 8000, "HIGH PRESSURE DETECTED - AUTO ESD TRIGGERED", 30.0,
            1.0)

        .add("Equip Failure",
            ProcessSafetyScenario.builder("Equipment Failure").utilityLoss("HP Separator").build(),
            null, 20.0, 1.0)

        .execute();
  }

  /**
   * Container for instrumentation setup.
   */
  private static class InstrumentationSetup {
    PressureTransmitter separatorPT;
    TemperatureTransmitter separatorTT;
    PushButton esdButton;
  }

  /**
   * Container for process logic setup.
   */
  private static class ProcessLogicSetup {
    ESDLogic hippsLogic;
    ESDLogic esdLogic;
    StartupLogic startupLogic;
  }

  /**
   * Creates independent HIPPS logic - fast-acting pressure protection.
   *
   * @param hippsValve the HIPPS valve to control
   * @return configured ESD logic for HIPPS protection
   */
  private static ESDLogic createHIPPSLogic(HIPPSValve hippsValve) {
    // Use ESDLogic as base for HIPPS (similar fast-acting safety function)
    ESDLogic hippsLogic = new ESDLogic("HIPPS Protection");

    // Single action: close HIPPS valve fast (no delay)
    hippsLogic.addAction(new CloseValveAction(hippsValve), 0.0);

    return hippsLogic;
  }

  /**
   * Example: Custom logic class for gradual pressure reduction.
   * 
   * <p>
   * This demonstrates how to create custom logic not in the library by implementing the
   * ProcessLogic interface. For production use, move this to a separate file like:
   * neqsim/process/logic/control/GradualPressureReductionLogic.java
   * </p>
   * 
   * <p>
   * Usage example:
   * </p>
   * 
   * <pre>
   * // Create instance
   * ProcessLogic customLogic =
   *     new GradualPressureReductionLogic("Gradual Reduction", inletValve, 30.0, // target opening
   *                                                                              // %
   *         2.0 // step size %
   *     );
   * 
   * // Add to runner
   * runner.addLogic(customLogic);
   * 
   * // Activate and execute
   * customLogic.activate();
   * while (!customLogic.isComplete()) {
   *   customLogic.execute(1.0); // 1 second timestep
   *   system.runTransient(1.0);
   * }
   * </pre>
   */
  private static class GradualPressureReductionLogic implements ProcessLogic {
    private final String name;
    private final ControlValve valve;
    private final double targetOpening;
    private final double step;
    private LogicState state = LogicState.IDLE;
    private double currentOpening;

    /**
     * Creates gradual pressure reduction logic.
     * 
     * @param name logic name
     * @param valve control valve to adjust
     * @param targetOpening target valve opening (%)
     * @param step opening change per time step (%)
     */
    public GradualPressureReductionLogic(String name, ControlValve valve, double targetOpening,
        double step) {
      this.name = name;
      this.valve = valve;
      this.targetOpening = targetOpening;
      this.step = step;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public LogicState getState() {
      return state;
    }

    @Override
    public void activate() {
      if (state == LogicState.IDLE || state == LogicState.COMPLETED) {
        state = LogicState.RUNNING;
        currentOpening = valve.getPercentValveOpening();
        System.out.println(
            name + " activated: Current=" + currentOpening + "%, Target=" + targetOpening + "%");
      }
    }

    @Override
    public void deactivate() {
      state = LogicState.IDLE;
    }

    @Override
    public boolean reset() {
      state = LogicState.IDLE;
      return true;
    }

    @Override
    public void execute(double timeStep) {
      if (state == LogicState.RUNNING) {
        // Gradually adjust valve opening
        if (Math.abs(currentOpening - targetOpening) > step) {
          currentOpening += (targetOpening > currentOpening) ? step : -step;
          valve.setPercentValveOpening(currentOpening);
        } else {
          // Reached target
          valve.setPercentValveOpening(targetOpening);
          state = LogicState.COMPLETED;
          System.out.println(name + " completed at " + targetOpening + "%");
        }
      }
    }

    @Override
    public boolean isActive() {
      return state == LogicState.RUNNING;
    }

    @Override
    public boolean isComplete() {
      return state == LogicState.COMPLETED;
    }

    @Override
    public List<ProcessEquipmentInterface> getTargetEquipment() {
      List<ProcessEquipmentInterface> equipment = new ArrayList<>();
      equipment.add(valve);
      return equipment;
    }

    @Override
    public String getStatusDescription() {
      return name + " - Current: " + String.format("%.1f", currentOpening) + "%, Target: "
          + targetOpening + "%";
    }
  }
}
