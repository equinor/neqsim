package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for ESDValve functionality.
 */
class ESDValveTest {
  private SystemInterface testSystem;
  private Stream testStream;
  private ESDValve esdValve;

  @BeforeEach
  void setUp() {
    // Create test fluid system
    testSystem = new SystemSrkEos(298.15, 50.0);
    testSystem.addComponent("methane", 10.0);
    testSystem.addComponent("ethane", 1.0);
    testSystem.setMixingRule(2);

    testStream = new Stream("Test Stream", testSystem);
    testStream.setFlowRate(1000.0, "kg/hr");
    testStream.setPressure(50.0, "bara");
    testStream.setTemperature(25.0, "C");
    testStream.run();

    esdValve = new ESDValve("ESD-XV-001", testStream);
    esdValve.setStrokeTime(10.0);
    esdValve.setCv(100.0);
  }

  @Test
  void testInitialState() {
    // ESD valve should start energized and fully open
    assertTrue(esdValve.isEnergized(), "Valve should be energized initially");
    assertEquals(100.0, esdValve.getPercentValveOpening(), 0.1,
        "Valve should be fully open initially");
    assertFalse(esdValve.isClosing(), "Valve should not be closing initially");
  }

  @Test
  void testDeEnergizeTriggersClosing() {
    // De-energize valve
    esdValve.deEnergize();

    assertFalse(esdValve.isEnergized(), "Valve should be de-energized");
    assertTrue(esdValve.isClosing(), "Valve should be closing");
    assertFalse(esdValve.hasTripCompleted(), "Trip should not be completed immediately");
  }

  @Test
  void testClosureProgression() {
    // De-energize and simulate closure
    esdValve.deEnergize();

    double dt = 1.0; // 1 second time steps
    UUID id = UUID.randomUUID();

    // Simulate halfway through stroke time
    for (int i = 0; i < 5; i++) {
      esdValve.runTransient(dt, id);
    }

    // Valve should be approximately 50% open after half stroke time
    double opening = esdValve.getPercentValveOpening();
    assertTrue(opening > 40.0 && opening < 60.0,
        "Valve should be ~50% open after half stroke time, but was " + opening + "%");
    assertTrue(esdValve.isClosing(), "Valve should still be closing");
    assertFalse(esdValve.hasTripCompleted(), "Trip should not be completed yet");
  }

  @Test
  void testFullClosureCompletion() {
    // De-energize and simulate full closure
    esdValve.deEnergize();

    double dt = 1.0;
    UUID id = UUID.randomUUID();

    // Simulate full stroke time
    for (int i = 0; i < 12; i++) { // Extra time to ensure completion
      esdValve.runTransient(dt, id);
    }

    assertEquals(0.0, esdValve.getPercentValveOpening(), 0.1,
        "Valve should be fully closed after stroke time");
    assertFalse(esdValve.isClosing(), "Valve should not be closing anymore");
    assertTrue(esdValve.hasTripCompleted(), "Trip should be completed");
  }

  @Test
  void testTripMethod() {
    // Test trip() method (should work same as deEnergize)
    esdValve.trip();

    assertFalse(esdValve.isEnergized(), "Valve should be de-energized after trip");
    assertTrue(esdValve.isClosing(), "Valve should be closing after trip");
  }

  @Test
  void testReset() {
    // De-energize, close, then reset
    esdValve.deEnergize();

    double dt = 1.0;
    UUID id = UUID.randomUUID();
    for (int i = 0; i < 12; i++) {
      esdValve.runTransient(dt, id);
    }

    // Reset valve
    esdValve.reset();

    assertTrue(esdValve.isEnergized(), "Valve should be energized after reset");
    assertEquals(100.0, esdValve.getPercentValveOpening(), 0.1,
        "Valve should be fully open after reset");
    assertFalse(esdValve.isClosing(), "Valve should not be closing after reset");
    assertFalse(esdValve.hasTripCompleted(), "Trip completed flag should be cleared");
  }

  @Test
  void testStrokeTimeConfiguration() {
    esdValve.setStrokeTime(20.0);
    assertEquals(20.0, esdValve.getStrokeTime(), 0.1, "Stroke time should be 20 seconds");

    // Test minimum stroke time enforcement
    esdValve.setStrokeTime(0.1);
    assertTrue(esdValve.getStrokeTime() >= 0.5,
        "Stroke time should be enforced to minimum 0.5 seconds");
  }

  @Test
  void testFailSafePosition() {
    // Test default fail-closed behavior
    assertEquals(0.0, esdValve.getFailSafePosition(), 0.1,
        "Default fail-safe position should be closed");

    // Test custom fail-safe position (e.g., fail-open valve)
    esdValve.setFailSafePosition(100.0);
    assertEquals(100.0, esdValve.getFailSafePosition(), 0.1,
        "Fail-safe position should be settable");

    // Test clamping
    esdValve.setFailSafePosition(150.0);
    assertEquals(100.0, esdValve.getFailSafePosition(), 0.1,
        "Fail-safe position should be clamped to 100%");
  }

  @Test
  void testPartialStrokeTest() {
    // Start partial stroke test
    esdValve.startPartialStrokeTest(80.0);

    assertTrue(esdValve.isPartialStrokeTestActive(), "Partial stroke test should be active");

    // Simulate partial closure
    double dt = 1.0;
    UUID id = UUID.randomUUID();
    for (int i = 0; i < 3; i++) { // Partial closure time
      esdValve.runTransient(dt, id);
    }

    double opening = esdValve.getPercentValveOpening();
    assertTrue(opening >= 80.0 && opening < 100.0,
        "Valve should be between 80-100% during PST, but was " + opening + "%");

    // Complete test
    esdValve.completePartialStrokeTest();

    assertFalse(esdValve.isPartialStrokeTestActive(), "Partial stroke test should be inactive");
    assertEquals(100.0, esdValve.getPercentValveOpening(), 0.1,
        "Valve should return to 100% after PST");
  }

  @Test
  void testTimeElapsedTracking() {
    esdValve.deEnergize();

    assertEquals(0.0, esdValve.getTimeElapsedSinceTrip(), 0.01, "Initial elapsed time should be 0");

    double dt = 1.0;
    UUID id = UUID.randomUUID();
    for (int i = 0; i < 5; i++) {
      esdValve.runTransient(dt, id);
    }

    assertEquals(5.0, esdValve.getTimeElapsedSinceTrip(), 0.1,
        "Elapsed time should be tracked correctly");
  }

  @Test
  void testToString() {
    String description = esdValve.toString();

    assertTrue(description.contains("ESD Valve"), "Description should identify valve type");
    assertTrue(description.contains("ENERGIZED"), "Description should show energized state");

    esdValve.deEnergize();
    description = esdValve.toString();

    assertTrue(description.contains("TRIPPING"), "Description should show tripping state");
  }

  @Test
  void testEnergizeAfterDeEnergize() {
    // De-energize then re-energize (abort closure)
    esdValve.deEnergize();

    double dt = 1.0;
    UUID id = UUID.randomUUID();
    esdValve.runTransient(dt, id); // Partial closure

    double openingDuringClosure = esdValve.getPercentValveOpening();
    assertTrue(openingDuringClosure < 100.0, "Valve should have started closing");

    // Re-energize
    esdValve.energize();

    assertTrue(esdValve.isEnergized(), "Valve should be energized");
    assertFalse(esdValve.isClosing(), "Valve should stop closing");
    // Note: Valve position remains where it stopped; separate command needed to reopen
  }
}
