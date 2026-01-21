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
 * Test class for HIPPSValve functionality.
 * 
 * <p>
 * Tests cover:
 * <ul>
 * <li>Basic HIPPS configuration and operation</li>
 * <li>Voting logic (1oo1, 1oo2, 2oo2, 2oo3)</li>
 * <li>Transient response and closure timing</li>
 * <li>Transmitter redundancy and failure scenarios</li>
 * <li>Partial stroke testing</li>
 * <li>SIL rating and proof test tracking</li>
 * <li>Integration with PSV (HIPPS prevents PSV from lifting)</li>
 * <li>Spurious trip detection</li>
 * </ul>
 *
 * @author ESOL
 */
class HIPPSValveTest {
  private SystemInterface testSystem;
  private Stream feedStream;
  private Separator separator;
  private HIPPSValve hippsValve;

  @BeforeEach
  void setUp() {
    // Create a high-pressure gas system
    testSystem = new SystemSrkEos(298.15, 80.0); // 80 bara - high pressure system
    testSystem.addComponent("methane", 85.0, "mol/sec");
    testSystem.addComponent("ethane", 10.0, "mol/sec");
    testSystem.addComponent("propane", 5.0, "mol/sec");
    testSystem.setMixingRule("classic");
    testSystem.setMultiPhaseCheck(true);
    testSystem.createDatabase(true);
    testSystem.init(0);

    // Create feed stream
    feedStream = new Stream("Feed", testSystem);
    feedStream.setFlowRate(15000.0, "kg/hr");
    feedStream.setTemperature(40.0, "C");
    feedStream.setPressure(80.0, "bara");
    feedStream.run();

    // Create separator (protected equipment)
    separator = new Separator("High Pressure Separator", feedStream);
    separator.run();
  }

  @Test
  void testBasicHIPPSConfiguration() {
    // Create HIPPS valve
    hippsValve = new HIPPSValve("HIPPS-XV-001", feedStream);

    // Verify initial state
    assertEquals(100.0, hippsValve.getPercentValveOpening(), 0.1);
    assertFalse(hippsValve.hasTripped());
    assertTrue(hippsValve.isTripEnabled());
    assertEquals(3, hippsValve.getSILRating()); // Default SIL 3
    assertEquals(HIPPSValve.VotingLogic.TWO_OUT_OF_THREE, hippsValve.getVotingLogic());
  }

  @Test
  void testOneOutOfOneVotingLogic() {
    // Single transmitter configuration (simple, SIL 1)
    hippsValve = new HIPPSValve("HIPPS-XV-001", feedStream);
    hippsValve.setVotingLogic(HIPPSValve.VotingLogic.ONE_OUT_OF_ONE);
    hippsValve.setSILRating(1);

    PressureTransmitter PT1 = new PressureTransmitter("PT-101", feedStream);
    AlarmConfig alarmConfig =
        AlarmConfig.builder().highHighLimit(90.0).deadband(2.0).delay(0.5).unit("bara").build();
    PT1.setAlarmConfig(alarmConfig);

    hippsValve.addPressureTransmitter(PT1);

    // Normal operation - pressure below HIHI
    PT1.evaluateAlarm(feedStream.getPressure("bara"), 0.1, 0.0);
    hippsValve.runTransient(0.1, UUID.randomUUID());
    assertFalse(hippsValve.hasTripped());
    assertEquals(100.0, hippsValve.getPercentValveOpening(), 0.1);

    // Simulate overpressure scenario
    feedStream.setPressure(92.0, "bara");
    feedStream.run();
    PT1.evaluateAlarm(92.0, 1.0, 1.0); // Trigger alarm after delay

    hippsValve.runTransient(0.1, UUID.randomUUID());

    // HIPPS should trip with 1oo1 logic
    assertTrue(hippsValve.hasTripped());
    assertEquals(0.0, hippsValve.getPercentValveOpening(), 0.1);
  }

