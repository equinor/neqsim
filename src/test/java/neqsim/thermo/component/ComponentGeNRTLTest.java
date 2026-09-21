package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseGENRTL;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemNRTL;

/** Regression coverage for NRTL activity-coefficient state publication (issue #3899). */
class ComponentGeNRTLTest extends neqsim.NeqSimTest {
  private static final double TOLERANCE = 1.0e-12;
  private static final double[][] ALPHA = {{0.0, 0.3}, {0.3, 0.0}};
  private static final double[][] DIJ = {{0.0, 200.0}, {-100.0, 0.0}};

  @ParameterizedTest
  @CsvSource({"0.2, 298.15", "0.5, 298.15", "0.8, 298.15", "0.2, 323.15", "0.5, 323.15", "0.8, 323.15"})
  void publishesAnalyticalGammaAndLogGamma(double methanolFraction, double temperature) {
    PhaseGENRTL phase = newPhase();
    for (int initType = 0; initType <= 3; initType++) {
      phase.setInitType(initType);
      evaluateAndAssert(phase, methanolFraction, temperature);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3})
  void refreshesStateAndReturnsToOriginalValues(int initType) {
    PhaseGENRTL phase = newPhase();
    phase.setInitType(initType);
    double[][] states = {{0.2, 298.15}, {0.2, 298.15}, {0.8, 323.15}, {0.5, 298.15}, {0.2, 298.15}};
    for (double[] state : states) {
      evaluateAndAssert(phase, state[0], state[1]);
    }
    assertEquals(0.1550063209115084, component(phase, 0).getLnGamma(), TOLERANCE);
  }

  @Test
  void resetsLogGammaWhenInteractionsBecomeIdeal() {
    PhaseGENRTL phase = newPhase();
    evaluateAndAssert(phase, 0.2, 298.15);
    phase.setDij(new double[2][2]);
    assertEquals(0.0, phase.getExcessGibbsEnergy(phase, 2, 298.15, 1.0, PhaseType.LIQUID), TOLERANCE);
    for (int i = 0; i < 2; i++) {
      assertEquals(1.0, component(phase, i).getGamma(), TOLERANCE);
      assertEquals(0.0, component(phase, i).getLnGamma(), TOLERANCE);
    }
  }

  @Test
  void clonePreservesPublishedStateAndCanBeReevaluatedIndependently() {
    PhaseGENRTL original = newPhase();
    evaluateAndAssert(original, 0.2, 298.15);
    PhaseGENRTL copy = (PhaseGENRTL) original.clone();
    assertNotSame(original.getComponent(0), copy.getComponent(0));
    assertStoredValues(copy, binaryLogGamma(0.2, 298.15));
    evaluateAndAssert(copy, 0.8, 323.15);
    assertStoredValues(original, binaryLogGamma(0.2, 298.15));
    evaluateAndAssert(copy, 0.2, 298.15);
  }

  @Test
  void serializationPreservesPublishedStateAndReevaluation() throws Exception {
    PhaseGENRTL original = newPhase();
    evaluateAndAssert(original, 0.2, 298.15);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(original);
    }
    PhaseGENRTL restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (PhaseGENRTL) input.readObject();
    }
    assertStoredValues(restored, binaryLogGamma(0.2, 298.15));
    evaluateAndAssert(restored, 0.8, 323.15);
    assertStoredValues(original, binaryLogGamma(0.2, 298.15));
    evaluateAndAssert(restored, 0.2, 298.15);
  }

  private static PhaseGENRTL newPhase() {
    SystemNRTL system = new SystemNRTL(298.15, 1.0);
    system.addComponent("methanol", 0.2);
    system.addComponent("water", 0.8);
    system.setMixingRule("classic");
    system.init(0);
    PhaseGENRTL phase = (PhaseGENRTL) system.getPhase(1);
    phase.setAlpha(ALPHA);
    phase.setDij(DIJ);
    return phase;
  }

  private static ComponentGeNRTL component(PhaseGENRTL phase, int index) {
    return (ComponentGeNRTL) phase.getComponent(index);
  }

  private static void evaluateAndAssert(PhaseGENRTL phase, double methanolFraction, double temperature) {
    phase.setTemperature(temperature);
    phase.getComponent(0).setx(methanolFraction);
    phase.getComponent(1).setx(1.0 - methanolFraction);
    double[] expected = binaryLogGamma(methanolFraction, temperature);
    // Exercise both the direct component call and the public phase dispatch.
    for (int i = 0; i < 2; i++) {
      double returned = component(phase, i).getGamma(phase, 2, temperature, 1.0, PhaseType.LIQUID, ALPHA, DIJ,
          new double[2][2], new String[2][2]);
      assertEquals(Math.exp(expected[i]), returned, TOLERANCE);
      assertEquals(returned, component(phase, i).getGamma(), 0.0);
      assertEquals(expected[i], component(phase, i).getLnGamma(), TOLERANCE);
    }
    double excess = phase.getExcessGibbsEnergy(phase, 2, temperature, 1.0, PhaseType.LIQUID);
    double expectedExcess = ThermodynamicConstantsInterface.R * temperature * phase.getNumberOfMolesInPhase()
        * (methanolFraction * expected[0] + (1.0 - methanolFraction) * expected[1]);
    assertEquals(expectedExcess, excess, 1.0e-9);
    assertStoredValues(phase, expected);
    for (int i = 0; i < 2; i++) {
      double expectedFugacity = Math.exp(expected[i]) * component(phase, i).getAntoineVaporPressure(temperature);
      assertEquals(expectedFugacity, component(phase, i).fugcoef(phase), TOLERANCE);
    }
  }

  private static void assertStoredValues(PhaseGENRTL phase, double[] expected) {
    for (int i = 0; i < 2; i++) {
      assertEquals(Math.exp(expected[i]), component(phase, i).getGamma(), TOLERANCE);
      assertEquals(expected[i], component(phase, i).getLnGamma(), TOLERANCE);
      assertEquals(Math.log(component(phase, i).getGamma()), component(phase, i).getLnGamma(), TOLERANCE);
    }
  }

  /** Independent closed binary form of the Renon-Prausnitz NRTL equation. */
  private static double[] binaryLogGamma(double x1, double temperature) {
    double x2 = 1.0 - x1;
    double tau12 = 200.0 / temperature;
    double tau21 = -100.0 / temperature;
    double g12 = Math.exp(-0.3 * tau12);
    double g21 = Math.exp(-0.3 * tau21);
    double denominator1 = x1 + x2 * g21;
    double denominator2 = x2 + x1 * g12;
    double logGamma1 = x2 * x2
        * (tau21 * g21 * g21 / (denominator1 * denominator1) + tau12 * g12 / (denominator2 * denominator2));
    double logGamma2 = x1 * x1
        * (tau12 * g12 * g12 / (denominator2 * denominator2) + tau21 * g21 / (denominator1 * denominator1));
    return new double[] {logGamma1, logGamma2};
  }
}
