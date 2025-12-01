package neqsim.process.util.example;

import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.valve.BlowdownValve;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.valve.SafetyValve;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.flare.Flare;
import neqsim.process.measurementdevice.PushButton;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.measurementdevice.TemperatureTransmitter;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Comprehensive example demonstrating integrated safety systems including:
 * <ul>
 * <li>Emergency Shutdown (ESD) System with SIL-2 implementation</li>
 * <li>High Integrity Pressure Protection System (HIPPS) with SIL-3</li>
 * <li>Pressure Safety Valve (PSV) as final protection layer</li>
 * <li>Blowdown system with flow control</li>
 * <li>Flare system with heat radiation monitoring</li>
 * <li>Multiple pressure and temperature transmitters</li>
 * <li>Fire detection system</li>
 * <li>Various process scenarios (normal, overpressure, fire, HIPPS activation)</li>
 * </ul>
 *
 * <p>
 * Safety Integrity Levels (SIL):
 * <ul>
 * <li>SIL-3: HIPPS (PFD 0.0001-0.001) - High pressure protection</li>
 * <li>SIL-2: ESD (PFD 0.001-0.01) - Emergency shutdown</li>
 * <li>SIL-1: Alarms (PFD 0.01-0.1) - Operator intervention</li>
 * <li>PSV: Mechanical final protection layer</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class IntegratedSafetySystemExample {

  // Safety setpoints
  private static final double NORMAL_OPERATING_PRESSURE = 50.0; // bara
  private static final double HIGH_PRESSURE_ALARM = 55.0; // bara (SIL-1)
  private static final double HIGH_HIGH_PRESSURE_ALARM = 58.0; // bara (triggers ESD SIL-2)
  private static final double HIPPS_ACTIVATION_PRESSURE = 60.0; // bara (SIL-3)
  private static final double PSV_SET_PRESSURE = 65.0; // bara (mechanical protection)
  private static final double FIRE_DETECTION_TEMPERATURE = 150.0; // C

  // Time constants
  private static final double HIPPS_CLOSURE_TIME = 2.0; // seconds (fast acting)
  private static final double ESD_CLOSURE_TIME = 5.0; // seconds
  private static final double BLOWDOWN_OPENING_TIME = 5.0; // seconds

  /**
   * HIPPS controller implementing SIL-3 logic.
   */
  static class HIPPSController extends ControllerDeviceBaseClass {
    private PressureTransmitter pt1;
    private PressureTransmitter pt2;
    private ThrottlingValve hippsValve;
    private boolean activated = false;
    private boolean voting2oo2 = true; // 2 out of 2 voting for higher integrity

    public HIPPSController(String name, PressureTransmitter pt1, PressureTransmitter pt2,
        ThrottlingValve valve) {
      super(name);
      this.pt1 = pt1;
      this.pt2 = pt2;
      this.hippsValve = valve;
    }

    public void runController(double timeStep) {
      double p1 = pt1.getMeasuredValue();
      double p2 = pt2.getMeasuredValue();

      // 2oo2 voting logic (both must detect high pressure)
      if (voting2oo2) {
        if (p1 >= HIPPS_ACTIVATION_PRESSURE && p2 >= HIPPS_ACTIVATION_PRESSURE) {
          if (!activated) {
            System.out.println(">>> HIPPS ACTIVATED (SIL-3) - Both pressure sensors confirm <<<");
            activated = true;
          }
          hippsValve.setPercentValveOpening(0.0); // Close immediately
        }
      } else {
        // 1oo2 voting (either sensor can trigger - less safe but more available)
        if (p1 >= HIPPS_ACTIVATION_PRESSURE || p2 >= HIPPS_ACTIVATION_PRESSURE) {
          if (!activated) {
            System.out.println(">>> HIPPS ACTIVATED (SIL-3) - Pressure sensor triggered <<<");
            activated = true;
          }
          hippsValve.setPercentValveOpening(0.0);
        }
      }
    }

    public boolean isActivated() {
      return activated;
    }

    public void reset() {
      activated = false;
    }
  }

  /**
   * ESD controller implementing SIL-2 logic.
   */
  static class ESDController extends ControllerDeviceBaseClass {
    private PressureTransmitter pressureMonitor;
    private TemperatureTransmitter tempMonitor;
    private PushButton manualESD;
    private ThrottlingValve esdValve;
    private BlowdownValve blowdownValve;
    private boolean activated = false;

    public ESDController(String name, PressureTransmitter pt, TemperatureTransmitter tt,
        PushButton manual, ThrottlingValve esdValve, BlowdownValve bdValve) {
      super(name);
      this.pressureMonitor = pt;
      this.tempMonitor = tt;
      this.manualESD = manual;
      this.esdValve = esdValve;
      this.blowdownValve = bdValve;
    }

    public void runController(double timeStep) {
      double pressure = pressureMonitor.getMeasuredValue();
      double temperature = tempMonitor.getMeasuredValue();

      // ESD activation conditions (SIL-2)
      if (!activated) {
        if (pressure >= HIGH_HIGH_PRESSURE_ALARM) {
          System.out.println(">>> ESD ACTIVATED (SIL-2) - High-High Pressure <<<");
          activated = true;
        } else if (temperature >= FIRE_DETECTION_TEMPERATURE) {
          System.out.println(">>> ESD ACTIVATED (SIL-2) - Fire Detected <<<");
          activated = true;
        } else if (manualESD.isPushed()) {
          System.out.println(">>> ESD ACTIVATED (SIL-2) - Manual Push Button <<<");
          activated = true;
        }
      }

      if (activated) {
        esdValve.setPercentValveOpening(0.0); // Close inlet valve
        if (!blowdownValve.isActivated()) {
          blowdownValve.activate(); // Open blowdown valve
        }
      }
    }

    public boolean isActivated() {
      return activated;
    }

    public void reset() {
      activated = false;
    }
  }

  /**
   * Fire detection system.
   */
  static class FireDetectionSystem {
    private TemperatureTransmitter[] tempSensors;
    private boolean fireDetected = false;
    private int sensorVoting; // number of sensors that must detect fire

    public FireDetectionSystem(TemperatureTransmitter[] sensors, int voting) {
      this.tempSensors = sensors;
      this.sensorVoting = voting;
    }

    public void monitor() {
      int fireCount = 0;
      for (TemperatureTransmitter sensor : tempSensors) {
        if (sensor.getMeasuredValue() >= FIRE_DETECTION_TEMPERATURE) {
          fireCount++;
        }
      }

      if (fireCount >= sensorVoting && !fireDetected) {
        System.out.println(">>> FIRE DETECTED - " + fireCount + " sensors confirm <<<");
        fireDetected = true;
      }
    }

    public boolean isFireDetected() {
      return fireDetected;
    }
  }

  /**
   * Main method demonstrating integrated safety systems and scenarios.
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    System.out.println("╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║     INTEGRATED SAFETY SYSTEMS EXAMPLE                          ║");
    System.out.println("║     ESD + HIPPS + PSV + Flare + Monitoring                     ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    // Create high-pressure gas system
    SystemInterface feedGas = new SystemSrkEos(298.15, 70.0);
    feedGas.addComponent("nitrogen", 1.0);
    feedGas.addComponent("methane", 85.0);
    feedGas.addComponent("ethane", 10.0);
    feedGas.addComponent("propane", 3.0);
    feedGas.addComponent("n-butane", 1.0);
    feedGas.setMixingRule(2);

    Stream highPressureFeed = new Stream("HP Feed", feedGas);
    highPressureFeed.setFlowRate(15000.0, "kg/hr");
    highPressureFeed.setPressure(70.0, "bara");
    highPressureFeed.setTemperature(25.0, "C");

    // HIPPS valve (SIL-3 protection before separator)
    ThrottlingValve hippsValve = new ThrottlingValve("HIPPS-XV-001", highPressureFeed);
    hippsValve.setPercentValveOpening(100.0);
    hippsValve.setCv(800.0);

    Stream postHIPPS = new Stream("Post HIPPS", hippsValve.getOutletStream());

    // Pressure transmitters for HIPPS (redundant, 2oo2 voting)
    PressureTransmitter hippsPT1 = new PressureTransmitter("PT-101A", postHIPPS);
    PressureTransmitter hippsPT2 = new PressureTransmitter("PT-101B", postHIPPS);

    // ESD inlet valve
    ThrottlingValve esdInletValve = new ThrottlingValve("ESD-XV-201", postHIPPS);
    esdInletValve.setPercentValveOpening(100.0);
    esdInletValve.setCv(600.0);

    Stream separatorInlet = new Stream("Separator Inlet", esdInletValve.getOutletStream());

    // High-pressure separator
    Separator separator = new Separator("HP Separator", separatorInlet);
    separator.setCalculateSteadyState(true);
    separator.setInternalDiameter(2.0);

    Stream separatorGasOut = new Stream("Sep Gas Out", separator.getGasOutStream());

    // Pressure and temperature monitoring on separator
    PressureTransmitter separatorPT = new PressureTransmitter("PT-301", separatorGasOut);
    TemperatureTransmitter separatorTT = new TemperatureTransmitter("TT-301", separatorGasOut);

    // Temperature sensors for fire detection (3 sensors, 2oo3 voting)
    TemperatureTransmitter fireTT1 = new TemperatureTransmitter("TT-401A", separatorGasOut);
    TemperatureTransmitter fireTT2 = new TemperatureTransmitter("TT-401B", separatorGasOut);
    TemperatureTransmitter fireTT3 = new TemperatureTransmitter("TT-401C", separatorGasOut);

    // ESD manual push button
    PushButton esdButton = new PushButton("ESD-PB-201");

    // Splitter for process/blowdown routing
    Splitter gasSplitter = new Splitter("Gas Splitter", separatorGasOut, 2);
    gasSplitter.setSplitFactors(new double[] {1.0, 0.0});

    Stream processStream = new Stream("To Process", gasSplitter.getSplitStream(0));
    Stream blowdownStream = new Stream("To Blowdown", gasSplitter.getSplitStream(1));

    // Blowdown valve
    BlowdownValve bdValve = new BlowdownValve("BD-301", blowdownStream);
    bdValve.setOpeningTime(BLOWDOWN_OPENING_TIME);
    bdValve.setCv(250.0);

    Stream bdValveOutlet = new Stream("BD Valve Outlet", bdValve.getOutletStream());

    // Pressure Safety Valve (PSV) - mechanical protection
    SafetyValve psv = new SafetyValve("PSV-401", separatorGasOut);
    psv.setPressureSpec(PSV_SET_PRESSURE);
    psv.setFullOpenPressure(PSV_SET_PRESSURE + 2.0);
    psv.setBlowdown(7.0); // 7% blowdown

    Stream psvOutlet = new Stream("PSV Outlet", psv.getOutletStream());

    // Flare header mixer (combines blowdown and PSV relief)
    Mixer flareHeader = new Mixer("Flare Header");
    flareHeader.addStream(bdValveOutlet);
    flareHeader.addStream(psvOutlet);

    Stream flareHeaderOutlet = new Stream("Flare Header Outlet", flareHeader.getOutletStream());

    // Flare system
    Flare flare = new Flare("Emergency Flare", flareHeaderOutlet);
    flare.setFlameHeight(60.0);
    flare.setRadiantFraction(0.25);
    flare.setTipDiameter(1.0);

    // Initialize controllers
    HIPPSController hippsController =
        new HIPPSController("HIPPS-Logic-001", hippsPT1, hippsPT2, hippsValve);

    BlowdownValve bdValveForESD = bdValve; // Reference for ESD controller
    ESDController esdController = new ESDController("ESD-Logic-201", separatorPT, separatorTT,
        esdButton, esdInletValve, bdValveForESD);

    FireDetectionSystem fireSystem =
        new FireDetectionSystem(new TemperatureTransmitter[] {fireTT1, fireTT2, fireTT3}, 2);

    // Print system configuration
    printSystemConfiguration();

    // Run scenarios
    runScenario1_NormalOperation(highPressureFeed, hippsValve, esdInletValve, separatorInlet,
        separator, separatorGasOut, gasSplitter, processStream, blowdownStream, bdValve, psv,
        psvOutlet, flareHeader, flareHeaderOutlet, flare, hippsPT1, hippsPT2, separatorPT,
        separatorTT, hippsController, esdController, fireSystem);

    // Reset system
    resetSystem(hippsValve, esdInletValve, gasSplitter, bdValve, separator, hippsController,
        esdController, esdButton);

    runScenario2_HIPPSActivation(highPressureFeed, hippsValve, esdInletValve, separatorInlet,
        separator, separatorGasOut, gasSplitter, processStream, blowdownStream, bdValve, psv,
        psvOutlet, flareHeader, flareHeaderOutlet, flare, hippsPT1, hippsPT2, separatorPT,
        separatorTT, hippsController, esdController, fireSystem);

    // Reset system
    resetSystem(hippsValve, esdInletValve, gasSplitter, bdValve, separator, hippsController,
        esdController, esdButton);

    runScenario3_ESDAndBlowdown(highPressureFeed, hippsValve, esdInletValve, separatorInlet,
        separator, separatorGasOut, gasSplitter, processStream, blowdownStream, bdValve, psv,
        psvOutlet, flareHeader, flareHeaderOutlet, flare, hippsPT1, hippsPT2, separatorPT,
        separatorTT, hippsController, esdController, fireSystem, esdButton);

    // Reset system
    resetSystem(hippsValve, esdInletValve, gasSplitter, bdValve, separator, hippsController,
        esdController, esdButton);

    runScenario4_PSVRelief(highPressureFeed, hippsValve, esdInletValve, separatorInlet, separator,
        separatorGasOut, gasSplitter, processStream, blowdownStream, bdValve, psv, psvOutlet,
        flareHeader, flareHeaderOutlet, flare, hippsPT1, hippsPT2, separatorPT, separatorTT,
        hippsController, esdController, fireSystem);

    System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║     ALL SAFETY SCENARIOS COMPLETED                             ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝");
  }

  /**
   * Print system configuration and safety setpoints.
   */
  private static void printSystemConfiguration() {
    System.out.println("═══ SYSTEM CONFIGURATION ═══");
    System.out.println("Feed: 15000 kg/hr at 70 bara");
    System.out.println("Normal operating pressure: " + NORMAL_OPERATING_PRESSURE + " bara");
    System.out.println();
    System.out.println("SAFETY LAYERS (Defense in Depth):");
    System.out.println("1. High Pressure Alarm (SIL-1): " + HIGH_PRESSURE_ALARM + " bara");
    System.out
        .println("2. High-High Pressure / ESD (SIL-2): " + HIGH_HIGH_PRESSURE_ALARM + " bara");
    System.out.println("3. HIPPS Protection (SIL-3): " + HIPPS_ACTIVATION_PRESSURE + " bara");
    System.out.println("4. PSV Relief (Mechanical): " + PSV_SET_PRESSURE + " bara");
    System.out.println();
    System.out.println("FIRE PROTECTION:");
    System.out.println("Fire detection temperature: " + FIRE_DETECTION_TEMPERATURE + " C");
    System.out.println("Fire sensors: 3 (2oo3 voting)");
    System.out.println();
  }

  /**
   * Scenario 1: Normal operation with monitoring.
   *
   * @param highPressureFeed high pressure feed stream
   * @param hippsValve HIPPS valve
   * @param esdInletValve ESD inlet valve
   * @param separatorInlet separator inlet stream
   * @param separator separator equipment
   * @param separatorGasOut separator gas outlet stream
   * @param gasSplitter gas splitter
   * @param processStream process stream
   * @param blowdownStream blowdown stream
   * @param bdValve blowdown valve
   * @param psv pressure safety valve
   * @param psvOutlet PSV outlet stream
   * @param flareHeader flare header mixer
   * @param flareHeaderOutlet flare header outlet stream
   * @param flare flare equipment
   * @param hippsPT1 HIPPS pressure transmitter 1
   * @param hippsPT2 HIPPS pressure transmitter 2
   * @param separatorPT separator pressure transmitter
   * @param separatorTT separator temperature transmitter
   * @param hippsController HIPPS controller
   * @param esdController ESD controller
   * @param fireSystem fire detection system
   */
  private static void runScenario1_NormalOperation(Stream highPressureFeed,
      ThrottlingValve hippsValve, ThrottlingValve esdInletValve, Stream separatorInlet,
      Separator separator, Stream separatorGasOut, Splitter gasSplitter, Stream processStream,
      Stream blowdownStream, BlowdownValve bdValve, SafetyValve psv, Stream psvOutlet,
      Mixer flareHeader, Stream flareHeaderOutlet, Flare flare, PressureTransmitter hippsPT1,
      PressureTransmitter hippsPT2, PressureTransmitter separatorPT,
      TemperatureTransmitter separatorTT, HIPPSController hippsController,
      ESDController esdController, FireDetectionSystem fireSystem) {

    System.out.println("╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║     SCENARIO 1: NORMAL OPERATION                               ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    // Set normal inlet pressure
    highPressureFeed.setPressure(55.0, "bara");

    // Run system
    runSystem(highPressureFeed, hippsValve, esdInletValve, separatorInlet, separator,
        separatorGasOut, gasSplitter, processStream, blowdownStream, bdValve, psv, psvOutlet,
        flareHeader, flareHeaderOutlet, flare);

    // Run controllers
    hippsController.runController(1.0);
    esdController.runController(1.0);
    fireSystem.monitor();

    // Report status
    System.out.println("═══ NORMAL OPERATION STATUS ═══");
    System.out.printf("Feed pressure: %.1f bara%n", highPressureFeed.getPressure("bara"));
    System.out.printf("HIPPS PT-101A: %.2f bara%n", hippsPT1.getMeasuredValue());
    System.out.printf("HIPPS PT-101B: %.2f bara%n", hippsPT2.getMeasuredValue());
    System.out.printf("Separator pressure (PT-301): %.2f bara%n", separatorPT.getMeasuredValue());
    System.out.printf("Separator temperature (TT-301): %.1f C%n", separatorTT.getMeasuredValue());
    System.out.printf("Process flow: %.1f kg/hr%n", processStream.getFlowRate("kg/hr"));
    System.out.printf("HIPPS status: %s%n", hippsController.isActivated() ? "ACTIVATED" : "NORMAL");
    System.out.printf("ESD status: %s%n", esdController.isActivated() ? "ACTIVATED" : "NORMAL");
    System.out.printf("Fire detection: %s%n", fireSystem.isFireDetected() ? "FIRE" : "NORMAL");
    System.out.printf("PSV status: %s (%.1f%% open)%n",
        psv.getPercentValveOpening() > 0 ? "RELIEVING" : "CLOSED", psv.getPercentValveOpening());
    System.out.println("\n✓ All systems operating normally\n");
  }

  /**
   * Scenario 2: HIPPS activation due to high pressure.
   *
   * @param highPressureFeed High pressure feed stream
   * @param hippsValve HIPPS valve
   * @param esdInletValve ESD inlet valve
   * @param separatorInlet Separator inlet stream
   * @param separator Separator vessel
   * @param separatorGasOut Separator gas outlet
   * @param gasSplitter Gas stream splitter
   * @param processStream Process stream
   * @param blowdownStream Blowdown stream
   * @param bdValve Blowdown valve
   * @param psv Pressure safety valve
   * @param psvOutlet PSV outlet stream
   * @param flareHeader Flare header mixer
   * @param flareHeaderOutlet Flare header outlet
   * @param flare Flare system
   * @param hippsPT1 HIPPS pressure transmitter 1
   * @param hippsPT2 HIPPS pressure transmitter 2
   * @param separatorPT Separator pressure transmitter
   * @param separatorTT Separator temperature transmitter
   * @param hippsController HIPPS controller
   * @param esdController ESD controller
   * @param fireSystem Fire and gas detection system
   */
  private static void runScenario2_HIPPSActivation(Stream highPressureFeed,
      ThrottlingValve hippsValve, ThrottlingValve esdInletValve, Stream separatorInlet,
      Separator separator, Stream separatorGasOut, Splitter gasSplitter, Stream processStream,
      Stream blowdownStream, BlowdownValve bdValve, SafetyValve psv, Stream psvOutlet,
      Mixer flareHeader, Stream flareHeaderOutlet, Flare flare, PressureTransmitter hippsPT1,
      PressureTransmitter hippsPT2, PressureTransmitter separatorPT,
      TemperatureTransmitter separatorTT, HIPPSController hippsController,
      ESDController esdController, FireDetectionSystem fireSystem) {

    System.out.println("╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║     SCENARIO 2: HIPPS ACTIVATION (SIL-3)                       ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    // Simulate pressure surge
    System.out.println(">>> SIMULATING PRESSURE SURGE <<<\n");
    highPressureFeed.setPressure(70.0, "bara");

    System.out.println("Time (s) | Feed P | HIPPS Valve | Sep P | HIPPS Status | ESD Status");
    System.out.println("---------|--------|-------------|-------|--------------|------------");

    for (double time = 0; time <= 15.0; time += 1.0) {
      // Gradually close HIPPS valve if activated
      if (hippsController.isActivated() && hippsValve.getPercentValveOpening() > 0) {
        double newOpening =
            Math.max(0, hippsValve.getPercentValveOpening() - 100.0 / HIPPS_CLOSURE_TIME);
        hippsValve.setPercentValveOpening(newOpening);
      }

      runSystem(highPressureFeed, hippsValve, esdInletValve, separatorInlet, separator,
          separatorGasOut, gasSplitter, processStream, blowdownStream, bdValve, psv, psvOutlet,
          flareHeader, flareHeaderOutlet, flare);

      hippsController.runController(1.0);
      esdController.runController(1.0);

      System.out.printf("%8.1f | %6.1f | %10.1f%% | %5.1f | %12s | %10s%n", time,
          highPressureFeed.getPressure("bara"), hippsValve.getPercentValveOpening(),
          separatorPT.getMeasuredValue(), hippsController.isActivated() ? "ACTIVATED" : "NORMAL",
          esdController.isActivated() ? "ACTIVATED" : "NORMAL");
    }

    System.out.println("\n✓ HIPPS successfully prevented overpressure (SIL-3 protection)\n");
  }

  /**
   * Scenario 3: ESD activation and blowdown.
   *
   * @param highPressureFeed High pressure feed stream
   * @param hippsValve HIPPS valve
   * @param esdInletValve ESD inlet valve
   * @param separatorInlet Separator inlet stream
   * @param separator Separator vessel
   * @param separatorGasOut Separator gas outlet
   * @param gasSplitter Gas stream splitter
   * @param processStream Process stream
   * @param blowdownStream Blowdown stream
   * @param bdValve Blowdown valve
   * @param psv Pressure safety valve
   * @param psvOutlet PSV outlet stream
   * @param flareHeader Flare header mixer
   * @param flareHeaderOutlet Flare header outlet
   * @param flare Flare system
   * @param hippsPT1 HIPPS pressure transmitter 1
   * @param hippsPT2 HIPPS pressure transmitter 2
   * @param separatorPT Separator pressure transmitter
   * @param separatorTT Separator temperature transmitter
   * @param hippsController HIPPS controller
   * @param esdController ESD controller
   * @param fireSystem Fire and gas detection system
   * @param esdButton ESD push button
   */
  private static void runScenario3_ESDAndBlowdown(Stream highPressureFeed,
      ThrottlingValve hippsValve, ThrottlingValve esdInletValve, Stream separatorInlet,
      Separator separator, Stream separatorGasOut, Splitter gasSplitter, Stream processStream,
      Stream blowdownStream, BlowdownValve bdValve, SafetyValve psv, Stream psvOutlet,
      Mixer flareHeader, Stream flareHeaderOutlet, Flare flare, PressureTransmitter hippsPT1,
      PressureTransmitter hippsPT2, PressureTransmitter separatorPT,
      TemperatureTransmitter separatorTT, HIPPSController hippsController,
      ESDController esdController, FireDetectionSystem fireSystem, PushButton esdButton) {

    System.out.println("╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║     SCENARIO 3: ESD ACTIVATION & BLOWDOWN (SIL-2)              ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    // Start at normal pressure
    highPressureFeed.setPressure(55.0, "bara");

    // Initialize system
    runSystem(highPressureFeed, hippsValve, esdInletValve, separatorInlet, separator,
        separatorGasOut, gasSplitter, processStream, blowdownStream, bdValve, psv, psvOutlet,
        flareHeader, flareHeaderOutlet, flare);

    // Operator pushes ESD button
    System.out.println(">>> OPERATOR ACTIVATES MANUAL ESD <<<\n");
    esdButton.push();

    // Switch to blowdown routing
    gasSplitter.setSplitFactors(new double[] {0.0, 1.0});

    // Switch separator to transient mode
    separator.setCalculateSteadyState(false);

    System.out
        .println("Time (s) | Sep P | ESD Valve | BD Valve | BD Flow | Flare Heat | Cum Gas BD");
    System.out
        .println("---------|-------|-----------|----------|---------|------------|------------");

    double timeStep = 1.0;
    for (double time = 0; time <= 30.0; time += timeStep) {
      // Run ESD controller
      esdController.runController(timeStep);

      // Gradually close ESD valve
      if (esdController.isActivated() && esdInletValve.getPercentValveOpening() > 0) {
        double newOpening =
            Math.max(0, esdInletValve.getPercentValveOpening() - 100.0 / ESD_CLOSURE_TIME);
        esdInletValve.setPercentValveOpening(newOpening);
      }

      // Control feed based on ESD valve
      if (esdInletValve.getPercentValveOpening() < 1.0) {
        separatorInlet.getThermoSystem().setTotalFlowRate(0.1, "kg/hr");
      }

      // Run system
      highPressureFeed.run();
      hippsValve.run();
      esdInletValve.run();
      separatorInlet.run();
      separator.runTransient(timeStep, java.util.UUID.randomUUID());
      separatorGasOut.run();
      gasSplitter.run();
      blowdownStream.run();
      bdValve.runTransient(timeStep, java.util.UUID.randomUUID());
      psv.run();
      psvOutlet.run();
      flareHeader.run();
      flareHeaderOutlet.run();
      flare.run();
      flare.updateCumulative(timeStep);

      System.out.printf("%8.1f | %5.1f | %8.1f%% | %7.1f%% | %7.0f | %9.2f | %10.1f%n", time,
          separatorPT.getMeasuredValue(), esdInletValve.getPercentValveOpening(),
          bdValve.getPercentValveOpening(), blowdownStream.getFlowRate("kg/hr"),
          flare.getHeatDuty("MW"), flare.getCumulativeGasBurned("kg"));
    }

    System.out.println("\n═══ ESD & BLOWDOWN SUMMARY ═══");
    System.out.printf("Total gas blown down: %.1f kg%n", flare.getCumulativeGasBurned("kg"));
    System.out.printf("Total heat released: %.2f GJ%n", flare.getCumulativeHeatReleased("GJ"));
    System.out.printf("Final separator pressure: %.1f bara%n", separatorPT.getMeasuredValue());
    System.out.println("\n✓ ESD and blowdown completed successfully (SIL-2 protection)\n");
  }

  /**
   * Scenario 4: PSV relief (final protection layer).
   */
  private static void runScenario4_PSVRelief(Stream highPressureFeed, ThrottlingValve hippsValve,
      ThrottlingValve esdInletValve, Stream separatorInlet, Separator separator,
      Stream separatorGasOut, Splitter gasSplitter, Stream processStream, Stream blowdownStream,
      BlowdownValve bdValve, SafetyValve psv, Stream psvOutlet, Mixer flareHeader,
      Stream flareHeaderOutlet, Flare flare, PressureTransmitter hippsPT1,
      PressureTransmitter hippsPT2, PressureTransmitter separatorPT,
      TemperatureTransmitter separatorTT, HIPPSController hippsController,
      ESDController esdController, FireDetectionSystem fireSystem) {

    System.out.println("╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║     SCENARIO 4: PSV RELIEF (FINAL PROTECTION)                  ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    System.out.println(">>> SIMULATING EXTREME OVERPRESSURE (HIPPS/ESD FAILED) <<<\n");

    // Simulate extreme pressure to trigger PSV
    highPressureFeed.setPressure(75.0, "bara");

    System.out.println("Time (s) | Sep P | PSV Status | PSV Flow | Flare Heat");
    System.out.println("---------|-------|------------|----------|------------");

    for (double time = 0; time <= 20.0; time += 1.0) {
      runSystem(highPressureFeed, hippsValve, esdInletValve, separatorInlet, separator,
          separatorGasOut, gasSplitter, processStream, blowdownStream, bdValve, psv, psvOutlet,
          flareHeader, flareHeaderOutlet, flare);

      System.out.printf("%8.1f | %5.1f | %10s | %8.0f | %10.2f%n", time,
          separatorPT.getMeasuredValue(), psv.getPercentValveOpening() > 0 ? "RELIEVING" : "CLOSED",
          psvOutlet.getFlowRate("kg/hr"), flare.getHeatDuty("MW"));
    }

    System.out.println("\n✓ PSV provided final mechanical protection\n");
  }

  /**
   * Run all equipment in the system.
   */
  private static void runSystem(Stream highPressureFeed, ThrottlingValve hippsValve,
      ThrottlingValve esdInletValve, Stream separatorInlet, Separator separator,
      Stream separatorGasOut, Splitter gasSplitter, Stream processStream, Stream blowdownStream,
      BlowdownValve bdValve, SafetyValve psv, Stream psvOutlet, Mixer flareHeader,
      Stream flareHeaderOutlet, Flare flare) {

    highPressureFeed.run();
    hippsValve.run();
    esdInletValve.run();
    separatorInlet.run();
    separator.run();
    separatorGasOut.run();
    gasSplitter.run();
    processStream.run();
    blowdownStream.run();
    bdValve.run();
    psv.run();
    psvOutlet.run();
    flareHeader.run();
    flareHeaderOutlet.run();
    flare.run();
  }

  /**
   * Reset system to normal operation.
   */
  private static void resetSystem(ThrottlingValve hippsValve, ThrottlingValve esdInletValve,
      Splitter gasSplitter, BlowdownValve bdValve, Separator separator,
      HIPPSController hippsController, ESDController esdController, PushButton esdButton) {

    System.out.println(">>> RESETTING SYSTEM FOR NEXT SCENARIO <<<\n");
    hippsValve.setPercentValveOpening(100.0);
    esdInletValve.setPercentValveOpening(100.0);
    gasSplitter.setSplitFactors(new double[] {1.0, 0.0});
    bdValve.reset();
    separator.setCalculateSteadyState(true);
    hippsController.reset();
    esdController.reset();
    esdButton.reset();
  }
}
