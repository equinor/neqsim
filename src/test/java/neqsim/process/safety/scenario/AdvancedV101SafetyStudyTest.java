package neqsim.process.safety.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.scenario.AdvancedV101SafetyStudy.CaseBundle;

/**
 * Strict native two-inventory integration tests with actual held inputs, voting, physical valve travel and FAIL
 * controls.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class AdvancedV101SafetyStudyTest {
  /**
   * The public command-line entry point executes and exports a real selected strict profile.
   *
   * @param output temporary export directory
   * @throws IOException if actual export cannot be read
   */
  @Test
  void publicMainExportsSelectedNativeCase(@TempDir Path output) throws IOException {
    AdvancedV101SafetyStudy.main(new String[] {output.toString(), "0.1", "NORMAL"});
    assertTrue(Files.exists(output.resolve("ADV-V101-NORMAL-DT-0.1.json")));
    assertTrue(Files.exists(output.resolve("ADV-V101-NORMAL-DT-0.1.csv")));
  }

  /** A no-demand process and a BPCS ramp cannot receive first-sample credit for a later unintended trip. */
  @Test
  void normalAndControlRampHaveFullWindowNoTripEvidence() throws IOException {
    for (String mode : new String[] {"NORMAL", "CONTROL_RAMP"}) {
      CaseBundle bundle = run(mode, 0.1);
      assertTrue(nativePassed(bundle));
      for (Map<String, Object> row : bundle.getRows()) {
        assertTrue(number(row, "20-ESDV-001.opening_pct") >= 49.9);
        assertEquals(50.0, number(row, "20-HIPPS-XV-001.opening_pct"));
      }
      assertNull(function(bundle, "SIF-HIPPS-001").get("firstVoteSeconds"));
      assertNull(function(bundle, "SIF-ESD-001").get("firstVoteSeconds"));
      assertFreshAndCompleteWindow(bundle, mode.equals("CONTROL_RAMP") ? 60.0 : 12.0);
    }
  }

  /** One actual false-high transmitter cannot vote, while two actual false-high transmitters cause a spurious trip. */
  @Test
  void twoOutOfThreeSeparatesOneFalseHighFromTwoFalseHighs() throws IOException {
    CaseBundle one = run("ONE_FALSE_HIGH", 0.1);
    CaseBundle two = run("TWO_FALSE_HIGH", 0.1);
    assertTrue(nativePassed(one));
    assertTrue(nativePassed(two));
    assertNull(function(one, "SIF-HIPPS-001").get("firstVoteSeconds"));
    assertNotNull(function(two, "SIF-HIPPS-001").get("firstVoteSeconds"));
    assertEquals(50.0, last(one, "20-HIPPS-XV-001.opening_pct"));
    assertEquals(0.0, last(two, "20-HIPPS-XV-001.opening_pct"));
    for (Map<String, Object> row : one.getRows()) {
      if (number(row, "event_relative_time_s") > 0.0) {
        assertEquals(42.0, number(row, "20-PT-101A.held_value"));
        assertTrue(number(row, "20-PT-001.held_value") < 41.0);
      }
    }
    assertFreshAndCompleteWindow(one, 12.0);
    assertFreshAndCompleteWindow(two, 12.0);
  }

  /** Actual healthy isolation is contrasted with the same initiating event and all protection inhibited. */
  @Test
  void healthyProtectionAndInhibitedNegativeControlRetainTheirObservedVerdicts() throws IOException {
    CaseBundle healthy = run("TRUE_DEMAND", 0.1);
    CaseBundle inhibited = run("ALL_INHIBITED", 0.1);
    assertTrue(nativePassed(healthy));
    assertFalse(nativePassed(inhibited));
    assertTrue(maximum(healthy, "20-V-101.pressure_bara") < 43.0);
    assertTrue(maximum(inhibited, "20-V-101.pressure_bara") > 43.0);
    assertEquals(0.0, last(healthy, "20-HIPPS-XV-001.opening_pct"));
    assertEquals(0.0, last(healthy, "20-ESDV-001.opening_pct"));
    assertEquals(50.0, last(inhibited, "20-HIPPS-XV-001.opening_pct"));
    assertEquals(50.0, last(inhibited, "20-ESDV-001.opening_pct"));
    Map<?, ?> envelope = criterion(inhibited, "V101-PRESSURE-ENVELOPE");
    assertFalse((Boolean) envelope.get("passed"));
    assertNotNull(envelope.get("firstViolationSeconds"));
    assertFreshAndCompleteWindow(healthy, 12.0);
    assertFreshAndCompleteWindow(inhibited, 12.0);
  }

  /** A stuck-normal physical signal and a bypass leave fixed voting cardinality, rather than silently degrading it. */
  @Test
  void stuckSignalsAndBypassesUseTheActualRemainingVoteCounts() throws IOException {
    CaseBundle oneStuck = run("ONE_STUCK_NORMAL", 0.1);
    CaseBundle twoStuck = run("TWO_STUCK_NORMAL", 0.1);
    CaseBundle oneBypassed = run("ONE_BYPASSED", 0.1);
    CaseBundle twoBypassed = run("TWO_BYPASSED", 0.1);
    assertTrue(nativePassed(oneStuck));
    assertFalse(nativePassed(twoStuck));
    assertTrue(nativePassed(oneBypassed));
    assertFalse(nativePassed(twoBypassed));
    assertNull(function(twoStuck, "SIF-HIPPS-001").get("firstVoteSeconds"));
    assertNull(function(twoBypassed, "SIF-HIPPS-001").get("firstVoteSeconds"));
    assertTrue(maximum(oneStuck, "20-PT-001.held_value") > 41.0);
    for (Map<String, Object> row : oneStuck.getRows()) {
      if (number(row, "event_relative_time_s") > 0.0) {
        assertEquals(40.0, number(row, "20-PT-101A.held_value"));
      }
    }
    assertEquals(0.0, last(oneBypassed, "20-HIPPS-XV-001.opening_pct"));
    assertEquals(50.0, last(twoBypassed, "20-HIPPS-XV-001.opening_pct"));
  }

  /** A trip command or later eventual closure cannot satisfy a required timely physical closure. */
  @Test
  void failedAndSlowActuatorsRemainSimulationFailures() throws IOException {
    CaseBundle failed = run("HIPPS_FAILED", 0.1);
    CaseBundle slow = run("HIPPS_SLOW", 0.1);
    assertFalse(nativePassed(failed));
    assertFalse(nativePassed(slow));
    assertNotNull(function(failed, "SIF-HIPPS-001").get("finalElementActuationSeconds"));
    assertNotNull(function(slow, "SIF-HIPPS-001").get("finalElementActuationSeconds"));
    assertEquals(50.0, last(failed, "20-HIPPS-XV-001.opening_pct"));
    assertFalse(((ESDValve) failed.getConfiguredProcess().getUnit("20-HIPPS-XV-001")).isActuatorAvailable());
    assertEquals(0.0, last(slow, "20-HIPPS-XV-001.opening_pct"));
    assertFalse((Boolean) criterion(slow, "ISOLATION-CLOSURE").get("passed"));
  }

  /** The ESD backup and an inhibited HIPPS candidate are distinct required functional scopes on real hardware. */
  @Test
  void esdBackupDoesNotGiveMissingHippsClosureCredit() throws IOException {
    CaseBundle esd = run("ESD_ONLY", 0.1);
    CaseBundle hippsInhibited = run("HIPPS_INHIBITED", 0.1);
    assertTrue(nativePassed(esd));
    assertFalse(nativePassed(hippsInhibited));
    assertEquals(0.0, last(esd, "20-ESDV-001.opening_pct"));
    assertEquals(50.0, last(esd, "20-HIPPS-XV-001.opening_pct"));
    assertTrue(maximum(esd, "20-V-101.pressure_bara") < 43.0);
  }

  /** An actual local push button drives parallel native actions and both real valves close together. */
  @Test
  void manualEmergencyTripUsesActualParallelFinalElements() throws IOException {
    CaseBundle manual = run("MANUAL", 0.1);
    assertTrue(nativePassed(manual));
    assertTrue((Boolean) criterion(manual, "MANUAL-UPSTREAM-ISOLATION-CLOSURE").get("passed"));
    for (Map<String, Object> row : manual.getRows()) {
      assertEquals(number(row, "20-ESDV-001.opening_pct"), number(row, "20-HIPPS-XV-001.opening_pct"));
    }
    assertEquals(0.0, last(manual, "20-ESDV-001.opening_pct"));
    assertEquals(0.0, last(manual, "20-HIPPS-XV-001.opening_pct"));
    assertNotNull(function(manual, "SIF-MANUAL-001").get("finalElementActuationSeconds"));
  }

  /**
   * Explicit current-input permissives, archived native epochs and real reopening/retrip prove reusable manual logic.
   */
  @Test
  void manualResetRefusesActiveInputAndRetainsBothTripEpochs() throws IOException {
    CaseBundle manual = run("MANUAL_RESET_RETRIP", 0.1);
    assertTrue(nativePassed(manual));
    List<?> events = (List<?>) manual.toMap().get("operatorEvents");
    boolean refused = false;
    boolean rearmed = false;
    boolean secondTrip = false;
    for (Object value : events) {
      Map<?, ?> event = (Map<?, ?>) value;
      if (event.get("action").equals("RESET_REQUEST")) {
        refused = !(Boolean) event.get("accepted");
      }
      if (event.get("action").equals("OPERATOR_REARM")) {
        rearmed = (Boolean) event.get("accepted");
      }
      if (event.get("action").equals("SECOND_MANUAL_TRIP")) {
        secondTrip = (Boolean) event.get("accepted");
      }
    }
    assertTrue(refused);
    assertTrue(rearmed);
    assertTrue(secondTrip);
    assertEquals(3, ((List<?>) manual.toMap().get("priorFunctionEpochs")).size());
    assertEquals(0.0, last(manual, "20-ESDV-001.opening_pct"));
    assertEquals(0.0, last(manual, "20-HIPPS-XV-001.opening_pct"));
    boolean reopenedAfterFirstClosure = false;
    for (Map<String, Object> row : manual.getRows()) {
      if (number(row, "event_relative_time_s") > 3.0 && number(row, "event_relative_time_s") < 8.0
          && number(row, "20-ESDV-001.opening_pct") == 50.0) {
        reopenedAfterFirstClosure = true;
      }
    }
    assertTrue(reopenedAfterFirstClosure);
  }

  /** Native channel latches preserve qualified pulses; a logic pause resumes rather than inventing a fresh signal. */
  @Test
  void pauseAndAsynchronousQualifiedPulsesHaveExplicitNativeSemantics() throws IOException {
    CaseBundle paused = run("PAUSE_RESUME", 0.1);
    CaseBundle pulses = run("ASYNC_QUALIFIED_PULSES", 0.1);
    assertTrue(nativePassed(paused));
    assertTrue(nativePassed(pulses));
    List<?> trace = (List<?>) function(pulses, "SIF-HIPPS-001").get("trace");
    boolean retainedQualifiedPulse = false;
    for (Object value : trace) {
      Map<?, ?> sample = (Map<?, ?>) value;
      List<?> channels = (List<?>) sample.get("channels");
      Map<?, ?> a = (Map<?, ?>) channels.get(0);
      if (Boolean.TRUE.equals(sample.get("vote")) && Boolean.TRUE.equals(a.get("tripped"))
          && ((Number) a.get("rawValue")).doubleValue() < 41.0) {
        retainedQualifiedPulse = true;
      }
    }
    assertTrue(retainedQualifiedPulse);
    assertEquals(0.1, ((Number) function(paused, "SIF-HIPPS-001").get("firstVoteSeconds")).doubleValue(), 1.0e-12);
    assertEquals(0.0, last(paused, "20-HIPPS-XV-001.opening_pct"));
  }

  /** The same frozen design is replayed at half dt; no criterion or solver tolerance is relaxed. */
  @Test
  void healthyCaseRefinementAndReplayPreserveActualEvidence() throws IOException {
    CaseBundle coarse = run("TRUE_DEMAND", 0.1);
    CaseBundle refined = run("TRUE_DEMAND", 0.05);
    assertTrue(nativePassed(coarse));
    assertTrue(nativePassed(refined));
    assertTrue(Math.abs(maximum(coarse, "20-V-101.pressure_bara") - maximum(refined, "20-V-101.pressure_bara")) < 0.25);
    assertTrue(Math.abs(firstClosed(coarse, "20-HIPPS-XV-001.opening_pct")
        - firstClosed(refined, "20-HIPPS-XV-001.opening_pct")) <= 0.2);
    assertFreshAndCompleteWindow(refined, 12.0);
    ProcessSystem source = AdvancedV101SafetyStudy.buildSource(AdvancedV101SafetyStudy.defaultSettings());
    double initial = ((Separator) source.getUnit("20-V-101")).getThermoSystem().getInternalEnergy();
    CaseBundle replay = AdvancedV101SafetyStudy.createCase(source, AdvancedV101SafetyStudy.defaultSettings(),
        "TRUE_DEMAND", 0.1);
    assertEquals(coarse.toMap().get("nativeResult"), replay.runStrict().toMap());
    assertEquals(initial, ((Separator) source.getUnit("20-V-101")).getThermoSystem().getInternalEnergy());
  }

  /** A tiny final alignment step retains its actual native UUID and timestep rather than a rounded wall-time guess. */
  @Test
  void refinedControlRampReportsActualFinalPhysicalStepIdentity() throws IOException {
    CaseBundle refined = run("CONTROL_RAMP", 0.05);
    assertTrue(nativePassed(refined));
    assertFreshAndCompleteWindow(refined, 60.0);
    assertEquals(refined.getConfiguredProcess().getCalculationIdentifier().toString(),
        refined.getRows().get(refined.getRows().size() - 1).get("physical_step_id"));
    assertEquals(refined.getConfiguredProcess().getTimeStep(), last(refined, "dt_s"));
  }

  /** A response PASS cannot erase an explicit separately reported native numerical energy failure. */
  @Test
  void numericalEnergyRemainsASeparateFrozenGate() throws IOException {
    CaseBundle healthy = run("TRUE_DEMAND", 0.1);
    assertTrue(nativePassed(healthy));
    Map<?, ?> energy = criterion(healthy, "10-V-100-NUMERICAL-ENERGY");
    assertFalse((Boolean) energy.get("passed"));
    assertFalse((Boolean) ((Map<?, ?>) energy.get("criterion")).get("required"));
    Map<?, ?> nativeEnergy = (Map<?, ?>) healthy.toMap().get("nativeEnergyAccounting");
    Map<?, ?> v100 = (Map<?, ?>) nativeEnergy.get("10-V-100");
    assertEquals("COMPLETE", v100.get("status"));
    assertEquals("FAIL", v100.get("verdict"));
    assertTrue(((Number) v100.get("maxRelativeCumulativeResidual")).doubleValue() > 1.0e-5);
    assertEquals("HOLD", healthy.toMap().get("engineeringQualification"));
  }

  /**
   * Runs the actual strict engine and optionally exports exactly the tested case for task evidence.
   *
   * @param mode explicit profile
   * @param dt physical timestep, seconds
   * @return completed actual case
   * @throws IOException if requested evidence export fails
   */
  private static CaseBundle run(String mode, double dt) throws IOException {
    Map<String, Double> settings = AdvancedV101SafetyStudy.defaultSettings();
    CaseBundle bundle = AdvancedV101SafetyStudy.createCase(AdvancedV101SafetyStudy.buildSource(settings), settings,
        mode, dt);
    DynamicSafetyScenarioResult nativeResult = bundle.runStrict();
    assertTrue(((List<?>) nativeResult.toMap().get("errors")).isEmpty(), nativeResult.toJson());
    String directory = System.getProperty("neqsim.advancedSafety.output");
    if (directory != null && !directory.trim().isEmpty()) {
      bundle.export(Paths.get(directory));
    }
    return bundle;
  }

  /**
   * Reads the actual native required-response verdict.
   *
   * @param bundle actual completed case
   * @return native response passed
   */
  private static boolean nativePassed(CaseBundle bundle) {
    return (Boolean) ((Map<?, ?>) bundle.toMap().get("nativeResult")).get("passed");
  }

  /**
   * Reads an actual native function's evidence without reconstructing its signals or vote.
   *
   * @param bundle actual completed case
   * @param id actual function identity
   * @return native function evidence
   */
  private static Map<String, Object> function(CaseBundle bundle, String id) {
    for (neqsim.process.safety.sif.ClosedLoopSafetyFunction function : bundle.getFunctions()) {
      if (function.getSifId().equals(id)) {
        return function.getDynamicSafetyScenarioEvidence();
      }
    }
    throw new IllegalArgumentException("Missing actual function " + id);
  }

  /**
   * Reads a native criterion result from its actual typed result map.
   *
   * @param bundle actual completed case
   * @param id native criterion identity
   * @return actual criterion result
   */
  private static Map<?, ?> criterion(CaseBundle bundle, String id) {
    Map<?, ?> result = (Map<?, ?>) bundle.toMap().get("nativeResult");
    return (Map<?, ?>) ((Map<?, ?>) result.get("criteria")).get(id);
  }

  /**
   * Reads an actual numeric row value.
   *
   * @param row actual state row
   * @param key field identity
   * @return numeric value
   */
  private static double number(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).doubleValue();
  }

  /**
   * Reads the final physical sample.
   *
   * @param bundle completed case
   * @param key field identity
   * @return actual final value
   */
  private static double last(CaseBundle bundle, String key) {
    return number(bundle.getRows().get(bundle.getRows().size() - 1), key);
  }

  /**
   * Computes a maximum from all actual physical rows rather than first-reach criteria.
   *
   * @param bundle completed case
   * @param key field identity
   * @return observed maximum
   */
  private static double maximum(CaseBundle bundle, String key) {
    double result = Double.NEGATIVE_INFINITY;
    for (Map<String, Object> row : bundle.getRows()) {
      result = Math.max(result, number(row, key));
    }
    return result;
  }

  /**
   * Finds actual closure from the physical trace, not the native command timestamp.
   *
   * @param bundle completed case
   * @param key actual valve-position column
   * @return event-relative first actual zero-position sample
   */
  private static double firstClosed(CaseBundle bundle, String key) {
    for (Map<String, Object> row : bundle.getRows()) {
      if (number(row, key) == 0.0) {
        return number(row, "event_relative_time_s");
      }
    }
    return Double.POSITIVE_INFINITY;
  }

  /**
   * Checks full sampled event-window coverage and native sample UUID freshness.
   *
   * @param bundle completed case
   * @param duration full scenario horizon, seconds
   */
  private static void assertFreshAndCompleteWindow(CaseBundle bundle, double duration) {
    assertFalse(bundle.getRows().isEmpty());
    assertEquals(1.0, number(bundle.getRows().get(0), "time_s"), 1.0e-9);
    assertEquals(duration, last(bundle, "time_s"), 1.0e-9);
    for (Map<String, Object> row : bundle.getRows()) {
      assertTrue((Boolean) row.get("sensor_samples_current_step"));
    }
  }
}
