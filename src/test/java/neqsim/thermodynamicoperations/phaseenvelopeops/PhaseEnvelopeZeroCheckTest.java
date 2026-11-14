package neqsim.thermodynamicoperations.phaseenvelopeops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class PhaseEnvelopeZeroCheckTest {
  @Test
  public void testGERGPhaseEnvelope() {
    SystemInterface gerg_fluid = new SystemGERG2008Eos();
    gerg_fluid.addComponent("methane", 1.0);
    gerg_fluid.addComponent("ethane", 0.1);
    gerg_fluid.addComponent("propane", 0.001);
    gerg_fluid.addComponent("n-butane", 0.001);
    gerg_fluid.addComponent("n-pentane", 0.0001);
    gerg_fluid.setPressure(50.1, "bara");
    gerg_fluid.setTemperature(300.0, "K");
    ThermodynamicOperations ops = new ThermodynamicOperations(gerg_fluid);
    ops.TPflash();
    ops.calcPTphaseEnvelope();

    double[] cricondenbar = ops.get("cricondenbar");
    double[] cricondentherm = ops.get("cricondentherm");
    double[] dewT = ops.get("dewT");
    double[] dewP = ops.get("dewP");
    double[] bubbleT = ops.get("bubT");
    double[] bubbleP = ops.get("bubP");
    assertBranch(dewT, dewP, "GERG dew");
    assertBranch(bubbleT, bubbleP, "GERG bubble");
    // Continuation may sample different points across JDKs. The extrema must come from the returned trace.
    assertEquals(Arrays.stream(dewP).filter(Double::isFinite).max().getAsDouble(), cricondenbar[1], 1e-8);
    assertEquals(Arrays.stream(dewT).filter(Double::isFinite).max().getAsDouble(), cricondentherm[0], 1e-8);
    assertTrue(cricondenbar[0] > 208.0 && cricondenbar[0] < 215.0, "GERG cricondenbar temperature");
    assertTrue(cricondenbar[1] > 58.0 && cricondenbar[1] < 61.0, "GERG cricondenbar pressure");
    assertTrue(cricondentherm[0] > 214.0 && cricondentherm[0] < 217.0, "GERG cricondentherm temperature");
    assertTrue(cricondentherm[1] > 50.0 && cricondentherm[1] < 55.0, "GERG cricondentherm pressure");
  }

  /**
   * Check a physical branch while allowing matched NaN markers between disconnected segments.
   *
   * @param temperatures temperatures in K
   * @param pressures pressures in bara
   * @param label branch name
   */
  private static void assertBranch(double[] temperatures, double[] pressures, String label) {
    assertEquals(temperatures.length, pressures.length, label + " arrays must align");
    int points = 0;
    for (int index = 0; index < temperatures.length; index++) {
      double temperature = temperatures[index];
      double pressure = pressures[index];
      if (Double.isNaN(temperature) && Double.isNaN(pressure)) {
        continue;
      }
      assertTrue(Double.isFinite(temperature) && temperature > 0.0, label + " temperature");
      assertTrue(Double.isFinite(pressure) && pressure > 0.0, label + " pressure");
      points++;
    }
    assertTrue(points >= 3, label + " branch is incomplete");
  }

  @Test
  public void testSRKPhaseEnvelope() {
    SystemInterface srk_fluid = new SystemSrkEos(300.0, 50.1);
    srk_fluid.addComponent("methane", 1.0);
    srk_fluid.addComponent("ethane", 0.1);
    srk_fluid.addComponent("propane", 0.001);
    srk_fluid.addComponent("n-butane", 0.001);
    srk_fluid.addComponent("n-pentane", 0.0001);
    ThermodynamicOperations ops = new ThermodynamicOperations(srk_fluid);
    ops.TPflash();
    ops.calcPTphaseEnvelope();

    double[] cricondenbar = ops.get("cricondenbar");
    double[] cricondentherm = ops.get("cricondentherm");
    double[] dewT = ops.get("dewT");
    double[] dewP = ops.get("dewP");
    assertTrue(cricondenbar[0] > 0.0 && cricondenbar[1] > 0.0, "cricondenbar should be calculated");
    assertTrue(cricondentherm[0] > 0.0 && cricondentherm[1] > 0.0, "cricondentherm should be calculated");
    assertTrue(Arrays.stream(dewT).noneMatch(t -> t == 0.0), "dewT should not contain zeros");
    assertTrue(Arrays.stream(dewP).noneMatch(p -> p == 0.0), "dewP should not contain zeros");
  }
}