  @Test
  void testOneOutOfTwoVotingLogic() {
    // High availability configuration - any one transmitter trips
    hippsValve = new HIPPSValve("HIPPS-XV-002", feedStream);
    hippsValve.setVotingLogic(HIPPSValve.VotingLogic.ONE_OUT_OF_TWO);

    PressureTransmitter PT1 = new PressureTransmitter("PT-101A", feedStream);
    PressureTransmitter PT2 = new PressureTransmitter("PT-101B", feedStream);

    AlarmConfig alarmConfig =
        AlarmConfig.builder().highHighLimit(90.0).deadband(2.0).delay(0.5).unit("bara").build();
    PT1.setAlarmConfig(alarmConfig);
    PT2.setAlarmConfig(alarmConfig);

    hippsValve.addPressureTransmitter(PT1);
    hippsValve.addPressureTransmitter(PT2);

    // Normal operation
    PT1.evaluateAlarm(feedStream.getPressure("bara"), 0.1, 0.0);
    PT2.evaluateAlarm(feedStream.getPressure("bara"), 0.1, 0.0);
    hippsValve.runTransient(0.1, UUID.randomUUID());
    assertEquals(0, hippsValve.getActiveTransmitterCount());
    assertFalse(hippsValve.hasTripped());

    // Only PT1 sees overpressure (e.g., process upset or PT2 failed safe)
    feedStream.setPressure(92.0, "bara");
    feedStream.run();
    PT1.evaluateAlarm(92.0, 1.0, 1.0);

    hippsValve.runTransient(0.1, UUID.randomUUID());

    // Should trip with 1oo2 logic (only one transmitter needed)
    assertTrue(hippsValve.hasTripped());
  }

  @Test
  void testTwoOutOfTwoVotingLogic() {
    // Low spurious trip configuration - both must trip
    hippsValve = new HIPPSValve("HIPPS-XV-003", feedStream);
    hippsValve.setVotingLogic(HIPPSValve.VotingLogic.TWO_OUT_OF_TWO);

    PressureTransmitter PT1 = new PressureTransmitter("PT-101A", feedStream);
    PressureTransmitter PT2 = new PressureTransmitter("PT-101B", feedStream);

    AlarmConfig alarmConfig =
        AlarmConfig.builder().highHighLimit(90.0).deadband(2.0).delay(0.5).unit("bara").build();
    PT1.setAlarmConfig(alarmConfig);
    PT2.setAlarmConfig(alarmConfig);

    hippsValve.addPressureTransmitter(PT1);
    hippsValve.addPressureTransmitter(PT2);

    // Only PT1 in alarm (could be spurious or PT failure)
    feedStream.setPressure(92.0, "bara");
    feedStream.run();
    PT1.evaluateAlarm(92.0, 1.0, 1.0);

    hippsValve.runTransient(0.1, UUID.randomUUID());

    // Should NOT trip with 2oo2 logic (need both transmitters)
    assertFalse(hippsValve.hasTripped());
    assertEquals(1, hippsValve.getActiveTransmitterCount());

    // Now PT2 also alarms
    PT2.evaluateAlarm(92.0, 1.0, 2.0);

    hippsValve.runTransient(0.1, UUID.randomUUID());

    // Now should trip (both in alarm)
    assertTrue(hippsValve.hasTripped());
    assertEquals(2, hippsValve.getActiveTransmitterCount());
  }

  @Test
  void testTwoOutOfThreeVotingLogic() {
    // Balanced configuration - typical for SIL 2/3
    hippsValve = new HIPPSValve("HIPPS-XV-004", feedStream);
    hippsValve.setVotingLogic(HIPPSValve.VotingLogic.TWO_OUT_OF_THREE);
    hippsValve.setSILRating(3);

    PressureTransmitter PT1 = new PressureTransmitter("PT-101A", feedStream);
    PressureTransmitter PT2 = new PressureTransmitter("PT-101B", feedStream);
    PressureTransmitter PT3 = new PressureTransmitter("PT-101C", feedStream);

    AlarmConfig alarmConfig =
        AlarmConfig.builder().highHighLimit(90.0).deadband(2.0).delay(0.5).unit("bara").build();
    PT1.setAlarmConfig(alarmConfig);
    PT2.setAlarmConfig(alarmConfig);
    PT3.setAlarmConfig(alarmConfig);

    hippsValve.addPressureTransmitter(PT1);
    hippsValve.addPressureTransmitter(PT2);
    hippsValve.addPressureTransmitter(PT3);

    // Simulate overpressure
    feedStream.setPressure(92.0, "bara");
    feedStream.run();

    // Only PT1 in alarm
    PT1.evaluateAlarm(92.0, 1.0, 1.0);
    hippsValve.runTransient(0.1, UUID.randomUUID());
    assertFalse(hippsValve.hasTripped()); // Need 2 out of 3
    assertEquals(1, hippsValve.getActiveTransmitterCount());

    // PT2 also alarms
    PT2.evaluateAlarm(92.0, 1.0, 2.0);
    hippsValve.runTransient(0.1, UUID.randomUUID());

    // Now should trip (2 out of 3)
    assertTrue(hippsValve.hasTripped());
    assertEquals(2, hippsValve.getActiveTransmitterCount());

    // Verify PT3 status (should also be in alarm but not required for trip)
    PT3.evaluateAlarm(92.0, 1.0, 3.0);
    assertEquals(3, hippsValve.getActiveTransmitterCount()); // All 3 now active
  }

