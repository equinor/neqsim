package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Analytical dispatch examples; prescribed coefficients test units/precedence, not fitted accuracy. */
class ComponentCorrelationSpecTest {
  @ParameterizedTest
  @ValueSource(strings = { "pow10", "pow10KPa", "exp", "log", "legacy-dippr", "loglog" })
  void eachCorrelationFormPublishesItsAnalyticalPressure(String form) {
    Component component = new ComponentSrk("i-pentane", 1.0, 1.0, 0);
    component.antoineLiqVapPresType = form;
    component.AntoineD = 0.0;
    component.AntoineE = 0.0;
    double temperature = 300.0;
    double expected = 2.0;
    if ("pow10".equals(form) || "pow10KPa".equals(form)) {
      component.AntoineA = Math.log10(2.0) + 1.0 + ("pow10KPa".equals(form) ? 5.0 : 0.0);
      component.AntoineB = 300.0;
      component.AntoineC = "pow10".equals(form) ? 273.15 : 0.0;
      // Explicit base-ten labels must take precedence over E, even when E is nonzero.
      component.AntoineE = 2.0;
    } else if ("legacy-dippr".equals(form)) {
      component.AntoineA = Math.log(200000.0) + 1.0;
      component.AntoineB = -300.0;
      component.AntoineC = 0.0;
      component.AntoineE = 2.0;
    } else if ("loglog".equals(form)) {
      // At Tc every positive-power reduced-temperature term vanishes: Psat = Pc.
      temperature = component.getTC();
      expected = component.getPC();
    } else {
      component.AntoineA = Math.log(2.0) + 1.0;
      component.AntoineB = 300.0;
      component.AntoineC = 0.0;
    }
    double actual = component.getAntoineVaporPressure(temperature);
    assertTrue(Double.isFinite(actual) && actual > 0.0);
    assertEquals(expected, actual, expected * 1e-12, form);
  }
}
