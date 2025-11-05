package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.alarm.AlarmConfig;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for PSD (Process Shutdown) Valve functionality.
 * 
 * <p>
 * Validates that PSD valve correctly responds to High-High (HIHI) pressure alarms by closing
 * automatically and preventing overpressure propagation to downstream equipment.
 * </p>
 */
public class PSDValveDynamicTest extends neqsim.NeqSimTest {

  private SystemInterface testFluid;
  private Stream feedStream;
  private PSDValve psdValve;
  private PressureTransmitter pressureTransmitter;
  private Separator separator;
  private ThrottlingValve outletValve;

  @BeforeEach
  public void setUp() {
    // Create test fluid - natural gas mixture
    testFluid = new SystemSrkEos(273.15 + 40, 50.0);
    testFluid.addComponent("nitrogen", 0.5);
    testFluid.addComponent("CO2", 1.0);
    testFluid.addComponent("methane", 85.0);
    testFluid.addComponent("ethane", 8.0);
    testFluid.addComponent("propane", 4.0);
    testFluid.addComponent("i-butane", 0.75);
    testFluid.addComponent("n-butane", 0.75);
    testFluid.setMixingRule(2);
    testFluid.setMultiPhaseCheck(true);

    // Create feed stream
    feedStream = new Stream("Feed", testFluid);
    feedStream.setFlowRate(5000.0, "kg/hr");
    feedStream.setTemperature(40.0, "C");
    feedStream.setPressure(50.0, "bara");
    feedStream.run();

    // Create PSD valve on inlet
    psdValve = new PSDValve("PSD-101", feedStream);
    psdValve.setPercentValveOpening(100.0);
    psdValve.setCv(150.0);
    psdValve.setClosureTime(2.0); // 2 seconds fast closure
    psdValve.run();

    // Create pressure transmitter monitoring separator inlet (PSD valve outlet)
    pressureTransmitter = new PressureTransmitter("PT-101", psdValve.getOutletStream());

    // Configure HIHI alarm at 55 bara with 1 bara deadband and 0.5 second delay
    AlarmConfig alarmConfig =
        AlarmConfig.builder().highHighLimit(55.0).deadband(1.0).delay(0.5).unit("bara").build();
    pressureTransmitter.setAlarmConfig(alarmConfig);

    // Link PSD valve to pressure transmitter
    psdValve.linkToPressureTransmitter(pressureTransmitter);

    // Create separator (1.5m ID, 4m length)
    separator = new Separator("Separator", psdValve.getOutletStream());
    separator.setInternalDiameter(1.5);
    separator.setSeparatorLength(4.0);
    separator.run();

    // Create outlet valve (normally operating at reduced opening to build pressure)
    outletValve = new ThrottlingValve("Outlet Valve", separator.getGasOutStream());
    outletValve.setPercentValveOpening(1.0); // Very restricted to build pressure
    outletValve.setCv(150.0);
    outletValve.run();
  }

  @Test
  public void testPSDValveClosesOnHighHighAlarm() {
    System.out.println("\n===== PSD VALVE HIHI TRIP TEST =====");

    double time = 0.0;
    double dt = 1.0;

    // Simulate normal operation at 50 bara
    System.out.printf("Initial state: PSD outlet pressure = %.2f bara%n",
        psdValve.getOutletStream().getPressure("bara"));
    pressureTransmitter.evaluateAlarm(50.0, dt, time);
    psdValve.runTransient(dt, UUID.randomUUID());
    assertFalse(psdValve.hasTripped(), "Valve should not trip at normal pressure");
    System.out.println("✓ Valve operating normally at 50.0 bara");

    // Simulate pressure rising to HIHI limit (55 bara) - blocked outlet scenario
    System.out.println("\nSimulating pressure rise due to blocked outlet...");
    for (double pressure = 51.0; pressure <= 60.0; pressure += 1.0) {
      time += dt;
      // Manually set outlet pressure to simulate blocked outlet causing buildup
      psdValve.getOutletStream().getThermoSystem().setPressure(pressure, "bara");

      // Evaluate alarm
      pressureTransmitter.evaluateAlarm(pressure, dt, time);

      // Run PSD valve transient logic
      psdValve.runTransient(dt, UUID.randomUUID());

      String alarmStatus = "NONE";
      if (pressureTransmitter.getAlarmState().isActive()) {
        alarmStatus = pressureTransmitter.getAlarmState().getActiveLevel().toString();
      }

      System.out.printf(
          "Time: %5.1f s | Pressure: %5.2f bara | Alarm: %4s | "
              + "PSD Opening: %5.1f %% | Tripped: %3s%n",
          time, pressure, alarmStatus, psdValve.getPercentValveOpening(),
          psdValve.hasTripped() ? "YES" : "NO");

      // Check if valve has tripped at HIHI limit
      if (pressure >= 55.0) {
        // After alarm delay (0.5s), valve should trip
        if (time >= 55.0 - 51.0 + 0.5) { // Time to reach 55 bara + alarm delay
          assertTrue(psdValve.hasTripped(), String
              .format("Valve should have tripped at %.2f bara (HIHI limit = 55 bara)", pressure));
          assertEquals(0.0, psdValve.getPercentValveOpening(), 0.1,
              "PSD valve should be fully closed after trip");
          assertTrue(pressureTransmitter.getAlarmState().isActive(),
              "Pressure transmitter should have active alarm");
          assertEquals(neqsim.process.alarm.AlarmLevel.HIHI,
              pressureTransmitter.getAlarmState().getActiveLevel(), "Active alarm should be HIHI");

          System.out.println("\n✓ PSD valve successfully tripped on HIHI alarm!");
          System.out.printf("Trip occurred when pressure reached %.2f bara%n", pressure);
          System.out.println("======================================");
          return; // Test passed
        }
      }
    }

    throw new AssertionError("PSD valve did not trip as expected");
  }

