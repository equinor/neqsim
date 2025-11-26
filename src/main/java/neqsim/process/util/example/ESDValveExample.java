package neqsim.process.util.example;

import java.util.UUID;
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

  /**
   * Main method to run the ESD valve example.
   *
   * @param args command line arguments (not used)
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    System.out.println("╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║          ESD VALVE (EMERGENCY SHUTDOWN) EXAMPLE                ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

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

    System.out.println("═══ SYSTEM CONFIGURATION ═══");
    System.out.println("Feed stream: 20000 kg/hr at 100 bara");
    System.out.println("ESD inlet valve: ESD-XV-101");
    System.out.println("  - Stroke time: 10 seconds");
    System.out.println("  - Cv: 800");
    System.out.println("  - Fail-safe position: Closed (fail-closed)");
    System.out.println();

    // =========================================================================
    // SCENARIO 1: NORMAL OPERATION
    // =========================================================================
    System.out.println("╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║     SCENARIO 1: NORMAL OPERATION                               ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    // Valve is energized (normal operation)
    esdInletValve.energize();

    feedStream.run();
    esdInletValve.run();
    separatorInlet.run();
    separator.run();

    System.out.println("═══ NORMAL OPERATION STATUS ═══");
    System.out.printf("Valve status: %s%n",
        esdInletValve.isEnergized() ? "ENERGIZED" : "DE-ENERGIZED");
    System.out.printf("Valve opening: %.1f%%%n", esdInletValve.getPercentValveOpening());
    System.out.printf("Feed pressure: %.1f bara%n", feedStream.getPressure("bara"));
    System.out.printf("Separator inlet pressure: %.1f bara%n", separatorInlet.getPressure("bara"));
    System.out.printf("Separator inlet flow: %.1f kg/hr%n", separatorInlet.getFlowRate("kg/hr"));
    System.out.println("✓ System operating normally with ESD valve open\n");

    // =========================================================================
    // SCENARIO 2: PARTIAL STROKE TEST (PST)
    // =========================================================================
    System.out.println("╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║     SCENARIO 2: PARTIAL STROKE TEST (SIL VERIFICATION)         ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    System.out.println("Initiating partial stroke test to 85% open...");
    esdInletValve.startPartialStrokeTest(85.0);

    System.out.println("\nTime (s) | Valve Opening (%) | Status");
    System.out.println("---------|-------------------|------------------");

    UUID id = UUID.randomUUID();
    for (double time = 0.0; time <= 5.0; time += 0.5) {
      esdInletValve.runTransient(0.5, id);

      System.out.printf("%8.1f | %17.1f | %s%n", time, esdInletValve.getPercentValveOpening(),
          esdInletValve.isPartialStrokeTestActive() ? "PST ACTIVE" : "PST COMPLETE");
    }

    System.out.println("\nCompleting partial stroke test...");
    esdInletValve.completePartialStrokeTest();
    System.out.printf("Valve returned to: %.1f%%%n", esdInletValve.getPercentValveOpening());
    System.out.println("✓ Partial stroke test completed - valve functionality verified\n");

    // =========================================================================
    // SCENARIO 3: EMERGENCY SHUTDOWN (ESD TRIP)
    // =========================================================================
    System.out.println("╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║     SCENARIO 3: EMERGENCY SHUTDOWN (ESD TRIP)                  ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    System.out.println(">>> EMERGENCY CONDITION DETECTED <<<");
    System.out.println(">>> TRIGGERING ESD VALVE CLOSURE <<<\n");

    // Trip the ESD valve (de-energize)
    esdInletValve.trip();

    System.out.println("═══ ESD VALVE CLOSURE SIMULATION ═══");
    System.out.println(
        "Time (s) | Valve Opening (%) | Status      | Elapsed (s) | Sep Inlet Flow (kg/hr)");
    System.out.println(
        "---------|-------------------|-------------|-------------|------------------------");

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

      System.out.printf("%8.1f | %17.1f | %-11s | %11.1f | %22.1f%n", time,
          esdInletValve.getPercentValveOpening(), status, esdInletValve.getTimeElapsedSinceTrip(),
          separatorInlet.getFlowRate("kg/hr"));
    }

    System.out.println("\n═══ ESD TRIP SUMMARY ═══");
    System.out.printf("Valve energized: %s%n", esdInletValve.isEnergized() ? "YES" : "NO");
    System.out.printf("Trip completed: %s%n", esdInletValve.hasTripCompleted() ? "YES" : "NO");
    System.out.printf("Final valve opening: %.1f%%%n", esdInletValve.getPercentValveOpening());
    System.out.printf("Total closure time: %.1f seconds%n",
        esdInletValve.getTimeElapsedSinceTrip());

    if (esdInletValve.hasTripCompleted() && esdInletValve.getPercentValveOpening() < 1.0) {
      System.out.println("✓ ESD valve successfully closed - process isolated");
    } else {
      System.out.println("✗ WARNING: ESD valve closure not completed");
    }
    System.out.println();

    // =========================================================================
    // SCENARIO 4: RESET AND RESTART
    // =========================================================================
    System.out.println("╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║     SCENARIO 4: RESET AND RESTART                              ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    System.out.println("Emergency resolved - preparing to reset ESD valve");
    System.out.println("(In real operations, this requires operator authorization)\n");

    // Reset valve
    esdInletValve.reset();

    System.out.println("═══ POST-RESET STATUS ═══");
    System.out.printf("Valve energized: %s%n", esdInletValve.isEnergized() ? "YES" : "NO");
    System.out.printf("Valve opening: %.1f%%%n", esdInletValve.getPercentValveOpening());
    System.out.printf("Trip completed flag: %s%n", esdInletValve.hasTripCompleted() ? "YES" : "NO");
    System.out.printf("Elapsed time reset: %.1f seconds%n",
        esdInletValve.getTimeElapsedSinceTrip());

    // Restart process
    separator.setCalculateSteadyState(true);
    feedStream.run();
    esdInletValve.run();
    separatorInlet.run();
    separator.run();

    System.out.printf("\nProcess flow restored: %.1f kg/hr%n", separatorInlet.getFlowRate("kg/hr"));
    System.out.println("✓ System successfully restarted\n");

    // =========================================================================
    // SCENARIO 5: DIFFERENT STROKE TIMES
    // =========================================================================
    System.out.println("╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║     SCENARIO 5: EFFECT OF STROKE TIME                          ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    System.out.println("═══ COMPARING DIFFERENT STROKE TIMES ═══\n");

    double[] strokeTimes = {5.0, 10.0, 20.0};
    for (double strokeTime : strokeTimes) {
      System.out.printf("Testing with stroke time: %.1f seconds%n", strokeTime);

      ESDValve testValve = new ESDValve("Test-ESD", feedStream);
      testValve.setStrokeTime(strokeTime);
      testValve.energize();
      testValve.trip();

      // Simulate closure
      for (int i = 0; i < 25; i++) {
        testValve.runTransient(1.0, UUID.randomUUID());
      }

      System.out.printf("  - Closure completed: %s%n", testValve.hasTripCompleted() ? "YES" : "NO");
      System.out.printf("  - Time elapsed: %.1f seconds%n", testValve.getTimeElapsedSinceTrip());
      System.out.printf("  - Final opening: %.1f%%%n%n", testValve.getPercentValveOpening());
    }

    System.out.println("✓ Faster stroke times provide quicker isolation");
    System.out.println("✓ Slower stroke times reduce waterhammer risk\n");

    // =========================================================================
    // SUMMARY
    // =========================================================================
    System.out.println("╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║     ESD VALVE EXAMPLE COMPLETED                                ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    System.out.println("KEY TAKEAWAYS:");
    System.out.println("1. ESD valves are normally open, energized to stay open");
    System.out.println("2. De-energization triggers fail-safe closure");
    System.out.println("3. Stroke time controls closure rate (balance speed vs. surge)");
    System.out.println("4. Partial stroke testing verifies functionality without shutdown");
    System.out.println("5. Reset requires manual intervention after emergency");
    System.out.println("\nESD valves are critical safety elements in SIL-rated systems!");
  }
}
