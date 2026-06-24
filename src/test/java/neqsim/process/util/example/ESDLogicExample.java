package neqsim.process.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.valve.BlowdownValve;
import neqsim.process.equipment.valve.ControlValve;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.logic.action.TripValveAction;
import neqsim.process.logic.action.ActivateBlowdownAction;
import neqsim.process.logic.action.SetSplitterAction;
import neqsim.process.measurementdevice.PushButton;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Example demonstrating ESD Logic Framework - coordinated shutdown sequence.
 *
 * <p>
 * This example shows how to use the new ProcessLogic framework to implement a coordinated ESD sequence that:
 * <ul>
 * <li>Closes inlet ESD valve (trip)</li>
 * <li>Opens blowdown valve (activate)</li>
 * <li>Redirects flow through splitter (set split factors)</li>
 * <li>All triggered by a single push button</li>
 * </ul>
 *
 * <p>
 * Benefits of logic framework:
 * <ul>
 * <li>Single button triggers multiple coordinated actions</li>
 * <li>Configurable delays between steps</li>
 * <li>Reusable logic sequences</li>
 * <li>Clear separation of logic from equipment</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class ESDLogicExample {
  private static final Logger logger = LogManager.getLogger(ESDLogicExample.class);

  /**
   * Main method to run the ESD logic example.
   *
   * @param args command line arguments (not used)
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    logger.info("╔════════════════════════════════════════════════════════════════╗");
    logger.info("║          ESD LOGIC FRAMEWORK DEMONSTRATION                     ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝\n");

    // Create separator feed gas
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

    // Inlet control valve
    ControlValve controlValve = new ControlValve("FCV-101", feedStream);
    controlValve.setPercentValveOpening(50.0);
    controlValve.setCv(300.0);
    controlValve.setOutletPressure(48.0);

    Stream afterControlValve = new Stream("After Control Valve", controlValve.getOutletStream());

    // ESD inlet valve
    ESDValve esdInletValve = new ESDValve("ESD-XV-101", afterControlValve);
    esdInletValve.setStrokeTime(5.0);
    esdInletValve.setCv(500.0);
    esdInletValve.energize();

    Stream separatorInlet = new Stream("Separator Inlet", esdInletValve.getOutletStream());

    // Separator
    Separator separator = new Separator("HP Separator", separatorInlet);
    separator.setCalculateSteadyState(true);

    Stream separatorGasOut = new Stream("Sep Gas Out", separator.getGasOutStream());

    // Splitter
    Splitter gasSplitter = new Splitter("Gas Splitter", separatorGasOut, 2);
    gasSplitter.setSplitFactors(new double[] { 1.0, 0.0 }); // Initially all to process

    Stream processStream = new Stream("To Process", gasSplitter.getSplitStream(0));
    Stream blowdownStream = new Stream("To Blowdown", gasSplitter.getSplitStream(1));

    // Blowdown valve
    BlowdownValve bdValve = new BlowdownValve("BD-101", blowdownStream);
    bdValve.setOpeningTime(5.0);
    bdValve.setCv(200.0);

    // ═══════════════════════════════════════════════════════════════
    // NEW: CREATE ESD LOGIC SEQUENCE
    // ═══════════════════════════════════════════════════════════════

    ESDLogic esdLogic = new ESDLogic("ESD Level 1 Logic");

    // Step 1: Trip inlet valve (immediate)
    esdLogic.addAction(new TripValveAction(esdInletValve), 0.0);

    // Step 2: Open blowdown valve (0.5s delay for coordination)
    esdLogic.addAction(new ActivateBlowdownAction(bdValve), 0.5);

    // Step 3: Redirect flow to blowdown (simultaneous with BD valve)
    esdLogic.addAction(new SetSplitterAction(gasSplitter, new double[] { 0.0, 1.0 }), 0.0);

    // Create ESD push button and link to logic
    PushButton esdButton = new PushButton("ESD-PB-101");
    esdButton.linkToLogic(esdLogic);

    // ═══════════════════════════════════════════════════════════════

    logger.info("═══ SYSTEM CONFIGURATION ═══");
    logger.info("Separator: HP Separator at 50 bara");
    logger.info("Gas flow rate: 10000 kg/hr");
    logger.info("Control valve: FCV-101 (50% opening)");
    logger.info("ESD valve: ESD-XV-101 (5s stroke time)");
    logger.info("Blowdown valve: BD-101 (5s opening time)");

    logger.info("ESD LOGIC SEQUENCE:");
    logger.info("  1. Trip inlet valve ESD-XV-101 (immediate)");
    logger.info("  2. Activate blowdown valve BD-101 (0.5s delay)");
    logger.info("  3. Redirect splitter to blowdown (0.0s delay)");
    logger.info("  Total actions: " + esdLogic.getActionCount());

    // Run initial steady state
    logger.info("═══ NORMAL OPERATION ═══");
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

    logger.printf(org.apache.logging.log4j.Level.INFO, "ESD valve: %s, %.1f%% open%n",
        esdInletValve.isEnergized() ? "ENERGIZED" : "DE-ENERGIZED", esdInletValve.getPercentValveOpening());
    logger.printf(org.apache.logging.log4j.Level.INFO, "Process flow: %.1f kg/hr%n",
        processStream.getFlowRate("kg/hr"));
    logger.printf(org.apache.logging.log4j.Level.INFO, "Blowdown flow: %.1f kg/hr%n",
        blowdownStream.getFlowRate("kg/hr"));
    logger.printf(org.apache.logging.log4j.Level.INFO, "BD valve: %s%n",
        bdValve.isActivated() ? "ACTIVATED" : "NOT ACTIVATED");
    logger.printf(org.apache.logging.log4j.Level.INFO, "ESD Logic: %s%n", esdLogic.getStatusDescription());

    // Trigger ESD
    logger.info("═══ ESD TRIGGERED ═══");
    logger.info(">>> OPERATOR PUSHES ESD BUTTON <<<");
    logger.info(">>> ESD LOGIC ACTIVATED <<<");
    esdButton.push();

    // Switch separator to dynamic mode
    separator.setCalculateSteadyState(false);

    logger.printf(org.apache.logging.log4j.Level.INFO, "Button state: %s%n",
        esdButton.isPushed() ? "PUSHED" : "NOT PUSHED");
    logger.printf(org.apache.logging.log4j.Level.INFO, "ESD Logic: %s%n", esdLogic.getStatusDescription());

    // Simulate with logic execution
    logger.info("═══ ESD SEQUENCE EXECUTION ═══");
    logger.info("Time (s) | Logic Status                    | ESD Valve (%) | BD Valve (%) | Process Flow | BD Flow");
    logger.info("---------|--------------------------------|---------------|--------------|--------------|----------");

    double timeStep = 0.5;
    double totalTime = 10.0;

    for (double time = 0.0; time <= totalTime; time += timeStep) {
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

      if (separator.getCalculateSteadyState()) {
        separator.run();
      } else {
        separator.runTransient(timeStep, java.util.UUID.randomUUID());
      }

      separatorGasOut.run();
      gasSplitter.run();
      blowdownStream.run();
      bdValve.runTransient(timeStep, java.util.UUID.randomUUID());
      processStream.run();

      // Print status (abbreviated for readability)
      String logicStatus = esdLogic.isComplete() ? "COMPLETED"
          : "Step " + (esdLogic.getCurrentActionIndex() + 1) + "/" + esdLogic.getActionCount();

      logger.printf(org.apache.logging.log4j.Level.INFO, "%8.1f | %-30s | %13.1f | %12.1f | %12.1f | %8.1f%n", time,
          logicStatus, esdInletValve.getPercentValveOpening(), bdValve.getPercentValveOpening(),
          processStream.getFlowRate("kg/hr"), blowdownStream.getFlowRate("kg/hr"));
    }

    logger.info("═══ FINAL STATUS ═══");
    logger.info(esdButton.toString());
    logger.printf(org.apache.logging.log4j.Level.INFO, "ESD Logic: %s%n", esdLogic.getStatusDescription());
    logger.printf(org.apache.logging.log4j.Level.INFO, "ESD valve: %.1f%% open (%s)%n",
        esdInletValve.getPercentValveOpening(), esdInletValve.hasTripCompleted() ? "TRIP COMPLETED" : "CLOSING");
    logger.printf(org.apache.logging.log4j.Level.INFO, "BD valve: %.1f%% open (%s)%n", bdValve.getPercentValveOpening(),
        bdValve.isActivated() ? "ACTIVATED" : "NOT ACTIVATED");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Process flow: %.1f kg/hr%n",
        processStream.getFlowRate("kg/hr"));
    logger.printf(org.apache.logging.log4j.Level.INFO, "Blowdown flow: %.1f kg/hr%n",
        blowdownStream.getFlowRate("kg/hr"));

    // Verification
    logger.info("═══ VERIFICATION ═══");
    if (esdLogic.isComplete()) {
      logger.info("✓ ESD logic sequence completed successfully");
    } else {
      logger.info("✗ WARNING: ESD logic not completed");
    }

    if (esdInletValve.hasTripCompleted()) {
      logger.info("✓ ESD inlet valve trip completed");
    }

    if (bdValve.isActivated() && bdValve.getPercentValveOpening() > 90.0) {
      logger.info("✓ Blowdown valve fully opened");
    }

    if (blowdownStream.getFlowRate("kg/hr") > processStream.getFlowRate("kg/hr")) {
      logger.info("✓ Flow redirected to blowdown system");
    }

    logger.info("╔════════════════════════════════════════════════════════════════╗");
    logger.info("║         ESD LOGIC FRAMEWORK DEMONSTRATION COMPLETED            ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝");
  }
}
