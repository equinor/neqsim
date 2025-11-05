package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import neqsim.process.alarm.AlarmConfig;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for simulating a choke valve collapse scenario where a throttle valve suddenly fails
 * open (100%), causing rapid pressure rise that triggers a downstream PSD valve to close for
 * protection.
 * 
 * <p>
 * Scenario:
 * <ul>
 * <li>High pressure inlet (100 bara) feeds through a choke valve (throttle valve)</li>
 * <li>Choke normally operates at 30% opening to control pressure to ~50 bara</li>
 * <li>Downstream PSD valve monitors separator inlet pressure with HIHI alarm at 55 bara</li>
 * <li>Choke suddenly fails open to 100% (choke collapse)</li>
 * <li>Pressure rises rapidly downstream</li>
 * <li>PSD valve trips and closes when HIHI alarm activates</li>
 * <li>System is protected from overpressure</li>
 * </ul>
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ChokeCollapsePSDProtectionTest {
  SystemInterface feedGas;
  Stream feedStream;
  ThrottlingValve chokeValve;
  Stream chokeOutlet;
  PSDValve psdValve;
  Stream psdOutlet;
  PressureTransmitter pressureTransmitter;
  Separator separator;

  /**
   * Set up the test scenario with: - High pressure feed (100 bara) - Choke valve (throttle valve)
   * controlling flow - PSD valve for protection - Separator as downstream equipment
   */
  @BeforeEach
  void setUp() {
    // Create high pressure natural gas feed at 100 bara, 40°C
    feedGas = new SystemSrkEos(273.15 + 40.0, 100.0);
    feedGas.addComponent("nitrogen", 0.5);
    feedGas.addComponent("CO2", 1.5);
    feedGas.addComponent("methane", 85.0);
    feedGas.addComponent("ethane", 8.0);
    feedGas.addComponent("propane", 3.0);
    feedGas.addComponent("i-butane", 1.0);
    feedGas.addComponent("n-butane", 1.0);
    feedGas.setMixingRule("classic");
    feedGas.createDatabase(true);
    feedGas.setMultiPhaseCheck(true);

    // Create feed stream at 5000 kg/hr
    feedStream = new Stream("High Pressure Feed", feedGas);
    feedStream.setFlowRate(5000.0, "kg/hr");
    feedStream.setTemperature(40.0, "C");
    feedStream.setPressure(100.0, "bara");
    feedStream.run();

    // Create choke valve (throttle valve) - normally at 30% opening to control pressure
    chokeValve = new ThrottlingValve("Inlet Choke Valve", feedStream);
    chokeValve.setPercentValveOpening(30.0); // Normal operating condition
    chokeValve.setOutletPressure(50.0); // Target 50 bara downstream

    // Create stream after choke
    chokeOutlet = new Stream("Choke Outlet", chokeValve.getOutletStream());

    // Create PSD valve for protection - normally 100% open
    psdValve = new PSDValve("PSD Inlet Protection", chokeOutlet);
    psdValve.setPercentValveOpening(100.0);

    // Create pressure transmitter to monitor pressure between choke and PSD
    pressureTransmitter = new PressureTransmitter("Separator Inlet PT", chokeOutlet);

    // Configure HIHI alarm at 55 bara with 1 second delay
    AlarmConfig alarmConfig = AlarmConfig.builder().highHighLimit(55.0).highLimit(52.0)
        .deadband(0.5).delay(1.0).unit("bara").build();
    pressureTransmitter.setAlarmConfig(alarmConfig);

    // Link PSD valve to pressure transmitter
    psdValve.linkToPressureTransmitter(pressureTransmitter);
    psdValve.setClosureTime(2.0); // 2 second closure time

    // Create outlet stream from PSD
    psdOutlet = new Stream("PSD Outlet", psdValve.getOutletStream());

    // Create separator as downstream equipment
    separator = new Separator("Production Separator", psdOutlet);
    separator.setInternalDiameter(1.5); // 1.5 meter diameter
    separator.setSeparatorLength(4.0); // 4 meter length
  }

  /**
   * Test the choke collapse scenario where the choke valve suddenly fails open to 100%, causing
   * rapid pressure rise that triggers the PSD valve to close.
   */
  @Test
  void testChokeCollapseTriggersDownstreamPSDClosure() {
    System.out.println("\n===== CHOKE COLLAPSE SCENARIO =====");
    System.out.println("Initial Configuration:");
    System.out.println("  Feed pressure: 100.0 bara");
    System.out.println("  Choke opening: 30.0% (normal operation)");
    System.out.println("  PSD opening: 100.0% (normal operation)");
    System.out.println("  PSD HIHI setpoint: 55.0 bara");
    System.out.println("  Expected downstream pressure: ~50 bara");
    System.out.println();

    // Run initial steady state
    feedStream.run();
    chokeValve.run();
    chokeOutlet.run();
    psdValve.run();
    psdOutlet.run();
    separator.run();

    double initialPressure = chokeOutlet.getPressure("bara");
    System.out.printf("Initial steady state pressure: %.2f bara%n", initialPressure);
    System.out.printf("Initial choke opening: %.1f%%%n", chokeValve.getPercentValveOpening());
    System.out.printf("Initial PSD opening: %.1f%%%n", psdValve.getPercentValveOpening());
    System.out.println();

    // Verify initial conditions
    assertTrue(initialPressure < 55.0, "Initial pressure should be below HIHI setpoint");
    assertEquals(30.0, chokeValve.getPercentValveOpening(), 0.1);
    assertEquals(100.0, psdValve.getPercentValveOpening(), 0.1);

    System.out.println("===== CHOKE VALVE COLLAPSE EVENT =====");
    System.out.println("t = 0.0 s: Choke valve fails OPEN to 100%!");
    System.out.println();

    // FAILURE EVENT: Choke valve suddenly fails open to 100%
    chokeValve.setPercentValveOpening(100.0);

    // Simulate pressure rise and PSD response over time
    double timeStep = 0.5; // 0.5 second time steps
    double simulationTime = 20.0; // 20 second simulation
    boolean psdHasTripped = false;
    double tripTime = 0.0;
    double maxPressure = 0.0;

    System.out.println(
        "Time (s) | Choke Opening | Pressure (bara) | Alarm State | PSD Opening | PSD Tripped");
    System.out.println(
        "---------|---------------|-----------------|-------------|-------------|------------");

    for (double time = 0.0; time <= simulationTime; time += timeStep) {
      // Run the process
      feedStream.run();
      chokeValve.run();
      chokeOutlet.run();

      // Simulate pressure rise due to choke being fully open
      // Pressure rises toward feed pressure (100 bara) but is limited by PSD closure
      if (!psdValve.hasTripped()) {
        // Before PSD trips: pressure rises rapidly toward feed pressure
        double pressureRiseRate = 2.0; // 2 bara per second
        double targetPressure = Math.min(100.0, initialPressure + pressureRiseRate * time);
        chokeOutlet.setFluid(feedGas.clone());
        chokeOutlet.setPressure(targetPressure, "bara");
      } else {
        // After PSD trips: pressure stabilizes as PSD closes and blocks flow
        // Pressure between choke and PSD rises to match feed pressure
        chokeOutlet.setPressure(Math.min(95.0, chokeOutlet.getPressure("bara")), "bara");
      }

      chokeOutlet.run();

      // Evaluate alarm and run PSD valve transient behavior
      pressureTransmitter.evaluateAlarm(chokeOutlet.getPressure("bara"), timeStep, time);
      psdValve.runTransient(timeStep, UUID.randomUUID());

      double currentPressure = chokeOutlet.getPressure("bara");
      maxPressure = Math.max(maxPressure, currentPressure);

      String alarmState = "NONE";
      if (pressureTransmitter.getAlarmState().isActive()) {
        alarmState = pressureTransmitter.getAlarmState().getActiveLevel().toString();
      }

      // Check if PSD just tripped
      if (psdValve.hasTripped() && !psdHasTripped) {
        psdHasTripped = true;
        tripTime = time;
      }

      System.out.printf(
          "%7.1f  |    %6.1f%%    |     %6.2f      |   %-6s    |   %6.1f%%   |    %s%n", time,
          chokeValve.getPercentValveOpening(), currentPressure, alarmState,
          psdValve.getPercentValveOpening(), psdValve.hasTripped() ? "YES" : "NO");

      // Stop if PSD has been tripped and closed for a while
      if (psdHasTripped && time > tripTime + 10.0) {
        break;
      }
    }

    System.out.println();
    System.out.println("===== SCENARIO SUMMARY =====");
    System.out.printf("Choke valve failed open at: 0.0 s%n");
    System.out.printf("PSD valve tripped at: %.1f s%n", tripTime);
    System.out.printf("Maximum pressure reached: %.2f bara%n", maxPressure);
    System.out.printf("HIHI setpoint: 55.0 bara%n");
    System.out.printf("PSD valve status: %s%n",
        psdValve.hasTripped() ? "TRIPPED (CLOSED)" : "OPEN");
    System.out.printf("Final PSD opening: %.1f%%%n", psdValve.getPercentValveOpening());
    System.out.println();

    // Verify that PSD valve tripped
    assertTrue(psdHasTripped, "PSD valve should have tripped due to HIHI alarm");
    assertTrue(psdValve.hasTripped(), "PSD valve should be in tripped state");
    assertEquals(0.0, psdValve.getPercentValveOpening(), 0.1,
        "PSD valve should be fully closed after trip");

    // Verify trip occurred at or shortly after reaching HIHI setpoint
    assertTrue(tripTime >= 2.0 && tripTime <= 5.0,
        "PSD should trip within reasonable time after pressure exceeds HIHI");

    // Verify maximum pressure didn't exceed safe limits significantly
    assertTrue(maxPressure < 65.0,
        "Maximum pressure should be controlled by PSD closure (< 65 bara)");

    System.out.println("✓ Choke collapse scenario completed successfully!");
    System.out.println("✓ PSD valve protected system from overpressure");
    System.out.println("✓ Rapid pressure rise was detected and mitigated");
    System.out.println("======================================");
  }

  /**
   * Test that the PSD valve can be reset after the choke collapse event and the choke is returned
   * to normal position.
   */
  @Test
  void testPSDResetAfterChokeRepair() {
    System.out.println("\n===== CHOKE REPAIR AND PSD RESET TEST =====");

    // Run to steady state
    feedStream.run();
    chokeValve.run();
    chokeOutlet.run();
    psdValve.run();

    // Simulate choke collapse
    System.out.println("Step 1: Simulating choke collapse...");
    chokeValve.setPercentValveOpening(100.0);

    // Simulate pressure rise to trigger PSD
    double time = 0.0;
    for (int i = 0; i < 10; i++) {
      double pressure = 50.0 + i * 1.0; // Rise from 50 to 59 bara
      chokeOutlet.setPressure(pressure, "bara");
      chokeOutlet.run();
      pressureTransmitter.evaluateAlarm(pressure, 0.5, time);
      psdValve.runTransient(0.5, UUID.randomUUID());
      time += 0.5;

      if (psdValve.hasTripped()) {
        System.out.printf("  PSD tripped at %.1f bara%n", pressure);
        break;
      }
    }

    assertTrue(psdValve.hasTripped(), "PSD should have tripped");
    assertEquals(0.0, psdValve.getPercentValveOpening(), 0.1);

    // Repair choke valve - return to normal 30% opening
    System.out.println();
    System.out.println("Step 2: Repairing choke valve (returning to 30% opening)...");
    chokeValve.setPercentValveOpening(30.0);
    chokeOutlet.setPressure(50.0, "bara"); // Pressure returns to normal
    chokeOutlet.run();

    System.out.println("  Choke repaired and pressure returned to 50 bara");

    // Try to open PSD valve - should fail while tripped
    System.out.println();
    System.out.println("Step 3: Attempting to open PSD valve while still tripped...");
    psdValve.setPercentValveOpening(50.0);
    assertEquals(0.0, psdValve.getPercentValveOpening(), 0.1,
        "PSD should remain closed while tripped");
    System.out.println("  ✓ PSD correctly prevents opening while tripped");

    // Reset PSD valve
    System.out.println();
    System.out.println("Step 4: Resetting PSD valve...");
    psdValve.reset();
    System.out.println("  PSD valve reset complete");

    // Now PSD can be opened
    System.out.println();
    System.out.println("Step 5: Opening PSD valve to resume operation...");
    psdValve.setPercentValveOpening(100.0);
    psdValve.run();

    assertEquals(100.0, psdValve.getPercentValveOpening(), 0.1, "PSD should open after reset");
    System.out.printf("  ✓ PSD successfully opened to %.1f%%%n", psdValve.getPercentValveOpening());

    System.out.println();
    System.out.println("===== RESET TEST SUMMARY =====");
    System.out.println("✓ Choke collapse triggered PSD trip");
    System.out.println("✓ Choke repaired (returned to 30% opening)");
    System.out.println("✓ PSD prevented opening while tripped");
    System.out.println("✓ PSD reset successful");
    System.out.println("✓ System ready to resume normal operation");
    System.out.println("======================================");
  }
}
