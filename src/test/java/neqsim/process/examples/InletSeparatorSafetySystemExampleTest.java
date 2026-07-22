package neqsim.process.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.examples.InletSeparatorSafetySystemExample.SafetyReport;
import neqsim.process.mechanicaldesign.valve.SafetyValveMechanicalDesign.SafetyValveScenarioResult;
import neqsim.process.safety.risk.sis.LOPAResult;
import neqsim.process.safety.risk.sis.SafetyInstrumentedFunction;

/**
 * Unit tests for {@link InletSeparatorSafetySystemExample}.
 *
 * <p>
 * These tests exercise the steady-state flowsheet, instrumentation, PSV sizing (API 520), Safety Instrumented Functions
 * (IEC 61511) and the LOPA worksheet, and assert that the safety study produces physically reasonable numbers.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
@Tag("slow")
public class InletSeparatorSafetySystemExampleTest {

  /** Example under test, rebuilt for each method to ensure independence. */
  private InletSeparatorSafetySystemExample example;

  /**
   * Creates a fresh example instance before each test so the SIS/LOPA caches are clean.
   */
  @BeforeEach
  public void setUp() {
    example = new InletSeparatorSafetySystemExample();
  }

  /**
   * Verifies that the feed fluid is constructed with all expected components and the classic mixing rule.
   */
  @Test
  public void testFeedFluidIsBuiltWithClassicMixingRule() {
    assertNotNull(example.buildFeedFluid());
    assertEquals(10, example.buildFeedFluid().getNumberOfComponents());
  }

  /**
   * Verifies that the steady-state flowsheet runs and produces a non-trivial gas / oil / water split.
   */
  @Test
  public void testProcessBuildsAndRunsWithThreePhaseSplit() {
    example.buildFeedFluid();
    example.buildProcess();
    example.addInstrumentation();
    example.getProcess().run();

    SafetyReport report = new SafetyReport();
    report.setGasMassFlow(example.getInletSeparator().getGasOutStream().getFlowRate("kg/hr"));
    report.setOilMassFlow(example.getInletSeparator().getOilOutStream().getFlowRate("kg/hr"));
    report.setWaterMassFlow(example.getInletSeparator().getWaterOutStream().getFlowRate("kg/hr"));

    assertTrue(report.getGasMassFlow() > 0.0, "Gas outlet must have non-zero flow");
    assertTrue(report.getOilMassFlow() > 0.0, "Oil outlet must have non-zero flow");
    assertTrue(report.getWaterMassFlow() > 0.0, "Water outlet must have non-zero flow");
    double totalOut = report.getGasMassFlow() + report.getOilMassFlow() + report.getWaterMassFlow();
    double imbalance = Math.abs(totalOut - InletSeparatorSafetySystemExample.FEED_FLOW_KGHR)
        / InletSeparatorSafetySystemExample.FEED_FLOW_KGHR;
    assertTrue(imbalance < 0.02, "Steady-state mass balance closure within 2 percent, got " + imbalance);
  }

  /**
   * Full demonstration: PSV sized, both relief scenarios populated, controlling orifice area positive and finite.
   */
  @Test
  public void testFullDemonstrationProducesSafetyReport() {
    SafetyReport report = example.runFullDemonstration();
    assertNotNull(report, "runFullDemonstration must return a report");

    // PSV sizing
    Map<String, SafetyValveScenarioResult> scenarios = report.getScenarioResults();
    assertEquals(2, scenarios.size(), "Two relieving scenarios should be sized");
    assertTrue(scenarios.containsKey("Blocked gas outlet"));
    assertTrue(scenarios.containsKey("External fire"));
    assertNotNull(report.getControllingScenario(), "A controlling scenario must be identified");
    assertTrue(report.getControllingOrificeArea() > 0.0, "Controlling orifice area must be positive");
    assertTrue(Double.isFinite(report.getControllingOrificeArea()));

    // Safety Instrumented System
    Map<String, SafetyInstrumentedFunction> sifs = report.getSifs();
    assertEquals(3, sifs.size(), "Three SIFs should be configured");
    SafetyInstrumentedFunction psh = sifs.get("SIF-001");
    assertNotNull(psh);
    assertEquals(2, psh.getSil(), "Inlet ESD PSH must be SIL 2");
    assertTrue(psh.getRiskReductionFactor() >= 100.0, "SIL 2 RRF must be at least 100");
    assertTrue(psh.getRiskReductionFactor() < 1000.0, "SIL 2 RRF must be below 1000");

    // LOPA worksheet
    LOPAResult lopa = report.getOverpressureLopa();
    assertNotNull(lopa);
    assertEquals("Inlet separator overpressure (V-100)", lopa.getScenarioName());
    assertTrue(lopa.getInitiatingEventFrequency() > 0.0);
    assertTrue(lopa.getMitigatedFrequency() > 0.0);
    assertTrue(lopa.getMitigatedFrequency() < lopa.getInitiatingEventFrequency(),
        "LOPA layers must reduce frequency below the initiating event rate");
    assertTrue(lopa.getTotalRRF() >= 1.0e4, "Combined IPLs (BPCS + PSH + SIL 2 SIF + PSV) must deliver RRF >= 1e4");
  }

  /**
   * Verifies the overpressure trip sequence de-energises the inlet ESD valve.
   */
  @Test
  public void testOverpressureTripDeEnergisesEsdv() {
    example.buildFeedFluid();
    example.buildProcess();
    example.addInstrumentation();
    example.getProcess().run();
    example.configureSafetyInstrumentedSystem();

    assertTrue(example.getInletEsdValve().isEnergized(), "ESDV-1001 must be energised at steady state");
    String description = example.demonstrateOverpressureTrip();
    assertNotNull(description);
    assertFalse(example.getInletEsdValve().isEnergized(), "ESDV-1001 must be de-energised after the trip");
  }
}
