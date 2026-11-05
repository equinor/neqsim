package neqsim.process.engineering.pid;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.google.gson.GsonBuilder;
import neqsim.process.safety.scenario.AdvancedV101PsvStudy;
import neqsim.process.safety.scenario.DynamicSafetyScenarioResult;

/** Actual strict-native PSV feedback and canonical proposal-edit/rerun regressions. */
class PidPressureReliefScenarioTest {
  @TempDir
  Path directory;

  /** A fully executed nominal case has no mechanical-demand response credit. */
  @Test
  @SuppressWarnings("unchecked")
  void nominalNativeCaseRemainsNoDemandAndFailedClosedRemainsObservedFail() throws Exception {
    AdvancedPressureReliefPidExample.Design design = AdvancedPressureReliefPidExample.buildDesign();
    for (String mode : Arrays.asList("PSV_NORMAL", "PSV_FAILED_CLOSED")) {
      PidPressureReliefValidator.ConfiguredRelief receipt = AdvancedPressureReliefPidExample.configuredReceipt(design,
          mode);
      AdvancedV101PsvStudy.CaseBundle bundle = AdvancedPressureReliefPidExample.configuredCase(design, mode, .1,
          receipt);
      DynamicSafetyScenarioResult result = bundle.runStrict();
      Map<String, Object> feedback = PidPressureReliefValidator.attachResult(receipt, bundle.getScenario(), result,
          new LinkedHashSet<String>(Arrays.asList("V101-PRESSURE-ENVELOPE", "V100-PRESSURE-ENVELOPE")));
      assertEquals(mode.equals("PSV_NORMAL") ? "MONITORED_NO_DEMAND" : "OBSERVED_FAIL", feedback.get("executionStatus"),
          result.toJson());
      assertEquals(Boolean.TRUE, feedback.get("fullHorizonCompleted"));
      assertEquals(Boolean.TRUE, feedback.get("engineeringApprovalRequired"));
      assertEquals(Boolean.FALSE, feedback.get("fitnessForConstruction"));
      assertEquals(mode.equals("PSV_NORMAL"), result.isPassed());
      if (mode.equals("PSV_NORMAL")) {
        Map<String, Object> nativeRelief = null;
        for (Map<String, Object> owner : result.getLogicEvidence().values()) {
          if (owner.get("pressureReliefEvidence") instanceof java.util.List) {
            nativeRelief = (Map<String, Object>) ((java.util.List<?>) owner.get("pressureReliefEvidence")).get(0);
          }
        }
        assertNotNull(nativeRelief);
        Object completeTrace = nativeRelief.get("trace");
        java.util.List<?> complete = (java.util.List<?>) completeTrace;
        nativeRelief.put("trace", java.util.Collections.singletonList(complete.get(0)));
        Map<String, Object> partial = PidPressureReliefValidator.attachResult(receipt, bundle.getScenario(), result,
            new LinkedHashSet<String>(Arrays.asList("V101-PRESSURE-ENVELOPE", "V100-PRESSURE-ENVELOPE")));
        assertEquals("INCOMPLETE", partial.get("executionStatus"));
        assertEquals(Boolean.FALSE, partial.get("fullHorizonCompleted"));
        assertEquals(Boolean.TRUE, partial.get("nativeFullHorizonCompleted"));
        nativeRelief.put("trace", java.util.Arrays.asList(complete.get(0), complete.get(complete.size() - 1)));
        partial = PidPressureReliefValidator.attachResult(receipt, bundle.getScenario(), result,
            new LinkedHashSet<String>(Arrays.asList("V101-PRESSURE-ENVELOPE", "V100-PRESSURE-ENVELOPE")));
        assertEquals("INCOMPLETE", partial.get("executionStatus"),
            "Endpoints alone cannot replace native sample count");
        nativeRelief.put("trace", completeTrace);
        neqsim.process.equipment.valve.SafetyReliefValve actual = (neqsim.process.equipment.valve.SafetyReliefValve) bundle
            .getConfiguredProcess().getUnit("20-PSV-001");
        actual.setCalculateSteadyState(true);
        PidPressureReliefValidator.ConfiguredRelief wrongMode = PidPressureReliefValidator.validateConfigured(
            design.project, design.pid, design.bindings, bundle.getConfiguredProcess(), "20-PSV-001");
        IllegalArgumentException modeFailure = assertThrows(IllegalArgumentException.class,
            () -> PidPressureReliefValidator.attachResult(wrongMode, bundle.getScenario(), result,
                new LinkedHashSet<String>(Arrays.asList("V101-PRESSURE-ENVELOPE", "V100-PRESSURE-ENVELOPE"))));
        assertTrue(modeFailure.getMessage().contains("FINITE_NATIVE_TRANSIENT"));
        actual.setCalculateSteadyState(false);
      }
      bundle.export(directory.resolve(mode));
      write(directory.resolve(mode + "-feedback.json"), feedback);
    }
  }

