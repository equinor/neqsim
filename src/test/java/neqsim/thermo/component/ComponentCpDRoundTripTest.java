package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Getter and setter for the ideal-gas CpD coefficient must be inverses.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class ComponentCpDRoundTripTest {
  /**
   * Builds a single-component methane fluid.
   *
   * @return methane at 298.15 K and 10 bara
   */
  private static SystemInterface methane() {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 1.0);
    return fluid;
  }

  /**
   * {@code setCpD} followed by {@code getCpD} returns the stored coefficient, including zero.
   */
  @Test
  public void getCpDReturnsTheValuePassedToSetCpD() {
    ComponentInterface methane = methane().getComponent(0);
    methane.setCpD(0.5);
    assertEquals(0.5, methane.getCpD(), 0.0);
    methane.setCpD(methane.getCpD());
    assertEquals(0.5, methane.getCpD(), 0.0);

    methane.setCpD(0.0);
    assertEquals(0.0, methane.getCpD(), 0.0);
    methane.setCpD(methane.getCpD());
    assertEquals(0.0, methane.getCpD(), 0.0);

    methane.setCpD(-1.08e-7);
    assertEquals(-1.08e-7, methane.getCpD(), 0.0);
  }

  /**
   * Ideal-gas heat capacity uses the stored CpD coefficient in the cubic temperature term.
   */
  @Test
  public void idealGasHeatCapacityUsesStoredCpD() {
    ComponentInterface methane = methane().getComponent(0);
    double temperature = 400.0;
    double cpA = 10.0;
    double cpB = -0.02;
    double cpC = 3.0e-5;
    double cpD = -1.08e-7;
    double cpE = 4.2e-11;
    methane.setCpA(cpA);
    methane.setCpB(cpB);
    methane.setCpC(cpC);
    methane.setCpD(cpD);
    methane.setCpE(cpE);

    double expected = cpA + cpB * temperature + cpC * temperature * temperature
        + cpD * temperature * temperature * temperature + cpE * Math.pow(temperature, 4);
    assertEquals(expected, methane.getCp0(temperature), 0.0);
  }
}