  @Test
  void testClosureTime() {
    hippsValve = new HIPPSValve("HIPPS-XV-005", feedStream);
    hippsValve.setClosureTime(5.0); // 5 second closure

    assertEquals(5.0, hippsValve.getClosureTime(), 0.01);
    assertEquals(5.0, hippsValve.getClosingTravelTime(), 0.01); // Should sync with travel time
  }

  @Test
  void testResetAndReopen() {
    hippsValve = new HIPPSValve("HIPPS-XV-006", feedStream);
    hippsValve.setVotingLogic(HIPPSValve.VotingLogic.ONE_OUT_OF_ONE);

    PressureTransmitter PT1 = new PressureTransmitter("PT-101", feedStream);
    AlarmConfig alarmConfig =
        AlarmConfig.builder().highHighLimit(90.0).deadband(2.0).delay(0.5).unit("bara").build();
    PT1.setAlarmConfig(alarmConfig);
    hippsValve.addPressureTransmitter(PT1);

    // Trip the HIPPS
    feedStream.setPressure(92.0, "bara");
    feedStream.run();
    PT1.evaluateAlarm(92.0, 1.0, 1.0);
    hippsValve.runTransient(0.1, UUID.randomUUID());
    assertTrue(hippsValve.hasTripped());

    // Try to open while tripped - should fail
    hippsValve.setPercentValveOpening(50.0);
    assertEquals(0.0, hippsValve.getPercentValveOpening(), 0.1); // Still closed

    // Reset the HIPPS
    hippsValve.reset();
    assertFalse(hippsValve.hasTripped());

    // Now should be able to open
    hippsValve.setPercentValveOpening(50.0);
    assertEquals(50.0, hippsValve.getPercentValveOpening(), 0.1);

    // And fully open
    hippsValve.setPercentValveOpening(100.0);
    assertEquals(100.0, hippsValve.getPercentValveOpening(), 0.1);
  }

  @Test
  void testBypassMode() {
    hippsValve = new HIPPSValve("HIPPS-XV-007", feedStream);
    hippsValve.setVotingLogic(HIPPSValve.VotingLogic.ONE_OUT_OF_ONE);

    PressureTransmitter PT1 = new PressureTransmitter("PT-101", feedStream);
    AlarmConfig alarmConfig =
        AlarmConfig.builder().highHighLimit(90.0).deadband(2.0).delay(0.5).unit("bara").build();
    PT1.setAlarmConfig(alarmConfig);
    hippsValve.addPressureTransmitter(PT1);

    // Disable trip (bypass mode)
    hippsValve.setTripEnabled(false);
    assertFalse(hippsValve.isTripEnabled());

    // Simulate overpressure
    feedStream.setPressure(92.0, "bara");
    feedStream.run();
    PT1.evaluateAlarm(92.0, 1.0, 1.0);
    hippsValve.runTransient(0.1, UUID.randomUUID());

    // Should NOT trip when bypassed
    assertFalse(hippsValve.hasTripped());
    assertEquals(100.0, hippsValve.getPercentValveOpening(), 0.1);
  }

  @Test
  void testPartialStrokeTest() {
    hippsValve = new HIPPSValve("HIPPS-XV-008", feedStream);

    // Valve initially fully open
    assertEquals(100.0, hippsValve.getPercentValveOpening(), 0.1);
    assertFalse(hippsValve.isPartialStrokeTestActive());

    // Perform 15% stroke test
    hippsValve.performPartialStrokeTest(0.15);
    assertTrue(hippsValve.isPartialStrokeTestActive());

    // During first half of test: valve should move to 85% (15% stroke)
    hippsValve.runTransient(0.5, UUID.randomUUID());
    assertEquals(85.0, hippsValve.getPercentValveOpening(), 0.1);

    // Continue test
    hippsValve.runTransient(2.0, UUID.randomUUID());
    assertEquals(100.0, hippsValve.getPercentValveOpening(), 0.1); // Returning to full open

    // Test should complete after default duration (5 seconds)
    hippsValve.runTransient(3.0, UUID.randomUUID());
    assertFalse(hippsValve.isPartialStrokeTestActive());
    assertEquals(100.0, hippsValve.getPercentValveOpening(), 0.1); // Back to full open
  }

