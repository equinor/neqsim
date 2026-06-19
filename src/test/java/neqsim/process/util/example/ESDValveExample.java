package neqsim.process.util.example;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Example demonstrating ESD (Emergency Shutdown) Valve functionality.
 *
 * <p>
 * This example illustrates the key features of ESDValve:
 * <ul>
 * <li>Normal operation with energized valve (fully open)</li>
 * <li>Emergency trip (de-energization) causing progressive closure</li>
 * <li>Configurable stroke time for closure</li>
 * <li>Partial stroke testing capability</li>
 * <li>Reset and restart procedures</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class ESDValveExample {
  private static final Logger logger = LogManager.getLogger(ESDValveExample.class);

  /**
   * Main method to run the ESD valve example.
   *
   * @param args command line arguments (not used)
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    logger.info("╔════════════════════════════════════════════════════════════════╗");
    logger.info("║          ESD VALVE (EMERGENCY SHUTDOWN) EXAMPLE                ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝\n");

    // Create process gas system
    SystemInterface processGas = new SystemSrkEos(298.15, 100.0);
    processGas.addComponent("methane", 85.0);
    processGas.addComponent("ethane", 10.0);
    processGas.addComponent("propane", 5.0);
    processGas.setMixingRule(2);
    processGas.createDatabase(true);

    Stream feedStream = new Stream("HP Feed", processGas);
    feedStream.setFlowRate(20000.0, "kg/hr");
    feedStream.setPressure(100.0, "bara");
    feedStream.setTemperature(25.0, "C");

    // Create ESD inlet valve (safety-critical isolation valve)
    ESDValve esdInletValve = new ESDValve("ESD-XV-101", feedStream);
    esdInletValve.setStrokeTime(10.0); // 10 seconds to close
    esdInletValve.setCv(800.0); // Large Cv for minimal pressure drop when open

    Stream separatorInlet = new Stream("Sep Inlet", esdInletValve.getOutletStream());

    // Create separator
    Separator separator = new Separator("HP Separator", separatorInlet);
    separator.setCalculateSteadyState(true);

    logger.info("═══ SYSTEM CONFIGURATION ═══");
    logger.info("Feed stream: 20000 kg/hr at 100 bara");
    logger.info("ESD inlet valve: ESD-XV-101");
    logger.info("  - Stroke time: 10 seconds");
    logger.info("  - Cv: 800");
    logger.info("  - Fail-safe position: Closed (fail-closed)");

    // =========================================================================
    // SCENARIO 1: NORMAL OPERATION
    // =========================================================================
    logger.info("╔════════════════════════════════════════════════════════════════╗");
    logger.info("║     SCENARIO 1: NORMAL OPERATION                               ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝\n");

    // Valve is energized (normal operation)
    esdInletValve.energize();

    feedStream.run();
    esdInletValve.run();
    separatorInlet.run();
    separator.run();

    logger.info("═══ NORMAL OPERATION STATUS ═══");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Valve status: %s%n",
	esdInletValve.isEnergized() ? "ENERGIZED" : "DE-ENERGIZED");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Valve opening: %.1f%%%n",
	esdInletValve.getPercentValveOpening());
    logger.printf(org.apache.logging.log4j.Level.INFO, "Feed pressure: %.1f bara%n", feedStream.getPressure("bara"));
    logger.printf(org.apache.logging.log4j.Level.INFO, "Separator inlet pressure: %.1f bara%n",
	separatorInlet.getPressure("bara"));
    logger.printf(org.apache.logging.log4j.Level.INFO, "Separator inlet flow: %.1f kg/hr%n",
	separatorInlet.getFlowRate("kg/hr"));
    logger.info("✓ System operating normally with ESD valve open\n");

    // =========================================================================
    // SCENARIO 2: PARTIAL STROKE TEST (PST)
    // =========================================================================
    logger.info("╔════════════════════════════════════════════════════════════════╗");
    logger.info("║     SCENARIO 2: PARTIAL STROKE TEST (SIL VERIFICATION)         ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝\n");

    logger.info("Initiating partial stroke test to 85% open...");
    esdInletValve.startPartialStrokeTest(85.0);

    logger.info("\nTime (s) | Valve Opening (%) | Status");
    logger.info("---------|-------------------|------------------");

    UUID id = UUID.randomUUID();
    for (double time = 0.0; time <= 5.0; time += 0.5) {
      esdInletValve.runTransient(0.5, id);

      logger.printf(org.apache.logging.log4j.Level.INFO, "%8.1f | %17.1f | %s%n", time,
	  esdInletValve.getPercentValveOpening(),
	  esdInletValve.isPartialStrokeTestActive() ? "PST ACTIVE" : "PST COMPLETE");
    }

    logger.info("\nCompleting partial stroke test...");
    esdInletValve.completePartialStrokeTest();
    logger.printf(org.apache.logging.log4j.Level.INFO, "Valve returned to: %.1f%%%n",
	esdInletValve.getPercentValveOpening());
    logger.info("✓ Partial stroke test completed - valve functionality verified\n");

    // =========================================================================
    // SCENARIO 3: EMERGENCY SHUTDOWN (ESD TRIP)
    // =========================================================================
    logger.info("╔════════════════════════════════════════════════════════════════╗");
    logger.info("║     SCENARIO 3: EMERGENCY SHUTDOWN (ESD TRIP)                  ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝\n");

    logger.info(">>> EMERGENCY CONDITION DETECTED <<<");
    logger.info(">>> TRIGGERING ESD VALVE CLOSURE <<<\n");

    // Trip the ESD valve (de-energize)
    esdInletValve.trip();

    logger.info("═══ ESD VALVE CLOSURE SIMULATION ═══");
    logger.info("Time (s) | Valve Opening (%) | Status      | Elapsed (s) | Sep Inlet Flow (kg/hr)");
    logger.info("---------|-------------------|-------------|-------------|------------------------");

    // Switch separator to transient mode
    separator.setCalculateSteadyState(false);

    double timeStep = 1.0;
    for (double time = 0.0; time <= 15.0; time += timeStep) {
      // Run transient simulation
      esdInletValve.runTransient(timeStep, UUID.randomUUID());

      // Control feed flow based on valve position
      if (esdInletValve.getPercentValveOpening() < 1.0) {
	separatorInlet.getThermoSystem().setTotalFlowRate(0.1, "kg/hr");
      } else {
	feedStream.run();
	esdInletValve.run();
	separatorInlet.run();
      }

      separator.runTransient(timeStep, UUID.randomUUID());

      String status;
      if (esdInletValve.isClosing()) {
	status = "CLOSING";
      } else if (esdInletValve.hasTripCompleted()) {
	status = "CLOSED";
      } else {
	status = "OPEN";
      }

      logger.printf(org.apache.logging.log4j.Level.INFO, "%8.1f | %17.1f | %-11s | %11.1f | %22.1f%n", time,
	  esdInletValve.getPercentValveOpening(), status, esdInletValve.getTimeElapsedSinceTrip(),
	  separatorInlet.getFlowRate("kg/hr"));
    }

    logger.info("\n═══ ESD TRIP SUMMARY ═══");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Valve energized: %s%n",
	esdInletValve.isEnergized() ? "YES" : "NO");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Trip completed: %s%n",
	esdInletValve.hasTripCompleted() ? "YES" : "NO");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Final valve opening: %.1f%%%n",
	esdInletValve.getPercentValveOpening());
    logger.printf(org.apache.logging.log4j.Level.INFO, "Total closure time: %.1f seconds%n",
	esdInletValve.getTimeElapsedSinceTrip());

    if (esdInletValve.hasTripCompleted() && esdInletValve.getPercentValveOpening() < 1.0) {
      logger.info("✓ ESD valve successfully closed - process isolated");
    } else {
      logger.info("✗ WARNING: ESD valve closure not completed");
    }

    // =========================================================================
    // SCENARIO 4: RESET AND RESTART
    // =========================================================================
    logger.info("╔════════════════════════════════════════════════════════════════╗");
    logger.info("║     SCENARIO 4: RESET AND RESTART                              ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝\n");

    logger.info("Emergency resolved - preparing to reset ESD valve");
    logger.info("(In real operations, this requires operator authorization)\n");

    // Reset valve
    esdInletValve.reset();

    logger.info("═══ POST-RESET STATUS ═══");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Valve energized: %s%n",
	esdInletValve.isEnergized() ? "YES" : "NO");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Valve opening: %.1f%%%n",
	esdInletValve.getPercentValveOpening());
    logger.printf(org.apache.logging.log4j.Level.INFO, "Trip completed flag: %s%n",
	esdInletValve.hasTripCompleted() ? "YES" : "NO");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Elapsed time reset: %.1f seconds%n",
	esdInletValve.getTimeElapsedSinceTrip());

    // Restart process
    separator.setCalculateSteadyState(true);
    feedStream.run();
    esdInletValve.run();
    separatorInlet.run();
    separator.run();

    logger.printf(org.apache.logging.log4j.Level.INFO, "\nProcess flow restored: %.1f kg/hr%n",
	separatorInlet.getFlowRate("kg/hr"));
    logger.info("✓ System successfully restarted\n");

    // =========================================================================
    // SCENARIO 5: DIFFERENT STROKE TIMES
    // =========================================================================
    logger.info("╔════════════════════════════════════════════════════════════════╗");
    logger.info("║     SCENARIO 5: EFFECT OF STROKE TIME                          ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝\n");

    logger.info("═══ COMPARING DIFFERENT STROKE TIMES ═══\n");

    double[] strokeTimes = { 5.0, 10.0, 20.0 };
    for (double strokeTime : strokeTimes) {
      logger.printf(org.apache.logging.log4j.Level.INFO, "Testing with stroke time: %.1f seconds%n", strokeTime);

      ESDValve testValve = new ESDValve("Test-ESD", feedStream);
      testValve.setStrokeTime(strokeTime);
      testValve.energize();
      testValve.trip();

      // Simulate closure
      for (int i = 0; i < 25; i++) {
	testValve.runTransient(1.0, UUID.randomUUID());
      }

      logger.printf(org.apache.logging.log4j.Level.INFO, "  - Closure completed: %s%n",
	  testValve.hasTripCompleted() ? "YES" : "NO");
      logger.printf(org.apache.logging.log4j.Level.INFO, "  - Time elapsed: %.1f seconds%n",
	  testValve.getTimeElapsedSinceTrip());
      logger.printf(org.apache.logging.log4j.Level.INFO, "  - Final opening: %.1f%%%n%n",
	  testValve.getPercentValveOpening());
    }

    logger.info("✓ Faster stroke times provide quicker isolation");
    logger.info("✓ Slower stroke times reduce waterhammer risk\n");

    // =========================================================================
    // SUMMARY
    // =========================================================================
    logger.info("╔════════════════════════════════════════════════════════════════╗");
    logger.info("║     ESD VALVE EXAMPLE COMPLETED                                ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝\n");

    logger.info("KEY TAKEAWAYS:");
    logger.info("1. ESD valves are normally open, energized to stay open");
    logger.info("2. De-energization triggers fail-safe closure");
    logger.info("3. Stroke time controls closure rate (balance speed vs. surge)");
    logger.info("4. Partial stroke testing verifies functionality without shutdown");
    logger.info("5. Reset requires manual intervention after emergency");
    logger.info("\nESD valves are critical safety elements in SIL-rated systems!");
  }
}