  /** An edited canonical response must actually run differently under unchanged pressure/deadline policy. */
  @Test
  @SuppressWarnings("unchecked")
  void canonicalResponseEditRejectsStaleReceiptAndRerunsNativeFailureToPass() throws Exception {
    AdvancedPressureReliefPidExample.Design original = AdvancedPressureReliefPidExample.buildDesign();
    PidElement psv = element(original.pid, "20-PSV-001");
    Map<String, Object> slow = new LinkedHashMap<String, Object>(
        (Map<String, Object>) psv.getAttributes().get("simulationPressureReliefConfiguration"));
    slow.put("tauOpenSeconds", Double.valueOf(4));
    slow.put("maximumLiftRatePerSecond", Double.valueOf(.2));
    psv.attribute("simulationPressureReliefConfiguration", slow);
    assertThrows(IllegalStateException.class, () -> original.bindings.assertCurrent(original.project, original.pid));
    AdvancedPressureReliefPidExample.Design design = rebound(original);
    PidPressureReliefValidator.ConfiguredRelief before = AdvancedPressureReliefPidExample.configuredReceipt(design,
        "PSV_RELIEF");
    AdvancedV101PsvStudy.CaseBundle first = AdvancedPressureReliefPidExample.configuredCase(design, "PSV_RELIEF", .1,
        before);
    DynamicSafetyScenarioResult failed = first.runStrict();
    assertFalse(failed.isPassed(), failed.toJson());
    assertEquals(4.0, ((Number) ((Map<?, ?>) first.toMap().get("settings")).get("psvTauOpenSeconds")).doubleValue());
    String oldPid = new GsonBuilder().setPrettyPrinting().create().toJson(design.pid.toMap());
    Map<String, Object> fast = new LinkedHashMap<String, Object>(slow);
    fast.put("tauOpenSeconds", Double.valueOf(.15));
    fast.put("maximumLiftRatePerSecond", Double.valueOf(3));
    psv.attribute("simulationPressureReliefConfiguration", fast);
    final AdvancedPressureReliefPidExample.Design stale = design;
    assertThrows(IllegalStateException.class, () -> stale.bindings.assertCurrent(stale.project, stale.pid));
    design = rebound(design);
    PidPressureReliefValidator.ConfiguredRelief after = AdvancedPressureReliefPidExample.configuredReceipt(design,
        "PSV_RELIEF");
    assertNotEquals(before.getFingerprint(), after.getFingerprint());
    assertThrows(IllegalStateException.class, () -> before.assertSameConfiguration(after));
    AdvancedV101PsvStudy.CaseBundle second = AdvancedPressureReliefPidExample.configuredCase(design, "PSV_RELIEF", .1,
        after);
    DynamicSafetyScenarioResult passed = second.runStrict();
    assertTrue(passed.isPassed(), passed.toJson());
    assertEquals(.15, ((Number) ((Map<?, ?>) second.toMap().get("settings")).get("psvTauOpenSeconds")).doubleValue());
    assertEquals(first.getScenario().getDurationSeconds(), second.getScenario().getDurationSeconds());
    assertEquals(first.getScenario().getCriteria().get(0).toMap(), second.getScenario().getCriteria().get(0).toMap());
    assertEquals(.15,
        ((neqsim.process.equipment.valve.SafetyReliefValve) design.source.getUnit("20-PSV-001")).getTauOpenSec());
    String auditDirectory = System.getProperty("neqsim.pid.psvProposalAuditDirectory");
    if (auditDirectory != null) {
      Path audit = Paths.get(auditDirectory);
      Files.createDirectories(audit);
      Files.write(audit.resolve("slow-canonical-pid.json"), oldPid.getBytes(StandardCharsets.UTF_8));
      write(audit.resolve("fast-canonical-pid.json"), design.pid.toMap());
      write(audit.resolve("slow-configuration.json"), before.toMap());
      write(audit.resolve("fast-configuration.json"), after.toMap());
      write(audit.resolve("slow-scenario.json"), first.getScenario().toMap());
      write(audit.resolve("fast-scenario.json"), second.getScenario().toMap());
      write(audit.resolve("slow-result.json"), failed.toMap());
      write(audit.resolve("fast-result.json"), passed.toMap());
    }
  }

  /**
   * @param original edited graph/source
   * @return fresh exact actual hardware bindings
   */
  private static AdvancedPressureReliefPidExample.Design rebound(AdvancedPressureReliefPidExample.Design original) {
    PidSimulationBindings bindings = PidSimulationBindings.from(original.project, original.pid);
    for (PidElement item : original.pid.getElements()) {
      if (item.getType() == PidElementType.MEASUREMENT) {
        bindings.bindMeasurement(item.getTag(), item.getTag());
      } else if (item.getType() == PidElementType.CONTROLLER) {
        bindings.bindController(item.getTag(), item.getTag());
      } else if (item.getType() == PidElementType.CONTROL_VALVE || item.getType() == PidElementType.SHUTDOWN_VALVE
          || item.getType() == PidElementType.SAFETY_RELIEF_VALVE) {
        bindings.bindEquipment(item.getTag(), item.getTag());
      }
    }
    return new AdvancedPressureReliefPidExample.Design(original.source, original.project, original.pid, bindings,
        original.policy);
  }

  /**
   * @param model canonical graph
   * @param tag actual tag
   * @return exact canonical element
   */
  private static PidElement element(PidDesignModel model, String tag) {
    for (PidElement item : model.getElements()) {
      if (item.getTag().equals(tag)) {
        return item;
      }
    }
    throw new IllegalArgumentException("Missing actual relief proposal");
  }

  /**
   * @param file evidence file
   * @param value exact retained data
   * @throws Exception for write failure
   */
  private static void write(Path file, Object value) throws Exception {
    Files.write(file, new GsonBuilder().setPrettyPrinting().create().toJson(value).getBytes(StandardCharsets.UTF_8));
  }
}
