package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.InadvertentValveOperationResult.ConsequenceSeverity;
import neqsim.process.equipment.valve.InadvertentValveOperationResult.IvoMode;
import neqsim.process.equipment.valve.InadvertentValveOperationResult.ValveRole;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for {@link InadvertentValveOperationAnalyzer}.
 *
 * @author esol
 * @version 1.0
 */
public class InadvertentValveOperationAnalyzerTest {
  private ThrottlingValve buildValve(double inletPbara, double outletPbara) {
    SystemInterface fluid = new SystemSrkEos(298.15, inletPbara);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setPressure(inletPbara, "bara");
    feed.setTemperature(25.0, "C");
    feed.run();
    ThrottlingValve v = new ThrottlingValve("XV-100", feed);
    v.setOutletPressure(outletPbara);
    v.run();
    return v;
  }

  @Test
  void spuriousCloseOfBlockValve_flagsBlockedOutletAndOverpressure() {
    ThrottlingValve v = buildValve(150.0, 50.0);
    InadvertentValveOperationResult r =
        new InadvertentValveOperationAnalyzer(v).setRole(ValveRole.BLOCK)
            .setMode(IvoMode.SPURIOUS_CLOSE).setDesignPressure(100.0, "bara").analyze();

    assertNotNull(r);
    assertTrue(r.isBlockedOutlet());
    assertEquals(1.5, r.getOverpressureFactor(), 1e-6);
    assertEquals(ConsequenceSeverity.SAFETY_CRITICAL, r.getSeverity());
    assertFalse(r.getRecommendations().isEmpty());
  }

  @Test
  void spuriousOpenOfBypass_intoLowPressureSegment_flagsOverpressure() {
    ThrottlingValve v = buildValve(150.0, 145.0);
    InadvertentValveOperationResult r =
        new InadvertentValveOperationAnalyzer(v).setRole(ValveRole.BYPASS)
            .setMode(IvoMode.SPURIOUS_OPEN).setDownstreamDesignPressure(50.0, "bara").analyze();

    assertEquals(3.0, r.getOverpressureFactor(), 1e-6);
    assertEquals(ConsequenceSeverity.SAFETY_CRITICAL, r.getSeverity());
    assertTrue(r.getDescription().toLowerCase().contains("spurious"));
  }

  @Test
  void psvIsolationValveClosed_flagsLossOfReliefPath() {
    ThrottlingValve v = buildValve(60.0, 59.0);
    InadvertentValveOperationResult r =
        new InadvertentValveOperationAnalyzer(v).setRole(ValveRole.PSV_ISOLATION)
            .setMode(IvoMode.SPURIOUS_CLOSE).setDesignPressure(80.0, "bara").analyze();

    assertTrue(r.isLossOfReliefPath());
    assertEquals(ConsequenceSeverity.SAFETY_CRITICAL, r.getSeverity());
  }

  @Test
  void esdStuckOpen_failsToIsolateOnDemand() {
    ThrottlingValve v = buildValve(100.0, 99.0);
    InadvertentValveOperationResult r =
        new InadvertentValveOperationAnalyzer(v).setRole(ValveRole.ESD).setMode(IvoMode.STUCK_OPEN)
            .setDesignPressure(110.0, "bara").analyze();

    assertTrue(r.isFailureToIsolateOnDemand());
    assertEquals(ConsequenceSeverity.SAFETY_CRITICAL, r.getSeverity());
  }

  @Test
  void checkValveStuckOpen_flagsReverseFlow() {
    ThrottlingValve v = buildValve(50.0, 49.0);
    InadvertentValveOperationResult r =
        new InadvertentValveOperationAnalyzer(v).setRole(ValveRole.CHECK)
            .setMode(IvoMode.STUCK_OPEN).setDesignPressure(70.0, "bara").analyze();

    assertTrue(r.isReverseFlowRisk());
    assertEquals(ConsequenceSeverity.MAJOR, r.getSeverity());
  }

  @Test
  void defaultFrequencyAppliedWhenNotOverridden() {
    ThrottlingValve v = buildValve(60.0, 50.0);
    InadvertentValveOperationResult spurious =
        new InadvertentValveOperationAnalyzer(v).setRole(ValveRole.BLOCK)
            .setMode(IvoMode.SPURIOUS_CLOSE).setDesignPressure(80.0, "bara").analyze();
    assertEquals(InadvertentValveOperationAnalyzer.DEFAULT_SPURIOUS_FREQUENCY_PER_YEAR,
        spurious.getFrequencyPerYear(), 1e-12);

    InadvertentValveOperationResult stuck =
        new InadvertentValveOperationAnalyzer(v).setRole(ValveRole.ESD).setMode(IvoMode.STUCK_OPEN)
            .setDesignPressure(80.0, "bara").analyze();
    assertEquals(InadvertentValveOperationAnalyzer.DEFAULT_STUCK_FREQUENCY_PER_YEAR,
        stuck.getFrequencyPerYear(), 1e-12);
  }

  @Test
  void resultToJsonContainsKeyFields() {
    ThrottlingValve v = buildValve(80.0, 60.0);
    InadvertentValveOperationResult r = new InadvertentValveOperationAnalyzer(v)
        .setRole(ValveRole.BLOCK).setMode(IvoMode.SPURIOUS_CLOSE).setDesignPressure(70.0, "bara")
        .setFrequencyPerYear(0.05).analyze();
    String json = r.toJson();
    assertNotNull(json);
    assertTrue(json.contains("\"valveName\""));
    assertTrue(json.contains("\"severity\""));
    assertTrue(json.contains("\"frequencyPerYear\""));
    assertTrue(json.contains("\"recommendations\""));
  }

  @Test
  void convenienceMethodOnThrottlingValveReturnsResult() {
    ThrottlingValve v = buildValve(120.0, 100.0);
    InadvertentValveOperationResult r =
        v.analyseInadvertentOperation(ValveRole.BLOCK, IvoMode.SPURIOUS_CLOSE, 110.0);
    assertNotNull(r);
    assertEquals("XV-100", r.getValveName());
    assertEquals(ValveRole.BLOCK, r.getRole());
    assertEquals(IvoMode.SPURIOUS_CLOSE, r.getMode());
  }
}
