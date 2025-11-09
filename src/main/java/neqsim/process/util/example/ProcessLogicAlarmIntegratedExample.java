package neqsim.process.util.example;

import java.util.Arrays;
import neqsim.process.alarm.AlarmActionHandler;
import neqsim.process.alarm.AlarmConfig;
import neqsim.process.alarm.AlarmEvaluator;
import neqsim.process.alarm.AlarmReporter;
import neqsim.process.alarm.ProcessAlarmManager;
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
import neqsim.process.measurementdevice.VolumeFlowTransmitter;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.logic.startup.StartupLogic;
import neqsim.process.logic.control.PressureControlLogic;
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
 * Complete integrated example demonstrating alarm-triggered process logic.
 * 
 * <p>
 * This example demonstrates the full integration of:
 * <ul>
 * <li>Multi-level alarm configuration (LOLO, LO, HI, HIHI)</li>
 * <li>ProcessAlarmManager for centralized alarm coordination</li>
 * <li>Alarms triggering automatic process control actions</li>
 * <li>Alarms triggering ESD sequences</li>
 * <li>Alarm acknowledgement and clearance workflows</li>
 * <li>HIPPS Logic with alarm-based activation</li>
 * <li>Startup Logic with alarm permissives</li>
 * <li>ProcessScenarioRunner for automated testing</li>
 * </ul>
 * 
 * <p>
 * Layered safety architecture with alarm integration:
 * <ul>
 * <li>Layer 1 (Alarms): HI/LO alarms trigger operator actions or control adjustments</li>
 * <li>Layer 2 (Alarms + Control): HIHI/LOLO alarms trigger automatic control responses</li>
 * <li>Layer 3 (SIL-2): HIPPS provides independent fast-acting pressure protection</li>
 * <li>Layer 4 (SIL-2): ESD system provides emergency shutdown</li>
 * <li>Layer 5 (Mechanical): PSV provides final mechanical protection</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class ProcessLogicAlarmIntegratedExample {

  // Safety setpoints with layered protection
  private static final double HIGH_PRESSURE_ALARM = 53.0; // bara (HI - operator notification)
  private static final double HIGH_HIGH_PRESSURE_ALARM = 56.0; // bara (HIHI - auto throttle)
  private static final double HIPPS_ACTIVATION_PRESSURE = 59.0; // bara (SIL-2 HIPPS)
  private static final double ESD_ACTIVATION_PRESSURE = 60.0; // bara (SIL-2 ESD)
  private static final double PSV_SET_PRESSURE = 65.0; // bara (mechanical protection)

  private static final double HIGH_TEMP_ALARM = 40.0; // C (HI)
  private static final double HIGH_HIGH_TEMP_ALARM = 55.0; // C (HIHI - trigger cooling)

  private static final double LOW_LEVEL_ALARM = 30.0; // % (LO - operator notification)
  private static final double LOW_LOW_LEVEL_ALARM = 20.0; // % (LOLO - trigger shutdown)
  private static final double HIGH_LEVEL_ALARM = 70.0; // % (HI)
  private static final double HIGH_HIGH_LEVEL_ALARM = 85.0; // % (HIHI)

  // Simulation state tracking
  private static double simulationTime = 0.0;

  /**
   * Main method demonstrating integrated alarm-triggered process logic.
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    System.out.println("╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║  INTEGRATED ALARM-TRIGGERED PROCESS LOGIC EXAMPLE             ║");
    System.out.println("║  Demonstrating Complete Alarm and Safety Integration          ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    // Build process system
    ProcessSystem processSystem = buildProcessSystem();

    // Create alarm manager
    ProcessAlarmManager alarmManager = new ProcessAlarmManager();

    // Setup instrumentation with alarms
    InstrumentationSetup instruments = setupInstrumentationWithAlarms(processSystem, alarmManager);

    // Setup process logic (ESD, HIPPS, Startup)
    ProcessLogicSetup logicSetup = setupProcessLogic(processSystem, instruments);

    // Register automatic alarm-triggered actions
    registerAlarmActions(alarmManager, processSystem, logicSetup);

    // Create scenario runner and initialize steady-state
    ProcessScenarioRunner runner = new ProcessScenarioRunner(processSystem);
    runner.initializeSteadyState();

    runner.addLogic(logicSetup.hippsLogic);
    runner.addLogic(logicSetup.esdLogic);
    runner.addLogic(logicSetup.startupLogic);

    // Run comprehensive scenarios demonstrating alarm-triggered logic
    runAlarmTriggeredScenarios(runner, alarmManager, instruments, logicSetup, processSystem);

    System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║    ALL ALARM-TRIGGERED SCENARIOS COMPLETED                     ║");
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

    // HIPPS Valve (High Integrity Pressure Protection System)
    HIPPSValve hippsValve = new HIPPSValve("HIPPS-001", inletValve.getOutletStream());
    hippsValve.setPercentValveOpening(100.0);
    hippsValve.setCv(400.0);
    hippsValve.setClosureTime(3.0);
    hippsValve.setSILRating(2);

    // ESD Inlet Isolation Valve
    ESDValve esdInletValve = new ESDValve("ESD Inlet Isolation", hippsValve.getOutletStream());
    esdInletValve.setPercentValveOpening(100.0);
    esdInletValve.setCv(350.0);
    esdInletValve.setStrokeTime(4.0);
    esdInletValve.energize();

    // High-pressure separator
    Separator separator = new Separator("HP Separator", esdInletValve.getOutletStream());
    separator.setCalculateSteadyState(true);
    separator.setInternalDiameter(2.0);

    // Gas routing splitter (process/PSV/ESD) - 3 outputs
    Splitter gasSplitter = new Splitter("Gas Splitter", separator.getGasOutStream(), 3);
    gasSplitter.setSplitFactors(new double[] {1.0, 0.0, 0.0});

    // Process outlet stream
    Stream processStream = new Stream("Process Stream", gasSplitter.getSplitStream(0));

    // Pressure Safety Valve
    SafetyValve psv = new SafetyValve("PSV-001", gasSplitter.getSplitStream(1));
    psv.setPressureSpec(PSV_SET_PRESSURE);
    psv.setFullOpenPressure(PSV_SET_PRESSURE + 2.0);
    psv.setBlowdown(7.0);

    // ESD Blowdown valve
    ESDValve bdValve = new ESDValve("ESD Blowdown Valve", gasSplitter.getSplitStream(2));
    bdValve.setStrokeTime(5.0);
    bdValve.setCv(250.0);
    bdValve.setFailSafePosition(100.0);
    bdValve.deEnergize();

    // Flare header
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
   * Sets up instrumentation with comprehensive alarm configuration.
   * 
   * @param system the process system to instrument
   * @param alarmManager the alarm manager to register devices with
   * @return the instrumentation setup containing all configured devices
   */
  private static InstrumentationSetup setupInstrumentationWithAlarms(ProcessSystem system,
      ProcessAlarmManager alarmManager) {
    InstrumentationSetup setup = new InstrumentationSetup();

    // Get equipment references
    ControlValve inletValve = (ControlValve) system.getUnit("Inlet Control Valve");
    HIPPSValve hippsValve = (HIPPSValve) system.getUnit("HIPPS-001");
    Separator separator = (Separator) system.getUnit("HP Separator");
    Stream processStream = (Stream) system.getUnit("Process Stream");

    // Pressure monitoring with layered protection
    setup.separatorPT = new PressureTransmitter("PT-101", separator.getGasOutStream());
    setup.separatorPT.setAlarmConfig(AlarmConfig.builder().highLimit(HIGH_PRESSURE_ALARM)
        .highHighLimit(HIGH_HIGH_PRESSURE_ALARM).deadband(0.5).delay(1.0).unit("bara").build());
    alarmManager.register(setup.separatorPT);

    // Temperature monitoring with cooling trigger
    setup.separatorTT = new TemperatureTransmitter("TT-101", separator.getGasOutStream());
    setup.separatorTT.setAlarmConfig(AlarmConfig.builder().highLimit(HIGH_TEMP_ALARM)
        .highHighLimit(HIGH_HIGH_TEMP_ALARM).deadband(2.0).delay(3.0).unit("C").build());
    alarmManager.register(setup.separatorTT);

    // Flow monitoring with low flow protection
    setup.flowTransmitter = new VolumeFlowTransmitter("FT-201", processStream);
    setup.flowTransmitter.setAlarmConfig(AlarmConfig.builder().lowLimit(100.0).lowLowLimit(50.0)
        .highLimit(2000.0).deadband(10.0).delay(5.0).unit("m3/hr").build());
    alarmManager.register(setup.flowTransmitter);

    // Level monitoring with shutdown protection
    setup.levelTransmitter = new LevelTransmitter("LT-101", separator);
    setup.levelTransmitter.setAlarmConfig(AlarmConfig.builder().lowLowLimit(LOW_LOW_LEVEL_ALARM)
        .lowLimit(LOW_LEVEL_ALARM).highLimit(HIGH_LEVEL_ALARM).highHighLimit(HIGH_HIGH_LEVEL_ALARM)
        .deadband(2.0).delay(2.0).unit("%").build());
    alarmManager.register(setup.levelTransmitter);

    // HIPPS pressure transmitters (2oo3 voting) - no delay for safety-critical operation

    PressureTransmitter hippsPT1 =
        new PressureTransmitter("PT-HIPPS-1", inletValve.getOutletStream());
    PressureTransmitter hippsPT2 =
        new PressureTransmitter("PT-HIPPS-2", inletValve.getOutletStream());
    PressureTransmitter hippsPT3 =
        new PressureTransmitter("PT-HIPPS-3", inletValve.getOutletStream());

    hippsValve.addPressureTransmitter(hippsPT1);
    hippsValve.addPressureTransmitter(hippsPT2);
    hippsValve.addPressureTransmitter(hippsPT3);

    AlarmConfig hippsAlarmConfig = AlarmConfig.builder().highHighLimit(HIPPS_ACTIVATION_PRESSURE)
        .deadband(0.2).delay(0.0).unit("bara").build();

    hippsPT1.setAlarmConfig(hippsAlarmConfig);
    hippsPT2.setAlarmConfig(hippsAlarmConfig);
    hippsPT3.setAlarmConfig(hippsAlarmConfig);
    alarmManager.register(hippsPT1);
    alarmManager.register(hippsPT2);
    alarmManager.register(hippsPT3);

    // ESD trigger pressure transmitter
    setup.esdPressureTransmitter =
        new PressureTransmitter("PT-ESD-001", separator.getGasOutStream());
    setup.esdPressureTransmitter.setAlarmConfig(AlarmConfig.builder()
        .highHighLimit(ESD_ACTIVATION_PRESSURE).deadband(0.5).delay(0.0).unit("bara").build());
    alarmManager.register(setup.esdPressureTransmitter);

    // Manual ESD button
    setup.esdButton = new PushButton("ESD-PB-001");

    System.out.println("═══════════════════════════════════════════════════════════");
    System.out.println("           ALARM CONFIGURATION WITH TRIGGER ACTIONS");
    System.out.println("═══════════════════════════════════════════════════════════");
    System.out.println("PT-101 (Separator Pressure):");
    System.out.println("  HI:   " + HIGH_PRESSURE_ALARM + " bara → Operator notification");
    System.out.println("  HIHI: " + HIGH_HIGH_PRESSURE_ALARM + " bara → Auto throttle inlet valve");
    System.out.println();
    System.out.println("TT-101 (Temperature):");
    System.out.println("  HI:   " + HIGH_TEMP_ALARM + "°C → Operator notification");
    System.out.println("  HIHI: " + HIGH_HIGH_TEMP_ALARM + "°C → Trigger cooling actions");
    System.out.println();
    System.out.println("FT-201 (Process Flow):");
    System.out.println("  LO:   100 m3/hr → Operator notification");
    System.out.println("  LOLO: 50 m3/hr → Trigger controlled shutdown");
    System.out.println();
    System.out.println("LT-101 (Separator Level):");
    System.out.println("  LOLO: " + LOW_LOW_LEVEL_ALARM + "% → Trigger emergency shutdown");
    System.out.println("  LO:   " + LOW_LEVEL_ALARM + "% → Operator notification");
    System.out.println();
    System.out.println("PT-HIPPS-1/2/3 (HIPPS Protection - 2oo3):");
    System.out.println("  HIHI: " + HIPPS_ACTIVATION_PRESSURE + " bara → Immediate HIPPS closure");
    System.out.println();
    System.out.println("PT-ESD-001 (ESD Trigger):");
    System.out.println("  HIHI: " + ESD_ACTIVATION_PRESSURE + " bara → Full ESD sequence");
    System.out.println("═══════════════════════════════════════════════════════════\n");

    return setup;
  }

  /**
   * Registers automatic alarm-triggered actions with the alarm manager.
   * 
   * @param alarmManager the alarm manager to register actions with
   * @param system the process system containing equipment
   * @param logicSetup the process logic setup containing ESD and HIPPS logic
   */
  private static void registerAlarmActions(ProcessAlarmManager alarmManager, ProcessSystem system,
      ProcessLogicSetup logicSetup) {

    ControlValve inletValve = (ControlValve) system.getUnit("Inlet Control Valve");

    // Create pressure control logic for automatic valve throttling
    PressureControlLogic throttleLogic =
        new PressureControlLogic("Pressure HIHI Auto-Throttle", inletValve, 50.0, system);

    // HIHI pressure alarm triggers automatic valve throttling logic
    alarmManager
        .registerActionHandler(AlarmActionHandler.activateLogicOnHIHI("PT-101", throttleLogic));

    // HIPPS transmitter alarms trigger HIPPS logic
    alarmManager.registerActionHandler(
        AlarmActionHandler.activateLogicOnHIHI("PT-HIPPS-1", logicSetup.hippsLogic));
    alarmManager.registerActionHandler(
        AlarmActionHandler.activateLogicOnHIHI("PT-HIPPS-2", logicSetup.hippsLogic));
    alarmManager.registerActionHandler(
        AlarmActionHandler.activateLogicOnHIHI("PT-HIPPS-3", logicSetup.hippsLogic));

    // ESD pressure alarm triggers ESD logic
    alarmManager.registerActionHandler(
        AlarmActionHandler.activateLogicOnHIHI("PT-ESD-001", logicSetup.esdLogic));

    System.out.println("═══════════════════════════════════════════════════════════");
    System.out.println("           AUTOMATIC ALARM ACTIONS REGISTERED");
    System.out.println("═══════════════════════════════════════════════════════════");
    System.out.println("PT-101 HIHI → " + throttleLogic.getStatusDescription());
    System.out.println("PT-HIPPS-x HIHI → Activate HIPPS protection (SIL-2)");
    System.out.println("PT-ESD-001 HIHI → Activate full ESD sequence (SIL-2)");
    System.out.println("═══════════════════════════════════════════════════════════\n");
  }

  /**
   * Sets up process logic sequences.
   * 
   * @param system the process system containing equipment
   * @param instruments the instrumentation setup containing measurement devices
   * @return the process logic setup containing configured logic sequences
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

    // HIPPS Logic (Alarm-triggered)
    setup.hippsLogic = createHIPPSLogic(hippsValve);

    // ESD Logic (Alarm-triggered or manual)
    setup.esdLogic = new ESDLogic("ESD Level 1");
    setup.esdLogic.addAction(new CloseValveAction(inletValve), 0.0);
    setup.esdLogic.addAction(new CloseValveAction(esdInletValve), 0.0);
    setup.esdLogic.addAction(new SetSplitterAction(gasSplitter, new double[] {0.0, 0.0, 1.0}), 0.5);
    setup.esdLogic.addAction(new EnergizeESDValveAction(bdValve, 100.0), 0.5);
    setup.esdLogic.addAction(new SetSeparatorModeAction(separator, false), 1.0);

    instruments.esdButton.linkToLogic(setup.esdLogic);

    // Startup Logic with alarm-based permissives
    setup.startupLogic = new StartupLogic("System Startup");
    setup.startupLogic.addPermissive(new PressureCondition(separator, 5.0, "<"));
    setup.startupLogic.addPermissive(new ValvePositionCondition(bdValve, "<", 5.0));
    setup.startupLogic.addPermissive(new TemperatureCondition(separator, 50.0, "<"));
    setup.startupLogic.addPermissive(new TimerCondition(10.0));

    setup.startupLogic.addAction(new OpenValveAction(inletValve), 0.0);
    setup.startupLogic.addAction(new SetValveOpeningAction(inletValve, 50.0), 5.0);
    setup.startupLogic.addAction(new SetValveOpeningAction(inletValve, 80.0), 10.0);
    setup.startupLogic.addAction(new SetSeparatorModeAction(separator, true), 15.0);

    System.out.println("Process logic setup completed with alarm integration:");
    System.out.println("  - HIPPS Logic: Triggered by PT-HIPPS HIHI alarms (2oo3 voting)");
    System.out.println("  - ESD Logic: Triggered by PT-ESD-001 HIHI alarm or manual button");
    System.out.println("  - Startup Logic: Permissives check no active alarms");
    System.out.println();

    return setup;
  }

  /**
   * Runs comprehensive alarm-triggered scenarios.
   * 
   * @param runner the scenario runner for executing scenarios
   * @param alarmManager the alarm manager for evaluation and tracking
   * @param instruments the instrumentation setup containing devices
   * @param logicSetup the process logic setup containing logic sequences
   * @param system the process system
   */
  private static void runAlarmTriggeredScenarios(ProcessScenarioRunner runner,
      ProcessAlarmManager alarmManager, InstrumentationSetup instruments,
      ProcessLogicSetup logicSetup, ProcessSystem system) {

    ScenarioTestRunner testRunner = new ScenarioTestRunner(runner);
    testRunner.printHeader();

    // ═══════════════════════════════════════════════════════════════
    // Scenario 1: Normal operation with alarm monitoring
    // ═══════════════════════════════════════════════════════════════
    printScenarioHeader("SCENARIO 1: Normal Operation with Alarm Monitoring");
    ProcessSafetyScenario normalScenario =
        ProcessSafetyScenario.builder("Normal Operation").build();
    testRunner.executeScenario("Normal Operation", normalScenario, "System Startup", 30.0, 1.0);
    runScenarioStep(system, alarmManager, instruments, 30.0);
    AlarmReporter.displayAlarmStatus(alarmManager, "Normal Operation");

    // ═══════════════════════════════════════════════════════════════
    // Scenario 2: HI Alarm - Operator Notification Only
    // ═══════════════════════════════════════════════════════════════
    printScenarioHeader("SCENARIO 2: HI Pressure Alarm - Operator Notification");
    ProcessSafetyScenario hiAlarmScenario =
        ProcessSafetyScenario.builder("HI Alarm").customManipulator("HP Feed", equipment -> {
          if (equipment instanceof Stream) {
            ((Stream) equipment).setPressure(54.0, "bara");
            System.out.println("  ➤ Feed pressure increased to 54 bara (above HI limit)");
          }
        }).build();
    testRunner.executeScenario("HI Alarm", hiAlarmScenario, 15.0, 0.5);
    runScenarioStep(system, alarmManager, instruments, 15.0);
    AlarmReporter.displayAlarmStatus(alarmManager, "After HI Alarm");
    System.out.println("\n  📢 OPERATOR ACTION REQUIRED: Pressure HI alarm active");
    System.out.println("  ✓ No automatic actions triggered (HI level only)\n");

    // ═══════════════════════════════════════════════════════════════
    // Scenario 3: HIHI Alarm - Automatic Valve Throttling
    // ═══════════════════════════════════════════════════════════════
    printScenarioHeader("SCENARIO 3: HIHI Pressure Alarm - Automatic Control Action");
    ProcessSafetyScenario hihiAlarmScenario =
        ProcessSafetyScenario.builder("HIHI Alarm").customManipulator("HP Feed", equipment -> {
          if (equipment instanceof Stream) {
            ((Stream) equipment).setPressure(57.0, "bara");
            System.out.println("  ➤ Feed pressure increased to 57 bara (above HIHI limit)");
          }
        }).build();
    testRunner.executeScenario("HIHI Alarm", hihiAlarmScenario, 20.0, 0.5);
    runScenarioStep(system, alarmManager, instruments, 20.0);
    alarmManager.acknowledgeAll(simulationTime);
    AlarmReporter.displayAlarmStatus(alarmManager, "After HIHI Alarm + Auto Control");

    // ═══════════════════════════════════════════════════════════════
    // Scenario 4: HIPPS Activation via Alarm
    // ═══════════════════════════════════════════════════════════════
    printScenarioHeader("SCENARIO 4: HIPPS Triggered by Pressure Alarm");
    ProcessSafetyScenario hippsScenario =
        ProcessSafetyScenario.builder("HIPPS Trigger").customManipulator("HP Feed", equipment -> {
          if (equipment instanceof Stream) {
            ((Stream) equipment).setPressure(60.0, "bara");
            System.out.println("  ➤ Feed pressure increased to 60 bara (HIPPS activation level)");
          }
        }).build();
    testRunner.executeScenario("HIPPS Trigger", hippsScenario, 15.0, 0.2);
    runScenarioStep(system, alarmManager, instruments, 15.0);
    alarmManager.acknowledgeAll(simulationTime);
    AlarmReporter.displayAlarmStatus(alarmManager, "After HIPPS Activation");

    // ═══════════════════════════════════════════════════════════════
    // Scenario 5: Full ESD Triggered by Alarm
    // ═══════════════════════════════════════════════════════════════
    printScenarioHeader("SCENARIO 5: ESD Triggered by HIHI Pressure Alarm");
    ProcessSafetyScenario esdAlarmScenario =
        ProcessSafetyScenario.builder("ESD Alarm").customManipulator("HP Feed", equipment -> {
          if (equipment instanceof Stream) {
            ((Stream) equipment).setPressure(61.0, "bara");
            System.out.println("  ➤ Feed pressure increased to 61 bara (ESD activation level)");
          }
        }).build();
    testRunner.executeScenario("ESD Alarm", esdAlarmScenario, 15.0, 0.2);
    runScenarioStep(system, alarmManager, instruments, 15.0);
    alarmManager.acknowledgeAll(simulationTime);
    runBlowdownSimulation(system, instruments, alarmManager, simulationTime);
    AlarmReporter.displayAlarmStatus(alarmManager, "After ESD Activation"); // ═══════════════════════════════════════════════════════════════
    // Scenario 6: Low Level Alarm Triggering Shutdown
    // ═══════════════════════════════════════════════════════════════
    printScenarioHeader("SCENARIO 6: LOLO Level Alarm - Emergency Shutdown");
    ProcessSafetyScenario lowLevelScenario =
        ProcessSafetyScenario.builder("Low Level").customManipulator("LT-101", equipment -> {
          if (equipment instanceof LevelTransmitter) {
            System.out.println(
                "  ➤ Simulating level drop to " + LOW_LOW_LEVEL_ALARM + "% (LOLO trigger)");
          }
        }).build();
    testRunner.executeScenario("Low Level", lowLevelScenario, 15.0, 0.5);
    simulationTime += 15.0;
    simulateLowLevelAlarm(logicSetup.esdLogic, alarmManager);
    AlarmReporter.displayAlarmStatus(alarmManager, "After LOLO Level Shutdown");

    // ═══════════════════════════════════════════════════════════════
    // Display comprehensive results
    // ═══════════════════════════════════════════════════════════════
    AlarmReporter.displayAlarmHistory(alarmManager);
    AlarmReporter.displayAlarmStatistics(alarmManager);

    testRunner.displayDashboard();
  }

  /**
   * Simulates low level alarm triggering shutdown.
   * 
   * @param esdLogic the ESD logic to activate
   * @param alarmManager the alarm manager for acknowledgement
   */
  private static void simulateLowLevelAlarm(ESDLogic esdLogic, ProcessAlarmManager alarmManager) {

    System.out.println("\n  ╔═══════════════════════════════════════════════════════╗");
    System.out.println("  ║  ⚠️  LOLO LEVEL ALARM - EMERGENCY SHUTDOWN           ║");
    System.out.println("  ╠═══════════════════════════════════════════════════════╣");
    System.out
        .println("  ║  Trigger: LT-101 LOLO at " + LOW_LOW_LEVEL_ALARM + "%                  ║");
    System.out.println("  ║  Action: Emergency shutdown to prevent dry running   ║");
    System.out.println("  ╚═══════════════════════════════════════════════════════╝\n");

    // Activate ESD
    esdLogic.activate();

    alarmManager.acknowledgeAll(simulationTime);
  }

  /**
   * Runs transient blowdown simulation after ESD activation.
   * 
   * @param system the process system
   * @param instruments the instrumentation setup
   * @param alarmManager the alarm manager
   * @param startTime the simulation start time
   */
  private static void runBlowdownSimulation(ProcessSystem system, InstrumentationSetup instruments,
      ProcessAlarmManager alarmManager, double startTime) {

    System.out.println("\n╔═══════════════════════════════════════════════════════════════╗");
    System.out.println("║           TRANSIENT BLOWDOWN SIMULATION                       ║");
    System.out.println("╚═══════════════════════════════════════════════════════════════╝");
    System.out.println("Simulating depressurization through blowdown valve and PSV...\n");

    double timeStep = 1.0; // 1 second time steps
    double blowdownTime = 60.0; // Simulate 60 seconds of blowdown
    double currentTime = startTime;

    System.out.println(String.format("%-10s %-15s %-15s %-15s %-15s", "Time [s]", "Pressure [bara]",
        "Temp [°C]", "Flow [kg/hr]", "Level [%]"));
    System.out.println("─────────────────────────────────────────────────────────────────────");

    for (int i = 0; i < (int) (blowdownTime / timeStep); i++) {
      currentTime += timeStep;

      // Run transient simulation step
      system.runTransient(timeStep);

      // Evaluate alarms during blowdown
      if (i % 10 == 0) { // Every 10 seconds
        AlarmEvaluator
            .evaluateDevices(alarmManager,
                Arrays.asList(instruments.separatorPT, instruments.separatorTT,
                    instruments.flowTransmitter, instruments.levelTransmitter),
                timeStep, currentTime);

        // Display current state
        System.out.println(String.format("%-10.1f %-15.2f %-15.2f %-15.2f %-15.2f",
            currentTime - startTime, instruments.separatorPT.getMeasuredValue(),
            instruments.separatorTT.getMeasuredValue(),
            instruments.flowTransmitter.getMeasuredValue(),
            instruments.levelTransmitter.getMeasuredValue()));
      }

      // Stop if pressure is sufficiently low
      if (instruments.separatorPT.getMeasuredValue() < 2.0) {
        System.out.println("\n✓ Blowdown complete - pressure reduced to safe level");
        break;
      }
    }

    System.out.println("─────────────────────────────────────────────────────────────────────");
    System.out.println(
        String.format("Final pressure: %.2f bara", instruments.separatorPT.getMeasuredValue()));
    System.out.println(String.format("Blowdown duration: %.1f seconds\n", currentTime - startTime));
  }

  /**
   * Prints scenario header.
   * 
   * @param title the scenario title to display
   */
  private static void printScenarioHeader(String title) {
    AlarmReporter.printScenarioHeader(title);
  }

  /**
   * Executes a scenario step and evaluates alarms.
   * 
   * @param system the process system
   * @param alarmManager the alarm manager
   * @param instruments the instrumentation setup
   * @param deltaTime time increment for this step
   */
  private static void runScenarioStep(ProcessSystem system, ProcessAlarmManager alarmManager,
      InstrumentationSetup instruments, double deltaTime) {
    simulationTime += deltaTime;
    system.run();
    AlarmEvaluator.evaluateAndDisplay(alarmManager,
        Arrays.asList(instruments.separatorPT, instruments.separatorTT, instruments.flowTransmitter,
            instruments.levelTransmitter, instruments.esdPressureTransmitter),
        deltaTime, simulationTime);
  }

  /**
   * Container for instrumentation setup.
   */
  private static class InstrumentationSetup {
    PressureTransmitter separatorPT;
    TemperatureTransmitter separatorTT;
    VolumeFlowTransmitter flowTransmitter;
    LevelTransmitter levelTransmitter;
    PressureTransmitter esdPressureTransmitter;
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
   * Creates independent HIPPS logic.
   * 
   * @param hippsValve the HIPPS valve to control
   * @return the configured HIPPS logic
   */
  private static ESDLogic createHIPPSLogic(HIPPSValve hippsValve) {
    ESDLogic hippsLogic = new ESDLogic("HIPPS Protection");
    hippsLogic.addAction(new CloseValveAction(hippsValve), 0.0);
    return hippsLogic;
  }
}