  @Test
  public void testPSDValveStaysClosedUntilReset() {
    System.out.println("\n===== PSD VALVE RESET TEST =====");

    // Manually trip the valve by setting outlet pressure high
    psdValve.getOutletStream().getThermoSystem().setPressure(60.0, "bara");
    double measuredPressure = psdValve.getOutletStream().getPressure("bara");

    // Trigger HIHI alarm
    pressureTransmitter.evaluateAlarm(measuredPressure, 1.0, 0.0);

    // Run valve transient - should trip
    psdValve.runTransient(1.0, UUID.randomUUID());

    assertTrue(psdValve.hasTripped(), "Valve should have tripped");
    assertEquals(0.0, psdValve.getPercentValveOpening(), 0.1, "Valve should be closed");

    System.out.println("PSD valve tripped at 60.0 bara");

    // Try to open valve while still tripped - should fail
    psdValve.setPercentValveOpening(50.0);
    assertEquals(0.0, psdValve.getPercentValveOpening(), 0.1,
        "Valve should remain closed while tripped");

    System.out.println("✓ Valve correctly prevents opening while tripped");

    // Reset the valve
    psdValve.reset();
    assertFalse(psdValve.hasTripped(), "Valve should not be tripped after reset");

    System.out.println("PSD valve reset");

    // Now we should be able to open it
    psdValve.setPercentValveOpening(50.0);
    assertEquals(50.0, psdValve.getPercentValveOpening(), 0.1, "Valve should open after reset");

    System.out.println("✓ Valve successfully opened to 50% after reset");
    System.out.println("======================================");
  }

  @Test
  public void testPSDValvePreventsOverpressure() {
    System.out.println("\n===== PSD VALVE OVERPRESSURE PROTECTION TEST =====");

    double time = 0.0;
    double dt = 1.0;
    double maxMeasuredPressure = 50.0;
    boolean psdTripped = false;

    System.out.println("\nSimulating gradual pressure rise...");

    // Simulate pressure rising gradually
    for (double pressure = 50.0; pressure <= 65.0; pressure += 0.5) {
      time += dt;

      // Set pressure to simulate blocked outlet scenario
      if (!psdTripped) {
        psdValve.getOutletStream().getThermoSystem().setPressure(pressure, "bara");
      }

      double measuredPressure = psdValve.getOutletStream().getPressure("bara");
      maxMeasuredPressure = Math.max(maxMeasuredPressure, measuredPressure);

      pressureTransmitter.evaluateAlarm(measuredPressure, dt, time);
      psdValve.runTransient(dt, UUID.randomUUID());

      if (time % 5 < dt || psdValve.hasTripped() != psdTripped) {
        System.out.printf("Time: %5.1f s | Pressure: %5.2f bara | PSD: %5.1f %% | Tripped: %3s%n",
            time, measuredPressure, psdValve.getPercentValveOpening(),
            psdValve.hasTripped() ? "YES" : "NO");
      }

      if (psdValve.hasTripped() && !psdTripped) {
        psdTripped = true;
        System.out.printf("%n✓ PSD valve tripped at %.1f s when pressure reached %.2f bara%n", time,
            measuredPressure);
        System.out.println("Pressure rise stopped by PSD valve closure\n");
      }
    }

    System.out.println("\n===== TEST SUMMARY =====");
    System.out.printf("Maximum measured pressure: %.2f bara%n", maxMeasuredPressure);
    System.out.printf("PSD valve tripped: %s%n", psdTripped ? "YES" : "NO");

    // The PSD valve should have tripped, limiting downstream pressure
    assertTrue(psdTripped, "PSD valve should have tripped to protect separator");

    // PSD valve should have stopped pressure from continuing to rise uncontrolled
    System.out.println("✓ PSD valve successfully protected separator from overpressure");
    System.out.println("======================================");
  }

  @Test
  public void testDisabledTripDoesNotClose() {
    System.out.println("\n===== PSD VALVE TRIP DISABLED TEST =====");

    // Disable trip function
    psdValve.setTripEnabled(false);
    assertFalse(psdValve.isTripEnabled(), "Trip should be disabled");

    // Set high pressure to trigger HIHI alarm
    psdValve.getOutletStream().getThermoSystem().setPressure(60.0, "bara");
    double measuredPressure = psdValve.getOutletStream().getPressure("bara");

    // Trigger HIHI alarm
    pressureTransmitter.evaluateAlarm(measuredPressure, 1.0, 0.0);

    // Run valve transient
    psdValve.runTransient(1.0, UUID.randomUUID());

    // Valve should NOT trip because trip is disabled
    assertFalse(psdValve.hasTripped(), "Valve should not trip when trip is disabled");
    assertEquals(100.0, psdValve.getPercentValveOpening(), 0.1,
        "Valve should remain open when trip is disabled");

    System.out.println("✓ Valve correctly stayed open with trip disabled at 60.0 bara");

    // Re-enable trip
    psdValve.setTripEnabled(true);
    assertTrue(psdValve.isTripEnabled(), "Trip should be enabled");

    // Run again - now it should trip
    psdValve.runTransient(1.0, UUID.randomUUID());

    assertTrue(psdValve.hasTripped(), "Valve should trip when trip is re-enabled");
    assertEquals(0.0, psdValve.getPercentValveOpening(), 0.1, "Valve should be closed");

    System.out.println("✓ Valve correctly tripped after re-enabling trip function");
    System.out.println("======================================");
  }
}
