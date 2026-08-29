package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhasePitzer;

/** Tests temperature derivatives of aqueous Henry-reference fugacity coefficients. */
class ComponentGEHenryDerivativeTest {
  private static final double TEMPERATURE = 298.15;

  @Test
  void activeCorrelationReturnsLogarithmicDerivative() {
    ComponentGeNRTL component = new ComponentGeNRTL("CO2", 1.0, 1.0, 0);
    component.setHenryCoefParameter(new double[] { 10.0, -1200.0, 1.5, 0.002 });
    PhasePitzer phase = phaseAt(TEMPERATURE);

    double expected = finiteDifferenceLogHenry(component, TEMPERATURE);

    assertEquals(expected, component.fugcoefDiffTemp(phase), 1.0e-9);
    assertEquals(
        1200.0 / (TEMPERATURE * TEMPERATURE) + 1.5 / TEMPERATURE + 0.002,
        component.fugcoefDiffTemp(phase), 1.0e-12);
  }

  @Test
  void pitzerNeutralGasUsesSameLogarithmicDerivative() {
    ComponentGePitzer component = new ComponentGePitzer("CO2", 1.0, 1.0, 0);
    component.setHenryCoefParameter(new double[] { 8.0, -800.0, 0.5, 0.001 });
    PhasePitzer phase = phaseAt(TEMPERATURE);

    assertEquals(
        finiteDifferenceLogHenry(component, TEMPERATURE), component.fugcoefDiffTemp(phase), 1.0e-9);
  }

  @Test
  void failClosedCorrelationHasZeroReferenceDerivative() {
    ComponentGeNRTL component = new ComponentGeNRTL("CO2", 1.0, 1.0, 0);
    component.setHenryCoefParameter(new double[] { 1000.0, 0.0, 0.0, 0.0 });

    assertTrue(Double.isInfinite(component.getHenryCoef(TEMPERATURE)));
    assertEquals(0.0, component.fugcoefDiffTemp(phaseAt(TEMPERATURE)), 0.0);
  }

  @Test
  void unsupportedPitzerHydrocarbonHasZeroReferenceDerivative() {
    ComponentGePitzer component = new ComponentGePitzer("methane", 1.0, 1.0, 0);
    component.setHenryCoefParameter(new double[] { 0.0, 0.0, 0.0, 0.01 });

    double rawLogDerivative = component.getHenryCoefdT(TEMPERATURE) / component.getHenryCoef(TEMPERATURE);

    assertEquals(0.01, rawLogDerivative, 1.0e-12);
    assertEquals(0.0, component.fugcoefDiffTemp(phaseAt(TEMPERATURE)), 0.0);
  }

  private static PhasePitzer phaseAt(double temperature) {
    PhasePitzer phase = new PhasePitzer();
    phase.setTemperature(temperature);
    return phase;
  }

  private static double finiteDifferenceLogHenry(ComponentGE component, double temperature) {
    double step = 1.0e-3;
    return (Math.log(component.getHenryCoef(temperature + step))
        - Math.log(component.getHenryCoef(temperature - step))) / (2.0 * step);
  }
}
