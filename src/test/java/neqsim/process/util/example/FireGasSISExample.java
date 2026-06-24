package neqsim.process.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
  private static final Logger logger = LogManager.getLogger(FireGasSISExample.class);

  /**
   * Main method to run the fire and gas SIS example.
   *
   * @param args command line arguments (not used)
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    logger.info("╔════════════════════════════════════════════════════════════════╗");
    logger.info("║    SAFETY INSTRUMENTED SYSTEM (SIS) - FIRE & GAS DETECTION    ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝\n");

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
    gasSplitter.setSplitFactors(new double[] { 1.0, 0.0 });

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
    SafetyInstrumentedFunction fireSIF = new SafetyInstrumentedFunction("Fire Detection SIF",
        VotingLogic.TWO_OUT_OF_THREE);

    Detector fireDetector1 = new Detector("FD-101", DetectorType.FIRE, AlarmLevel.HIGH, 60.0, "°C");
    Detector fireDetector2 = new Detector("FD-102", DetectorType.FIRE, AlarmLevel.HIGH, 60.0, "°C");
    Detector fireDetector3 = new Detector("FD-103", DetectorType.FIRE, AlarmLevel.HIGH, 60.0, "°C");

    fireSIF.addDetector(fireDetector1);
    fireSIF.addDetector(fireDetector2);
    fireSIF.addDetector(fireDetector3);

    // Gas Detection SIF (2oo3 voting)
    SafetyInstrumentedFunction gasSIF = new SafetyInstrumentedFunction("Gas Detection SIF",
        VotingLogic.TWO_OUT_OF_THREE);

    Detector gasDetector1 = new Detector("GD-101", DetectorType.GAS, AlarmLevel.HIGH_HIGH, 25.0, "% LEL");
    Detector gasDetector2 = new Detector("GD-102", DetectorType.GAS, AlarmLevel.HIGH_HIGH, 25.0, "% LEL");
    Detector gasDetector3 = new Detector("GD-103", DetectorType.GAS, AlarmLevel.HIGH_HIGH, 25.0, "% LEL");

    gasSIF.addDetector(gasDetector1);
    gasSIF.addDetector(gasDetector2);
    gasSIF.addDetector(gasDetector3);

    // ESD Logic Sequence
    ESDLogic esdLogic = new ESDLogic("ESD Level 1");
    esdLogic.addAction(new TripValveAction(esdInletValve), 0.0);
    esdLogic.addAction(new ActivateBlowdownAction(bdValve), 0.5);
    esdLogic.addAction(new SetSplitterAction(gasSplitter, new double[] { 0.0, 1.0 }), 0.0);

    // Link SIFs to ESD logic
    fireSIF.linkToLogic(esdLogic);
    gasSIF.linkToLogic(esdLogic);

    // Manual push button
    PushButton esdButton = new PushButton("ESD-PB-101");
    esdButton.linkToLogic(esdLogic);

    // ═══════════════════════════════════════════════════════════════
    // DISPLAY SYSTEM CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    logger.info("═══ SYSTEM CONFIGURATION ═══");
    logger.info("Separator: HP Separator at 50 bara");
    logger.info("Gas flow rate: 10000 kg/hr");

    logger.info("SAFETY INSTRUMENTED FUNCTIONS:");
    logger.info("1. Fire Detection SIF:");
    logger.info("   - Voting: " + fireSIF.getVotingLogic());
    logger.info("   - Detectors: FD-101, FD-102, FD-103");
    logger.info("   - Setpoint: 60°C (High)");

    logger.info("2. Gas Detection SIF:");
    logger.info("   - Voting: " + gasSIF.getVotingLogic());
    logger.info("   - Detectors: GD-101, GD-102, GD-103");
    logger.info("   - Setpoint: 25% LEL (High-High)");

    logger.info("ESD LOGIC ACTIONS:");
    logger.info("  1. Trip inlet valve ESD-XV-101 (immediate)");
    logger.info("  2. Activate blowdown valve BD-101 (0.5s delay)");
    logger.info("  3. Redirect flow to blowdown (0.0s delay)");

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

    logger.info("═══ SCENARIO 1: NORMAL OPERATION ═══");
    fireSIF.update(25.0, 27.0, 26.0); // Normal temperatures
    gasSIF.update(0.0, 0.5, 0.3); // No gas detected

    logger.info(fireSIF.getStatusDescription());
    for (Detector det : fireSIF.getDetectors()) {
      logger.info("  " + det.toString());
    }

    logger.info(gasSIF.getStatusDescription());
    for (Detector det : gasSIF.getDetectors()) {
      logger.info("  " + det.toString());
    }

    logger.printf(org.apache.logging.log4j.Level.INFO, "ESD Logic: %s%n", esdLogic.getStatusDescription());
    logger.printf(org.apache.logging.log4j.Level.INFO, "Process flow: %.1f kg/hr%n",
        processStream.getFlowRate("kg/hr"));

    // ═══════════════════════════════════════════════════════════════
    // SCENARIO 2: SINGLE FIRE DETECTOR TRIP (NO ESD)
    // ═══════════════════════════════════════════════════════════════

    logger.info("═══ SCENARIO 2: SINGLE FIRE DETECTOR TRIP (1/3) ═══");
    logger.info(">>> FD-101 detects elevated temperature <<<");
    fireSIF.update(75.0, 27.0, 26.0); // Only FD-101 above setpoint

    logger.info(fireSIF.getStatusDescription());
    logger.info("  FD-101: " + (fireDetector1.isTripped() ? "TRIPPED" : "NORMAL"));
    logger.info("  FD-102: " + (fireDetector2.isTripped() ? "TRIPPED" : "NORMAL"));
    logger.info("  FD-103: " + (fireDetector3.isTripped() ? "TRIPPED" : "NORMAL"));
    logger.info("SIF Status: " + (fireSIF.isTripped() ? "TRIPPED (ESD ACTIVATED)" : "NOT TRIPPED (Monitoring)"));
    logger.info("Voting requires 2/3 detectors - ESD NOT activated");

    // ═══════════════════════════════════════════════════════════════
    // SCENARIO 3: FIRE DETECTED - 2oo3 VOTING SATISFIED
    // ═══════════════════════════════════════════════════════════════

    logger.info("═══ SCENARIO 3: FIRE DETECTED - 2oo3 VOTING SATISFIED ═══");
    logger.info(">>> FD-101 and FD-102 detect fire <<<");
    fireSIF.update(75.0, 82.0, 26.0); // FD-101 and FD-102 above setpoint

    logger.info(fireSIF.getStatusDescription());
    logger.info("  FD-101: " + (fireDetector1.isTripped() ? "TRIPPED" : "NORMAL"));
    logger.info("  FD-102: " + (fireDetector2.isTripped() ? "TRIPPED" : "NORMAL"));
    logger.info("  FD-103: " + (fireDetector3.isTripped() ? "TRIPPED" : "NORMAL"));
    logger.info("SIF Status: " + (fireSIF.isTripped() ? "TRIPPED - ESD ACTIVATED" : "NOT TRIPPED"));
    logger.printf(org.apache.logging.log4j.Level.INFO, "ESD Logic: %s%n", esdLogic.getStatusDescription());

    // Switch to dynamic mode
    separator.setCalculateSteadyState(false);

    // Simulate ESD execution
    logger.info("═══ ESD SEQUENCE EXECUTION ═══");
    logger.info("Time (s) | Fire SIF | Gas SIF  | ESD Step | ESD Valve (%) | BD Valve (%) | Process Flow");
    logger.info("---------|----------|----------|----------|---------------|--------------|-------------");

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
      String esdStep = esdLogic.isComplete() ? "DONE" : "Step " + (esdLogic.getCurrentActionIndex() + 1) + "/3";

      logger.printf(org.apache.logging.log4j.Level.INFO, "%8.1f | %8s | %8s | %8s | %13.1f | %12.1f | %12.1f%n", time,
          fireStatus, gasStatus, esdStep, esdInletValve.getPercentValveOpening(), bdValve.getPercentValveOpening(),
          processStream.getFlowRate("kg/hr"));
    }

    // ═══════════════════════════════════════════════════════════════
    // SCENARIO 4: GAS DETECTION WITH BYPASS
    // ═══════════════════════════════════════════════════════════════

    logger.info("═══ SCENARIO 4: DETECTOR BYPASS CAPABILITY ═══");
    logger.info(">>> GD-101 bypassed for maintenance <<<");

    // Reset for new scenario
    fireSIF.reset();
    gasSIF.reset();
    esdLogic.reset();

    gasDetector1.setBypass(true); // Bypass GD-101
    gasSIF.update(0.0, 30.0, 32.0); // GD-102 and GD-103 detect gas

    logger.info("Gas detectors status:");
    logger.info("  GD-101: " + (gasDetector1.isBypassed() ? "BYPASSED" : "ACTIVE") + " ("
        + (gasDetector1.isTripped() ? "TRIPPED" : "NORMAL") + ")");
    logger.info("  GD-102: " + (gasDetector2.isBypassed() ? "BYPASSED" : "ACTIVE") + " ("
        + (gasDetector2.isTripped() ? "TRIPPED" : "NORMAL") + ")");
    logger.info("  GD-103: " + (gasDetector3.isBypassed() ? "BYPASSED" : "ACTIVE") + " ("
        + (gasDetector3.isTripped() ? "TRIPPED" : "NORMAL") + ")");

    logger.info(gasSIF.getStatusDescription());
    logger.info("Note: With 1 bypassed, 2oo3 becomes effectively 2oo2");
    logger.info("SIF still provides adequate safety: " + (gasSIF.isTripped() ? "YES (ESD activated)" : "NO"));

    // ═══════════════════════════════════════════════════════════════
    // FINAL SUMMARY
    // ═══════════════════════════════════════════════════════════════

    logger.info("═══ SAFETY SYSTEM SUMMARY ═══");

    logger.info("KEY FEATURES DEMONSTRATED:");
    logger.info("✓ 2oo3 voting logic for fire detection");
    logger.info("✓ 2oo3 voting logic for gas detection");
    logger.info("✓ Automatic ESD activation on SIF trip");
    logger.info("✓ Detector bypass capability (1 allowed without compromising safety)");
    logger.info("✓ Coordinated multi-valve shutdown sequence");
    logger.info("✓ IEC 61511 compliant safety architecture");

    logger.info("SAFETY INTEGRITY:");
    logger.info("• Redundant detection reduces spurious trips");
    logger.info("• One detector can fail without losing safety function");
    logger.info("• Maintenance possible with one detector bypassed");
    logger.info("• Multiple independent SIFs for different hazards");

    logger.info("╔════════════════════════════════════════════════════════════════╗");
    logger.info("║         FIRE & GAS SIS EXAMPLE COMPLETED                       ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝");
  }
}
