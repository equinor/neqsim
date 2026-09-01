package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemEOSCGEos;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Regression tests for phase-envelope tracing with multiparameter reference equations of state. */
class PTPhaseEnvelopeReferenceEosTest {

  /** Verify that GERG-2008 traces finite dew and bubble branches for a natural-gas mixture. */
  @Test
  void testGerg2008NaturalGasEnvelope() {
    SystemInterface fluid = new SystemGERG2008Eos(300.0, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.025);
    fluid.addComponent("n-butane", 0.01);
    fluid.addComponent("nitrogen", 0.005);

    assertEnvelope(fluid, "GERG-2008", 1.0, false);
  }

  /** Verify that EOS-CG traces finite dew and bubble branches for a combustion-gas mixture. */
  @Test
  void testEosCgCombustionGasEnvelope() {
    SystemInterface fluid = new SystemEOSCGEos(280.0, 30.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("CO2", 0.10);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.025);
    fluid.addComponent("n-butane", 0.01);
    fluid.addComponent("nitrogen", 0.005);

    assertEnvelope(fluid, "EOS-CG", 1.0, false);
  }

  /**
   * Assert that an equation of state produces both branches and physical characteristic points.
   *
   * @param fluid thermodynamic system to trace
   * @param modelName model name used in assertion messages
   * @param lowPressure starting pressure in bara
   * @param bubbleFirst whether to begin on the bubble branch
   */
  private static void assertEnvelope(SystemInterface fluid, String modelName, double lowPressure, boolean bubbleFirst) {
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.calcPTphaseEnvelope(bubbleFirst, lowPressure);

    double[] dewTemperatures = operations.get("dewT");
    double[] dewPressures = operations.get("dewP");
    double[] bubbleTemperatures = operations.get("bubT");
    double[] bubblePressures = operations.get("bubP");
    double[] cricondenbar = operations.get("cricondenbar");
    double[] cricondentherm = operations.get("cricondentherm");

    assertTrue(countFinitePositive(dewTemperatures) >= 3,
        modelName + " should trace at least three dew points, got T=" + Arrays.toString(dewTemperatures) + ", P="
            + Arrays.toString(dewPressures) + "; bubble T=" + Arrays.toString(bubbleTemperatures) + ", bubble P="
            + Arrays.toString(bubblePressures));
    assertTrue(countFinitePositive(bubbleTemperatures) >= 3,
        modelName + " should trace at least three bubble points, got T=" + Arrays.toString(bubbleTemperatures) + ", P="
            + Arrays.toString(bubblePressures));
    assertFinitePositive(dewTemperatures, modelName + " dew temperatures");
    assertFinitePositive(dewPressures, modelName + " dew pressures");
    assertFinitePositive(bubbleTemperatures, modelName + " bubble temperatures");
    assertFinitePositive(bubblePressures, modelName + " bubble pressures");
    assertTrue(Double.isFinite(cricondenbar[0]) && cricondenbar[0] > 0.0,
        modelName + " cricondenbar temperature should be finite and positive");
    assertTrue(Double.isFinite(cricondenbar[1]) && cricondenbar[1] > 0.0,
        modelName + " cricondenbar pressure should be finite and positive");
    assertTrue(Double.isFinite(cricondentherm[0]) && cricondentherm[0] > 0.0,
        modelName + " cricondentherm temperature should be finite and positive");
    assertTrue(Double.isFinite(cricondentherm[1]) && cricondentherm[1] > 0.0,
        modelName + " cricondentherm pressure should be finite and positive");
  }

  /**
   * Assert that all physical curve points are finite and positive while allowing NaN segment separators.
   *
   * @param values values to inspect
   * @param label assertion label
   */
  private static void assertFinitePositive(double[] values, String label) {
    for (double value : values) {
      assertTrue(Double.isNaN(value) || Double.isFinite(value) && value > 0.0,
          label + " must contain only finite positive values or NaN segment separators");
    }
  }

  /** Count finite positive physical values, excluding NaN branch separators. */
  private static int countFinitePositive(double[] values) {
    int count = 0;
    for (double value : values) {
      if (Double.isFinite(value) && value > 0.0) {
        count++;
      }
    }
    return count;
  }
}
