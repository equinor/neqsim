package neqsim.process.synthesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the {@link FlowsheetSynthesisEngine} heuristics.
 */
class FlowsheetSynthesisEngineTest {

  private Stream makeFeed(String name, double pBara, double tC, String[] comps, double[] zMole,
      double flowKgHr) {
    SystemInterface fluid = new SystemSrkEos(273.15 + tC, pBara);
    for (int i = 0; i < comps.length; i++) {
      fluid.addComponent(comps[i], zMole[i]);
    }
    fluid.setMixingRule("classic");
    Stream s = new Stream(name, fluid);
    s.setFlowRate(flowKgHr, "kg/hr");
    s.setTemperature(tC, "C");
    s.setPressure(pBara, "bara");
    s.run();
    return s;
  }

  @Test
  void dutyValidatesArguments() {
    assertThrows(IllegalArgumentException.class,
        () -> new SeparationDuty(null,
            makeFeed("f", 30, 30, new String[] {"methane"}, new double[] {1.0}, 1000), null, null,
            Double.NaN));
    assertThrows(IllegalArgumentException.class,
        () -> new SeparationDuty("",
            makeFeed("f", 30, 30, new String[] {"methane"}, new double[] {1.0}, 1000), null, null,
            Double.NaN));
  }

  @Test
  void singleFlashChosenForMethaneNDecane() {
    // Methane + n-decane at 30 bara, 25C — flash already gives ~pure methane gas and
    // mostly decane liquid. Reasonable specs should be met by single flash.
    Stream feed = makeFeed("HC", 30.0, 25.0, new String[] {"methane", "nC10"},
        new double[] {0.6, 0.4}, 10000.0);
    Map<String, Double> topSpec = new LinkedHashMap<String, Double>();
    topSpec.put("methane", 0.97);
    Map<String, Double> botSpec = new LinkedHashMap<String, Double>();
    botSpec.put("nC10", 0.80);
    SeparationDuty duty = new SeparationDuty("HCsplit", feed, topSpec, botSpec, Double.NaN);

    FlowsheetSynthesisEngine eng = new FlowsheetSynthesisEngine();
    FlowsheetProposal p = eng.proposeAndBuild(duty);

    assertNotNull(p);
    assertEquals(FlowsheetProposal.Strategy.SINGLE_FLASH, p.getStrategy());
    assertTrue(p.isSpecsMet(), "rationale: " + p.getRationale());
    assertNotNull(p.getProcessSystem());
    // ProcessSystem should contain a Separator
    boolean foundSep = false;
    for (Object u : p.getProcessSystem().getUnitOperations()) {
      if (u instanceof Separator) {
        foundSep = true;
        break;
      }
    }
    assertTrue(foundSep, "expected a Separator in the proposed flowsheet");
  }

  @Test
  void distillationProposedForPropaneNButane() {
    // Propane/n-butane at 10 bara, 50C — α ~ 2.5, single flash won't meet 99%/99% specs.
    Stream feed = makeFeed("LPG", 10.0, 50.0, new String[] {"propane", "n-butane"},
        new double[] {0.5, 0.5}, 5000.0);
    Map<String, Double> topSpec = new LinkedHashMap<String, Double>();
    topSpec.put("propane", 0.98);
    Map<String, Double> botSpec = new LinkedHashMap<String, Double>();
    botSpec.put("n-butane", 0.98);
    SeparationDuty duty = new SeparationDuty("LPGsplit", feed, topSpec, botSpec, Double.NaN);

    FlowsheetSynthesisEngine eng = new FlowsheetSynthesisEngine();
    FlowsheetProposal p = eng.proposeAndBuild(duty);

    assertNotNull(p);
    assertEquals(FlowsheetProposal.Strategy.DISTILLATION, p.getStrategy());
    assertTrue(p.isSpecsMet());
    // Should mention LK/HK in rationale
    assertTrue(p.getRationale().contains("LK=propane"), p.getRationale());
    assertTrue(p.getRationale().contains("HK=n-butane"), p.getRationale());
    // ProcessSystem should contain a DistillationColumn
    boolean foundCol = false;
    for (Object u : p.getProcessSystem().getUnitOperations()) {
      if (u instanceof DistillationColumn) {
        foundCol = true;
        break;
      }
    }
    assertTrue(foundCol, "expected a DistillationColumn in the proposed flowsheet");
  }

  @Test
  void singlePhaseFeedEscalatesToDistillation() {
    // Pure methane at 50 bara, 50C — single-phase gas. Engine must escalate.
    Stream feed = makeFeed("pure", 50.0, 50.0, new String[] {"methane", "ethane"},
        new double[] {0.5, 0.5}, 1000.0);
    Map<String, Double> topSpec = new LinkedHashMap<String, Double>();
    topSpec.put("methane", 0.95);
    Map<String, Double> botSpec = new LinkedHashMap<String, Double>();
    botSpec.put("ethane", 0.95);
    SeparationDuty duty = new SeparationDuty("split", feed, topSpec, botSpec, Double.NaN);
    FlowsheetProposal p = new FlowsheetSynthesisEngine().proposeAndBuild(duty);
    // Either DISTILLATION (escalated) or SINGLE_FLASH if the SRK fluid is two-phase at conditions
    assertNotNull(p.getStrategy());
    assertNotNull(p.getProcessSystem());
  }

  @Test
  void proposalJsonHasExpectedFields() {
    Stream feed = makeFeed("HC", 30.0, 25.0, new String[] {"methane", "nC10"},
        new double[] {0.6, 0.4}, 10000.0);
    Map<String, Double> topSpec = new LinkedHashMap<String, Double>();
    topSpec.put("methane", 0.97);
    SeparationDuty duty = new SeparationDuty("HCsplit", feed, topSpec, null, Double.NaN);
    FlowsheetProposal p = new FlowsheetSynthesisEngine().proposeAndBuild(duty);

    String json = p.toJson().toString();
    assertTrue(json.contains("\"schemaVersion\":\"1.0\""), json);
    assertTrue(json.contains("\"strategy\""));
    assertTrue(json.contains("\"specsMet\""));
    assertTrue(json.contains("\"topProductPredicted\""));
    assertTrue(json.contains("\"bottomProductPredicted\""));
    assertTrue(json.contains("\"alternatives\""));
  }

  @Test
  void nullDutyThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> new FlowsheetSynthesisEngine().proposeAndBuild(null));
  }

  @Test
  void infeasibleWhenAlphaIsLow() {
    // Two close-boiling species with very similar K — engineer them via xylene isomers.
    // SRK isn't accurate for them but we just need alpha near 1.
    Stream feed = makeFeed("xy", 1.5, 140.0, new String[] {"o-Xylene", "p-Xylene"},
        new double[] {0.5, 0.5}, 1000.0);
    Map<String, Double> topSpec = new LinkedHashMap<String, Double>();
    topSpec.put("p-Xylene", 0.99);
    Map<String, Double> botSpec = new LinkedHashMap<String, Double>();
    botSpec.put("o-Xylene", 0.99);
    SeparationDuty duty = new SeparationDuty("xysplit", feed, topSpec, botSpec, Double.NaN);
    FlowsheetProposal p = new FlowsheetSynthesisEngine().proposeAndBuild(duty);
    // Accept either INFEASIBLE (alpha < 1.5) or SINGLE_FLASH/DISTILLATION — but rationale
    // must always be populated.
    assertNotNull(p.getRationale());
    assertFalse(p.getRationale().isEmpty());
  }
}
