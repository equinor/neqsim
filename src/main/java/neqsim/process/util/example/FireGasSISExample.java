package neqsim.process.util.example;

import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.valve.BlowdownValve;
import neqsim.process.equipment.valve.ControlValve;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.flare.Flare;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.logic.sis.SafetyInstrumentedFunction;
import neqsim.process.logic.sis.Detector;
import neqsim.process.logic.sis.Detector.AlarmLevel;
import neqsim.process.logic.sis.Detector.DetectorType;
import neqsim.process.logic.sis.VotingLogic;
import neqsim.process.logic.action.TripValveAction;
import neqsim.process.logic.action.ActivateBlowdownAction;
import neqsim.process.logic.action.SetSplitterAction;
import neqsim.process.measurementdevice.PushButton;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Example demonstrating Safety Instrumented System (SIS) for fire and gas detection.
 * 
 * <p>
 * This example implements IEC 61511 compliant safety logic including:
 * <ul>
 * <li>Fire detection with 2oo3 voting (2 out of 3 detectors must trip)</li>
 * <li>Gas detection with 2oo3 voting</li>
 * <li>Automatic ESD activation when SIF trips</li>
 * <li>Detector bypass capability (max 1 bypassed)</li>
 * <li>Manual push button override</li>
 * <li>Coordinated valve operations</li>
 * </ul>
 * 
 * <p>
 * Safety Integrity Level (SIL) considerations:
 * <ul>
 * <li>2oo3 voting provides good balance of safety and availability</li>
 * <li>One detector can be bypassed for maintenance without compromising safety</li>
 * <li>Redundant fire and gas detection for high hazard scenarios</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class FireGasSISExample {

  /**
   * Main method to run the fire and gas SIS example.
   *
   * @param args command line arguments (not used)
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    System.out.println("╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║    SAFETY INSTRUMENTED SYSTEM (SIS) - FIRE & GAS DETECTION    ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    // ═══════════════════════════════════════════════════════════════
    // PROCESS EQUIPMENT SETUP
    // ═══════════════════════════════════════════════════════════════

    SystemInterface separatorGas = new SystemSrkEos(298.15, 50.0);
    separatorGas.addComponent("nitrogen", 1.0);
    separatorGas.addComponent("methane", 85.0);
    separatorGas.addComponent("ethane", 10.0);
    separatorGas.addComponent("propane", 3.0);
    separatorGas.addComponent("n-butane", 1.0);
    separatorGas.setMixingRule(2);

    Stream feedStream = new Stream("Feed", separatorGas);
    feedStream.setFlowRate(10000.0, "kg/hr");
    feedStream.setPressure(50.0, "bara");
    feedStream.setTemperature(25.0, "C");

    ControlValve controlValve = new ControlValve("FCV-101", feedStream);
    controlValve.setPercentValveOpening(50.0);
    controlValve.setCv(300.0);
    controlValve.setOutletPressure(48.0);

    Stream afterControlValve = new Stream("After Control Valve", controlValve.getOutletStream());

    ESDValve esdInletValve = new ESDValve("ESD-XV-101", afterControlValve);
    esdInletValve.setStrokeTime(5.0);
    esdInletValve.setCv(500.0);
    esdInletValve.energize();

    Stream separatorInlet = new Stream("Separator Inlet", esdInletValve.getOutletStream());

    Separator separator = new Separator("HP Separator", separatorInlet);
    separator.setCalculateSteadyState(true);

    Stream separatorGasOut = new Stream("Sep Gas Out", separator.getGasOutStream());

    Splitter gasSplitter = new Splitter("Gas Splitter", separatorGasOut, 2);
    gasSplitter.setSplitFactors(new double[] {1.0, 0.0});

    Stream processStream = new Stream("To Process", gasSplitter.getSplitStream(0));
    Stream blowdownStream = new Stream("To Blowdown", gasSplitter.getSplitStream(1));

    BlowdownValve bdValve = new BlowdownValve("BD-101", blowdownStream);
    bdValve.setOpeningTime(5.0);
    bdValve.setCv(200.0);

    Stream bdValveOutlet = new Stream("BD Valve Outlet", bdValve.getOutletStream());

    Mixer flareHeader = new Mixer("Flare Header");
    flareHeader.addStream(bdValveOutlet);

    Stream flareHeaderOutlet = new Stream("Flare Header Outlet", flareHeader.getOutletStream());

    Flare flare = new Flare("Blowdown Flare", flareHeaderOutlet);

    // ═══════════════════════════════════════════════════════════════
    // SAFETY INSTRUMENTED SYSTEM (SIS) SETUP
    // ═══════════════════════════════════════════════════════════════

    // Fire Detection SIF (2oo3 voting)
    SafetyInstrumentedFunction fireSIF =
        new SafetyInstrumentedFunction("Fire Detection SIF", VotingLogic.TWO_OUT_OF_THREE);

    Detector fireDetector1 = new Detector("FD-101", DetectorType.FIRE, AlarmLevel.HIGH, 60.0, "°C");
    Detector fireDetector2 = new Detector("FD-102", DetectorType.FIRE, AlarmLevel.HIGH, 60.0, "°C");
    Detector fireDetector3 = new Detector("FD-103", DetectorType.FIRE, AlarmLevel.HIGH, 60.0, "°C");

    fireSIF.addDetector(fireDetector1);
    fireSIF.addDetector(fireDetector2);
    fireSIF.addDetector(fireDetector3);

    // Gas Detection SIF (2oo3 voting)
    SafetyInstrumentedFunction gasSIF =
        new SafetyInstrumentedFunction("Gas Detection SIF", VotingLogic.TWO_OUT_OF_THREE);

    Detector gasDetector1 =
        new Detector("GD-101", DetectorType.GAS, AlarmLevel.HIGH_HIGH, 25.0, "% LEL");
    Detector gasDetector2 =
        new Detector("GD-102", DetectorType.GAS, AlarmLevel.HIGH_HIGH, 25.0, "% LEL");
    Detector gasDetector3 =
        new Detector("GD-103", DetectorType.GAS, AlarmLevel.HIGH_HIGH, 25.0, "% LEL");

    gasSIF.addDetector(gasDetector1);
    gasSIF.addDetector(gasDetector2);
    gasSIF.addDetector(gasDetector3);

    // ESD Logic Sequence
    ESDLogic esdLogic = new ESDLogic("ESD Level 1");
    esdLogic.addAction(new TripValveAction(esdInletValve), 0.0);
    esdLogic.addAction(new ActivateBlowdownAction(bdValve), 0.5);
    esdLogic.addAction(new SetSplitterAction(gasSplitter, new double[] {0.0, 1.0}), 0.0);

    // Link SIFs to ESD logic
    fireSIF.linkToLogic(esdLogic);
    gasSIF.linkToLogic(esdLogic);

    // Manual push button
    PushButton esdButton = new PushButton("ESD-PB-101");
    esdButton.linkToLogic(esdLogic);

    // ═══════════════════════════════════════════════════════════════
    // DISPLAY SYSTEM CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    System.out.println("═══ SYSTEM CONFIGURATION ═══");
    System.out.println("Separator: HP Separator at 50 bara");
    System.out.println("Gas flow rate: 10000 kg/hr");
    System.out.println();

    System.out.println("SAFETY INSTRUMENTED FUNCTIONS:");
    System.out.println("1. Fire Detection SIF:");
    System.out.println("   - Voting: " + fireSIF.getVotingLogic());
    System.out.println("   - Detectors: FD-101, FD-102, FD-103");
    System.out.println("   - Setpoint: 60°C (High)");
    System.out.println();

    System.out.println("2. Gas Detection SIF:");
    System.out.println("   - Voting: " + gasSIF.getVotingLogic());
    System.out.println("   - Detectors: GD-101, GD-102, GD-103");
    System.out.println("   - Setpoint: 25% LEL (High-High)");
    System.out.println();

    System.out.println("ESD LOGIC ACTIONS:");
    System.out.println("  1. Trip inlet valve ESD-XV-101 (immediate)");
    System.out.println("  2. Activate blowdown valve BD-101 (0.5s delay)");
    System.out.println("  3. Redirect flow to blowdown (0.0s delay)");
    System.out.println();

    // Run initial steady state
    feedStream.run();
    controlValve.run();
    afterControlValve.run();
    esdInletValve.run();
    separatorInlet.run();
    separator.run();
    separatorGasOut.run();
    gasSplitter.run();
    processStream.run();
    blowdownStream.run();
    bdValve.run();

    // ═══════════════════════════════════════════════════════════════
    // SCENARIO 1: NORMAL OPERATION
    // ═══════════════════════════════════════════════════════════════

    System.out.println("═══ SCENARIO 1: NORMAL OPERATION ═══");
    fireSIF.update(25.0, 27.0, 26.0); // Normal temperatures
    gasSIF.update(0.0, 0.5, 0.3); // No gas detected

    System.out.println(fireSIF.getStatusDescription());
    for (Detector det : fireSIF.getDetectors()) {
      System.out.println("  " + det.toString());
    }
    System.out.println();

    System.out.println(gasSIF.getStatusDescription());
    for (Detector det : gasSIF.getDetectors()) {
      System.out.println("  " + det.toString());
    }
    System.out.println();

    System.out.printf("ESD Logic: %s%n", esdLogic.getStatusDescription());
    System.out.printf("Process flow: %.1f kg/hr%n", processStream.getFlowRate("kg/hr"));
    System.out.println();

    // ═══════════════════════════════════════════════════════════════
    // SCENARIO 2: SINGLE FIRE DETECTOR TRIP (NO ESD)
    // ═══════════════════════════════════════════════════════════════

    System.out.println("═══ SCENARIO 2: SINGLE FIRE DETECTOR TRIP (1/3) ═══");
    System.out.println(">>> FD-101 detects elevated temperature <<<");
    fireSIF.update(75.0, 27.0, 26.0); // Only FD-101 above setpoint

    System.out.println(fireSIF.getStatusDescription());
    System.out.println("  FD-101: " + (fireDetector1.isTripped() ? "TRIPPED" : "NORMAL"));
    System.out.println("  FD-102: " + (fireDetector2.isTripped() ? "TRIPPED" : "NORMAL"));
    System.out.println("  FD-103: " + (fireDetector3.isTripped() ? "TRIPPED" : "NORMAL"));
    System.out.println("SIF Status: "
        + (fireSIF.isTripped() ? "TRIPPED (ESD ACTIVATED)" : "NOT TRIPPED (Monitoring)"));
    System.out.println("Voting requires 2/3 detectors - ESD NOT activated");
    System.out.println();

    // ═══════════════════════════════════════════════════════════════
    // SCENARIO 3: FIRE DETECTED - 2oo3 VOTING SATISFIED
    // ═══════════════════════════════════════════════════════════════

    System.out.println("═══ SCENARIO 3: FIRE DETECTED - 2oo3 VOTING SATISFIED ═══");
    System.out.println(">>> FD-101 and FD-102 detect fire <<<");
    fireSIF.update(75.0, 82.0, 26.0); // FD-101 and FD-102 above setpoint

    System.out.println(fireSIF.getStatusDescription());
    System.out.println("  FD-101: " + (fireDetector1.isTripped() ? "TRIPPED" : "NORMAL"));
    System.out.println("  FD-102: " + (fireDetector2.isTripped() ? "TRIPPED" : "NORMAL"));
    System.out.println("  FD-103: " + (fireDetector3.isTripped() ? "TRIPPED" : "NORMAL"));
    System.out.println(
        "SIF Status: " + (fireSIF.isTripped() ? "TRIPPED - ESD ACTIVATED" : "NOT TRIPPED"));
    System.out.printf("ESD Logic: %s%n", esdLogic.getStatusDescription());
    System.out.println();

    // Switch to dynamic mode
    separator.setCalculateSteadyState(false);

    // Simulate ESD execution
    System.out.println("═══ ESD SEQUENCE EXECUTION ═══");
    System.out.println(
        "Time (s) | Fire SIF | Gas SIF  | ESD Step | ESD Valve (%) | BD Valve (%) | Process Flow");
    System.out.println(
        "---------|----------|----------|----------|---------------|--------------|-------------");

    double timeStep = 1.0;
    double totalTime = 8.0;

    for (double time = 0.0; time <= totalTime; time += timeStep) {
      // Continue monitoring (temperatures gradually increasing)
      double temp1 = 75.0 + time * 2.0;
      double temp2 = 82.0 + time * 1.5;
      double temp3 = 26.0 + time * 3.0; // FD-103 also starts detecting
      fireSIF.update(temp1, temp2, temp3);
      gasSIF.update(0.0, 0.5, 0.3); // No gas

      // Execute ESD logic
      esdLogic.execute(timeStep);

      // Run equipment transients
      esdInletValve.runTransient(timeStep, java.util.UUID.randomUUID());

      if (esdInletValve.getPercentValveOpening() < 1.0) {
        separatorInlet.getThermoSystem().setTotalFlowRate(0.1, "kg/hr");
      } else {
        feedStream.run();
        controlValve.run();
        afterControlValve.run();
        esdInletValve.run();
        separatorInlet.run();
      }

      separator.runTransient(timeStep, java.util.UUID.randomUUID());
      separatorGasOut.run();
      gasSplitter.run();
      blowdownStream.run();
      bdValve.runTransient(timeStep, java.util.UUID.randomUUID());
      processStream.run();

      String fireStatus = fireSIF.isTripped() ? "TRIPPED" : "NORMAL ";
      String gasStatus = gasSIF.isTripped() ? "TRIPPED" : "NORMAL ";
      String esdStep =
          esdLogic.isComplete() ? "DONE" : "Step " + (esdLogic.getCurrentActionIndex() + 1) + "/3";

      System.out.printf("%8.1f | %8s | %8s | %8s | %13.1f | %12.1f | %12.1f%n", time, fireStatus,
          gasStatus, esdStep, esdInletValve.getPercentValveOpening(),
          bdValve.getPercentValveOpening(), processStream.getFlowRate("kg/hr"));
    }

    System.out.println();

    // ═══════════════════════════════════════════════════════════════
    // SCENARIO 4: GAS DETECTION WITH BYPASS
    // ═══════════════════════════════════════════════════════════════

    System.out.println("═══ SCENARIO 4: DETECTOR BYPASS CAPABILITY ═══");
    System.out.println(">>> GD-101 bypassed for maintenance <<<");

    // Reset for new scenario
    fireSIF.reset();
    gasSIF.reset();
    esdLogic.reset();

    gasDetector1.setBypass(true); // Bypass GD-101
    gasSIF.update(0.0, 30.0, 32.0); // GD-102 and GD-103 detect gas

    System.out.println("Gas detectors status:");
    System.out.println("  GD-101: " + (gasDetector1.isBypassed() ? "BYPASSED" : "ACTIVE") + " ("
        + (gasDetector1.isTripped() ? "TRIPPED" : "NORMAL") + ")");
    System.out.println("  GD-102: " + (gasDetector2.isBypassed() ? "BYPASSED" : "ACTIVE") + " ("
        + (gasDetector2.isTripped() ? "TRIPPED" : "NORMAL") + ")");
    System.out.println("  GD-103: " + (gasDetector3.isBypassed() ? "BYPASSED" : "ACTIVE") + " ("
        + (gasDetector3.isTripped() ? "TRIPPED" : "NORMAL") + ")");
    System.out.println();

    System.out.println(gasSIF.getStatusDescription());
    System.out.println("Note: With 1 bypassed, 2oo3 becomes effectively 2oo2");
    System.out.println("SIF still provides adequate safety: "
        + (gasSIF.isTripped() ? "YES (ESD activated)" : "NO"));
    System.out.println();

    // ═══════════════════════════════════════════════════════════════
    // FINAL SUMMARY
    // ═══════════════════════════════════════════════════════════════

    System.out.println("═══ SAFETY SYSTEM SUMMARY ═══");
    System.out.println();
    System.out.println("KEY FEATURES DEMONSTRATED:");
    System.out.println("✓ 2oo3 voting logic for fire detection");
    System.out.println("✓ 2oo3 voting logic for gas detection");
    System.out.println("✓ Automatic ESD activation on SIF trip");
    System.out.println("✓ Detector bypass capability (1 allowed without compromising safety)");
    System.out.println("✓ Coordinated multi-valve shutdown sequence");
    System.out.println("✓ IEC 61511 compliant safety architecture");
    System.out.println();

    System.out.println("SAFETY INTEGRITY:");
    System.out.println("• Redundant detection reduces spurious trips");
    System.out.println("• One detector can fail without losing safety function");
    System.out.println("• Maintenance possible with one detector bypassed");
    System.out.println("• Multiple independent SIFs for different hazards");
    System.out.println();

    System.out.println("╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║         FIRE & GAS SIS EXAMPLE COMPLETED                       ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝");
  }
}