  @Test
  void testProofTestTracking() {
    hippsValve = new HIPPSValve("HIPPS-XV-009", feedStream);

    // Set annual proof test interval
    hippsValve.setProofTestInterval(8760.0); // 1 year in hours

    // Initially not due
    assertFalse(hippsValve.isProofTestDue());
    assertEquals(0.0, hippsValve.getTimeSinceProofTest(), 0.1);

    // Run for 1 year of operation (simulated)
    double hoursInYear = 8760.0;
    double secondsInYear = hoursInYear * 3600.0;
    double timeStep = 3600.0; // 1 hour timestep

    for (double t = 0; t < secondsInYear; t += timeStep) {
      hippsValve.runTransient(timeStep, UUID.randomUUID());
    }

    // Should now be due
    assertTrue(hippsValve.isProofTestDue());
    assertTrue(hippsValve.getTimeSinceProofTest() >= 8760.0);

    // Perform proof test
    hippsValve.performProofTest();
    assertFalse(hippsValve.isProofTestDue());
    assertEquals(0.0, hippsValve.getTimeSinceProofTest(), 0.1);
  }

  @Test
  void testSpuriousTripTracking() {
    hippsValve = new HIPPSValve("HIPPS-XV-010", feedStream);

    assertEquals(0, hippsValve.getSpuriousTripCount());

    // Record spurious trips (would be done by operator or diagnostics system)
    hippsValve.recordSpuriousTrip();
    assertEquals(1, hippsValve.getSpuriousTripCount());

    hippsValve.recordSpuriousTrip();
    hippsValve.recordSpuriousTrip();
    assertEquals(3, hippsValve.getSpuriousTripCount());
  }

  @Test
  void testHIPPSPreventsOverpressure() {
    // Scenario: HIPPS trips at 90 bara, preventing pressure from reaching PSV setpoint (100 bara)
    // This test verifies that HIPPS provides effective primary protection

    // Create HIPPS with 2oo3 voting
    hippsValve = new HIPPSValve("HIPPS-XV-011", feedStream);
    hippsValve.setVotingLogic(HIPPSValve.VotingLogic.TWO_OUT_OF_THREE);
    hippsValve.setClosureTime(3.0);

    // Setup pressure transmitters with HIPPS trip at 90 bara
    PressureTransmitter PT1 = new PressureTransmitter("PT-101A", feedStream);
    PressureTransmitter PT2 = new PressureTransmitter("PT-101B", feedStream);
    PressureTransmitter PT3 = new PressureTransmitter("PT-101C", feedStream);

    // No delay for immediate trip when pressure exceeds 90 bara
    AlarmConfig hippsAlarm =
        AlarmConfig.builder().highHighLimit(90.0).deadband(2.0).delay(0.0).unit("bara").build();

    PT1.setAlarmConfig(hippsAlarm);
    PT2.setAlarmConfig(hippsAlarm);
    PT3.setAlarmConfig(hippsAlarm);

    hippsValve.addPressureTransmitter(PT1);
    hippsValve.addPressureTransmitter(PT2);
    hippsValve.addPressureTransmitter(PT3);

    // Simulate pressure at 92 bara (above HIPPS trip point, below PSV setpoint)
    feedStream.setPressure(92.0, "bara");
    feedStream.run();

    // Evaluate alarms - 2 out of 3 transmitters should detect high pressure
    PT1.evaluateAlarm(92.0, 0.1, 1.0);
    PT2.evaluateAlarm(92.0, 0.1, 1.0);
    PT3.evaluateAlarm(92.0, 0.1, 1.0);

    // Run HIPPS transient logic
    hippsValve.runTransient(0.1, UUID.randomUUID());

    // Verify HIPPS tripped
    assertTrue(hippsValve.hasTripped(), "HIPPS should trip at 92 bara (above 90 bara trip point)");
    assertEquals(0.0, hippsValve.getPercentValveOpening(), 0.01,
        "HIPPS valve should be fully closed after trip");

    // Verify pressure is still below typical PSV setpoint (100 bara)
    assertTrue(feedStream.getPressure() < 100.0,
        "Pressure (92 bara) is below PSV setpoint (100 bara) - HIPPS provides primary protection");

    // In a real system, the PSV would be backup protection at 100 bara
    // Since HIPPS trips at 90 bara, pressure never reaches PSV setpoint
    // This demonstrates HIPPS preventing overpressure and eliminating need for PSV to open
  }

