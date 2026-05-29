package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for {@link ChokeCollapseAnalyzer}.
 *
 * @author esol
 * @version 1.0
 */
public class ChokeCollapseAnalyzerTest {
  private static final double TOL = 1e-3;

  private ThrottlingValve buildGasValve(double inletPbara, double outletPbara) {
    SystemInterface fluid = new SystemSrkEos(298.15, inletPbara);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setPressure(inletPbara, "bara");
    feed.setTemperature(25.0, "C");
    feed.run();
    ThrottlingValve v = new ThrottlingValve("CV-100", feed);
    v.setOutletPressure(outletPbara);
    v.run();
    return v;
  }

  @Test
  void criticalPressureRatioMatchesAnalyticalFormula() {
    double gamma = 1.4;
    double expected = Math.pow(2.0 / (gamma + 1.0), gamma / (gamma - 1.0));
    assertEquals(expected, ChokeCollapseAnalyzer.criticalPressureRatio(gamma), TOL);
  }

  @Test
  void gasChokeBelowCritical_returnsCritical() {
    // p2/p1 = 10/100 = 0.10, well below rc (~0.54 for CH4)
    ThrottlingValve v = buildGasValve(100.0, 10.0);
    ChokeCollapseResult r = v.analyseChokeCollapse();
    assertNotNull(r);
    assertEquals("gas", r.getFluidPhase());
    assertEquals(ChokeCollapseResult.FlowRegime.CRITICAL, r.getFlowRegime());
    assertTrue(r.getCriticalPressureRatio() > 0.4 && r.getCriticalPressureRatio() < 0.7);
    assertTrue(r.getPressureRatio() < r.getCriticalPressureRatio());
  }

  @Test
  void gasChokeAboveCritical_returnsSubcritical() {
    // p2/p1 = 90/100 = 0.90, well above rc
    ThrottlingValve v = buildGasValve(100.0, 90.0);
    ChokeCollapseResult r = v.analyseChokeCollapse();
    assertEquals(ChokeCollapseResult.FlowRegime.SUBCRITICAL, r.getFlowRegime());
    assertEquals(ChokeCollapseResult.CollapseMode.COLLAPSED, r.getCollapseMode());
    assertTrue(r.getPressureRatio() > r.getCriticalPressureRatio());
  }

  @Test
  void reverseFlow_isDetected() {
    ThrottlingValve v = buildGasValve(50.0, 50.0);
    // Force downstream override above upstream
    ChokeCollapseAnalyzer a = new ChokeCollapseAnalyzer(v);
    a.setDownstreamPressure(60.0, "bara");
    ChokeCollapseResult r = a.analyze();
    assertEquals(ChokeCollapseResult.FlowRegime.REVERSE, r.getFlowRegime());
  }

  @Test
  void findCollapsePressureRatio_matchesCriticalRatio() {
    ThrottlingValve v = buildGasValve(100.0, 50.0);
    ChokeCollapseAnalyzer a = new ChokeCollapseAnalyzer(v);
    double rc = a.findCollapsePressureRatio();
    // Methane near ambient: gamma ~1.3, rc ~0.54
    assertTrue(rc > 0.4 && rc < 0.7, "rc out of expected range: " + rc);
  }

  @Test
  void resultToJsonRoundtripsKeyFields() {
    ThrottlingValve v = buildGasValve(100.0, 10.0);
    String json = v.analyseChokeCollapse().toJson();
    assertNotNull(json);
    assertTrue(json.contains("\"flowRegime\""));
    assertTrue(json.contains("CRITICAL"));
    assertTrue(json.contains("\"criticalPressureRatio\""));
  }
}