  @Test
  void testDiagnosticsOutput() {
    hippsValve = new HIPPSValve("HIPPS-XV-012", feedStream);
    hippsValve.setVotingLogic(HIPPSValve.VotingLogic.TWO_OUT_OF_THREE);
    hippsValve.setSILRating(3);

    PressureTransmitter PT1 = new PressureTransmitter("PT-101A", feedStream);
    PressureTransmitter PT2 = new PressureTransmitter("PT-101B", feedStream);
    PressureTransmitter PT3 = new PressureTransmitter("PT-101C", feedStream);

    hippsValve.addPressureTransmitter(PT1);
    hippsValve.addPressureTransmitter(PT2);
    hippsValve.addPressureTransmitter(PT3);

    // Get diagnostics
    String diagnostics = hippsValve.getDiagnostics();

    // Verify key information present
    assertTrue(diagnostics.contains("HIPPS DIAGNOSTICS"));
    assertTrue(diagnostics.contains("SIL 3"));
    assertTrue(diagnostics.contains("2oo3"));
    assertTrue(diagnostics.contains("Transmitter Status"));
    assertTrue(diagnostics.contains("Proof Test"));
  }

  @Test
  void testToString() {
    hippsValve = new HIPPSValve("HIPPS-XV-013", feedStream);
    hippsValve.setSILRating(2);

    String str = hippsValve.toString();

    assertTrue(str.contains("HIPPS Valve"));
    assertTrue(str.contains("SIL Rating: 2"));
    assertTrue(str.contains("Opening:"));
    assertTrue(str.contains("Status:"));
  }

  @Test
  void testTransmitterFailureScenario() {
    // Scenario: One transmitter fails, system should still provide protection

    hippsValve = new HIPPSValve("HIPPS-XV-014", feedStream);
    hippsValve.setVotingLogic(HIPPSValve.VotingLogic.TWO_OUT_OF_THREE);

    PressureTransmitter PT1 = new PressureTransmitter("PT-101A", feedStream);
    PressureTransmitter PT2 = new PressureTransmitter("PT-101B", feedStream);
    PressureTransmitter PT3 = new PressureTransmitter("PT-101C", feedStream);

    AlarmConfig alarmConfig =
        AlarmConfig.builder().highHighLimit(90.0).deadband(2.0).delay(0.5).unit("bara").build();

    PT1.setAlarmConfig(alarmConfig);
    PT2.setAlarmConfig(alarmConfig);
    PT3.setAlarmConfig(alarmConfig);

    hippsValve.addPressureTransmitter(PT1);
    hippsValve.addPressureTransmitter(PT2);
    hippsValve.addPressureTransmitter(PT3);

    // Simulate PT2 failure (remove from array - like maintenance bypass)
    hippsValve.removePressureTransmitter(PT2);
    assertEquals(2, hippsValve.getPressureTransmitters().size());

    // System now operates with 2 transmitters
    // For safety, could change to 1oo2 voting when one transmitter is bypassed
    hippsValve.setVotingLogic(HIPPSValve.VotingLogic.ONE_OUT_OF_TWO);

    // Simulate overpressure
    feedStream.setPressure(92.0, "bara");
    feedStream.run();
    PT1.evaluateAlarm(92.0, 1.0, 1.0);

    hippsValve.runTransient(0.1, UUID.randomUUID());

    // Should still trip with 1oo2 (PT1 in alarm)
    assertTrue(hippsValve.hasTripped());
  }

  @Test
  void testSILRatingValidation() {
    hippsValve = new HIPPSValve("HIPPS-XV-015", feedStream);

    // Valid SIL ratings
    hippsValve.setSILRating(1);
    assertEquals(1, hippsValve.getSILRating());

    hippsValve.setSILRating(2);
    assertEquals(2, hippsValve.getSILRating());

    hippsValve.setSILRating(3);
    assertEquals(3, hippsValve.getSILRating());

    // Invalid ratings should be ignored
    hippsValve.setSILRating(4);
    assertEquals(3, hippsValve.getSILRating()); // Should remain at 3

    hippsValve.setSILRating(0);
    assertEquals(3, hippsValve.getSILRating()); // Should remain at 3
  }
}
